#!/usr/bin/env python3

import os
from base64 import b64decode

from openobd import *
from openobd.openobd import ContextVariableType
from openobd_utils.session_context import SessionContext

# Load in the session info given by the calling function
serialized_session_info = os.getenv("OPENOBD_SESSION_INFO")

session_info = SessionInfo()
session_info.ParseFromString(b64decode(serialized_session_info))
first_session = OpenOBDSession(session_info)

print(f"[i] Creating context")
first_context = SessionContext(first_session)
SessionTokenHandler(first_session)

# Set a variable for a context (by default only your own context)
first_session.set_context_variable(key="example-function-key", value="this is a variable only for this context")
print(f" [i] function variable value: {first_session.get_context_variable(key="example-function-key").value}")

# Set a variable for the global context (every other context can read this value)
first_session.set_context_variable(key="example-global-key", value="this is a variable all contexts for this openOBD session can retrieve", variable_type=ContextVariableType.GLOBAL)
print(f" [i] global variable value: {first_session.get_context_variable(key="example-global-key", variable_type=ContextVariableType.GLOBAL).value}")

# Get a variable from a connection (limited to what you can get/set)
print(f" [i] connection variable value: {first_session.get_context_variable(key="vehicle:info:vin", variable_type=ContextVariableType.CONNECTION).value}")

first_session.finish(service_result=ServiceResult(result=[Result.RESULT_SUCCESS]))

print(f"\n[i] Create a new session to fake a different function being called")

# Pretend that we are a different openOBD function that'll create a new session
session_info = SessionInfo()
session_info.ParseFromString(b64decode(serialized_session_info))
second_session = OpenOBDSession(session_info)

print(f"\n[i] Creating context")
second_context = SessionContext(second_session)

SessionTokenHandler(second_session)

# Try and retrieve variables that have been set by a previous function
try:
    function_variable = second_session.get_context_variable("example-function-key")
    print(f" [i] function variable value: {second_session.get_context_variable(key="example-function-key").value}")
except OpenOBDException:
    print(f" [!] Could not find function variable (as it is scoped to the previous context/function)!")

global_variable = second_session.get_context_variable(key="example-global-key", variable_type=ContextVariableType.GLOBAL)
print(f" [i] global variable value: {global_variable.value}")

connection_variable = second_session.get_context_variable(key="vehicle:info:vin", variable_type=ContextVariableType.CONNECTION)
print(f" [i] connection variable value: {connection_variable.value}")

second_session.finish(service_result=ServiceResult(result=[Result.RESULT_SUCCESS]))

print(f"\n[i] Finishing up the contexts")

# Pretend that we get back control in the first function
session_info = SessionInfo()
session_info.ParseFromString(b64decode(serialized_session_info))
third_session = OpenOBDSession(session_info)

# Finish the whole session as there are nog more contexts
third_session.finish(service_result=ServiceResult(result=[Result.RESULT_SUCCESS]))