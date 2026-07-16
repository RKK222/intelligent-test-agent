package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.opencodeprocess.BackendRuntimeMetrics;
import com.enterprise.testagent.domain.opencodeprocess.ContainerRuntimeMetrics;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServer;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainer;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerManager;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeManagerBackendConnection;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.enterprise.testagent.opencode.runtime.process.RuntimeManagementBackendMetricHistory;
import com.enterprise.testagent.opencode.runtime.process.RuntimeManagementBackendMetricSample;
import com.enterprise.testagent.opencode.runtime.process.RuntimeManagementBackendProcess;
import com.enterprise.testagent.opencode.runtime.process.RuntimeManagementContainer;
import com.enterprise.testagent.opencode.runtime.process.RuntimeManagementContainerMetricHistory;
import com.enterprise.testagent.opencode.runtime.process.RuntimeManagementContainerMetricSample;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessControlResult;
import com.enterprise.testagent.opencode.runtime.process.RuntimeManagementManager;
import com.enterprise.testagent.opencode.runtime.process.RuntimeManagementManagedProcess;
import com.enterprise.testagent.opencode.runtime.process.RuntimeManagementOpencodeProcess;
import com.enterprise.testagent.opencode.runtime.process.RuntimeManagementOverview;
import com.enterprise.testagent.opencode.runtime.process.RuntimeManagementSummary;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 超级管理员运行管理 HTTP DTO，隔离前端 wire shape 与后端领域对象。
 */
final class RuntimeManagementDtos {

    private RuntimeManagementDtos() {
    }

    static PageResponse<OpencodeProcessResponse> opencodeProcessPage(PageResponse<RuntimeManagementOpencodeProcess> processPage) {
        return new PageResponse<>(
                processPage.items().stream().map(OpencodeProcessResponse::from).toList(),
                processPage.page(),
                processPage.size(),
                processPage.total());
    }

    record OverviewResponse(
            Instant generatedAt,
            SummaryResponse summary,
            List<LinuxServerResponse> linuxServers,
            List<BackendProcessResponse> backendProcesses,
            List<ContainerResponse> containers,
            List<ManagerResponse> managers,
            List<ManagerBackendConnectionResponse> managerBackendConnections,
            PageResponse<OpencodeProcessResponse> opencodeProcesses) {

        static OverviewResponse from(RuntimeManagementOverview overview) {
            PageResponse<RuntimeManagementOpencodeProcess> processPage = overview.opencodeProcesses();
            return new OverviewResponse(
                    overview.generatedAt(),
                    SummaryResponse.from(overview.summary()),
                    overview.linuxServers().stream().map(LinuxServerResponse::from).toList(),
                    overview.backendProcesses().stream().map(BackendProcessResponse::from).toList(),
                    overview.containers().stream().map(ContainerResponse::from).toList(),
                    overview.managers().stream().map(ManagerResponse::from).toList(),
                    overview.managerBackendConnections().stream().map(ManagerBackendConnectionResponse::from).toList(),
                    new PageResponse<>(
                            processPage.items().stream().map(OpencodeProcessResponse::from).toList(),
                            processPage.page(),
                            processPage.size(),
                            processPage.total()));
        }
    }

    record SummaryResponse(
            int linuxServers,
            int readyLinuxServers,
            int backendProcesses,
            int readyBackendProcesses,
            int containers,
            int readyContainers,
            int managers,
            int connectedManagers,
            int managerBackendConnections,
            long opencodeProcesses,
            long runningOpencodeProcesses,
            long userBindings) {

        static SummaryResponse from(RuntimeManagementSummary summary) {
            return new SummaryResponse(
                    summary.linuxServers(),
                    summary.readyLinuxServers(),
                    summary.backendProcesses(),
                    summary.readyBackendProcesses(),
                    summary.containers(),
                    summary.readyContainers(),
                    summary.managers(),
                    summary.connectedManagers(),
                    summary.managerBackendConnections(),
                    summary.opencodeProcesses(),
                    summary.runningOpencodeProcesses(),
                    summary.userBindings());
        }
    }

    record LinuxServerResponse(
            String linuxServerId,
            String name,
            String status,
            Map<String, Object> capacitySummary,
            Instant lastHeartbeatAt,
            Instant createdAt,
            Instant updatedAt,
            String traceId) {

        static LinuxServerResponse from(LinuxServer server) {
            return new LinuxServerResponse(
                    server.linuxServerId().value(),
                    server.name(),
                    server.status().name(),
                    server.capacitySummary(),
                    server.lastHeartbeatAt(),
                    server.createdAt(),
                    server.updatedAt(),
                    server.traceId());
        }
    }

    record BackendProcessResponse(
            String backendProcessId,
            String buildVersion,
            String linuxServerId,
            String listenUrl,
            String status,
            Instant startedAt,
            Instant lastHeartbeatAt,
            Double cpuUsagePercent,
            Integer cpuCoreCount,
            Double loadAverage1m,
            Double loadAverage5m,
            Double loadAverage15m,
            Long memoryMaxBytes,
            Long memoryTotalBytes,
            Long memoryAvailableBytes,
            Long memoryFreeBytes,
            Long memoryUsedBytes,
            Double memoryUsagePercent,
            Long memoryBuffersBytes,
            Long memoryCachedBytes,
            Long swapTotalBytes,
            Long swapFreeBytes,
            Long swapUsedBytes,
            Double swapUsagePercent,
            Long diskMaxBytes,
            Long diskAvailableBytes,
            Long diskUsedBytes,
            Double diskUsagePercent,
            Double jvmProcessCpuUsagePercent,
            Double jvmProcessCpuCoreUsage,
            Long jvmProcessCpuTimeNanos,
            Long jvmProcessResidentMemoryBytes,
            Long jvmProcessPeakResidentMemoryBytes,
            Long jvmProcessVirtualMemoryBytes,
            Long jvmProcessSwapBytes,
            Long jvmOpenFileDescriptorCount,
            Long jvmMaxFileDescriptorCount,
            Long jvmMemoryUsedBytes,
            Long jvmMemoryCommittedBytes,
            Long jvmMemoryMaxBytes,
            Long jvmHeapUsedBytes,
            Long jvmHeapCommittedBytes,
            Long jvmHeapMaxBytes,
            Long jvmNonHeapUsedBytes,
            Long jvmNonHeapCommittedBytes,
            Long jvmNonHeapMaxBytes,
            Long jvmDirectBufferCount,
            Long jvmDirectBufferUsedBytes,
            Long jvmDirectBufferCapacityBytes,
            Long jvmMappedBufferCount,
            Long jvmMappedBufferUsedBytes,
            Long jvmMappedBufferCapacityBytes,
            Long jvmGcPauseMillis,
            Long jvmGcCollectionTimeDeltaMillis,
            Long jvmGcCollectionCountDelta,
            Double jvmGcTimePercent,
            Integer jvmThreadsLive,
            Integer jvmThreadsDaemon,
            Integer jvmThreadsPeak,
            Long jvmThreadsTotalStarted,
            Instant createdAt,
            Instant updatedAt,
            String traceId) {

        static BackendProcessResponse from(RuntimeManagementBackendProcess row) {
            BackendJavaProcess process = row.process();
            BackendRuntimeMetrics metrics = row.metrics();
            return new BackendProcessResponse(
                    process.backendProcessId().value(),
                    row.buildVersion(),
                    process.linuxServerId().value(),
                    process.listenUrl(),
                    process.status().name(),
                    process.startedAt(),
                    process.lastHeartbeatAt(),
                    metrics == null ? null : metrics.cpuUsagePercent(),
                    metrics == null ? null : metrics.cpuCoreCount(),
                    metrics == null ? null : metrics.loadAverage1m(),
                    metrics == null ? null : metrics.loadAverage5m(),
                    metrics == null ? null : metrics.loadAverage15m(),
                    metrics == null ? null : metrics.memoryMaxBytes(),
                    metrics == null ? null : metrics.memoryTotalBytes(),
                    metrics == null ? null : metrics.memoryAvailableBytes(),
                    metrics == null ? null : metrics.memoryFreeBytes(),
                    metrics == null ? null : metrics.memoryUsedBytes(),
                    metrics == null ? null : metrics.memoryUsagePercent(),
                    metrics == null ? null : metrics.memoryBuffersBytes(),
                    metrics == null ? null : metrics.memoryCachedBytes(),
                    metrics == null ? null : metrics.swapTotalBytes(),
                    metrics == null ? null : metrics.swapFreeBytes(),
                    metrics == null ? null : metrics.swapUsedBytes(),
                    metrics == null ? null : metrics.swapUsagePercent(),
                    metrics == null ? null : metrics.diskMaxBytes(),
                    metrics == null ? null : metrics.diskAvailableBytes(),
                    metrics == null ? null : metrics.diskUsedBytes(),
                    metrics == null ? null : metrics.diskUsagePercent(),
                    metrics == null ? null : metrics.jvmProcessCpuUsagePercent(),
                    metrics == null ? null : metrics.jvmProcessCpuCoreUsage(),
                    metrics == null ? null : metrics.jvmProcessCpuTimeNanos(),
                    metrics == null ? null : metrics.jvmProcessResidentMemoryBytes(),
                    metrics == null ? null : metrics.jvmProcessPeakResidentMemoryBytes(),
                    metrics == null ? null : metrics.jvmProcessVirtualMemoryBytes(),
                    metrics == null ? null : metrics.jvmProcessSwapBytes(),
                    metrics == null ? null : metrics.jvmOpenFileDescriptorCount(),
                    metrics == null ? null : metrics.jvmMaxFileDescriptorCount(),
                    metrics == null ? null : metrics.jvmMemoryUsedBytes(),
                    metrics == null ? null : metrics.jvmMemoryCommittedBytes(),
                    metrics == null ? null : metrics.jvmMemoryMaxBytes(),
                    metrics == null ? null : metrics.jvmHeapUsedBytes(),
                    metrics == null ? null : metrics.jvmHeapCommittedBytes(),
                    metrics == null ? null : metrics.jvmHeapMaxBytes(),
                    metrics == null ? null : metrics.jvmNonHeapUsedBytes(),
                    metrics == null ? null : metrics.jvmNonHeapCommittedBytes(),
                    metrics == null ? null : metrics.jvmNonHeapMaxBytes(),
                    metrics == null ? null : metrics.jvmDirectBufferCount(),
                    metrics == null ? null : metrics.jvmDirectBufferUsedBytes(),
                    metrics == null ? null : metrics.jvmDirectBufferCapacityBytes(),
                    metrics == null ? null : metrics.jvmMappedBufferCount(),
                    metrics == null ? null : metrics.jvmMappedBufferUsedBytes(),
                    metrics == null ? null : metrics.jvmMappedBufferCapacityBytes(),
                    metrics == null ? null : metrics.jvmGcPauseMillis(),
                    metrics == null ? null : metrics.jvmGcCollectionTimeDeltaMillis(),
                    metrics == null ? null : metrics.jvmGcCollectionCountDelta(),
                    metrics == null ? null : metrics.jvmGcTimePercent(),
                    metrics == null ? null : metrics.jvmThreadsLive(),
                    metrics == null ? null : metrics.jvmThreadsDaemon(),
                    metrics == null ? null : metrics.jvmThreadsPeak(),
                    metrics == null ? null : metrics.jvmThreadsTotalStarted(),
                    process.createdAt(),
                    process.updatedAt(),
                    process.traceId());
        }
    }

    record ContainerResponse(
            String containerId,
            String linuxServerId,
            String containerName,
            int portStart,
            int portEnd,
            int maxProcesses,
            int currentProcesses,
            int availableCapacity,
            String metricsSource,
            Double cpuUsagePercent,
            Long memoryMaxBytes,
            Long memoryUsedBytes,
            Double memoryUsagePercent,
            Double diskReadBytesPerSecond,
            Double diskWriteBytesPerSecond,
            String status,
            Instant lastHeartbeatAt,
            Instant createdAt,
            Instant updatedAt,
            String traceId) {

        static ContainerResponse from(RuntimeManagementContainer row) {
            OpencodeContainer container = row.container();
            ContainerRuntimeMetrics metrics = row.metrics();
            return new ContainerResponse(
                    container.containerId().value(),
                    container.linuxServerId().value(),
                    container.containerName(),
                    container.portStart(),
                    container.portEnd(),
                    container.maxProcesses(),
                    container.currentProcesses(),
                    container.availableCapacity(),
                    metrics == null ? null : metrics.metricsSource(),
                    metrics == null ? null : metrics.cpuUsagePercent(),
                    metrics == null ? null : metrics.memoryMaxBytes(),
                    metrics == null ? null : metrics.memoryUsedBytes(),
                    metrics == null ? null : metrics.memoryUsagePercent(),
                    metrics == null ? null : metrics.diskReadBytesPerSecond(),
                    metrics == null ? null : metrics.diskWriteBytesPerSecond(),
                    container.status().name(),
                    container.lastHeartbeatAt(),
                    container.createdAt(),
                    container.updatedAt(),
                    container.traceId());
        }
    }

    record ManagerResponse(
            String managerId,
            String buildVersion,
            String containerId,
            String linuxServerId,
            String protocolVersion,
            String connectionStatus,
            Map<String, Object> capabilities,
            Instant lastHeartbeatAt,
            Instant createdAt,
            Instant updatedAt,
            String traceId,
            List<ManagedProcessResponse> managedProcesses) {

        static ManagerResponse from(RuntimeManagementManager row) {
            OpencodeContainerManager manager = row.manager();
            return new ManagerResponse(
                    manager.managerId().value(),
                    row.buildVersion(),
                    manager.containerId().value(),
                    manager.linuxServerId().value(),
                    manager.protocolVersion(),
                    manager.connectionStatus().name(),
                    manager.capabilities(),
                    manager.lastHeartbeatAt(),
                    manager.createdAt(),
                    manager.updatedAt(),
                    manager.traceId(),
                    row.managedProcesses().stream().map(ManagedProcessResponse::from).toList());
        }
    }

    record ManagedProcessResponse(
            int port,
            Long pid,
            String baseUrl,
            String sessionPath,
            String configPath,
            Instant startedAt,
            String startCommand,
            String traceId,
            String ownership,
            String processId,
            String processStatus,
            String healthMessage,
            String userId,
            String username,
            String bindingAgentId,
            String bindingStatus,
            Instant bindingUpdatedAt) {

        static ManagedProcessResponse from(RuntimeManagementManagedProcess process) {
            return new ManagedProcessResponse(
                    process.port(),
                    process.pid(),
                    process.baseUrl(),
                    process.sessionPath(),
                    process.configPath(),
                    process.startedAt(),
                    process.startCommand(),
                    process.traceId(),
                    process.ownership() == null ? null : process.ownership().name(),
                    process.processId() == null ? null : process.processId().value(),
                    process.processStatus() == null ? null : process.processStatus().name(),
                    process.healthMessage(),
                    process.userId() == null ? null : process.userId().value(),
                    process.username().orElse(null),
                    process.bindingAgentId(),
                    process.bindingStatus() == null ? null : process.bindingStatus().name(),
                    process.bindingUpdatedAt());
        }
    }

    record ManagedProcessCommandResponse(
            String command,
            String status,
            int port,
            Long pid,
            String baseUrl,
            String sessionPath,
            String configPath,
            Boolean healthy,
            String message,
            String traceId) {

        static ManagedProcessCommandResponse from(OpencodeProcessControlResult result) {
            return new ManagedProcessCommandResponse(
                    result.command(),
                    result.status(),
                    result.port(),
                    result.pid(),
                    result.baseUrl(),
                    result.sessionPath(),
                    result.configPath(),
                    result.healthy(),
                    result.message(),
                    result.traceId());
        }
    }

    record ManagerBackendConnectionResponse(
            String managerId,
            String backendProcessId,
            String status,
            Instant connectedAt,
            Instant lastHeartbeatAt,
            Instant updatedAt,
            String traceId) {

        static ManagerBackendConnectionResponse from(OpencodeManagerBackendConnection connection) {
            return new ManagerBackendConnectionResponse(
                    connection.managerId().value(),
                    connection.backendProcessId().value(),
                    connection.status().name(),
                    connection.connectedAt(),
                    connection.lastHeartbeatAt(),
                    connection.updatedAt(),
                    connection.traceId());
        }
    }

    record OpencodeProcessResponse(
            String processId,
            String userId,
            String username,
            String linuxServerId,
            String containerId,
            int port,
            Long pid,
            String baseUrl,
            String status,
            String sessionPath,
            String configPath,
            Instant startedAt,
            Instant lastHealthCheckAt,
            String healthMessage,
            Instant createdAt,
            Instant updatedAt,
            String traceId,
            String bindingAgentId,
            String bindingStatus,
            Instant bindingUpdatedAt,
            String managerStatus,
            String healthStatus,
            boolean restartable) {

        static OpencodeProcessResponse from(RuntimeManagementOpencodeProcess row) {
            OpencodeServerProcess process = row.process();
            UserOpencodeProcessBinding binding = row.binding().orElse(null);
            return new OpencodeProcessResponse(
                    process.processId().value(),
                    process.userId().value(),
                    row.username().orElse(process.userId().value()),
                    process.linuxServerId().value(),
                    process.containerId().value(),
                    process.port(),
                    process.pid(),
                    process.baseUrl(),
                    process.status().name(),
                    process.sessionPath(),
                    process.configPath(),
                    process.startedAt(),
                    process.lastHealthCheckAt(),
                    process.healthMessage(),
                    process.createdAt(),
                    process.updatedAt(),
                    process.traceId(),
                    binding == null ? null : binding.agentId(),
                    binding == null ? null : binding.status().name(),
                    binding == null ? null : binding.updatedAt(),
                    row.managerStatus(),
                    row.healthStatus(),
                    row.restartable());
        }
    }

    record ContainerMetricHistoryResponse(
            Instant generatedAt,
            String containerId,
            Instant from,
            Instant to,
            List<ContainerMetricSampleResponse> samples) {

        static ContainerMetricHistoryResponse from(RuntimeManagementContainerMetricHistory history) {
            return new ContainerMetricHistoryResponse(
                    history.generatedAt(),
                    history.containerId().value(),
                    history.from(),
                    history.to(),
                    history.samples().stream().map(ContainerMetricSampleResponse::from).toList());
        }
    }

    record ContainerMetricSampleResponse(
            Instant sampledAt,
            int maxProcesses,
            int currentProcesses,
            String metricsSource,
            Double cpuUsagePercent,
            Long memoryMaxBytes,
            Long memoryUsedBytes,
            Double memoryUsagePercent,
            Double diskReadBytesPerSecond,
            Double diskWriteBytesPerSecond) {

        static ContainerMetricSampleResponse from(RuntimeManagementContainerMetricSample sample) {
            return new ContainerMetricSampleResponse(
                    sample.sampledAt(),
                    sample.maxProcesses(),
                    sample.currentProcesses(),
                    sample.metricsSource(),
                    sample.cpuUsagePercent(),
                    sample.memoryMaxBytes(),
                    sample.memoryUsedBytes(),
                    sample.memoryUsagePercent(),
                    sample.diskReadBytesPerSecond(),
                    sample.diskWriteBytesPerSecond());
        }
    }

    record BackendMetricHistoryResponse(
            Instant generatedAt,
            String linuxServerId,
            String backendProcessId,
            Instant from,
            Instant to,
            List<BackendMetricSampleResponse> samples) {

        static BackendMetricHistoryResponse from(RuntimeManagementBackendMetricHistory history) {
            return new BackendMetricHistoryResponse(
                    history.generatedAt(),
                    history.linuxServerId() == null ? null : history.linuxServerId().value(),
                    history.backendProcessId().map(item -> item.value()).orElse(null),
                    history.from(),
                    history.to(),
                    history.samples().stream().map(BackendMetricSampleResponse::from).toList());
        }
    }

    record BackendMetricSampleResponse(
            Instant sampledAt,
            Double cpuUsagePercent,
            Integer cpuCoreCount,
            Double loadAverage1m,
            Double loadAverage5m,
            Double loadAverage15m,
            Long memoryMaxBytes,
            Long memoryTotalBytes,
            Long memoryAvailableBytes,
            Long memoryFreeBytes,
            Long memoryUsedBytes,
            Double memoryUsagePercent,
            Long memoryBuffersBytes,
            Long memoryCachedBytes,
            Long swapTotalBytes,
            Long swapFreeBytes,
            Long swapUsedBytes,
            Double swapUsagePercent,
            Long diskMaxBytes,
            Long diskAvailableBytes,
            Long diskUsedBytes,
            Double diskUsagePercent,
            Double jvmProcessCpuUsagePercent,
            Double jvmProcessCpuCoreUsage,
            Long jvmProcessCpuTimeNanos,
            Long jvmProcessResidentMemoryBytes,
            Long jvmProcessPeakResidentMemoryBytes,
            Long jvmProcessVirtualMemoryBytes,
            Long jvmProcessSwapBytes,
            Long jvmOpenFileDescriptorCount,
            Long jvmMaxFileDescriptorCount,
            Long jvmMemoryUsedBytes,
            Long jvmMemoryCommittedBytes,
            Long jvmMemoryMaxBytes,
            Long jvmHeapUsedBytes,
            Long jvmHeapCommittedBytes,
            Long jvmHeapMaxBytes,
            Long jvmNonHeapUsedBytes,
            Long jvmNonHeapCommittedBytes,
            Long jvmNonHeapMaxBytes,
            Long jvmDirectBufferCount,
            Long jvmDirectBufferUsedBytes,
            Long jvmDirectBufferCapacityBytes,
            Long jvmMappedBufferCount,
            Long jvmMappedBufferUsedBytes,
            Long jvmMappedBufferCapacityBytes,
            Long jvmGcPauseMillis,
            Long jvmGcCollectionTimeDeltaMillis,
            Long jvmGcCollectionCountDelta,
            Double jvmGcTimePercent,
            Integer jvmThreadsLive,
            Integer jvmThreadsDaemon,
            Integer jvmThreadsPeak,
            Long jvmThreadsTotalStarted) {

        static BackendMetricSampleResponse from(RuntimeManagementBackendMetricSample sample) {
            return new BackendMetricSampleResponse(
                    sample.sampledAt(),
                    sample.cpuUsagePercent(),
                    sample.cpuCoreCount(),
                    sample.loadAverage1m(),
                    sample.loadAverage5m(),
                    sample.loadAverage15m(),
                    sample.memoryMaxBytes(),
                    sample.memoryTotalBytes(),
                    sample.memoryAvailableBytes(),
                    sample.memoryFreeBytes(),
                    sample.memoryUsedBytes(),
                    sample.memoryUsagePercent(),
                    sample.memoryBuffersBytes(),
                    sample.memoryCachedBytes(),
                    sample.swapTotalBytes(),
                    sample.swapFreeBytes(),
                    sample.swapUsedBytes(),
                    sample.swapUsagePercent(),
                    sample.diskMaxBytes(),
                    sample.diskAvailableBytes(),
                    sample.diskUsedBytes(),
                    sample.diskUsagePercent(),
                    sample.jvmProcessCpuUsagePercent(),
                    sample.jvmProcessCpuCoreUsage(),
                    sample.jvmProcessCpuTimeNanos(),
                    sample.jvmProcessResidentMemoryBytes(),
                    sample.jvmProcessPeakResidentMemoryBytes(),
                    sample.jvmProcessVirtualMemoryBytes(),
                    sample.jvmProcessSwapBytes(),
                    sample.jvmOpenFileDescriptorCount(),
                    sample.jvmMaxFileDescriptorCount(),
                    sample.jvmMemoryUsedBytes(),
                    sample.jvmMemoryCommittedBytes(),
                    sample.jvmMemoryMaxBytes(),
                    sample.jvmHeapUsedBytes(),
                    sample.jvmHeapCommittedBytes(),
                    sample.jvmHeapMaxBytes(),
                    sample.jvmNonHeapUsedBytes(),
                    sample.jvmNonHeapCommittedBytes(),
                    sample.jvmNonHeapMaxBytes(),
                    sample.jvmDirectBufferCount(),
                    sample.jvmDirectBufferUsedBytes(),
                    sample.jvmDirectBufferCapacityBytes(),
                    sample.jvmMappedBufferCount(),
                    sample.jvmMappedBufferUsedBytes(),
                    sample.jvmMappedBufferCapacityBytes(),
                    sample.jvmGcPauseMillis(),
                    sample.jvmGcCollectionTimeDeltaMillis(),
                    sample.jvmGcCollectionCountDelta(),
                    sample.jvmGcTimePercent(),
                    sample.jvmThreadsLive(),
                    sample.jvmThreadsDaemon(),
                    sample.jvmThreadsPeak(),
                    sample.jvmThreadsTotalStarted());
        }
    }
}
