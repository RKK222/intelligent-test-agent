package com.enterprise.testagent.memory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.memory.MemoryLearningEvidence;
import com.enterprise.testagent.domain.memory.MemoryLearningEvidenceRepository;
import com.enterprise.testagent.domain.memory.MemoryLearningJob;
import com.enterprise.testagent.domain.memory.QaMemoryRepository;
import com.enterprise.testagent.domain.memory.QaTaskType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemoryLearningWorkerTest {
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");

    private QaMemoryRepository repository;
    private MemoryLearningEvidenceRepository evidence;
    private MemoryDocumentStore documents;
    private MemoryModelGrantService grants;
    private MemoryLearningCandidateService candidates;
    private ExecutorService executor;
    private MemoryLearningWorker worker;

    @BeforeEach
    void setUp() {
        repository = mock(QaMemoryRepository.class);
        evidence = mock(MemoryLearningEvidenceRepository.class);
        documents = mock(MemoryDocumentStore.class);
        grants = mock(MemoryModelGrantService.class);
        candidates = mock(MemoryLearningCandidateService.class);
        QaMemoryProperties properties = new QaMemoryProperties();
        properties.setEnabled(true);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        worker = new MemoryLearningWorker(
                repository, evidence, documents, grants, candidates, new QaTaskClassifier(), properties,
                Clock.fixed(NOW, ZoneOffset.UTC), "worker_1", executor);
        when(repository.isWhitelisted("usr_1")).thenReturn(true);
        when(evidence.findByRunId("run_1")).thenReturn(Optional.of(new MemoryLearningEvidence(
                "run_1", List.of(
                        new MemoryLearningEvidence.Message("user", "生成测试案例时请覆盖边界条件", NOW),
                        new MemoryLearningEvidence.Message("assistant", "已补充边界场景", NOW)))));
    }

    @AfterEach
    void tearDown() {
        executor.close();
    }

    @Test
    void successfulExtractionCompletesOutboxWithoutCopyingMessagesToGovernanceRepository() {
        MemoryLearningJob job = job();
        MemoryDocumentStore.ExtractedCandidate extracted = new MemoryDocumentStore.ExtractedCandidate(
                "测试案例覆盖边界条件", "PERSONAL_GLOBAL", List.of(QaTaskType.TEST_CASE_GENERATION),
                true, false, false, 0.98d, "用户明确要求");
        when(grants.selectModel(job)).thenReturn(Optional.of("fixed-chat"));
        when(grants.issue("usr_1", "run_1", "fixed-chat"))
                .thenReturn(new MemoryModelGrantService.IssuedGrant("mfg_testtoken_12345678901234567890", "fixed-chat", NOW.plusSeconds(60)));
        when(documents.extract(any())).thenReturn(List.of(extracted));

        worker.processSafely(job);

        verify(documents).extract(org.mockito.ArgumentMatchers.argThat(command ->
                command.userId().equals("usr_1") && command.runId().equals("run_1")
                        && command.messages().size() == 2));
        verify(candidates).apply(job, extracted);
        verify(repository).completeLearningJob("mlj_1", "worker_1", NOW);
        verify(repository, never()).retryLearningJob(any(), any(), any(), any(), any(), eq(8));
    }

    @Test
    void unavailableChatModelOnlyRetriesLearningJob() {
        MemoryLearningJob job = job();
        when(grants.selectModel(job)).thenReturn(Optional.empty());

        worker.processSafely(job);

        verify(repository).retryLearningJob(
                eq("mlj_1"), eq("worker_1"), eq("CHAT_MODEL_UNAVAILABLE"),
                eq(NOW.plusSeconds(5)), eq(NOW), eq(8));
        verify(repository, never()).completeLearningJob(any(), any(), any());
        verify(documents, never()).extract(any());
    }

    private MemoryLearningJob job() {
        return new MemoryLearningJob(
                "mlj_1", "run_1", "ses_1", "wrk_1", "usr_1", "app_1",
                "opencode", "run-chat", "PROCESSING", 0, NOW, "worker_1", NOW.plusSeconds(60),
                null, NOW, NOW);
    }
}
