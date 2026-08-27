package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ConnectException;
import org.junit.jupiter.api.Test;

class LocalClientMainTest {

    @Test
    void shouldExposeExplicitInteractiveEnrollmentWithoutAcceptingKeyArguments() {
        assertThat(LocalClientMain.parseCommand(new String[] {"enroll"}))
                .isEqualTo(LocalClientMain.Command.ENROLL);
        assertThat(LocalClientMain.parseCommand(new String[0]))
                .isEqualTo(LocalClientMain.Command.RUN);
        assertThatThrownBy(() -> LocalClientMain.parseCommand(
                new String[] {"--client-key=tack_v1_secret"}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAcceptOnlyCompleteInternalSelfCheckCommand() {
        assertThat(LocalClientMain.parseCommand(new String[] {
                "self-check", "/opt/test-agent/releases/20260820153045", "20260820153045"
        })).isEqualTo(LocalClientMain.Command.SELF_CHECK);

        assertThatThrownBy(() -> LocalClientMain.parseCommand(new String[] {"self-check"}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldProvideSafeActionableEnrollmentFailureDetails() {
        var authentication = LocalClientFailureReporter.details(
                LocalClientMain.Command.ENROLL,
                new LocalClientEnrollment.AuthenticationException());
        var connection = LocalClientFailureReporter.details(
                LocalClientMain.Command.ENROLL,
                new LocalClientRegistrationProbe.PlatformConnectionException(
                        new ConnectException("tack_v1_secret must not escape")));
        var rejected = LocalClientFailureReporter.details(
                LocalClientMain.Command.ENROLL,
                new LocalClientRegistrationProbe.RegistrationRejectedException("VALIDATION_ERROR"));

        assertThat(authentication.category()).isEqualTo("AUTHENTICATION_REJECTED");
        assertThat(authentication.userMessage()).contains("统一认证号", "Client key");
        assertThat(connection.category()).isEqualTo("PLATFORM_CONNECTION_FAILED");
        assertThat(connection.userMessage()).contains("WebSocket Upgrade").doesNotContain("tack_v1_secret");
        assertThat(rejected.failureCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(rejected.userMessage()).contains("VALIDATION_ERROR");
    }
}
