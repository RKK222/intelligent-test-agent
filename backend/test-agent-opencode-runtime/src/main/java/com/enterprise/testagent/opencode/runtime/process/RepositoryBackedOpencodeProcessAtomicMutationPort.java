package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessAssignmentConflictException;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessAtomicMutationPort;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import java.util.Objects;

/**
 * 无 Spring/MyBatis 装配场景的兼容实现，供既有单测和手工构造器使用。
 *
 * <p>生产 Spring 构造器必须注入数据库 CAS 端口；这里仅在单进程内先比较两个权威对象再保存，
 * 不作为跨节点并发保证。
 */
final class RepositoryBackedOpencodeProcessAtomicMutationPort implements OpencodeProcessAtomicMutationPort {

    private final OpencodeProcessManagementRepository repository;

    RepositoryBackedOpencodeProcessAtomicMutationPort(OpencodeProcessManagementRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public synchronized void compareAndSetAssignment(
            OpencodeServerProcess expectedProcess,
            UserOpencodeProcessBinding expectedBinding,
            OpencodeServerProcess replacementProcess,
            UserOpencodeProcessBinding replacementBinding) {
        OpencodeServerProcess currentProcess = repository
                .findOpencodeServerProcessById(expectedProcess.processId())
                .orElseThrow(() -> conflict("TestAgent 进程已不存在"));
        UserOpencodeProcessBinding currentBinding = repository
                .findUserBinding(expectedBinding.userId(), expectedBinding.agentId())
                .orElseThrow(() -> conflict("TestAgent 用户绑定已不存在"));
        if (!sameRuntimeGeneration(currentProcess, expectedProcess)
                || !sameBindingAssignment(currentBinding, expectedBinding)) {
            throw conflict("TestAgent 分配已被并发修改");
        }
        repository.saveOpencodeServerProcess(replacementProcess);
        repository.saveUserBinding(replacementBinding);
    }

    @Override
    public synchronized boolean compareAndSetRuntimeState(
            OpencodeServerProcess expectedAssignment,
            OpencodeServerProcess replacementState) {
        return repository.findOpencodeServerProcessById(expectedAssignment.processId())
                .filter(current -> sameRuntimeGeneration(current, expectedAssignment))
                .map(current -> {
                    repository.saveOpencodeServerProcess(replacementState);
                    return true;
                })
                .orElse(false);
    }

    private boolean sameProcessAssignment(OpencodeServerProcess left, OpencodeServerProcess right) {
        return left.processId().equals(right.processId())
                && left.userId().equals(right.userId())
                && left.linuxServerId().equals(right.linuxServerId())
                && left.containerId().equals(right.containerId())
                && left.port() == right.port();
    }

    /**
     * 运行态 CAS 还必须匹配旧生命周期标识；仅比较 assignment 坐标会允许迟到 PID 覆盖同端口新实例。
     */
    private boolean sameRuntimeGeneration(OpencodeServerProcess left, OpencodeServerProcess right) {
        return sameProcessAssignment(left, right)
                && Objects.equals(left.pid(), right.pid())
                && left.status() == right.status()
                && Objects.equals(left.traceId(), right.traceId());
    }

    private boolean sameBindingAssignment(
            UserOpencodeProcessBinding left,
            UserOpencodeProcessBinding right) {
        return left.userId().equals(right.userId())
                && left.agentId().equals(right.agentId())
                && left.processId().equals(right.processId())
                && left.linuxServerId().equals(right.linuxServerId())
                && left.port() == right.port()
                && left.status() == right.status();
    }

    private OpencodeProcessAssignmentConflictException conflict(String message) {
        return new OpencodeProcessAssignmentConflictException(message);
    }
}
