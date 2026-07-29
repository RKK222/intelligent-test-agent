package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.lobehub.LobehubGrantPayload;
import com.enterprise.testagent.domain.lobehub.LobehubTicketPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 使用真实 Redis 5 验证 LobeHub 票据、nonce 和委托 Lua 的原子兼容性及前缀隔离。 */
@Testcontainers(disabledWithoutDocker = true)
class RedisLobehubSsoStoreIntegrationTest {

    private static final String FIRST_DIGEST = "a".repeat(64);
    private static final String SECOND_DIGEST = "b".repeat(64);
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-29T00:00:00Z");

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:5.0.14-alpine"))
            .withExposedPorts(6379);

    @Test
    void realRedisConsumesOnceRotatesGrantAndKeepsEveryKeyInTheDedicatedPrefix() throws Exception {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        try {
            StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
            redis.afterPropertiesSet();
            RedisLobehubSsoStore store = new RedisLobehubSsoStore(
                    redis,
                    new ObjectMapper().findAndRegisterModules());
            store.saveTicket(
                    FIRST_DIGEST,
                    new LobehubTicketPayload("usr_real_redis_lobehub", EXPIRES_AT),
                    Duration.ofSeconds(60));

            assertThat(redis.getExpire("test-agent:lobehub-sso:ticket:" + FIRST_DIGEST))
                    .isNotNull()
                    .isPositive()
                    .isLessThanOrEqualTo(60L);
            assertThat(concurrentConsumeCount(store, FIRST_DIGEST)).isEqualTo(1);
            assertThat(store.reserveNonce(FIRST_DIGEST, Duration.ofSeconds(120))).isTrue();
            assertThat(store.reserveNonce(FIRST_DIGEST, Duration.ofSeconds(120))).isFalse();

            store.rotateGrant(
                    "usr_real_redis_lobehub",
                    FIRST_DIGEST,
                    grant(EXPIRES_AT),
                    Duration.ofDays(30));
            store.rotateGrant(
                    "usr_real_redis_lobehub",
                    SECOND_DIGEST,
                    grant(EXPIRES_AT.plusSeconds(1)),
                    Duration.ofDays(30));
            assertThat(store.findGrant(FIRST_DIGEST)).isEmpty();
            assertThat(store.findGrant(SECOND_DIGEST)).isPresent();

            store.revokeGrant(SECOND_DIGEST);
            assertThat(store.findGrant(SECOND_DIGEST)).isEmpty();
            Set<String> keys = redis.keys("*");
            assertThat(keys).allMatch(key -> key.startsWith("test-agent:lobehub-sso:"));
            assertThat(keys).noneMatch(key -> key.contains("usr_real_redis_lobehub"));
        } finally {
            connectionFactory.destroy();
        }
    }

    private static int concurrentConsumeCount(RedisLobehubSsoStore store, String digest) throws Exception {
        int workers = 8;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int index = 0; index < workers; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    return store.consumeTicket(digest).isPresent();
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int consumed = 0;
            for (Future<Boolean> result : results) {
                if (result.get(5, TimeUnit.SECONDS)) {
                    consumed++;
                }
            }
            return consumed;
        } finally {
            executor.shutdownNow();
        }
    }

    private static LobehubGrantPayload grant(Instant expiresAt) {
        return new LobehubGrantPayload(
                "usr_real_redis_lobehub",
                "lobehub",
                "model-gateway",
                expiresAt);
    }
}
