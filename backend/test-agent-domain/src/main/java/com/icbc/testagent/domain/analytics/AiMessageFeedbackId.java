package com.icbc.testagent.domain.analytics;

import com.icbc.testagent.domain.support.DomainValidation;

/**
 * AI 消息反馈业务 ID，使用 fb_ 前缀。
 */
public record AiMessageFeedbackId(String value) {

    public AiMessageFeedbackId {
        value = DomainValidation.requirePrefixedId(value, "fb_", "feedbackId");
    }
}
