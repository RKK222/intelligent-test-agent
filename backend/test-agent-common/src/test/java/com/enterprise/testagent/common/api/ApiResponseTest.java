package com.enterprise.testagent.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void successFactoryBuildsUnifiedSuccessResponse() {
        ApiResponse<String> response = ApiResponse.ok("created", "trace_1234567890abcdef");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("created");
        assertThat(response.traceId()).isEqualTo("trace_1234567890abcdef");
    }

    @Test
    void successResponseRejectsFalseSuccessFlag() {
        assertThatThrownBy(() -> new ApiResponse<>(false, "data", "trace_1234567890abcdef"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("success");
    }
}
