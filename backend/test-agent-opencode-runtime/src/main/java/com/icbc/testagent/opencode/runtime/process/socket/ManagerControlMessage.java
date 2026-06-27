package com.icbc.testagent.opencode.runtime.process.socket;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * 管理进程 WebSocket 控制面 JSON 文本帧，字段按消息类型选择性使用。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ManagerControlMessage(
        String type,
        String protocolVersion,
        String traceId,
        String managerId,
        String containerId,
        String linuxServerId,
        String containerName,
        Integer portStart,
        Integer portEnd,
        Integer maxProcesses,
        Integer currentProcesses,
        Double cpuUsagePercent,
        Long memoryMaxBytes,
        Long memoryUsedBytes,
        Double memoryUsagePercent,
        Double diskReadBytesPerSecond,
        Double diskWriteBytesPerSecond,
        List<ManagerManagedProcess> managedProcesses,
        Map<String, Object> capabilities,
        String backendProcessId,
        String commandId,
        String command,
        Integer port,
        Long timeoutMillis,
        String status,
        Long pid,
        String baseUrl,
        String sessionPath,
        String configPath,
        Boolean healthy,
        String message,
        String errorCode,
        List<String> connectedBackendProcessIds,
        List<ManagerBackendEndpoint> backendEndpoints,
        String metricsSource) {

    /**
     * 规整可扩展能力字段，避免调用方持有可变 Map。
     */
    public ManagerControlMessage {
        capabilities = capabilities == null ? Map.of() : Map.copyOf(capabilities);
        managedProcesses = managedProcesses == null ? List.of() : List.copyOf(managedProcesses);
        connectedBackendProcessIds = connectedBackendProcessIds == null ? List.of() : List.copyOf(connectedBackendProcessIds);
        backendEndpoints = backendEndpoints == null ? List.of() : List.copyOf(backendEndpoints);
    }

    /**
     * 兼容旧构造调用；新增 metricsSource 只由新 manager 心跳或 JSON 反序列化提供。
     */
    public ManagerControlMessage(
            String type,
            String protocolVersion,
            String traceId,
            String managerId,
            String containerId,
            String linuxServerId,
            String containerName,
            Integer portStart,
            Integer portEnd,
            Integer maxProcesses,
            Integer currentProcesses,
            Double cpuUsagePercent,
            Long memoryMaxBytes,
            Long memoryUsedBytes,
            Double memoryUsagePercent,
            Double diskReadBytesPerSecond,
            Double diskWriteBytesPerSecond,
            List<ManagerManagedProcess> managedProcesses,
            Map<String, Object> capabilities,
            String backendProcessId,
            String commandId,
            String command,
            Integer port,
            Long timeoutMillis,
            String status,
            Long pid,
            String baseUrl,
            String sessionPath,
            String configPath,
            Boolean healthy,
            String message,
            String errorCode,
            List<String> connectedBackendProcessIds,
            List<ManagerBackendEndpoint> backendEndpoints) {
        this(
                type,
                protocolVersion,
                traceId,
                managerId,
                containerId,
                linuxServerId,
                containerName,
                portStart,
                portEnd,
                maxProcesses,
                currentProcesses,
                cpuUsagePercent,
                memoryMaxBytes,
                memoryUsedBytes,
                memoryUsagePercent,
                diskReadBytesPerSecond,
                diskWriteBytesPerSecond,
                managedProcesses,
                capabilities,
                backendProcessId,
                commandId,
                command,
                port,
                timeoutMillis,
                status,
                pid,
                baseUrl,
                sessionPath,
                configPath,
                healthy,
                message,
                errorCode,
                connectedBackendProcessIds,
                backendEndpoints,
                null);
    }

    /**
     * 构造管理进程注册消息。
     */
    public static ManagerControlMessage register(
            String managerId,
            String containerId,
            String linuxServerId,
            String containerName,
            int portStart,
            int portEnd,
            int maxProcesses,
            int currentProcesses,
            Map<String, Object> capabilities,
            String traceId) {
        return new ManagerControlMessage(
                ManagerControlProtocol.TYPE_REGISTER,
                ManagerControlProtocol.VERSION,
                traceId,
                managerId,
                containerId,
                linuxServerId,
                containerName,
                portStart,
                portEnd,
                maxProcesses,
                currentProcesses,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                capabilities,
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
                null);
    }

    /**
     * 构造 manager 全局心跳消息；只需通过任一已连接 socket 上报一次。
     */
    public static ManagerControlMessage managerHeartbeat(
            String managerId,
            String containerId,
            String linuxServerId,
            String containerName,
            int portStart,
            int portEnd,
            int maxProcesses,
            int currentProcesses,
            Map<String, Object> capabilities,
            List<String> connectedBackendProcessIds,
            String traceId) {
        return new ManagerControlMessage(
                ManagerControlProtocol.TYPE_MANAGER_HEARTBEAT,
                ManagerControlProtocol.VERSION,
                traceId,
                managerId,
                containerId,
                linuxServerId,
                containerName,
                portStart,
                portEnd,
                maxProcesses,
                currentProcesses,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                capabilities,
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
                connectedBackendProcessIds,
                null);
    }

    /**
     * 构造后端确认注册消息。
     */
    public static ManagerControlMessage registered(String backendProcessId, String traceId) {
        return new ManagerControlMessage(
                ManagerControlProtocol.TYPE_REGISTERED,
                ManagerControlProtocol.VERSION,
                traceId,
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
                List.of(),
                Map.of(),
                backendProcessId,
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
                "registered",
                null,
                null,
                null);
    }

    /**
     * 构造 manager 主动询问后端实例列表的请求。
     */
    public static ManagerControlMessage backendListRequest(String traceId) {
        return new ManagerControlMessage(
                ManagerControlProtocol.TYPE_BACKEND_LIST_REQUEST,
                ManagerControlProtocol.VERSION,
                traceId,
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
                List.of(),
                Map.of(),
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
                null);
    }

    /**
     * 构造后端返回给 manager 的存活 Java 实例列表。
     */
    public static ManagerControlMessage backendListResponse(List<ManagerBackendEndpoint> backendEndpoints, String traceId) {
        return new ManagerControlMessage(
                ManagerControlProtocol.TYPE_BACKEND_LIST_RESPONSE,
                ManagerControlProtocol.VERSION,
                traceId,
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
                List.of(),
                Map.of(),
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
                backendEndpoints);
    }

    /**
     * 构造后端发往管理进程的命令消息。
     */
    public static ManagerControlMessage command(
            String commandId,
            String command,
            int port,
            long timeoutMillis,
            String traceId) {
        return new ManagerControlMessage(
                ManagerControlProtocol.TYPE_COMMAND,
                ManagerControlProtocol.VERSION,
                traceId,
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
                List.of(),
                Map.of(),
                null,
                commandId,
                command,
                port,
                timeoutMillis,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /**
     * 构造命令执行结果消息。
     */
    public static ManagerControlMessage commandResult(
            String commandId,
            String command,
            String status,
            Integer port,
            Long pid,
            String baseUrl,
            String sessionPath,
            String configPath,
            Boolean healthy,
            String message,
            String traceId) {
        return new ManagerControlMessage(
                ManagerControlProtocol.TYPE_COMMAND_RESULT,
                ManagerControlProtocol.VERSION,
                traceId,
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
                List.of(),
                Map.of(),
                null,
                commandId,
                command,
                port,
                null,
                status,
                pid,
                baseUrl,
                sessionPath,
                configPath,
                healthy,
                message,
                null,
                null,
                null);
    }

    /**
     * 构造后端→manager 的运行时配置下发消息（当前仅承载最大进程数）。
     */
    public static ManagerControlMessage configUpdate(int maxProcesses, String traceId) {
        return new ManagerControlMessage(
                ManagerControlProtocol.TYPE_CONFIG_UPDATE,
                ManagerControlProtocol.VERSION,
                traceId,
                null,
                null,
                null,
                null,
                null,
                null,
                maxProcesses,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                Map.of(),
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
                null);
    }

    /**
     * 构造安全错误消息，错误说明不得包含 token 或内部堆栈。
     */
    public static ManagerControlMessage error(String errorCode, String message, String traceId) {
        return new ManagerControlMessage(
                ManagerControlProtocol.TYPE_ERROR,
                ManagerControlProtocol.VERSION,
                traceId,
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
                List.of(),
                Map.of(),
                null,
                null,
                null,
                null,
                null,
                "FAILED",
                null,
                null,
                null,
                null,
                false,
                message,
                errorCode,
                null,
                null);
    }
}
