package com.icbc.testagent.opencode.runtime.process.socket;

import com.fasterxml.jackson.annotation.JsonInclude;
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
        String errorCode) {

    /**
     * 规整可扩展能力字段，避免调用方持有可变 Map。
     */
    public ManagerControlMessage {
        capabilities = capabilities == null ? Map.of() : Map.copyOf(capabilities);
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
                null);
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
                errorCode);
    }
}
