# openOBD Function Executor

The _executor_ is an application consisting of an API and openOBD functions (written in Python 3). The 
_executor_ has an endpoint that is called by passing a `function id` and an openOBD `session id`. The corresponding function (if 
it exists) will be executed by the _executor_ on the given openOBD session. The log of this execution will be saved (to the location as described in 
the _Configuration_ chapter below) after the function instance has exited (regardless of exit code). 

All functions are imported as compressed archives. These are all extracted to the runtime folder of the _executor_. Every 
REST call to the _executor_'s API checks if the requested function exists and if it does, it'll
be executed. A function archive is named after its function id and must have a `function.py` file (which will be executed 
as entrypoint). See `server::download_functions()` for the specifics

## Running the executor

There are multiple ways to run the executor locally:

### pipenv

The _executor_ has a couple of dependencies which are all included in the included Pipfile. After installing these 
dependencies (with, for example [pipenv](https://pipenv.pypa.io)), 

```bash
pipenv install
pipenv shell
```

you can run the _executor_ with an ASGI compliant webserver, like [Uvicorn](https://www.uvicorn.org/).

```bash
uvicorn server:api --host 0.0.0.0 --port 8080
```

### Docker

Another option is to run the server with Docker using the included Dockerfile. It creates a container containing all needed 
dependencies for the _executor_. First you'll need to build the image with:

```bash
docker build --tag <name_for_the_created_image> .
```

After which you can start up the container, exposing the port configured for the API

```bash
docker run --publish 8080 <name_for_the_created_image>
```

## Configuration

The _executor_ uses environment variables to configure various options. Below is a list of these environment variables and
their explanations:

| Variable name            | Required | Default            | Explanation                                                                                                                                     |
|--------------------------|----------|--------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| `STORAGE_DRIVER`         | NO       | local              | Changes the way functions are retrieved and logs are written (see `server::sync()`)                                                             |
| `FUNCTIONS_LOCATION`     | YES      |                    | The directory in which your functions are located as archive files (as `.tar`, `.gz`, `.tar.gz`, `.tar.xz`, see `server::download_functions()`) |
| `EXECUTOR_RUN_DIRECTORY` | NO       | /tmp/executor      | The directory that the _executor_ will use for its runtime files                                                                                |
| `LOGS_LOCATION`          | NO       | /tmp/executor_logs | The directory in which the _executor_ will save its function execution logs                                                                       |

_As the `STORAGE_DRIVER` might indicate, there are more 'drivers' than the local one. Internally Jifeline uses the S3 driver, for instance. 
With this driver, all function archives get downloaded from an S3 bucket and all logs get sent to an S3 bucket. Should you want to use this same
feature, you can set the `STORAGE_DRIVER` to `s3`. This will require you to use S3 URLs for both the `LOGS_LOCATION` and the `FUNCTIONS_LOCATION`
(see `server::sync` and `server::copy` for specifics)._ 

> [!Note]
> Should you need any other dependencies for your function(s), you can add these to the Pipfile as you would normally do 
> when you run the _executor_ locally or in `pipenv`. But keep in mind that you would have to update the Dockerfile to contain 
> the extra dependencies should you use the Docker environment to run the _executor_ (and remember to rebuild your Docker environment).