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
import com.enterprise.testagent.domain.externalapi.ExternalApiScope;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** 验证外部凭据注册表只暴露完整、不可变且经过校验的内存快照。 */
class ExternalApiCredentialRegistryTest {

    private static final String TOOL_CODE = "deploy.bot";
    private static final String API_KEY = "taak_v1_AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE";

    @Test
    void startupLoadsCredentialAndAuthenticatesWithConstantSnapshotLookup() {
        RsaKeyService rsa = new RsaKeyService();
        ExternalApiCredentialCipher cipher = new ExternalApiCredentialCipher(rsa);
        FakeRepository repository = new FakeRepository(credential(cipher, API_KEY, true));
        ExternalApiCredentialRegistry registry = new ExternalApiCredentialRegistry(repository, cipher);

        registry.loadOnStartup();

        assertThat(registry.authenticate(TOOL_CODE, API_KEY).toolCode()).isEqualTo(TOOL_CODE);
        assertThat(registry.authenticate(TOOL_CODE, API_KEY).scopes())
                .containsExactly(ExternalApiScope.USER_SSH_KEY_READ);
    }

    @Test
    void startupRejectsUndecryptableDatabaseValueAndLeavesRegistryUnavailable() {
        RsaKeyService rsa = new RsaKeyService();
        ExternalApiCredentialCipher cipher = new ExternalApiCredentialCipher(rsa);
        ExternalApiCredential invalid = new ExternalApiCredential(
                new ExternalApiCredentialId("eac_invalid"), TOOL_CODE, "部署工具", "not-base64",
                "0".repeat(64), "taak_v1_...AAAA", true,
                Set.of(ExternalApiScope.USER_SSH_KEY_READ), Instant.EPOCH, Instant.EPOCH);
        ExternalApiCredentialRegistry registry = new ExternalApiCredentialRegistry(
                new FakeRepository(invalid), cipher);

        assertThatThrownBy(registry::loadOnStartup).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> registry.authenticate(TOOL_CODE, API_KEY))
                .isInstanceOfSatisfying(PlatformException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.EXTERNAL_API_UNAVAILABLE));
    }

    @Test
    void refreshAtomicallyInvalidatesOldKeyAndRetainsLastSnapshotOnFailure() {
        RsaKeyService rsa = new RsaKeyService();
        ExternalApiCredentialCipher cipher = new ExternalApiCredentialCipher(rsa);
        FakeRepository repository = new FakeRepository(credential(cipher, API_KEY, true));
        ExternalApiCredentialRegistry registry = new ExternalApiCredentialRegistry(repository, cipher);
        registry.loadOnStartup();
        String rotated = "taak_v1_AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI";

        repository.credential = credential(cipher, rotated, true);
        registry.refresh("trace_rotate");

        assertThatThrownBy(() -> registry.authenticate(TOOL_CODE, API_KEY))
                .isInstanceOfSatisfying(PlatformException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        assertThat(registry.authenticate(TOOL_CODE, rotated).apiKey()).isEqualTo(rotated);

        repository.failure = new IllegalStateException("database unavailable");
        registry.refresh("trace_compensation");
        assertThat(registry.authenticate(TOOL_CODE, rotated).toolCode()).isEqualTo(TOOL_CODE);
    }

    @Test
    void disabledCredentialAlwaysUsesUnauthenticatedContract() {
        ExternalApiCredentialCipher cipher = new ExternalApiCredentialCipher(new RsaKeyService());
        ExternalApiCredentialRegistry registry = new ExternalApiCredentialRegistry(
                new FakeRepository(credential(cipher, API_KEY, false)), cipher);
        registry.loadOnStartup();

        assertThatThrownBy(() -> registry.authenticate(TOOL_CODE, API_KEY))
                .isInstanceOfSatisfying(PlatformException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    void compensationRefreshConvergesMissedBroadcast() {
        ExternalApiCredentialCipher cipher = new ExternalApiCredentialCipher(new RsaKeyService());
        FakeRepository repository = new FakeRepository(credential(cipher, API_KEY, true));
        ExternalApiCredentialRegistry registry = new ExternalApiCredentialRegistry(repository, cipher);
        registry.loadOnStartup();
        String rotated = "taak_v1_AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI";
        repository.credential = credential(cipher, rotated, true);

        registry.compensationRefresh();

        assertThatThrownBy(() -> registry.authenticate(TOOL_CODE, API_KEY))
                .isInstanceOfSatisfying(PlatformException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        assertThat(registry.authenticate(TOOL_CODE, rotated).toolCode()).isEqualTo(TOOL_CODE);
    }

    @Test
    void concurrentReadersNeverObserveAPartialSnapshot() throws Exception {
        ExternalApiCredentialCipher cipher = new ExternalApiCredentialCipher(new RsaKeyService());
        FakeRepository repository = new FakeRepository(credential(cipher, API_KEY, true));
        ExternalApiCredentialRegistry registry = new ExternalApiCredentialRegistry(repository, cipher);
        registry.loadOnStartup();
        ExecutorService executor = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            for (int worker = 0; worker < 6; worker++) {
                executor.submit(() -> {
                    try {
                        start.await();
                        for (int iteration = 0; iteration < 500; iteration++) {
                            registry.authenticate(TOOL_CODE, API_KEY);
                        }
                    } catch (Throwable throwable) {
                        failure.compareAndSet(null, throwable);
                    }
                });
            }
            start.countDown();
            for (int iteration = 0; iteration < 50; iteration++) {
                registry.refresh("trace_concurrent_" + iteration);
            }
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(failure.get()).isNull();
    }

    private static ExternalApiCredential credential(
            ExternalApiCredentialCipher cipher, String apiKey, boolean enabled) {
        return new ExternalApiCredential(
                new ExternalApiCredentialId("eac_deploy"), TOOL_CODE, "部署工具",
                cipher.encrypt(apiKey), ExternalApiKeyGenerator.fingerprint(apiKey), "taak_v1_...AAAA",
                enabled, Set.of(ExternalApiScope.USER_SSH_KEY_READ), Instant.EPOCH, Instant.EPOCH);
    }

    /** 单凭据仓储替身，可在运行时切换记录或模拟数据库故障。 */
    private static final class FakeRepository implements ExternalApiCredentialRepository {
        private ExternalApiCredential credential;
        private RuntimeException failure;

        private FakeRepository(ExternalApiCredential credential) {
            this.credential = credential;
        }

        @Override
        public List<ExternalApiCredential> findAll() {
            if (failure != null) {
                throw failure;
            }
            return credential == null ? List.of() : List.of(credential);
        }

        @Override
        public PageResponse<ExternalApiCredential> findPage(String keyword, Boolean enabled, PageRequest pageRequest) {
            return new PageResponse<>(findAll(), pageRequest.page(), pageRequest.size(), credential == null ? 0 : 1);
        }

        @Override
        public Optional<ExternalApiCredential> findById(ExternalApiCredentialId credentialId) {
            return Optional.ofNullable(credential);
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
            this.credential = null;
            return true;
        }
    }
}
