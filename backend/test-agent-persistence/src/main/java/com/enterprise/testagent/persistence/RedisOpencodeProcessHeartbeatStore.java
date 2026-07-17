package com.enterprise.testagent.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.opencodeprocess.BackendRuntimeMetricSample;
import com.enterprise.testagent.domain.opencodeprocess.BackendRuntimeMetrics;
import com.enterprise.testagent.domain.opencodeprocess.BackendRuntimeSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.ContainerRuntimeMetricSample;
import com.enterprise.testagent.domain.opencodeprocess.ContainerRuntimeMetrics;
import com.enterprise.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.domain.opencodeprocess.ServerRuntimeMetricSample;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 运行进程心跳存储；Java/manager 快照 10 秒过期，opencode 进程健康心跳保留 5 分钟窗口。
 */
public class RedisOpencodeProcessHeartbeatStore implements OpencodeProcessHeartbeatStore {

    private static final Duration RUNTIME_SNAPSHOT_TTL = Duration.ofSeconds(10);
    private static final Duration OPENCODE_HEARTBEAT_TTL = Duration.ofMinutes(5);
    private static final Duration METRICS_HISTORY_RETENTION = Duration.ofHours(48);
    private static final Duration METRICS_HISTORY_KEY_TTL = Duration.ofHours(49);
    private static final String BACKEND_KEY_PREFIX = "test-agent:runtime-heartbeat:backend:";
    private static final String OPENCODE_KEY_PREFIX = "test-agent:runtime-heartbeat:opencode:";
    private static final String BACKEND_SNAPSHOT_KEY_PREFIX = "test-agent:runtime-snapshot:backend:";
    private static final String MANAGER_SNAPSHOT_KEY_PREFIX = "test-agent:runtime-snapshot:manager:";
    private static final String CONTAINER_METRICS_KEY_PREFIX = "test-agent:runtime-metrics:container:";
    private static final String BACKEND_METRICS_KEY_PREFIX = "test-agent:runtime-metrics:backend:";
    private static final String SERVER_METRICS_KEY_PREFIX = "test-agent:runtime-metrics:server:";
    private static final String BACKEND_INDEX_KEY = "test-agent:runtime-heartbeat:index:backend";
    private static final String OPENCODE_INDEX_KEY = "test-agent:runtime-heartbeat:index:opencode";
    private static final String BACKEND_SNAPSHOT_INDEX_KEY = "test-agent:runtime-snapshot:index:backend";
    private static final String MANAGER_SNAPSHOT_INDEX_KEY = "test-agent:runtime-snapshot:index:manager";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 注入 Redis 字符串模板；value 保存 epoch millis，TTL 才是活跃判定主依据。
     */
    public RedisOpencodeProcessHeartbeatStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public void recordBackendHeartbeat(LinuxServerId linuxServerId, Instant heartbeatAt) {
        String id = linuxServerId.value();
        redisTemplate.opsForValue().set(BACKEND_KEY_PREFIX + id, String.valueOf(heartbeatAt.toEpochMilli()), RUNTIME_SNAPSHOT_TTL);
        redisTemplate.opsForSet().add(BACKEND_INDEX_KEY, id);
    }

    @Override
    public void recordBackendSnapshot(BackendRuntimeSnapshot snapshot) {
        String id = snapshot.backendProcess().backendProcessId().value();
        redisTemplate.opsForValue().set(BACKEND_SNAPSHOT_KEY_PREFIX + id, encode(snapshot), RUNTIME_SNAPSHOT_TTL);
        redisTemplate.opsForSet().add(BACKEND_SNAPSHOT_INDEX_KEY, id);
        recordBackendHeartbeat(snapshot.backendProcess().linuxServerId(), snapshot.backendProcess().lastHeartbeatAt());
        appendBackendMetricSample(snapshot);
    }

    @Override
    public void recordManagerSnapshot(ManagerRuntimeSnapshot snapshot) {
        String id = snapshot.manager().managerId().value();
        redisTemplate.opsForValue().set(MANAGER_SNAPSHOT_KEY_PREFIX + id, encode(snapshot), RUNTIME_SNAPSHOT_TTL);
        redisTemplate.opsForSet().add(MANAGER_SNAPSHOT_INDEX_KEY, id);
        appendContainerMetricSample(snapshot);
    }

    @Override
    public void recordOpencodeHeartbeat(OpencodeProcessId processId, Instant heartbeatAt) {
        String id = processId.value();
        redisTemplate.opsForValue().set(OPENCODE_KEY_PREFIX + id, String.valueOf(heartbeatAt.toEpochMilli()), OPENCODE_HEARTBEAT_TTL);
        redisTemplate.opsForSet().add(OPENCODE_INDEX_KEY, id);
    }

    @Override
    public List<BackendRuntimeSnapshot> liveBackendSnapshots() {
        return liveSnapshots(BACKEND_SNAPSHOT_INDEX_KEY, BACKEND_SNAPSHOT_KEY_PREFIX, BackendRuntimeSnapshot.class);
    }

    @Override
    public List<ManagerRuntimeSnapshot> liveManagerSnapshots() {
        return liveSnapshots(MANAGER_SNAPSHOT_INDEX_KEY, MANAGER_SNAPSHOT_KEY_PREFIX, ManagerRuntimeSnapshot.class);
    }

    @Override
    public List<ContainerRuntimeMetricSample> containerMetricSamples(
            OpencodeContainerId containerId,
            Instant from,
            Instant to) {
        List<ContainerRuntimeMetricSample> samples = metricSamples(
                CONTAINER_METRICS_KEY_PREFIX + containerId.value(),
                from,
                to,
                ContainerRuntimeMetricSample.class);
        return samples.stream()
                .sorted(Comparator.comparing(ContainerRuntimeMetricSample::sampledAt))
                .toList();
    }

    @Override
    public List<BackendRuntimeMetricSample> backendMetricSamples(
            LinuxServerId linuxServerId,
            Instant from,
            Instant to) {
        List<BackendRuntimeMetricSample> samples = metricSamples(
                BACKEND_METRICS_KEY_PREFIX + linuxServerId.value(),
                from,
                to,
                BackendRuntimeMetricSample.class);
        return samples.stream()
                .sorted(Comparator.comparing(BackendRuntimeMetricSample::sampledAt))
                .toList();
    }

    @Override
    public List<ServerRuntimeMetricSample> serverMetricSamples(
            LinuxServerId linuxServerId,
            Instant from,
            Instant to) {
        List<ServerRuntimeMetricSample> samples = metricSamples(
                SERVER_METRICS_KEY_PREFIX + linuxServerId.value(),
                from,
                to,
                ServerRuntimeMetricSample.class);
        return samples.stream()
                .sorted(Comparator.comparing(ServerRuntimeMetricSample::sampledAt))
                .toList();
    }

    @Override
    public List<BackendRuntimeMetricSample> legacyBackendMetricSamples(
            BackendProcessId backendProcessId,
            Instant from,
            Instant to) {
        List<BackendRuntimeMetricSample> samples = metricSamples(
                BACKEND_METRICS_KEY_PREFIX + backendProcessId.value(),
                from,
                to,
                BackendRuntimeMetricSample.class);
        return samples.stream()
                .sorted(Comparator.comparing(BackendRuntimeMetricSample::sampledAt))
                .toList();
    }

    @Override
    public Set<LinuxServerId> liveBackendServerIds() {
        Set<String> ids = liveIds(BACKEND_INDEX_KEY, BACKEND_KEY_PREFIX);
        Set<LinuxServerId> result = new LinkedHashSet<>();
        for (String id : ids) {
            result.add(new LinuxServerId(id));
        }
        for (BackendRuntimeSnapshot snapshot : liveBackendSnapshots()) {
            result.add(snapshot.backendProcess().linuxServerId());
        }
        return result;
    }

    @Override
    public Set<OpencodeProcessId> liveOpencodeProcessIds() {
        Set<String> ids = liveIds(OPENCODE_INDEX_KEY, OPENCODE_KEY_PREFIX);
        Set<OpencodeProcessId> result = new LinkedHashSet<>();
        for (String id : ids) {
            result.add(new OpencodeProcessId(id));
        }
        return result;
    }

    @Override
    public void cleanupExpiredHeartbeats() {
        cleanupIndex(BACKEND_INDEX_KEY, BACKEND_KEY_PREFIX);
        cleanupIndex(OPENCODE_INDEX_KEY, OPENCODE_KEY_PREFIX);
        cleanupIndex(BACKEND_SNAPSHOT_INDEX_KEY, BACKEND_SNAPSHOT_KEY_PREFIX);
        cleanupIndex(MANAGER_SNAPSHOT_INDEX_KEY, MANAGER_SNAPSHOT_KEY_PREFIX);
    }

    private Set<String> liveIds(String indexKey, String keyPrefix) {
        Set<String> indexed = redisTemplate.opsForSet().members(indexKey);
        if (indexed == null || indexed.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String id : indexed) {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(keyPrefix + id))) {
                result.add(id);
            }
        }
        return result;
    }

    private <T> List<T> liveSnapshots(String indexKey, String keyPrefix, Class<T> snapshotType) {
        Set<String> indexed = redisTemplate.opsForSet().members(indexKey);
        if (indexed == null || indexed.isEmpty()) {
            return List.of();
        }
        List<T> result = new ArrayList<>();
        for (String id : indexed) {
            String key = keyPrefix + id;
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                redisTemplate.opsForSet().remove(indexKey, id);
                continue;
            }
            try {
                result.add(objectMapper.readValue(value, snapshotType));
            } catch (JsonProcessingException exception) {
                // Redis 中的损坏快照不应阻断管理页，删除索引等待下一次心跳重建。
                redisTemplate.delete(key);
                redisTemplate.opsForSet().remove(indexKey, id);
            }
        }
        return List.copyOf(result);
    }

    private void appendContainerMetricSample(ManagerRuntimeSnapshot snapshot) {
        ContainerRuntimeMetrics metrics = snapshot.metrics();
        if (metrics == null) {
            return;
        }
        ContainerRuntimeMetricSample sample = new ContainerRuntimeMetricSample(
                snapshot.container().lastHeartbeatAt(),
                metrics.maxProcesses(),
                metrics.currentProcesses(),
                metrics.metricsSource(),
                metrics.cpuUsagePercent(),
                metrics.memoryMaxBytes(),
                metrics.memoryUsedBytes(),
                metrics.memoryUsagePercent(),
                metrics.diskReadBytesPerSecond(),
                metrics.diskWriteBytesPerSecond());
        appendMetricSample(CONTAINER_METRICS_KEY_PREFIX + snapshot.container().containerId().value(), sample.sampledAt(), sample);
    }

    private void appendBackendMetricSample(BackendRuntimeSnapshot snapshot) {
        BackendRuntimeMetrics metrics = snapshot.metrics();
        if (metrics == null) {
            return;
        }
        ServerRuntimeMetricSample serverSample = new ServerRuntimeMetricSample(
                snapshot.backendProcess().lastHeartbeatAt(),
                metrics.cpuUsagePercent(),
                metrics.cpuCoreCount(),
                metrics.loadAverage1m(),
                metrics.loadAverage5m(),
                metrics.loadAverage15m(),
                metrics.memoryMaxBytes(),
                metrics.memoryTotalBytes(),
                metrics.memoryAvailableBytes(),
                metrics.memoryFreeBytes(),
                metrics.memoryUsedBytes(),
                metrics.memoryUsagePercent(),
                metrics.memoryBuffersBytes(),
                metrics.memoryCachedBytes(),
                metrics.swapTotalBytes(),
                metrics.swapFreeBytes(),
                metrics.swapUsedBytes(),
                metrics.swapUsagePercent(),
                metrics.diskMaxBytes(),
                metrics.diskAvailableBytes(),
                metrics.diskUsedBytes(),
                metrics.diskUsagePercent());
        appendMetricSample(SERVER_METRICS_KEY_PREFIX + snapshot.linuxServer().linuxServerId().value(), serverSample.sampledAt(), serverSample);
        BackendRuntimeMetricSample sample = new BackendRuntimeMetricSample(
                snapshot.backendProcess().lastHeartbeatAt(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                metrics.jvmProcessCpuUsagePercent(),
                metrics.jvmProcessCpuCoreUsage(),
                metrics.jvmProcessCpuTimeNanos(),
                metrics.jvmProcessResidentMemoryBytes(),
                metrics.jvmProcessPeakResidentMemoryBytes(),
                metrics.jvmProcessVirtualMemoryBytes(),
                metrics.jvmProcessSwapBytes(),
                metrics.jvmOpenFileDescriptorCount(),
                metrics.jvmMaxFileDescriptorCount(),
                metrics.jvmMemoryUsedBytes(),
                metrics.jvmMemoryCommittedBytes(),
                metrics.jvmMemoryMaxBytes(),
                metrics.jvmHeapUsedBytes(),
                metrics.jvmHeapCommittedBytes(),
                metrics.jvmHeapMaxBytes(),
                metrics.jvmNonHeapUsedBytes(),
                metrics.jvmNonHeapCommittedBytes(),
                metrics.jvmNonHeapMaxBytes(),
                metrics.jvmDirectBufferCount(),
                metrics.jvmDirectBufferUsedBytes(),
                metrics.jvmDirectBufferCapacityBytes(),
                metrics.jvmMappedBufferCount(),
                metrics.jvmMappedBufferUsedBytes(),
                metrics.jvmMappedBufferCapacityBytes(),
                metrics.jvmGcPauseMillis(),
                metrics.jvmGcCollectionTimeDeltaMillis(),
                metrics.jvmGcCollectionCountDelta(),
                metrics.jvmGcTimePercent(),
                metrics.jvmThreadsLive(),
                metrics.jvmThreadsDaemon(),
                metrics.jvmThreadsPeak(),
                metrics.jvmThreadsTotalStarted());
        appendMetricSample(BACKEND_METRICS_KEY_PREFIX + snapshot.backendProcess().linuxServerId().value(), sample.sampledAt(), sample);
    }

    private void appendMetricSample(String key, Instant sampledAt, Object sample) {
        redisTemplate.opsForZSet().add(key, encode(sample), sampledAt.toEpochMilli());
        redisTemplate.opsForZSet().removeRangeByScore(
                key,
                0,
                sampledAt.minus(METRICS_HISTORY_RETENTION).toEpochMilli() - 1);
        redisTemplate.expire(key, METRICS_HISTORY_KEY_TTL);
    }

    private <T> List<T> metricSamples(String key, Instant from, Instant to, Class<T> sampleType) {
        Set<String> values = redisTemplate.opsForZSet().rangeByScore(key, from.toEpochMilli(), to.toEpochMilli());
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<T> result = new ArrayList<>();
        for (String value : values) {
            try {
                result.add(objectMapper.readValue(value, sampleType));
            } catch (JsonProcessingException exception) {
                redisTemplate.opsForZSet().remove(key, value);
            }
        }
        return List.copyOf(result);
    }

    private void cleanupIndex(String indexKey, String keyPrefix) {
        Set<String> indexed = redisTemplate.opsForSet().members(indexKey);
        if (indexed == null || indexed.isEmpty()) {
            return;
        }
        for (String id : indexed) {
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(keyPrefix + id))) {
                redisTemplate.opsForSet().remove(indexKey, id);
            }
        }
    }

    private String encode(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("encode runtime heartbeat snapshot failed", exception);
        }
    }
}
