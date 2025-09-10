package nl.factorit.openobd.functionlauncher.broker.communication;


import com.jifeline.OpenOBD.FunctionBroker.Messages.*;

/**
 * Value object for a message that will be sent to the Function Broker, via a gRPC stream
 *
 * @param <T> The type of message that will be sent
 */
public abstract class OutgoingMessage<T> {
    protected T message;
    protected String messageDescription;

    /**
     * Function that constructs the FunctionUpdate's builder which will create the Function Update send to the Function Broker
     *
     * @param builder The builder that will be used to create the actual message send via the gRPC stream
     */
    protected abstract void addPayload(FunctionUpdate.Builder builder);

    public FunctionUpdate toFunctionUpdate() {
        FunctionUpdate.Builder builder = FunctionUpdate.newBuilder();

        this.addPayload(builder);

        return builder.build();
    }

    /**
     * @return Description of the OutgoingMessage
     */
    public String toString() {
        return this.messageDescription;
    }

    /**
     * Message to request a FunctionCall, which should execute an openOBD function with the Function Broker
     */
    public static class FunctionCallMessage extends OutgoingMessage<FunctionCall> {
        public FunctionCallMessage(FunctionCall message) {
            this.message = message;
            this.messageDescription = "Execution message for %s".formatted(this.message.getId());
        }

        @Override
        protected void addPayload(FunctionUpdate.Builder builder) {
            builder.setType(FunctionUpdateType.FUNCTION_UPDATE_TYPE_REQUEST)
                    .setFunctionCall(this.message);
        }
    }

    /**
     * Message to request a FunctionRegistration, which should register an openOBD function with the Function Broker
     */
    public static class FunctionRegistrationMessage extends OutgoingMessage<FunctionRegistration> {
        public FunctionRegistrationMessage(FunctionRegistration message) {
            this.message = message;
            this.messageDescription = "Registration message for %s, %s".formatted(this.message.getDetails().getId(), this.message.getState());
        }

        @Override
        protected void addPayload(FunctionUpdate.Builder builder) {
            builder.setType(FunctionUpdateType.FUNCTION_UPDATE_TYPE_REQUEST)
                    .setFunctionRegistration(this.message);
        }
    }

    /**
     * Message to ping a FunctionRegistration, to prevent the stream from closing
     */
    public static class FunctionBrokerTokenMessage extends OutgoingMessage<FunctionBrokerToken> {
        public FunctionBrokerTokenMessage(FunctionBrokerToken message) {
            this.message = message;
        }

        @Override
        protected void addPayload(FunctionUpdate.Builder builder) {
            builder.setType(FunctionUpdateType.FUNCTION_UPDATE_TYPE_RESPONSE)
                    .setResponseDescription("Updated Broker token")
                    .setFunctionBrokerToken(this.message);
        }
    }

    /**
     * Value object for a Response to an OutgoingMessage
     *
     * @param <T> The type of OutgoingMessage this is a response to
     */
    public abstract static class Response<T> extends OutgoingMessage<T> {
        protected FunctionUpdateResponse responseType;
        protected String responseDescription;
    }

    /**
     * Response to a FunctionCallMessage containing the state of the function
     */
    public static class FunctionCallResponse extends Response<FunctionCallMessage> {
        public FunctionCallResponse(FunctionCall message, FunctionUpdateResponse responseType, String responseDescription) {
            this.responseType = responseType;
            this.responseDescription = responseDescription;
            this.message = new FunctionCallMessage(message);
            this.messageDescription = "%s response (%s) for %s".formatted(this.responseType, this.responseDescription, this.message);
        }

        @Override
        protected void addPayload(FunctionUpdate.Builder builder) {
            this.message.addPayload(builder);

            builder.setType(FunctionUpdateType.FUNCTION_UPDATE_TYPE_RESPONSE)
                    .setResponse(this.responseType)
                    .setResponseDescription(this.responseDescription);
        }
    }
}
