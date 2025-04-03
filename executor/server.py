import datetime
import os
import shutil
import uuid
from threading import Thread, Lock
import logging

from executor import execute
from pydantic import BaseModel
from fastapi import FastAPI, HTTPException

lock = Lock()
active_functions = dict()

logging.getLogger('uvicorn.access').setLevel(logging.WARNING)

class FunctionRequest(BaseModel):
    session_info: str # Should be a base64 encoded gRPC SessionInfo object


def print_with_timestamp(line: str):
    print(f"[{datetime.datetime.now()}] {line}")


def print_with_runtime_id(runtime_id: uuid.UUID, line: str):
    print_with_timestamp(f"({runtime_id}): {line}")


runtime_executor_directory = os.getenv("EXECUTOR_RUN_DIRECTORY", "/tmp/executor") # Where the executor will keep its context
functions_root_directory = f"{runtime_executor_directory}/functions" # Contains the ACTIVE and DOWNLOAD directories which contain extracted functions
functions_cache_directory = f"{runtime_executor_directory}/cache" # Contains all function zips downloaded from S3
execution_root_directory = f"{runtime_executor_directory}/executions" # Contains the function runs

download_function_dir = f"{functions_root_directory}/a"
active_function_dir = f"{functions_root_directory}/b" # We start at dir 'b' as the application startup will immediately go through the update cycle

def initialize_folders():
    print_with_timestamp("Initializing directories...")
    os.makedirs(download_function_dir, exist_ok=True)
    os.makedirs(active_function_dir, exist_ok=True)
    os.makedirs(functions_cache_directory, exist_ok=True)
    os.makedirs(execution_root_directory, exist_ok=True)


def sync(source: str, destination: str):
    match (os.getenv("STORAGE_DRIVER")):
        case "s3":
            execute(f"aws s3 sync {source} {destination}", check=True)
        case "local" | _:
            execute(f"cp -R {source} {destination}", check=True)


def copy(source: str, destination: str):
    match (os.getenv("STORAGE_DRIVER")):
        case "s3":
            execute(f"aws s3 cp {source} {destination}", check=True)
        case "local" | _:
            execute(f"cp -R {source} {destination}", check=True)


functions_location = os.getenv("FUNCTIONS_LOCATION") # From where the functions (in archive from) will be available, for example: s3://functions-stg -> /tmp/functions

def download_functions():
    with lock:
        global active_function_dir
        global download_function_dir

        sync(f"{functions_location}", functions_cache_directory)

        # Extract all compressed functions to the ACTIVE dir
        for function in os.listdir(functions_cache_directory):
            print_with_timestamp(f"Found function dir {function}")
            if function.endswith((".tar", ".gz", ".tar.gz", ".tar.xz")):
                execute(f"tar -xf {functions_cache_directory}/{function}",
                    directory=download_function_dir
                )
                print_with_timestamp(f" ^ Extracted")

        print_with_timestamp(f"Updated functions, setting {download_function_dir} as ACTIVE")

        # Flip the DOWNLOAD dir and ACTIVE dir
        new_download_dir = active_function_dir
        active_function_dir = download_function_dir
        download_function_dir = new_download_dir


print_with_timestamp("Starting executor...")
# Ensure the folders we use exist
initialize_folders()

# First download the functions
download_functions()

print_with_timestamp("Starting API...")

# Then start hosting the API (and the discovered functions)
api = FastAPI()

@api.get("/")
@api.get("/python")
def health():
    return {"healthy": True}


@api.get("/python/functions")
def get_functions():
    return os.listdir(active_function_dir)


# TODO: do we even want this?
@api.get("/python/running_functions")
def running_functions():
    return list(active_functions.keys())


@api.get("/python/reload")
def reload_functions():
    download_functions()
    return get_functions()


@api.post("/python/function/{function_id}")
def execute_function(function_id: str, request: FunctionRequest):
    function_location = f"{active_function_dir}/{function_id}"

    if not os.path.isdir(function_location):
        raise HTTPException(status_code=404, detail=f"Function {function_id} not found")

    runtime_id = uuid.uuid4()
    execution_thread = Thread(target=execute_command, args=(runtime_id, function_id, request, function_location))
    active_functions[runtime_id] = execution_thread

    execution_thread.start()

    return {
        "runtime_id": runtime_id,
        "function_id": function_id,
        "request": request
    }


log_location = os.getenv("LOGS_LOCATION", "/tmp/executor_logs") # Where the local logs will be send to, for example: /tmp/logs -> s3://logs-bucket-stg

def execute_command(runtime_id: uuid.UUID, function_id: str, request: FunctionRequest, function_location: str):
    execution_log_directory_identifier = f"{runtime_id}_{function_id}"
    execution_directory = f"{execution_root_directory}/{execution_log_directory_identifier}"
    execution_code_directory = f"{execution_directory}/code"

    # Create a functions 'context' and an 'alias' directory you can find by a functions id
    os.makedirs(execution_code_directory)

    execution_code_log_file = f"{execution_directory}/output.log"

    with open(execution_code_log_file, "w") as log_file:
        try:
            # Copy over the function 'code' to the execution directory (its 'context')
            create_function_context = execute(
                f"cp -R {function_location}/* {execution_code_directory}",
                stdout_file=log_file, stderr_file=log_file, # Merge both the stdout and stderr to the same file
                asynchronous=True,
                check=True
            )

            print_with_runtime_id(runtime_id, f"Starting {execution_directory}, created from {function_location}")

            # Execute the function in its 'context' for the given session
            succeeded = execute(
                f"python {execution_code_directory}/function.py | tee {execution_code_log_file}", # TODO: do we want to add all output from functions to the executions log?
                dependencies=[create_function_context], # Only execute the command after the context has been created
                environment={"OPENOBD_SESSION_INFO": request.session_info},
                directory=execution_directory,
                check=True
            )
        finally:
            shutil.rmtree(execution_code_directory) # remove the 'code' directory in the functions 'context' to save disk space

            if succeeded:
                print_with_runtime_id(runtime_id, f"Function {function_id} COMPLETED")
            else:
                print_with_runtime_id(runtime_id, f"Function {function_id} FAILED, with code {succeeded}")

            now = datetime.datetime.now()
            date_index_log_location = f"{log_location}/{now:%Y}/{now:%m}/{now:%d}"

            os.makedirs(date_index_log_location, exist_ok=True)

            copy(execution_code_log_file, f"{date_index_log_location}/{execution_log_directory_identifier}.log")

            del active_functions[runtime_id]