package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.integration.lobehub.LobehubModelIdentity;
import com.enterprise.testagent.integration.lobehub.LobehubSsoService;
import com.enterprise.testagent.model.gateway.ModelGatewayCaller;
import com.enterprise.testagent.model.gateway.ModelGatewayCatalogService;
import com.enterprise.testagent.model.gateway.ModelGatewayForwarder;
import com.enterprise.testagent.model.gateway.ModelGatewayModelView;
import com.enterprise.testagent.memory.MemoryModelHmacAuthenticator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 供 LobeHub 和记忆服务使用受控用户身份调用的 OpenAI-compatible 企业模型入口。 */
@RestController
public class ModelGatewayController {

    public static final String BASE_PATH = "/api/internal/platform/model-gateway/v1";
    public static final String MODELS_PATH = BASE_PATH + "/models";
    static final int MAX_JSON_REQUEST_BODY_BYTES = 16 * 1024 * 1024;

    private final LobehubSsoService ssoService;
    private final ModelGatewayCatalogService catalogService;
    private final ModelGatewayForwarder forwardingService;
    private MemoryModelHmacAuthenticator memoryHmac;

    @Autowired
    public ModelGatewayController(
            LobehubSsoService ssoService,
            ModelGatewayCatalogService catalogService,
            ModelGatewayForwarder forwardingService) {
        this.ssoService = Objects.requireNonNull(ssoService);
        this.catalogService = Objects.requireNonNull(catalogService);
        this.forwardingService = Objects.requireNonNull(forwardingService);
    }

    /** 返回已启用且能力探测成功的动态目录，不暴露内部供应商路由。 */
    @GetMapping(MODELS_PATH)
    public Mono<OpenAiModelList> models(ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
                    authenticate(exchange);
                    List<OpenAiModel> models = catalogService.listModels().stream()
                            .map(ModelGatewayController::toOpenAiModel)
                            .toList();
                    return new OpenAiModelList("list", models);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** JSON 模态统一使用公开模型 ID；HMAC 必须在读取原始正文后校验 body digest。 */
    @PostMapping(path = {
            BASE_PATH + "/chat/completions",
            BASE_PATH + "/responses",
            BASE_PATH + "/embeddings",
            BASE_PATH + "/rerank",
            BASE_PATH + "/images/generations",
            BASE_PATH + "/audio/speech"
    }, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Void> proxyJson(ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        long length = exchange.getRequest().getHeaders().getContentLength();
        if (length > MAX_JSON_REQUEST_BODY_BYTES) {
            return Mono.error(payloadTooLarge());
        }
        return readBody(exchange)
                .flatMap(body -> Mono.fromCallable(() -> authenticate(exchange, body))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(identity -> Mono.fromCallable(() -> forwardingService.prepare(exchange, body))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(prepared -> {
                                    if (identity.memoryIdentity() != null) {
                                        memoryHmac.requireModel(identity.memoryIdentity(), prepared.publicModelId());
                                    }
                                    return forwardingService.forward(
                                            exchange,
                                            prepared,
                                            new ModelGatewayCaller(
                                                    identity.userId(), identity.unifiedAuthId(), identity.source()),
                                            traceId);
                                })));
    }

    /** multipart 由 WebFlux 写入受限临时文件后流式转发，平台不持久化音频内容。 */
    @PostMapping(path = BASE_PATH + "/audio/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Void> proxyTranscription(ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> authenticate(exchange))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(identity -> exchange.getMultipartData()
                        .flatMap(parts -> Mono.fromCallable(() -> forwardingService.prepareMultipart(exchange, parts))
                                .subscribeOn(Schedulers.boundedElastic()))
                        .flatMap(prepared -> forwardingService.forwardMultipart(
                                exchange,
                                prepared,
                                new ModelGatewayCaller(identity.userId(), identity.unifiedAuthId(), identity.source()),
                                traceId)));
    }

    private GatewayIdentity authenticate(ServerWebExchange exchange) {
        return authenticate(exchange, new byte[0]);
    }

    private GatewayIdentity authenticate(ServerWebExchange exchange, byte[] body) {
        if (memoryHmac != null && memoryHmac.supports(exchange)) {
            MemoryModelHmacAuthenticator.Identity identity = memoryHmac.authenticate(exchange, body);
            return new GatewayIdentity(
                    identity.userId(), identity.unifiedAuthId(), "memory", identity);
        }
        String grant = AuthWebSupport.extractBearerToken(exchange);
        LobehubModelIdentity identity = ssoService.authenticateModelGrant(grant);
        return new GatewayIdentity(identity.userId(), identity.unifiedAuthId(), "lobehub", null);
    }

    /** 可选方法注入保持关闭记忆能力的装配和既有 Controller 单元测试稳定。 */
    @Autowired(required = false)
    void setMemoryHmac(MemoryModelHmacAuthenticator memoryHmac) {
        this.memoryHmac = memoryHmac;
    }

    private Mono<byte[]> readBody(ServerWebExchange exchange) {
        return DataBufferUtils.join(exchange.getRequest().getBody(), MAX_JSON_REQUEST_BODY_BYTES)
                .map(buffer -> {
                    try {
                        byte[] body = new byte[buffer.readableByteCount()];
                        buffer.read(body);
                        return body;
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                })
                .onErrorMap(DataBufferLimitException.class, ignored -> payloadTooLarge())
                .defaultIfEmpty(new byte[0]);
    }

    private PlatformException payloadTooLarge() {
        return new PlatformException(
                ErrorCode.PAYLOAD_TOO_LARGE,
                "企业模型请求体超过 16 MiB 上限",
                Map.of("maxBytes", MAX_JSON_REQUEST_BODY_BYTES));
    }

    private static OpenAiModel toOpenAiModel(ModelGatewayModelView model) {
        return new OpenAiModel(
                model.id(),
                "model",
                0L,
                "enterprise",
                model.displayName(),
                model.contextLimit(),
                model.capabilities().stream().map(capability -> capability.name().toLowerCase()).sorted().toList());
    }

    private record GatewayIdentity(
            String userId,
            String unifiedAuthId,
            String source,
            MemoryModelHmacAuthenticator.Identity memoryIdentity) {
    }

    /** OpenAI 模型目录外壳，保持企业适配器可直接消费。 */
    public record OpenAiModelList(String object, List<OpenAiModel> data) {
    }

    /** providerId 永不进入外部模型对象。 */
    public record OpenAiModel(
            String id,
            String object,
            long created,
            @JsonProperty("owned_by") String ownedBy,
            String name,
            @JsonProperty("context_window") Long contextWindow,
            List<String> capabilities) {
    }
}
