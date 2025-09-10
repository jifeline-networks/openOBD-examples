# openOBD Function Launcher

First, make sure you have configured your environment as specified in the _Configuration_ chapter below. After that, you can use
Maven to compile the _launcher_ and simply run the resulting `.jar` file with java

```bash
mvn package
java -jar <location_of_the_compiled_jar_file>
```

This should start up a _launcher_ that will register its functions to the _broker_ and listen to requests
of said _broker_. Requests for functions will be passed along to the configured _executor_, provided these
functions are described in the function description file.

> [!Note]
> Be aware that the _launcher_ interacts with the _broker_ by default. So when you run the _launcher_ this way, openOBD clients
> could request your locally hosted functions!

# Configuration

There are two things you'll need to have configured before running this _launcher_:

- The function description file
- The Partner API credentials

The function description file is a file containing all openOBD functions that this _launcher_ will register with the
public _broker_. Every function described in the file consists of a couple of properties. Most importantly, a function's
`id`, its `signature` and its associated _executor_. See `FunctionsParser::FunctionDescription` for more info.
The combination of a function's `id` and `signature` are used to [register a function](https://docs.openobd.com/latest/design/function_broker/#generating-function-uuid-and-signature).
The combination of a function's `id` and _executor_ are used to trigger a function execution when requested by the _broker_.

The following list describes the environment variables available to the _launcher_:

| Variable name                   | Required | Default          | Explanation                                                                                    |
|---------------------------------|----------|------------------|------------------------------------------------------------------------------------------------|
| `DEV_MODE`                      | NO       | <none>           | Running DEV_MODE uses insecure gRPC channels (_any_ value will enable this option)             |
| `FUNCTIONS_FILE_LOCATION`       | YES      |                  | Location of the file containing all function descriptions                                      |
| `FUNCTIONS_MINIMUM_MODE`        | NO       | UNDEFINED        | Makes it possible to only register some defined functions (see `FunctionParser::FunctionMode`) |
| `LOG_OUTPUT_LEVEL`              | NO       | INFO             | Will result in more log output (see `Logger::Level`)                                           |
| `OPENOBD_CLUSTER_ID`            | NO       | 001              | The cluster used to authorize a Partner through the Partner API. `001` refers to Europe        |
| `OPENOBD_GRPC_HOST`             | NO       | grpc.openobd.com | The hostname of the openOBD Function Broker                                                    |
| `OPENOBD_PARTNER_CLIENT_ID`     | YES      |                  | A Partner's API credentials id                                                                 |
| `OPENOBD_PARTNER_CLIENT_SECRET` | YES      |                  | A Partner's API credentials secret                                                             |