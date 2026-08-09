package com.enterprise.testagent.persistence;

import com.enterprise.testagent.domain.memory.MemoryHmacNonceStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;

/** HMAC nonce 的 Redis 原子占位；key 只保留 clientId+nonce 的 SHA-256 摘要。 */
public final class RedisMemoryHmacNonceStore implements MemoryHmacNonceStore {
    private static final String PREFIX = "testagent:memory:hmac-nonce:";
    private final StringRedisTemplate redis;

    public RedisMemoryHmacNonceStore(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis);
    }

    @Override
    public boolean claim(String clientId, String nonce, Duration ttl) {
        Boolean claimed = redis.opsForValue().setIfAbsent(
                PREFIX + digest(clientId + ":" + nonce), "1", ttl);
        return Boolean.TRUE.equals(claimed);
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
