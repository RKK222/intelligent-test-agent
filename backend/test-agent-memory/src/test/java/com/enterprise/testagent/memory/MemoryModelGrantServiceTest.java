package com.enterprise.testagent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.ModelCapability;
import com.enterprise.testagent.domain.memory.MemoryLearningJob;
import com.enterprise.testagent.domain.memory.MemoryModelGrantPayload;
import com.enterprise.testagent.domain.memory.MemoryModelGrantStore;
import com.enterprise.testagent.domain.memory.MemorySettings;
import com.enterprise.testagent.domain.memory.QaMemoryRepository;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.user.UserStatus;
import com.enterprise.testagent.model.gateway.ModelGatewayCatalogService;
import com.enterprise.testagent.model.gateway.ResolvedModel;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemoryModelGrantServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");

    private final Map<String, MemoryModelGrantPayload> saved = new LinkedHashMap<>();
    private QaMemoryRepository memories;
    private ModelGatewayCatalogService catalog;
    private MemoryModelGrantService service;

    @BeforeEach
    void setUp() {
        memories = mock(QaMemoryRepository.class);
        catalog = mock(ModelGatewayCatalogService.class);
        UserRepository users = mock(UserRepository.class);
        MemoryModelGrantStore grants = new MemoryModelGrantStore() {
            @Override
            public boolean save(String digest, MemoryModelGrantPayload payload, Duration ttl) {
                return saved.putIfAbsent(digest, payload) == null;
            }

            @Override
            public Optional<MemoryModelGrantPayload> consume(String digest) {
                return Optional.ofNullable(saved.remove(digest));
            }
        };
        when(users.findByUserId(new UserId("usr_1"))).thenReturn(Optional.of(new User(
                new UserId("usr_1"), "AUTH_1", "tester", "hash", null, null, null,
                UserStatus.ACTIVE, NOW, NOW)));
        QaMemoryProperties properties = new QaMemoryProperties();
        properties.setGrantTtl(Duration.ofMinutes(2));
        service = new MemoryModelGrantService(
                memories, grants, users, catalog, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void fixedChatModelWinsAndGrantIsBoundAndOneTime() {
        when(memories.loadSettings()).thenReturn(new MemorySettings("fixed-chat", true, 0, null, NOW));
        when(catalog.resolve(eq("fixed-chat"), eq(ModelCapability.CHAT))).thenReturn(mock(ResolvedModel.class));
        when(catalog.resolve(eq("run-chat"), eq(ModelCapability.CHAT))).thenReturn(mock(ResolvedModel.class));

        String selected = service.selectModel(job("run-chat")).orElseThrow();
        MemoryModelGrantService.IssuedGrant grant = service.issue("usr_1", "run_1", selected);
        MemoryModelGrantService.GrantIdentity identity = service.authenticate(
                grant.token(), "usr_1", "run_1");

        assertThat(selected).isEqualTo("fixed-chat");
        assertThat(identity.unifiedAuthId()).isEqualTo("AUTH_1");
        assertThat(identity.modelId()).isEqualTo("fixed-chat");
        assertThat(grant.toString()).contains("<redacted>").doesNotContain(grant.token());
        assertThatThrownBy(() -> service.authenticate(grant.token(), "usr_1", "run_1"))
                .isInstanceOfSatisfying(PlatformException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    void fallsBackOnlyToAvailableInternalRunModelAndConsumesMismatchedGrant() {
        when(memories.loadSettings()).thenReturn(new MemorySettings("missing-chat", true, 0, null, NOW));
        when(catalog.resolve(eq("missing-chat"), eq(ModelCapability.CHAT)))
                .thenThrow(new PlatformException(ErrorCode.NOT_FOUND, "missing"));
        when(catalog.resolve(eq("run-chat"), eq(ModelCapability.CHAT))).thenReturn(mock(ResolvedModel.class));

        String selected = service.selectModel(job("run-chat")).orElseThrow();
        MemoryModelGrantService.IssuedGrant grant = service.issue("usr_1", "run_1", selected);

        assertThat(selected).isEqualTo("run-chat");
        assertThatThrownBy(() -> service.authenticate(grant.token(), "usr_other", "run_1"))
                .isInstanceOf(PlatformException.class);
        assertThatThrownBy(() -> service.authenticate(grant.token(), "usr_1", "run_1"))
                .isInstanceOf(PlatformException.class);
    }

    private MemoryLearningJob job(String model) {
        return new MemoryLearningJob(
                "mlj_1", "run_1", "ses_1", "wrk_1", "usr_1", "app_1",
                "opencode", model, "PROCESSING", 0, NOW, "worker", NOW.plusSeconds(60),
                null, NOW, NOW);
    }
}
