package nl.factorit.openobd.functionlauncher.broker.communication;

import com.jifeline.OpenOBD.FunctionBroker.Messages.FunctionUpdate;
import com.jifeline.OpenOBD.FunctionBroker.functionBrokerGrpc;
import io.grpc.stub.StreamObserver;
import nl.factorit.openobd.functionlauncher.Logger;

import java.util.Optional;

/**
 * Communicator that provides the public interface for the communication to and from the Function Broker
 */
public class BrokerCommunicator {
    private static final Logger logger = Logger.getLogger("BrokerCommunicator");

    private final BrokerStream.Incoming incomingStream;
    private final BrokerStream.Outgoing outgoingStream;

    private BrokerCommunicator(BrokerStream.Incoming incomingStream, BrokerStream.Outgoing outgoingStream) {
        this.incomingStream = incomingStream;
        this.outgoingStream = outgoingStream;
    }

    /**
     * Initialized the BrokerCommunicator for the given stub
     *
     * @param functionBroker The fully initialized Function Brokers gRPC stub
     *
     * @return A ready to use BrokerCommunicator
     */
    public static BrokerCommunicator startCommunications(functionBrokerGrpc.functionBrokerStub functionBroker) {
        logger.debug("starting Broker communication streams");

        BrokerStream.Incoming incomingStream = new BrokerStream.Incoming();

        StreamObserver<FunctionUpdate> requestStream = functionBroker.openFunctionStream(incomingStream);

        BrokerStream.Outgoing outgoingStream = new BrokerStream.Outgoing(requestStream);

        return new BrokerCommunicator(incomingStream, outgoingStream);
    }

    /**
     * Gracefully stops the communication streams to and from the Function Broker
     */
    public void stopCommunications() {
        logger.debug("Stopping Broker communication streams");

        this.incomingStream.stop();
        this.outgoingStream.stop();
    }

    /**
     * Send a message to the Function Broker (i.e. to register a function)
     *
     * @param message The message to send
     * @throws BrokerStream.StreamEndingException Thrown when a message was sent with an already closed stream
     */
    public void send(OutgoingMessage<?> message) throws BrokerStream.StreamEndingException {
        this.outgoingStream.send(message);
    }

    /**
     * Retrieves an update from the Function Broker, if any is available
     *
     * @return Optional loaded with an update, if any
     * @throws BrokerStream.StreamEndingException  Thrown when a response was read with an already closed stream
     */
    public Optional<FunctionUpdate> receive() throws BrokerStream.StreamEndingException {
        return this.incomingStream.receive();
    }
}
