package com.icbc.testagent.api.web.platform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.opencode.runtime.run.StartRunInput;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerBackendEndpoint;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessStatusResponse;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.TokenUsage;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionMessage;
import com.icbc.testagent.domain.session.SessionMessageRole;
import com.icbc.testagent.domain.workspace.Workspace;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Runtime API DTO 集合，统一隔离 HTTP 契约与 domain 对象，避免 Controller 直接返回领域模型。
 */
final class RuntimeDtos {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> MESSAGE_PARTS_TYPE = new TypeReference<>() {
    };

    /**
     * DTO 容器不允许实例化，所有类型都通过嵌套 record 暴露。
     */
    private RuntimeDtos() {
    }

    /**
     * 创建工作区请求体。
     */
    record CreateWorkspaceRequest(@NotBlank String name, @NotBlank String rootPath) {
    }

    /**
     * 写入文件内容请求体，content 允许为空字符串。
     */
    record WriteFileRequest(@NotBlank String path, String content) {
    }

    /**
     * 创建会话请求体。
     */
    record CreateSessionRequest(@NotBlank String workspaceId, @NotBlank String title) {
    }

    /**
     * 局部更新会话请求体，空字段表示不修改。
     */
    record UpdateSessionRequest(String title, Boolean pinned) {
    }

    /**
     * 追加会话消息请求体，role 可空时由应用层按默认角色处理。
     */
    record AppendMessageRequest(SessionMessageRole role, @NotBlank String content) {
    }

    /**
     * 启动运行请求体，兼容旧 prompt 字符串和新 prompt parts。
     */
    record StartRunRequest(
            @NotBlank String sessionId,
            String prompt,
            List<PromptPartRequest> parts,
            String messageId,
            String agent,
            String model,
            String variant,
            String mode) {

        /**
         * 校验 prompt 或文本 part 至少提供一个，避免空运行进入应用层。
         */
        @AssertTrue(message = "prompt or text part must not be blank")
        boolean hasPromptText() {
            return !effectivePrompt().isBlank();
        }

        /**
         * 返回应用层可直接使用的文本 prompt，优先使用旧字段以保持兼容。
         */
        String effectivePrompt() {
            if (prompt != null && !prompt.isBlank()) {
                return prompt;
            }
            return textFromParts();
        }

        /**
         * 将 HTTP 请求转换为运行应用服务输入对象。
         */
        StartRunInput toInput() {
            List<StartRunInput.PromptPart> mappedParts = parts == null
                    ? List.of()
                    : parts.stream()
                    .filter(part -> part != null && part.type() != null && !part.type().isBlank())
                    .map(PromptPartRequest::toInputPart)
                    .toList();
            return new StartRunInput(new SessionId(sessionId), prompt, mappedParts, messageId, agent, model, variant, mode);
        }

        /**
         * 从文本 parts 拼出兼容旧 prompt 字段的内容。
         */
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

    /**
     * Prompt part 请求体，字段集合对齐前端 Phase 11 输入模型。
     */
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

        /**
         * 转换为应用层 prompt part，保留扩展字段供后续 opencode facade 使用。
         */
        StartRunInput.PromptPart toInputPart() {
            return new StartRunInput.PromptPart(
                    type,
                    text,
                    path,
                    name,
                    mimeType,
                    content,
                    url,
                    agentId,
                    id,
                    label,
                    uri,
                    source,
                    metadata);
        }
    }

    /**
     * manager discovery 返回的后端实例直连端点 DTO。
     */
    record ManagerBackendResponse(
            String backendProcessId,
            String linuxServerId,
            String listenUrl,
            String webSocketUrl,
            Instant lastHeartbeatAt) {

        static ManagerBackendResponse from(ManagerBackendEndpoint endpoint) {
            return new ManagerBackendResponse(
                    endpoint.backendProcessId(),
                    endpoint.linuxServerId(),
                    endpoint.listenUrl(),
                    endpoint.webSocketUrl(),
                    endpoint.lastHeartbeatAt());
        }
    }

    /**
     * 工作区响应 DTO。
     */
    record WorkspaceResponse(
            String workspaceId,
            String name,
            String rootPath,
            String status,
            Instant createdAt,
            Instant updatedAt) {

        /**
         * 从领域对象映射为 API 响应，避免直接暴露 domain 类型。
         */
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

    /**
     * 会话响应 DTO。
     */
    record SessionResponse(
            String sessionId,
            String workspaceId,
            String title,
            String status,
            boolean pinned,
            Instant createdAt,
            Instant updatedAt) {

        /**
         * 从领域会话映射为 API 响应。
         */
        static SessionResponse from(Session session) {
            return new SessionResponse(
                    session.sessionId().value(),
                    session.workspaceId().value(),
                    session.title(),
                    session.status().name(),
                    session.pinned(),
                    session.createdAt(),
                    session.updatedAt());
        }
    }

    /**
     * 会话消息响应 DTO。
     */
    record SessionMessageResponse(
            String messageId,
            String sessionId,
            String role,
            String content,
            Instant createdAt,
            String runId,
            String remoteMessageId,
            List<Map<String, Object>> parts,
            TokenUsageResponse tokens,
            BigDecimal costUsd,
            Instant updatedAt) {

        /**
         * 从领域消息映射为 API 响应。
         */
        static SessionMessageResponse from(SessionMessage message) {
            return new SessionMessageResponse(
                    message.messageId().value(),
                    message.sessionId().value(),
                    message.role().name(),
                    message.content(),
                    message.createdAt(),
                    message.runId() == null ? null : message.runId().value(),
                    message.remoteMessageId(),
                    parseParts(message.partsJson()),
                    TokenUsageResponse.from(message.tokenUsage()),
                    message.costUsd(),
                    message.updatedAt());
        }
    }

    /**
     * token 消耗响应 DTO，字段可空以兼容 agent 未返回局部统计的情况。
     */
    record TokenUsageResponse(
            Long input,
            Long output,
            Long reasoning,
            Long cacheRead,
            Long cacheWrite) {

        static TokenUsageResponse from(TokenUsage tokenUsage) {
            if (tokenUsage == null || tokenUsage.isEmpty()) {
                return null;
            }
            return new TokenUsageResponse(
                    tokenUsage.input(),
                    tokenUsage.output(),
                    tokenUsage.reasoning(),
                    tokenUsage.cacheRead(),
                    tokenUsage.cacheWrite());
        }
    }

    /**
     * 运行响应 DTO。
     */
    record RunResponse(
            String runId,
            String sessionId,
            String workspaceId,
            String status,
            Instant createdAt,
            Instant updatedAt,
            TokenUsageResponse tokens,
            BigDecimal costUsd) {

        /**
         * 从领域运行对象映射为 API 响应。
         */
        static RunResponse from(Run run) {
            return new RunResponse(
                    run.runId().value(),
                    run.sessionId().value(),
                    run.workspaceId().value(),
                    run.status().name(),
                    run.createdAt(),
                    run.updatedAt(),
                    TokenUsageResponse.from(run.tokenUsage()),
                    run.costUsd());
        }
    }

    private static List<Map<String, Object>> parseParts(String partsJson) {
        if (partsJson == null || partsJson.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(partsJson, MESSAGE_PARTS_TYPE);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    /**
     * 当前用户 opencode 进程状态响应 DTO，供前端决定是否允许对话和是否展示初始化入口。
     */
    record UserOpencodeProcessResponse(
            String status,
            boolean initializable,
            String message,
            String processId,
            String linuxServerId,
            String containerId,
            Integer port,
            String baseUrl,
            Instant checkedAt) {

        /**
         * 从应用层响应映射为 HTTP DTO，避免 Controller 泄露内部枚举对象。
         */
        static UserOpencodeProcessResponse from(UserOpencodeProcessStatusResponse response) {
            return new UserOpencodeProcessResponse(
                    response.status().name(),
                    response.initializable(),
                    response.message(),
                    response.processId(),
                    response.linuxServerId(),
                    response.containerId(),
                    response.port(),
                    response.baseUrl(),
                    response.checkedAt());
        }
    }

    /**
     * 单文件 diff 响应 DTO。
     */
    record RunDiffFileResponse(
            String path,
            String patch,
            long additions,
            long deletions,
            String status) {

        /**
         * 从运行态 diff 文件响应映射为 API DTO。
         */
        static RunDiffFileResponse from(com.icbc.testagent.opencode.runtime.run.RunDiffFileResponse file) {
            return new RunDiffFileResponse(
                    file.path(),
                    file.patch(),
                    file.additions(),
                    file.deletions(),
                    file.status());
        }
    }

    /**
     * 运行 diff 响应 DTO。
     */
    record RunDiffResponse(
            String runId,
            List<RunDiffFileResponse> files) {

        /**
         * 从运行态 diff 响应映射为 API DTO，并递归映射文件列表。
         */
        static RunDiffResponse from(com.icbc.testagent.opencode.runtime.run.RunDiffResponse diff) {
            return new RunDiffResponse(
                    diff.runId(),
                    diff.files().stream().map(RunDiffFileResponse::from).toList());
        }
    }

    /**
     * diff 动作响应 DTO。
     */
    record RunDiffActionResponse(
            String runId,
            String action,
            String status,
            int fileCount) {

        /**
         * 从运行态 diff 动作结果映射为 API DTO。
         */
        static RunDiffActionResponse from(com.icbc.testagent.opencode.runtime.run.RunDiffActionResponse action) {
            return new RunDiffActionResponse(
                    action.runId(),
                    action.action(),
                    action.status(),
                    action.fileCount());
        }
    }

    /**
     * 映射工作区分页响应。
     */
    static PageResponse<WorkspaceResponse> workspacePage(PageResponse<Workspace> page) {
        return new PageResponse<>(page.items().stream().map(WorkspaceResponse::from).toList(), page.page(), page.size(), page.total());
    }

    /**
     * 映射会话分页响应。
     */
    static PageResponse<SessionResponse> sessionPage(PageResponse<Session> page) {
        return new PageResponse<>(page.items().stream().map(SessionResponse::from).toList(), page.page(), page.size(), page.total());
    }

    /**
     * 映射会话消息分页响应。
     */
    static PageResponse<SessionMessageResponse> messagePage(PageResponse<SessionMessage> page) {
        List<SessionMessageResponse> items = page.items().stream().map(SessionMessageResponse::from).toList();
        return new PageResponse<>(items, page.page(), page.size(), page.total());
    }
}
