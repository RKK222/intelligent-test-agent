package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.lobehub.LobehubGrantPayload;
import com.enterprise.testagent.domain.lobehub.LobehubSsoStore;
import com.enterprise.testagent.domain.lobehub.LobehubTicketPayload;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.integration.lobehub.LobehubHmacAuthenticator;
import com.enterprise.testagent.integration.lobehub.LobehubIntegrationProperties;
import com.enterprise.testagent.integration.lobehub.LobehubModelIdentity;
import com.enterprise.testagent.integration.lobehub.LobehubSsoRedeemResult;
import com.enterprise.testagent.integration.lobehub.LobehubSsoService;
import com.enterprise.testagent.integration.lobehub.LobehubSsoTicketIssue;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class LobehubSsoControllerTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String TRACE_ID = "trace_lobehub_1234567890";

    @Test
    void authenticatedPlatformUserCanIssueFormPostTicket() {
        FakeSsoService service = new FakeSsoService();

        userClient(service).post()
                .uri(LobehubSsoController.TICKETS_PATH)
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.ticket").isEqualTo("ticket-value")
                .jsonPath("$.data.consumeUrl").isEqualTo("http://lobehub.internal/api/auth/platform/consume");

        assertThat(service.issuedFor.userId().value()).isEqualTo("usr_lobehub");
    }

    @Test
    void signedServiceCanRedeemOnceAndRevokeServerSideGrant() {
        FakeSsoService service = new FakeSsoService();
        WebTestClient client = serviceClient(service);
        String redeemBody = "{\"ticket\":\"ticket-value\"}";

        signedPost(client, LobehubSsoController.REDEEM_PATH, redeemBody)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.userId").isEqualTo("usr_lobehub")
                .jsonPath("$.data.department").isEqualTo("研发一部")
                .jsonPath("$.data.modelGrant").isEqualTo("grant-value");
        assertThat(service.redeemedTicket).isEqualTo("ticket-value");

        String revokeBody = "{\"modelGrant\":\"grant-value\"}";
        signedPost(client, LobehubSsoController.REVOKE_PATH, revokeBody)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.revoked").isEqualTo(true);
        assertThat(service.revokedGrant).isEqualTo("grant-value");
    }

    @Test
    void tamperedBodyIsRejectedBeforeTicketRedeem() {
        FakeSsoService service = new FakeSsoService();
        WebTestClient client = serviceClient(service);
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String nonce = "nonce-value-123456";
        String signature = LobehubHmacAuthenticator.sign(
                SECRET,
                "POST",
                LobehubSsoController.REDEEM_PATH,
                "{\"ticket\":\"original\"}".getBytes(StandardCharsets.UTF_8),
                timestamp,
                nonce);

        client.post()
                .uri(LobehubSsoController.REDEEM_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(LobehubSsoController.TIMESTAMP_HEADER, timestamp)
                .header(LobehubSsoController.NONCE_HEADER, nonce)
                .header(LobehubSsoController.SIGNATURE_HEADER, signature)
                .bodyValue("{\"ticket\":\"tampered\"}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED");

        assertThat(service.redeemedTicket).isNull();
    }

    @Test
    void missingServiceSignatureHeadersUseTheSameUnauthenticatedEnvelope() {
        FakeSsoService service = new FakeSsoService();

        serviceClient(service).post()
                .uri(LobehubSsoController.REDEEM_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"ticket\":\"ticket-value\"}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED");

        assertThat(service.redeemedTicket).isNull();
    }

    private static WebTestClient.ResponseSpec signedPost(WebTestClient client, String path, String body) {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String nonce = "nonce-" + java.util.UUID.randomUUID();
        String signature = LobehubHmacAuthenticator.sign(
                SECRET,
                "POST",
                path,
                body.getBytes(StandardCharsets.UTF_8),
                timestamp,
                nonce);
        return client.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .header(LobehubSsoController.TIMESTAMP_HEADER, timestamp)
                .header(LobehubSsoController.NONCE_HEADER, nonce)
                .header(LobehubSsoController.SIGNATURE_HEADER, signature)
                .bodyValue(body)
                .exchange();
    }

    private static WebTestClient userClient(FakeSsoService service) {
        AuthPrincipal principal = new AuthPrincipal(
                "platform-token",
                new UserId("usr_lobehub"),
                "平台用户",
                "AUTH_LOBEHUB",
                List.of("USER"),
                Instant.now().minusSeconds(10),
                Instant.now().plusSeconds(3600));
        return baseClient(service)
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .build();
    }

    private static WebTestClient serviceClient(FakeSsoService service) {
        return baseClient(service).build();
    }

    private static WebTestClient.ControllerSpec baseClient(FakeSsoService service) {
        LobehubIntegrationProperties properties = new LobehubIntegrationProperties();
        properties.setHmacSecret(SECRET);
        LobehubHmacAuthenticator authenticator = new LobehubHmacAuthenticator(new NonceStore(), properties);
        return WebTestClient.bindToController(new LobehubSsoController(
                        service,
                        authenticator,
                        new ObjectMapper()))
                .webFilter(new TraceIdWebFilter())
                .controllerAdvice(new GlobalExceptionHandler());
    }

    private static final class FakeSsoService implements LobehubSsoService {
        private AuthPrincipal issuedFor;
        private String redeemedTicket;
        private String revokedGrant;

        @Override
        public LobehubSsoTicketIssue issue(AuthPrincipal principal) {
            issuedFor = principal;
            return new LobehubSsoTicketIssue(
                    "ticket-value",
                    Instant.parse("2026-07-30T01:01:00Z"),
                    "http://lobehub.internal/api/auth/platform/consume");
        }

        @Override
        public LobehubSsoRedeemResult redeem(String ticket) {
            redeemedTicket = ticket;
            return new LobehubSsoRedeemResult(
                    "usr_lobehub",
                    "AUTH_LOBEHUB",
                    "平台用户",
                    "AUTH_LOBEHUB@example.internal",
                    "研发一部",
                    "department-key",
                    "member",
                    List.of("USER"),
                    "grant-value",
                    Instant.parse("2026-08-29T01:00:00Z"));
        }

        @Override
        public LobehubModelIdentity authenticateModelGrant(String grant) {
            return new LobehubModelIdentity("usr_lobehub", "AUTH_LOBEHUB", "model-gateway");
        }

        @Override
        public void revokeModelGrant(String grant) {
            revokedGrant = grant;
        }
    }

    private static final class NonceStore implements LobehubSsoStore {
        private final Set<String> nonces = new HashSet<>();

        @Override
        public void saveTicket(String digest, LobehubTicketPayload payload, Duration ttl) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<LobehubTicketPayload> consumeTicket(String digest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean reserveNonce(String digest, Duration ttl) {
            return nonces.add(digest);
        }

        @Override
        public void rotateGrant(String userId, String digest, LobehubGrantPayload payload, Duration ttl) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<LobehubGrantPayload> findGrant(String digest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void revokeGrant(String digest) {
            throw new UnsupportedOperationException();
        }
    }
}
