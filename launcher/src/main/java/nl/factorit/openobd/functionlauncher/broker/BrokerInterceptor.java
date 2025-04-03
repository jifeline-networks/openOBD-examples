package nl.factorit.openobd.functionlauncher.broker;

import io.grpc.*;
import io.grpc.stub.MetadataUtils;
import nl.factorit.openobd.functionlauncher.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A small wrapper around gRPCs HeaderAttachingClientInterceptor so it'll keep updating its Bearer token.
 * It's based on the MetadataUtils.newCaptureMetadataInterceptor, but for some reason that doesn't seem to add the
 * headers like I thought it would...

 * see MetadataUtils.HeaderAttachingClientInterceptor
 * see MetadataUtils.MetadataCapturingClientInterceptor
 */
public class BrokerInterceptor implements ClientInterceptor {
    private static final Logger logger = Logger.getLogger("BrokerInterceptor");

    private final AtomicReference<List<Header>> headers;

    /**
     * Constructor that will always use the latest values in the given header
     *
     * @param headers A reference to metadata that can be updated after the fact
     */
    public BrokerInterceptor(AtomicReference<List<Header>> headers) {
        this.headers = headers;
    }

    /**
     * Constructor that simply adds static headers to all requests
     *
     * @param headers The headers to add to requests, which will NOT update if you change the values in the map
     */
    public BrokerInterceptor(List<Header> headers) {
        this.headers = new AtomicReference<>(headers);
    }

    public static BrokerInterceptor withBearerToken(String token) {
        return new BrokerInterceptor(List.of(
                Header.fromBearerToken(token)
        ));
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions, Channel channel) {
        Metadata metadata = new Metadata();

        this.headers.get().forEach(header -> {
            logger.debug("Adding header to request: %s".formatted(header.key));

            metadata.put(
                    Metadata.Key.of(header.key, Metadata.ASCII_STRING_MARSHALLER),
                    header.value
            );
        });

        return MetadataUtils.newAttachHeadersInterceptor(metadata)
                .interceptCall(methodDescriptor, callOptions, channel);
    }

    /**
     * Value object representing a HTTP Header
     *
     * @param key name of the Header (i.e. Authorization)
     * @param value value of the Header (i.e. a Bearer token)
     */
    public record Header(String key, String value) {
        public static final String AUTHORIZATION_HEADER = "Authorization";

        /**
         * Named constructor specifically to create a Bearer token header
         *
         * @param token token to use (without the 'Bearer' prefix)
         * @return Header representing an Authorization header with a Bearer token
         */
        public static Header fromBearerToken(String token) {
            return new Header(AUTHORIZATION_HEADER, "Bearer %s".formatted(token));
        }
    }
}
