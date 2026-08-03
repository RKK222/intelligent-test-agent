package com.enterprise.testagent.api.web.aop;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** 不依赖 Mockito，固定原始 SSO 请求体绝不被日志切面解码。 */
class ApiLoggingSensitiveBodyTest {

    @Test
    void reportsOnlyBinaryLengthForRawTicketOrGrantBody() {
        byte[] secretBody = "{\"ticket\":\"must-never-enter-logs\"}"
                .getBytes(StandardCharsets.UTF_8);

        String logged = new ApiLoggingAspect().extractRequestBody(new Object[]{secretBody});

        assertThat(logged).isEqualTo("[binary:" + secretBody.length + "]");
        assertThat(logged).doesNotContain("ticket", "must-never-enter-logs");
    }
}
