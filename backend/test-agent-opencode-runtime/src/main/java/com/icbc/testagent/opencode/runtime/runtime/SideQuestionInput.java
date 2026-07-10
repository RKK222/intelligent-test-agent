package com.icbc.testagent.opencode.runtime.runtime;

import com.icbc.testagent.domain.support.DomainValidation;

/**
 * 宠物旁路问答输入：只允许一次问题，并可指定 fork 的消息边界和模型。
 */
public record SideQuestionInput(
        String question,
        String messageId,
        String agent,
        String model) {

    /**
     * 固化输入文本，空的可选字段按缺失处理，避免把前端空字符串透传到 opencode。
     */
    public SideQuestionInput {
        question = DomainValidation.requireText(question, "question");
        messageId = optionalText(messageId);
        agent = optionalText(agent);
        model = optionalText(model);
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
