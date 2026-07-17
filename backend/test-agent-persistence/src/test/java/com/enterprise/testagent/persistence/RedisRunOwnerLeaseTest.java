package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunOwnerLease;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/** owner lease 的本地截止时间必须保守，不得晚于 Redis 真实 TTL。 */
class RedisRunOwnerLeaseTest {

    private static final Instant NOW = Instant.parse("2026-07-11T06:00:00Z");

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void claimComputesLocalExpiryFromTimeBeforeRedisRoundTrip() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        MutableClock clock = new MutableClock(NOW);
        when(redis.execute(any(RedisScript.class), anyList(), any(), any())).thenAnswer(invocation -> {
            clock.advance(Duration.ofSeconds(4));
            return 1L;
        });
        RedisRunRuntimeStore store = new RedisRunRuntimeStore(redis, new ObjectMapper(), clock);

        RunOwnerLease lease = store.claimOwnerLease(new RunId("run_owner_request_time"), "backend-a")
                .orElseThrow();

        assertThat(lease.expiresAt()).isEqualTo(NOW.plusSeconds(15));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void renewComputesLocalExpiryFromTimeBeforeRedisRoundTrip() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        MutableClock clock = new MutableClock(NOW);
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any())).thenAnswer(invocation -> {
            clock.advance(Duration.ofSeconds(4));
            return 1L;
        });
        RedisRunRuntimeStore store = new RedisRunRuntimeStore(redis, new ObjectMapper(), clock);
        RunOwnerLease current = new RunOwnerLease(
                new RunId("run_owner_renew_request_time"), "backend-a", 9L, NOW.plusSeconds(2));

        RunOwnerLease renewed = store.renewOwnerLease(current).orElseThrow();

        assertThat(renewed.expiresAt()).isEqualTo(NOW.plusSeconds(15));
    }

    @Test
    void fencedLuaScriptsCheckOwnerBeforeAnyRuntimeMutation() throws ReflectiveOperationException {
        String append = script("APPEND_SCRIPT");
        String project = script("PROJECT_SCRIPT");
        String bind = script("BIND_REMOTE_SESSION_SCRIPT");
        String saveScope = script("SAVE_SCOPE_SCRIPT");
        String claimRaw = script("CLAIM_RAW_EVENT_SCRIPT");
        String appendPending = script("APPEND_PENDING_SCRIPT");
        String drainPending = script("DRAIN_PENDING_SCRIPT");
        String conditionalClaim = script("CLAIM_OWNER_LEASE_IF_UNCHANGED_SCRIPT");

        assertThat(append.indexOf("ARGV[26] == '1'"))
                .isBetween(0, append.indexOf("'lastSeq', 1"));
        assertThat(project.indexOf("ARGV[26] == '1'"))
                .isBetween(0, project.indexOf("'runtimeVersion', 1"));
        assertThat(bind.indexOf("ARGV[4] == '1'"))
                .isBetween(0, bind.indexOf("'rootRemoteSessionId'"));
        assertThat(saveScope.indexOf("ARGV[7] == '1'"))
                .isBetween(0, saveScope.indexOf("local previousScope"));
        assertThat(claimRaw.indexOf("ARGV[3] == '1'"))
                .isBetween(0, claimRaw.indexOf("HSETNX"));
        assertThat(appendPending.indexOf("ARGV[3] == '1'"))
                .isBetween(0, appendPending.indexOf("RPUSH"));
        assertThat(appendPending)
                .contains("detailBytes", "updatedAt", "detailsExpiresAt", "ARGV[6]")
                .containsSubsequence("if nextDetailBytes >", "return -3", "RPUSH");
        assertThat(drainPending.indexOf("ARGV[1] == '1'"))
                .isBetween(0, drainPending.indexOf("LRANGE"));
        assertThat(drainPending)
                .contains("detailBytes", "releasedBytes", "SREM");
        assertThat(conditionalClaim)
                .contains("status ~= 'PENDING'", "unchanged('lastSeq'", "unchanged('detailBytes'",
                        "unchanged('attentionEventId'", "unchanged('updatedAt'", "HINCRBY");
    }

    @Test
    void terminalLuaPublishesVersionedProjectionOutboxBeforeReturning() throws ReflectiveOperationException {
        String append = script("APPEND_SCRIPT");
        String project = script("PROJECT_SCRIPT");
        String acknowledge = script("ACK_TERMINAL_PROJECTION_SCRIPT");

        assertTerminalOutboxBeforeReturn(append);
        assertTerminalOutboxBeforeReturn(project);
        assertThat(acknowledge)
                .contains("terminalProjectionPending", "terminalProjectionVersion", "ARGV[1]")
                .containsSubsequence("terminalProjectionVersion", "~= ARGV[1]", "terminalProjectionPending', '0'");
    }

    private void assertTerminalOutboxBeforeReturn(String script) {
        int pending = script.indexOf("'terminalProjectionPending', '1'");
        int returned = script.lastIndexOf("return cjson.encode");
        assertThat(pending).isGreaterThanOrEqualTo(0).isLessThan(returned);
        assertThat(script)
                .contains("terminalProjectionVersion", "terminalProjectionStatus", "terminalProjectionSource",
                        "terminalProjectionReasonCode", "terminalProjectionSafeErrorMessage",
                        "terminalProjectionRemoteStopConfirmed", "terminalProjectionTraceId",
                        "terminalProjectionOccurredAt");
    }

    private String script(String fieldName) throws ReflectiveOperationException {
        Field field = RedisRunRuntimeStore.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        RedisScript<?> script = (RedisScript<?>) field.get(null);
        return script.getScriptAsString();
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
