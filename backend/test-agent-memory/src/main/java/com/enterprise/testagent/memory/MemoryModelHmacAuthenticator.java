package com.enterprise.testagent.memory;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.ModelCapability;
import com.enterprise.testagent.domain.memory.MemoryHmacNonceStore;
import com.enterprise.testagent.domain.memory.MemorySettings;
import com.enterprise.testagent.domain.memory.QaMemoryRepository;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.model.gateway.ModelGatewayCatalogService;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

/** 校验 Mem0→Java HMAC，绑定 path、正文摘要、用户、Run、Session、operation 和能力。 */
@Service
public class MemoryModelHmacAuthenticator {
    private static final String CHAT_PATH = "/api/internal/platform/model-gateway/v1/chat/completions";
    private static final String EMBEDDING_PATH = "/api/internal/platform/model-gateway/v1/embeddings";

    private final QaMemoryProperties properties;
    private final MemoryHmacNonceStore nonces;
    private final UserRepository users;
    private final QaMemoryRepository settingsRepository;
    private final ModelGatewayCatalogService catalog;
    private final Clock clock;

    @Autowired
    public MemoryModelHmacAuthenticator(
            QaMemoryProperties properties,
            MemoryHmacNonceStore nonces,
            UserRepository users,
            QaMemoryRepository settingsRepository,
            ModelGatewayCatalogService catalog) {
        this(properties, nonces, users, settingsRepository, catalog, Clock.systemUTC());
    }

    MemoryModelHmacAuthenticator(
            QaMemoryProperties properties,
            MemoryHmacNonceStore nonces,
            UserRepository users,
            QaMemoryRepository settingsRepository,
            ModelGatewayCatalogService catalog,
            Clock clock) {
        this.properties = Objects.requireNonNull(properties);
        this.nonces = Objects.requireNonNull(nonces);
        this.users = Objects.requireNonNull(users);
        this.settingsRepository = Objects.requireNonNull(settingsRepository);
        this.catalog = Objects.requireNonNull(catalog);
        this.clock = Objects.requireNonNull(clock);
    }

    public boolean supports(ServerWebExchange exchange) {
        return exchange.getRequest().getHeaders().getFirst("X-Memory-Signature") != null
                || exchange.getRequest().getHeaders().getFirst("X-Memory-Client-Id") != null;
    }

    public Identity authenticate(ServerWebExchange exchange, byte[] body) {
        if (!properties.isEnabled()) {
            throw unauthenticated();
        }
        String clientId = header(exchange, "X-Memory-Client-Id", 64);
        String userId = header(exchange, "X-Memory-User-Id", 128);
        String runId = header(exchange, "X-Memory-Run-Id", 128);
        String sessionId = header(exchange, "X-Memory-Session-Id", 128);
        // 与 memory-service REST 和独立库 memory_operations.operation_id 的 varchar(128)
        // 保持一致，避免超长幂等键通过网关后才在 PostgreSQL 层失败。
        String operationId = header(exchange, "X-Memory-Operation-Id", 128);
        String timestampText = header(exchange, "X-Memory-Timestamp", 20);
        String nonce = header(exchange, "X-Memory-Nonce", 64);
        String suppliedDigest = header(exchange, "X-Memory-Body-SHA256", 64).toLowerCase(Locale.ROOT);
        String capabilityText = header(exchange, "X-Memory-Capability", 16).toUpperCase(Locale.ROOT);
        String signature = header(exchange, "X-Memory-Signature", 64).toLowerCase(Locale.ROOT);
        if (!properties.getModelGatewayHmacClientId().equals(clientId)
                || !nonce.matches("[a-f0-9]{32}")
                || !suppliedDigest.matches("[a-f0-9]{64}")
                || !signature.matches("[a-f0-9]{64}")) {
            throw unauthenticated();
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampText);
        } catch (NumberFormatException exception) {
            throw unauthenticated();
        }
        Instant issuedAt = Instant.ofEpochSecond(timestamp);
        DurationWindow window = new DurationWindow(clock.instant(), properties.getModelGatewayHmacClockSkew());
        if (!window.contains(issuedAt)) {
            throw unauthenticated();
        }
        String path = exchange.getRequest().getURI().getRawPath();
        ModelCapability capability = switch (capabilityText) {
            case "CHAT" -> ModelCapability.CHAT;
            case "EMBEDDING" -> ModelCapability.EMBEDDING;
            default -> throw unauthenticated();
        };
        if (capability == ModelCapability.CHAT && !CHAT_PATH.equals(path)
                || capability == ModelCapability.EMBEDDING && !EMBEDDING_PATH.equals(path)) {
            throw unauthenticated();
        }
        String embeddingInputType = exchange.getRequest().getHeaders()
                .getFirst("X-Embedding-Input-Type");
        if (capability == ModelCapability.EMBEDDING) {
            if (!"query".equals(embeddingInputType) && !"document".equals(embeddingInputType)) {
                throw unauthenticated();
            }
        } else if (embeddingInputType != null) {
            throw unauthenticated();
        }
        String actualDigest = digest(body);
        if (!constantEquals(actualDigest, suppliedDigest)) {
            throw unauthenticated();
        }
        String canonical = String.join("\n",
                "POST", path, actualDigest, clientId, userId, runId, sessionId,
                operationId, timestampText, nonce, capability.name(),
                embeddingInputType == null ? "" : embeddingInputType);
        if (!constantEquals(hmac(canonical), signature)
                || !nonces.claim(clientId, nonce, properties.getModelGatewayNonceTtl())) {
            throw unauthenticated();
        }
        User user = users.findByUserId(new UserId(userId)).filter(User::canLogin)
                .orElseThrow(this::unauthenticated);
        return new Identity(userId, user.unifiedAuthId(), runId, sessionId, operationId, capability);
    }

    public void requireModel(Identity identity, String modelId) {
        MemorySettings settings = settingsRepository.loadSettings();
        if (identity.capability() == ModelCapability.CHAT) {
            if (!Objects.equals(settings.primaryChatModelId(), modelId)) {
                throw forbidden();
            }
            catalog.resolve(modelId, ModelCapability.CHAT);
            return;
        }
        boolean cpu = Objects.equals(settings.cpuEmbeddingModelId(), modelId);
        boolean enterprise = settings.primaryEmbeddingModelId() != null
                && Objects.equals(settings.primaryEmbeddingModelId(), modelId);
        if (!cpu && !enterprise) {
            throw forbidden();
        }
        var resolved = catalog.resolve(modelId, ModelCapability.EMBEDDING);
        Integer dimension = resolved.model().embeddingDimension();
        if (dimension == null || dimension <= 0 || cpu && dimension != 512) {
            throw new PlatformException(ErrorCode.CONFLICT, "记忆 embedding 模型维度配置不完整");
        }
    }

    private String header(ServerWebExchange exchange, String name, int maximum) {
        String value = exchange.getRequest().getHeaders().getFirst(name);
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw unauthenticated();
        }
        return value;
    }

    private String hmac(String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.requireModelGatewayHmacSecret(), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("HmacSHA256 unavailable", exception);
        }
    }

    private String digest(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private boolean constantEquals(String first, String second) {
        return MessageDigest.isEqual(
                first.getBytes(StandardCharsets.US_ASCII), second.getBytes(StandardCharsets.US_ASCII));
    }

    private PlatformException unauthenticated() {
        return new PlatformException(ErrorCode.UNAUTHENTICATED, "记忆模型网关 HMAC 无效");
    }

    private PlatformException forbidden() {
        return new PlatformException(ErrorCode.FORBIDDEN, "记忆模型不在当前设置允许范围内");
    }

    public record Identity(
            String userId,
            String unifiedAuthId,
            String runId,
            String sessionId,
            String operationId,
            ModelCapability capability) {
    }

    private record DurationWindow(Instant now, java.time.Duration skew) {
        boolean contains(Instant value) {
            return !value.isBefore(now.minus(skew)) && !value.isAfter(now.plus(skew));
        }
    }
}
