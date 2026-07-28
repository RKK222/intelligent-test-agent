package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import org.junit.jupiter.api.Test;

class AppSourceWebSocketOriginTest {

    @Test
    void canonicalizesSchemeHostAndDefaultPortButKeepsNonDefaultPort() {
        assertThat(AppSourceWebSocketOrigin.canonicalize("HTTPS://Console.Example:443"))
                .isEqualTo("https://console.example");
        assertThat(AppSourceWebSocketOrigin.canonicalize("http://Console.Example:4187"))
                .isEqualTo("http://console.example:4187");
    }

    @Test
    void rejectsMissingOriginAndNonOriginUriComponents() {
        assertInvalid(null);
        assertInvalid("https://user@console.example");
        assertInvalid("https://console.example/");
        assertInvalid("https://console.example/path");
        assertInvalid("https://console.example?query=1");
        assertInvalid("https://console.example#fragment");
        assertInvalid("file://console.example");
    }

    private void assertInvalid(String origin) {
        assertThatThrownBy(() -> AppSourceWebSocketOrigin.canonicalize(origin))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }
}
