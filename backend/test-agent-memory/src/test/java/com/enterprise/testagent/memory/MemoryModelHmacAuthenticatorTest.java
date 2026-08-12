package com.enterprise.testagent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.configuration.InternalModelProvider;
import com.enterprise.testagent.domain.configuration.InternalModelProviderModel;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRuntimeConfig;
import com.enterprise.testagent.domain.configuration.ModelCapability;
import com.enterprise.testagent.domain.memory.MemoryHmacNonceStore;
import com.enterprise.testagent.domain.memory.MemorySettings;
import com.enterprise.testagent.domain.memory.QaMemoryRepository;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.user.UserStatus;
import com.enterprise.testagent.model.gateway.ModelGatewayCatalogService;
import com.enterprise.testagent.model.gateway.ResolvedModel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

class MemoryModelHmacAuthenticatorTest {
    private static final Instant NOW = Instant.parse("2026-08-09T08:00:00Z");
    private static final String SECRET = "memory-hmac-secret-1234567890abcdef";
    private static final String USER_ID = "usr_memory";
    private static final byte[] EMBEDDING_BODY =
            "{\"input\":[\"检索\"],\"model\":\"enterprise-embedding\"}"
                    .getBytes(StandardCharsets.UTF_8);

    private QaMemoryProperties properties;
    private QaMemoryRepository repository;
    private ModelGatewayCatalogService catalog;
    private Set<String> claimedNonces;
    private MemoryModelHmacAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        properties = new QaMemoryProperties();
        properties.setEnabled(true);
        properties.setModelGatewayHmacClientId("mem0-cluster");
        properties.setModelGatewayHmacSecret(SECRET);
        claimedNonces = new HashSet<>();
        MemoryHmacNonceStore nonces = (clientId, nonce, ttl) ->
                claimedNonces.add(clientId + ":" + nonce);
        UserRepository users = mock(UserRepository.class);
        when(users.findByUserId(new UserId(USER_ID))).thenReturn(Optional.of(new User(
                new UserId(USER_ID), "AUTH_MEMORY", "memory-user", "hash", null, null, null,
                UserStatus.ACTIVE, NOW, NOW)));
        repository = mock(QaMemoryRepository.class);
        when(repository.loadSettings()).thenReturn(new MemorySettings(
                "memory-chat", "enterprise-embedding", "memory-bge-small-zh-v1.5",
                1L, "usr_admin", NOW));
        catalog = mock(ModelGatewayCatalogService.class);
        when(catalog.resolve("enterprise-embedding", ModelCapability.EMBEDDING))
                .thenReturn(resolved("enterprise-embedding", 768));
        when(catalog.resolve("memory-bge-small-zh-v1.5", ModelCapability.EMBEDDING))
                .thenReturn(resolved("memory-bge-small-zh-v1.5", 512));
        when(catalog.resolve("memory-chat", ModelCapability.CHAT))
                .thenReturn(resolved("memory-chat", null));
        authenticator = new MemoryModelHmacAuthenticator(
                properties, nonces, users, repository, catalog,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void acceptsSignedEmbeddingIdentityAndConfiguredModel() {
        ServerWebExchange exchange = signedExchange(
                "EMBEDDING", "query", "0123456789abcdef0123456789abcdef", EMBEDDING_BODY,
                "query");

        MemoryModelHmacAuthenticator.Identity identity =
                authenticator.authenticate(exchange, EMBEDDING_BODY);

        assertThat(identity.userId()).isEqualTo(USER_ID);
        assertThat(identity.capability()).isEqualTo(ModelCapability.EMBEDDING);
        assertThatCode(() -> authenticator.requireModel(identity, "enterprise-embedding"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsReplayBodyTamperingAndUnsignedInputTypeChange() {
        String replayNonce = "1123456789abcdef0123456789abcdef";
        ServerWebExchange first = signedExchange(
                "EMBEDDING", "query", replayNonce, EMBEDDING_BODY, "query");
        authenticator.authenticate(first, EMBEDDING_BODY);

        assertThatThrownBy(() -> authenticator.authenticate(first, EMBEDDING_BODY))
                .hasMessageContaining("HMAC");

        ServerWebExchange tamperedBody = signedExchange(
                "EMBEDDING", "query", "2123456789abcdef0123456789abcdef",
                EMBEDDING_BODY, "query");
        assertThatThrownBy(() -> authenticator.authenticate(
                tamperedBody, "{}".getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("HMAC");

        ServerWebExchange tamperedType = signedExchange(
                "EMBEDDING", "document", "3123456789abcdef0123456789abcdef",
                EMBEDDING_BODY, "query");
        assertThatThrownBy(() -> authenticator.authenticate(tamperedType, EMBEDDING_BODY))
                .hasMessageContaining("HMAC");
    }

    @Test
    void rejectsEmbeddingModelWithWrongDeclaredDimension() {
        when(catalog.resolve("memory-bge-small-zh-v1.5", ModelCapability.EMBEDDING))
                .thenReturn(resolved("memory-bge-small-zh-v1.5", 768));
        MemoryModelHmacAuthenticator.Identity identity = new MemoryModelHmacAuthenticator.Identity(
                USER_ID, "AUTH_MEMORY", "run-memory", "session-memory", "operation-memory",
                ModelCapability.EMBEDDING);

        assertThatThrownBy(() -> authenticator.requireModel(identity, "memory-bge-small-zh-v1.5"))
                .hasMessageContaining("维度");
    }

    @Test
    void rejectsOperationIdBeyondSharedDatabaseContract() {
        String operationId = "o".repeat(129);
        ServerWebExchange exchange = signedExchange(
                "EMBEDDING", "query", "4123456789abcdef0123456789abcdef",
                EMBEDDING_BODY, "query", operationId);

        assertThatThrownBy(() -> authenticator.authenticate(exchange, EMBEDDING_BODY))
                .hasMessageContaining("HMAC");
    }

    private ServerWebExchange signedExchange(
            String capability,
            String actualInputType,
            String nonce,
            byte[] signedBody,
            String signedInputType) {
        return signedExchange(
                capability, actualInputType, nonce, signedBody, signedInputType, "operation-memory");
    }

    private ServerWebExchange signedExchange(
            String capability,
            String actualInputType,
            String nonce,
            byte[] signedBody,
            String signedInputType,
            String operationId) {
        String path = "/api/internal/platform/model-gateway/v1/embeddings";
        String digest = sha256(signedBody);
        String timestamp = Long.toString(NOW.getEpochSecond());
        String canonical = String.join("\n",
                "POST", path, digest, "mem0-cluster", USER_ID, "run-memory", "session-memory",
                operationId, timestamp, nonce, capability,
                signedInputType == null ? "" : signedInputType);
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.post(path)
                .header("X-Memory-Client-Id", "mem0-cluster")
                .header("X-Memory-User-Id", USER_ID)
                .header("X-Memory-Run-Id", "run-memory")
                .header("X-Memory-Session-Id", "session-memory")
                .header("X-Memory-Operation-Id", operationId)
                .header("X-Memory-Timestamp", timestamp)
                .header("X-Memory-Nonce", nonce)
                .header("X-Memory-Body-SHA256", digest)
                .header("X-Memory-Capability", capability)
                .header("X-Memory-Signature", hmac(canonical));
        if (actualInputType != null) {
            request.header("X-Embedding-Input-Type", actualInputType);
        }
        return MockServerWebExchange.from(request.build());
    }

    private static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String hmac(String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static ResolvedModel resolved(String modelId, Integer dimension) {
        Set<ModelCapability> capabilities = dimension == null
                ? Set.of(ModelCapability.CHAT)
                : Set.of(ModelCapability.EMBEDDING);
        InternalModelProvider provider = new InternalModelProvider(
                "provider-memory", "Memory Provider", "http://memory-model/v1", true, 0,
                NOW, NOW);
        InternalModelProviderModel model = new InternalModelProviderModel(
                "provider-memory", modelId, modelId, modelId, null, dimension, true,
                capabilities, capabilities, NOW, NOW, NOW);
        return new ResolvedModel(new InternalModelProviderRuntimeConfig(provider, "secret"), model);
    }
}
