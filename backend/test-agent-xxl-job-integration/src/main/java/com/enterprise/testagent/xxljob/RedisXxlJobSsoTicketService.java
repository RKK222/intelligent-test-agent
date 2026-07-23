package com.enterprise.testagent.xxljob;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.auth.TokenSessionMarkerStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Redis Lua 原子一次性 SSO 票据实现，键和值均不保存平台原始 Token。 */
@Component
public class RedisXxlJobSsoTicketService implements XxlJobSsoTicketService {

    private static final String KEY_PREFIX = "test-agent:xxl-job:sso-ticket:";
    private static final String TICKET_PATTERN = "[A-Za-z0-9_-]{43}";
    /** 使用 Redis 2.6 即支持的 Lua，兼容未提供 GETDEL 命令的企业 Redis 5/6.0。 */
    private static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if value then
                redis.call('DEL', KEYS[1])
            end
            return value
            """, String.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final XxlJobProperties properties;
    private final TokenSessionMarkerStore markerStore;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    public RedisXxlJobSsoTicketService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            XxlJobProperties properties,
            TokenSessionMarkerStore markerStore,
            Clock clock) {
        this(redisTemplate, objectMapper, properties, markerStore, clock, new SecureRandom());
    }

    RedisXxlJobSsoTicketService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            XxlJobProperties properties,
            TokenSessionMarkerStore markerStore,
            Clock clock,
            SecureRandom secureRandom) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.markerStore = Objects.requireNonNull(markerStore, "markerStore must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
    }

    @Override
    public XxlJobSsoTicketIssue issue(AuthPrincipal principal) {
        Objects.requireNonNull(principal, "principal must not be null");
        Instant now = clock.instant();
        Instant expiresAt = min(now.plus(properties.getSso().getTicketTtl()), principal.expiresAt());
        Duration ttl = Duration.between(now, expiresAt);
        if (ttl.isZero() || ttl.isNegative()) {
            throw new PlatformException(ErrorCode.UNAUTHENTICATED, "平台会话已过期");
        }
        String ticket = newTicket();
        TicketPayload payload = new TicketPayload(
                principal.userId().value(),
                principal.username(),
                markerStore.digest(principal.token()),
                principal.expiresAt(),
                expiresAt);
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + ticket, objectMapper.writeValueAsString(payload), ttl);
        } catch (JsonProcessingException exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "XXL-JOB 登录票据签发失败", Map.of(), exception);
        }
        return new XxlJobSsoTicketIssue(
                ticket,
                expiresAt,
                properties.getAdmin().getContextPath() + "/platform-sso/login");
    }

    @Override
    public Optional<XxlJobSsoIdentity> consume(String ticket) {
        if (ticket == null || !ticket.matches(TICKET_PATTERN)) {
            return Optional.empty();
        }
        String json = redisTemplate.execute(CONSUME_SCRIPT, List.of(KEY_PREFIX + ticket));
        if (json == null) {
            return Optional.empty();
        }
        try {
            TicketPayload payload = objectMapper.readValue(json, TicketPayload.class);
            if (!payload.ticketExpiresAt().isAfter(clock.instant())) {
                return Optional.empty();
            }
            return Optional.of(new XxlJobSsoIdentity(
                    payload.platformUserId(),
                    payload.displayName(),
                    payload.sessionDigest(),
                    payload.sessionExpiresAt()));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private String newTicket() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static Instant min(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private record TicketPayload(
            String platformUserId,
            String displayName,
            String sessionDigest,
            Instant sessionExpiresAt,
            Instant ticketExpiresAt) {
    }
}
