package com.example.testagent.opencode.client;

import com.example.testagent.domain.event.RunEventDraft;
import com.example.testagent.domain.event.RunEventType;
import com.example.testagent.domain.run.RunId;
import com.example.testagent.domain.support.DomainValidation;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * opencode raw JSON 事件到平台 RunEventDraft 的转换器，未知事件保留上下文但不中断运行。
 */
@Component
public class OpencodeRunEventMapper {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final Supplier<Instant> now;

    /**
     * 使用系统时间创建事件映射器，生产环境由 Spring 注入共享 ObjectMapper。
     */
    @Autowired
    public OpencodeRunEventMapper(ObjectMapper objectMapper) {
        this(objectMapper, Instant::now);
    }

    /**
     * 创建可注入时钟的映射器，便于测试固定 RunEventDraft 时间戳。
     */
    public OpencodeRunEventMapper(ObjectMapper objectMapper, Supplier<Instant> now) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.now = Objects.requireNonNull(now, "now must not be null");
    }

    /**
     * 将单条 opencode raw event 转换为平台事件草稿，未知事件保留 raw payload 供前端降级展示。
     */
    public RunEventDraft toDraft(JsonNode rawEvent, RunId runId, String traceId) {
        Objects.requireNonNull(rawEvent, "rawEvent must not be null");
        Objects.requireNonNull(runId, "runId must not be null");
        traceId = DomainValidation.requireText(traceId, "traceId");

        String rawType = rawType(rawEvent);
        Map<String, Object> payload = new LinkedHashMap<>(toMap(properties(rawEvent)));
        RunEventType type = mapType(rawType, payload);
        payload.put("rawType", rawType);
        payload.put("rawEventId", rawEventId(rawEvent));
        payload.put("rawPayload", toMap(rawEvent));

        if (type == RunEventType.ASSISTANT_MESSAGE_DELTA) {
            copyTextAlias(payload);
        }
        if (type == RunEventType.MESSAGE_PART_DELTA) {
            copyTextAlias(payload);
        }

        return new RunEventDraft(runId, type, traceId, now.get(), payload);
    }

    /**
     * 把 opencode 事件类型映射为平台稳定事件类型，并在必要时补充平台状态字段。
     */
    private RunEventType mapType(String rawType, Map<String, Object> payload) {
        return switch (rawType) {
            case "session.next.prompted" -> RunEventType.RUN_STARTED;
            case "session.next.step.ended", "session.idle" -> RunEventType.RUN_SUCCEEDED;
            case "session.status" -> mapSessionStatus(payload);
            case "session.next.step.failed", "session.error" -> RunEventType.RUN_FAILED;
            case "session.next.text.delta" -> RunEventType.ASSISTANT_MESSAGE_DELTA;
            case "message.updated" -> RunEventType.MESSAGE_UPDATED;
            case "message.removed" -> RunEventType.MESSAGE_REMOVED;
            case "message.part.updated" -> RunEventType.MESSAGE_PART_UPDATED;
            case "message.part.removed" -> RunEventType.MESSAGE_PART_REMOVED;
            case "message.part.delta" -> RunEventType.MESSAGE_PART_DELTA;
            case "session.next.tool.called", "session.next.tool.input.started" -> RunEventType.TOOL_STARTED;
            case "session.next.tool.success" -> {
                payload.put("status", "success");
                yield RunEventType.TOOL_FINISHED;
            }
            case "session.next.tool.failed" -> {
                payload.put("status", "failed");
                yield RunEventType.TOOL_FINISHED;
            }
            case "session.diff" -> RunEventType.DIFF_PROPOSED;
            case "todo.updated" -> RunEventType.TODO_UPDATED;
            case "permission.asked", "permission.v2.asked" -> RunEventType.PERMISSION_ASKED;
            case "permission.replied", "permission.v2.replied" -> RunEventType.PERMISSION_REPLIED;
            case "question.asked", "question.v2.asked" -> RunEventType.QUESTION_ASKED;
            case "question.replied", "question.v2.replied" -> RunEventType.QUESTION_REPLIED;
            case "question.rejected", "question.v2.rejected" -> RunEventType.QUESTION_REJECTED;
            case "vcs.branch.updated" -> RunEventType.VCS_BRANCH_UPDATED;
            case "lsp.updated" -> RunEventType.LSP_UPDATED;
            case "mcp.tools.changed" -> RunEventType.MCP_TOOLS_CHANGED;
            case "test.finished" -> RunEventType.TEST_FINISHED;
            default -> RunEventType.OPENCODE_EVENT_UNKNOWN;
        };
    }

    /**
     * 识别 opencode 1.17.8 的 session.status 终态；非 idle 状态继续作为未知事件透传。
     */
    private RunEventType mapSessionStatus(Map<String, Object> payload) {
        // opencode 1.17.8 不再发送 session.next.step.ended，idle 状态是本次 prompt 已收敛的终态信号。
        Object status = payload.get("status");
        if (status instanceof Map<?, ?> statusMap && "idle".equals(statusMap.get("type"))) {
            return RunEventType.RUN_SUCCEEDED;
        }
        return RunEventType.OPENCODE_EVENT_UNKNOWN;
    }

    /**
     * 兼容 opencode 顶层 type 与 payload.type 两种事件包裹格式。
     */
    private String rawType(JsonNode rawEvent) {
        JsonNode topLevelType = rawEvent.path("type");
        if (topLevelType.isTextual()) {
            return topLevelType.asText();
        }
        JsonNode payloadType = rawEvent.path("payload").path("type");
        if (payloadType.isTextual()) {
            return payloadType.asText();
        }
        return "unknown";
    }

    /**
     * 兼容 opencode 顶层 id 与 payload.id 两种事件 ID 格式。
     */
    private String rawEventId(JsonNode rawEvent) {
        JsonNode topLevelId = rawEvent.path("id");
        if (topLevelId.isTextual()) {
            return topLevelId.asText();
        }
        JsonNode payloadId = rawEvent.path("payload").path("id");
        if (payloadId.isTextual()) {
            return payloadId.asText();
        }
        return "unknown";
    }

    /**
     * 提取事件 properties；没有结构化属性时返回空对象，避免空指针打断事件流。
     */
    private JsonNode properties(JsonNode rawEvent) {
        JsonNode topLevelProperties = rawEvent.path("properties");
        if (topLevelProperties.isObject()) {
            return topLevelProperties;
        }
        JsonNode payloadProperties = rawEvent.path("payload").path("properties");
        if (payloadProperties.isObject()) {
            return payloadProperties;
        }
        return objectMapper.createObjectNode();
    }

    /**
     * 将 JsonNode 转为普通 Map，并移除 null 值以保持 SSE payload 简洁。
     */
    private Map<String, Object> toMap(JsonNode node) {
        Map<String, Object> converted = objectMapper.convertValue(node, MAP_TYPE);
        return sanitize(converted);
    }

    /**
     * 移除 raw payload 中的 null 值，避免前端把缺失字段误判为显式空值。
     */
    private Map<String, Object> sanitize(Map<String, Object> source) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (value != null) {
                sanitized.put(key, value);
            }
        });
        return sanitized;
    }

    /**
     * 为 delta 类消息补充 text alias，兼容旧前端只读取 text 字段的展示逻辑。
     */
    private void copyTextAlias(Map<String, Object> payload) {
        if (!payload.containsKey("text")) {
            Object delta = payload.get("delta");
            if (delta instanceof String text) {
                payload.put("text", text);
            }
        }
    }
}
