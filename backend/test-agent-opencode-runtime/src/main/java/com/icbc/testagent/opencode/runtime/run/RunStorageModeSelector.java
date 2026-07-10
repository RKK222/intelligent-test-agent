package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.domain.run.ConversationRunContext;
import com.icbc.testagent.domain.run.RunStorageMode;
import com.icbc.testagent.domain.user.UserId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 为新 Run 选择一次固定的存储模式。灰度只对已校验上下文且带幂等请求号的新客户端生效，
 * 同一用户使用稳定哈希桶，避免重试或多 Java 节点选择不同模式。
 */
@Service
public class RunStorageModeSelector {

    private final ConversationContextProperties properties;

    public RunStorageModeSelector(ConversationContextProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public RunStorageMode select(
            UserId userId,
            StartRunInput input,
            ConversationRunContext context) {
        Objects.requireNonNull(input, "input must not be null");
        if (!properties.isEnabled()
                || properties.getRolloutPercentage() == 0
                || userId == null
                || context == null
                || input.contextToken() == null
                || input.clientRequestId() == null) {
            return RunStorageMode.LEGACY_FULL;
        }
        return bucket(userId) < properties.getRolloutPercentage()
                ? RunStorageMode.REDIS_SUMMARY
                : RunStorageMode.LEGACY_FULL;
    }

    /** 返回 0..99 的稳定用户桶；只使用 SHA-256 前两字节即可满足灰度均匀性。 */
    int bucket(UserId userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(userId.value().getBytes(StandardCharsets.UTF_8));
            return (((digest[0] & 0xff) << 8) | (digest[1] & 0xff)) % 100;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }
}
