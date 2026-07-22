package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.domain.opencodeprocess.ManagedOpencodeProcessSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 运行管理页展示的 manager 托管 opencode server 明细，合并本地进程快照与用户绑定归属。
 */
public record RuntimeManagementManagedProcess(
        int port,
        Long pid,
        String baseUrl,
        String sessionPath,
        String configPath,
        Instant startedAt,
        String startCommand,
        String traceId,
        String unifiedAuthId,
        String managerStatus,
        RuntimeManagementManagedProcessOwnership ownership,
        OpencodeProcessId processId,
        OpencodeServerProcessStatus processStatus,
        String healthMessage,
        UserId userId,
        Optional<String> username,
        String bindingAgentId,
        UserOpencodeProcessBindingStatus bindingStatus,
        Instant bindingUpdatedAt) {

    /**
     * 兼容旧 manager 快照：归属字段缺失时按无主进程展示，避免前端展开时报错。
     */
    public RuntimeManagementManagedProcess {
        username = username == null ? Optional.empty() : username;
        ownership = ownership == null ? RuntimeManagementManagedProcessOwnership.UNBOUND : ownership;
    }

    /**
     * 兼容未携带 manager 原始元数据的既有调用方；新增字段统一按缺失处理。
     */
    public RuntimeManagementManagedProcess(
            int port,
            Long pid,
            String baseUrl,
            String sessionPath,
            String configPath,
            Instant startedAt,
            String startCommand,
            String traceId,
            RuntimeManagementManagedProcessOwnership ownership,
            OpencodeProcessId processId,
            OpencodeServerProcessStatus processStatus,
            String healthMessage,
            UserId userId,
            Optional<String> username,
            String bindingAgentId,
            UserOpencodeProcessBindingStatus bindingStatus,
            Instant bindingUpdatedAt) {
        this(
                port,
                pid,
                baseUrl,
                sessionPath,
                configPath,
                startedAt,
                startCommand,
                traceId,
                null,
                null,
                ownership,
                processId,
                processStatus,
                healthMessage,
                userId,
                username,
                bindingAgentId,
                bindingStatus,
                bindingUpdatedAt);
    }

    static RuntimeManagementManagedProcess unbound(ManagedOpencodeProcessSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        return new RuntimeManagementManagedProcess(
                snapshot.port(),
                snapshot.pid(),
                snapshot.baseUrl(),
                snapshot.sessionPath(),
                snapshot.configPath(),
                snapshot.startedAt(),
                snapshot.startCommand(),
                snapshot.traceId(),
                snapshot.unifiedAuthId(),
                snapshot.managerStatus(),
                RuntimeManagementManagedProcessOwnership.UNBOUND,
                null,
                null,
                null,
                null,
                Optional.empty(),
                null,
                null,
                null);
    }

    static RuntimeManagementManagedProcess unbound(
            ManagedOpencodeProcessSnapshot snapshot,
            OpencodeServerProcess process) {
        Objects.requireNonNull(process, "process must not be null");
        return withProcess(
                snapshot,
                process,
                RuntimeManagementManagedProcessOwnership.UNBOUND,
                null,
                Optional.empty());
    }

    static RuntimeManagementManagedProcess bound(
            ManagedOpencodeProcessSnapshot snapshot,
            OpencodeServerProcess process,
            UserOpencodeProcessBinding binding,
            Optional<String> username) {
        Objects.requireNonNull(binding, "binding must not be null");
        return withProcess(
                snapshot,
                process,
                RuntimeManagementManagedProcessOwnership.BOUND,
                binding,
                username);
    }

    private static RuntimeManagementManagedProcess withProcess(
            ManagedOpencodeProcessSnapshot snapshot,
            OpencodeServerProcess process,
            RuntimeManagementManagedProcessOwnership ownership,
            UserOpencodeProcessBinding binding,
            Optional<String> username) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(process, "process must not be null");
        return new RuntimeManagementManagedProcess(
                snapshot.port(),
                snapshot.pid(),
                snapshot.baseUrl(),
                snapshot.sessionPath(),
                snapshot.configPath(),
                snapshot.startedAt(),
                snapshot.startCommand(),
                snapshot.traceId(),
                snapshot.unifiedAuthId(),
                snapshot.managerStatus(),
                ownership,
                process.processId(),
                process.status(),
                process.healthMessage(),
                binding == null ? null : binding.userId(),
                username,
                binding == null ? null : binding.agentId(),
                binding == null ? null : binding.status(),
                binding == null ? null : binding.updatedAt());
    }
}
