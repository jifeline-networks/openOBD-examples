package nl.factorit.openobd.functionlauncher.broker.tooling;

import com.google.protobuf.InvalidProtocolBufferException;
import com.jifeline.OpenOBD.Function.Messages.FunctionContext;
import com.jifeline.OpenOBD.Function.Messages.Variable;
import com.jifeline.OpenOBD.Function.Messages.VariableList;
import com.jifeline.OpenOBD.Function.functionGrpc;
import com.jifeline.OpenOBD.FunctionBroker.Messages.FunctionBrokerToken;
import com.jifeline.OpenOBD.FunctionBroker.Messages.FunctionCall;
import com.jifeline.OpenOBD.FunctionBroker.Messages.FunctionUpdate;
import com.jifeline.OpenOBD.FunctionBroker.functionBrokerGrpc;
import com.jifeline.OpenOBD.Messages.EmptyMessage;
import com.jifeline.OpenOBD.Session.Messages.Result;
import com.jifeline.OpenOBD.Session.Messages.ServiceResult;
import com.jifeline.OpenOBD.Session.Messages.SessionToken;
import com.jifeline.OpenOBD.Session.sessionGrpc;
import com.jifeline.OpenOBD.SessionController.Messages.*;
import com.jifeline.OpenOBD.SessionController.sessionControllerGrpc;
import io.grpc.Channel;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.TlsChannelCredentials;
import nl.factorit.openobd.functionlauncher.Logger;
import nl.factorit.openobd.functionlauncher.broker.BrokerInterceptor;

import java.time.Duration;
import java.util.Base64;
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
                .setClusterId(System.getenv("OPENOBD_CLUSTER_ID"))
                .build();

        SessionControllerToken controllerToken = sessionControllerService.getSessionControllerToken(auth);

        AtomicReference<List<BrokerInterceptor.Header>> sessionControllerHeaders = new AtomicReference<>();
        sessionControllerHeaders.set(List.of(BrokerInterceptor.Header.fromBearerToken(controllerToken.getValue())));

        sessionControllerService = sessionControllerService.withInterceptors(new BrokerInterceptor(sessionControllerHeaders));

        // Create an openOBD session on a ticket
        String ticketId = System.getenv("TICKET_ID");
        logger.info("Creating openOBD session on ticket %s".formatted(ticketId));
        SessionInfo sessionInfo = null;

        try {
            sessionInfo = sessionControllerService.startSessionOnTicket(TicketId.newBuilder().setValue(ticketId).build());

            AtomicReference<List<BrokerInterceptor.Header>> sessionServiceHeaders = new AtomicReference<>();
            sessionServiceHeaders.set(List.of(BrokerInterceptor.Header.fromBearerToken(sessionInfo.getAuthenticationToken())));

            sessionGrpc.sessionBlockingStub session = sessionGrpc
                    .newBlockingStub(channel)
                    .withInterceptors(new BrokerInterceptor(sessionServiceHeaders));

            SessionToken token = session.authenticate(EmptyMessage.getDefaultInstance());

//            sessionServiceHeaders.set(List.of(BrokerInterceptor.Header.fromBearerToken(token.getValue())));

//            functionGrpc.functionBlockingStub function = functionGrpc
//                    .newBlockingStub(channel)
//                    .withInterceptors(new BrokerInterceptor(sessionServiceHeaders));

            AtomicReference<List<BrokerInterceptor.Header>> brokerServiceHeaders = new AtomicReference<>();
            brokerServiceHeaders.set(List.of(BrokerInterceptor.Header.fromBearerToken(sessionInfo.getAuthenticationToken())));

            functionBrokerGrpc.functionBrokerBlockingStub functionBroker = functionBrokerGrpc
                    .newBlockingStub(channel)
                    .withInterceptors(new BrokerInterceptor(brokerServiceHeaders));

            FunctionBrokerToken functionBrokerToken = functionBroker.getFunctionBrokerToken(auth);
            brokerServiceHeaders.set(List.of(BrokerInterceptor.Header.fromBearerToken(functionBrokerToken.getValue())));

//            FunctionContext functionContext = function.startFunctionContext(EmptyMessage.getDefaultInstance());
            FunctionUpdate functionUpdate = functionBroker.runFunction(FunctionCall.newBuilder()
                    .setId("ba9f6de0-2c41-488a-a669-8e565ecb1519")
                    .setSessionInfo(sessionInfo)
                    .build());

            logger.info(functionUpdate.getResponseDescription());

            Thread.sleep(30*1000);
//
//            sessionServiceHeaders.set(List.of(BrokerInterceptor.Header.fromBearerToken(functionContext.getAuthenticationToken())));
////            SessionToken token = session.authenticate(EmptyMessage.getDefaultInstance());
////            functionServiceHeaders.set(List.of(BrokerInterceptor.Header.fromBearerToken(token.getValue())));
//
////            function.setFunctionArgument(Variable.newBuilder().setKey("Klondike").setValue("bar!").build());
//            functionServiceHeaders.set(List.of(BrokerInterceptor.Header.fromBearerToken(functionContext.getAuthenticationToken())));
//            VariableList functionArgument = function.getFunctionArgumentList(EmptyMessage.getDefaultInstance());
//
////            functionServiceHeaders.set(List.of(BrokerInterceptor.Header.fromBearerToken(token.getValue())));
//
//            function.setFunctionResult(Variable.newBuilder().setKey("asdf").setValue("fdsa").build());
//            functionServiceHeaders.set(List.of(BrokerInterceptor.Header.fromBearerToken(functionContext.getAuthenticationToken())));
//            VariableList functionResults = function.getFunctionResultList(EmptyMessage.getDefaultInstance());
//
//            session.finish(ServiceResult.newBuilder().addResult(Result.RESULT_SUCCESS).build());
//
//            functionServiceHeaders.set(List.of(BrokerInterceptor.Header.fromBearerToken(sessionInfo.getAuthenticationToken())));
//
//            FunctionContext functionContext1 = function.startFunctionContext(EmptyMessage.getDefaultInstance());
//            sessionServiceHeaders.set(List.of(BrokerInterceptor.Header.fromBearerToken(functionContext1.getAuthenticationToken())));
//            session.finish(ServiceResult.newBuilder().addResult(Result.RESULT_SUCCESS).build());
//
////            sessionServiceHeaders.set(List.of(BrokerInterceptor.Header.fromBearerToken(sessionInfo.getAuthenticationToken())));
////            SessionToken token2 = session.authenticate(EmptyMessage.getDefaultInstance());
////            functionServiceHeaders.set(List.of(BrokerInterceptor.Header.fromBearerToken(token2.getValue())));
//            functionServiceHeaders.set(List.of(BrokerInterceptor.Header.fromBearerToken(sessionInfo.getAuthenticationToken())));
//            VariableList functionResults2 = function.getFunctionResultList(EmptyMessage.getDefaultInstance());
//


            session.finish(ServiceResult.newBuilder().addResult(Result.RESULT_SUCCESS).build());

//            Iterator<SessionContext> contextIterator = session.monitorContext(SessionContext.newBuilder().build());
//            session.authenticate(EmptyMessage.newBuilder().build());
//
//            SessionContext next = contextIterator.next();
//
//            next.getFinished();

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

            if (null != sessionInfo) {
                logger.info("Interrupting session %s".formatted(sessionInfo.getId()));

                sessionInfo = sessionControllerService.interruptSession(SessionId.newBuilder().setValue(sessionInfo.getId()).build());

                logger.info("Session state is now %s".formatted(sessionInfo.getState()));
            }
        }

        logger.info("Function launcher client done");
    }
}
