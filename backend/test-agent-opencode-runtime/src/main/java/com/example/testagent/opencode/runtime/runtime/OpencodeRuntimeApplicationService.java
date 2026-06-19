package com.example.testagent.opencode.runtime.runtime;

import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.domain.node.ExecutionNodeRepository;
import com.example.testagent.domain.session.Session;
import com.example.testagent.domain.session.SessionId;
import com.example.testagent.domain.session.SessionRepository;
import com.example.testagent.domain.workspace.Workspace;
import com.example.testagent.domain.workspace.WorkspaceId;
import com.example.testagent.domain.workspace.WorkspaceRepository;
import com.example.testagent.opencode.client.OpencodeClientFacade;
import com.example.testagent.opencode.client.OpencodeRuntimeCommand;
import com.example.testagent.opencode.client.OpencodeRuntimeResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
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

    public Object listAgents(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId), "/api/agent", Map.of(), traceId);
    }

    public Object listModels(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId), "/api/model", Map.of(), traceId);
    }

    public Object listProviders(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId), "/api/provider", Map.of(), traceId);
    }

    public Object listCommands(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId), "/api/command", Map.of(), traceId);
    }

    public Object listReferences(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId), "/api/reference", Map.of(), traceId);
    }

    public Object fsList(String workspaceId, String path, String traceId) {
        return get(workspaceLocation(workspaceId), "/file", query("path", path == null || path.isBlank() ? "." : path), traceId);
    }

    public Object fsFind(String workspaceId, String query, String traceId) {
        return get(workspaceLocation(workspaceId), "/find/file", query("query", query), traceId);
    }

    public Object fsRead(String workspaceId, String path, String traceId) {
        return get(workspaceLocation(workspaceId), "/file/content", query("path", path), traceId);
    }

    public Object vcsStatus(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId), "/vcs/status", Map.of(), traceId);
    }

    public Object vcsDiff(String workspaceId, String mode, Integer context, String traceId) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("mode", mode == null || mode.isBlank() ? "working" : mode);
        if (context != null) {
            query.put("context", Integer.toString(context));
        }
        return get(workspaceLocation(workspaceId), "/vcs/diff", query, traceId);
    }

    public Object lspStatus(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId), "/lsp", Map.of(), traceId);
    }

    public Object mcpStatus(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId), "/mcp", Map.of(), traceId);
    }

    public Object mcpResources(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId), "/experimental/resource", Map.of(), traceId);
    }

    public Object mcpTools(String workspaceId, String provider, String model, String traceId) {
        Map<String, String> query = new LinkedHashMap<>();
        if (provider != null && !provider.isBlank() && model != null && !model.isBlank()) {
            query.put("provider", provider);
            query.put("model", model);
            return get(workspaceLocation(workspaceId), "/experimental/tool", query, traceId);
        }
        return get(workspaceLocation(workspaceId), "/experimental/tool/ids", Map.of(), traceId);
    }

    public Object sessionChildren(String sessionId, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return get(location, "/session/" + encodePath(location.opencodeSessionId()) + "/children", Map.of(), traceId);
    }

    public Object sessionTodo(String sessionId, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return get(location, "/session/" + encodePath(location.opencodeSessionId()) + "/todo", Map.of(), traceId);
    }

    public Object sessionDiff(String sessionId, String messageId, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return get(location, "/session/" + encodePath(location.opencodeSessionId()) + "/diff", query("messageID", messageId), traceId);
    }

    public Object abortSession(String sessionId, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return post(location, "/session/" + encodePath(location.opencodeSessionId()) + "/abort", Map.of(), traceId);
    }

    public Object forkSession(String sessionId, Map<String, Object> body, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return post(location, "/session/" + encodePath(location.opencodeSessionId()) + "/fork", safeBody(body), traceId);
    }

    public Object compactSession(String sessionId, Map<String, Object> body, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return post(location, "/session/" + encodePath(location.opencodeSessionId()) + "/summarize", safeBody(body), traceId);
    }

    public Object revertSession(String sessionId, Map<String, Object> body, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return post(location, "/session/" + encodePath(location.opencodeSessionId()) + "/revert", safeBody(body), traceId);
    }

    public Object unrevertSession(String sessionId, Map<String, Object> body, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return post(location, "/session/" + encodePath(location.opencodeSessionId()) + "/unrevert", safeBody(body), traceId);
    }

    public Object commandSession(String sessionId, Map<String, Object> body, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return post(location, "/session/" + encodePath(location.opencodeSessionId()) + "/command", safeBody(body), traceId);
    }

    public Object shellSession(String sessionId, Map<String, Object> body, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return post(location, "/session/" + encodePath(location.opencodeSessionId()) + "/shell", safeBody(body), traceId);
    }

    public Object listPermissions(String sessionId, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return get(location, "/permission", Map.of(), traceId);
    }

    public Object replyPermission(String sessionId, String requestId, Map<String, Object> body, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return post(
                location,
                "/permission/" + encodePath(requestId) + "/reply",
                permissionReplyBody(body),
                traceId);
    }

    public Object listQuestions(String sessionId, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return get(location, "/question", Map.of(), traceId);
    }

    public Object replyQuestion(String sessionId, String requestId, Map<String, Object> body, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return post(
                location,
                "/question/" + encodePath(requestId) + "/reply",
                safeBody(body),
                traceId);
    }

    public Object rejectQuestion(String sessionId, String requestId, String traceId) {
        SessionLocation location = sessionLocation(sessionId);
        return post(
                location,
                "/question/" + encodePath(requestId) + "/reject",
                Map.of(),
                traceId);
    }

    private Object get(RuntimeTarget location, String path, Map<String, String> query, String traceId) {
        return call(location, "GET", path, query, null, traceId);
    }

    private Object post(RuntimeTarget location, String path, Map<String, Object> body, String traceId) {
        return call(location, "POST", path, Map.of(), body, traceId);
    }

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

    private ExecutionNode routableNode() {
        return executionNodeRepository.findRoutableNodes(1).stream()
                .findFirst()
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "没有可用 opencode 执行节点"));
    }

    private Map<String, String> query(String name, String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        return Map.of(name, value);
    }

    private Map<String, Object> safeBody(Map<String, Object> body) {
        return body == null ? Map.of() : body;
    }

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
        ExecutionNode node();

        String directory();
    }

    private record WorkspaceLocation(ExecutionNode node, String directory) implements RuntimeTarget {
    }

    private record SessionLocation(ExecutionNode node, String directory, String opencodeSessionId) implements RuntimeTarget {
    }
}
