package com.enterprise.testagent.integration.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.GitRemoteService;
import com.enterprise.testagent.common.git.SshKeyEncryptionService;
import com.enterprise.testagent.domain.auth.TokenSessionMarkerStore;
import com.enterprise.testagent.domain.configuration.ApplicationDefinition;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.dictionary.DictionaryRepository;
import com.enterprise.testagent.domain.dictionary.UserRoleRepository;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.user.UserStatus;
import com.enterprise.testagent.domain.workflowcapability.CheckoutTicketPayload;
import com.enterprise.testagent.domain.workflowcapability.WorkflowCapabilityStore;
import com.enterprise.testagent.domain.workflowcapability.WorkflowModelGrantPayload;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkflowCapabilityApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T08:00:00Z");
    private static final UserId USER_ID = new UserId("usr_workflow_owner");
    private static final WorkflowCapabilityCaller CALLER =
            new WorkflowCapabilityCaller(USER_ID, "a".repeat(64));
    private static final String RUNNER_PUBLIC_KEY =
            "-----BEGIN PUBLIC KEY-----\nYWJj\n-----END PUBLIC KEY-----\n";

    @Test
    void checkoutTicketIsOneTimeRunnerBoundAndRechecksCurrentRepositoryMembership() {
        Fixture fixture = fixture();

        WorkflowCapabilityApplicationService.CheckoutTicketIssue issue = fixture.service().issueCheckoutTicket(
                CALLER,
                fixture.repository().repositoryId().value(),
                "task_12345678",
                "run_12345678",
                "runner-a",
                RUNNER_PUBLIC_KEY,
                "feature/impact",
                null);

        assertThat(issue.expiresAt()).isEqualTo(NOW.plusSeconds(120));
        assertThat(fixture.store().checkoutTickets).hasSize(1);
        assertThat(fixture.store().checkoutTickets.keySet()).noneMatch(key -> key.contains(issue.ticketId()));

        fixture.membership().active = false;
        assertThatThrownBy(() -> fixture.service().consumeCheckoutTicket(
                        issue.ticketId(), "task_12345678", "run_12345678", "runner-a"))
                .isInstanceOfSatisfying(
                        PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> fixture.service().consumeCheckoutTicket(
                        issue.ticketId(), "task_12345678", "run_12345678", "runner-a"))
                .isInstanceOfSatisfying(
                        PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    void checkoutMaterialOnlyIncludesOtherPlatformRepositoriesTheUserCanAccess() {
        Fixture fixture = fixture();
        WorkflowCapabilityApplicationService.CheckoutTicketIssue issue = fixture.service().issueCheckoutTicket(
                CALLER,
                fixture.repository().repositoryId().value(),
                "task_12345678",
                "run_12345678",
                "runner-a",
                RUNNER_PUBLIC_KEY,
                "feature/impact",
                "main");

        WorkflowCapabilityApplicationService.CheckoutMaterial material = fixture.service().consumeCheckoutTicket(
                issue.ticketId(), "task_12345678", "run_12345678", "runner-a");

        assertThat(material.authorizedSubmodules())
                .extracting(WorkflowCapabilityApplicationService.AuthorizedSubmoduleMaterial::repositoryId)
                .containsExactly(fixture.submoduleRepository().repositoryId().value());
        assertThat(material.authorizedSubmodules().getFirst().remoteUrl())
                .isEqualTo(fixture.submoduleRepository().gitUrl());
        assertThat(material.targetBranch()).isEqualTo("feature/impact");
    }

    @Test
    void checkoutTicketRejectsAnUnlistedTargetBranchBeforeIssuingCredentials() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> fixture.service().issueCheckoutTicket(
                        CALLER,
                        fixture.repository().repositoryId().value(),
                        "task_12345678",
                        "run_12345678",
                        "runner-a",
                        RUNNER_PUBLIC_KEY,
                        "secret/not-visible",
                        "main"))
                .isInstanceOfSatisfying(
                        PlatformException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.VALIDATION_ERROR));

        assertThat(fixture.store().checkoutTickets).isEmpty();
    }

    @Test
    void modelGrantSupportsAgentScopeAndRunScopedRevocation() {
        Fixture fixture = fixture();

        WorkflowCapabilityApplicationService.ModelGrantIssue intentGrant = fixture.service().issueModelGrant(
                CALLER,
                "intent_task_12345678",
                "intent_run_12345678",
                List.of("agentscope-intent"));
        WorkflowCapabilityApplicationService.ModelGrantIssue analyzerGrant = fixture.service().issueModelGrant(
                CALLER,
                "task_12345678",
                "run_12345678",
                List.of("codex", "opencode", "agentscope-synthesis"));

        assertThat(fixture.service().authenticateModelGrant(intentGrant.grant()).userId())
                .isEqualTo(USER_ID.value());
        assertThat(fixture.service().authenticateModelGrant(analyzerGrant.grant()).scope())
                .isEqualTo("workflow-model-gateway");

        fixture.service().revokeModelGrants(CALLER, "task_12345678", "run_12345678");

        assertThatThrownBy(() -> fixture.service().authenticateModelGrant(analyzerGrant.grant()))
                .isInstanceOfSatisfying(
                        PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        assertThat(fixture.service().authenticateModelGrant(intentGrant.grant()).userId())
                .isEqualTo(USER_ID.value());

        assertThatThrownBy(() -> fixture.service().issueModelGrant(
                        CALLER,
                        "task_12345678",
                        "run_12345678",
                        List.of("codex")))
                .isInstanceOfSatisfying(
                        PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    void modelGrantRefreshExtendsTheSameOpaqueGrantInsteadOfReplacingIt() {
        Fixture fixture = fixture();
        WorkflowCapabilityApplicationService.ModelGrantIssue issued = fixture.service().issueModelGrant(
                CALLER,
                "task_12345678",
                "run_12345678",
                List.of("codex"));

        WorkflowCapabilityApplicationService.ModelGrantRefresh refreshed =
                fixture.service().refreshModelGrant(
                        CALLER,
                        issued.grantId(),
                        "task_12345678",
                        "run_12345678");

        assertThat(refreshed.grantId()).isEqualTo(issued.grantId());
        assertThat(fixture.store().grants).hasSize(1);
        assertThat(fixture.service().authenticateModelGrant(issued.grant()).userId())
                .isEqualTo(USER_ID.value());
    }

    @Test
    void modelGrantBecomesInvalidImmediatelyWhenPlatformSessionIsRevoked() {
        Fixture fixture = fixture();
        WorkflowCapabilityApplicationService.ModelGrantIssue grant = fixture.service().issueModelGrant(
                CALLER,
                "task_12345678",
                "run_12345678",
                List.of("codex"));

        fixture.membership().sessionActive = false;

        assertThatThrownBy(() -> fixture.service().authenticateModelGrant(grant.grant()))
                .isInstanceOfSatisfying(
                        PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    void modelGrantBecomesInvalidWhenTheUsersUnifiedAuthenticationIdChanges() {
        Fixture fixture = fixture();
        WorkflowCapabilityApplicationService.ModelGrantIssue grant = fixture.service().issueModelGrant(
                CALLER,
                "task_12345678",
                "run_12345678",
                List.of("codex"));

        fixture.user().value = activeUser("AUTH_CHANGED");

        assertThatThrownBy(() -> fixture.service().authenticateModelGrant(grant.grant()))
                .isInstanceOfSatisfying(
                        PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    private static Fixture fixture() {
        User user = activeUser("AUTH_WORKFLOW");
        UserState userState = new UserState(user);
        CodeRepository repository = new CodeRepository(
                new CodeRepositoryId("repo_workflow_12345678"),
                "https://git.example.internal/group/repository.git",
                "订单代码库",
                "orders-service",
                CodeRepositoryType.APPLICATION_CODE_REPOSITORY.value(),
                false,
                NOW,
                NOW);
        CodeRepository submoduleRepository = new CodeRepository(
                new CodeRepositoryId("repo_contracts_12345678"),
                "https://git.example.internal/group/contracts.git",
                "契约代码库",
                "contracts",
                CodeRepositoryType.APPLICATION_CODE_REPOSITORY.value(),
                false,
                NOW,
                NOW);
        ApplicationDefinition application = new ApplicationDefinition(
                new ApplicationId("app_workflow_12345678"),
                "订单应用",
                true,
                NOW,
                NOW);
        MembershipState membership = new MembershipState();

        UserRepository users = mock(UserRepository.class);
        when(users.findByUserId(USER_ID)).thenAnswer(ignored -> Optional.of(userState.value));
        ConfigurationManagementRepository configuration = mock(ConfigurationManagementRepository.class);
        when(configuration.findRepository(repository.repositoryId())).thenReturn(Optional.of(repository));
        when(configuration.findRepository(submoduleRepository.repositoryId()))
                .thenReturn(Optional.of(submoduleRepository));
        when(configuration.findApplicationsByRepository(repository.repositoryId()))
                .thenReturn(List.of(application));
        when(configuration.findApplicationsByRepository(submoduleRepository.repositoryId()))
                .thenReturn(List.of(application));
        when(configuration.isActiveMember(application.appId(), USER_ID))
                .thenAnswer(ignored -> membership.active);
        when(configuration.findApplicationsByMember(USER_ID)).thenReturn(List.of(application));
        when(configuration.findRepositoriesByApplication(application.appId()))
                .thenReturn(List.of(repository, submoduleRepository));

        TokenSessionMarkerStore markers = mock(TokenSessionMarkerStore.class);
        when(markers.isActiveForUser(CALLER.sessionDigest(), USER_ID))
                .thenAnswer(ignored -> membership.sessionActive);
        GitRemoteService git = mock(GitRemoteService.class);
        when(git.listBranches(repository.gitUrl(), null)).thenReturn(List.of("main", "feature/impact"));
        when(git.resolveDefaultBranch(repository.gitUrl(), null)).thenReturn(Optional.of("main"));
        RunnerPublicKeyEncryptionService runnerEncryption = mock(RunnerPublicKeyEncryptionService.class);
        when(runnerEncryption.samePublicKey(RUNNER_PUBLIC_KEY, RUNNER_PUBLIC_KEY))
                .thenReturn(true);

        WorkflowCapabilityProperties properties = new WorkflowCapabilityProperties();
        properties.setRunnerId("runner-a");
        properties.setRunnerPublicKey(RUNNER_PUBLIC_KEY);
        properties.setCheckoutTicketTtl(Duration.ofMinutes(2));
        properties.setModelGrantTtl(Duration.ofMinutes(5));
        properties.setModelGatewayUrl("http://10.20.30.40:8080/api/internal/platform/model-gateway/v1");
        InMemoryStore store = new InMemoryStore();
        WorkflowCapabilityApplicationService service = new WorkflowCapabilityApplicationService(
                users,
                mock(UserRoleRepository.class),
                mock(DictionaryRepository.class),
                configuration,
                mock(SshKeyEncryptionService.class),
                markers,
                store,
                properties,
                runnerEncryption,
                git,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SequenceTokenFactory());
        return new Fixture(service, store, repository, submoduleRepository, membership, userState);
    }

    private static User activeUser(String unifiedAuthId) {
        return new User(
                USER_ID,
                unifiedAuthId,
                "工作流用户",
                "password-hash",
                "总行",
                "研发中心",
                "测试部",
                UserStatus.ACTIVE,
                NOW.minusSeconds(600),
                NOW);
    }

    private record Fixture(
            WorkflowCapabilityApplicationService service,
            InMemoryStore store,
            CodeRepository repository,
            CodeRepository submoduleRepository,
            MembershipState membership,
            UserState user) {
    }

    private static final class UserState {
        private User value;

        private UserState(User value) {
            this.value = value;
        }
    }

    private static final class MembershipState {
        private boolean active = true;
        private boolean sessionActive = true;
    }

    private static final class SequenceTokenFactory implements WorkflowCapabilityTokenFactory {
        private int value;

        @Override
        public String token(String prefix) {
            value++;
            return prefix + "value_" + value + "_1234567890";
        }
    }

    private static final class InMemoryStore implements WorkflowCapabilityStore {
        private final Map<String, CheckoutTicketPayload> checkoutTickets = new HashMap<>();
        private final Map<String, WorkflowModelGrantPayload> grants = new HashMap<>();
        private final Map<String, String> grantDigestsById = new HashMap<>();
        private final Map<String, Set<String>> grantsByRun = new HashMap<>();
        private final Set<String> revokedRuns = new LinkedHashSet<>();

        @Override
        public boolean reserveNonce(String nonceDigest, Duration ttl) {
            return true;
        }

        @Override
        public void saveCheckoutTicket(String ticketDigest, CheckoutTicketPayload payload, Duration ttl) {
            checkoutTickets.put(ticketDigest, payload);
        }

        @Override
        public Optional<CheckoutTicketPayload> consumeCheckoutTicket(String ticketDigest) {
            return Optional.ofNullable(checkoutTickets.remove(ticketDigest));
        }

        @Override
        public boolean saveModelGrant(
                String grantId,
                String grantDigest,
                WorkflowModelGrantPayload payload,
                Duration ttl) {
            if (revokedRuns.contains(runKey(payload.taskId(), payload.runId()))) {
                return false;
            }
            grants.put(grantDigest, payload);
            grantDigestsById.put(grantId, grantDigest);
            grantsByRun.computeIfAbsent(runKey(payload.taskId(), payload.runId()), ignored -> new LinkedHashSet<>())
                    .add(grantId);
            return true;
        }

        @Override
        public Optional<WorkflowModelGrantPayload> findModelGrant(String grantDigest) {
            return Optional.ofNullable(grants.get(grantDigest));
        }

        @Override
        public Optional<WorkflowModelGrantPayload> findModelGrantById(String grantId) {
            return Optional.ofNullable(grantDigestsById.get(grantId)).flatMap(this::findModelGrant);
        }

        @Override
        public boolean refreshModelGrant(
                String grantId,
                WorkflowModelGrantPayload payload,
                Duration ttl) {
            String digest = grantDigestsById.get(grantId);
            if (digest == null) {
                return false;
            }
            grants.put(digest, payload);
            return true;
        }

        @Override
        public List<WorkflowModelGrantPayload> findModelGrants(String taskId, String runId) {
            return grantsByRun.getOrDefault(runKey(taskId, runId), Set.of()).stream()
                    .map(this::findModelGrantById)
                    .flatMap(Optional::stream)
                    .toList();
        }

        @Override
        public void revokeModelGrant(String grantId) {
            String digest = grantDigestsById.remove(grantId);
            if (digest != null) {
                WorkflowModelGrantPayload removed = grants.remove(digest);
                if (removed != null) {
                    Set<String> runGrants = grantsByRun.get(runKey(removed.taskId(), removed.runId()));
                    if (runGrants != null) {
                        runGrants.remove(grantId);
                    }
                }
            }
        }

        @Override
        public void revokeModelGrants(String taskId, String runId, Duration tombstoneTtl) {
            assertThat(tombstoneTtl).isPositive();
            revokedRuns.add(runKey(taskId, runId));
            List.copyOf(grantsByRun.getOrDefault(runKey(taskId, runId), Set.of()))
                    .forEach(this::revokeModelGrant);
            grantsByRun.remove(runKey(taskId, runId));
        }

        private static String runKey(String taskId, String runId) {
            return taskId + ":" + runId;
        }
    }
}
