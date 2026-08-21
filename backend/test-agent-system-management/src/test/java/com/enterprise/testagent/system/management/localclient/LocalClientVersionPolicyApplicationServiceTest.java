package com.enterprise.testagent.system.management.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.localclient.LocalClientVersionModels;
import com.enterprise.testagent.domain.localclient.LocalClientVersionRepository;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LocalClientVersionPolicyApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T10:20:00Z");
    private static final UserId ADMIN = new UserId("usr_admin");
    private static final UserId USER = new UserId("usr_user");

    @Test
    void shouldResolveUserOverrideBeforeGlobalAndPreserveRevisionWhenCleared() {
        InMemoryVersionRepository repository = new InMemoryVersionRepository();
        repository.releases.put("20260820100000", release("20260820100000", true));
        repository.releases.put("20260820110000", release("20260820110000", true));
        LocalClientVersionPolicyApplicationService service = service(repository);

        service.setGlobalTarget("20260820100000", ADMIN);
        LocalClientVersionPolicyApplicationService.EffectivePolicy global = service.effectivePolicy(USER);
        service.setUserTarget(USER, "20260820110000", ADMIN);
        LocalClientVersionPolicyApplicationService.EffectivePolicy overridden = service.effectivePolicy(USER);
        service.clearUserTarget(USER, ADMIN);
        LocalClientVersionPolicyApplicationService.EffectivePolicy restored = service.effectivePolicy(USER);

        assertThat(global.targetVersion()).isEqualTo("20260820100000");
        assertThat(global.source()).isEqualTo("GLOBAL");
        assertThat(overridden.targetVersion()).isEqualTo("20260820110000");
        assertThat(overridden.source()).isEqualTo("USER");
        assertThat(restored.targetVersion()).isEqualTo("20260820100000");
        assertThat(restored.source()).isEqualTo("GLOBAL");
        assertThat(restored.policyRevision()).isGreaterThan(overridden.policyRevision());
        assertThat(repository.audits).extracting(LocalClientVersionModels.PolicyAudit::action)
                .containsExactly("SET_GLOBAL", "SET_USER", "CLEAR_USER");
    }

    @Test
    void shouldRejectUnknownOrIncompatibleRelease() {
        InMemoryVersionRepository repository = new InMemoryVersionRepository();
        repository.releases.put("20260820110000", release("20260820110000", false));
        LocalClientVersionPolicyApplicationService service = service(repository);

        assertThatThrownBy(() -> service.setGlobalTarget("20260820120000", ADMIN))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> service.setGlobalTarget("20260820110000", ADMIN))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    private static LocalClientVersionPolicyApplicationService service(InMemoryVersionRepository repository) {
        return new LocalClientVersionPolicyApplicationService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static LocalClientVersionModels.Release release(String version, boolean compatible) {
        return new LocalClientVersionModels.Release(
                version,
                "linux",
                "arm64",
                1,
                1,
                "local-opencode-client.v1",
                "http://downloads.example/releases/" + version + "/manifest.json",
                "a".repeat(64),
                "signature",
                compatible,
                NOW,
                NOW,
                List.of(new LocalClientVersionModels.Artifact(
                        "CLIENT_JAR",
                        "http://downloads.example/releases/" + version + "/client.jar",
                        1024,
                        "b".repeat(64),
                        "artifact-signature")));
    }

    private static final class InMemoryVersionRepository implements LocalClientVersionRepository {
        private final Map<String, LocalClientVersionModels.Release> releases = new LinkedHashMap<>();
        private final Map<UserId, LocalClientVersionModels.UserPolicy> userPolicies = new LinkedHashMap<>();
        private final List<LocalClientVersionModels.PolicyAudit> audits = new ArrayList<>();
        private LocalClientVersionModels.GlobalPolicy globalPolicy;
        private long revision;

        @Override
        public Optional<LocalClientVersionModels.Release> findRelease(String version) {
            return Optional.ofNullable(releases.get(version));
        }

        @Override
        public List<LocalClientVersionModels.Release> findReleases() {
            return List.copyOf(releases.values());
        }

        @Override
        public void insertRelease(LocalClientVersionModels.Release release) {
            if (releases.putIfAbsent(release.version(), release) != null) {
                throw new IllegalStateException("duplicate release");
            }
        }

        @Override
        public long nextPolicyRevision() {
            return ++revision;
        }

        @Override
        public Optional<LocalClientVersionModels.GlobalPolicy> findGlobalPolicy() {
            return Optional.ofNullable(globalPolicy);
        }

        @Override
        public void saveGlobalPolicy(LocalClientVersionModels.GlobalPolicy policy) {
            globalPolicy = policy;
        }

        @Override
        public Optional<LocalClientVersionModels.UserPolicy> findUserPolicy(UserId userId) {
            return Optional.ofNullable(userPolicies.get(userId));
        }

        @Override
        public List<LocalClientVersionModels.UserPolicy> findUserPolicies() {
            return List.copyOf(userPolicies.values());
        }

        @Override
        public void saveUserPolicy(LocalClientVersionModels.UserPolicy policy) {
            userPolicies.put(policy.userId(), policy);
        }

        @Override
        public void savePolicyAudit(LocalClientVersionModels.PolicyAudit audit) {
            audits.add(audit);
        }

        @Override
        public void insertRollout(LocalClientVersionModels.Rollout rollout) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<LocalClientVersionModels.Rollout> findRollouts() {
            return List.of();
        }

        @Override
        public Optional<LocalClientVersionModels.Rollout> findRollout(String rolloutId) {
            return Optional.empty();
        }

        @Override
        public Optional<LocalClientVersionModels.Rollout> findRolloutForUpdate(String rolloutId) {
            return Optional.empty();
        }

        @Override
        public boolean completeRollout(String rolloutId, String status, Instant completedAt) {
            return false;
        }

        @Override
        public void insertAttempts(List<LocalClientVersionModels.Attempt> attempts) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<LocalClientVersionModels.Attempt> findAttemptsByRollout(String rolloutId) {
            return List.of();
        }

        @Override
        public Optional<LocalClientVersionModels.Attempt> findAttempt(String commandId) {
            return Optional.empty();
        }

        @Override
        public List<LocalClientVersionModels.Attempt> findDispatchableAttempts(int limit, int offset, Instant now) {
            return List.of();
        }

        @Override
        public boolean transitionAttempt(
                String commandId,
                String expectedStatus,
                String status,
                String releaseDigest,
                String errorCode,
                Instant observedAt) {
            return false;
        }
    }
}
