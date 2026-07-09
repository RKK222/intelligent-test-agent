package com.icbc.testagent.persistence;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.opencodeprocess.BackendRuntimeMetrics;
import com.icbc.testagent.domain.opencodeprocess.BackendRuntimeMetricSample;
import com.icbc.testagent.domain.opencodeprocess.BackendRuntimeSnapshot;
import com.icbc.testagent.domain.opencodeprocess.ContainerManagerId;
import com.icbc.testagent.domain.opencodeprocess.ContainerRuntimeMetrics;
import com.icbc.testagent.domain.opencodeprocess.ContainerRuntimeMetricSample;
import com.icbc.testagent.domain.opencodeprocess.LinuxServer;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerStatus;
import com.icbc.testagent.domain.opencodeprocess.ManagedOpencodeProcessSnapshot;
import com.icbc.testagent.domain.opencodeprocess.ManagerConnectionStatus;
import com.icbc.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainer;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerManager;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerStatus;
import com.icbc.testagent.domain.opencodeprocess.OpencodeManagerBackendConnection;
import com.icbc.testagent.domain.opencodeprocess.ServerRuntimeMetricSample;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

/**
 * 验证 Redis 运行快照的 key、索引和 TTL 约定，避免在线判定重新滑回数据库心跳。
 */
class RedisOpencodeProcessHeartbeatStoreTest {

    private static final Instant NOW = Instant.parse("2026-06-24T00:00:00Z");

    @Test
    void recordBackendSnapshotWritesSnapshotByBackendProcessAndServerHeartbeatWithTenSecondTtl() {
        RedisFixture fixture = RedisFixture.create();
        RedisOpencodeProcessHeartbeatStore store = new RedisOpencodeProcessHeartbeatStore(fixture.redisTemplate);

        store.recordBackendSnapshot(backendSnapshot());

        verify(fixture.values).set(
                eq("test-agent:runtime-snapshot:backend:bjp_1234567890abcdef"),
                contains("\"listenUrl\":\"http://10.8.0.12:8080\""),
                eq(Duration.ofSeconds(10)));
        verify(fixture.sets).add("test-agent:runtime-snapshot:index:backend", "bjp_1234567890abcdef");
        verify(fixture.values).set(
                eq("test-agent:runtime-heartbeat:backend:10.8.0.12"),
                eq(String.valueOf(NOW.toEpochMilli())),
                eq(Duration.ofSeconds(10)));
        verify(fixture.sets).add("test-agent:runtime-heartbeat:index:backend", "10.8.0.12");
    }

    @Test
    void recordBackendSnapshotKeepsMultipleJavaSnapshotsForSameServer() {
        RedisFixture fixture = RedisFixture.create();
        RedisOpencodeProcessHeartbeatStore store = new RedisOpencodeProcessHeartbeatStore(fixture.redisTemplate);

        store.recordBackendSnapshot(backendSnapshot(new BackendProcessId("bjp_1234567890abcdef")));
        store.recordBackendSnapshot(backendSnapshot(new BackendProcessId("bjp_2234567890abcdef")));

        verify(fixture.values).set(
                eq("test-agent:runtime-snapshot:backend:bjp_1234567890abcdef"),
                contains("\"backendProcessId\":{\"value\":\"bjp_1234567890abcdef\"}"),
                eq(Duration.ofSeconds(10)));
        verify(fixture.values).set(
                eq("test-agent:runtime-snapshot:backend:bjp_2234567890abcdef"),
                contains("\"backendProcessId\":{\"value\":\"bjp_2234567890abcdef\"}"),
                eq(Duration.ofSeconds(10)));
        verify(fixture.sets).add("test-agent:runtime-snapshot:index:backend", "bjp_1234567890abcdef");
        verify(fixture.sets).add("test-agent:runtime-snapshot:index:backend", "bjp_2234567890abcdef");
    }

    @Test
    void recordManagerSnapshotWritesSnapshotWithTenSecondTtl() {
        RedisFixture fixture = RedisFixture.create();
        RedisOpencodeProcessHeartbeatStore store = new RedisOpencodeProcessHeartbeatStore(fixture.redisTemplate);

        store.recordManagerSnapshot(managerSnapshot());

        verify(fixture.values).set(
                eq("test-agent:runtime-snapshot:manager:mgr_1234567890abcdef"),
                contains("\"currentProcesses\":2"),
                eq(Duration.ofSeconds(10)));
        verify(fixture.values).set(
                eq("test-agent:runtime-snapshot:manager:mgr_1234567890abcdef"),
                contains("\"startCommand\":\"XDG_DATA_HOME=/data/opencode/session/4096"),
                eq(Duration.ofSeconds(10)));
        verify(fixture.sets).add("test-agent:runtime-snapshot:index:manager", "mgr_1234567890abcdef");
    }

    @Test
    void recordRuntimeSnapshotsAppendMetricHistoryAndTrimFortyEightHours() {
        RedisFixture fixture = RedisFixture.create();
        RedisOpencodeProcessHeartbeatStore store = new RedisOpencodeProcessHeartbeatStore(fixture.redisTemplate);

        store.recordManagerSnapshot(managerSnapshotWithMetrics());
        store.recordBackendSnapshot(backendSnapshotWithMetrics());

        verify(fixture.zsets).add(
                eq("test-agent:runtime-metrics:container:ctr_01"),
                contains("\"cpuUsagePercent\":12.5"),
                eq((double) NOW.toEpochMilli()));
        verify(fixture.redisTemplate).expire(
                "test-agent:runtime-metrics:container:ctr_01",
                Duration.ofHours(49));
        verify(fixture.zsets).removeRangeByScore(
                "test-agent:runtime-metrics:container:ctr_01",
                0,
                NOW.minus(Duration.ofHours(48)).toEpochMilli() - 1);
        verify(fixture.zsets).add(
                eq("test-agent:runtime-metrics:server:10.8.0.12"),
                contains("\"diskUsagePercent\":25.0"),
                eq((double) NOW.toEpochMilli()));
        verify(fixture.zsets).add(
                eq("test-agent:runtime-metrics:server:10.8.0.12"),
                contains("\"memoryAvailableBytes\":1536"),
                eq((double) NOW.toEpochMilli()));
        verify(fixture.zsets).add(
                eq("test-agent:runtime-metrics:backend:10.8.0.12"),
                contains("\"jvmThreadsLive\":42"),
                eq((double) NOW.toEpochMilli()));
        verify(fixture.zsets).add(
                eq("test-agent:runtime-metrics:backend:10.8.0.12"),
                contains("\"jvmProcessResidentMemoryBytes\":700"),
                eq((double) NOW.toEpochMilli()));
        verify(fixture.zsets).removeRangeByScore(
                "test-agent:runtime-metrics:server:10.8.0.12",
                0,
                NOW.minus(Duration.ofHours(48)).toEpochMilli() - 1);
    }

    @Test
    void readsRuntimeMetricHistoryFromRedisSortedSets() {
        RedisFixture fixture = RedisFixture.create();
        RedisOpencodeProcessHeartbeatStore store = new RedisOpencodeProcessHeartbeatStore(fixture.redisTemplate);
        when(fixture.zsets.rangeByScore(
                "test-agent:runtime-metrics:container:ctr_01",
                NOW.minusSeconds(60).toEpochMilli(),
                NOW.toEpochMilli()))
                .thenReturn(java.util.Set.of("""
                        {"sampledAt":"2026-06-24T00:00:00Z","maxProcesses":5,"currentProcesses":2,"cpuUsagePercent":12.5,"memoryMaxBytes":1024,"memoryUsedBytes":512,"memoryUsagePercent":50.0,"diskReadBytesPerSecond":128.0,"diskWriteBytesPerSecond":256.0}
                        """));
        when(fixture.zsets.rangeByScore(
                "test-agent:runtime-metrics:server:10.8.0.12",
                NOW.minusSeconds(60).toEpochMilli(),
                NOW.toEpochMilli()))
                .thenReturn(java.util.Set.of("""
                        {"sampledAt":"2026-06-24T00:00:00Z","cpuUsagePercent":22.5,"cpuCoreCount":8,"loadAverage1m":1.5,"memoryMaxBytes":2048,"memoryTotalBytes":2048,"memoryAvailableBytes":1536,"memoryFreeBytes":1280,"memoryUsedBytes":512,"memoryUsagePercent":25.0,"memoryBuffersBytes":64,"memoryCachedBytes":256,"swapTotalBytes":1024,"swapFreeBytes":768,"swapUsedBytes":256,"swapUsagePercent":25.0,"diskMaxBytes":4096,"diskAvailableBytes":3072,"diskUsedBytes":1024,"diskUsagePercent":25.0,"futureField":"ignored"}
                        """));
        when(fixture.zsets.rangeByScore(
                "test-agent:runtime-metrics:backend:10.8.0.12",
                NOW.minusSeconds(60).toEpochMilli(),
                NOW.toEpochMilli()))
                .thenReturn(java.util.Set.of("""
                        {"sampledAt":"2026-06-24T00:00:00Z","jvmProcessCpuUsagePercent":7.5,"jvmProcessResidentMemoryBytes":700,"jvmMemoryUsedBytes":300,"jvmMemoryCommittedBytes":400,"jvmMemoryMaxBytes":500,"jvmHeapUsedBytes":200,"jvmNonHeapUsedBytes":100,"jvmGcPauseMillis":7,"jvmGcCollectionTimeDeltaMillis":7,"jvmGcCollectionCountDelta":3,"jvmThreadsLive":42}
                        """));

        List<ContainerRuntimeMetricSample> containerSamples = store.containerMetricSamples(
                new OpencodeContainerId("ctr_01"),
                NOW.minusSeconds(60),
                NOW);
        List<BackendRuntimeMetricSample> backendSamples = store.backendMetricSamples(
                new LinuxServerId("10.8.0.12"),
                NOW.minusSeconds(60),
                NOW);
        List<ServerRuntimeMetricSample> serverSamples = store.serverMetricSamples(
                new LinuxServerId("10.8.0.12"),
                NOW.minusSeconds(60),
                NOW);

        org.assertj.core.api.Assertions.assertThat(containerSamples)
                .extracting(ContainerRuntimeMetricSample::cpuUsagePercent)
                .containsExactly(12.5);
        org.assertj.core.api.Assertions.assertThat(serverSamples)
                .extracting(ServerRuntimeMetricSample::diskUsagePercent)
                .containsExactly(25.0);
        org.assertj.core.api.Assertions.assertThat(serverSamples)
                .extracting(ServerRuntimeMetricSample::memoryAvailableBytes)
                .containsExactly(1536L);
        org.assertj.core.api.Assertions.assertThat(backendSamples)
                .extracting(BackendRuntimeMetricSample::jvmThreadsLive)
                .containsExactly(42);
        org.assertj.core.api.Assertions.assertThat(backendSamples)
                .extracting(BackendRuntimeMetricSample::jvmProcessResidentMemoryBytes)
                .containsExactly(700L);
        org.assertj.core.api.Assertions.assertThat(backendSamples.getFirst().jvmThreadsDaemon()).isNull();
    }

    private static BackendRuntimeSnapshot backendSnapshot() {
        return backendSnapshot(new BackendProcessId("bjp_1234567890abcdef"));
    }

    private static BackendRuntimeSnapshot backendSnapshot(BackendProcessId backendProcessId) {
        LinuxServerId linuxServerId = new LinuxServerId("10.8.0.12");
        return new BackendRuntimeSnapshot(
                new LinuxServer(
                        linuxServerId,
                        "10.8.0.12",
                        LinuxServerStatus.READY,
                        Map.of("capacity", 4),
                        NOW,
                        NOW,
                        NOW,
                        "trace_1234567890abcdef"),
                new BackendJavaProcess(
                        backendProcessId,
                        linuxServerId,
                        "http://10.8.0.12:8080",
                        BackendJavaProcessStatus.READY,
                        NOW,
                        NOW,
                        NOW,
                        NOW,
                        "trace_1234567890abcdef"));
    }

    private static BackendRuntimeSnapshot backendSnapshotWithMetrics() {
        BackendRuntimeSnapshot snapshot = backendSnapshot();
        return new BackendRuntimeSnapshot(
                snapshot.linuxServer(),
                snapshot.backendProcess(),
                new BackendRuntimeMetrics(
                        22.5,
                        8,
                        1.5,
                        1.2,
                        0.8,
                        2048L,
                        2048L,
                        1536L,
                        1280L,
                        512L,
                        25.0,
                        64L,
                        256L,
                        1024L,
                        768L,
                        256L,
                        25.0,
                        4096L,
                        3072L,
                        1024L,
                        25.0,
                        7.5,
                        0.6,
                        123456789L,
                        700L,
                        900L,
                        4096L,
                        32L,
                        50L,
                        1024L,
                        300L,
                        400L,
                        500L,
                        200L,
                        300L,
                        400L,
                        100L,
                        100L,
                        100L,
                        2L,
                        16L,
                        32L,
                        1L,
                        8L,
                        16L,
                        7L,
                        7L,
                        3L,
                        0.4,
                        42,
                        12,
                        48,
                        1000L));
    }

    private static ManagerRuntimeSnapshot managerSnapshot() {
        LinuxServerId linuxServerId = new LinuxServerId("10.8.0.12");
        OpencodeContainerId containerId = new OpencodeContainerId("ctr_01");
        ContainerManagerId managerId = new ContainerManagerId("mgr_1234567890abcdef");
        BackendProcessId backendProcessId = new BackendProcessId("bjp_1234567890abcdef");
        return new ManagerRuntimeSnapshot(
                new OpencodeContainer(
                        containerId,
                        linuxServerId,
                        "opencode-a",
                        4096,
                        4100,
                        5,
                        2,
                        OpencodeContainerStatus.READY,
                        NOW,
                        NOW,
                        NOW,
                        "trace_1234567890abcdef"),
                new OpencodeContainerManager(
                        managerId,
                        containerId,
                        linuxServerId,
                        "opencode-manager.v1",
                        ManagerConnectionStatus.CONNECTED,
                        Map.of("commands", List.of("start", "health")),
                        NOW,
                        NOW,
                        NOW,
                        "trace_1234567890abcdef"),
                List.of(new OpencodeManagerBackendConnection(
                        managerId,
                        backendProcessId,
                        ManagerConnectionStatus.CONNECTED,
                        NOW,
                        NOW,
                        NOW,
                        "trace_1234567890abcdef")),
                null,
                List.of(new ManagedOpencodeProcessSnapshot(
                        4096,
                        12345L,
                        "http://10.8.0.12:4096",
                        "/data/opencode/session/4096",
                        "/data/opencode/.config/opencode/",
                        NOW,
                        "XDG_DATA_HOME=/data/opencode/session/4096 OPENCODE_CONFIG_DIR=/data/opencode/.config/opencode/ opencode serve --hostname 0.0.0.0 --port 4096 --print-logs",
                        "trace_process")));
    }

    private static ManagerRuntimeSnapshot managerSnapshotWithMetrics() {
        ManagerRuntimeSnapshot snapshot = managerSnapshot();
        return new ManagerRuntimeSnapshot(
                snapshot.container(),
                snapshot.manager(),
                snapshot.connections(),
                new ContainerRuntimeMetrics(
                        snapshot.container().portStart(),
                        snapshot.container().portEnd(),
                        snapshot.container().maxProcesses(),
                        snapshot.container().currentProcesses(),
                        "cgroup",
                        12.5,
                        1024L,
                        512L,
                        50.0,
                        128.0,
                        256.0),
                List.of());
    }

    private record RedisFixture(
            StringRedisTemplate redisTemplate,
            ValueOperations<String, String> values,
            SetOperations<String, String> sets,
            ZSetOperations<String, String> zsets) {

        @SuppressWarnings("unchecked")
        private static RedisFixture create() {
            StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
            ValueOperations<String, String> values = mock(ValueOperations.class);
            SetOperations<String, String> sets = mock(SetOperations.class);
            ZSetOperations<String, String> zsets = mock(ZSetOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(values);
            when(redisTemplate.opsForSet()).thenReturn(sets);
            when(redisTemplate.opsForZSet()).thenReturn(zsets);
            return new RedisFixture(redisTemplate, values, sets, zsets);
        }
    }
}
