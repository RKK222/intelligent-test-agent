package com.enterprise.testagent.integration.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.auth.TokenSessionMarkerStore;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workflowcapability.WorkflowCapabilityStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WorkflowCapabilityHmacAuthenticatorTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final Instant NOW = Instant.ofEpochSecond(1_800_000_000L);

    @Test
    void authenticatesExactCanonicalRequestAndCurrentSessionMarker() throws Exception {
        WorkflowCapabilityStore store = mock(WorkflowCapabilityStore.class);
        TokenSessionMarkerStore markers = mock(TokenSessionMarkerStore.class);
        WorkflowCapabilityProperties properties = properties();
        WorkflowCapabilityHmacAuthenticator authenticator = new WorkflowCapabilityHmacAuthenticator(
                store,
                markers,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
        String path = "/api/internal/workflow-capabilities/v1/repositories";
        byte[] body = new byte[0];
        String bodyDigest = sha256(body);
        String sessionDigest = "a".repeat(64);
        String canonical = String.join("\n",
                "GET",
                path,
                bodyDigest,
                "usr_12345678",
                sessionDigest,
                "1800000000",
                "nonce_1234567890",
                "workflow");
        when(store.reserveNonce(sha256("nonce_1234567890".getBytes(StandardCharsets.UTF_8)), Duration.ofSeconds(60)))
                .thenReturn(true);
        when(markers.isActiveForUser(sessionDigest, new UserId("usr_12345678"))).thenReturn(true);

        WorkflowCapabilityCaller caller = authenticator.authenticateWorkflow(
                "GET",
                path,
                body,
                "workflow",
                "usr_12345678",
                sessionDigest,
                "1800000000",
                "nonce_1234567890",
                bodyDigest,
                hmac(canonical));

        assertThat(caller.userId().value()).isEqualTo("usr_12345678");
        assertThat(caller.sessionDigest()).isEqualTo(sessionDigest);
        verify(markers).isActiveForUser(sessionDigest, new UserId("usr_12345678"));
    }

    @Test
    void rejectsReplayBeforeCallingBusinessService() throws Exception {
        WorkflowCapabilityStore store = mock(WorkflowCapabilityStore.class);
        TokenSessionMarkerStore markers = mock(TokenSessionMarkerStore.class);
        WorkflowCapabilityHmacAuthenticator authenticator = new WorkflowCapabilityHmacAuthenticator(
                store,
                markers,
                properties(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String digest = sha256(body);
        String session = "b".repeat(64);
        String canonical = String.join("\n",
                "POST", "/path", digest, "usr_12345678", session,
                "1800000000", "nonce_1234567890", "workflow");
        when(store.reserveNonce(sha256("nonce_1234567890".getBytes(StandardCharsets.UTF_8)), Duration.ofSeconds(60)))
                .thenReturn(false);

        assertThatThrownBy(() -> authenticator.authenticateWorkflow(
                "POST", "/path", body, "workflow", "usr_12345678", session,
                "1800000000", "nonce_1234567890", digest, hmac(canonical)))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("服务认证失败");
    }

    private static WorkflowCapabilityProperties properties() {
        WorkflowCapabilityProperties properties = new WorkflowCapabilityProperties();
        properties.setHmacSecret(SECRET);
        properties.setRunnerHmacSecret(SECRET);
        properties.setRunnerId("runner-a");
        properties.setRunnerPublicKey("public-key");
        return properties;
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static String hmac(String canonical) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    }
}
