package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
