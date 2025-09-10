package nl.factorit.openobd.functionlauncher;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jifeline.OpenOBD.SessionController.Messages.SessionInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.net.URL;

/**
 * Client to execute an openOBD function via HTTP requests
 */
public class ExecutorClient {
    public static final int EXECUTOR_HTTP_TIMEOUT = (int) Duration.ofSeconds(5).toMillis();
    private static final Logger logger = Logger.getLogger("ExecutorClient");

    private final String executorHost;

    public ExecutorClient() {
        this.executorHost = System.getenv("OPENOBD_EXECUTOR_HOST");
    }

    public FunctionResponse startFunction(FunctionAndSessionInfo functionAndSessionInfo) throws FunctionNotStartedException, FunctionStartedWithException {
        HttpURLConnection executorConnection;
        OutputStream output;

        try {
            logger.debug("Starting function %s on %s:%s".formatted(
                    functionAndSessionInfo.function.registration.getDetails().getId(),
                    functionAndSessionInfo.getFunctionExecutor(),
                    functionAndSessionInfo.getRuntimeId()
            ));

            // The actual URL is constructed like: <HOST>/<PATH>/function/<FUNCTION_ID>,
            // for example: ptc-internal-pdflatex-service-401.acc.jifeline.cloud/python/function/36af611a-832e-40dd-9946-a5dd24b4c0b9
            // The given payload should contain the session the function should be executed for
            URL url = new URL(
                String.join("/",
                    this.executorHost,
                    functionAndSessionInfo.getFunctionExecutor(),
                    "function",
                    functionAndSessionInfo.function.registration.getDetails().getId()
                )
            );

            executorConnection = (HttpURLConnection) url.openConnection();
            executorConnection.setReadTimeout(EXECUTOR_HTTP_TIMEOUT);
            executorConnection.setConnectTimeout(EXECUTOR_HTTP_TIMEOUT);
            executorConnection.setDoInput(true);
            executorConnection.setDoOutput(true);

            executorConnection.setRequestMethod("POST");
            executorConnection.setRequestProperty("Content-Type", "application/json");
            executorConnection.setRequestProperty("RuntimeId", functionAndSessionInfo.getRuntimeId());

            output = executorConnection.getOutputStream();
        } catch (IOException e) {
            throw new FunctionNotStartedException(e);
        }

        try {
            output.write(functionAndSessionInfo.toRequest().toJson().getBytes());

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            executorConnection.getInputStream()
                    )
            );

            String line;
            StringBuilder response = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            executorConnection.disconnect();

            logger.debug("Got %s as response".formatted(response.toString()));

            return FunctionResponse.fromJson(response.toString());
        } catch (Exception e) {
            throw new FunctionStartedWithException(e);
        }
    }

    public record FunctionAndSessionInfo(
        FunctionLauncher.Function function,
        SessionInfo session
    ) {
        public String getRuntimeId() {
            return this.function.description.runtime();
        }

        public String getFunctionExecutor() {
            return this.function.description.executor();
        }

        public FunctionRequest toRequest() {
            return new FunctionRequest(
                    this.function.registration.getDetails().getId(),
                    Base64.getEncoder().encodeToString(this.session.toByteArray())
            );
        }
    }

    public record FunctionRequest (
        @JsonProperty("uuid") String functionId,
        @JsonProperty("session_info") String session

    ) {
        public String toJson() throws JsonProcessingException {
            return new ObjectMapper().writeValueAsString(this);
        }
    }

    public static class FunctionResponse extends HashMap<String, Object> {
        public static FunctionResponse fromJson(String json) throws JsonProcessingException {
            return new ObjectMapper().readValue(json, FunctionResponse.class);
        }
    }

    public static class FunctionNotStartedException extends RuntimeException {
        public FunctionNotStartedException(Throwable cause) {
            super("Function could not be started due to a %s: %s".formatted(cause.getClass(), cause.getMessage()), cause);
        }
    }

    public static class FunctionStartedWithException extends RuntimeException {
        public FunctionStartedWithException(Throwable cause) {
            super("Function was started but resulted in an exception %s: %s".formatted(cause.getClass(), cause.getMessage()), cause);
        }
    }
}
