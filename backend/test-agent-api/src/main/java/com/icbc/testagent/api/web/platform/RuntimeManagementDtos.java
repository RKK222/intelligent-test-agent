package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.LinuxServer;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainer;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerManager;
import com.icbc.testagent.domain.opencodeprocess.OpencodeManagerBackendConnection;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
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
            Instant createdAt,
            Instant updatedAt,
            String traceId) {

        static BackendProcessResponse from(BackendJavaProcess process) {
            return new BackendProcessResponse(
                    process.backendProcessId().value(),
                    process.linuxServerId().value(),
                    process.listenUrl(),
                    process.status().name(),
                    process.startedAt(),
                    process.lastHeartbeatAt(),
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
            String status,
            Instant lastHeartbeatAt,
            Instant createdAt,
            Instant updatedAt,
            String traceId) {

        static ContainerResponse from(OpencodeContainer container) {
            return new ContainerResponse(
                    container.containerId().value(),
                    container.linuxServerId().value(),
                    container.containerName(),
                    container.portStart(),
                    container.portEnd(),
                    container.maxProcesses(),
                    container.currentProcesses(),
                    container.availableCapacity(),
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
            String traceId) {

        static ManagerResponse from(OpencodeContainerManager manager) {
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
                    manager.traceId());
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
            Instant bindingUpdatedAt) {

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
                    binding == null ? null : binding.updatedAt());
        }
    }
}
