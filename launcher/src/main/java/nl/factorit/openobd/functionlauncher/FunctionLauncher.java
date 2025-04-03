package nl.factorit.openobd.functionlauncher;

import com.jifeline.OpenOBD.FunctionBroker.Messages.*;
import nl.factorit.openobd.functionlauncher.broker.BrokerClient;
import nl.factorit.openobd.functionlauncher.broker.communication.BrokerStream;
import nl.factorit.openobd.functionlauncher.broker.communication.OutgoingMessage;
import java.util.logging.*;

import java.io.IOException;
import java.time.Duration;
import java.util.*;

/**
 * Main thread of the FunctionLauncher that will manage the configured openOBD Functions and execute these when a client
 * requests the Function Broker to launch one of its function
 */
public class FunctionLauncher {
    private static final long REFRESH_INTERVAL = Duration.ofSeconds(1).toMillis();
    private static final Logger logger = Logger.getLogger("FunctionLauncher");

    private final Map<String, Function> functions = new HashMap<>();
    private BrokerClient brokerClient;

    private final ExecutorClient executorClient;

    private boolean markedForShutdown = false;
    private boolean running = false;

    public FunctionLauncher() {
        try {
            logger.info("Starting with an interval of %d second(s)".formatted(Duration.ofMillis(REFRESH_INTERVAL).toSeconds()));

            this.executorClient = new ExecutorClient();

            initializeBrokerStream();

            logger.info("Serving %d function(s)".formatted(this.functions.size()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (BrokerStream.StreamEndingException e) {
            logger.error(e.getMessage(), e.getCause());

            throw e;
        } catch (BrokerClient.BrokerAuthenticationException e) {
            logger.error(e.getMessage());

            throw e;
        }
    }

    private void initializeBrokerStream() throws IOException {
        logger.debug("Opening Function Broker stream");
        this.brokerClient = new BrokerClient();

        // Register all the functions the parser retrieved and set them to ONLINE
        new FunctionsParser().getFunctions().forEach((functionId, functionDescription) -> {
            FunctionRegistration registration = FunctionRegistration.newBuilder()
                    .setId(functionId.toString())
                    .setName(functionDescription.name())
                    .setSignature(functionDescription.signature())
                    .setState(FunctionRegistrationState.FUNCTION_REGISTRATION_STATE_ONLINE)
                    .setVersion(functionDescription.version())
                    .setDescription(functionDescription.description())
                    .build();

            brokerClient.send(new OutgoingMessage.FunctionRegistrationMessage(registration));

            functions.put(
                    functionId.toString(),
                    new Function(
                            functionDescription,
                            registration
                    )
            );
        });
    }

    /**
     * Main loop that will start listening for messages send by the Function Broker and act accordingly.
     */
    public void run() throws InterruptedException {
        logger.info("Listening for function calls");

        int tries = 0;

        while (!Thread.currentThread().isInterrupted() && !this.markedForShutdown) {
            this.running = true;

            Optional<FunctionUpdate> request = this.brokerClient.receive();

            try {
                Thread.sleep(REFRESH_INTERVAL);

                if (request.isEmpty()) {
                    continue;
                }

                FunctionUpdate update = request.get();

                if (FunctionUpdateType.FUNCTION_UPDATE_TYPE_REQUEST != update.getType()) {
                    logger.debug("Ignoring %s update: %s".formatted(update.getType(), update.getResponse()));
                    continue;
                }

                if (update.hasFunctionRegistration()) {
                    FunctionCall call = update.getFunctionCall();

                    logger.debug("%s: Got a REQUEST".formatted(call.getId()));

                    Function function = this.functions.get(call.getId());

                    if (null == function) {
                        throw new UnknownFunctionException(call.getId());
                    }

                    // Start the requested function
                    this.executorClient.startFunction(
                            new ExecutorClient.FunctionAndSessionInfo(
                                    function,
                                    call.getSessionInfo()
                            )
                    );

                    // If there wasn't any error we send a start success to the broker
                    this.brokerClient.send(
                            new OutgoingMessage.FunctionCallResponse(
                                    call,
                                    FunctionUpdateResponse.FUNCTION_UPDATE_SUCCESS,
                                    "Function %s has been started successfully".formatted(call.getId())
                            )
                    );

                } else if (update.hasFunctionBrokerToken()) {
                    logger.debug("Token update, refreshing token");
                    // The ping possibly contains an updated token (meaning our current one could expire soon) so we'll
                    // always override it
                    this.brokerClient.updateToken(update.getFunctionBrokerToken());

                    // And we send a ping back to keep the gRPC stream from being closed by the ALB
                    this.brokerClient.send(
                            new OutgoingMessage.FunctionBrokerTokenMessage(update.getFunctionBrokerToken())
                    );
                } else if (update.hasFunctionBrokerReconnect()) {
                    logger.debug("Broker going down, initiating reconnect");
                    throw new BrokerReconnectException(update.getFunctionBrokerReconnect().getSecondsUntilDisconnect());
                }

                tries = 0; // Reset the recover stream counter as we have successfully listened for a request
            } catch (BrokerStream.StreamEndingException | BrokerReconnectException e) {
                // Try to fix the broker connection between the Function Launcher and Function Broken
                if (Server.MAX_ITERATIONS <= tries) {
                    logger.error("Could not recover Broker communication, stopping");

                    this.requestShutdown();
                    throw e;
                }

                if (null != e.getCause()) {
                    logger.error(e.getMessage(), e.getCause());
                } else {
                    logger.error(e.getMessage());
                }
                logger.info("Trying to recover Broker communication (trying %s more time(s))...".formatted(Server.MAX_ITERATIONS - tries));

                try {
                    initializeBrokerStream();
                } catch (IOException ignored) {
                    // If it fails the only thing we can do is try it again... (with the max amount of tries of course)
                }

                tries = 1 + tries;
                Thread.sleep(Server.SLEEP_TIME_IN_SECONDS.toMillis() * tries);
            } catch (
                UnknownFunctionException |
                ExecutorClient.FunctionNotStartedException e
            ) {
                // Inform the client that requested an openOBD function that it could not be started
                request.ifPresent(update -> {
                    if (null != e.getCause()) {
                        logger.error("Function %s could not be started: %s".formatted(update.getFunctionCall().getId(), e.getMessage()), e.getCause());
                    } else {
                        logger.error("Function %s could not be started: %s".formatted(update.getFunctionCall().getId(), e.getMessage()));
                    }

                    brokerClient.send(
                        new OutgoingMessage.FunctionCallResponse(
                                update.getFunctionCall(),
                                FunctionUpdateResponse.FUNCTION_UPDATE_FAILED,
                                "Function %s could not be started".formatted(update.getFunctionCall().getId())
                        )
                    );
                });
            }
        }

        this.cleanup();

        this.running = false;

        logger.info("Stopped listening for updates");
    }

    /**
     * Gracefully stop the Function Broker communications by setting all the served openOBD function to OFFLINE and closing
     * communication streams
     */
    private void cleanup() {
        // Set all functions the Function Launchers serves to OFFLINE
        this.functions.forEach((functionId, function) -> {
            FunctionRegistration offlineFunction = FunctionRegistration.newBuilder(function.registration)
                    .setState(FunctionRegistrationState.FUNCTION_REGISTRATION_STATE_OFFLINE)
                    .build();

            brokerClient.send(new OutgoingMessage.FunctionRegistrationMessage(offlineFunction));
            functions.get(functionId).updateRegistration(offlineFunction);
        });

        this.brokerClient.stopCommunications();
    }

    /**
     * Break the update loop from the outside
     */
    public void requestShutdown() {
        if (this.markedForShutdown) {
            logger.debug("Another shutdown request recieved");

            return;
        }

        logger.info("Shutdown request received");

        this.markedForShutdown = true;
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Basically a tuple, holding information about a single Function
     */
    public static class Function {
        public FunctionsParser.FunctionDescription description;
        public FunctionRegistration registration;

        public Function(FunctionsParser.FunctionDescription description, FunctionRegistration registration) {
            this.description = description;
            this.registration = registration;
        }

        /**
         * Updates the registration (done after a server mutation request)
         *
         * @param registration The registration returned by the server
         */
        public void updateRegistration(FunctionRegistration registration) {
            this.registration = registration;
        }
    }

    public static class BrokerReconnectException extends RuntimeException {
        public BrokerReconnectException(int secondsUntilDisconnect) {
            super("Connected Broker is going down in %ss, reconnect to new Broker instance".formatted(secondsUntilDisconnect));
        }
    }

    public static class UnknownFunctionException extends RuntimeException {
        public UnknownFunctionException(String functionId) {
            super("Unknown openOBD function (%s) could not be started".formatted(functionId));
        }
    }
}
