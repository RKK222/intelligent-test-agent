package com.icbc.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.domain.configuration.CommonParameterLoadSnapshot;
import com.icbc.testagent.domain.configuration.LoadedParameter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * 验证通用参数加载快照的 Redis key、索引与 TTL 约定，以及读取时跳过过期键的行为。
 */
class RedisCommonParameterLoadSnapshotStoreTest {

    private static final Instant NOW = Instant.parse("2026-06-27T00:00:00Z");
    private static final String BACKEND_ID = "bjp_1234567890abcdef";

    @Test
    void recordWritesSnapshotWithThirtySecondTtlAndIndex() {
        RedisFixture fixture = RedisFixture.create();
        RedisCommonParameterLoadSnapshotStore store = new RedisCommonParameterLoadSnapshotStore(fixture.redisTemplate);

        store.record(snapshot(BACKEND_ID, "srv-a"));

        verify(fixture.values).set(
                eq("test-agent:common-param-snapshot:backend:" + BACKEND_ID),
                contains("\"backendProcessId\":\"" + BACKEND_ID + "\""),
                eq(Duration.ofSeconds(30)));
        verify(fixture.sets).add("test-agent:common-param-snapshot:index:backend", BACKEND_ID);
    }

    @Test
    void liveSnapshotsSkipsExpiredAndRemovesIndex() {
        RedisFixture fixture = RedisFixture.create();
        RedisCommonParameterLoadSnapshotStore store = new RedisCommonParameterLoadSnapshotStore(fixture.redisTemplate);
        when(fixture.sets.members("test-agent:common-param-snapshot:index:backend"))
                .thenReturn(Set.of(BACKEND_ID, "bjp_other"));
        when(fixture.redisTemplate.hasKey("test-agent:common-param-snapshot:backend:" + BACKEND_ID))
                .thenReturn(false);
        // 另一个键已过期：get 返回 null，应从索引移除。
        when(fixture.values.get("test-agent:common-param-snapshot:backend:bjp_other")).thenReturn(null);

        List<CommonParameterLoadSnapshot> result = store.liveSnapshots();

        assertThat(result).isEmpty();
        verify(fixture.sets).remove("test-agent:common-param-snapshot:index:backend", "bjp_other");
    }

    @Test
    void liveSnapshotsReturnsParsedSnapshotsSortedByBackendProcessId() {
        RedisFixture fixture = RedisFixture.create();
        RedisCommonParameterLoadSnapshotStore store = new RedisCommonParameterLoadSnapshotStore(fixture.redisTemplate);
        when(fixture.sets.members("test-agent:common-param-snapshot:index:backend"))
                .thenReturn(Set.of("bjp_b", "bjp_a"));
        when(fixture.values.get("test-agent:common-param-snapshot:backend:bjp_a"))
                .thenReturn("""
                        {"backendProcessId":"bjp_a","linuxServerId":"srv-a","listenUrl":"http://a:8080","instanceId":"inst-a","loadedAt":"2026-06-27T00:00:00Z","parameters":[]}
                        """);
        when(fixture.values.get("test-agent:common-param-snapshot:backend:bjp_b"))
                .thenReturn("""
                        {"backendProcessId":"bjp_b","linuxServerId":"srv-b","listenUrl":"http://b:8080","instanceId":"inst-b","loadedAt":"2026-06-27T00:00:00Z","parameters":[]}
                        """);

        List<CommonParameterLoadSnapshot> result = store.liveSnapshots();

        assertThat(result).extracting(CommonParameterLoadSnapshot::backendProcessId)
                .containsExactly("bjp_a", "bjp_b");
    }

    private static CommonParameterLoadSnapshot snapshot(String backendProcessId, String linuxServerId) {
        return new CommonParameterLoadSnapshot(
                backendProcessId,
                linuxServerId,
                "http://" + linuxServerId + ":8080",
                "inst-" + linuxServerId,
                NOW,
                List.of(new LoadedParameter("OPENCODE_MANAGER_MAX_PROCESSES", "all", "8", "8", false, null)));
    }

    private record RedisFixture(
            StringRedisTemplate redisTemplate,
            ValueOperations<String, String> values,
            SetOperations<String, String> sets) {

        @SuppressWarnings("unchecked")
        private static RedisFixture create() {
            StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
            ValueOperations<String, String> values = mock(ValueOperations.class);
            SetOperations<String, String> sets = mock(SetOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(values);
            when(redisTemplate.opsForSet()).thenReturn(sets);
            when(redisTemplate.hasKey(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
            return new RedisFixture(redisTemplate, values, sets);
        }
    }
}
