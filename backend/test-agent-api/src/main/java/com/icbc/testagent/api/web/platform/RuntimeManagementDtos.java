package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.opencodeprocess.BackendRuntimeMetrics;
import com.icbc.testagent.domain.opencodeprocess.ContainerRuntimeMetrics;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.LinuxServer;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainer;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerManager;
import com.icbc.testagent.domain.opencodeprocess.OpencodeManagerBackendConnection;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementBackendMetricHistory;
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementBackendMetricSample;
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementBackendProcess;
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementContainer;
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementContainerMetricHistory;
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementContainerMetricSample;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessControlResult;
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementManager;
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementManagedProcess;
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementOpencodeProcess;
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementOverview;
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementSummary;
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
            String linuxServerId,
            String listenUrl,
            String status,
            Instant startedAt,
            Instant lastHeartbeatAt,
            Double cpuUsagePercent,
            Long memoryMaxBytes,
            Long memoryUsedBytes,
            Double memoryUsagePercent,
            Long diskMaxBytes,
            Long diskUsedBytes,
            Double diskUsagePercent,
            Long jvmMemoryUsedBytes,
            Long jvmMemoryCommittedBytes,
            Long jvmMemoryMaxBytes,
            Long jvmGcPauseMillis,
            Integer jvmThreadsLive,
            Instant createdAt,
            Instant updatedAt,
            String traceId) {

        static BackendProcessResponse from(RuntimeManagementBackendProcess row) {
            BackendJavaProcess process = row.process();
            BackendRuntimeMetrics metrics = row.metrics();
            return new BackendProcessResponse(
                    process.backendProcessId().value(),
                    process.linuxServerId().value(),
                    process.listenUrl(),
                    process.status().name(),
                    process.startedAt(),
                    process.lastHeartbeatAt(),
                    metrics == null ? null : metrics.cpuUsagePercent(),
                    metrics == null ? null : metrics.memoryMaxBytes(),
                    metrics == null ? null : metrics.memoryUsedBytes(),
                    metrics == null ? null : metrics.memoryUsagePercent(),
                    metrics == null ? null : metrics.diskMaxBytes(),
                    metrics == null ? null : metrics.diskUsedBytes(),
                    metrics == null ? null : metrics.diskUsagePercent(),
                    metrics == null ? null : metrics.jvmMemoryUsedBytes(),
                    metrics == null ? null : metrics.jvmMemoryCommittedBytes(),
                    metrics == null ? null : metrics.jvmMemoryMaxBytes(),
                    metrics == null ? null : metrics.jvmGcPauseMillis(),
                    metrics == null ? null : metrics.jvmThreadsLive(),
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
            String backendProcessId,
            Instant from,
            Instant to,
            List<BackendMetricSampleResponse> samples) {

        static BackendMetricHistoryResponse from(RuntimeManagementBackendMetricHistory history) {
            return new BackendMetricHistoryResponse(
                    history.generatedAt(),
                    history.backendProcessId().value(),
                    history.from(),
                    history.to(),
                    history.samples().stream().map(BackendMetricSampleResponse::from).toList());
        }
    }

    record BackendMetricSampleResponse(
            Instant sampledAt,
            Double cpuUsagePercent,
            Long memoryMaxBytes,
            Long memoryUsedBytes,
            Double memoryUsagePercent,
            Long diskMaxBytes,
            Long diskUsedBytes,
            Double diskUsagePercent,
            Long jvmMemoryUsedBytes,
            Long jvmMemoryCommittedBytes,
            Long jvmMemoryMaxBytes,
            Long jvmGcPauseMillis,
            Integer jvmThreadsLive) {

        static BackendMetricSampleResponse from(RuntimeManagementBackendMetricSample sample) {
            return new BackendMetricSampleResponse(
                    sample.sampledAt(),
                    sample.cpuUsagePercent(),
                    sample.memoryMaxBytes(),
                    sample.memoryUsedBytes(),
                    sample.memoryUsagePercent(),
                    sample.diskMaxBytes(),
                    sample.diskUsedBytes(),
                    sample.diskUsagePercent(),
                    sample.jvmMemoryUsedBytes(),
                    sample.jvmMemoryCommittedBytes(),
                    sample.jvmMemoryMaxBytes(),
                    sample.jvmGcPauseMillis(),
                    sample.jvmThreadsLive());
        }
    }
}
