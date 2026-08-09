package com.enterprise.testagent.memory;

import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.memory.MemoryEvidence;
import com.enterprise.testagent.domain.memory.MemoryId;
import com.enterprise.testagent.domain.memory.MemoryLearningEvidence;
import com.enterprise.testagent.domain.memory.MemoryLearningEvidenceRepository;
import com.enterprise.testagent.domain.memory.MemoryLearningJob;
import com.enterprise.testagent.domain.memory.MemoryScope;
import com.enterprise.testagent.domain.memory.MemorySettings;
import com.enterprise.testagent.domain.memory.MemorySource;
import com.enterprise.testagent.domain.memory.MemoryStatus;
import com.enterprise.testagent.domain.memory.QaMemory;
import com.enterprise.testagent.domain.memory.QaMemoryRepository;
import com.enterprise.testagent.domain.memory.QaTaskType;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 异步消费学习 Outbox；一次调用 Mem0 2.0.17 原生 infer，不做 QA 分类或二次提示词抽取。 */
@Component
public class MemoryLearningWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryLearningWorker.class);
    private static final int MAX_LEARNING_CHARS = 120_000;

    private final QaMemoryRepository repository;
    private final MemoryLearningEvidenceRepository evidenceRepository;
    private final MemoryDocumentStore documents;
    private final MemorySafetyPolicy safety;
    private final QaMemoryProperties properties;
    private final Clock clock;
    private final String workerId;
    private final ExecutorService executor;

    @Autowired
    public MemoryLearningWorker(
            QaMemoryRepository repository,
            MemoryLearningEvidenceRepository evidenceRepository,
            MemoryDocumentStore documents,
            MemorySafetyPolicy safety,
            QaMemoryProperties properties) {
        this(repository, evidenceRepository, documents, safety, properties, Clock.systemUTC(),
                "mlw_" + UUID.randomUUID().toString().replace("-", ""),
                Executors.newVirtualThreadPerTaskExecutor());
    }

    MemoryLearningWorker(
            QaMemoryRepository repository,
            MemoryLearningEvidenceRepository evidenceRepository,
            MemoryDocumentStore documents,
            MemorySafetyPolicy safety,
            QaMemoryProperties properties,
            Clock clock,
            String workerId,
            ExecutorService executor) {
        this.repository = Objects.requireNonNull(repository);
        this.evidenceRepository = Objects.requireNonNull(evidenceRepository);
        this.documents = Objects.requireNonNull(documents);
        this.safety = Objects.requireNonNull(safety);
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
        this.workerId = Objects.requireNonNull(workerId);
        this.executor = Objects.requireNonNull(executor);
    }

    @Scheduled(fixedDelayString = "${test-agent.memory.learning-poll-interval:PT5S}")
    public void drain() {
        if (!properties.isEnabled()) {
            return;
        }
        Instant now = clock.instant();
        List<MemoryLearningJob> claimed = repository.claimLearningJobs(
                workerId, now, now.plus(properties.getLearningLease()), properties.getLearningBatchSize());
        claimed.forEach(job -> executor.submit(() -> processSafely(job)));
    }

    void processSafely(MemoryLearningJob job) {
        try {
            process(job);
            repository.completeLearningJob(job.jobId(), workerId, clock.instant());
        } catch (RuntimeException failure) {
            String code = errorCode(failure);
            Instant now = clock.instant();
            repository.retryLearningJob(
                    job.jobId(), workerId, code, now.plus(backoff(job.attempts())), now,
                    properties.getLearningMaxAttempts());
            LOGGER.warn(
                    "Memory learning deferred, jobId={}, runId={}, attempt={}, errorCode={}, exceptionType={}",
                    job.jobId(), job.runId(), job.attempts() + 1, code,
                    failure.getClass().getSimpleName());
        }
    }

    private void process(MemoryLearningJob job) {
        if (!properties.isEnabled() || !repository.isWhitelisted(job.userId())) {
            return;
        }
        // 兼容升级前可能遗留的无 Application 任务：完成但不学习，禁止自动生成个人全局记忆。
        if (job.applicationId() == null || job.applicationId().isBlank()) {
            return;
        }
        MemoryLearningEvidence evidence = evidenceRepository.findByRunId(job.runId())
                .orElseThrow(() -> new LearningFailure("EVIDENCE_NOT_READY"));
        List<MemoryDocumentStore.Message> messages = boundedMessages(evidence.messages());
        if (messages.stream().noneMatch(message -> "user".equals(message.role()))
                || messages.stream().noneMatch(message -> "assistant".equals(message.role()))) {
            throw new LearningFailure("EVIDENCE_NOT_READY");
        }
        MemorySettings settings = repository.loadSettings();
        if (settings.primaryChatModelId() == null || settings.primaryChatModelId().isBlank()) {
            throw new LearningFailure("CHAT_MODEL_UNAVAILABLE");
        }
        MemoryScope scope = MemoryScope.PERSONAL_APPLICATION;
        MemoryDocumentStore.RequestContext context = new MemoryDocumentStore.RequestContext(
                job.userId(), job.runId(), job.sessionId(), "learn:" + job.jobId());
        List<MemoryDocumentStore.StoredDocument> learned = documents.add(new MemoryDocumentStore.AddMemories(
                messages, true, settings.primaryChatModelId(), context,
                new MemoryDocumentStore.OwnerScope(
                        scope.name(), "platform:" + job.userId(), null, job.applicationId()),
                Map.of("source", MemorySource.NATIVE.name())));
        for (MemoryDocumentStore.StoredDocument document : learned) {
            persistNativeMemory(job, scope, document);
        }
    }

    private void persistNativeMemory(
            MemoryLearningJob job,
            MemoryScope scope,
            MemoryDocumentStore.StoredDocument document) {
        String content = safety.requireSafeContent(document.content());
        OptionalMemory existing = existing(document.id(), job.runId());
        if (existing.alreadyObserved()) {
            return;
        }
        Instant now = clock.instant();
        if (existing.memory() != null) {
            repository.insertEvidence(evidence(job, existing.memory().memoryId(), content, now));
            return;
        }
        QaMemory memory = new QaMemory(
                new MemoryId("mem_" + UUID.randomUUID().toString().replace("-", "")),
                document.id(), scope, job.userId(), job.applicationId(), MemoryStatus.ACTIVE,
                MemorySource.NATIVE, List.of(QaTaskType.GENERAL), safety.displaySummary(content),
                0.0d, 1, 1, now, now, now, null, job.userId(), 0L,
                "SYNCED", now, now);
        repository.insertMemory(memory);
        repository.insertEvidence(evidence(job, memory.memoryId(), content, now));
    }

    private OptionalMemory existing(String logicalMemoryId, String runId) {
        QaMemory memory = repository.findByMem0MemoryId(logicalMemoryId).orElse(null);
        if (memory == null) {
            return new OptionalMemory(null, false);
        }
        boolean observed = repository.listEvidence(memory.memoryId()).stream()
                .anyMatch(item -> runId.equals(item.runId()));
        return new OptionalMemory(memory, observed);
    }

    private MemoryEvidence evidence(
            MemoryLearningJob job, MemoryId memoryId, String content, Instant now) {
        return new MemoryEvidence(
                "mev_" + UUID.randomUUID().toString().replace("-", ""),
                memoryId, job.runId(), job.sessionId(), job.userId(), MemorySource.NATIVE,
                safety.evidenceSummary(content), now);
    }

    private List<MemoryDocumentStore.Message> boundedMessages(
            List<MemoryLearningEvidence.Message> source) {
        List<MemoryDocumentStore.Message> result = new ArrayList<>();
        int remaining = MAX_LEARNING_CHARS;
        for (MemoryLearningEvidence.Message message : source) {
            if (!("user".equals(message.role()) || "assistant".equals(message.role()))
                    || message.content() == null || message.content().isBlank() || remaining <= 0) {
                continue;
            }
            String content = message.content();
            if (content.length() > remaining) {
                content = content.substring(0, remaining);
            }
            result.add(new MemoryDocumentStore.Message(message.role(), content));
            remaining -= content.length();
        }
        return List.copyOf(result);
    }

    private Duration backoff(int attempts) {
        long seconds = Math.min(600L, 5L << Math.min(7, Math.max(0, attempts)));
        return Duration.ofSeconds(seconds);
    }

    private String errorCode(RuntimeException failure) {
        if (failure instanceof LearningFailure learning) {
            return learning.code;
        }
        if (failure instanceof PlatformException platform) {
            return platform.errorCode().name();
        }
        return "LEARNING_FAILED";
    }

    @PreDestroy
    void close() {
        executor.close();
    }

    private record OptionalMemory(QaMemory memory, boolean alreadyObserved) {
    }

    private static final class LearningFailure extends RuntimeException {
        private final String code;

        private LearningFailure(String code) {
            super(code);
            this.code = code;
        }
    }
}
