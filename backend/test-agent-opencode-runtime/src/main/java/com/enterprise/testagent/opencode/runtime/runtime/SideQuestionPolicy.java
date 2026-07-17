package com.enterprise.testagent.opencode.runtime.runtime;

import com.enterprise.testagent.agent.runtime.AgentSessionMessage;
import com.enterprise.testagent.agent.runtime.AgentSessionMessagesResult;
import java.time.Duration;
import java.util.Map;

/**
 * 同步与流式旁路问答共享的稳定安全策略，防止阈值、agent 和 system prompt 在入口之间漂移。
 */
final class SideQuestionPolicy {

    static final int MAX_QUESTION_LENGTH = 4_000;
    static final int CONTEXT_MESSAGE_LIMIT = 40;
    static final int CONTEXT_CHARACTER_LIMIT = 48_000;
    static final int MAX_ANSWER_BYTES = 64 * 1024;
    static final Duration TASK_TIMEOUT = Duration.ofSeconds(120);
    /** SSE 丢失终态时的低频消息快照补偿间隔；正常事件流完成时不会继续查询。 */
    static final Duration MESSAGE_RECOVERY_INTERVAL = Duration.ofSeconds(3);
    /** 宠物问答需要直接产出答案，固定使用 build agent；系统提示仍约束为只读。 */
    static final String BUILD_AGENT = "build";
    static final String SYSTEM_PROMPT = "You are a side-question answerer. "
            + "You may use read-only inspection tools when needed to verify the answer, but never edit files, "
            + "run destructive commands, or change state. After the read-only tools finish, return only a concise "
            + "natural-language answer to the user's question. Do not stop after a tool call. "
            + "If the context is insufficient, say so plainly.";

    private SideQuestionPolicy() {
    }

    /** 统一校验并规范化旁路问题，不把空白或超长正文交给远端。 */
    static String requireQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        String normalized = question.trim();
        if (normalized.length() > MAX_QUESTION_LENGTH) {
            throw new IllegalArgumentException("question exceeds the maximum length");
        }
        return normalized;
    }

    /** 消息数或递归字符串规模任一超过共享预算时，只压缩临时 fork。 */
    static boolean shouldCompact(AgentSessionMessagesResult context) {
        return context != null
                && (context.messages().size() > CONTEXT_MESSAGE_LIMIT
                || estimateContextCharacters(context) > CONTEXT_CHARACTER_LIMIT);
    }

    /** 包级暴露估算值供边界测试固定“超过而非达到”的语义。 */
    static int estimateContextCharacters(AgentSessionMessagesResult context) {
        if (context == null) {
            return 0;
        }
        int total = 0;
        for (AgentSessionMessage message : context.messages()) {
            total += estimateValueCharacters(message.message());
            total += estimateValueCharacters(message.parts());
            if (total > CONTEXT_CHARACTER_LIMIT) {
                return total;
            }
        }
        return total;
    }

    private static int estimateValueCharacters(Object value) {
        if (value instanceof String text) {
            return text.length();
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .mapToInt(entry -> estimateValueCharacters(entry.getKey()) + estimateValueCharacters(entry.getValue()))
                    .sum();
        }
        if (value instanceof Iterable<?> iterable) {
            int total = 0;
            for (Object item : iterable) {
                total += estimateValueCharacters(item);
                if (total > CONTEXT_CHARACTER_LIMIT) {
                    return total;
                }
            }
            return total;
        }
        return 0;
    }
}
