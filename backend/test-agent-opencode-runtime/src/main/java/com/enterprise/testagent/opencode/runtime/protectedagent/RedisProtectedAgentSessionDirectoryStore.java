package com.enterprise.testagent.opencode.runtime.protectedagent;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** Redis 映射使受保护会话在 Java 重启后仍能恢复服务器目录，缺失时不降级到用户本地绝对路径。 */
@Service
public class RedisProtectedAgentSessionDirectoryStore implements ProtectedAgentSessionDirectoryStore {

    private static final String KEY_PREFIX = "test-agent:protected-agent:session-directory:";
    private final StringRedisTemplate redisTemplate;

    public RedisProtectedAgentSessionDirectoryStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(String remoteSessionId, String directory, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + remoteSessionId, directory, ttl);
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public Optional<String> find(String remoteSessionId) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX + remoteSessionId));
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private PlatformException unavailable(RuntimeException cause) {
        return new PlatformException(
                ErrorCode.RUNTIME_STATE_UNAVAILABLE,
                "受保护 Agent 会话目录运行态不可用",
                java.util.Map.of(),
                cause);
    }
}
