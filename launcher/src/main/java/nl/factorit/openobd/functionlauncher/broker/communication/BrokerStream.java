package nl.factorit.openobd.functionlauncher.broker.communication;

import com.jifeline.OpenOBD.FunctionBroker.Messages.FunctionUpdate;
import io.grpc.stub.StreamObserver;
import nl.factorit.openobd.functionlauncher.Logger;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public abstract class BrokerStream {
    // Error that caused the stream to stop
    protected StreamEndingException closingError = null;

    /**
     * Gracefully stop the Stream
     */
    public abstract void stop();

    /**
     * Stream that captures all incoming messages from the Function Broker asynchronously, so it can be read synchronously
     */
    static class Incoming extends BrokerStream implements StreamObserver<FunctionUpdate> {
        private static final Logger logger = Logger.getLogger("BrokerStream:Incoming");

        private final Queue<FunctionUpdate> messages = new LinkedBlockingQueue<>(); // FIFO queue

        /**
         * Reads a message from the queue if any is available and removes is from the queue
         *
         * @return The oldest message in the queue, if any
         * @throws StreamEndingException Thrown when the stream was closed
         */
        public Optional<FunctionUpdate> receive() throws StreamEndingException {
            if (null != this.closingError) {
                throw this.closingError;
            }

            try {
                return Optional.of(this.messages.remove());
            } catch (NoSuchElementException e) {
                return Optional.empty();
            }
        }

        @Override
        public void stop() {
            logger.debug("Stopping stream");

            this.onCompleted();
        }

        @Override
        public void onNext(FunctionUpdate message) {
            logger.debug("Received message: %s - %s".formatted(message.getType().name(), message.getFunctionDataCase().name()));

            this.messages.add(message);
        }

        @Override
        public void onError(Throwable cause) {
            this.closingError = new StreamEndingException(cause);

            logger.error("Stream had an error and was closed", this.closingError);
        }

        @Override
        public void onCompleted() {
            logger.debug("Stream was completed");
        }
    }

    /**
     * Stream that is used to send all messages to the Function Broker asynchronously
     */
    static class Outgoing extends BrokerStream {
        private static final Logger logger = Logger.getLogger("BrokerStream:Outgoing");

        private final StreamObserver<FunctionUpdate> stream;

        public Outgoing(StreamObserver<FunctionUpdate> stream) {
            this.stream = stream;
        }

        public void send(OutgoingMessage<?> message) throws StreamEndingException {
            if (null != this.closingError) {
                throw this.closingError;
            }

            try {
                FunctionUpdate update = message.toFunctionUpdate();

                logger.debug("Sending function update request: %s".formatted(message));

                this.stream.onNext(update);
            } catch (IllegalStateException e) {
                this.closingError = new StreamEndingException(e);

                logger.error("Stream had an error and was closed", this.closingError);

                throw this.closingError;
            }
        }

        @Override
        public void stop() {
            logger.debug("Stopping Broker stream");

            this.stream.onCompleted();
        }
    }

    /**
     * Exception that should be thrown when a stream was closed due to an exception
     */
    public static class StreamEndingException extends RuntimeException {
        public StreamEndingException(Throwable cause) {
            super("Stream ended due to a %s: %s".formatted(cause.getClass(), cause.getMessage()), cause);
        }
    }
}
