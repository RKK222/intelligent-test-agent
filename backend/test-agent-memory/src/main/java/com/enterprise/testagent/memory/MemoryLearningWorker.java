package com.enterprise.testagent.memory;

import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.memory.MemoryLearningEvidence;
import com.enterprise.testagent.domain.memory.MemoryLearningEvidenceRepository;
import com.enterprise.testagent.domain.memory.MemoryLearningJob;
import com.enterprise.testagent.domain.memory.QaMemoryRepository;
import com.enterprise.testagent.domain.memory.QaTaskType;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 异步消费持久化学习 Outbox；失败只重试学习，不阻塞或改写原 QA Run。 */
@Component
public class MemoryLearningWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryLearningWorker.class);
    private static final int MAX_EXTRACTION_CHARS = 100_000;

    private final QaMemoryRepository repository;
    private final MemoryLearningEvidenceRepository evidenceRepository;
    private final MemoryDocumentStore documents;
    private final MemoryModelGrantService modelGrants;
    private final MemoryLearningCandidateService candidates;
    private final QaTaskClassifier taskClassifier;
    private final QaMemoryProperties properties;
    private final Clock clock;
    private final String workerId;
    private final ExecutorService executor;

    @Autowired
    public MemoryLearningWorker(
            QaMemoryRepository repository,
            MemoryLearningEvidenceRepository evidenceRepository,
            MemoryDocumentStore documents,
            MemoryModelGrantService modelGrants,
            MemoryLearningCandidateService candidates,
            QaTaskClassifier taskClassifier,
            QaMemoryProperties properties) {
        this(repository, evidenceRepository, documents, modelGrants, candidates,
                taskClassifier, properties, Clock.systemUTC(),
                "mlw_" + UUID.randomUUID().toString().replace("-", ""),
                Executors.newVirtualThreadPerTaskExecutor());
    }

    MemoryLearningWorker(
            QaMemoryRepository repository,
            MemoryLearningEvidenceRepository evidenceRepository,
            MemoryDocumentStore documents,
            MemoryModelGrantService modelGrants,
            MemoryLearningCandidateService candidates,
            QaTaskClassifier taskClassifier,
            QaMemoryProperties properties,
            Clock clock,
            String workerId,
            ExecutorService executor) {
        this.repository = Objects.requireNonNull(repository);
        this.evidenceRepository = Objects.requireNonNull(evidenceRepository);
        this.documents = Objects.requireNonNull(documents);
        this.modelGrants = Objects.requireNonNull(modelGrants);
        this.candidates = Objects.requireNonNull(candidates);
        this.taskClassifier = Objects.requireNonNull(taskClassifier);
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
                    "QA memory learning deferred, jobId={}, runId={}, attempt={}, errorCode={}, exceptionType={}",
                    job.jobId(), job.runId(), job.attempts() + 1, code,
                    failure.getClass().getSimpleName());
        }
    }

    private void process(MemoryLearningJob job) {
        if (!properties.isEnabled() || !repository.isWhitelisted(job.userId())) {
            return;
        }
        MemoryLearningEvidence evidence = evidenceRepository.findByRunId(job.runId())
                .orElseThrow(() -> new LearningFailure("EVIDENCE_NOT_READY"));
        List<MemoryDocumentStore.ExtractionMessage> messages = boundedMessages(evidence.messages());
        if (messages.stream().noneMatch(message -> "user".equals(message.role()))
                || messages.stream().noneMatch(message -> "assistant".equals(message.role()))) {
            throw new LearningFailure("EVIDENCE_NOT_READY");
        }
        String prompt = messages.stream()
                .filter(message -> "user".equals(message.role()))
                .map(MemoryDocumentStore.ExtractionMessage::content)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        QaTaskType taskType = taskClassifier.classify(prompt);
        String model = modelGrants.selectModel(job)
                .orElseThrow(() -> new LearningFailure("CHAT_MODEL_UNAVAILABLE"));
        MemoryModelGrantService.IssuedGrant grant = modelGrants.issue(job.userId(), job.runId(), model);
        List<MemoryDocumentStore.ExtractedCandidate> extracted = documents.extract(
                new MemoryDocumentStore.ExtractCommand(
                        model, grant.token(), job.userId(), job.runId(), job.sessionId(),
                        job.applicationId(), taskType, messages));
        for (MemoryDocumentStore.ExtractedCandidate candidate : extracted) {
            candidates.apply(job, candidate);
        }
    }

    private List<MemoryDocumentStore.ExtractionMessage> boundedMessages(
            List<MemoryLearningEvidence.Message> source) {
        List<MemoryDocumentStore.ExtractionMessage> result = new ArrayList<>();
        int remaining = MAX_EXTRACTION_CHARS;
        for (MemoryLearningEvidence.Message message : source) {
            if (!("user".equals(message.role()) || "assistant".equals(message.role()))
                    || message.content() == null || message.content().isBlank() || remaining <= 0) {
                continue;
            }
            String content = message.content().trim();
            if (content.length() > remaining) {
                content = content.substring(0, remaining);
            }
            result.add(new MemoryDocumentStore.ExtractionMessage(message.role(), content));
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

    private static final class LearningFailure extends RuntimeException {
        private final String code;

        private LearningFailure(String code) {
            super(code);
            this.code = code;
        }
    }
}
