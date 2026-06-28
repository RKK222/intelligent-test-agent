package com.icbc.testagent.domain.analytics;

/**
 * 不满意反馈原因编码，保持稳定枚举便于运营统计。
 */
public enum AiMessageFeedbackReasonCode {
    WRONG_ANSWER,
    NOT_HELPFUL,
    DID_NOT_FOLLOW_INSTRUCTION,
    CODE_QUALITY_LOW,
    TEST_RESULT_BAD,
    TOO_SLOW,
    TOO_VERBOSE,
    TOO_SHORT,
    OTHER
}
