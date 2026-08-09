package com.enterprise.testagent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.memory.MemoryLearningEvidence;
import com.enterprise.testagent.domain.memory.MemoryLearningEvidenceRepository;
import com.enterprise.testagent.domain.memory.MemoryLearningJob;
import com.enterprise.testagent.domain.memory.MemoryScope;
import com.enterprise.testagent.domain.memory.MemorySettings;
import com.enterprise.testagent.domain.memory.MemorySource;
import com.enterprise.testagent.domain.memory.MemoryStatus;
import com.enterprise.testagent.domain.memory.QaMemory;
import com.enterprise.testagent.domain.memory.QaMemoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MemoryLearningWorkerTest {
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");

    private QaMemoryRepository repository;
    private MemoryLearningEvidenceRepository evidence;
    private MemoryDocumentStore documents;
    private ExecutorService executor;
    private MemoryLearningWorker worker;

    @BeforeEach
    void setUp() {
        repository = mock(QaMemoryRepository.class);
        evidence = mock(MemoryLearningEvidenceRepository.class);
        documents = mock(MemoryDocumentStore.class);
        QaMemoryProperties properties = new QaMemoryProperties();
        properties.setEnabled(true);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        worker = new MemoryLearningWorker(
                repository, evidence, documents, new MemorySafetyPolicy(), properties,
                Clock.fixed(NOW, ZoneOffset.UTC), "worker_1", executor);
        when(repository.isWhitelisted("usr_1")).thenReturn(true);
        when(repository.loadSettings()).thenReturn(new MemorySettings(
                "fixed-chat", null, "memory-bge-small-zh-v1.5", 0L, null, NOW));
        when(evidence.findByRunId("run_1")).thenReturn(Optional.of(new MemoryLearningEvidence(
                "run_1", List.of(
                        new MemoryLearningEvidence.Message("user", "以后回答都使用中文", NOW),
                        new MemoryLearningEvidence.Message("assistant", "好的", NOW)))));
    }

    @AfterEach
    void tearDown() {
        executor.close();
    }

    @Test
    void nativeInferRunsOnceAndPersistsReturnedLogicalMemory() {
        MemoryLearningJob job = job();
        when(documents.add(any())).thenReturn(List.of(new MemoryDocumentStore.StoredDocument(
                "logical-memory-0001", "用户偏好使用中文回答", Map.of("profile", "cpu"), NOW)));

        worker.processSafely(job);

        ArgumentCaptor<MemoryDocumentStore.AddMemories> command =
                ArgumentCaptor.forClass(MemoryDocumentStore.AddMemories.class);
        verify(documents).add(command.capture());
        assertThat(command.getValue()).satisfies(value -> {
            assertThat(value.infer()).isTrue();
            assertThat(value.chatModelId()).isEqualTo("fixed-chat");
            assertThat(value.owner().scope()).isEqualTo(MemoryScope.PERSONAL_APPLICATION.name());
            assertThat(value.owner().userId()).isEqualTo("platform:usr_1");
            assertThat(value.owner().applicationId()).isEqualTo("app_1");
            assertThat(value.metadata()).containsEntry("source", MemorySource.NATIVE.name());
            assertThat((List<?>) value.messages()).hasSize(2);
        });
        ArgumentCaptor<QaMemory> persisted = ArgumentCaptor.forClass(QaMemory.class);
        verify(repository).insertMemory(persisted.capture());
        assertThat(persisted.getValue()).satisfies(memory -> {
            assertThat(memory.mem0MemoryId()).isEqualTo("logical-memory-0001");
            assertThat(memory.scope()).isEqualTo(MemoryScope.PERSONAL_APPLICATION);
            assertThat(memory.status()).isEqualTo(MemoryStatus.ACTIVE);
            assertThat(memory.source()).isEqualTo(MemorySource.NATIVE);
        });
        verify(repository).insertEvidence(any());
        verify(repository).completeLearningJob("mlj_1", "worker_1", NOW);
        verify(repository, never()).retryLearningJob(any(), any(), any(), any(), any(), eq(8));
    }

    @Test
    void missingFixedChatModelRetriesWithoutCallingMem0() {
        MemoryLearningJob job = job();
        when(repository.loadSettings()).thenReturn(new MemorySettings(
                null, null, "memory-bge-small-zh-v1.5", 0L, null, NOW));

        worker.processSafely(job);

        verify(repository).retryLearningJob(
                eq("mlj_1"), eq("worker_1"), eq("CHAT_MODEL_UNAVAILABLE"),
                eq(NOW.plusSeconds(5)), eq(NOW), eq(8));
        verify(repository, never()).completeLearningJob(any(), any(), any());
        verify(documents, never()).add(any());
    }

    @Test
    void alreadyObservedLogicalMemoryIsNotDuplicated() {
        MemoryLearningJob job = job();
        QaMemory existing = new QaMemory(
                new com.enterprise.testagent.domain.memory.MemoryId("mem_existing"),
                "logical-memory-0001", MemoryScope.PERSONAL_APPLICATION, "usr_1", "app_1",
                MemoryStatus.ACTIVE, MemorySource.NATIVE,
                List.of(com.enterprise.testagent.domain.memory.QaTaskType.GENERAL),
                "用户偏好使用中文回答", 0.0d, 1, 1, NOW, NOW, NOW, null,
                "usr_1", 0L, "SYNCED", NOW, NOW);
        when(documents.add(any())).thenReturn(List.of(new MemoryDocumentStore.StoredDocument(
                "logical-memory-0001", "用户偏好使用中文回答", Map.of(), NOW)));
        when(repository.findByMem0MemoryId("logical-memory-0001")).thenReturn(Optional.of(existing));
        when(repository.listEvidence(existing.memoryId())).thenReturn(List.of(
                new com.enterprise.testagent.domain.memory.MemoryEvidence(
                        "evidence-1", existing.memoryId(), "run_1", "ses_1", "usr_1",
                        MemorySource.NATIVE, "用户偏好使用中文回答", NOW)));

        worker.processSafely(job);

        verify(repository, never()).insertMemory(any());
        verify(repository, never()).insertEvidence(any());
        verify(repository).completeLearningJob("mlj_1", "worker_1", NOW);
    }

    @Test
    void legacyJobWithoutApplicationCompletesWithoutCreatingGlobalMemory() {
        MemoryLearningJob job = new MemoryLearningJob(
                "mlj_legacy", "run_1", "ses_1", "wrk_1", "usr_1", null,
                "opencode", "run-chat", "PROCESSING", 0, NOW, "worker_1",
                NOW.plusSeconds(60), null, NOW, NOW);

        worker.processSafely(job);

        verify(documents, never()).add(any());
        verify(repository, never()).insertMemory(any());
        verify(repository).completeLearningJob("mlj_legacy", "worker_1", NOW);
    }

    private MemoryLearningJob job() {
        return new MemoryLearningJob(
                "mlj_1", "run_1", "ses_1", "wrk_1", "usr_1", "app_1",
                "opencode", "run-chat", "PROCESSING", 0, NOW, "worker_1", NOW.plusSeconds(60),
                null, NOW, NOW);
    }
}
