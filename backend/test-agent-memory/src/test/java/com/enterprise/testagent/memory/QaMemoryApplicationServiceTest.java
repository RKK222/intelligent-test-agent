package com.enterprise.testagent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Asset;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.AssetType;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillCategory;
import com.enterprise.testagent.domain.hub.AgentSkillHubRepository;
import com.enterprise.testagent.domain.memory.MemorySkillProposal;
import com.enterprise.testagent.domain.memory.MemorySkillProposalStatus;
import com.enterprise.testagent.domain.memory.MemoryEvidence;
import com.enterprise.testagent.domain.memory.MemoryId;
import com.enterprise.testagent.domain.memory.MemoryScope;
import com.enterprise.testagent.domain.memory.MemorySource;
import com.enterprise.testagent.domain.memory.MemoryStatus;
import com.enterprise.testagent.domain.memory.QaMemory;
import com.enterprise.testagent.domain.memory.QaMemoryRepository;
import com.enterprise.testagent.domain.memory.QaTaskType;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.transaction.annotation.Transactional;

class QaMemoryApplicationServiceTest {
    private static final UserId USER = new UserId("usr_memory");
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");

    private QaMemoryRepository repository;
    private ConfigurationManagementRepository configuration;
    private AgentSkillHubRepository skillHubRepository;
    private MemoryDocumentStore documents;
    private final Map<String, QaMemory> memories = new LinkedHashMap<>();
    private final Map<String, MemorySkillProposal> proposals = new LinkedHashMap<>();
    private QaMemoryApplicationService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(QaMemoryRepository.class);
        configuration = Mockito.mock(ConfigurationManagementRepository.class);
        skillHubRepository = Mockito.mock(AgentSkillHubRepository.class);
        documents = Mockito.mock(MemoryDocumentStore.class);
        QaMemoryProperties properties = new QaMemoryProperties();
        properties.setEnabled(true);
        when(repository.isWhitelisted(USER.value())).thenReturn(true);
        when(configuration.isActiveMember(any(ApplicationId.class), any(UserId.class))).thenReturn(true);
        when(documents.add(any())).thenReturn(List.of(new MemoryDocumentStore.StoredDocument(
                "mem0_1", "覆盖异常与边界", Map.of(), NOW)));
        when(documents.get("mem0_1")).thenReturn(Optional.of(new MemoryDocumentStore.StoredDocument(
                "mem0_1", "覆盖异常与边界", Map.of(), NOW)));
        Mockito.doAnswer(invocation -> {
            QaMemory memory = invocation.getArgument(0);
            memories.put(memory.memoryId().value(), memory);
            return null;
        }).when(repository).insertMemory(any());
        when(repository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(
                memories.get(invocation.<com.enterprise.testagent.domain.memory.MemoryId>getArgument(0).value())));
        when(repository.findByIdForUpdate(any())).thenAnswer(invocation -> Optional.ofNullable(
                memories.get(invocation.<com.enterprise.testagent.domain.memory.MemoryId>getArgument(0).value())));
        when(repository.updateMemory(any(), anyLong())).thenAnswer(invocation -> {
            QaMemory updated = invocation.getArgument(0);
            long expected = invocation.getArgument(1);
            QaMemory current = memories.get(updated.memoryId().value());
            if (current == null || current.version() != expected) {
                return false;
            }
            memories.put(updated.memoryId().value(), updated);
            return true;
        });
        Mockito.doAnswer(invocation -> {
            MemorySkillProposal proposal = invocation.getArgument(0);
            proposals.put(proposal.proposalId(), proposal);
            return null;
        }).when(repository).insertSkillProposal(any());
        when(repository.findSkillProposal(any())).thenAnswer(invocation -> Optional.ofNullable(
                proposals.get(invocation.<String>getArgument(0))));
        when(repository.updateSkillProposal(any(), any(Long.class))).thenAnswer(invocation -> {
            MemorySkillProposal updated = invocation.getArgument(0);
            long expected = invocation.getArgument(1);
            MemorySkillProposal current = proposals.get(updated.proposalId());
            if (current == null || current.version() != expected) {
                return false;
            }
            proposals.put(updated.proposalId(), updated);
            return true;
        });
        service = new QaMemoryApplicationService(
                repository, configuration, skillHubRepository, documents, new MemorySafetyPolicy(), properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void manualPersonalMemoryActivatesImmediatelyWithoutPersistingFullDocumentInGovernance() {
        var view = service.createPersonal(
                USER, MemoryScope.PERSONAL_GLOBAL, null,
                "覆盖异常与边界");

        assertThat(view.status()).isEqualTo(MemoryStatus.ACTIVE);
        assertThat(view.content()).isEqualTo("覆盖异常与边界");
        ArgumentCaptor<QaMemory> memory = ArgumentCaptor.forClass(QaMemory.class);
        verify(repository).insertMemory(memory.capture());
        assertThat(memory.getValue().mem0MemoryId()).isEqualTo("mem0_1");
        assertThat(memory.getValue().displaySummary()).isEqualTo("覆盖异常与边界");
        assertThat(memory.getValue().ownerUserId()).isEqualTo(USER.value());
    }

    @Test
    void ordinaryTeamContributionStaysCandidateAndCreatesPendingReview() {
        var view = service.createTeamCandidate(
                USER, "app_memory", "分析结论必须附证据");

        assertThat(view.scope()).isEqualTo(MemoryScope.TEAM_APPLICATION);
        assertThat(view.status()).isEqualTo(MemoryStatus.CANDIDATE);
        verify(repository).insertReview(any());
    }

    @Test
    void personalMemoryCanBeManuallyProposedToTeamWithReferenceOnlyEvidence() {
        var personal = service.createPersonal(
                USER, MemoryScope.PERSONAL_GLOBAL, null, "覆盖异常与边界");
        when(repository.listEvidence(new com.enterprise.testagent.domain.memory.MemoryId(personal.memoryId())))
                .thenReturn(List.of(new MemoryEvidence(
                        "mev_source", new com.enterprise.testagent.domain.memory.MemoryId(personal.memoryId()),
                        "run_source", "session_source", "来源对话", USER.value(), USER.value(),
                        MemorySource.NATIVE, "用户明确表达长期偏好", NOW)));

        var team = service.createTeamCandidate(
                USER, "app_memory", personal.content(), personal.memoryId());

        assertThat(team.scope()).isEqualTo(MemoryScope.TEAM_APPLICATION);
        ArgumentCaptor<MemoryEvidence> copied = ArgumentCaptor.forClass(MemoryEvidence.class);
        verify(repository).insertEvidence(copied.capture());
        assertThat(copied.getValue().memoryId().value()).isEqualTo(team.memoryId());
        assertThat(copied.getValue().sessionId()).isEqualTo("session_source");
        assertThat(copied.getValue().summary()).isEqualTo("用户明确表达长期偏好");
    }

    @Test
    void skillProposalProducesEditableDraftOnlyAfterAdminApproval() {
        var memory = service.createPersonal(
                USER, MemoryScope.PERSONAL_GLOBAL, null,
                "覆盖异常与边界");

        var pending = service.createSkillProposal(USER, memory.memoryId(), "app_memory", "异常边界检查");
        assertThat(pending.status()).isEqualTo(MemorySkillProposalStatus.PENDING_REVIEW);
        assertThat(pending.skillMdDraft()).isEmpty();

        var approved = service.reviewSkillProposal(USER, pending.proposalId(), "APPROVE", 0L);
        assertThat(approved.status()).isEqualTo(MemorySkillProposalStatus.DRAFT);
        assertThat(approved.reviewedByUserId()).isEqualTo(USER.value());
        assertThat(approved.skillMdDraft())
                .contains("---\nname: 异常边界检查")
                .contains("## 工作要求")
                .contains("覆盖异常与边界");
    }

    @Test
    void publishedSkillCanOnlyBeLinkedToPublishedSkillAssetFromSameApplication() {
        var memory = service.createPersonal(
                USER, MemoryScope.PERSONAL_GLOBAL, null,
                "覆盖异常与边界");
        var pending = service.createSkillProposal(USER, memory.memoryId(), "app_memory", "异常边界检查");
        var draft = service.reviewSkillProposal(USER, pending.proposalId(), "APPROVE", 0L);
        when(skillHubRepository.findAsset("asset_skill_1")).thenReturn(Optional.of(new Asset(
                "asset_skill_1", "app_memory", "aws_1", AssetType.SKILL, "edge-check",
                SkillCategory.TEST, null, "rev_1", "rev_1", NOW, NOW)));

        var published = service.linkPublishedSkill(
                USER, draft.proposalId(), "asset_skill_1", draft.version());

        assertThat(published.status()).isEqualTo(MemorySkillProposalStatus.PUBLISHED);
        assertThat(published.publishedAssetId()).isEqualTo("asset_skill_1");
    }

    @Test
    void memberExitRevokesTeamMemoryAndProposalReviewAccessImmediately() {
        var team = service.createTeamCandidate(
                USER, "app_memory", "分析结论必须附证据");
        when(configuration.isActiveMember(any(ApplicationId.class), any(UserId.class))).thenReturn(false);

        assertThatThrownBy(() -> service.get(USER, team.memoryId()))
                .isInstanceOf(com.enterprise.testagent.common.error.PlatformException.class)
                .hasMessageContaining("不是该应用的有效成员");
    }

    @Test
    void createFailureUsesAnIndependentCompensationOperationId() {
        Mockito.doThrow(new IllegalStateException("database write failed"))
                .when(repository).insertMemory(any());

        assertThatThrownBy(() -> service.createPersonal(
                USER, MemoryScope.PERSONAL_GLOBAL, null, "覆盖异常与边界"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database write failed");

        ArgumentCaptor<MemoryDocumentStore.AddMemories> add =
                ArgumentCaptor.forClass(MemoryDocumentStore.AddMemories.class);
        ArgumentCaptor<MemoryDocumentStore.RequestContext> compensation =
                ArgumentCaptor.forClass(MemoryDocumentStore.RequestContext.class);
        verify(documents).add(add.capture());
        verify(documents).delete(eq("mem0_1"), compensation.capture());
        assertThat(compensation.getValue().operationId())
                .isNotEqualTo(add.getValue().context().operationId())
                .isEqualTo(add.getValue().context().operationId() + ":compensate-delete");
    }

    @Test
    void teamEditKeepsItsExistingScopeAndUsesTheLockedGovernanceRow() {
        var team = service.createTeamCandidate(USER, "app_memory", "分析结论必须附证据");

        var updated = service.update(USER, team.memoryId(), "分析结论必须附带可复核证据", 0L, true);

        assertThat(updated.content()).isEqualTo("分析结论必须附带可复核证据");
        verify(repository).findByIdForUpdate(new MemoryId(team.memoryId()));
        verify(documents).update(
                eq("mem0_1"), eq("分析结论必须附带可复核证据"), any(),
                isNull(), isNull(), any());
    }

    @Test
    void rejectedTeamReviewPersistsGovernanceAndAuditBeforeDeletingDocument() {
        var team = service.createTeamCandidate(USER, "app_memory", "分析结论必须附证据");
        Mockito.clearInvocations(repository, documents);

        service.reviewTeam(USER, team.memoryId(), "REJECT", "证据不足", 0L);

        InOrder ordered = inOrder(repository, documents);
        ordered.verify(repository).findByIdForUpdate(new MemoryId(team.memoryId()));
        ordered.verify(repository).updateMemory(any(), eq(0L));
        ordered.verify(repository).insertReview(any());
        ordered.verify(documents).delete(eq("mem0_1"), any());
    }

    @Test
    void unavailableDocumentNeverMasqueradesTheSummaryAsEditableContent() {
        var created = service.createPersonal(
                USER, MemoryScope.PERSONAL_GLOBAL, null, "覆盖异常与边界");
        when(documents.get("mem0_1")).thenThrow(
                new PlatformException(ErrorCode.MEMORY_UNAVAILABLE, "memory unavailable"));

        var unavailable = service.get(USER, created.memoryId());

        assertThat(unavailable.contentAvailable()).isFalse();
        assertThat(unavailable.content()).isEmpty();
        assertThat(unavailable.displaySummary()).isEqualTo("覆盖异常与边界");
    }

    @Test
    void explicitApplicationProposalListingRequiresCurrentMembership() {
        when(configuration.isActiveMember(any(ApplicationId.class), any(UserId.class))).thenReturn(false);

        assertThatThrownBy(() -> service.listSkillProposals(USER, "app_memory", 1, 30))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("不是该应用的有效成员");
    }

    @Test
    void multiStepAndCrossStoreMutationsDeclareTransactionBoundaries() throws Exception {
        assertThat(QaMemoryApplicationService.class.getMethod(
                "createTeamCandidate", UserId.class, String.class, String.class, String.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(QaMemoryApplicationService.class.getMethod(
                "update", UserId.class, String.class, String.class, long.class, boolean.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(QaMemoryApplicationService.class.getMethod(
                "pausePersonal", UserId.class, String.class, long.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(QaMemoryApplicationService.class.getMethod(
                "promotePersonalGlobal", UserId.class, String.class, long.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(QaMemoryApplicationService.class.getMethod(
                "archive", UserId.class, String.class, long.class, boolean.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(QaMemoryApplicationService.class.getMethod(
                "reviewTeam", UserId.class, String.class, String.class, String.class, long.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }
}
