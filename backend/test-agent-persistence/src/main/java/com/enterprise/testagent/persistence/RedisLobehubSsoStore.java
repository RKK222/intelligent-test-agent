package com.enterprise.testagent.persistence;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.lobehub.LobehubGrantPayload;
import com.enterprise.testagent.domain.lobehub.LobehubSsoStore;
import com.enterprise.testagent.domain.lobehub.LobehubTicketPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** Redis 中的 LobeHub SSO 票据、nonce 与模型委托存储实现。 */
public class RedisLobehubSsoStore implements LobehubSsoStore {

    private static final String PREFIX = "test-agent:lobehub-sso:";
    private static final String TICKET_PREFIX = PREFIX + "ticket:";
    private static final String NONCE_PREFIX = PREFIX + "nonce:";
    private static final String GRANT_PREFIX = PREFIX + "grant:";
    private static final String USER_GRANT_PREFIX = PREFIX + "user-grant:";
    private static final String DIGEST_PATTERN = "[a-f0-9]{64}";

    /** 使用 Redis 2.6 已支持的 Lua 代替 GETDEL，兼容企业 Redis 5/6。 */
    private static final DefaultRedisScript<String> CONSUME_TICKET_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if value then
                redis.call('DEL', KEYS[1])
            end
            return value
            """, String.class);

    /** 同一原子操作删除旧委托并更新用户反向索引。 */
    private static final DefaultRedisScript<String> ROTATE_GRANT_SCRIPT = new DefaultRedisScript<>("""
            local old = redis.call('GET', KEYS[1])
            if old then
                redis.call('DEL', ARGV[1] .. old)
            end
            redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3])
            redis.call('SET', KEYS[1], ARGV[4], 'PX', ARGV[3])
            return old
            """, String.class);

    /** 删除委托后仅在反向索引仍指向它时一并删除，避免误删已轮换的新委托。 */
    private static final DefaultRedisScript<Long> REVOKE_GRANT_SCRIPT = new DefaultRedisScript<>("""
            redis.call('DEL', KEYS[1])
            if redis.call('GET', KEYS[2]) == ARGV[1] then
                redis.call('DEL', KEYS[2])
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 注入平台共享 Redis 模板和 JSON 序列化器。 */
    public RedisLobehubSsoStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public void saveTicket(String ticketDigest, LobehubTicketPayload payload, Duration ttl) {
        redisTemplate.opsForValue().set(
                TICKET_PREFIX + requireDigest(ticketDigest),
                serialize(payload),
                requirePositiveTtl(ttl));
    }

    @Override
    public Optional<LobehubTicketPayload> consumeTicket(String ticketDigest) {
        String json = redisTemplate.execute(
                CONSUME_TICKET_SCRIPT,
                List.of(TICKET_PREFIX + requireDigest(ticketDigest)));
        return deserialize(json, LobehubTicketPayload.class);
    }

    @Override
    public boolean reserveNonce(String nonceDigest, Duration ttl) {
        Boolean reserved = redisTemplate.opsForValue().setIfAbsent(
                NONCE_PREFIX + requireDigest(nonceDigest),
                "1",
                requirePositiveTtl(ttl));
        return Boolean.TRUE.equals(reserved);
    }

    @Override
    public void rotateGrant(
            String userId,
            String grantDigest,
            LobehubGrantPayload payload,
            Duration ttl) {
        String digest = requireDigest(grantDigest);
        Duration validTtl = requirePositiveTtl(ttl);
        redisTemplate.execute(
                ROTATE_GRANT_SCRIPT,
                List.of(userGrantKey(userId), GRANT_PREFIX + digest),
                GRANT_PREFIX,
                serialize(payload),
                Long.toString(validTtl.toMillis()),
                digest);
    }

    @Override
    public Optional<LobehubGrantPayload> findGrant(String grantDigest) {
        String json = redisTemplate.opsForValue().get(GRANT_PREFIX + requireDigest(grantDigest));
        return deserialize(json, LobehubGrantPayload.class);
    }

    @Override
    public void revokeGrant(String grantDigest) {
        String digest = requireDigest(grantDigest);
        String grantKey = GRANT_PREFIX + digest;
        Optional<LobehubGrantPayload> payload = deserialize(
                redisTemplate.opsForValue().get(grantKey),
                LobehubGrantPayload.class);
        if (payload.isEmpty()) {
            redisTemplate.delete(grantKey);
            return;
        }
        redisTemplate.execute(
                REVOKE_GRANT_SCRIPT,
                List.of(grantKey, userGrantKey(payload.get().userId())),
                digest);
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new PlatformException(
                    ErrorCode.INTERNAL_ERROR,
                    "LobeHub SSO 状态序列化失败",
                    java.util.Map.of(),
                    exception);
        }
    }

    private <T> Optional<T> deserialize(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, type));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static String userGrantKey(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        return USER_GRANT_PREFIX + sha256(userId);
    }

    private static String requireDigest(String digest) {
        if (digest == null || !digest.matches(DIGEST_PATTERN)) {
            throw new IllegalArgumentException("digest must be a lowercase SHA-256 value");
        }
        return digest;
    }

    private static Duration requirePositiveTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        return ttl;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide SHA-256", exception);
        }
    }
}
