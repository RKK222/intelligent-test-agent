package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LocalClientDiagnosticsTest {

    @Test
    void replacesInjectedSessionValueAndReportsOnlyRootFailureType() {
        String previous = System.getProperty("testagent.localclient.sessionId");
        try {
            System.setProperty("testagent.localclient.sessionId", "trace_client_bad\nsecret");
            String sessionId = LocalClientDiagnostics.ensureSessionId();

            assertThat(sessionId).matches("trace_client_[a-f0-9]{32}");
            assertThat(sessionId).doesNotContain("secret", "\n");
            assertThat(LocalClientDiagnostics.rootFailureType(
                    new IllegalStateException("outer", new SecurityException("tack_v1_secret"))))
                    .isEqualTo("SecurityException");
        } finally {
            if (previous == null) {
                System.clearProperty("testagent.localclient.sessionId");
            } else {
                System.setProperty("testagent.localclient.sessionId", previous);
            }
        }
    }
}
