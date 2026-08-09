package com.enterprise.testagent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.memory.MemoryEvidence;
import com.enterprise.testagent.domain.memory.MemoryLearningJob;
import com.enterprise.testagent.domain.memory.MemoryReview;
import com.enterprise.testagent.domain.memory.MemoryStatus;
import com.enterprise.testagent.domain.memory.QaMemory;
import com.enterprise.testagent.domain.memory.QaMemoryRepository;
import com.enterprise.testagent.domain.memory.QaTaskType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemoryLearningCandidateServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");

    private final Map<String, QaMemory> memories = new LinkedHashMap<>();
    private final Map<String, List<MemoryEvidence>> evidence = new LinkedHashMap<>();
    private final Map<String, MemoryDocumentStore.StoredDocument> stored = new LinkedHashMap<>();
    private final AtomicInteger documentSequence = new AtomicInteger();
    private QaMemoryRepository repository;
    private ConfigurationManagementRepository configuration;
    private MemoryLearningCandidateService service;

    @BeforeEach
    void setUp() {
        repository = mock(QaMemoryRepository.class);
        configuration = mock(ConfigurationManagementRepository.class);
        MemoryDocumentStore documents = mock(MemoryDocumentStore.class);
        QaMemoryProperties properties = new QaMemoryProperties();
        properties.setImplicitSessionThreshold(3);
        properties.setCandidateMatchThreshold(0.8d);
        when(configuration.isActiveMember(any(), any())).thenReturn(true);
        doAnswer(invocation -> {
            QaMemory memory = invocation.getArgument(0);
            memories.put(memory.memoryId().value(), memory);
            return null;
        }).when(repository).insertMemory(any());
        when(repository.findByMem0MemoryId(any())).thenAnswer(invocation -> {
            String mem0Id = invocation.getArgument(0);
            return memories.values().stream().filter(memory -> mem0Id.equals(memory.mem0MemoryId())).findFirst();
        });
        when(repository.listEvidence(any())).thenAnswer(invocation -> List.copyOf(
                evidence.getOrDefault(invocation.<com.enterprise.testagent.domain.memory.MemoryId>getArgument(0).value(), List.of())));
        doAnswer(invocation -> {
            MemoryEvidence item = invocation.getArgument(0);
            evidence.computeIfAbsent(item.memoryId().value(), ignored -> new ArrayList<>()).add(item);
            return null;
        }).when(repository).insertEvidence(any());
        when(repository.updateMemory(any(), any(Long.class))).thenAnswer(invocation -> {
            QaMemory updated = invocation.getArgument(0);
            long expected = invocation.getArgument(1);
            QaMemory current = memories.get(updated.memoryId().value());
            if (current == null || current.version() != expected) {
                return false;
            }
            memories.put(updated.memoryId().value(), updated);
            return true;
        });
        when(documents.add(any())).thenAnswer(invocation -> {
            MemoryDocumentStore.AddDocument command = invocation.getArgument(0);
            String id = "00000000-0000-0000-0000-" + String.format("%012d", documentSequence.incrementAndGet());
            MemoryDocumentStore.StoredDocument document = new MemoryDocumentStore.StoredDocument(
                    id, command.content(), command.metadata(), NOW);
            stored.put(id, document);
            return document;
        });
        when(documents.search(any())).thenAnswer(invocation -> stored.values().stream()
                .map(document -> new MemoryDocumentStore.StoredDocument(
                        document.id(), document.content(), document.metadata(), document.updatedAt(), 0.95d))
                .toList());
        doAnswer(invocation -> {
            stored.remove(invocation.<String>getArgument(0));
            return null;
        }).when(documents).delete(any());
        service = new MemoryLearningCandidateService(
                repository, configuration, documents, new MemorySafetyPolicy(), properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void explicitPreferenceActivatesOnFirstDistinctSession() {
        service.apply(job(1), candidate(true, false, false));

        assertThat(memories.values()).singleElement().satisfies(memory -> {
            assertThat(memory.status()).isEqualTo(MemoryStatus.ACTIVE);
            assertThat(memory.distinctSessionCount()).isEqualTo(1);
            assertThat(memory.confirmedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void implicitPreferenceActivatesOnlyAfterThreeDifferentSessions() {
        service.apply(job(1), candidate(false, false, false));
        assertThat(memories.values()).singleElement()
                .extracting(QaMemory::status).isEqualTo(MemoryStatus.CANDIDATE);

        service.apply(job(2), candidate(false, false, false));
        assertThat(memories.values()).singleElement()
                .extracting(QaMemory::status).isEqualTo(MemoryStatus.CANDIDATE);

        service.apply(job(3), candidate(false, false, false));
        assertThat(memories.values()).singleElement().satisfies(memory -> {
            assertThat(memory.status()).isEqualTo(MemoryStatus.ACTIVE);
            assertThat(memory.distinctSessionCount()).isEqualTo(3);
        });
    }

    @Test
    void temporaryCandidateIsNeverPersisted() {
        service.apply(job(1), candidate(true, true, false));

        assertThat(memories).isEmpty();
        assertThat(stored).isEmpty();
    }

    @Test
    void implicitConflictWaitsForConfirmationWhileExplicitReplacementSupersedesOldVersion() {
        service.apply(job(1), candidate(true, false, false));
        QaMemory original = memories.values().iterator().next();

        service.apply(job(2), candidate(false, false, true));
        assertThat(memories.values()).extracting(QaMemory::status)
                .containsExactlyInAnyOrder(MemoryStatus.ACTIVE, MemoryStatus.CONFLICTED);

        service.apply(job(3), candidate(true, false, true));
        assertThat(memories.get(original.memoryId().value()).status()).isEqualTo(MemoryStatus.SUPERSEDED);
        assertThat(memories.values().stream().filter(memory -> memory.status() == MemoryStatus.ACTIVE))
                .hasSize(1);
    }

    @Test
    void implicitTeamCandidateNeedsTwoActiveMembersAndThreeSessionsThenStillWaitsForAdmin() {
        service.apply(teamJob(1, "usr_1"), teamCandidate());
        service.apply(teamJob(2, "usr_1"), teamCandidate());
        assertThat(memories.values()).singleElement().satisfies(memory -> {
            assertThat(memory.status()).isEqualTo(MemoryStatus.CANDIDATE);
            assertThat(memory.distinctUserCount()).isEqualTo(1);
        });

        service.apply(teamJob(3, "usr_2"), teamCandidate());

        assertThat(memories.values()).singleElement().satisfies(memory -> {
            assertThat(memory.status()).isEqualTo(MemoryStatus.PENDING_CONFIRMATION);
            assertThat(memory.distinctSessionCount()).isEqualTo(3);
            assertThat(memory.distinctUserCount()).isEqualTo(2);
            assertThat(memory.confirmedAt()).isNull();
            assertThat(memory.ownerUserId()).isNull();
            assertThat(memory.applicationId()).isEqualTo("app_1");
        });
        verify(repository).insertReview(any(MemoryReview.class));
    }

    @Test
    void teamCandidateFromFormerMemberIsIgnored() {
        when(configuration.isActiveMember(any(), any())).thenReturn(false);

        service.apply(teamJob(1, "usr_left"), teamCandidate());

        assertThat(memories).isEmpty();
        assertThat(stored).isEmpty();
    }

    private MemoryLearningJob job(int index) {
        return new MemoryLearningJob(
                "mlj_" + index, "run_" + index, "ses_" + index, "wrk_1", "usr_1", "app_1",
                "opencode", "chat-model", "PROCESSING", 0, NOW, "worker", NOW.plusSeconds(60),
                null, NOW, NOW);
    }

    private MemoryLearningJob teamJob(int index, String userId) {
        return new MemoryLearningJob(
                "mlj_team_" + index, "run_team_" + index, "ses_team_" + index,
                "wrk_1", userId, "app_1", "opencode", "chat-model", "PROCESSING", 0,
                NOW, "worker", NOW.plusSeconds(60), null, NOW, NOW);
    }

    private MemoryDocumentStore.ExtractedCandidate candidate(
            boolean explicit, boolean temporary, boolean replacesExisting) {
        return new MemoryDocumentStore.ExtractedCandidate(
                "生成测试案例时必须覆盖异常场景和边界条件",
                "PERSONAL_GLOBAL",
                List.of(QaTaskType.TEST_CASE_GENERATION),
                explicit,
                temporary,
                replacesExisting,
                0.95d,
                "用户反复提出同一测试要求");
    }

    private MemoryDocumentStore.ExtractedCandidate teamCandidate() {
        return new MemoryDocumentStore.ExtractedCandidate(
                "缺陷结论必须附带可追溯证据",
                "TEAM_APPLICATION",
                List.of(QaTaskType.DEFECT_ANALYSIS),
                false, false, false, 0.92d, "多个成员采用同一验收要求");
    }
}
