package com.example.testagent.app.web;

import com.example.testagent.common.pagination.PageResponse;
import com.example.testagent.domain.run.Run;
import com.example.testagent.domain.session.Session;
import com.example.testagent.domain.session.SessionMessage;
import com.example.testagent.domain.session.SessionMessageRole;
import com.example.testagent.domain.workspace.Workspace;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Runtime API DTO 集合，统一隔离 HTTP 契约与 domain 对象，避免 Controller 直接返回领域模型。
 */
final class RuntimeDtos {

    private RuntimeDtos() {
    }

    record CreateWorkspaceRequest(@NotBlank String name, @NotBlank String rootPath) {
    }

    record WriteFileRequest(@NotBlank String path, String content) {
    }

    record CreateSessionRequest(@NotBlank String workspaceId, @NotBlank String title) {
    }

    record AppendMessageRequest(SessionMessageRole role, @NotBlank String content) {
    }

    record StartRunRequest(
            @NotBlank String sessionId,
            String prompt,
            List<PromptPartRequest> parts,
            String messageId,
            String agent,
            String model,
            String variant,
            String mode) {

        @AssertTrue(message = "prompt or text part must not be blank")
        boolean hasPromptText() {
            return !effectivePrompt().isBlank();
        }

        String effectivePrompt() {
            if (prompt != null && !prompt.isBlank()) {
                return prompt;
            }
            return textFromParts();
        }

        private String textFromParts() {
            if (parts == null || parts.isEmpty()) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            for (PromptPartRequest part : parts) {
                // 当前 RunApplicationService 仍接收 prompt 字符串；Phase 11 后续批次再把完整 parts 下沉到 facade。
                if (part != null && "text".equals(part.type()) && part.text() != null && !part.text().isBlank()) {
                    if (!builder.isEmpty()) {
                        builder.append('\n');
                    }
                    builder.append(part.text());
                }
            }
            return builder.toString();
        }
    }

    record PromptPartRequest(
            String type,
            String text,
            String path,
            String name,
            String mimeType,
            String content,
            String url,
            String agentId,
            String id,
            String label,
            String uri,
            Map<String, Object> source,
            Map<String, Object> metadata) {
    }

    record WorkspaceResponse(
            String workspaceId,
            String name,
            String rootPath,
            String status,
            Instant createdAt,
            Instant updatedAt) {

        static WorkspaceResponse from(Workspace workspace) {
            return new WorkspaceResponse(
                    workspace.workspaceId().value(),
                    workspace.name(),
                    workspace.rootPath(),
                    workspace.status().name(),
                    workspace.createdAt(),
                    workspace.updatedAt());
        }
    }

    record SessionResponse(
            String sessionId,
            String workspaceId,
            String title,
            String status,
            Instant createdAt,
            Instant updatedAt) {

        static SessionResponse from(Session session) {
            return new SessionResponse(
                    session.sessionId().value(),
                    session.workspaceId().value(),
                    session.title(),
                    session.status().name(),
                    session.createdAt(),
                    session.updatedAt());
        }
    }

    record SessionMessageResponse(
            String messageId,
            String sessionId,
            String role,
            String content,
            Instant createdAt) {

        static SessionMessageResponse from(SessionMessage message) {
            return new SessionMessageResponse(
                    message.messageId().value(),
                    message.sessionId().value(),
                    message.role().name(),
                    message.content(),
                    message.createdAt());
        }
    }

    record RunResponse(
            String runId,
            String sessionId,
            String workspaceId,
            String status,
            Instant createdAt,
            Instant updatedAt) {

        static RunResponse from(Run run) {
            return new RunResponse(
                    run.runId().value(),
                    run.sessionId().value(),
                    run.workspaceId().value(),
                    run.status().name(),
                    run.createdAt(),
                    run.updatedAt());
        }
    }

    record RunDiffFileResponse(
            String path,
            String patch,
            long additions,
            long deletions,
            String status) {

        static RunDiffFileResponse from(com.example.testagent.app.run.RunDiffFileResponse file) {
            return new RunDiffFileResponse(
                    file.path(),
                    file.patch(),
                    file.additions(),
                    file.deletions(),
                    file.status());
        }
    }

    record RunDiffResponse(
            String runId,
            List<RunDiffFileResponse> files) {

        static RunDiffResponse from(com.example.testagent.app.run.RunDiffResponse diff) {
            return new RunDiffResponse(
                    diff.runId(),
                    diff.files().stream().map(RunDiffFileResponse::from).toList());
        }
    }

    record RunDiffActionResponse(
            String runId,
            String action,
            String status,
            int fileCount) {

        static RunDiffActionResponse from(com.example.testagent.app.run.RunDiffActionResponse action) {
            return new RunDiffActionResponse(
                    action.runId(),
                    action.action(),
                    action.status(),
                    action.fileCount());
        }
    }

    static PageResponse<WorkspaceResponse> workspacePage(PageResponse<Workspace> page) {
        return new PageResponse<>(page.items().stream().map(WorkspaceResponse::from).toList(), page.page(), page.size(), page.total());
    }

    static PageResponse<SessionResponse> sessionPage(PageResponse<Session> page) {
        return new PageResponse<>(page.items().stream().map(SessionResponse::from).toList(), page.page(), page.size(), page.total());
    }

    static PageResponse<SessionMessageResponse> messagePage(PageResponse<SessionMessage> page) {
        List<SessionMessageResponse> items = page.items().stream().map(SessionMessageResponse::from).toList();
        return new PageResponse<>(items, page.page(), page.size(), page.total());
    }
}
