package com.example.testagent.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PlatformExceptionTest {

    @Test
    void platformExceptionCarriesStableErrorCodeAndSafeDetails() {
        PlatformException exception = new PlatformException(
                ErrorCode.NOT_FOUND,
                "Session 不存在",
                Map.of("sessionId", "ses_123"));

        assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("Session 不存在");
        assertThat(exception.details()).containsEntry("sessionId", "ses_123");
    }

    @Test
    void platformExceptionUsesDefaultMessageWhenCustomMessageIsMissing() {
        PlatformException exception = new PlatformException(ErrorCode.OPENCODE_TIMEOUT);

        assertThat(exception.getMessage()).isEqualTo("opencode 服务超时");
        assertThat(exception.details()).isEmpty();
    }
}
