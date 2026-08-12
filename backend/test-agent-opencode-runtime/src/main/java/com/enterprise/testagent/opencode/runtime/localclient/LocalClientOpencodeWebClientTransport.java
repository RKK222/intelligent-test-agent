package com.enterprise.testagent.opencode.runtime.localclient;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.runtime.RuntimeKind;
import com.enterprise.testagent.localclient.protocol.LocalClientFrame;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameCodec;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameType;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.enterprise.testagent.localclient.protocol.LocalClientProtocol;
import com.enterprise.testagent.opencode.client.OpencodeWebClientTransport;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** generated SDK 的本地 OpenCode HTTP/SSE ExchangeFunction，所有字节经固定 generation 反向隧道。 */
@org.springframework.stereotype.Component
public class LocalClientOpencodeWebClientTransport implements OpencodeWebClientTransport {

    private static final int MAX_REQUEST_BODY_BYTES = 1024 * 1024;
    private static final Set<String> BLOCKED_REQUEST_HEADERS = Set.of(
            "authorization", "proxy-authorization", "proxy-authenticate", "cookie", "host", "connection",
            "content-length", "transfer-encoding", "upgrade", "keep-alive", "te", "trailer");

    private final LocalClientTunnelGateway tunnelGateway;
    private final LocalClientFrameCodec codec = new LocalClientFrameCodec();

    public LocalClientOpencodeWebClientTransport(LocalClientTunnelGateway tunnelGateway) {
        this.tunnelGateway = tunnelGateway;
    }

    @Override
    public boolean supports(ExecutionNode node) {
        return node != null && node.runtimeKind() == RuntimeKind.LOCAL_CLIENT;
    }

    @Override
    public WebClient create(ExecutionNode node, String traceId, int maxInMemorySize) {
        if (!supports(node)) {
            throw new IllegalArgumentException("local transport only supports LOCAL_CLIENT nodes");
        }
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(maxInMemorySize))
                .build();
        return WebClient.builder()
                .exchangeStrategies(strategies)
                .exchangeFunction(request -> encodeRequest(request, strategies)
                        .flatMap(encoded -> exchange(node, traceId, request, encoded, strategies)))
                .build();
    }

    private Mono<ClientResponse> exchange(
            ExecutionNode node,
            String traceId,
            ClientRequest request,
            EncodedRequest encoded,
            ExchangeStrategies strategies) {
        boolean streaming = request.headers().getAccept().stream()
                .anyMatch(mediaType -> MediaType.TEXT_EVENT_STREAM.isCompatibleWith(mediaType));
        URI uri = request.url();
        String pathAndQuery = uri.getRawPath()
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        LocalClientPayloads.HttpRequest payload = new LocalClientPayloads.HttpRequest(
                request.method().name(),
                pathAndQuery,
                requestHeaders(encoded.headers()),
                encoded.body().length == 0 ? null : Base64.getEncoder().encodeToString(encoded.body()),
                streaming);
        LocalClientInstanceId clientInstanceId = new LocalClientInstanceId(node.localClientInstanceId());
        long generation = node.connectionGeneration();
        if (!streaming) {
            return tunnelGateway.request(
                            clientInstanceId,
                            generation,
                            LocalClientFrameType.HTTP_REQUEST,
                            payload,
                            traceId,
                            LocalClientProtocol.LONG_REQUEST_TIMEOUT)
                    .map(frame -> nonStreamingResponse(frame, strategies));
        }
        Flux<LocalClientFrame> frames = tunnelGateway.stream(
                clientInstanceId,
                generation,
                LocalClientFrameType.HTTP_REQUEST,
                payload,
                traceId,
                Duration.ofSeconds(60));
        return frames.switchOnFirst((signal, connectedFrames) -> {
                    if (!signal.hasValue()) {
                        return signal.hasError()
                                ? Flux.error(signal.getThrowable())
                                : Flux.empty();
                    }
                    LocalClientFrame first = signal.get();
                    if (first.type() != LocalClientFrameType.STREAM_OPEN) {
                        return Flux.error(new PlatformException(
                                ErrorCode.OPENCODE_BAD_GATEWAY,
                                "本地 OpenCode SSE 未返回流式响应"));
                    }
                    LocalClientPayloads.StreamOpen open = codec.payload(first, LocalClientPayloads.StreamOpen.class);
                    Flux<DataBuffer> body = connectedFrames.skip(1)
                            .takeUntil(frame -> frame.type() == LocalClientFrameType.STREAM_END)
                            .handle((frame, sink) -> {
                                if (frame.type() == LocalClientFrameType.STREAM_CHUNK) {
                                    LocalClientPayloads.BinaryChunk chunk = codec.payload(
                                            frame, LocalClientPayloads.BinaryChunk.class);
                                    sink.next(DefaultDataBufferFactory.sharedInstance.wrap(
                                            Base64.getDecoder().decode(chunk.dataBase64())));
                                } else if (frame.type() != LocalClientFrameType.STREAM_END) {
                                    sink.error(new PlatformException(
                                            ErrorCode.OPENCODE_BAD_GATEWAY,
                                            "本地 OpenCode SSE 帧类型无效"));
                                }
                            });
                    return Flux.just(responseBuilder(open.status(), open.headers(), strategies)
                            .body(body)
                            .build());
                })
                .next();
    }

    private ClientResponse nonStreamingResponse(LocalClientFrame frame, ExchangeStrategies strategies) {
        if (frame.type() != LocalClientFrameType.HTTP_RESPONSE) {
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "本地 OpenCode HTTP 响应类型无效");
        }
        LocalClientPayloads.HttpResponse response = codec.payload(frame, LocalClientPayloads.HttpResponse.class);
        byte[] body = response.bodyBase64() == null || response.bodyBase64().isBlank()
                ? new byte[0]
                : Base64.getDecoder().decode(response.bodyBase64());
        return responseBuilder(response.status(), response.headers(), strategies)
                .body(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(body)))
                .build();
    }

    private ClientResponse.Builder responseBuilder(
            int status,
            Map<String, List<String>> headers,
            ExchangeStrategies strategies) {
        ClientResponse.Builder builder = ClientResponse.create(HttpStatusCode.valueOf(status), strategies);
        if (headers != null) {
            headers.forEach((name, values) -> {
                if (name != null && values != null) {
                    values.forEach(value -> builder.header(name, value));
                }
            });
        }
        return builder;
    }

    private Mono<EncodedRequest> encodeRequest(ClientRequest request, ExchangeStrategies strategies) {
        CollectingClientHttpRequest collector = new CollectingClientHttpRequest(
                request.method(), request.url(), MAX_REQUEST_BODY_BYTES);
        return request.writeTo(collector, strategies)
                .then(Mono.fromSupplier(() -> new EncodedRequest(
                        HttpHeaders.readOnlyHttpHeaders(collector.getHeaders()), collector.bytes())));
    }

    private Map<String, List<String>> requestHeaders(HttpHeaders headers) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        headers.forEach((name, values) -> {
            if (name != null && !BLOCKED_REQUEST_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                result.put(name, List.copyOf(values));
            }
        });
        return Map.copyOf(result);
    }

    private record EncodedRequest(HttpHeaders headers, byte[] body) {
    }

    /** 让 WebClient 的 BodyInserter 按正常 codec 编码，再有界收集为隧道请求体。 */
    private static final class CollectingClientHttpRequest implements ClientHttpRequest {

        private final HttpMethod method;
        private final URI uri;
        private final int maxBytes;
        private final HttpHeaders headers = new HttpHeaders();
        private final MultiValueMap<String, HttpCookie> cookies = new LinkedMultiValueMap<>();
        private final Map<String, Object> attributes = new LinkedHashMap<>();
        private final List<Supplier<? extends Mono<Void>>> beforeCommit = new ArrayList<>();
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private boolean committed;

        private CollectingClientHttpRequest(HttpMethod method, URI uri, int maxBytes) {
            this.method = method;
            this.uri = uri;
            this.maxBytes = maxBytes;
        }

        @Override
        public HttpMethod getMethod() {
            return method;
        }

        @Override
        public URI getURI() {
            return uri;
        }

        @Override
        public MultiValueMap<String, HttpCookie> getCookies() {
            return cookies;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return attributes;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getNativeRequest() {
            return (T) this;
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public DataBufferFactory bufferFactory() {
            return DefaultDataBufferFactory.sharedInstance;
        }

        @Override
        public void beforeCommit(Supplier<? extends Mono<Void>> action) {
            beforeCommit.add(action);
        }

        @Override
        public boolean isCommitted() {
            return committed;
        }

        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            return commitActions().thenMany(Flux.from(body)
                            .concatMap(buffer -> Mono.fromRunnable(() -> append(buffer))))
                    .then(Mono.fromRunnable(() -> committed = true));
        }

        @Override
        public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
            return writeWith(Flux.from(body).concatMap(inner -> inner));
        }

        @Override
        public Mono<Void> setComplete() {
            return commitActions().then(Mono.fromRunnable(() -> committed = true));
        }

        private Mono<Void> commitActions() {
            return Flux.fromIterable(beforeCommit).concatMap(Supplier::get).then();
        }

        private void append(DataBuffer buffer) {
            try {
                int readable = buffer.readableByteCount();
                if (output.size() + readable > maxBytes) {
                    throw new PlatformException(
                            ErrorCode.VALIDATION_ERROR,
                            "本地 OpenCode 请求体超过 1 MiB，首版不支持附件");
                }
                byte[] bytes = new byte[readable];
                buffer.read(bytes);
                output.writeBytes(bytes);
            } finally {
                DataBufferUtils.release(buffer);
            }
        }

        private byte[] bytes() {
            return output.toByteArray();
        }
    }
}
