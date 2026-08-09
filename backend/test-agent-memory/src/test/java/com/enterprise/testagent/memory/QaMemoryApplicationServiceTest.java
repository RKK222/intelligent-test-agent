package com.enterprise.testagent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Asset;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.AssetType;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillCategory;
import com.enterprise.testagent.domain.hub.AgentSkillHubRepository;
import com.enterprise.testagent.domain.memory.MemorySkillProposal;
import com.enterprise.testagent.domain.memory.MemorySkillProposalStatus;
import com.enterprise.testagent.domain.memory.MemoryScope;
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
import org.mockito.Mockito;

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
        when(documents.add(any())).thenReturn(new MemoryDocumentStore.StoredDocument(
                "mem0_1", "覆盖异常与边界", Map.of(), NOW));
        when(documents.get("mem0_1")).thenReturn(Optional.of(new MemoryDocumentStore.StoredDocument(
                "mem0_1", "覆盖异常与边界", Map.of(), NOW)));
        Mockito.doAnswer(invocation -> {
            QaMemory memory = invocation.getArgument(0);
            memories.put(memory.memoryId().value(), memory);
            return null;
        }).when(repository).insertMemory(any());
        when(repository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(
                memories.get(invocation.<com.enterprise.testagent.domain.memory.MemoryId>getArgument(0).value())));
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
                "覆盖异常与边界", List.of(QaTaskType.TEST_CASE_GENERATION));

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
                USER, "app_memory", "分析结论必须附证据", List.of(QaTaskType.DEFECT_ANALYSIS));

        assertThat(view.scope()).isEqualTo(MemoryScope.TEAM_APPLICATION);
        assertThat(view.status()).isEqualTo(MemoryStatus.CANDIDATE);
        verify(repository).insertReview(any());
    }

    @Test
    void skillProposalProducesEditableDraftOnlyAfterAdminApproval() {
        var memory = service.createPersonal(
                USER, MemoryScope.PERSONAL_GLOBAL, null,
                "覆盖异常与边界", List.of(QaTaskType.TEST_CASE_GENERATION));

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
                "覆盖异常与边界", List.of(QaTaskType.TEST_CASE_GENERATION));
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
                USER, "app_memory", "分析结论必须附证据", List.of(QaTaskType.DEFECT_ANALYSIS));
        when(configuration.isActiveMember(any(ApplicationId.class), any(UserId.class))).thenReturn(false);

        assertThatThrownBy(() -> service.get(USER, team.memoryId()))
                .isInstanceOf(com.enterprise.testagent.common.error.PlatformException.class)
                .hasMessageContaining("不是该应用的有效成员");
    }
}
