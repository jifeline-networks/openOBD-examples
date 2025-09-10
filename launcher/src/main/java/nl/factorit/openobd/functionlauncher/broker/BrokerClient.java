package nl.factorit.openobd.functionlauncher.broker;

import com.jifeline.OpenOBD.FunctionBroker.Messages.FunctionBrokerToken;
import com.jifeline.OpenOBD.FunctionBroker.Messages.FunctionUpdate;
import com.jifeline.OpenOBD.FunctionBroker.functionBrokerGrpc;
import com.jifeline.OpenOBD.SessionController.Messages.Authenticate;
import io.grpc.*;
import nl.factorit.openobd.functionlauncher.Logger;
import nl.factorit.openobd.functionlauncher.broker.BrokerInterceptor.Header;
import nl.factorit.openobd.functionlauncher.broker.communication.BrokerCommunicator;
import nl.factorit.openobd.functionlauncher.broker.communication.OutgoingMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Client that is used to initialize contact with the Function Broker, will create the streams and handle the authorisation
 */
public class BrokerClient {
    private static final Logger logger = Logger.getLogger("BrokerClient");
    private static final String DEFAULT_CLUSTER_ID = "001";
    private static final String DEFAULT_GRPC_HOST = "grpc.openobd.com";

    private final AtomicReference<List<Header>> headers = new AtomicReference<>(new ArrayList<>());
    private final BrokerCommunicator communicator;

    public BrokerClient() throws BrokerAuthenticationException {
        String grpcHost = System.getenv("OPENOBD_GRPC_HOST");

        if (null == grpcHost) {
            grpcHost = DEFAULT_GRPC_HOST;
        }

        logger.debug("Using %s as gRPC host".formatted(grpcHost));

        Channel channel;
        if (null != System.getenv("DEV_MODE")) {
            logger.debug("Using insecure credentials for gRPC");
            channel = Grpc.newChannelBuilder(grpcHost, InsecureChannelCredentials.create()).build();
        } else {
            channel = Grpc.newChannelBuilder(grpcHost, TlsChannelCredentials.create()).build();
        }

        String token = retrieveFunctionBrokerToken(channel).getValue();

        this.headers.set(List.of(
                Header.fromBearerToken(token)
        ));

        logger.debug("Opening Function stream on the Function Broker");

        functionBrokerGrpc.functionBrokerStub functionBroker = functionBrokerGrpc.newStub(channel)
                .withInterceptors(new BrokerInterceptor(this.headers));

        this.communicator = BrokerCommunicator.startCommunications(functionBroker);
    }

    private FunctionBrokerToken retrieveFunctionBrokerToken(Channel channel) {
        functionBrokerGrpc.functionBrokerBlockingStub synchronousBroker = functionBrokerGrpc.newBlockingStub(channel);

        String clusterId = System.getenv("OPENOBD_CLUSTER_ID");

        if (null == clusterId) {
            clusterId = DEFAULT_CLUSTER_ID;
        }

        Authenticate auth = Authenticate.newBuilder()
                .setClientId(System.getenv("OPENOBD_PARTNER_CLIENT_ID"))
                .setClientSecret(System.getenv("OPENOBD_PARTNER_CLIENT_SECRET"))
                .setClusterId(clusterId)
                .build();

        logger.debug("Authenticating as client %s on cluster %s's Function Broker".formatted(auth.getClientId(), auth.getClusterId()));

        return synchronousBroker.getFunctionBrokerToken(auth);
    }

    /**
     * Update the token used to authenticate with the Function Broker
     *
     * @param newToken the new token to be used
     */
    public void updateToken(FunctionBrokerToken newToken) {
        logger.debug("Updating Bearer token");

        this.headers.set(List.of(
                Header.fromBearerToken(newToken.getValue())
        ));
    }

    /**
     * Stops all communications with the FunctionBroker
     *
     * @see BrokerCommunicator
     */
    public void stopCommunications() {
        this.communicator.stopCommunications();
    }

    /**
     * Sends a request to the Function Broker
     *
     * @param message either a request or response to a request
     *
     * @see BrokerCommunicator
     * @see OutgoingMessage request
     * @see OutgoingMessage.Response response to a request
     */
    public void send(OutgoingMessage<?> message) {
        this.communicator.send(message);
    }

    /**
     * Get a message that was sent by the Function Broker if any
     *
     * @return Optional with a Function update or an empty optional
     *
     * @see BrokerCommunicator
     */
    public Optional<FunctionUpdate> receive() {
        return this.communicator.receive();
    }

    public static class BrokerAuthenticationException extends RuntimeException {
        public BrokerAuthenticationException() {
            super("Could not authenticate with the Function Broker!");
        }
    }
}
