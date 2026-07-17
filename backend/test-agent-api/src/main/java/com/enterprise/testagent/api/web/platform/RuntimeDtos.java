package com.enterprise.testagent.api.web.platform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.opencode.runtime.run.StartRunInput;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerBackendEndpoint;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessStartOperation;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessStartOperationStatus;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessStartOperationStep;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessWeakHealthResponse;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessStatusResponse;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunStorageMode;
import com.enterprise.testagent.domain.run.TokenUsage;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.session.SessionHistoryItem;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionMessage;
import com.enterprise.testagent.domain.session.SessionMessageRole;
import com.enterprise.testagent.domain.session.SessionRuntimeState;
import com.enterprise.testagent.domain.session.SessionRuntimeStateSummary;
import com.enterprise.testagent.domain.session.SessionWorkspaceContext;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.event.RunEventSsePayload;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
            String contextToken,
            String clientRequestId,
            String prompt,
            List<PromptPartRequest> parts,
            String messageId,
            String agent,
            String model,
            String variant,
            String mode,
            String command,
            String arguments) {

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
            return new StartRunInput(
                    new SessionId(sessionId),
                    prompt,
                    mappedParts,
                    messageId,
                    agent,
                    model,
                    variant,
                    mode,
                    command,
                    arguments,
                    contextToken,
                    clientRequestId);
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
     * manager 后端列表响应和兼容诊断接口返回的后端实例直连端点 DTO。
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
            String linuxServerId,
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
                    workspace.linuxServerId(),
                    workspace.createdAt(),
                    workspace.updatedAt());
        }
    }

    /**
     * 历史会话所属工作区上下文响应 DTO。
     */
    record SessionWorkspaceContextResponse(
            String appId,
            String appName,
            String applicationWorkspaceId,
            String workspaceName,
            String versionId,
            String version) {

        /**
         * 从领域上下文映射为 API 响应；上下文缺失时返回 null。
         */
        static SessionWorkspaceContextResponse from(SessionWorkspaceContext context) {
            if (context == null) {
                return null;
            }
            return new SessionWorkspaceContextResponse(
                    context.appId(),
                    context.appName(),
                    context.applicationWorkspaceId(),
                    context.workspaceName(),
                    context.versionId(),
                    context.version());
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
            Instant updatedAt,
            SessionWorkspaceContextResponse workspaceContext) {

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
                    session.updatedAt(),
                    null);
        }

        /**
         * 从历史会话列表项映射为 API 响应，附带应用/工作空间/版本上下文。
         */
        static SessionResponse from(SessionHistoryItem item) {
            Session session = item.session();
            return new SessionResponse(
                    session.sessionId().value(),
                    session.workspaceId().value(),
                    session.title(),
                    session.status().name(),
                    session.pinned(),
                    session.createdAt(),
                    session.updatedAt(),
                    SessionWorkspaceContextResponse.from(item.workspaceContext()));
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
            Instant updatedAt,
            String contentKind,
            String summaryStatus,
            Integer summaryVersion) {

        /**
         * 从旧领域消息映射为 API 响应；摘要元数据保持可空，避免旧数据被误标记为终态摘要。
         */
        static SessionMessageResponse from(SessionMessage message) {
            return from(message, null, null, null);
        }

        /**
         * 从消息和终态摘要投影元数据映射响应，供新存储模式的历史读取链路接线。
         */
        static SessionMessageResponse from(
                SessionMessage message,
                String contentKind,
                String summaryStatus,
                Integer summaryVersion) {
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
                    message.updatedAt(),
                    contentKind,
                    summaryStatus,
                    summaryVersion);
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
            BigDecimal costUsd,
            String storageMode,
            String clientRequestId,
            Instant detailsAvailableUntil) {

        /**
         * 从旧领域运行对象映射为 API 响应；新存储元数据保持可空以兼容历史 Run。
         */
        static RunResponse from(Run run) {
            return from(run, null, null, null);
        }

        /**
         * 从 Run 与无原文锚点元数据映射响应，供新模式创建、查询和恢复入口复用。
         */
        static RunResponse from(
                Run run,
                RunStorageMode storageMode,
                String clientRequestId,
                Instant detailsAvailableUntil) {
            return new RunResponse(
                    run.runId().value(),
                    run.sessionId().value(),
                    run.workspaceId().value(),
                    run.status().name(),
                    run.createdAt(),
                    run.updatedAt(),
                    TokenUsageResponse.from(run.tokenUsage()),
                    run.costUsd(),
                    storageMode == null ? null : storageMode.name(),
                    clientRequestId,
                    detailsAvailableUntil);
        }
    }

    /**
     * 用户级会话运行态摘要响应 DTO。
     */
    record SessionRuntimeStateResponse(
            int runningCount,
            int questionCount,
            List<SessionRuntimeStateItemResponse> sessions,
            Instant generatedAt) {

        static SessionRuntimeStateResponse from(SessionRuntimeStateSummary summary) {
            return new SessionRuntimeStateResponse(
                    summary.runningCount(),
                    summary.questionCount(),
                    summary.sessions().stream()
                            .map(SessionRuntimeStateItemResponse::from)
                            .toList(),
                    summary.generatedAt());
        }
    }

    /**
     * 单个历史会话的运行中状态，attention 为 null 时表示不需要额外提醒。
     */
    record SessionRuntimeStateItemResponse(
            String sessionId,
            String runId,
            String runStatus,
            String attention,
            String attentionEventId,
            Instant attentionAt,
            Instant updatedAt) {

        static SessionRuntimeStateItemResponse from(SessionRuntimeState state) {
            return new SessionRuntimeStateItemResponse(
                    state.sessionId().value(),
                    state.runId().value(),
                    state.runStatus().name(),
                    state.attention() == null ? null : state.attention().name(),
                    state.attentionEventId(),
                    state.attentionAt(),
                    state.updatedAt());
        }
    }

    /**
     * Run 当前 session scope 的消息树 snapshot，主用于刷新/断线恢复时一次性获取 root + child 内容。
     */
    record RunSessionTreeMessagesResponse(
            String runId,
            List<RunSessionTreeSessionResponse> sessions,
            Map<String, List<Map<String, Object>>> messagesBySessionId,
            Map<String, String> childSessionIdByTaskPartId,
            List<RunSessionTreeEventResponse> events,
            String historyRepresentation,
            Boolean replayAvailable,
            Instant detailsAvailableUntil) {

        static RunSessionTreeMessagesResponse from(String runId, List<RunEventSsePayload> snapshotEvents) {
            return from(runId, snapshotEvents, "FULL", true, null);
        }

        /** 显式映射历史来源元数据；所有新增字段保持可选响应兼容。 */
        static RunSessionTreeMessagesResponse from(
                String runId,
                List<RunEventSsePayload> snapshotEvents,
                String historyRepresentation,
                boolean replayAvailable,
                Instant detailsAvailableUntil) {
            SessionTreeProjection projection = sessionTreeProjection(snapshotEvents);
            return new RunSessionTreeMessagesResponse(
                    runId,
                    projection.sessions(),
                    projection.messagesBySessionId(),
                    projection.childSessionIdByTaskPartId(),
                    projection.events(),
                    historyRepresentation,
                    replayAvailable,
                    detailsAvailableUntil);
        }
    }

    /**
     * Session root 下全量历史树 snapshot，用于历史页面查看 root + 已发现 child。
     */
    record SessionTreeMessagesResponse(
            String sessionId,
            List<RunSessionTreeSessionResponse> sessions,
            Map<String, List<Map<String, Object>>> messagesBySessionId,
            Map<String, String> childSessionIdByTaskPartId,
            List<RunSessionTreeEventResponse> events,
            String historyRepresentation,
            Boolean replayAvailable,
            Instant detailsAvailableUntil) {

        /** 旧历史读取链路仍返回完整可回放内容，并保留可空的详情过期时间。 */
        static SessionTreeMessagesResponse from(String sessionId, List<RunEventSsePayload> snapshotEvents) {
            return from(sessionId, snapshotEvents, "FULL", true, null);
        }

        /**
         * 映射历史来源元数据；新链路可明确标识 Redis/OpenCode 完整详情或 PostgreSQL 摘要降级。
         */
        static SessionTreeMessagesResponse from(
                String sessionId,
                List<RunEventSsePayload> snapshotEvents,
                String historyRepresentation,
                boolean replayAvailable,
                Instant detailsAvailableUntil) {
            SessionTreeProjection projection = sessionTreeProjection(snapshotEvents);
            return new SessionTreeMessagesResponse(
                    sessionId,
                    projection.sessions(),
                    projection.messagesBySessionId(),
                    projection.childSessionIdByTaskPartId(),
                    projection.events(),
                    historyRepresentation,
                    replayAvailable,
                    detailsAvailableUntil);
        }
    }

    /**
     * Run scope 内单个 session 的轻量 DTO。
     */
    record RunSessionTreeSessionResponse(
            String rootSessionId,
            String sessionId,
            String parentSessionId,
            boolean childSession,
            String taskMessageId,
            String taskPartId,
            String taskCallId) {
    }

    /**
     * HTTP snapshot 中保留事件化 payload，便于前端复用 SSE reducer。
     */
    record RunSessionTreeEventResponse(
            String type,
            String rootSessionId,
            String sessionId,
            String parentSessionId,
            Boolean childSession,
            Map<String, Object> payload) {
    }

    private record SessionTreeProjection(
            List<RunSessionTreeSessionResponse> sessions,
            Map<String, List<Map<String, Object>>> messagesBySessionId,
            Map<String, String> childSessionIdByTaskPartId,
            List<RunSessionTreeEventResponse> events) {
    }

    private static SessionTreeProjection sessionTreeProjection(List<RunEventSsePayload> snapshotEvents) {
        Map<String, RunSessionTreeSessionResponse> sessionsById = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> messagesBySessionId = new LinkedHashMap<>();
        Map<String, String> childSessionIdByTaskPartId = new LinkedHashMap<>();
        List<RunSessionTreeEventResponse> events = new ArrayList<>();
        for (RunEventSsePayload event : snapshotEvents == null ? List.<RunEventSsePayload>of() : snapshotEvents) {
            Map<String, Object> payload = event.payload() == null ? Map.of() : event.payload();
            String sessionId = text(payload.get("sessionId"));
            String rootSessionId = text(payload.get("rootSessionId"));
            String parentSessionId = text(payload.get("parentSessionId"));
            Boolean childSession = bool(payload.get("isChildSession"));
            String taskMessageId = text(payload.get("taskMessageId"));
            String taskPartId = text(payload.get("taskPartId"));
            String taskCallId = text(payload.get("taskCallId"));
            if (sessionId != null) {
                RunSessionTreeSessionResponse sessionResponse = new RunSessionTreeSessionResponse(
                        rootSessionId,
                        sessionId,
                        parentSessionId,
                        Boolean.TRUE.equals(childSession),
                        taskMessageId,
                        taskPartId,
                        taskCallId);
                RunSessionTreeSessionResponse existing = sessionsById.get(sessionId);
                if (existing == null || (existing.taskPartId() == null && taskPartId != null)) {
                    sessionsById.put(sessionId, sessionResponse);
                }
                if (Boolean.TRUE.equals(childSession) && taskPartId != null) {
                    childSessionIdByTaskPartId.putIfAbsent(taskPartId, sessionId);
                }
                if (isMessageEvent(event.type())) {
                    messagesBySessionId.computeIfAbsent(sessionId, ignored -> new ArrayList<>()).add(Map.copyOf(payload));
                }
            }
            events.add(new RunSessionTreeEventResponse(
                    event.type(),
                    rootSessionId,
                    sessionId,
                    parentSessionId,
                    childSession,
                    Map.copyOf(payload)));
        }
        Map<String, List<Map<String, Object>>> immutableMessages = new LinkedHashMap<>();
        messagesBySessionId.forEach((sessionId, payloads) -> immutableMessages.put(sessionId, List.copyOf(payloads)));
        return new SessionTreeProjection(
                List.copyOf(sessionsById.values()),
                Map.copyOf(immutableMessages),
                Map.copyOf(childSessionIdByTaskPartId),
                List.copyOf(events));
    }

    private static boolean isMessageEvent(String type) {
        return type != null && type.startsWith("message.");
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

    private static String text(Object value) {
        return value instanceof String string && !string.isBlank() ? string : null;
    }

    private static Boolean bool(Object value) {
        return value instanceof Boolean bool ? bool : null;
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
            Instant checkedAt,
            String serviceStatus,
            String serviceAddress,
            String backendJavaServerIp) {

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
                    response.checkedAt(),
                    response.serviceStatus().name(),
                    response.serviceAddress(),
                    response.backendJavaServerIp());
        }
    }

    /**
     * 当前用户 opencode 进程初始化请求体，operationId 可空以兼容旧调用。
     */
    record UserOpencodeProcessInitializeRequest(String operationId) {
    }

    /**
     * 当前用户 opencode 进程弱健康响应 DTO；用于前端高频健康轮询，不代表数据库状态。
     */
    record UserOpencodeProcessHealthResponse(
            boolean healthy,
            String status,
            String serviceStatus,
            String linuxServerId,
            String containerId,
            int port,
            String baseUrl,
            Instant checkedAt,
            String message) {

        static UserOpencodeProcessHealthResponse from(OpencodeProcessWeakHealthResponse response) {
            return new UserOpencodeProcessHealthResponse(
                    response.healthy(),
                    response.status().name(),
                    response.serviceStatus(),
                    response.linuxServerId(),
                    response.containerId(),
                    response.port(),
                    response.baseUrl(),
                    response.checkedAt(),
                    response.message());
        }
    }

    /**
     * opencode 进程初始化进度响应 DTO，供前端轮询展示步骤状态。
     */
    record OpencodeProcessStartOperationResponse(
            String operationId,
            String status,
            String currentStep,
            List<OpencodeProcessStartOperationStepResponse> steps,
            String errorCode,
            String errorMessage,
            String processId,
            String serviceAddress,
            String traceId,
            Instant createdAt,
            Instant updatedAt) {

        static OpencodeProcessStartOperationResponse from(OpencodeProcessStartOperation operation) {
            return new OpencodeProcessStartOperationResponse(
                    operation.operationId(),
                    operation.status().name(),
                    operation.currentStep().name(),
                    List.of(OpencodeProcessStartOperationStep.values()).stream()
                            .map(step -> OpencodeProcessStartOperationStepResponse.from(operation, step))
                            .toList(),
                    operation.errorCode(),
                    operation.errorMessage(),
                    operation.processId(),
                    operation.serviceAddress(),
                    operation.traceId(),
                    operation.createdAt(),
                    operation.updatedAt());
        }
    }

    /**
     * 单个初始化步骤的展示状态，后端按枚举顺序从 operation 快照推导。
     */
    record OpencodeProcessStartOperationStepResponse(
            String step,
            String code,
            String name,
            String status) {

        static OpencodeProcessStartOperationStepResponse from(
                OpencodeProcessStartOperation operation,
                OpencodeProcessStartOperationStep step) {
            return new OpencodeProcessStartOperationStepResponse(
                    step.name(),
                    step.name(),
                    step.displayName(),
                    stepStatus(operation, step));
        }

        private static String stepStatus(OpencodeProcessStartOperation operation, OpencodeProcessStartOperationStep step) {
            if (operation.status() == OpencodeProcessStartOperationStatus.SUCCEEDED) {
                return "SUCCEEDED";
            }
            int currentOrdinal = operation.currentStep().ordinal();
            int stepOrdinal = step.ordinal();
            if (operation.status() == OpencodeProcessStartOperationStatus.FAILED) {
                if (stepOrdinal < currentOrdinal) {
                    return "SUCCEEDED";
                }
                return stepOrdinal == currentOrdinal ? "FAILED" : "PENDING";
            }
            if (stepOrdinal < currentOrdinal) {
                return "SUCCEEDED";
            }
            return stepOrdinal == currentOrdinal ? "RUNNING" : "PENDING";
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
        static RunDiffFileResponse from(com.enterprise.testagent.opencode.runtime.run.RunDiffFileResponse file) {
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
        static RunDiffResponse from(com.enterprise.testagent.opencode.runtime.run.RunDiffResponse diff) {
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
        static RunDiffActionResponse from(com.enterprise.testagent.opencode.runtime.run.RunDiffActionResponse action) {
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
     * 映射用户历史会话分页响应。
     */
    static PageResponse<SessionResponse> sessionHistoryPage(PageResponse<SessionHistoryItem> page) {
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
