package com.enterprise.testagent.memory;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.ModelCapability;
import com.enterprise.testagent.domain.memory.MemoryLearningJob;
import com.enterprise.testagent.domain.memory.MemoryModelGrantPayload;
import com.enterprise.testagent.domain.memory.MemoryModelGrantStore;
import com.enterprise.testagent.domain.memory.MemorySettings;
import com.enterprise.testagent.domain.memory.QaMemoryRepository;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.model.gateway.ModelGatewayCatalogService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 选择已探测 CHAT 模型并签发绑定用户、Run、模型的一次性 `mfg_` 授权。 */
@Service
public class MemoryModelGrantService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final QaMemoryRepository memories;
    private final MemoryModelGrantStore grants;
    private final UserRepository users;
    private final ModelGatewayCatalogService catalog;
    private final QaMemoryProperties properties;
    private final Clock clock;

    @Autowired
    public MemoryModelGrantService(
            QaMemoryRepository memories,
            MemoryModelGrantStore grants,
            UserRepository users,
            ModelGatewayCatalogService catalog,
            QaMemoryProperties properties) {
        this(memories, grants, users, catalog, properties, Clock.systemUTC());
    }

    MemoryModelGrantService(
            QaMemoryRepository memories,
            MemoryModelGrantStore grants,
            UserRepository users,
            ModelGatewayCatalogService catalog,
            QaMemoryProperties properties,
            Clock clock) {
        this.memories = Objects.requireNonNull(memories);
        this.grants = Objects.requireNonNull(grants);
        this.users = Objects.requireNonNull(users);
        this.catalog = Objects.requireNonNull(catalog);
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 固定模型优先；只有当前 Run 模型仍在内部目录且 CHAT 探测成功时才允许回退。 */
    public Optional<String> selectModel(MemoryLearningJob job) {
        MemorySettings settings = memories.loadSettings();
        if (availableChatModel(settings.primaryChatModelId())) {
            return Optional.of(settings.primaryChatModelId().trim());
        }
        if (settings.currentRunModelFallbackEnabled() && availableChatModel(job.selectedModelId())) {
            return Optional.of(job.selectedModelId().trim());
        }
        return Optional.empty();
    }

    public IssuedGrant issue(String userId, String runId, String modelId) {
        // 签发瞬间再次解析，避免目录或探测状态在选择后变化。
        catalog.resolve(modelId, ModelCapability.CHAT);
        User user = users.findByUserId(new UserId(userId))
                .filter(User::canLogin)
                .orElseThrow(() -> unauthenticated("记忆抽取用户不存在或已停用"));
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.getGrantTtl());
        for (int attempt = 0; attempt < 3; attempt++) {
            String token = token();
            MemoryModelGrantPayload payload = new MemoryModelGrantPayload(
                    "mfgid_" + UUID.randomUUID().toString().replace("-", ""),
                    userId, user.unifiedAuthId(), runId,
                    modelId, issuedAt, expiresAt);
            if (grants.save(digest(token), payload, properties.getGrantTtl())) {
                return new IssuedGrant(token, modelId, expiresAt);
            }
        }
        throw new PlatformException(ErrorCode.MEMORY_UNAVAILABLE, "记忆抽取模型授权签发失败");
    }

    /** 网关认证时一次性消费；Header、Token 与 payload 任一不匹配都返回统一未认证。 */
    public GrantIdentity authenticate(String token, String userId, String runId) {
        if (token == null || !token.matches("mfg_[A-Za-z0-9_-]{20,256}")) {
            throw unauthenticated("记忆抽取模型授权无效");
        }
        MemoryModelGrantPayload payload = grants.consume(digest(token))
                .orElseThrow(() -> unauthenticated("记忆抽取模型授权无效或已使用"));
        if (!payload.userId().equals(userId)
                || !payload.runId().equals(runId)
                || !payload.expiresAt().isAfter(clock.instant())) {
            throw unauthenticated("记忆抽取模型授权边界不匹配或已过期");
        }
        return new GrantIdentity(
                payload.userId(), payload.unifiedAuthId(), payload.runId(), payload.modelId());
    }

    public void requireModel(GrantIdentity identity, String modelId) {
        if (identity == null || modelId == null || !identity.modelId().equals(modelId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "记忆抽取模型不在授权范围内");
        }
    }

    private boolean availableChatModel(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return false;
        }
        try {
            catalog.resolve(modelId.trim(), ModelCapability.CHAT);
            return true;
        } catch (PlatformException unavailable) {
            return false;
        }
    }

    private String token() {
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        return "mfg_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private String digest(String token) {
        try {
            byte[] value = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private PlatformException unauthenticated(String message) {
        return new PlatformException(ErrorCode.UNAUTHENTICATED, message);
    }

    public record IssuedGrant(String token, String modelId, Instant expiresAt) {
        @Override
        public String toString() {
            return "IssuedGrant[token=<redacted>, modelId=" + modelId + ", expiresAt=" + expiresAt + "]";
        }
    }

    public record GrantIdentity(String userId, String unifiedAuthId, String runId, String modelId) {
    }
}
