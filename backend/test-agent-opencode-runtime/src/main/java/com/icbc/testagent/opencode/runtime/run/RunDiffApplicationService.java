package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.agent.runtime.AgentDiffCommand;
import com.icbc.testagent.agent.runtime.AgentDiffFile;
import com.icbc.testagent.agent.runtime.AgentDiffResult;
import com.icbc.testagent.agent.runtime.AgentRejectDiffCommand;
import com.icbc.testagent.agent.runtime.AgentRejectDiffResult;
import com.icbc.testagent.agent.runtime.AgentRuntime;
import com.icbc.testagent.agent.runtime.AgentRuntimeRegistry;
import com.icbc.testagent.domain.agent.AgentSessionBinding;
import com.icbc.testagent.domain.agent.AgentSessionBindingRepository;
import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventRepository;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeRepository;
import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunDetailsLocator;
import com.icbc.testagent.domain.run.RunDiffAction;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunRepository;
import com.icbc.testagent.domain.run.RunRuntimeManifest;
import com.icbc.testagent.domain.run.RunRuntimeReplay;
import com.icbc.testagent.domain.run.RunRuntimeStore;
import com.icbc.testagent.domain.run.RunStorageMode;
import com.icbc.testagent.domain.run.RunSummaryPersistencePort;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceRepository;
import com.icbc.testagent.event.RunEventAppender;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Run 级 Diff 应用服务，负责读取当前 Diff、接受事件追加和拒绝时调用 agent revert。
 */
@Service
public class RunDiffApplicationService {

    private static final int EVENT_LOOKBACK_LIMIT = 500;

    private final WorkspaceRepository workspaceRepository;
    private final com.icbc.testagent.domain.session.SessionRepository sessionRepository;
    private final RunRepository runRepository;
    private final RunEventRepository runEventRepository;
    private final ExecutionNodeRepository executionNodeRepository;
    private final RunEventAppender runEventAppender;
    private final AgentRuntimeRegistry agentRuntimeRegistry;
    private final AgentSessionBindingRepository agentSessionBindingRepository;
    private final ManagedWorkspacePathResolver pathResolver;
    private final RunRuntimeStore runRuntimeStore;
    private final RunSummaryPersistencePort runSummaryPersistencePort;

    /**
     * 创建 Run Diff 应用服务，依赖领域仓储和 agent runtime registry，不直接访问持久化实现。
     */
    @Autowired
    public RunDiffApplicationService(
            WorkspaceRepository workspaceRepository,
            com.icbc.testagent.domain.session.SessionRepository sessionRepository,
            RunRepository runRepository,
            RunEventRepository runEventRepository,
            ExecutionNodeRepository executionNodeRepository,
            RunEventAppender runEventAppender,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository,
            ManagedWorkspacePathResolver pathResolver,
            RunRuntimeStore runRuntimeStore,
            RunSummaryPersistencePort runSummaryPersistencePort) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.runEventRepository = Objects.requireNonNull(runEventRepository, "runEventRepository must not be null");
        this.executionNodeRepository = Objects.requireNonNull(executionNodeRepository, "executionNodeRepository must not be null");
        this.runEventAppender = Objects.requireNonNull(runEventAppender, "runEventAppender must not be null");
        this.agentRuntimeRegistry = Objects.requireNonNull(agentRuntimeRegistry, "agentRuntimeRegistry must not be null");
        this.agentSessionBindingRepository = Objects.requireNonNull(agentSessionBindingRepository, "agentSessionBindingRepository must not be null");
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver must not be null");
        this.runRuntimeStore = runRuntimeStore;
        this.runSummaryPersistencePort = runSummaryPersistencePort;
    }
    public RunDiffApplicationService(
            WorkspaceRepository workspaceRepository,
            com.icbc.testagent.domain.session.SessionRepository sessionRepository,
            RunRepository runRepository,
            RunEventRepository runEventRepository,
            ExecutionNodeRepository executionNodeRepository,
            RunEventAppender runEventAppender,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository) {
        this(
                workspaceRepository,
                sessionRepository,
                runRepository,
                runEventRepository,
                executionNodeRepository,
                runEventAppender,
                agentRuntimeRegistry,
                agentSessionBindingRepository,
                ManagedWorkspacePathResolver.legacyOnly(),
                null,
                null);
    }

    /**
     * 查询 Run 当前 Diff；优先使用最近的 durable Diff 事件，没有事件时回落到 agent sessionDiff。
     */
    public RunDiffResponse getDiff(RunId runId, String traceId) {
        return getDiff(agentRuntimeRegistry.defaultAgentId(), runId, traceId);
    }

    /**
     * 查询指定 agent 的 Run 当前 Diff，响应格式保持平台统一模型。
     */
    public RunDiffResponse getDiff(String agentId, RunId runId, String traceId) {
        String resolvedAgentId = agentRuntimeRegistry.normalize(agentId);
        AgentRuntime runtime = agentRuntimeRegistry.require(resolvedAgentId);
        Run run = findRun(runId);
        DiffDetails details = diffDetails(run.runId());
        return getDiff(resolvedAgentId, runtime, run, details, traceId);
    }

    private RunDiffResponse getDiff(
            String resolvedAgentId,
            AgentRuntime runtime,
            Run run,
            DiffDetails details,
            String traceId) {
        List<RunEventDraft> events = details.events();
        List<RunDiffFileResponse> eventFiles = latestDiffFiles(events);
        if (!eventFiles.isEmpty()) {
            return new RunDiffResponse(run.runId().value(), eventFiles);
        }
        Optional<RunDetailsLocator> locator = detailsLocator(details, run.runId());
        Optional<AgentDiffTarget> target = agentTarget(resolvedAgentId, run, details, locator, traceId);
        if (target.isEmpty()) {
            return new RunDiffResponse(run.runId().value(), List.of());
        }
        Workspace workspace = findWorkspace(run);
        String messageId = latestTextPayloadValue(events, "messageID")
                .or(() -> locator.map(RunDetailsLocator::lastRemoteMessageId))
                .orElse(null);
        AgentDiffResult result = runtime.getDiff(new AgentDiffCommand(
                        target.orElseThrow().node(),
                        target.orElseThrow().remoteSessionId(),
                        workspaceRoot(workspace),
                        null,
                        messageId,
                        traceId))
                .block();
        List<RunDiffFileResponse> files = result == null ? List.of() : result.files().stream()
                .map(this::fromAgentDiffFile)
                .toList();
        return new RunDiffResponse(run.runId().value(), files);
    }

    /**
     * 接受当前 Run Diff，只追加平台接受事件，不直接调用远端写操作。
     */
    public RunDiffActionResponse acceptDiff(RunId runId, String traceId) {
        return acceptDiff(agentRuntimeRegistry.defaultAgentId(), runId, traceId);
    }

    /**
     * 接受指定 agent 的当前 Run Diff，只追加平台接受事件。
     */
    public RunDiffActionResponse acceptDiff(String agentId, RunId runId, String traceId) {
        String resolvedAgentId = agentRuntimeRegistry.normalize(agentId);
        AgentRuntime runtime = agentRuntimeRegistry.require(resolvedAgentId);
        Run run = findRun(runId);
        DiffDetails details = diffDetails(runId);
        RunDiffResponse diff = getDiff(resolvedAgentId, runtime, run, details, traceId);
        appendActionEvent(
                runId,
                RunEventType.DIFF_ACCEPTED,
                "accept",
                "accepted",
                diff.files().size(),
                traceId,
                details.storageMode());
        return new RunDiffActionResponse(runId.value(), "accept", "accepted", diff.files().size());
    }

    /**
     * 拒绝当前 Run Diff，必须找到 messageID 后调用 agent revert，再追加平台拒绝事件。
     */
    public RunDiffActionResponse rejectDiff(RunId runId, String traceId) {
        return rejectDiff(agentRuntimeRegistry.defaultAgentId(), runId, traceId);
    }

    /**
     * 拒绝指定 agent 的当前 Run Diff。
     */
    public RunDiffActionResponse rejectDiff(String agentId, RunId runId, String traceId) {
        String resolvedAgentId = agentRuntimeRegistry.normalize(agentId);
        AgentRuntime runtime = agentRuntimeRegistry.require(resolvedAgentId);
        Run run = findRun(runId);
        DiffDetails details = diffDetails(run.runId());
        List<RunEventDraft> events = details.events();
        Optional<RunDetailsLocator> locator = detailsLocator(details, runId);
        String messageId = latestTextPayloadValue(events, "messageID")
                .or(() -> locator.map(RunDetailsLocator::lastRemoteMessageId))
                .orElseThrow(() -> new PlatformException(
                        details.storageMode() == RunStorageMode.REDIS_SUMMARY
                                ? ErrorCode.RUN_DETAILS_EXPIRED
                                : ErrorCode.CONFLICT,
                        details.storageMode() == RunStorageMode.REDIS_SUMMARY
                                ? "Run Diff 定位详情已过期"
                                : "缺少 agent messageID，无法拒绝 Diff",
                        Map.of("runId", runId.value())));
        String partId = latestTextPayloadValue(events, "partID")
                .or(() -> locator.map(RunDetailsLocator::lastRemotePartId))
                .orElse(null);
        AgentDiffTarget target = agentTarget(resolvedAgentId, run, details, locator, traceId)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.CONFLICT,
                        "Session 未绑定 agent 会话，无法拒绝 Diff",
                        Map.of("runId", runId.value(), "sessionId", run.sessionId().value(), "agentId", resolvedAgentId)));
        Workspace workspace = findWorkspace(run);
        AgentRejectDiffResult result = runtime.rejectDiff(new AgentRejectDiffCommand(
                        target.node(),
                        target.remoteSessionId(),
                        workspaceRoot(workspace),
                        null,
                        messageId,
                        partId,
                        traceId))
                .block();
        if (result == null || !result.rejected()) {
            throw new PlatformException(
                    ErrorCode.OPENCODE_BAD_GATEWAY,
                    "agent 拒绝 Diff 未返回成功结果",
                    Map.of("runId", runId.value(), "agentId", resolvedAgentId));
        }
        int fileCount = latestDiffFiles(events).size();
        appendActionEvent(
                runId,
                RunEventType.DIFF_REJECTED,
                "reject",
                "rejected",
                fileCount,
                traceId,
                details.storageMode());
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

    private String workspaceRoot(Workspace workspace) {
        return pathResolver.resolve(workspace.rootPath()).toString();
    }

    /**
     * 查询 Session 绑定的 agent 节点，缺失时按远端不可用处理。
     */
    private ExecutionNode findNode(AgentSessionBinding binding) {
        return executionNodeRepository.findById(binding.executionNodeId())
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "Session 绑定的 agent 执行节点不存在",
                        Map.of(
                                "sessionId", binding.sessionId().value(),
                                "agentId", binding.agentId(),
                                "nodeId", binding.executionNodeId().value())));
    }

    private ExecutionNode findNode(String executionNodeId) {
        return executionNodeRepository.findById(new com.icbc.testagent.domain.node.ExecutionNodeId(executionNodeId))
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "Run 绑定的 agent 执行节点不存在",
                        Map.of("nodeId", executionNodeId)));
    }

    /**
     * 读取最近一段 RunEvent，用于寻找 Diff 和 messageID，避免全量扫描历史事件。
     */
    private List<RunEventDraft> runEvents(RunId runId) {
        return runEventRepository.findByRunIdAfter(runId, 0, EVENT_LOOKBACK_LIMIT).stream()
                .map(event -> new RunEventDraft(
                        event.runId(),
                        event.type(),
                        event.traceId(),
                        event.occurredAt(),
                        event.payload(),
                        event.scopeContext()))
                .toList();
    }

    /**
     * 从事件列表中倒序寻找最近一次 Diff proposal，并解析为响应文件列表。
     */
    private List<RunDiffFileResponse> latestDiffFiles(List<RunEventDraft> events) {
        List<RunEventDraft> reversed = new ArrayList<>(events);
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
     * 将 agent runtime Diff DTO 转换为 runtime 响应对象。
     */
    private RunDiffFileResponse fromAgentDiffFile(AgentDiffFile file) {
        return new RunDiffFileResponse(
                file.path(),
                file.patch(),
                file.additions(),
                file.deletions(),
                file.status());
    }

    /**
     * 查询通用 agent 绑定；opencode 旧字段只作为兼容回填来源。
     */
    private Optional<AgentSessionBinding> findAgentBinding(String agentId, Session session, String traceId) {
        Optional<AgentSessionBinding> binding =
                agentSessionBindingRepository.findBySessionIdAndAgentId(session.sessionId(), agentId);
        if (binding.isPresent()) {
            return binding;
        }
        if (AgentRuntimeRegistry.DEFAULT_AGENT_ID.equals(agentRuntimeRegistry.normalize(agentId))
                && session.hasOpencodeSessionMapping()) {
            AgentSessionBinding legacy = new AgentSessionBinding(
                    session.sessionId(),
                    agentId,
                    session.opencodeSessionId(),
                    session.opencodeExecutionNodeId(),
                    session.createdAt(),
                    session.updatedAt(),
                    traceId);
            return Optional.of(agentSessionBindingRepository.save(legacy));
        }
        return Optional.empty();
    }

    /**
     * 倒序查找最近的文本 payload 字段，用于定位 opencode messageID/partID。
     */
    private Optional<String> latestTextPayloadValue(List<RunEventDraft> events, String key) {
        List<RunEventDraft> reversed = new ArrayList<>(events);
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
            String traceId,
            RunStorageMode storageMode) {
        runEventAppender.append(new RunEventDraft(
                runId,
                type,
                traceId,
                Instant.now(),
                Map.of(
                        "action", action,
                        "status", status,
                        "fileCount", fileCount)), storageMode);
        if (storageMode == RunStorageMode.REDIS_SUMMARY) {
            RunDiffAction diffAction = type == RunEventType.DIFF_ACCEPTED
                    ? RunDiffAction.ACCEPTED
                    : RunDiffAction.REJECTED;
            if (runSummaryPersistencePort == null
                    || !runSummaryPersistencePort.recordDiffAction(runId, diffAction)) {
                throw new PlatformException(
                        ErrorCode.RUNTIME_STATE_UNAVAILABLE,
                        "Diff 动作已进入运行态但控制面计数更新失败",
                        Map.of("runId", runId.value(), "action", diffAction.name()));
            }
        }
    }

    /**
     * 新模式只读取 Redis 物化快照；manifest 缺失但关系库仍有摘要锚点时明确判定为详情过期。
     */
    private DiffDetails diffDetails(RunId runId) {
        if (runRuntimeStore != null) {
            try {
                Optional<RunRuntimeManifest> manifest = runRuntimeStore.findManifest(runId);
                if (manifest.filter(item -> item.storageMode() == RunStorageMode.REDIS_SUMMARY).isPresent()) {
                    RunRuntimeReplay replay = runRuntimeStore.replayAfter(runId, 0L, 1);
                    return new DiffDetails(
                            RunStorageMode.REDIS_SUMMARY,
                            replay.snapshot().events(),
                            manifest);
                }
            } catch (PlatformException exception) {
                if (exception.errorCode() != ErrorCode.RUNTIME_STATE_UNAVAILABLE
                        || runSummaryPersistencePort == null
                        || runSummaryPersistencePort.findDetailsLocator(runId).isPresent()) {
                    throw exception;
                }
                // Redis 故障不能改变已确认 legacy Run 的数据库事实源；新模式锚点存在时仍 fail-closed。
                return legacyDetails(runId);
            }
        }
        Optional<RunDetailsLocator> locator = runSummaryPersistencePort == null
                ? Optional.empty()
                : runSummaryPersistencePort.findDetailsLocator(runId);
        if (locator.filter(item -> item.storageMode() == RunStorageMode.REDIS_SUMMARY).isPresent()) {
            throw new PlatformException(
                    ErrorCode.RUN_DETAILS_EXPIRED,
                    "Run 详情已过期",
                    Map.of("runId", runId.value()));
        }
        return legacyDetails(runId);
    }

    private DiffDetails legacyDetails(RunId runId) {
        return new DiffDetails(RunStorageMode.LEGACY_FULL, runEvents(runId), Optional.empty());
    }

    /** Redis snapshot 已足够时不读取 PostgreSQL；仅 fallback 定位远端 message/part 时查一次锚点。 */
    private Optional<RunDetailsLocator> detailsLocator(DiffDetails details, RunId runId) {
        if (details.storageMode() != RunStorageMode.REDIS_SUMMARY || runSummaryPersistencePort == null) {
            return Optional.empty();
        }
        return runSummaryPersistencePort.findDetailsLocator(runId);
    }

    private Optional<AgentDiffTarget> agentTarget(
            String resolvedAgentId,
            Run run,
            DiffDetails details,
            Optional<RunDetailsLocator> locator,
            String traceId) {
        if (details.storageMode() == RunStorageMode.REDIS_SUMMARY) {
            RunRuntimeManifest manifest = details.manifest().orElseThrow();
            String remoteSessionId = textValue(manifest.rootRemoteSessionId())
                    .or(() -> locator.map(RunDetailsLocator::rootRemoteSessionId))
                    .orElseThrow(() -> new PlatformException(
                            ErrorCode.RUN_DETAILS_EXPIRED,
                            "Run 远端 Session 定位详情已过期",
                            Map.of("runId", run.runId().value())));
            String executionNodeId = textValue(manifest.executionNodeId())
                    .or(() -> locator.map(RunDetailsLocator::executionNodeId))
                    .orElseThrow(() -> new PlatformException(
                            ErrorCode.RUN_DETAILS_EXPIRED,
                            "Run 执行节点定位详情已过期",
                            Map.of("runId", run.runId().value())));
            return Optional.of(new AgentDiffTarget(findNode(executionNodeId), remoteSessionId));
        }
        Session session = findSession(run);
        return findAgentBinding(resolvedAgentId, session, traceId)
                .map(binding -> new AgentDiffTarget(findNode(binding), binding.remoteSessionId()));
    }

    private record DiffDetails(
            RunStorageMode storageMode,
            List<RunEventDraft> events,
            Optional<RunRuntimeManifest> manifest) {
    }

    private record AgentDiffTarget(ExecutionNode node, String remoteSessionId) {
    }
}
