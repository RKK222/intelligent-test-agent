package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.workspace.WorkspaceGitAccessCheck;
import com.enterprise.testagent.domain.workspace.WorkspaceGitAccessCheckRepository;
import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 分页、并发复用现有版本 Git 只读预检，刷新服务器工作空间的用户级权限投影。 */
@Service
public class WorkspaceGitAccessInspectionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkspaceGitAccessInspectionService.class);
    static final int PAGE_SIZE = 200;
    static final int MAX_CONCURRENCY = 8;

    private final WorkspaceGitAccessCheckRepository checks;
    private final ManagedWorkspaceApplicationService managedWorkspaces;
    private final Clock clock;

    public WorkspaceGitAccessInspectionService(
            WorkspaceGitAccessCheckRepository checks,
            ManagedWorkspaceApplicationService managedWorkspaces,
            Clock clock) {
        this.checks = Objects.requireNonNull(checks);
        this.managedWorkspaces = Objects.requireNonNull(managedWorkspaces);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 每轮完整扫描 ACTIVE 用户可见的启用模板；单个仓库失败不会中断其它工作空间。 */
    public InspectionResult inspectApplicationWorkspaces(ScheduledTaskContext context) {
        int checked = 0;
        int accessible = 0;
        int inaccessible = 0;
        int unknown = 0;
        String afterUserId = null;
        String afterWorkspaceId = null;

        try (ExecutorService executor = Executors.newFixedThreadPool(
                MAX_CONCURRENCY,
                Thread.ofVirtual().name("workspace-git-access-", 0).factory())) {
            while (true) {
                context.throwIfStopRequested();
                List<WorkspaceGitAccessCheckRepository.ApplicationWorkspaceCandidate> page =
                        checks.findApplicationWorkspaceCandidatesAfter(
                                afterUserId, afterWorkspaceId, PAGE_SIZE);
                if (page.isEmpty()) {
                    break;
                }
                List<Future<WorkspaceGitAccessCheck>> futures = new ArrayList<>(page.size());
                for (var candidate : page) {
                    futures.add(executor.submit(() -> inspect(candidate)));
                }
                for (Future<WorkspaceGitAccessCheck> future : futures) {
                    context.throwIfStopRequested();
                    WorkspaceGitAccessCheck check = await(future, futures);
                    checks.save(check);
                    checked++;
                    switch (check.status()) {
                        case ACCESSIBLE -> accessible++;
                        case INACCESSIBLE -> inaccessible++;
                        case UNKNOWN -> unknown++;
                    }
                }
                var last = page.get(page.size() - 1);
                afterUserId = last.userId().value();
                afterWorkspaceId = last.applicationWorkspaceId();
                if (page.size() < PAGE_SIZE) {
                    break;
                }
            }
        }
        return new InspectionResult(checked, accessible, inaccessible, unknown);
    }

    private WorkspaceGitAccessCheck inspect(
            WorkspaceGitAccessCheckRepository.ApplicationWorkspaceCandidate candidate) {
        Instant checkedAt = clock.instant();
        try {
            ManagedWorkspaceResponses.GitRepositoryAccessResponse access =
                    managedWorkspaces.checkVersionGitAccess(candidate.versionId(), candidate.userId());
            if (access.accessible()) {
                return accessible(candidate, checkedAt);
            }
            String reason = Objects.toString(access.reason(), "REPOSITORY_PERMISSION_REQUIRED");
            return inaccessible(candidate, reason, inaccessibleMessage(reason), checkedAt);
        } catch (PlatformException exception) {
            String reason = transientReason(exception);
            LOGGER.warn(
                    "event=workspace_git_access_inspection_unknown targetType=APPLICATION_WORKSPACE errorCode={} reason={}",
                    exception.errorCode(),
                    reason);
            return unknown(candidate, reason, unknownMessage(reason), checkedAt);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "event=workspace_git_access_inspection_unknown targetType=APPLICATION_WORKSPACE errorType={}",
                    exception.getClass().getSimpleName());
            return unknown(candidate, "INSPECTION_FAILED", "Git 权限巡检暂时无法完成", checkedAt);
        }
    }

    private WorkspaceGitAccessCheck await(
            Future<WorkspaceGitAccessCheck> future,
            List<Future<WorkspaceGitAccessCheck>> page) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            page.forEach(candidate -> candidate.cancel(true));
            Thread.currentThread().interrupt();
            throw new IllegalStateException("workspace Git access inspection interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("workspace Git access inspection failed", cause);
        }
    }

    private WorkspaceGitAccessCheck accessible(
            WorkspaceGitAccessCheckRepository.ApplicationWorkspaceCandidate candidate,
            Instant checkedAt) {
        return new WorkspaceGitAccessCheck(
                candidate.userId(),
                WorkspaceGitAccessCheck.TargetKind.APPLICATION_WORKSPACE,
                candidate.applicationWorkspaceId(),
                WorkspaceGitAccessCheck.Status.ACCESSIBLE,
                null,
                null,
                checkedAt);
    }

    private WorkspaceGitAccessCheck inaccessible(
            WorkspaceGitAccessCheckRepository.ApplicationWorkspaceCandidate candidate,
            String reason,
            String message,
            Instant checkedAt) {
        return new WorkspaceGitAccessCheck(
                candidate.userId(),
                WorkspaceGitAccessCheck.TargetKind.APPLICATION_WORKSPACE,
                candidate.applicationWorkspaceId(),
                WorkspaceGitAccessCheck.Status.INACCESSIBLE,
                reason,
                message,
                checkedAt);
    }

    private WorkspaceGitAccessCheck unknown(
            WorkspaceGitAccessCheckRepository.ApplicationWorkspaceCandidate candidate,
            String reason,
            String message,
            Instant checkedAt) {
        return new WorkspaceGitAccessCheck(
                candidate.userId(),
                WorkspaceGitAccessCheck.TargetKind.APPLICATION_WORKSPACE,
                candidate.applicationWorkspaceId(),
                WorkspaceGitAccessCheck.Status.UNKNOWN,
                reason,
                message,
                checkedAt);
    }

    private static String inaccessibleMessage(String reason) {
        return "SSH_KEY_MISSING".equals(reason)
                ? "未配置 Git SSH key"
                : "Git 仓库读取权限已失效";
    }

    private static String transientReason(PlatformException exception) {
        if (exception.errorCode() == ErrorCode.GIT_TIMEOUT) {
            return "TIMEOUT";
        }
        Object failureType = exception.details().get("gitFailureType");
        return failureType == null ? "INSPECTION_FAILED" : failureType.toString();
    }

    private static String unknownMessage(String reason) {
        return switch (reason) {
            case "NETWORK_UNAVAILABLE" -> "Git 远端网络暂不可用，权限状态待下次巡检确认";
            case "TIMEOUT" -> "Git 远端响应超时，权限状态待下次巡检确认";
            default -> "Git 权限巡检暂时无法完成";
        };
    }

    /** 任务结果只包含计数，不暴露用户、仓库或工作空间标识。 */
    public record InspectionResult(int checked, int accessible, int inaccessible, int unknown) {
    }
}
