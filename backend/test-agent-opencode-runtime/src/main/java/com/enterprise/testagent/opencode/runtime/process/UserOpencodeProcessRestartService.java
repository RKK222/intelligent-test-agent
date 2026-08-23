package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.session.SessionRuntimeStateSummary;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.run.RunApplicationService;
import com.enterprise.testagent.opencode.runtime.session.SessionRuntimeStateApplicationService;
import com.enterprise.testagent.opencode.runtime.session.UserRuntimeDisposeCoordinator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 当前用户 TestAgent 进程重启编排服务。
 *
 * <p>重启与普通 dispose 共用用户级维护闸门；确认后先把用户全部活动 Run 写入终态，
 * 再复用公共停止和分配恢复程序，禁止业务入口直接操作 manager 或自行回写进程状态。
 */
@Service
public class UserOpencodeProcessRestartService {

    private static final String OPENCODE_AGENT_ID = "opencode";

    private final OpencodeProcessManagementRepository repository;
    private final SessionRuntimeStateApplicationService runtimeStateService;
    private final RunApplicationService runService;
    private final OpencodeProcessStopService stopService;
    private final UserOpencodeProcessAssignmentService assignmentService;
    private final UserRuntimeDisposeCoordinator disposeCoordinator;
    private final UserOpencodeProcessBindingActivationService bindingActivationService;

    public UserOpencodeProcessRestartService(
            OpencodeProcessManagementRepository repository,
            SessionRuntimeStateApplicationService runtimeStateService,
            RunApplicationService runService,
            OpencodeProcessStopService stopService,
            UserOpencodeProcessAssignmentService assignmentService,
            UserRuntimeDisposeCoordinator disposeCoordinator) {
        this(
                repository,
                runtimeStateService,
                runService,
                stopService,
                assignmentService,
                disposeCoordinator,
                new UserOpencodeProcessBindingActivationService(
                        repository,
                        new RepositoryBackedOpencodeProcessAtomicMutationPort(repository)));
    }

    /** Spring 生产入口额外注入显式关闭 binding 的恢复服务。 */
    @Autowired
    public UserOpencodeProcessRestartService(
            OpencodeProcessManagementRepository repository,
            SessionRuntimeStateApplicationService runtimeStateService,
            RunApplicationService runService,
            OpencodeProcessStopService stopService,
            UserOpencodeProcessAssignmentService assignmentService,
            UserRuntimeDisposeCoordinator disposeCoordinator,
            UserOpencodeProcessBindingActivationService bindingActivationService) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.runtimeStateService = Objects.requireNonNull(runtimeStateService, "runtimeStateService must not be null");
        this.runService = Objects.requireNonNull(runService, "runService must not be null");
        this.stopService = Objects.requireNonNull(stopService, "stopService must not be null");
        this.assignmentService = Objects.requireNonNull(assignmentService, "assignmentService must not be null");
        this.disposeCoordinator = Objects.requireNonNull(disposeCoordinator, "disposeCoordinator must not be null");
        this.bindingActivationService = Objects.requireNonNull(
                bindingActivationService, "bindingActivationService must not be null");
    }

    /**
     * 重启当前用户的服务端 OpenCode；活动 Run 的存在与数量始终以后端租约内重新读取的快照为准。
     */
    public UserOpencodeProcessStatusResponse restart(
            UserId userId,
            String agentId,
            boolean confirmRunning,
            String traceId) {
        Objects.requireNonNull(userId, "userId must not be null");
        String normalizedAgentId = normalizeAgentId(agentId);
        return disposeCoordinator.withUserMaintenance(userId, traceId, guard -> {
            UserOpencodeProcessBinding binding = requireRestartableBinding(userId, normalizedAgentId);
            OpencodeServerProcess process = requireBoundProcess(userId, binding);
            SessionRuntimeStateSummary initial = runtimeStateService.snapshot(userId);
            if (initial.runningCount() > 0 && !confirmRunning) {
                throw confirmationRequired(initial.runningCount());
            }
            if (initial.runningCount() > 0) {
                cancelActiveRuns(initial, traceId);
                SessionRuntimeStateSummary remaining = runtimeStateService.snapshot(userId);
                if (remaining.runningCount() > 0) {
                    throw runsStillActive(remaining.runningCount());
                }
            }
            guard.requireActive();
            if (binding.status() == UserOpencodeProcessBindingStatus.INACTIVE) {
                if (process.status() != OpencodeServerProcessStatus.STOPPED) {
                    throw new PlatformException(
                            ErrorCode.OPENCODE_UNAVAILABLE,
                            "管理员关闭的 TestAgent 进程状态已变化，请重试");
                }
                boolean activated = bindingActivationService.activateForExplicitRestart(process, traceId);
                try {
                    bindingActivationService.requireActiveBinding(process);
                    guard.requireActive();
                    return assignmentService.initialize(userId, normalizedAgentId, traceId);
                } catch (RuntimeException exception) {
                    if (activated) {
                        try {
                            bindingActivationService.restoreInactiveAfterFailedRestart(process, traceId);
                        } catch (RuntimeException compensationFailure) {
                            exception.addSuppressed(compensationFailure);
                        }
                    }
                    throw exception;
                }
            }
            stopService.stopAndVerify(OpencodeProcessStopRequest.tracked(process, traceId));
            guard.requireActive();
            return assignmentService.initialize(userId, normalizedAgentId, traceId);
        });
    }

    /** 相同 Run 可能被多条 Session 摘要引用；重启只对唯一 runId 发起一次取消。 */
    private void cancelActiveRuns(SessionRuntimeStateSummary summary, String traceId) {
        Set<RunId> runIds = new LinkedHashSet<>();
        summary.sessions().forEach(state -> runIds.add(state.runId()));
        runIds.forEach(runId -> runService.cancelRun(runId, traceId));
    }

    private UserOpencodeProcessBinding requireRestartableBinding(UserId userId, String agentId) {
        return repository.findUserBinding(userId, agentId)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "当前用户尚未分配可重启的 TestAgent 进程"));
    }

    /** 固定 binding 指向的权威进程代次，公共停止服务还会在 manager stop 前再次校验 PID/startedAt。 */
    private OpencodeServerProcess requireBoundProcess(UserId userId, UserOpencodeProcessBinding binding) {
        return repository.findOpencodeServerProcessById(binding.processId())
                .filter(process -> process.userId().equals(userId))
                .filter(process -> process.linuxServerId().equals(binding.linuxServerId()))
                .filter(process -> process.port() == binding.port())
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "当前用户的 TestAgent 进程分配已变化，请重试"));
    }

    private PlatformException confirmationRequired(int runningCount) {
        return new PlatformException(
                ErrorCode.CONFLICT,
                "当前用户仍有运行中的对话，确认后将先中止这些任务再重启进程",
                Map.of("confirmationRequired", true, "runningCount", runningCount));
    }

    private PlatformException runsStillActive(int runningCount) {
        return new PlatformException(
                ErrorCode.CONFLICT,
                "仍有活动 Run 无法进入终态，已中止进程重启",
                Map.of("confirmationRequired", false, "runningCount", runningCount));
    }

    private String normalizeAgentId(String agentId) {
        String normalized = agentId == null ? "" : agentId.trim().toLowerCase(Locale.ROOT);
        if (!OPENCODE_AGENT_ID.equals(normalized)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "当前只支持 TestAgent 用户进程");
        }
        return normalized;
    }
}
