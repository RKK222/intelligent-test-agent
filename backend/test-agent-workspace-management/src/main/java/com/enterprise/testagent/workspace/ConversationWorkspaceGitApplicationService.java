package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.agent.AgentSessionBinding;
import com.enterprise.testagent.domain.agent.AgentSessionBindingRepository;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspace;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.session.SessionRepository;
import com.enterprise.testagent.domain.user.UserId;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 对话 Tool 的工作区 Git 编排服务。
 *
 * <p>远端 OpenCode session 只用于反查平台 Session 和当前 runtime workspace；真正的
 * owner、路径、Git 与发布校验全部复用现有托管工作区服务，Tool 不接收可伪造的 workspace ID。</p>
 */
@Service
public class ConversationWorkspaceGitApplicationService {

    private static final String OPENCODE_AGENT_ID = "opencode";

    private final AgentSessionBindingRepository bindingRepository;
    private final SessionRepository sessionRepository;
    private final ManagedWorkspaceRepository managedWorkspaceRepository;
    private final ManagedWorkspaceApplicationService workspaceService;

    public ConversationWorkspaceGitApplicationService(
            AgentSessionBindingRepository bindingRepository,
            SessionRepository sessionRepository,
            ManagedWorkspaceRepository managedWorkspaceRepository,
            ManagedWorkspaceApplicationService workspaceService) {
        this.bindingRepository = Objects.requireNonNull(bindingRepository, "bindingRepository must not be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.managedWorkspaceRepository = Objects.requireNonNull(
                managedWorkspaceRepository, "managedWorkspaceRepository must not be null");
        this.workspaceService = Objects.requireNonNull(workspaceService, "workspaceService must not be null");
    }

    /**
     * 在当前对话绑定的个人工作区执行一个受控 Git 动作。
     */
    public ConversationWorkspaceGitResult execute(
            String remoteSessionId,
            String action,
            List<String> files,
            String message,
            String path,
            String resolution,
            String content,
            String expectedApplicationHead,
            UserId userId,
            Collection<String> roles,
            String traceId) {
        ConversationWorkspace context = resolveWorkspace(remoteSessionId, userId);
        String normalizedAction = requireAction(action);
        List<String> selectedFiles = files == null ? List.of() : List.copyOf(files);
        Object result = switch (normalizedAction) {
            case "STATUS" -> workspaceService.getWorkspaceGitDiff(context.workspaceId(), userId);
            case "PULL" -> workspaceService.gitPullPersonalWorkspace(
                    context.personalWorkspaceId(), userId, traceId);
            case "STAGE" -> {
                requireFiles(selectedFiles);
                ManagedWorkspaceGitPathPolicy.requireWriteAccess(selectedFiles, roles);
                workspaceService.stageWorkspaceGitFiles(context.workspaceId(), selectedFiles, userId);
                yield Map.of("updated", true);
            }
            case "UNSTAGE" -> {
                requireFiles(selectedFiles);
                ManagedWorkspaceGitPathPolicy.requireWriteAccess(selectedFiles, roles);
                workspaceService.unstageWorkspaceGitFiles(context.workspaceId(), selectedFiles, userId);
                yield Map.of("updated", true);
            }
            case "DISCARD" -> {
                requireFiles(selectedFiles);
                ManagedWorkspaceGitPathPolicy.requireWriteAccess(selectedFiles, roles);
                workspaceService.discardWorkspaceGitFiles(
                        context.workspaceId(), selectedFiles, userId, traceId);
                yield Map.of("updated", true);
            }
            case "COMMIT" -> {
                requireFiles(selectedFiles);
                ManagedWorkspaceGitPathPolicy.requireWriteAccess(selectedFiles, roles);
                yield workspaceService.commitPersonalWorkspace(
                        context.personalWorkspaceId(), requireMessage(message), selectedFiles, userId, traceId);
            }
            case "PUBLISH_PREVIEW" -> workspaceService.previewPersonalWorkspacePublish(
                    context.personalWorkspaceId(), userId, traceId);
            case "PUBLISH" -> {
                requireFiles(selectedFiles);
                ManagedWorkspaceGitPathPolicy.requireWriteAccess(selectedFiles, roles);
                yield workspaceService.publishPersonalWorkspace(
                        context.personalWorkspaceId(),
                        requireMessage(message),
                        selectedFiles,
                        expectedApplicationHead,
                        userId,
                        traceId);
            }
            case "CONFLICT" -> workspaceService.getWorkspaceGitConflict(
                    context.workspaceId(), requirePath(path), userId);
            case "RESOLVE_CONFLICT" -> {
                ManagedWorkspaceGitPathPolicy.requireWriteAccess(List.of(requirePath(path)), roles);
                workspaceService.resolveWorkspaceGitConflict(
                        context.workspaceId(), path, resolution, content, userId);
                yield Map.of("updated", true);
            }
            case "ABORT_MERGE" -> {
                requireConflictWriteAccess(context.workspaceId(), userId, roles, true);
                workspaceService.abortWorkspaceGitConflict(context.workspaceId(), userId);
                yield Map.of("updated", true);
            }
            case "COMPLETE_MERGE" -> {
                requireConflictWriteAccess(context.workspaceId(), userId, roles, false);
                yield workspaceService.completeWorkspaceGitMerge(context.workspaceId(), userId, traceId);
            }
            default -> throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "不支持的对话 Git 操作",
                    Map.of("action", normalizedAction));
        };
        return new ConversationWorkspaceGitResult(
                normalizedAction,
                context.workspaceId(),
                context.personalWorkspaceId(),
                result);
    }

    private ConversationWorkspace resolveWorkspace(String remoteSessionId, UserId userId) {
        String normalizedSessionId = requireText(remoteSessionId, "远端会话 ID 不能为空", "sessionId");
        AgentSessionBinding binding = bindingRepository
                .findByAgentIdAndRemoteSessionId(OPENCODE_AGENT_ID, normalizedSessionId)
                .orElseThrow(() -> new PlatformException(ErrorCode.FORBIDDEN, "当前对话没有可操作的工作区"));
        Session session = sessionRepository.findById(binding.sessionId())
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "平台会话不存在"));
        PersonalWorkspace personal = managedWorkspaceRepository
                .findPersonalWorkspaceByRuntimeWorkspace(session.workspaceId())
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.CONFLICT,
                        "当前对话未绑定个人 workspace，请先切换到个人 workspace"));
        if (!personal.userId().equals(userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "只能操作自己的个人 workspace");
        }
        return new ConversationWorkspace(
                personal.runtimeWorkspaceId().value(), personal.personalWorkspaceId().value());
    }

    private void requireConflictWriteAccess(
            String workspaceId,
            UserId userId,
            Collection<String> roles,
            boolean conflictsOnly) {
        if (ManagedWorkspaceGitPathPolicy.hasApplicationAdminRole(roles)) {
            return;
        }
        var diff = workspaceService.getWorkspaceGitDiff(workspaceId, userId);
        List<String> protectedFiles = diff.files().stream()
                .filter(file -> !conflictsOnly || "conflict".equalsIgnoreCase(file.status()))
                .map(ManagedWorkspaceResponses.WorkspaceGitDiffFileResponse::path)
                .filter(ManagedWorkspaceGitPathPolicy::isApplicationConfigPath)
                .toList();
        ManagedWorkspaceGitPathPolicy.requireWriteAccess(protectedFiles, roles);
    }

    private String requireAction(String action) {
        return requireText(action, "Git 操作不能为空", "action").toUpperCase(Locale.ROOT);
    }

    private void requireFiles(List<String> files) {
        if (files.isEmpty()) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "请明确选择要操作的文件",
                    Map.of("field", "files"));
        }
    }

    private String requireMessage(String message) {
        return requireText(message, "提交说明不能为空", "message");
    }

    private String requirePath(String path) {
        return requireText(path, "冲突文件路径不能为空", "path");
    }

    private String requireText(String value, String message, String field) {
        if (value == null || value.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, message, Map.of("field", field));
        }
        return value.trim();
    }

    private record ConversationWorkspace(String workspaceId, String personalWorkspaceId) {
    }

    /** Tool 返回稳定的上下文标识和既有业务响应。 */
    public record ConversationWorkspaceGitResult(
            String action,
            String workspaceId,
            String personalWorkspaceId,
            Object result) {
    }
}
