package com.enterprise.testagent.xxljob;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.auth.TokenSessionMarkerStore;
import com.enterprise.testagent.domain.user.UserId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 使用真实 Redis 5 验证兼容旧版本的原子一次消费和票据 TTL。 */
@Testcontainers(disabledWithoutDocker = true)
class RedisXxlJobSsoTicketIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-20T00:00:00Z");

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:5.0.14-alpine"))
            .withExposedPorts(6379);

    @Test
    void realRedisConsumesTicketOnceAndKeepsSixtySecondUpperBound() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        try {
            StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
            redis.afterPropertiesSet();
            TokenSessionMarkerStore markers = new TokenSessionMarkerStore() {
                @Override
                public String digest(String token) {
                    return TokenSessionMarkerStore.sha256(token);
                }

                @Override
                public boolean isActive(String sessionDigest) {
                    return true;
                }
            };
            XxlJobProperties properties = new XxlJobProperties();
            RedisXxlJobSsoTicketService service = new RedisXxlJobSsoTicketService(
                    redis,
                    new ObjectMapper().findAndRegisterModules(),
                    properties,
                    markers,
                    Clock.fixed(NOW, ZoneOffset.UTC));
            AuthPrincipal principal = new AuthPrincipal(
                    "real-redis-platform-token",
                    new UserId("usr_real_redis_admin"),
                    "平台管理员",
                    "AUTH_REAL_REDIS",
                    List.of("SUPER_ADMIN"),
                    NOW,
                    NOW.plus(Duration.ofHours(1)));

            XxlJobSsoTicketIssue issue = service.issue(principal);
            Long ttl = redis.getExpire("test-agent:xxl-job:sso-ticket:" + issue.ticket());

            assertThat(ttl).isNotNull().isPositive().isLessThanOrEqualTo(60L);
            assertThat(service.consume(issue.ticket())).isPresent();
            assertThat(service.consume(issue.ticket())).isEmpty();
        } finally {
            connectionFactory.destroy();
        }
    }
}
