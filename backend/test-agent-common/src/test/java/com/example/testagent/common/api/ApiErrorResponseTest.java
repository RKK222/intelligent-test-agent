package com.example.testagent.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiErrorResponseTest {

    @Test
    void factoryBuildsUnifiedErrorResponseFromErrorCode() {
        ApiErrorResponse response = ApiErrorResponse.of(
                ErrorCode.VALIDATION_ERROR,
                "trace_1234567890abcdef",
                Map.of("field", "name"));

        assertThat(response.success()).isFalse();
        assertThat(response.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.message()).isEqualTo("请求参数无效");
        assertThat(response.traceId()).isEqualTo("trace_1234567890abcdef");
        assertThat(response.details()).containsEntry("field", "name");
    }

    @Test
    void factoryBuildsUnifiedErrorResponseFromPlatformException() {
        PlatformException exception = new PlatformException(
                ErrorCode.CONFLICT,
                "Run 状态冲突",
                Map.of("status", "succeeded"));

        ApiErrorResponse response = ApiErrorResponse.from(exception, "trace_1234567890abcdef");

        assertThat(response.success()).isFalse();
        assertThat(response.code()).isEqualTo("CONFLICT");
        assertThat(response.message()).isEqualTo("Run 状态冲突");
        assertThat(response.details()).containsEntry("status", "succeeded");
    }

    @Test
    void errorResponseRejectsTrueSuccessFlag() {
        assertThatThrownBy(() -> new ApiErrorResponse(
                        true,
                        "VALIDATION_ERROR",
                        "请求参数无效",
                        "trace_1234567890abcdef",
                        Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("success");
    }
}
