package com.example.testagent.opencode.runtime.run;

import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
import com.example.testagent.domain.event.RunEvent;
import com.example.testagent.domain.event.RunEventDraft;
import com.example.testagent.domain.event.RunEventRepository;
import com.example.testagent.domain.event.RunEventType;
import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.domain.node.ExecutionNodeRepository;
import com.example.testagent.domain.run.Run;
import com.example.testagent.domain.run.RunId;
import com.example.testagent.domain.run.RunRepository;
import com.example.testagent.domain.session.Session;
import com.example.testagent.domain.workspace.Workspace;
import com.example.testagent.domain.workspace.WorkspaceRepository;
import com.example.testagent.event.RunEventAppender;
import com.example.testagent.opencode.client.OpencodeClientFacade;
import com.example.testagent.opencode.client.OpencodeDiffCommand;
import com.example.testagent.opencode.client.OpencodeDiffFile;
import com.example.testagent.opencode.client.OpencodeDiffResult;
import com.example.testagent.opencode.client.OpencodeRejectDiffCommand;
import com.example.testagent.opencode.client.OpencodeRejectDiffResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Run 级 Diff 应用服务，负责读取当前 Diff、接受事件追加和拒绝时调用 opencode revert。
 */
@Service
public class RunDiffApplicationService {

    private static final int EVENT_LOOKBACK_LIMIT = 500;

    private final WorkspaceRepository workspaceRepository;
    private final com.example.testagent.domain.session.SessionRepository sessionRepository;
    private final RunRepository runRepository;
    private final RunEventRepository runEventRepository;
    private final ExecutionNodeRepository executionNodeRepository;
    private final RunEventAppender runEventAppender;
    private final OpencodeClientFacade opencodeClientFacade;

    /**
     * 创建 Run Diff 应用服务，依赖领域仓储和 opencode facade，不直接访问持久化实现。
     */
    public RunDiffApplicationService(
            WorkspaceRepository workspaceRepository,
            com.example.testagent.domain.session.SessionRepository sessionRepository,
            RunRepository runRepository,
            RunEventRepository runEventRepository,
            ExecutionNodeRepository executionNodeRepository,
            RunEventAppender runEventAppender,
            OpencodeClientFacade opencodeClientFacade) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.runEventRepository = Objects.requireNonNull(runEventRepository, "runEventRepository must not be null");
        this.executionNodeRepository = Objects.requireNonNull(executionNodeRepository, "executionNodeRepository must not be null");
        this.runEventAppender = Objects.requireNonNull(runEventAppender, "runEventAppender must not be null");
        this.opencodeClientFacade = Objects.requireNonNull(opencodeClientFacade, "opencodeClientFacade must not be null");
    }

    /**
     * 查询 Run 当前 Diff；优先使用最近的 durable Diff 事件，没有事件时回落到 opencode sessionDiff。
     */
    public RunDiffResponse getDiff(RunId runId, String traceId) {
        Run run = findRun(runId);
        List<RunEvent> events = runEvents(run.runId());
        List<RunDiffFileResponse> eventFiles = latestDiffFiles(events);
        if (!eventFiles.isEmpty()) {
            return new RunDiffResponse(run.runId().value(), eventFiles);
        }
        Session session = findSession(run);
        if (!session.hasOpencodeSessionMapping()) {
            return new RunDiffResponse(run.runId().value(), List.of());
        }
        Workspace workspace = findWorkspace(run);
        ExecutionNode node = findNode(session);
        String messageId = latestTextPayloadValue(events, "messageID").orElse(null);
        OpencodeDiffResult result = opencodeClientFacade.getDiff(new OpencodeDiffCommand(
                        node,
                        session.opencodeSessionId(),
                        workspace.rootPath(),
                        null,
                        messageId,
                        traceId))
                .block();
        List<RunDiffFileResponse> files = result == null ? List.of() : result.files().stream()
                .map(this::fromOpencodeDiffFile)
                .toList();
        return new RunDiffResponse(run.runId().value(), files);
    }

    /**
     * 接受当前 Run Diff，只追加平台接受事件，不直接调用远端写操作。
     */
    public RunDiffActionResponse acceptDiff(RunId runId, String traceId) {
        RunDiffResponse diff = getDiff(runId, traceId);
        appendActionEvent(runId, RunEventType.DIFF_ACCEPTED, "accept", "accepted", diff.files().size(), traceId);
        return new RunDiffActionResponse(runId.value(), "accept", "accepted", diff.files().size());
    }

    /**
     * 拒绝当前 Run Diff，必须找到 messageID 后调用 opencode revert，再追加平台拒绝事件。
     */
    public RunDiffActionResponse rejectDiff(RunId runId, String traceId) {
        Run run = findRun(runId);
        List<RunEvent> events = runEvents(run.runId());
        String messageId = latestTextPayloadValue(events, "messageID")
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.CONFLICT,
                        "缺少 opencode messageID，无法拒绝 Diff",
                        Map.of("runId", runId.value())));
        String partId = latestTextPayloadValue(events, "partID").orElse(null);
        Session session = findSession(run);
        if (!session.hasOpencodeSessionMapping()) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "Session 未绑定 opencode 会话，无法拒绝 Diff",
                    Map.of("runId", runId.value(), "sessionId", session.sessionId().value()));
        }
        Workspace workspace = findWorkspace(run);
        ExecutionNode node = findNode(session);
        OpencodeRejectDiffResult result = opencodeClientFacade.rejectDiff(new OpencodeRejectDiffCommand(
                        node,
                        session.opencodeSessionId(),
                        workspace.rootPath(),
                        null,
                        messageId,
                        partId,
                        traceId))
                .block();
        if (result == null || !result.rejected()) {
            throw new PlatformException(
                    ErrorCode.OPENCODE_BAD_GATEWAY,
                    "opencode 拒绝 Diff 未返回成功结果",
                    Map.of("runId", runId.value()));
        }
        int fileCount = latestDiffFiles(events).size();
        appendActionEvent(runId, RunEventType.DIFF_REJECTED, "reject", "rejected", fileCount, traceId);
        return new RunDiffActionResponse(runId.value(), "reject", "rejected", fileCount);
    }

    /**
     * 查询 Run，不存在时返回统一 NOT_FOUND 错误。
     */
    private Run findRun(RunId runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Run 不存在", Map.of("runId", runId.value())));
    }

    /**
     * 根据 Run 查询 Session，不存在时返回统一 NOT_FOUND 错误。
     */
    private Session findSession(Run run) {
        return sessionRepository.findById(run.sessionId())
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.NOT_FOUND,
                        "Session 不存在",
                        Map.of("sessionId", run.sessionId().value())));
    }

    /**
     * 根据 Run 查询 Workspace，不存在时返回统一 NOT_FOUND 错误。
     */
    private Workspace findWorkspace(Run run) {
        return workspaceRepository.findById(run.workspaceId())
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.NOT_FOUND,
                        "Workspace 不存在",
                        Map.of("workspaceId", run.workspaceId().value())));
    }

    /**
     * 查询 Session 绑定的 opencode 节点，缺失时按远端不可用处理。
     */
    private ExecutionNode findNode(Session session) {
        return executionNodeRepository.findById(session.opencodeExecutionNodeId())
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "Session 绑定的 opencode 执行节点不存在",
                        Map.of("sessionId", session.sessionId().value())));
    }

    /**
     * 读取最近一段 RunEvent，用于寻找 Diff 和 messageID，避免全量扫描历史事件。
     */
    private List<RunEvent> runEvents(RunId runId) {
        return runEventRepository.findByRunIdAfter(runId, 0, EVENT_LOOKBACK_LIMIT);
    }

    /**
     * 从事件列表中倒序寻找最近一次 Diff proposal，并解析为响应文件列表。
     */
    private List<RunDiffFileResponse> latestDiffFiles(List<RunEvent> events) {
        List<RunEvent> reversed = new ArrayList<>(events);
        Collections.reverse(reversed);
        return reversed.stream()
                .filter(event -> event.type() == RunEventType.DIFF_PROPOSED)
                .map(event -> filesFromPayload(event.payload()))
                .filter(files -> !files.isEmpty())
                .findFirst()
                .orElse(List.of());
    }

    /**
     * 兼容 diff/files 两种 payload 字段，解析失败时返回空列表。
     */
    private List<RunDiffFileResponse> filesFromPayload(Map<String, Object> payload) {
        Object diff = payload.get("diff");
        if (diff instanceof List<?> items) {
            return items.stream().map(this::fileFromAny).flatMap(Optional::stream).toList();
        }
        Object files = payload.get("files");
        if (files instanceof List<?> items) {
            return items.stream().map(this::fileFromAny).flatMap(Optional::stream).toList();
        }
        return List.of();
    }

    /**
     * 将字符串路径或 Map 形式的 Diff 文件 payload 转换为稳定响应对象。
     */
    @SuppressWarnings("unchecked")
    private Optional<RunDiffFileResponse> fileFromAny(Object value) {
        if (value instanceof String path && !path.isBlank()) {
            return Optional.of(new RunDiffFileResponse(path, "", 0, 0, "modified"));
        }
        if (!(value instanceof Map<?, ?> raw)) {
            return Optional.empty();
        }
        Map<String, Object> map = (Map<String, Object>) raw;
        Object pathValue = map.containsKey("path") ? map.get("path") : map.get("file");
        if (!(pathValue instanceof String path) || path.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new RunDiffFileResponse(
                path,
                textValue(map.get("patch")).orElse(""),
                numberValue(map.get("additions")),
                numberValue(map.get("deletions")),
                textValue(map.get("status")).orElse("modified")));
    }

    /**
     * 将 opencode-client Diff DTO 转换为 runtime 响应对象。
     */
    private RunDiffFileResponse fromOpencodeDiffFile(OpencodeDiffFile file) {
        return new RunDiffFileResponse(
                file.path(),
                file.patch(),
                file.additions(),
                file.deletions(),
                file.status());
    }

    /**
     * 倒序查找最近的文本 payload 字段，用于定位 opencode messageID/partID。
     */
    private Optional<String> latestTextPayloadValue(List<RunEvent> events, String key) {
        List<RunEvent> reversed = new ArrayList<>(events);
        Collections.reverse(reversed);
        return reversed.stream()
                .map(event -> textValue(event.payload().get(key)))
                .flatMap(Optional::stream)
                .findFirst();
    }

    /**
     * 读取非空字符串字段，其他类型按缺失处理。
     */
    private Optional<String> textValue(Object value) {
        return value instanceof String text && !text.isBlank() ? Optional.of(text) : Optional.empty();
    }

    /**
     * 读取数字字段，字符串无法解析或缺失时按 0 处理。
     */
    private long numberValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * 追加 Diff 操作审计事件，记录动作、状态和文件数量。
     */
    private void appendActionEvent(
            RunId runId,
            RunEventType type,
            String action,
            String status,
            int fileCount,
            String traceId) {
        runEventAppender.append(new RunEventDraft(
                runId,
                type,
                traceId,
                Instant.now(),
                Map.of(
                        "action", action,
                        "status", status,
                        "fileCount", fileCount)));
    }
}
