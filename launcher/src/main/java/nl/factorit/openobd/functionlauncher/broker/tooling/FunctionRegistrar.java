package nl.factorit.openobd.functionlauncher.broker.tooling;

import com.jifeline.OpenOBD.Function.functionGrpc;
import com.jifeline.OpenOBD.FunctionBroker.Messages.FunctionBrokerToken;
import com.jifeline.OpenOBD.FunctionBroker.Messages.FunctionSignature;
import com.jifeline.OpenOBD.FunctionBroker.functionBrokerGrpc;
import com.jifeline.OpenOBD.Messages.EmptyMessage;
import com.jifeline.OpenOBD.SessionController.Messages.Authenticate;
import com.jifeline.OpenOBD.SessionController.Messages.SessionControllerToken;
import com.jifeline.OpenOBD.SessionController.sessionControllerGrpc;
import io.grpc.Channel;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.TlsChannelCredentials;
import nl.factorit.openobd.functionlauncher.Logger;
import nl.factorit.openobd.functionlauncher.broker.BrokerInterceptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class FunctionRegistrar {
    private static final Logger logger = Logger.getLogger("FunctionRegistrar");

    public static void main(String[] args) {
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

//        SessionControllerToken controllerToken = sessionControllerService.getSessionControllerToken(auth);
//
//        AtomicReference<List<BrokerInterceptor.Header>> headers = new AtomicReference<>();
//        headers.set(List.of(BrokerInterceptor.Header.fromBearerToken(controllerToken.getValue())));

        functionBrokerGrpc.functionBrokerBlockingStub functionBrokerService = functionBrokerGrpc.newBlockingStub(channel);

        FunctionBrokerToken functionBrokerToken = functionBrokerService.getFunctionBrokerToken(auth);

        functionBrokerService = functionBrokerService
                .withInterceptors(BrokerInterceptor.withBearerToken(functionBrokerToken.getValue()));

//        headers.set(List.of(BrokerInterceptor.Header.fromBearerToken(functionBrokerToken.getValue())));

        FunctionSignature functionSignature = functionBrokerService.generateFunctionSignature(EmptyMessage.getDefaultInstance());

        logger.info("Generated an openOBD function:");
        logger.info("Function uuid:      %s".formatted(functionSignature.getId()));
        logger.info("Function signature: %s".formatted(functionSignature.getSignature()));
    }
}
