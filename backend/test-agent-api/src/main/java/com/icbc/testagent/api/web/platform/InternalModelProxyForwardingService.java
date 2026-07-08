package com.icbc.testagent.api.web.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.configuration.InternalModelProvider;
import com.icbc.testagent.observability.TraceConstants;
import com.icbc.testagent.opencode.runtime.internalmodel.InternalModelProviderRegistry;
import com.icbc.testagent.opencode.runtime.internalmodel.InternalModelProxyRuntimeSettings;
import com.icbc.testagent.opencode.runtime.internalmodel.InternalModelThinkStreamConverter;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 内部模型代理转发服务，封装鉴权、供应商选择、上游鉴权注入和 SSE 思考内容转换。
 */
@Service
public class InternalModelProxyForwardingService {

    public static final String PROVIDER_HEADER = "X-ICBC-Model-Provider";
    public static final String UCID_HEADER = "ucid";

    private final InternalModelProviderRegistry registry;
    private final InternalModelProxyRuntimeSettings settings;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public InternalModelProxyForwardingService(
            InternalModelProviderRegistry registry,
            InternalModelProxyRuntimeSettings settings,
            ObjectMapper objectMapper) {
        this(registry, settings, WebClient.create(), objectMapper);
    }

    InternalModelProxyForwardingService(
            InternalModelProviderRegistry registry,
            InternalModelProxyRuntimeSettings settings,
            WebClient webClient,
            ObjectMapper objectMapper) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.webClient = Objects.requireNonNull(webClient, "webClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public Mono<ResponseEntity<Flux<String>>> forward(ServerWebExchange exchange, String body, String traceId) {
        validateProxyAuth(exchange);
        String providerId = exchange.getRequest().getHeaders().getFirst(PROVIDER_HEADER);
        InternalModelProvider provider = registry.requireProvider(providerId);
        validateModel(body);
        String targetUrl = targetUrl(provider.baseUrl(), downstreamPath(exchange), exchange.getRequest().getURI().getRawQuery());
        InternalModelThinkStreamConverter converter = new InternalModelThinkStreamConverter(objectMapper);
        return webClient.method(exchange.getRequest().getMethod() == null ? HttpMethod.POST : exchange.getRequest().getMethod())
                .uri(URI.create(targetUrl))
                .headers(headers -> applyForwardHeaders(headers, exchange, traceId))
                .body(BodyInserters.fromValue(body == null ? "" : body))
                .exchangeToMono(response -> Mono.just(ResponseEntity
                        .status(response.statusCode())
                        .headers(safeResponseHeaders(response.headers().asHttpHeaders()))
                        .body(convertSse(response.bodyToFlux(String.class), converter))));
    }

    private void validateProxyAuth(ServerWebExchange exchange) {
        String token = com.icbc.testagent.api.web.common.AuthWebSupport.extractBearerToken(exchange);
        if (!settings.matchesApiKey(token)) {
            throw new PlatformException(ErrorCode.UNAUTHENTICATED, "内部模型代理鉴权失败");
        }
    }

    private void validateModel(String body) {
        try {
            JsonNode root = objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
            JsonNode model = root.get("model");
            if (model == null || !model.isTextual() || model.asText().isBlank()) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "内部模型代理请求缺少 model");
            }
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "内部模型代理请求体不是合法 JSON");
        }
    }

    private void applyForwardHeaders(HttpHeaders headers, ServerWebExchange exchange, String traceId) {
        headers.setBearerAuth(registry.requireAuthToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(exchange.getRequest().getHeaders().getAccept());
        headers.set(TraceConstants.TRACE_ID_HEADER, traceId);
        String ucid = exchange.getRequest().getHeaders().getFirst(UCID_HEADER);
        if (ucid != null && !ucid.isBlank()) {
            headers.set(UCID_HEADER, ucid);
        }
    }

    private HttpHeaders safeResponseHeaders(HttpHeaders source) {
        HttpHeaders headers = new HttpHeaders();
        MediaType contentType = source.getContentType();
        if (contentType != null) {
            headers.setContentType(contentType);
        }
        return headers;
    }

    private Flux<String> convertSse(Flux<String> chunks, InternalModelThinkStreamConverter converter) {
        AtomicReference<String> carry = new AtomicReference<>("");
        return chunks.concatMapIterable(chunk -> {
            String text = carry.get() + (chunk == null ? "" : chunk);
            String[] lines = text.split("\\r?\\n", -1);
            carry.set(lines.length == 0 ? "" : lines[lines.length - 1]);
            List<String> converted = new ArrayList<>();
            for (int index = 0; index < lines.length - 1; index++) {
                converted.add(converter.convertLine(lines[index]) + "\n");
            }
            return converted;
        });
    }

    private String downstreamPath(ServerWebExchange exchange) {
        String fullPath = exchange.getRequest().getURI().getRawPath();
        String prefix = InternalModelProxyRuntimeSettings.PROXY_PATH;
        if (fullPath == null || !fullPath.startsWith(prefix)) {
            return "/";
        }
        String path = fullPath.substring(prefix.length());
        return path.isBlank() ? "/" : path;
    }

    private String targetUrl(String baseUrl, String path, String rawQuery) {
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return rawQuery == null || rawQuery.isBlank()
                ? normalizedBase + normalizedPath
                : normalizedBase + normalizedPath + "?" + rawQuery;
    }
}
