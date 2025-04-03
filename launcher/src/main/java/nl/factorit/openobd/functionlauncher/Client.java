package nl.factorit.openobd.functionlauncher;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.jifeline.OpenOBD.FunctionBroker.Messages.*;
import com.jifeline.OpenOBD.FunctionBroker.functionBrokerGrpc;
import com.jifeline.OpenOBD.Messages.EmptyMessage;
import com.jifeline.OpenOBD.Session.Messages.FunctionId;
import com.jifeline.OpenOBD.Session.Messages.SessionContext;
import com.jifeline.OpenOBD.Session.sessionGrpc;
import com.jifeline.OpenOBD.SessionController.Messages.*;
import com.jifeline.OpenOBD.SessionController.sessionControllerGrpc;
import io.grpc.Channel;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.TlsChannelCredentials;
import nl.factorit.openobd.functionlauncher.broker.BrokerInterceptor;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Only used to test the gRPC server of the FunctionLauncher when it is deployed to AWS (or even locally)
 */
public class Client {
    private static final Logger logger = Logger.getLogger("Client");

    /**2
     * Implements an openOBD function client to request a function from the Function Broker on our own Function Launcher
     */
    public static void main(String[] args) throws InvalidProtocolBufferException {
        // Get the tokens to get the authorization to create an openOBD session
        String grpcHost = System.getenv("OPENOBD_GRPC_HOST");

        Channel channel;
        if (null != System.getenv("DEV_MODE")) {
            channel = Grpc.newChannelBuilder(grpcHost, InsecureChannelCredentials.create()).build();
        } else {
            channel = Grpc.newChannelBuilder(grpcHost, TlsChannelCredentials.create()).build();
        }

        logger.info("Authenticating with the Session Controller");
        sessionControllerGrpc.sessionControllerBlockingStub sessionControllerService = sessionControllerGrpc.newBlockingStub(channel);

        Authenticate auth = Authenticate.newBuilder()
                .setClientId(System.getenv("OPENOBD_PARTNER_CLIENT_ID"))
                .setClientSecret(System.getenv("OPENOBD_PARTNER_CLIENT_SECRET"))
                .setApiKey(System.getenv("OPENOBD_PARTNER_API_KEY"))
                .setClusterId(System.getenv("OPENOBD_CLUSTER_ID"))
                .build();

        SessionControllerToken controllerToken = sessionControllerService.getSessionControllerToken(auth);

        sessionControllerService = sessionControllerService.withInterceptors(BrokerInterceptor.withBearerToken(controllerToken.getValue()));

        // Create an openOBD session on a ticket
        String ticketId = System.getenv("TICKET_ID");
        logger.info("Creating openOBD session on ticket %s".formatted(ticketId));
        SessionInfo sessionInfo = null;

        try {
            sessionInfo = sessionControllerService.startSessionOnTicket(TicketId.newBuilder().setValue(ticketId).build());

            logger.info("Session info: %s".formatted(Base64.getEncoder().encodeToString(sessionInfo.toByteArray())));

            sessionGrpc.sessionBlockingStub session = sessionGrpc.newBlockingStub(channel);

            Iterator<SessionContext> contextIterator = session.monitorContext(SessionContext.newBuilder().build());
            session.authenticate(EmptyMessage.newBuilder().build());

            SessionContext next = contextIterator.next();

            next.getFinished();

//            AtomicReference<List<BrokerInterceptor.Header>> headers = new AtomicReference<>(new ArrayList<>());
//
//            functionBrokerGrpc.functionBrokerBlockingStub functionBrokerService = functionBrokerGrpc.newBlockingStub(channel);
//
//            logger.info("Authenticating with the Function Broker");
//            FunctionBrokerToken brokerToken = functionBrokerService.getFunctionBrokerToken(auth);
//            headers.set(List.of(
//                    BrokerInterceptor.Header.fromBearerToken(brokerToken.getValue())
//            ));

//            functionBrokerService = functionBrokerService
//                    .withInterceptors(new BrokerInterceptor(headers));
//
//            // Check if the openOBD function we want to execute is online
//            String functionId = System.getenv("FUNCTION_ID");
//
//            logger.info("Checking state of function %s".formatted(functionId));
//            FunctionRegistration functionRegistration = functionBrokerService.getFunctionRegistration(FunctionId.newBuilder().setValue(functionId).build());
//
//            logger.info("Function %s (v%s) has state %s".formatted(functionRegistration.getId(), functionRegistration.getVersion(), functionRegistration.getState().name()));
//
//            if (FunctionRegistrationState.FUNCTION_REGISTRATION_STATE_ONLINE != functionRegistration.getState()) {
//                logger.error("Cannot call Function %s as it is not ONLINE!".formatted(functionRegistration.getId()));
//
//                return;
//            }
//
//            // Request an execution of the openOBD function on our openOBD session
//            logger.info("Trying to run function %s".formatted(functionId));
//            FunctionCall call = FunctionCall.newBuilder()
//                    .setId(functionId)
//                    .setSessionInfo(session)
//                    .build();
//
//            FunctionUpdate functionUpdate = functionBrokerService.runFunction(call);
//
//            logger.info("Response: %2s, %s".formatted(functionUpdate.getResponseDescription(), functionUpdate.getResponse().name()));

            // TODO: Monitor the function execution when this feature has been developed
        } catch (Exception e) {
            logger.error("%s Exception was thrown: %s".formatted(e.getClass(), e.getMessage()));
            throw new RuntimeException(e);
        } finally {
            // Clean the openOBD session by disconnecting the robots, so we can create new openOBD session for the same ticket

//            if (null != session) {
//                logger.info("Interrupting session %s".formatted(session.getId()));
//
//                SessionInfo sessionInfo = sessionControllerService.interruptSession(SessionId.newBuilder().setValue(session.getId()).build());
//
//                logger.log(Level.FINE, "Session state is now %s".formatted(sessionInfo.getState()));
//            }
        }

        logger.info("Function launcher client done");
    }
}
