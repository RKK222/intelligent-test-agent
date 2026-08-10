package com.enterprise.testagent.system.management.externalapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.RsaKeyService;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.externalapi.ExternalApiCredential;
import com.enterprise.testagent.domain.externalapi.ExternalApiCredentialId;
import com.enterprise.testagent.domain.externalapi.ExternalApiCredentialRepository;
import com.enterprise.testagent.domain.externalapi.ExternalApiCredentialsUpdatedEvent;
import com.enterprise.testagent.domain.externalapi.ExternalApiScope;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationEventPublisher;

/** 验证外部 API 凭据管理的密文持久化、校验、查看、轮换和删除语义。 */
class ExternalApiCredentialApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-09T04:00:00Z");

    @Test
    void productionSpringBeanUsesRepositoryCipherAndGeneratorConstructor() {
        new ApplicationContextRunner()
                .withBean(ExternalApiCredentialRepository.class, () -> new FakeRepository())
                .withBean(ExternalApiCredentialCipher.class, () -> org.mockito.Mockito.mock(
                        ExternalApiCredentialCipher.class))
                .withBean(ExternalApiKeyGenerator.class, () -> org.mockito.Mockito.mock(ExternalApiKeyGenerator.class))
                .withBean(ExternalApiCredentialApplicationService.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ExternalApiCredentialApplicationService.class);
                });
    }

    @Test
    void createPersistsCiphertextAndPublishesRefreshWithoutCredentialMaterial() {
        Fixture fixture = fixture();

        ExternalApiCredentialResponses.Created response = fixture.service.create(
                new ExternalApiCredentialResponses.CreateCommand(
                        "deploy.bot", "  部署工具  ", Set.of(ExternalApiScope.USER_SSH_KEY_READ), true),
                "trace_create");

        assertThat(response.apiKey()).matches("taak_v1_[A-Za-z0-9_-]{43}");
        assertThat(fixture.repository.credential.toolName()).isEqualTo("部署工具");
        assertThat(fixture.repository.credential.encryptedApiKey()).doesNotContain(response.apiKey());
        assertThat(fixture.cipher.decrypt(fixture.repository.credential.encryptedApiKey()))
                .isEqualTo(response.apiKey());
        assertThat(fixture.events).singleElement()
                .isEqualTo(new ExternalApiCredentialsUpdatedEvent("trace_create"));
    }

    @Test
    void rejectsDuplicateToolCodeAndEmptyScopes() {
        Fixture fixture = fixture();
        fixture.repository.credential = existing(fixture.cipher, "deploy.bot");

        assertThatThrownBy(() -> fixture.service.create(
                new ExternalApiCredentialResponses.CreateCommand(
                        "deploy.bot", "部署工具", Set.of(ExternalApiScope.USER_SSH_KEY_READ), true),
                "trace_duplicate"))
                .isInstanceOfSatisfying(PlatformException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        assertThatThrownBy(() -> fixture.service.create(
                new ExternalApiCredentialResponses.CreateCommand("other", "其它", Set.of(), true),
                "trace_scope"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revealReturnsCurrentKeyAndRotateReplacesItImmediately() {
        Fixture fixture = fixture();
        fixture.repository.credential = existing(fixture.cipher, "deploy.bot");
        String oldKey = fixture.cipher.decrypt(fixture.repository.credential.encryptedApiKey());

        assertThat(fixture.service.reveal("eac_existing").apiKey()).isEqualTo(oldKey);
        ExternalApiCredentialResponses.Revealed rotated = fixture.service.rotate(
                "eac_existing", "trace_rotate");

        assertThat(rotated.apiKey()).isNotEqualTo(oldKey);
        assertThat(fixture.cipher.decrypt(fixture.repository.credential.encryptedApiKey()))
                .isEqualTo(rotated.apiKey());
        assertThat(fixture.repository.credential.apiKeyFingerprint())
                .isEqualTo(ExternalApiKeyGenerator.fingerprint(rotated.apiKey()));
    }

    @Test
    void updateKeepsToolCodeAndDeleteRemovesAggregate() {
        Fixture fixture = fixture();
        fixture.repository.credential = existing(fixture.cipher, "deploy.bot");

        fixture.service.update("eac_existing", new ExternalApiCredentialResponses.UpdateCommand(
                "新名称", Set.of(ExternalApiScope.USER_SSH_KEY_READ), false), "trace_update");
        assertThat(fixture.repository.credential.toolCode()).isEqualTo("deploy.bot");
        assertThat(fixture.repository.credential.enabled()).isFalse();

        fixture.service.delete("eac_existing", "trace_delete");
        assertThat(fixture.repository.credential).isNull();
    }

    private static Fixture fixture() {
        ExternalApiCredentialCipher cipher = new ExternalApiCredentialCipher(new RsaKeyService());
        FakeRepository repository = new FakeRepository();
        List<Object> events = new ArrayList<>();
        ApplicationEventPublisher publisher = events::add;
        ExternalApiCredentialApplicationService service = new ExternalApiCredentialApplicationService(
                repository,
                cipher,
                new ExternalApiKeyGenerator(),
                publisher,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> "eac_generated");
        return new Fixture(service, repository, cipher, events);
    }

    private static ExternalApiCredential existing(ExternalApiCredentialCipher cipher, String toolCode) {
        String key = "taak_v1_AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE";
        return new ExternalApiCredential(
                new ExternalApiCredentialId("eac_existing"), toolCode, "部署工具", cipher.encrypt(key),
                ExternalApiKeyGenerator.fingerprint(key), ExternalApiKeyGenerator.keyHint(key), true,
                Set.of(ExternalApiScope.USER_SSH_KEY_READ), NOW, NOW);
    }

    private record Fixture(
            ExternalApiCredentialApplicationService service,
            FakeRepository repository,
            ExternalApiCredentialCipher cipher,
            List<Object> events) {
    }

    /** 保存单个聚合的仓储替身，便于断言没有明文落库。 */
    private static final class FakeRepository implements ExternalApiCredentialRepository {
        private ExternalApiCredential credential;

        @Override
        public List<ExternalApiCredential> findAll() {
            return credential == null ? List.of() : List.of(credential);
        }

        @Override
        public PageResponse<ExternalApiCredential> findPage(String keyword, Boolean enabled, PageRequest pageRequest) {
            return new PageResponse<>(findAll(), pageRequest.page(), pageRequest.size(), credential == null ? 0 : 1);
        }

        @Override
        public Optional<ExternalApiCredential> findById(ExternalApiCredentialId credentialId) {
            return credential != null && credential.credentialId().equals(credentialId)
                    ? Optional.of(credential) : Optional.empty();
        }

        @Override
        public boolean existsByToolCode(String toolCode) {
            return credential != null && credential.toolCode().equals(toolCode);
        }

        @Override
        public void insert(ExternalApiCredential credential) {
            this.credential = credential;
        }

        @Override
        public void updateDetails(ExternalApiCredential credential) {
            this.credential = credential;
        }

        @Override
        public void updateKey(ExternalApiCredential credential) {
            this.credential = credential;
        }

        @Override
        public boolean delete(ExternalApiCredentialId credentialId) {
            if (credential == null || !credential.credentialId().equals(credentialId)) {
                return false;
            }
            credential = null;
            return true;
        }
    }
}
