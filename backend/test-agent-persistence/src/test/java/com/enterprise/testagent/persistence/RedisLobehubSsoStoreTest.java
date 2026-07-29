package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.lobehub.LobehubGrantPayload;
import com.enterprise.testagent.domain.lobehub.LobehubTicketPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

class RedisLobehubSsoStoreTest {

    private static final Duration TTL = Duration.ofSeconds(60);
    private static final String DIGEST = "a".repeat(64);
    private static final Instant EXPIRES_AT = Instant.parse("2026-07-30T01:01:00Z");

    private final CapturingRedisTemplate redisTemplate = new CapturingRedisTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private RedisLobehubSsoStore store;

    @BeforeEach
    void setUp() {
        store = new RedisLobehubSsoStore(redisTemplate, objectMapper);
    }

    @Test
    void storesOnlyDigestKeysAndConsumesTicketWithAtomicLua() throws Exception {
        LobehubTicketPayload payload = new LobehubTicketPayload("usr_lobehub", EXPIRES_AT);
        store.saveTicket(DIGEST, payload, TTL);

        assertThat(redisTemplate.lastValueKey).isEqualTo("test-agent:lobehub-sso:ticket:" + DIGEST);
        assertThat(redisTemplate.lastValue).doesNotContain("opaque-ticket");
        assertThat(redisTemplate.lastTtl).isEqualTo(TTL);

        redisTemplate.scriptResult = objectMapper.writeValueAsString(payload);
        assertThat(store.consumeTicket(DIGEST)).contains(payload);

        assertThat(redisTemplate.lastScript.getScriptAsString()).contains("GET", "DEL");
        assertThat(redisTemplate.lastKeys).containsExactly("test-agent:lobehub-sso:ticket:" + DIGEST);
    }

    @Test
    void reservesNonceWithSetIfAbsentAndDigestOnlyKey() {
        redisTemplate.setIfAbsentResults.add(true);
        redisTemplate.setIfAbsentResults.add(false);

        assertThat(store.reserveNonce(DIGEST, Duration.ofSeconds(120))).isTrue();
        assertThat(store.reserveNonce(DIGEST, Duration.ofSeconds(120))).isFalse();

        assertThat(redisTemplate.setIfAbsentKeys).containsExactly(
                "test-agent:lobehub-sso:nonce:" + DIGEST,
                "test-agent:lobehub-sso:nonce:" + DIGEST);
        assertThat(redisTemplate.lastTtl).isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    void rotatesAndRevokesGrantWithAtomicUserIndexScripts() throws Exception {
        LobehubGrantPayload payload = new LobehubGrantPayload(
                "usr_lobehub", "lobehub", "model-gateway", EXPIRES_AT.plus(Duration.ofDays(30)));

        store.rotateGrant("usr_lobehub", DIGEST, payload, Duration.ofDays(30));

        assertThat(redisTemplate.lastScript.getScriptAsString()).contains("DEL", "SET", "PX");
        assertThat(redisTemplate.lastKeys).allSatisfy(key ->
                assertThat(key).doesNotContain("usr_lobehub"));
        assertThat(redisTemplate.lastArguments).hasSize(4);

        String grantKey = "test-agent:lobehub-sso:grant:" + DIGEST;
        redisTemplate.values.put(grantKey, objectMapper.writeValueAsString(payload));
        assertThat(store.findGrant(DIGEST)).contains(payload);

        redisTemplate.scriptResult = 1L;
        store.revokeGrant(DIGEST);

        assertThat(redisTemplate.lastScript.getScriptAsString()).contains("GET", "DEL");
        assertThat(redisTemplate.lastKeys).allSatisfy(key ->
                assertThat(key).doesNotContain("usr_lobehub"));
    }

    /** 不建立网络连接，只捕获存储器向 Spring Redis 发出的完整命令。 */
    private static final class CapturingRedisTemplate extends StringRedisTemplate {
        private final Map<String, String> values = new HashMap<>();
        private final ArrayDeque<Boolean> setIfAbsentResults = new ArrayDeque<>();
        private final java.util.ArrayList<String> setIfAbsentKeys = new java.util.ArrayList<>();
        private final ValueOperations<String, String> valueOperations;
        private String lastValueKey;
        private String lastValue;
        private Duration lastTtl;
        private RedisScript<?> lastScript;
        private List<String> lastKeys = List.of();
        private List<Object> lastArguments = List.of();
        private Object scriptResult;

        @SuppressWarnings("unchecked")
        private CapturingRedisTemplate() {
            valueOperations = (ValueOperations<String, String>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(),
                    new Class<?>[]{ValueOperations.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "set" -> {
                            lastValueKey = (String) arguments[0];
                            lastValue = (String) arguments[1];
                            lastTtl = (Duration) arguments[2];
                            values.put(lastValueKey, lastValue);
                            yield null;
                        }
                        case "setIfAbsent" -> {
                            lastValueKey = (String) arguments[0];
                            lastValue = (String) arguments[1];
                            lastTtl = (Duration) arguments[2];
                            setIfAbsentKeys.add(lastValueKey);
                            yield setIfAbsentResults.removeFirst();
                        }
                        case "get" -> values.get((String) arguments[0]);
                        case "toString" -> "CapturingValueOperations";
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        @Override
        public ValueOperations<String, String> opsForValue() {
            return valueOperations;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            this.lastScript = script;
            this.lastKeys = List.copyOf(keys);
            this.lastArguments = Arrays.asList(args.clone());
            return (T) scriptResult;
        }

        @Override
        public Boolean delete(String key) {
            return values.remove(key) != null;
        }
    }
}
