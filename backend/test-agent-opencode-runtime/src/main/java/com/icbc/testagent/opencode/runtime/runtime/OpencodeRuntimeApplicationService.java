package com.icbc.testagent.opencode.runtime.runtime;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeRepository;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionRepository;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.WorkspaceRepository;
import com.icbc.testagent.opencode.client.OpencodeClientFacade;
import com.icbc.testagent.opencode.client.OpencodeRuntimeCommand;
import com.icbc.testagent.opencode.client.OpencodeRuntimeResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Phase 11 opencode Web App 运行态 API 编排层，统一把平台请求映射到 opencode-client facade。
 */
@Service
public class OpencodeRuntimeApplicationService {

    private final WorkspaceRepository workspaceRepository;
    private final SessionRepository sessionRepository;
    private final ExecutionNodeRepository executionNodeRepository;
    private final OpencodeClientFacade opencodeClientFacade;
    private final ObjectMapper objectMapper;

    /**
     * 创建 opencode runtime 编排服务，Controller 只通过本服务访问 opencode runtime facade。
     */
    public OpencodeRuntimeApplicationService(
            WorkspaceRepository workspaceRepository,
            SessionRepository sessionRepository,
            ExecutionNodeRepository executionNodeRepository,
            OpencodeClientFacade opencodeClientFacade,
            ObjectMapper objectMapper) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.executionNodeRepository = Objects.requireNonNull(executionNodeRepository, "executionNodeRepository must not be null");
        this.opencodeClientFacade = Objects.requireNonNull(opencodeClientFacade, "opencodeClientFacade must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * 列出当前 workspace 可用 agent。
     */
    public Object listAgents(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId), "/api/agent", Map.of(), traceId);
    }

    /**
     * 列出当前 workspace 可用模型。
     */
    public Object listModels(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId), "/api/model", Map.of(), traceId);
    }

    /**
     * 列出当前 workspace 可用 provider。
     */
    public Object listProviders(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId), "/api/provider", Map.of(), traceId);
    }

    /**
     * 列出 opencode command catalog。
     */
    public Object listCommands(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId), "/api/command", Map.of(), traceId);
    }

    /**
     * 列出 opencode reference catalog。
     */
    public Object listReferences(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId), "/api/reference", Map.of(), traceId);
    }

    /**
     * 读取 opencode 文件列表，path 缺省时使用当前目录。
     */
    public Object fsList(String workspaceId, String path, String traceId) {
        return get(workspaceLocation(workspaceId), "/file", query("path", path == null || path.isBlank() ? "." : path), traceId);
    }

    /**
     * 调用 opencode 文件搜索 API。
     */
    public Object fsFind(String workspaceId, String query, String traceId) {
        return get(workspaceLocation(workspaceId), "/find/file", query("query", query), traceId);
    }

    /**
     * 读取 opencode workspace 文件内容。
     */
    public Object fsRead(String workspaceId, String path, String traceId) {
        return get(workspaceLocation(workspaceId), "/file/content", query("path", path), traceId);
    }

    /**
     * 读取远端 VCS 状态。
     */
    public Object vcsStatus(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId), "/vcs/status", Map.of(), traceId);
    }

    /**
     * 读取远端 VCS Diff，mode 缺省为 working，context 仅在调用方传入时透传。
     */
    public Object vcsDiff(String workspaceId, String mode, Integer context, String traceId) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("mode", mode == null || mode.isBlank() ? "working" : mode);
        if (context != null) {
            query.put("context", Integer.toString(context));
        }
        return get(workspaceLocation(workspaceId), "/vcs/diff", query, traceId);
    }

    /**
     * 查询远端 LSP 状态。
     */
    public Object lspStatus(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId), "/lsp", Map.of(), traceId);
    }

    /**
     * 查询远端 MCP 状态。
     */
    public Object mcpStatus(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId), "/mcp", Map.of(), traceId);
    }

    /**
     * 查询远端 MCP resources。
     */
    public Object mcpResources(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId), "/experimental/resource", Map.of(), traceId);
    }

    /**
     * 查询远端 MCP tools；指定 provider/model 时读取工具详情，否则读取工具 ID 列表。
     */
    public Object mcpTools(String workspaceId, String provider, String model, String traceId) {
        Map<String, String> query = new LinkedHashMap<>();
        if (provider != null && !provider.isBlank() && model != null && !model.isBlank()) {
            query.put("provider", provider);
            query.put("model", model);
            return get(workspaceLocation(workspaceId), "/experimental/tool", query, traceId);
        }
        return get(workspaceLocation(workspaceId), "/experimental/tool/ids", Map.of(), traceId);
    }

    /**
     * 读取 opencode 全局配置；仍按 workspace 路由节点，避免前端直连 opencode server。
     */
    public Object getConfig(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId), "/global/config", Map.of(), traceId);
    }

    /**
     * 更新 opencode 全局配置，body 只做空值兜底，字段兼容由 opencode runtime 负责。
     */
    public Object updateConfig(String workspaceId, Map<String, Object> body, String traceId) {
        return patch(workspaceLocation(workspaceId), "/global/config", safeBody(body), traceId);
    }

    /**
     * 触发 opencode runtime dispose，用于 Web App 设置页的服务重载能力。
     */
    public Object disposeGlobal(String traceId) {
        return post(workspaceLocation(null), "/global/dispose", Map.of(), traceId);
    }

    /**
     * 查询 provider auth 状态。
     */
    public Object listProviderAuth(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId), "/provider/auth", Map.of(), traceId);
    }

    /**
     * 发起 provider OAuth 授权。
     */
    public Object authorizeProviderOAuth(String providerId, Map<String, Object> body, String traceId) {
        return post(workspaceLocation(null), "/provider/" + encodePath(providerId) + "/oauth/authorize", safeBody(body), traceId);
    }

    /**
     * 完成 provider OAuth 回调。
     */
    public Object completeProviderOAuth(String providerId, Map<String, Object> body, String traceId) {
        return post(workspaceLocation(null), "/provider/" + encodePath(providerId) + "/oauth/callback", safeBody(body), traceId);
    }

    /**
     * 写入 provider auth secret，secret 不在应用层记录日志或持久化。
     */
    public Object setProviderAuth(String providerId, Map<String, Object> body, String traceId) {
        return put(workspaceLocation(null), "/auth/" + encodePath(providerId), safeBody(body), traceId);
    }

    /**
     * 删除 provider auth secret。
     */
    public Object removeProviderAuth(String providerId, String traceId) {
        return delete(workspaceLocation(null), "/auth/" + encodePath(providerId), Map.of(), traceId);
    }

    /**
     * 查询 opencode experimental worktree 列表。
     */
    public Object listWorktrees(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId), "/experimental/worktree", Map.of(), traceId);
    }

    /**
     * 创建 worktree；workspaceId 只用于平台路由，不透传为额外策略。
     */
    public Object createWorktree(Map<String, Object> body, String traceId) {
        return post(workspaceLocation(text(safeBody(body).get("workspaceId"))), "/experimental/worktree", safeBody(body), traceId);
    }

    /**
     * 删除 worktree。
     */
    public Object removeWorktree(Map<String, Object> body, String traceId) {
        return delete(workspaceLocation(text(safeBody(body).get("workspaceId"))), "/experimental/worktree", safeBody(body), traceId);
    }

    /**
     * 重置 worktree。
     */
    public Object resetWorktree(Map<String, Object> body, String traceId) {
        return post(workspaceLocation(text(safeBody(body).get("workspaceId"))), "/experimental/worktree/reset", safeBody(body), traceId);
    }

    /**
     * 查询远端 session children，平台 sessionId 会先映射为 opencode session id。
     */
    public Object sessionChildren(String sessionId, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return get(location, "/session/" + encodePath(location.opencodeSessionId()) + "/children", Map.of(), traceId);
    }

    /**
     * 查询远端 session todo。
     */
    public Object sessionTodo(String sessionId, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return get(location, "/session/" + encodePath(location.opencodeSessionId()) + "/todo", Map.of(), traceId);
    }

    /**
     * 查询远端 session Diff，messageId 为空时不发送 messageID query。
     */
    public Object sessionDiff(String sessionId, String messageId, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return get(location, "/session/" + encodePath(location.opencodeSessionId()) + "/diff", query("messageID", messageId), traceId);
    }

    /**
     * 请求远端中止 session。
     */
    public Object abortSession(String sessionId, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return post(location, "/session/" + encodePath(location.opencodeSessionId()) + "/abort", Map.of(), traceId);
    }

    /**
     * 请求远端 fork session，body 透传已由 API 层完成输入约束。
     */
    public Object forkSession(String sessionId, Map<String, Object> body, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return post(location, "/session/" + encodePath(location.opencodeSessionId()) + "/fork", safeBody(body), traceId);
    }

    /**
     * 请求远端 compact/summarize session。
     */
    public Object compactSession(String sessionId, Map<String, Object> body, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return post(location, "/session/" + encodePath(location.opencodeSessionId()) + "/summarize", safeBody(body), traceId);
    }

    /**
     * 请求远端 revert session。
     */
    public Object revertSession(String sessionId, Map<String, Object> body, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return post(location, "/session/" + encodePath(location.opencodeSessionId()) + "/revert", safeBody(body), traceId);
    }

    /**
     * 请求远端 unrevert session。
     */
    public Object unrevertSession(String sessionId, Map<String, Object> body, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return post(location, "/session/" + encodePath(location.opencodeSessionId()) + "/unrevert", safeBody(body), traceId);
    }

    /**
     * 执行远端 session command。
     */
    public Object commandSession(String sessionId, Map<String, Object> body, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return post(location, "/session/" + encodePath(location.opencodeSessionId()) + "/command", safeBody(body), traceId);
    }

    /**
     * 执行远端 session shell 命令；shell 安全边界由 API 层和 opencode runtime 共同约束。
     */
    public Object shellSession(String sessionId, Map<String, Object> body, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return post(location, "/session/" + encodePath(location.opencodeSessionId()) + "/shell", safeBody(body), traceId);
    }

    /**
     * 创建 opencode 会话分享链接，sessionId 经平台映射后再访问远端。
     */
    public Object shareSession(String sessionId, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return post(location, "/session/" + encodePath(location.opencodeSessionId()) + "/share", Map.of(), traceId);
    }

    /**
     * 取消 opencode 会话分享。
     */
    public Object unshareSession(String sessionId, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return delete(location, "/session/" + encodePath(location.opencodeSessionId()) + "/share", Map.of(), traceId);
    }

    /**
     * 查询远端 permission 请求列表。
     */
    public Object listPermissions(String sessionId, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return get(location, "/permission", Map.of(), traceId);
    }

    /**
     * 回复远端 permission 请求，并兼容前端 decision 字段到 opencode reply 字段。
     */
    public Object replyPermission(String sessionId, String requestId, Map<String, Object> body, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return post(
                location,
                "/permission/" + encodePath(requestId) + "/reply",
                permissionReplyBody(body),
                traceId);
    }

    /**
     * 查询远端 question 请求列表。
     */
    public Object listQuestions(String sessionId, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return get(location, "/question", Map.of(), traceId);
    }

    /**
     * 回复远端 question 请求。
     */
    public Object replyQuestion(String sessionId, String requestId, Map<String, Object> body, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return post(
                location,
                "/question/" + encodePath(requestId) + "/reply",
                questionReplyBody(body),
                traceId);
    }

    /**
     * 拒绝远端 question 请求。
     */
    public Object rejectQuestion(String sessionId, String requestId, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return post(
                location,
                "/question/" + encodePath(requestId) + "/reject",
                Map.of(),
                traceId);
    }

    /**
     * 发起 MCP auth。
     */
    public Object startMcpAuth(String name, Map<String, Object> body, String traceId) {
        return post(workspaceLocation(text(safeBody(body).get("workspaceId"))), "/mcp/" + encodePath(name) + "/auth", safeBody(body), traceId);
    }

    /**
     * 完成 MCP auth 回调。
     */
    public Object completeMcpAuth(String name, Map<String, Object> body, String traceId) {
        return post(
                workspaceLocation(text(safeBody(body).get("workspaceId"))),
                "/mcp/" + encodePath(name) + "/auth/callback",
                safeBody(body),
                traceId);
    }

    /**
     * 执行 MCP auth authenticate 步骤。
     */
    public Object authenticateMcp(String name, Map<String, Object> body, String traceId) {
        return post(
                workspaceLocation(text(safeBody(body).get("workspaceId"))),
                "/mcp/" + encodePath(name) + "/auth/authenticate",
                safeBody(body),
                traceId);
    }

    /**
     * 删除 MCP auth。
     */
    public Object removeMcpAuth(String name, String traceId) {
        return delete(workspaceLocation(null), "/mcp/" + encodePath(name) + "/auth", Map.of(), traceId);
    }

    /**
     * 发送 GET runtime 请求。
     */
    private Object get(RuntimeTarget location, String path, Map<String, String> query, String traceId) {
        return call(location, "GET", path, query, null, traceId);
    }

    /**
     * 发送 POST runtime 请求。
     */
    private Object post(RuntimeTarget location, String path, Map<String, Object> body, String traceId) {
        return call(location, "POST", path, Map.of(), body, traceId);
    }

    /**
     * 发送 PATCH runtime 请求。
     */
    private Object patch(RuntimeTarget location, String path, Map<String, Object> body, String traceId) {
        return call(location, "PATCH", path, Map.of(), body, traceId);
    }

    /**
     * 发送 PUT runtime 请求。
     */
    private Object put(RuntimeTarget location, String path, Map<String, Object> body, String traceId) {
        return call(location, "PUT", path, Map.of(), body, traceId);
    }

    /**
     * 发送 DELETE runtime 请求。
     */
    private Object delete(RuntimeTarget location, String path, Map<String, Object> body, String traceId) {
        return call(location, "DELETE", path, Map.of(), body, traceId);
    }

    /**
     * 统一调用 opencode facade runtime 方法，并把 JsonNode projection 转回普通 Java 对象。
     */
    private Object call(RuntimeTarget location, String method, String path, Map<String, String> query, Object body, String traceId) {
        OpencodeRuntimeResult result = opencodeClientFacade.runtime(new OpencodeRuntimeCommand(
                        location.node(),
                        method,
                        path,
                        location.directory(),
                        null,
                        query,
                        body,
                        traceId))
                .block();
        return objectMapper.convertValue(result.body(), Object.class);
    }

    /**
     * 构造 workspace 级 runtime target；未指定 workspace 时只选择可用节点，不传 directory。
     */
    private WorkspaceLocation workspaceLocation(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return new WorkspaceLocation(routableNode(), null);
        }
        Workspace workspace = workspaceRepository.findById(new WorkspaceId(workspaceId))
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.NOT_FOUND,
                        "Workspace 不存在",
                        Map.of("workspaceId", workspaceId)));
        return new WorkspaceLocation(routableNode(), workspace.rootPath());
    }

    /**
     * 构造 session 级 runtime target，要求平台 Session 已绑定远端 opencode session 和节点。
     */
    private SessionLocation sessionLocation(String sessionId) {
        Session session = sessionRepository.findById(new SessionId(sessionId))
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.NOT_FOUND,
                        "Session 不存在",
                        Map.of("sessionId", sessionId)));
        if (!session.hasOpencodeSessionMapping()) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "Session 尚未绑定远端 opencode 会话",
                    Map.of("sessionId", sessionId));
        }
        Workspace workspace = workspaceRepository.findById(session.workspaceId())
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.NOT_FOUND,
                        "Workspace 不存在",
                        Map.of("workspaceId", session.workspaceId().value())));
        ExecutionNode node = executionNodeRepository.findById(session.opencodeExecutionNodeId())
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "会话绑定的 opencode 执行节点不存在",
                        Map.of("nodeId", session.opencodeExecutionNodeId().value())));
        return new SessionLocation(node, workspace.rootPath(), session.opencodeSessionId());
    }

    /**
     * 选择一个当前可路由 opencode 节点，供 workspace catalog/file/vcs 类请求使用。
     */
    private ExecutionNode routableNode() {
        return executionNodeRepository.findRoutableNodes(1).stream()
                .findFirst()
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "没有可用 opencode 执行节点"));
    }

    /**
     * 构造单值 query，空白值按缺失处理。
     */
    private Map<String, String> query(String name, String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        return Map.of(name, value);
    }

    /**
     * 规范化可选请求体，null body 以空对象发送。
     */
    private Map<String, Object> safeBody(Map<String, Object> body) {
        return body == null ? Map.of() : body;
    }

    private String text(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    /**
     * 兼容前端 permission decision 字段，转换为 opencode 期望的 reply 字段。
     */
    private Map<String, Object> permissionReplyBody(Map<String, Object> body) {
        Map<String, Object> source = safeBody(body);
        Object reply = source.getOrDefault("reply", source.get("decision"));
        if (reply == null) {
            return source;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("reply", reply);
        if (source.containsKey("message")) {
            normalized.put("message", source.get("message"));
        }
        return normalized;
    }

    /**
     * 兼容前端扁平 answers 字段，转换为 opencode 期望的嵌套结构。
     * <p>
     * opencode {@code /question/{requestId}/reply} 要求 {@code answers} 为 {@code List<List<String>>}：
     * 外层数组每个问题一个内层数组，内层放选中的 label。前端 {@code RuntimeDock} 只发送扁平
     * {@code string[]}（单选 {@code [label]}、文本 {@code [text]}、多选 {@code [l1,l2]}），
     * 且每条回复只针对一个问题，因此把扁平数组整体包成单个内层数组即可。
     * 对已嵌套或空数组做幂等处理，避免重复包装。
     */
    private Map<String, Object> questionReplyBody(Map<String, Object> body) {
        Map<String, Object> source = safeBody(body);
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("answers", toQuestionAnswers(source.get("answers")));
        return normalized;
    }

    /**
     * 把前端 answers 归一化为 opencode 的 {@code List<List<String>>}。
     * <ul>
     *   <li>null 或非数组 → 空列表；</li>
     *   <li>空数组 → 空列表；</li>
     *   <li>已是嵌套（首元素为 List）→ 逐个内层数组转 String 透传；</li>
     *   <li>扁平标量数组 → 整体包成单个内层数组。</li>
     * </ul>
     */
    private List<List<String>> toQuestionAnswers(Object answers) {
        if (!(answers instanceof List<?> outer) || outer.isEmpty()) {
            return List.of();
        }
        // 已嵌套：每个元素本身就是某问题的答案数组，逐项转 String 透传。
        if (outer.get(0) instanceof List<?>) {
            return outer.stream()
                    .filter(element -> element instanceof List<?>)
                    .map(element -> toStringList((List<?>) element))
                    .toList();
        }
        // 扁平：整组 label 属于同一个问题，包成单个内层数组。
        return List.of(toStringList(outer));
    }

    /**
     * 把任意 List 的元素转为 String 列表，null 元素跳过。
     */
    private List<String> toStringList(List<?> list) {
        return list.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .toList();
    }

    /**
     * 对路径片段逐段 URL 编码，保留分段斜杠并避免空段进入远端路径。
     */
    private String encodePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String[] segments = path.split("/");
        StringBuilder builder = new StringBuilder();
        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('/');
            }
            builder.append(URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return builder.toString();
    }

    private interface RuntimeTarget {
        /**
         * 返回本次 runtime 调用选中的 opencode 执行节点。
         */
        ExecutionNode node();

        /**
         * 返回本次 runtime 调用要传给 opencode 的 directory，目录无关请求可为空。
         */
        String directory();
    }

    private record WorkspaceLocation(ExecutionNode node, String directory) implements RuntimeTarget {
    }

    private record SessionLocation(ExecutionNode node, String directory, String opencodeSessionId) implements RuntimeTarget {
    }
}
