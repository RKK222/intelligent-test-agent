package com.enterprise.testagent.opencode.runtime.internalmodel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 内部模型 OpenAI-compatible SSE 观测器：单次解析事件、记录到达时间，并只允许真实模型输出刷新超时。
 * 注释、空事件、role/usage 元数据、畸形 data 和非对象 data 均不属于模型输出。
 */
public final class InternalModelSseStreamObserver {

    private static final String DONE = "[DONE]";

    private final ObjectMapper objectMapper;

    public InternalModelSseStreamObserver(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * 首输出使用固定绝对截止时间；首输出后只在下一条有效输出到达时刷新输出空闲截止时间。
     * 无效事件仍原样下传，但不能把上游输出停滞隐藏为健康连接。
     */
    public Flux<ObservedEvent> observe(
            Flux<ServerSentEvent<String>> source,
            Duration firstOutputTimeout,
            Duration outputIdleTimeout) {
        Objects.requireNonNull(source, "source must not be null");
        Duration firstTimeout = requirePositive(firstOutputTimeout, "firstOutputTimeout");
        Duration idleTimeout = requirePositive(outputIdleTimeout, "outputIdleTimeout");
        return Flux.defer(() -> {
            AtomicLong outputDeadline = new AtomicLong(deadlineAfter(System.nanoTime(), firstTimeout));
            Flux<ObservedEvent> observed = source.map(this::inspect);
            return observed.timeout(
                    timeoutUntil(outputDeadline.get()),
                    event -> {
                        if (event.output()) {
                            outputDeadline.set(deadlineAfter(event.receivedNanos(), idleTimeout));
                        }
                        return timeoutUntil(outputDeadline.get());
                    });
        });
    }

    /** 单次检查由代理观测与探活共同复用，调用方不得再次解析同一事件来判断输出。 */
    public ObservedEvent inspect(ServerSentEvent<String> event) {
        long receivedNanos = System.nanoTime();
        String data = event == null ? null : event.data();
        if (data == null || data.isBlank()) {
            return new ObservedEvent(event, false, false, receivedNanos);
        }
        if (DONE.equals(data.trim())) {
            return new ObservedEvent(event, false, true, receivedNanos);
        }
        return new ObservedEvent(event, containsModelOutput(data), false, receivedNanos);
    }

    private boolean containsModelOutput(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            if (root == null || !root.isObject()) {
                return false;
            }
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray()) {
                return false;
            }
            for (JsonNode choice : choices) {
                JsonNode delta = choice.get("delta");
                if (containsOutputText(delta, "content")
                        || containsOutputText(delta, "reasoning_content")
                        || containsOutputText(delta, "refusal")
                        || containsOutputText(choice, "text")
                        || containsToolOutput(delta)
                        || containsLegacyFunctionOutput(delta)) {
                    return true;
                }
            }
            return false;
        } catch (Exception ignored) {
            // 畸形 data 仍由转发/适配链路按原有协议处理，但不能伪装成首 token 或刷新输出空闲时间。
            return false;
        }
    }

    private static boolean containsOutputText(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        return value != null && value.isTextual() && !value.textValue().isEmpty();
    }

    private static boolean containsToolOutput(JsonNode delta) {
        JsonNode toolCalls = delta == null ? null : delta.get("tool_calls");
        if (toolCalls == null || !toolCalls.isArray()) {
            return false;
        }
        for (JsonNode toolCall : toolCalls) {
            JsonNode function = toolCall.get("function");
            if (containsOutputText(function, "name") || containsOutputText(function, "arguments")) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsLegacyFunctionOutput(JsonNode delta) {
        JsonNode functionCall = delta == null ? null : delta.get("function_call");
        return containsOutputText(functionCall, "name")
                || containsOutputText(functionCall, "arguments");
    }

    private static long deadlineAfter(long baseNanos, Duration timeout) {
        return baseNanos + timeout.toNanos();
    }

    private static Mono<Long> timeoutUntil(long deadlineNanos) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        return Mono.delay(Duration.ofNanos(Math.max(0L, remainingNanos)));
    }

    private static Duration requirePositive(Duration value, String name) {
        Duration duration = Objects.requireNonNull(value, name + " must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }

    /** 原始 SSE 事件及一次性派生的低基数观测信号；不保存或复制响应正文。 */
    public record ObservedEvent(
            ServerSentEvent<String> event,
            boolean output,
            boolean done,
            long receivedNanos) {
    }
}
