package com.icbc.testagent.domain.opencodeprocess;

import com.icbc.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Objects;

/**
 * opencode 容器拓扑和容量快照，端口范围按容器独立配置且主机端口直通。
 */
public record OpencodeContainer(
        OpencodeContainerId containerId,
        LinuxServerId linuxServerId,
        String containerName,
        int portStart,
        int portEnd,
        int maxProcesses,
        int currentProcesses,
        OpencodeContainerStatus status,
        Instant lastHeartbeatAt,
        Instant createdAt,
        Instant updatedAt,
        String traceId) {

    /**
     * 校验端口范围、容量和当前进程数，确保后续调度不会选到无效容器。
     */
    public OpencodeContainer {
        Objects.requireNonNull(containerId, "containerId must not be null");
        Objects.requireNonNull(linuxServerId, "linuxServerId must not be null");
        containerName = DomainValidation.requireText(containerName, "containerName");
        if (portStart < 1 || portEnd > 65535 || portStart > portEnd) {
            throw new IllegalArgumentException("port range must be between 1 and 65535");
        }
        int availablePorts = portEnd - portStart + 1;
        if (maxProcesses < 1 || maxProcesses > availablePorts) {
            throw new IllegalArgumentException("maxProcesses must be between 1 and available port count");
        }
        if (currentProcesses < 0 || currentProcesses > maxProcesses) {
            throw new IllegalArgumentException("currentProcesses must be between 0 and maxProcesses");
        }
        Objects.requireNonNull(status, "status must not be null");
        lastHeartbeatAt = DomainValidation.requireInstant(lastHeartbeatAt, "lastHeartbeatAt");
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        traceId = DomainValidation.requireText(traceId, "traceId");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    /**
     * 返回容器还能启动的 opencode 进程数量。
     */
    public int availableCapacity() {
        return maxProcesses - currentProcesses;
    }

    /**
     * 判断容器是否可参与后续用户进程调度。
     */
    public boolean canAcceptProcess() {
        return status == OpencodeContainerStatus.READY && availableCapacity() > 0;
    }
}
