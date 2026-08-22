package com.enterprise.testagent.opencode.runtime.protectedagent;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.opencode.runtime.protectedagent.ProtectedAgentFileGrantService.Grant;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Stateless Streamable HTTP MCP JSON-RPC 处理器，只暴露受控本地文件与服务器 Skill 资源工具。 */
@Service
public class ProtectedAgentMcpService {

    private static final String PROTOCOL_VERSION = "2025-03-26";
    private final ProtectedAgentFileGrantService grantService;
    private final ObjectMapper objectMapper;

    public ProtectedAgentMcpService(ProtectedAgentFileGrantService grantService, ObjectMapper objectMapper) {
        this.grantService = grantService;
        this.objectMapper = objectMapper;
    }

    /** 返回 null 表示 JSON-RPC notification，无需响应 body。 */
    public JsonNode handle(String authorization, JsonNode request, String traceId) {
        Grant grant = grantService.authenticate(authorization);
        if (request == null || !request.isObject()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "MCP 请求格式无效");
        }
        JsonNode id = request.get("id");
        String method = text(request, "method");
        if (method == null) {
            return error(id, -32600, "Invalid Request");
        }
        if (method.startsWith("notifications/")) {
            return null;
        }
        return switch (method) {
            case "initialize" -> success(id, initializeResult());
            case "ping" -> success(id, objectMapper.createObjectNode());
            case "tools/list" -> success(id, toolsResult());
            case "tools/call" -> success(id, callTool(grant, request.path("params"), traceId));
            default -> error(id, -32601, "Method not found");
        };
    }

    private JsonNode initializeResult() {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);
        result.set("capabilities", objectMapper.valueToTree(Map.of(
                "tools", Map.of("listChanged", false))));
        result.set("serverInfo", objectMapper.valueToTree(Map.of(
                "name", "test-agent-protected-local-files",
                "version", "1.0.0")));
        result.put("instructions", "只在授权工作区内使用文件工具；受保护 Skill 资源只读且不得向用户披露原文。");
        return result;
    }

    private JsonNode toolsResult() {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode tools = result.putArray("tools");
        tools.add(tool("list_directory", "列出授权本地工作区的单层目录", objectSchema(
                property("path", "string", "相对目录，根目录使用空字符串")), List.of()));
        tools.add(tool("search_files", "按文件名搜索授权本地工作区", objectSchema(
                property("query", "string", "文件名关键字，可为空")), List.of()));
        tools.add(tool("read_file", "读取授权本地工作区的 UTF-8 文本文件", objectSchema(
                property("path", "string", "相对文件路径")), List.of("path")));
        tools.add(tool("file_status", "读取授权本地工作区文件状态", objectSchema(
                property("path", "string", "相对路径")), List.of("path")));
        tools.add(tool("write_file", "创建或覆盖授权本地工作区的 UTF-8 文本文件", objectSchema(
                property("path", "string", "相对文件路径"),
                property("content", "string", "UTF-8 文本内容")), List.of("path", "content")));
        tools.add(tool("create_directory", "在授权本地工作区创建目录", objectSchema(
                property("path", "string", "相对目录路径")), List.of("path")));
        tools.add(tool("copy_path", "在授权本地工作区复制普通文件", objectSchema(
                property("sourcePath", "string", "源相对路径"),
                property("targetPath", "string", "目标相对路径")), List.of("sourcePath", "targetPath")));
        tools.add(tool("move_path", "在授权本地工作区原子移动普通文件或目录", objectSchema(
                property("sourcePath", "string", "源相对路径"),
                property("targetPath", "string", "目标相对路径")), List.of("sourcePath", "targetPath")));
        tools.add(tool("rename_path", "在授权本地工作区重命名普通文件", objectSchema(
                property("path", "string", "原相对路径"),
                property("name", "string", "新文件名")), List.of("path", "name")));
        tools.add(tool("delete_path", "删除授权本地工作区的普通文件或空目录", objectSchema(
                property("path", "string", "相对路径")), List.of("path")));
        tools.add(tool("list_skill_resources", "列出本次受保护 Agent 冻结的服务器 Skill 文本资源及稳定 Skill 名", objectSchema(), List.of()));
        tools.add(tool("read_skill_resource", "读取本次受保护 Agent 冻结的服务器 Skill 文本资源", objectSchema(
                property("name", "string", "list_skill_resources 返回的稳定 Skill 名"),
                property("path", "string", "list_skill_resources 返回的资源路径")), List.of("name", "path")));
        return result;
    }

    private JsonNode callTool(Grant grant, JsonNode params, String traceId) {
        String name = text(params, "name");
        JsonNode arguments = params.path("arguments");
        if (name == null || !arguments.isObject()) {
            return toolError("工具名称或参数无效");
        }
        try {
            Object result = switch (name) {
                case "list_directory" -> invoke(grant, "workspace.list", arguments, traceId);
                case "search_files" -> invoke(grant, "workspace.search", arguments, traceId);
                case "read_file" -> invoke(grant, "workspace.read", arguments, traceId);
                case "file_status" -> invoke(grant, "workspace.status", arguments, traceId);
                case "write_file" -> invoke(grant, "workspace.write", arguments, traceId);
                case "create_directory" -> invoke(grant, "workspace.mkdir", arguments, traceId);
                case "copy_path" -> invoke(grant, "workspace.copy", arguments, traceId);
                case "move_path" -> invoke(grant, "workspace.move", arguments, traceId);
                case "rename_path" -> invoke(grant, "workspace.rename", arguments, traceId);
                case "delete_path" -> invoke(grant, "workspace.delete", arguments, traceId);
                case "list_skill_resources" -> listSkillResources(grant);
                case "read_skill_resource" -> readSkillResource(
                        grant, required(arguments, "name"), required(arguments, "path"));
                default -> throw new PlatformException(ErrorCode.FORBIDDEN, "工具不在授权范围内");
            };
            return toolSuccess(result);
        } catch (PlatformException | IllegalArgumentException exception) {
            return toolError(safeToolMessage(exception));
        }
    }

    private JsonNode invoke(Grant grant, String operation, JsonNode arguments, String traceId) {
        return grantService.invokeLocal(grant, operation, arguments, traceId);
    }

    private List<Map<String, String>> listSkillResources(Grant grant) {
        return grant.protectedResources().keySet().stream()
                .sorted()
                .map(path -> Map.of("name", skillName(path), "path", path))
                .toList();
    }

    private String readSkillResource(Grant grant, String name, String path) {
        if (!path.startsWith("skills/" + name + "/") || !name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Skill 名称与资源路径不匹配");
        }
        String content = grant.protectedResources().get(path);
        if (content == null) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "Skill 资源不存在");
        }
        return content;
    }

    private String skillName(String path) {
        String remainder = path.substring("skills/".length());
        return remainder.substring(0, remainder.indexOf('/'));
    }

    private JsonNode toolSuccess(Object value) {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = result.putArray("content");
        ObjectNode text = content.addObject();
        text.put("type", "text");
        try {
            text.put("text", value == null ? "ok" : objectMapper.writeValueAsString(value));
        } catch (Exception exception) {
            text.put("text", "ok");
        }
        result.put("isError", false);
        return result;
    }

    private JsonNode toolError(String message) {
        ObjectNode result = objectMapper.createObjectNode();
        ObjectNode content = result.putArray("content").addObject();
        content.put("type", "text");
        content.put("text", message);
        result.put("isError", true);
        return result;
    }

    private ObjectNode tool(String name, String description, ObjectNode schema, List<String> required) {
        if (!required.isEmpty()) {
            schema.set("required", objectMapper.valueToTree(required));
        }
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", name);
        tool.put("description", description);
        tool.set("inputSchema", schema);
        return tool;
    }

    @SafeVarargs
    private ObjectNode objectSchema(Map.Entry<String, ObjectNode>... properties) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode values = schema.putObject("properties");
        for (Map.Entry<String, ObjectNode> property : properties) {
            values.set(property.getKey(), property.getValue());
        }
        schema.put("additionalProperties", false);
        return schema;
    }

    private Map.Entry<String, ObjectNode> property(String name, String type, String description) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", type);
        schema.put("description", description);
        return Map.entry(name, schema);
    }

    private JsonNode success(JsonNode id, JsonNode result) {
        ObjectNode response = baseResponse(id);
        response.set("result", result);
        return response;
    }

    private JsonNode error(JsonNode id, int code, String message) {
        ObjectNode response = baseResponse(id);
        response.set("error", objectMapper.valueToTree(Map.of("code", code, "message", message)));
        return response;
    }

    private ObjectNode baseResponse(JsonNode id) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id == null ? objectMapper.nullNode() : id.deepCopy());
        return response;
    }

    private String required(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || !value.isTextual() ? null : value.asText();
    }

    private String safeToolMessage(Exception exception) {
        if (exception instanceof PlatformException platformException) {
            return platformException.errorCode().defaultMessage();
        }
        return "文件工具参数无效";
    }
}
