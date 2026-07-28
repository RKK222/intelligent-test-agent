package com.enterprise.testagent.opencode.runtime.internalmodel;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 将 Codex 0.145.0 使用的 Responses API 子集适配到现有 Chat Completions 供应商。
 *
 * <p>该适配器只接受白盒分析所需的纯文本与 function tool，任何图片、文件、内置 Web 工具或
 * 自定义工具类型均失败关闭；上游 reasoning 字段不会回传，避免暴露模型思维链。
 */
public class InternalModelResponsesAdapter {

    private static final String DONE = "[DONE]";
    private static final String DEFAULT_TOOL_CHOICE = "auto";

    private final ObjectMapper objectMapper;

    public InternalModelResponsesAdapter(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /** 将 Responses 请求转换为供应商兼容的流式 Chat Completions 请求。 */
    public ConvertedRequest convertRequest(byte[] requestBody) {
        ObjectNode source = requireObject(parse(requestBody), "Responses 请求体必须是 JSON 对象");
        String model = requireText(source, "model", "Responses 请求缺少 model");
        if (!source.path("stream").asBoolean(false)) {
            throw validation("Responses 请求必须启用 stream");
        }
        rejectPresent(source, "previous_response_id", "暂不支持 previous_response_id");
        validateTextControls(source.get("text"));

        ObjectNode target = objectMapper.createObjectNode();
        target.put("model", model);
        target.put("stream", true);
        target.putObject("stream_options").put("include_usage", true);

        ArrayNode messages = target.putArray("messages");
        appendInstructions(messages, source.get("instructions"));
        appendInput(messages, source.get("input"));

        JsonNode tools = source.get("tools");
        if (tools != null && !tools.isNull()) {
            target.set("tools", convertTools(tools));
        }
        target.set("tool_choice", convertToolChoice(source.get("tool_choice")));
        if (source.has("parallel_tool_calls")) {
            if (!source.get("parallel_tool_calls").isBoolean()) {
                throw validation("parallel_tool_calls 必须是布尔值");
            }
            target.set("parallel_tool_calls", source.get("parallel_tool_calls"));
        }
        try {
            return new ConvertedRequest(model, objectMapper.writeValueAsBytes(target));
        } catch (JsonProcessingException exception) {
            throw validation("Responses 请求转换失败", exception);
        }
    }

    /** 每次上游 SSE 响应使用独立会话，避免跨请求复用增量状态。 */
    public StreamSession newStreamSession(String model) {
        if (model == null || model.isBlank()) {
            throw validation("Responses 流缺少 model");
        }
        return new StreamSession(model);
    }

    private void appendInstructions(ArrayNode messages, JsonNode instructions) {
        if (instructions == null || instructions.isNull()) {
            return;
        }
        if (!instructions.isTextual()) {
            throw validation("instructions 仅支持文本");
        }
        if (!instructions.asText().isBlank()) {
            messages.add(message("system", instructions.asText()));
        }
    }

    private void appendInput(ArrayNode messages, JsonNode input) {
        if (input == null || !input.isArray()) {
            throw validation("input 必须是数组");
        }
        ArrayNode pendingToolCalls = null;
        for (JsonNode itemNode : input) {
            ObjectNode item = requireObject(itemNode, "input 元素必须是对象");
            String type = requireText(item, "type", "input 元素缺少 type");
            if (!"function_call".equals(type) && pendingToolCalls != null) {
                messages.add(assistantToolMessage(pendingToolCalls));
                pendingToolCalls = null;
            }
            switch (type) {
                case "message" -> appendMessage(messages, item);
                case "function_call" -> {
                    if (pendingToolCalls == null) {
                        pendingToolCalls = objectMapper.createArrayNode();
                    }
                    pendingToolCalls.add(convertFunctionCall(item));
                }
                case "function_call_output" -> appendFunctionOutput(messages, item);
                case "reasoning" -> {
                    // Codex 会回放加密 reasoning item；Chat Completions 不需要且不得透传。
                }
                default -> throw validation("Responses input 类型不受支持: " + safeType(type));
            }
        }
        if (pendingToolCalls != null) {
            messages.add(assistantToolMessage(pendingToolCalls));
        }
    }

    private void appendMessage(ArrayNode messages, ObjectNode item) {
        String role = requireText(item, "role", "message 缺少 role");
        if (!List.of("user", "assistant", "system", "developer").contains(role)) {
            throw validation("message role 不受支持");
        }
        JsonNode content = item.get("content");
        if (content == null || !content.isArray()) {
            throw validation("message content 必须是数组");
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode partNode : content) {
            ObjectNode part = requireObject(partNode, "message content 元素必须是对象");
            String type = requireText(part, "type", "message content 缺少 type");
            if (!"input_text".equals(type) && !"output_text".equals(type)) {
                throw validation("message content 类型不受支持: " + safeType(type));
            }
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(requireTextAllowEmpty(part, "text", "message 文本缺失"));
        }
        // 大多数内部 Chat Completions 供应商尚不识别 developer，统一降级为 system。
        messages.add(message("developer".equals(role) ? "system" : role, text.toString()));
    }

    private ObjectNode convertFunctionCall(ObjectNode item) {
        String callId = requireText(item, "call_id", "function_call 缺少 call_id");
        String name = requireText(item, "name", "function_call 缺少 name");
        String arguments = requireTextAllowEmpty(item, "arguments", "function_call 缺少 arguments");
        ObjectNode toolCall = objectMapper.createObjectNode();
        toolCall.put("id", callId);
        toolCall.put("type", "function");
        ObjectNode function = toolCall.putObject("function");
        function.put("name", name);
        function.put("arguments", arguments);
        return toolCall;
    }

    private void appendFunctionOutput(ArrayNode messages, ObjectNode item) {
        String callId = requireText(item, "call_id", "function_call_output 缺少 call_id");
        JsonNode output = item.get("output");
        String text;
        if (output != null && output.isTextual()) {
            text = output.asText();
        } else if (output != null && output.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode partNode : output) {
                ObjectNode part = requireObject(partNode, "function_call_output 元素必须是对象");
                if (!"input_text".equals(requireText(part, "type", "function_call_output 缺少 type"))) {
                    throw validation("function_call_output 仅支持 input_text");
                }
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(requireTextAllowEmpty(part, "text", "function_call_output 文本缺失"));
            }
            text = builder.toString();
        } else {
            throw validation("function_call_output 仅支持文本");
        }
        ObjectNode message = message("tool", text);
        message.put("tool_call_id", callId);
        messages.add(message);
    }

    private ArrayNode convertTools(JsonNode tools) {
        if (!tools.isArray()) {
            throw validation("tools 必须是数组");
        }
        ArrayNode converted = objectMapper.createArrayNode();
        for (JsonNode toolNode : tools) {
            ObjectNode tool = requireObject(toolNode, "tool 必须是对象");
            if (!"function".equals(requireText(tool, "type", "tool 缺少 type"))) {
                throw validation("只支持 function tool");
            }
            ObjectNode function = objectMapper.createObjectNode();
            function.put("name", requireText(tool, "name", "function tool 缺少 name"));
            copyOptionalText(tool, function, "description");
            JsonNode parameters = tool.get("parameters");
            if (parameters != null && !parameters.isNull()) {
                if (!parameters.isObject()) {
                    throw validation("function tool parameters 必须是对象");
                }
                function.set("parameters", parameters);
            } else {
                function.set("parameters", objectMapper.createObjectNode());
            }
            if (tool.has("strict")) {
                if (!tool.get("strict").isBoolean()) {
                    throw validation("function tool strict 必须是布尔值");
                }
                function.set("strict", tool.get("strict"));
            }
            ObjectNode chatTool = objectMapper.createObjectNode();
            chatTool.put("type", "function");
            chatTool.set("function", function);
            converted.add(chatTool);
        }
        return converted;
    }

    private JsonNode convertToolChoice(JsonNode toolChoice) {
        if (toolChoice == null || toolChoice.isNull()) {
            return objectMapper.getNodeFactory().textNode(DEFAULT_TOOL_CHOICE);
        }
        if (toolChoice.isTextual()) {
            String choice = toolChoice.asText();
            if (!List.of("auto", "none", "required").contains(choice)) {
                throw validation("tool_choice 不受支持");
            }
            return toolChoice;
        }
        ObjectNode choice = requireObject(toolChoice, "tool_choice 必须是字符串或对象");
        if (!"function".equals(requireText(choice, "type", "tool_choice 缺少 type"))) {
            throw validation("tool_choice 仅支持 function");
        }
        ObjectNode converted = objectMapper.createObjectNode();
        converted.put("type", "function");
        ObjectNode function = converted.putObject("function");
        function.put("name", requireText(choice, "name", "tool_choice 缺少 name"));
        return converted;
    }

    private void validateTextControls(JsonNode textControls) {
        if (textControls == null || textControls.isNull()) {
            return;
        }
        ObjectNode text = requireObject(textControls, "text 必须是对象");
        JsonNode format = text.get("format");
        if (format == null || format.isNull()) {
            return;
        }
        if (!format.isObject() || !"text".equals(format.path("type").asText())) {
            throw validation("仅支持纯文本响应格式");
        }
    }

    private void rejectPresent(ObjectNode source, String field, String message) {
        JsonNode value = source.get(field);
        if (value != null && !value.isNull()) {
            throw validation(message);
        }
    }

    private void copyOptionalText(ObjectNode source, ObjectNode target, String field) {
        JsonNode value = source.get(field);
        if (value == null || value.isNull()) {
            return;
        }
        if (!value.isTextual()) {
            throw validation(field + " 必须是文本");
        }
        target.set(field, value);
    }

    private ObjectNode assistantToolMessage(ArrayNode toolCalls) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", "assistant");
        message.putNull("content");
        message.set("tool_calls", toolCalls);
        return message;
    }

    private ObjectNode message(String role, String content) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private JsonNode parse(byte[] requestBody) {
        try {
            return objectMapper.readTree(requestBody == null ? new byte[0] : requestBody);
        } catch (Exception exception) {
            throw validation("Responses 请求体不是合法 JSON", exception);
        }
    }

    private ObjectNode requireObject(JsonNode node, String message) {
        if (!(node instanceof ObjectNode objectNode)) {
            throw validation(message);
        }
        return objectNode;
    }

    private String requireText(ObjectNode node, String field, String message) {
        String value = requireTextAllowEmpty(node, field, message);
        if (value.isBlank()) {
            throw validation(message);
        }
        return value;
    }

    private String requireTextAllowEmpty(ObjectNode node, String field, String message) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw validation(message);
        }
        return value.asText();
    }

    private String safeType(String type) {
        return type.length() > 64 ? type.substring(0, 64) : type;
    }

    private PlatformException validation(String message) {
        return new PlatformException(ErrorCode.VALIDATION_ERROR, message);
    }

    private PlatformException validation(String message, Throwable cause) {
        return new PlatformException(ErrorCode.VALIDATION_ERROR, message, Map.of(), cause);
    }

    public record ConvertedRequest(String model, byte[] body) {

        public ConvertedRequest {
            Objects.requireNonNull(model, "model must not be null");
            Objects.requireNonNull(body, "body must not be null");
        }

        public String bodyUtf8() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    public record ResponseEvent(String event, String data) {

        public ResponseEvent {
            Objects.requireNonNull(event, "event must not be null");
            Objects.requireNonNull(data, "data must not be null");
        }
    }

    /** 将 Chat Completions SSE 增量组装为 Codex 可消费的 Responses 事件。 */
    public final class StreamSession {

        private final String model;
        private final String responseId = "resp_" + UUID.randomUUID().toString().replace("-", "");
        private final String messageId = "msg_" + UUID.randomUUID().toString().replace("-", "");
        private final StringBuilder outputText = new StringBuilder();
        private final Map<Integer, ToolCallState> toolCalls = new LinkedHashMap<>();
        private JsonNode usage;
        private boolean created;
        private boolean completed;

        private StreamSession(String model) {
            this.model = model;
        }

        /** 一个上游 data payload 可能转换为多个语义事件。 */
        public List<ResponseEvent> convertData(String payload) {
            if (completed) {
                return List.of();
            }
            if (payload == null || payload.isBlank()) {
                return List.of();
            }
            List<ResponseEvent> events = new ArrayList<>();
            emitCreated(events);
            if (DONE.equals(payload.trim())) {
                emitCompleted(events);
                return List.copyOf(events);
            }

            JsonNode root;
            try {
                root = objectMapper.readTree(payload);
            } catch (Exception exception) {
                emitFailed(events, "upstream_malformed_sse", "上游模型返回了无法解析的流事件");
                return List.copyOf(events);
            }
            if (root == null || !root.isObject()) {
                emitFailed(events, "upstream_malformed_sse", "上游模型返回了非法流事件");
                return List.copyOf(events);
            }
            if (root.has("usage") && root.get("usage").isObject()) {
                usage = root.get("usage");
            }
            JsonNode choices = root.get("choices");
            if (choices == null || choices.isNull()) {
                return List.copyOf(events);
            }
            if (!choices.isArray()) {
                emitFailed(events, "upstream_malformed_sse", "上游模型 choices 字段非法");
                return List.copyOf(events);
            }
            for (JsonNode choice : choices) {
                JsonNode delta = choice.path("delta");
                if (!delta.isObject()) {
                    continue;
                }
                JsonNode content = delta.get("content");
                if (content != null && content.isTextual() && !content.asText().isEmpty()) {
                    outputText.append(content.asText());
                    ObjectNode event = event("response.output_text.delta");
                    event.put("item_id", messageId);
                    event.put("output_index", 0);
                    event.put("content_index", 0);
                    event.put("delta", content.asText());
                    events.add(serialize("response.output_text.delta", event));
                }
                JsonNode deltaToolCalls = delta.get("tool_calls");
                if (deltaToolCalls != null && !deltaToolCalls.isNull()) {
                    if (!deltaToolCalls.isArray()) {
                        emitFailed(events, "upstream_malformed_sse", "上游模型 tool_calls 字段非法");
                        return List.copyOf(events);
                    }
                    appendToolCallDeltas(events, deltaToolCalls);
                }
            }
            return List.copyOf(events);
        }

        /** 上游在 [DONE] 前结束或超时时补发安全错误事件，不向 Codex 暴露供应商异常正文。 */
        public List<ResponseEvent> failIfIncomplete(String code, String message) {
            if (completed) {
                return List.of();
            }
            List<ResponseEvent> events = new ArrayList<>();
            emitCreated(events);
            emitFailed(events, code, message);
            return List.copyOf(events);
        }

        private void appendToolCallDeltas(List<ResponseEvent> events, JsonNode deltaToolCalls) {
            for (JsonNode node : deltaToolCalls) {
                if (!node.isObject() || !node.path("index").canConvertToInt()) {
                    emitFailed(events, "upstream_malformed_sse", "上游模型工具调用增量非法");
                    return;
                }
                int index = node.path("index").asInt();
                ToolCallState state = toolCalls.computeIfAbsent(index, ignored -> new ToolCallState(index));
                if (node.path("id").isTextual() && !node.path("id").asText().isBlank()) {
                    state.callId = node.path("id").asText();
                    state.itemId = "fc_" + safeIdentifier(state.callId);
                }
                JsonNode function = node.path("function");
                if (function.isObject()) {
                    if (function.path("name").isTextual()) {
                        state.name.append(function.path("name").asText());
                    }
                    if (function.path("arguments").isTextual()) {
                        String arguments = function.path("arguments").asText();
                        state.arguments.append(arguments);
                        ObjectNode event = event("response.function_call_arguments.delta");
                        event.put("item_id", state.itemId());
                        event.put("call_id", state.callId());
                        event.put("output_index", index + 1);
                        event.put("delta", arguments);
                        events.add(serialize("response.function_call_arguments.delta", event));
                    }
                }
            }
        }

        private void emitCreated(List<ResponseEvent> events) {
            if (created) {
                return;
            }
            created = true;
            ObjectNode response = baseResponse("in_progress");
            ObjectNode event = event("response.created");
            event.set("response", response);
            events.add(serialize("response.created", event));
        }

        private void emitCompleted(List<ResponseEvent> events) {
            if (completed) {
                return;
            }
            if (!outputText.isEmpty()) {
                ObjectNode item = objectMapper.createObjectNode();
                item.put("type", "message");
                item.put("id", messageId);
                item.put("role", "assistant");
                ArrayNode content = item.putArray("content");
                ObjectNode text = content.addObject();
                text.put("type", "output_text");
                text.put("text", outputText.toString());
                events.add(outputItemDone(item, 0));
            }
            for (ToolCallState state : toolCalls.values()) {
                ObjectNode item = objectMapper.createObjectNode();
                item.put("type", "function_call");
                item.put("id", state.itemId());
                item.put("call_id", state.callId());
                item.put("name", state.name());
                item.put("arguments", state.arguments.toString());
                events.add(outputItemDone(item, state.index + 1));
            }
            ObjectNode response = baseResponse("completed");
            // Codex 仅在 end_turn=false 时继续执行本轮 function tool 并把结果回送模型。
            response.put("end_turn", toolCalls.isEmpty());
            response.set("usage", convertUsage());
            ObjectNode event = event("response.completed");
            event.set("response", response);
            events.add(serialize("response.completed", event));
            completed = true;
        }

        private ResponseEvent outputItemDone(ObjectNode item, int outputIndex) {
            ObjectNode event = event("response.output_item.done");
            event.put("output_index", outputIndex);
            event.set("item", item);
            return serialize("response.output_item.done", event);
        }

        private void emitFailed(List<ResponseEvent> events, String code, String message) {
            if (completed) {
                return;
            }
            ObjectNode response = baseResponse("failed");
            ObjectNode error = response.putObject("error");
            error.put("type", "server_error");
            error.put("code", code);
            error.put("message", message);
            ObjectNode event = event("response.failed");
            event.set("response", response);
            events.add(serialize("response.failed", event));
            completed = true;
        }

        private ObjectNode baseResponse(String status) {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("id", responseId);
            response.put("object", "response");
            response.put("status", status);
            response.put("model", model);
            response.set("output", objectMapper.createArrayNode());
            return response;
        }

        private ObjectNode convertUsage() {
            long inputTokens = longValue(usage, "prompt_tokens");
            long outputTokens = longValue(usage, "completion_tokens");
            long totalTokens = usage != null && usage.path("total_tokens").canConvertToLong()
                    ? usage.path("total_tokens").asLong()
                    : inputTokens + outputTokens;
            ObjectNode converted = objectMapper.createObjectNode();
            converted.put("input_tokens", inputTokens);
            converted.putObject("input_tokens_details")
                    .put("cached_tokens", nestedLongValue(usage, "prompt_tokens_details", "cached_tokens"))
                    .put("cache_write_tokens", 0);
            converted.put("output_tokens", outputTokens);
            converted.putObject("output_tokens_details")
                    .put("reasoning_tokens", nestedLongValue(usage, "completion_tokens_details", "reasoning_tokens"));
            converted.put("total_tokens", totalTokens);
            return converted;
        }

        private long longValue(JsonNode node, String field) {
            return node != null && node.path(field).canConvertToLong() ? node.path(field).asLong() : 0;
        }

        private long nestedLongValue(JsonNode node, String parent, String field) {
            return node != null && node.path(parent).path(field).canConvertToLong()
                    ? node.path(parent).path(field).asLong()
                    : 0;
        }

        private ObjectNode event(String type) {
            ObjectNode event = objectMapper.createObjectNode();
            event.put("type", type);
            return event;
        }

        private ResponseEvent serialize(String eventName, ObjectNode event) {
            try {
                return new ResponseEvent(eventName, objectMapper.writeValueAsString(event));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Responses 流事件序列化失败", exception);
            }
        }

        private String safeIdentifier(String value) {
            String normalized = value.replaceAll("[^A-Za-z0-9_-]", "_");
            return normalized.isBlank() ? UUID.randomUUID().toString().replace("-", "") : normalized;
        }

        private final class ToolCallState {

            private final int index;
            private final StringBuilder name = new StringBuilder();
            private final StringBuilder arguments = new StringBuilder();
            private String callId;
            private String itemId;

            private ToolCallState(int index) {
                this.index = index;
            }

            private String callId() {
                if (callId == null || callId.isBlank()) {
                    callId = "call_" + UUID.randomUUID().toString().replace("-", "");
                }
                return callId;
            }

            private String itemId() {
                if (itemId == null || itemId.isBlank()) {
                    itemId = "fc_" + safeIdentifier(callId());
                }
                return itemId;
            }

            private String name() {
                if (name.isEmpty()) {
                    emitNameFallback();
                }
                return name.toString();
            }

            private void emitNameFallback() {
                name.append("unknown_function");
            }
        }
    }
}
