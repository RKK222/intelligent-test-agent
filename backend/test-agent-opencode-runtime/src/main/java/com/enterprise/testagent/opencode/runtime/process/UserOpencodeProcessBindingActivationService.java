package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessAtomicMutationPort;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 管理员显式关闭与重新启动之间的用户进程绑定状态转换服务。
 *
 * <p>INACTIVE 只表达显式管理动作；普通健康失败、自动闲置停止和短暂离线继续保留 ACTIVE，
 * 从而让前端只隐藏被明确关闭的服务端 OpenCode 投影。</p>
 */
@Service
public class UserOpencodeProcessBindingActivationService {

    private static final String OPENCODE_AGENT_ID = "opencode";

    private final OpencodeProcessManagementRepository repository;
    private final OpencodeProcessAtomicMutationPort atomicMutationPort;
    private final Clock clock;

    @Autowired
    public UserOpencodeProcessBindingActivationService(
            OpencodeProcessManagementRepository repository,
            OpencodeProcessAtomicMutationPort atomicMutationPort) {
        this(repository, atomicMutationPort, Clock.systemUTC());
    }

    UserOpencodeProcessBindingActivationService(
            OpencodeProcessManagementRepository repository,
            OpencodeProcessAtomicMutationPort atomicMutationPort,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.atomicMutationPort = Objects.requireNonNull(atomicMutationPort, "atomicMutationPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /** 管理员停止确认完成后，把仍指向同一进程的 ACTIVE binding 标记为 INACTIVE。 */
    public boolean deactivateAfterAdministrativeStop(OpencodeServerProcess process, String traceId) {
        return transition(
                process,
                UserOpencodeProcessBindingStatus.ACTIVE,
                UserOpencodeProcessBindingStatus.INACTIVE,
                traceId,
                null);
    }

    /** 显式重启前恢复同一进程的 INACTIVE binding；历史无 binding 进程继续沿用原管理语义。 */
    public boolean activateForExplicitRestart(OpencodeServerProcess process, String traceId) {
        return transition(
                process,
                UserOpencodeProcessBindingStatus.INACTIVE,
                UserOpencodeProcessBindingStatus.ACTIVE,
                traceId,
                null);
    }

    /** 重启失败时只撤销由本次 trace 激活的 binding，避免迟到补偿隐藏并发成功的新重启。 */
    public boolean restoreInactiveAfterFailedRestart(OpencodeServerProcess process, String activationTraceId) {
        return transition(
                process,
                UserOpencodeProcessBindingStatus.ACTIVE,
                UserOpencodeProcessBindingStatus.INACTIVE,
                activationTraceId,
                activationTraceId);
    }

    /** 当前 binding 必须已恢复为 ACTIVE 且仍精确指向原进程。 */
    public void requireActiveBinding(OpencodeServerProcess process) {
        UserOpencodeProcessBinding binding = matchingBinding(process)
                .orElseThrow(() -> unavailable("TestAgent 用户绑定已变化"));
        if (binding.status() != UserOpencodeProcessBindingStatus.ACTIVE) {
            throw unavailable("TestAgent 用户绑定尚未恢复");
        }
    }

    private boolean transition(
            OpencodeServerProcess expectedProcess,
            UserOpencodeProcessBindingStatus expectedStatus,
            UserOpencodeProcessBindingStatus replacementStatus,
            String traceId,
            String requiredBindingTraceId) {
        Objects.requireNonNull(expectedProcess, "process must not be null");
        OpencodeServerProcess currentProcess = repository
                .findOpencodeServerProcessById(expectedProcess.processId())
                .filter(current -> sameAssignment(current, expectedProcess))
                .orElseThrow(() -> unavailable("TestAgent 进程分配已变化"));
        Optional<UserOpencodeProcessBinding> bindingOptional = matchingBinding(currentProcess);
        if (bindingOptional.isEmpty()) {
            return false;
        }
        UserOpencodeProcessBinding binding = bindingOptional.get();
        if (binding.status() == replacementStatus) {
            return false;
        }
        if (binding.status() != expectedStatus
                || (requiredBindingTraceId != null && !requiredBindingTraceId.equals(binding.traceId()))) {
            return false;
        }
        Instant updatedAt = clock.instant();
        if (updatedAt.isBefore(binding.createdAt())) {
            updatedAt = binding.createdAt();
        }
        UserOpencodeProcessBinding replacement = new UserOpencodeProcessBinding(
                binding.userId(),
                binding.agentId(),
                binding.processId(),
                binding.linuxServerId(),
                binding.port(),
                replacementStatus,
                binding.createdAt(),
                updatedAt,
                traceId);
        atomicMutationPort.compareAndSetAssignment(currentProcess, binding, currentProcess, replacement);
        return true;
    }

    private Optional<UserOpencodeProcessBinding> matchingBinding(OpencodeServerProcess process) {
        Optional<UserOpencodeProcessBinding> current = repository.findUserBinding(
                process.userId(), OPENCODE_AGENT_ID);
        if (current.isEmpty()) {
            current = Optional.ofNullable(repository
                    .findUserBindingsByProcessIds(List.of(process.processId()))
                    .get(process.processId()));
        }
        return current.filter(binding -> binding.processId().equals(process.processId()))
                .filter(binding -> binding.linuxServerId().equals(process.linuxServerId()))
                .filter(binding -> binding.port() == process.port());
    }

    private boolean sameAssignment(OpencodeServerProcess left, OpencodeServerProcess right) {
        return left.processId().equals(right.processId())
                && left.userId().equals(right.userId())
                && left.linuxServerId().equals(right.linuxServerId())
                && left.containerId().equals(right.containerId())
                && left.port() == right.port();
    }

    private PlatformException unavailable(String message) {
        return new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, message);
    }
}
