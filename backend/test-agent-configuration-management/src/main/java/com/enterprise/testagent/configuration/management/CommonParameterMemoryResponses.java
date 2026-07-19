package com.enterprise.testagent.configuration.management;

import java.time.Instant;
import java.util.List;

/** 通用参数 JVM 内存诊断与刷新响应模型。 */
public final class CommonParameterMemoryResponses {

    private CommonParameterMemoryResponses() {
    }

    /** 单个 Java 进程的查询或刷新结果状态。 */
    public enum ProcessStatus {
        SUCCESS,
        PARTIAL,
        FAILED,
        UNAVAILABLE
    }

    /** 单个显式内存参数的当前状态。 */
    public record ParameterResponse(
            String englishName,
            String platform,
            String sourceValue,
            String memoryValue,
            Instant loadedAt,
            Instant lastRefreshAttemptAt,
            String refreshStatus,
            String errorMessage) {
    }

    /** 单个在线 Java 进程的内存参数快照或刷新结果。 */
    public record ProcessResponse(
            String backendProcessId,
            String linuxServerId,
            String listenUrl,
            String instanceId,
            Instant capturedAt,
            ProcessStatus status,
            String errorCode,
            String errorMessage,
            List<ParameterResponse> parameters) {

        public ProcessResponse {
            parameters = parameters == null ? List.of() : List.copyOf(parameters);
        }

        /** 构造集群聚合中的不可用进程占位，不泄露底层转发异常。 */
        public static ProcessResponse unavailable(
                String backendProcessId,
                String linuxServerId,
                String listenUrl,
                Instant capturedAt,
                String errorCode,
                String errorMessage) {
            return new ProcessResponse(
                    backendProcessId,
                    linuxServerId,
                    listenUrl,
                    null,
                    capturedAt,
                    ProcessStatus.UNAVAILABLE,
                    errorCode,
                    errorMessage,
                    List.of());
        }
    }

    /** 集群查询或刷新汇总；部分失败保留全部逐进程结果。 */
    public record ClusterResponse(
            Instant capturedAt,
            int totalProcesses,
            int successfulProcesses,
            int partiallySuccessfulProcesses,
            int failedProcesses,
            List<ProcessResponse> processes) {

        public ClusterResponse {
            processes = processes == null ? List.of() : List.copyOf(processes);
        }

        /** 根据逐进程状态计算稳定汇总计数。 */
        public static ClusterResponse from(Instant capturedAt, List<ProcessResponse> processes) {
            List<ProcessResponse> safeProcesses = processes == null ? List.of() : List.copyOf(processes);
            int successful = (int) safeProcesses.stream()
                    .filter(item -> item.status() == ProcessStatus.SUCCESS)
                    .count();
            int partial = (int) safeProcesses.stream()
                    .filter(item -> item.status() == ProcessStatus.PARTIAL)
                    .count();
            int failed = safeProcesses.size() - successful - partial;
            return new ClusterResponse(
                    capturedAt,
                    safeProcesses.size(),
                    successful,
                    partial,
                    failed,
                    safeProcesses);
        }
    }
}
