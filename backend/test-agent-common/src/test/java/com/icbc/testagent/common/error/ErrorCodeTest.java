package com.icbc.testagent.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErrorCodeTest {

    @Test
    void errorCodesExposeStableHttpStatusAndChineseDefaultMessages() {
        assertThat(ErrorCode.VALIDATION_ERROR.httpStatus()).isEqualTo(400);
        assertThat(ErrorCode.UNAUTHENTICATED.httpStatus()).isEqualTo(401);
        assertThat(ErrorCode.FORBIDDEN.httpStatus()).isEqualTo(403);
        assertThat(ErrorCode.NOT_FOUND.httpStatus()).isEqualTo(404);
        assertThat(ErrorCode.CONFLICT.httpStatus()).isEqualTo(409);
        assertThat(ErrorCode.RATE_LIMITED.httpStatus()).isEqualTo(429);
        assertThat(ErrorCode.INTERNAL_ERROR.httpStatus()).isEqualTo(500);
        assertThat(ErrorCode.OPENCODE_BAD_GATEWAY.httpStatus()).isEqualTo(502);
        assertThat(ErrorCode.OPENCODE_UNAVAILABLE.httpStatus()).isEqualTo(503);
        assertThat(ErrorCode.OPENCODE_TIMEOUT.httpStatus()).isEqualTo(504);

        assertThat(ErrorCode.VALIDATION_ERROR.defaultMessage()).isEqualTo("请求参数无效");
        assertThat(ErrorCode.OPENCODE_TIMEOUT.defaultMessage()).isEqualTo("opencode 服务超时");
    }
}
