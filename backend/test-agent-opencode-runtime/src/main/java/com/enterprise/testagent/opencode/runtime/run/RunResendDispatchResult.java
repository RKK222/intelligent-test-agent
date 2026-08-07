package com.enterprise.testagent.opencode.runtime.run;

/** 单条重发跨 Java 分发结果，只暴露安全状态码。 */
public record RunResendDispatchResult(
        String resendId,
        boolean successful,
        String status,
        String errorCode) {
}
