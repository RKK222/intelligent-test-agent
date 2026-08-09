package com.enterprise.testagent.memory;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.memory.MemoryEvidence;
import com.enterprise.testagent.domain.memory.MemoryId;
import com.enterprise.testagent.domain.memory.MemoryLearningJob;
import com.enterprise.testagent.domain.memory.MemoryReview;
import com.enterprise.testagent.domain.memory.MemoryScope;
import com.enterprise.testagent.domain.memory.MemorySource;
import com.enterprise.testagent.domain.memory.MemoryStatus;
import com.enterprise.testagent.domain.memory.QaMemory;
import com.enterprise.testagent.domain.memory.QaMemoryRepository;
import com.enterprise.testagent.domain.memory.QaTaskType;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 将抽取候选按明确/隐式/冲突规则收敛为个人记忆；原始对话不进入本服务。 */
@Service
public class MemoryLearningCandidateService {
    private final QaMemoryRepository repository;
    private final ConfigurationManagementRepository configuration;
    private final MemoryDocumentStore documents;
    private final MemorySafetyPolicy safety;
    private final QaMemoryProperties properties;
    private final Clock clock;

    @Autowired
    public MemoryLearningCandidateService(
            QaMemoryRepository repository,
            ConfigurationManagementRepository configuration,
            MemoryDocumentStore documents,
            MemorySafetyPolicy safety,
            QaMemoryProperties properties) {
        this(repository, configuration, documents, safety, properties, Clock.systemUTC());
    }

    MemoryLearningCandidateService(
            QaMemoryRepository repository,
            ConfigurationManagementRepository configuration,
            MemoryDocumentStore documents,
            MemorySafetyPolicy safety,
            QaMemoryProperties properties,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.configuration = Objects.requireNonNull(configuration);
        this.documents = Objects.requireNonNull(documents);
        this.safety = Objects.requireNonNull(safety);
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public void apply(MemoryLearningJob job, MemoryDocumentStore.ExtractedCandidate candidate) {
        if (candidate.temporary() || candidate.scopeSuggestion() == null) {
            return;
        }
        String content = safety.requireSafeContent(candidate.content());
        List<QaTaskType> tasks = normalizeTasks(candidate.taskTypes());
        Optional<MemoryScope> resolvedScope = resolveScope(job, candidate.scopeSuggestion());
        if (resolvedScope.isEmpty()) {
            return;
        }
        MemoryScope scope = resolvedScope.orElseThrow();
        Optional<QaMemory> match = findMatch(job, scope, content);
        MemorySource source = candidate.explicit() ? MemorySource.EXPLICIT : MemorySource.IMPLICIT;
        if (match.isPresent() && candidate.replacesExisting()) {
            if (scope == MemoryScope.TEAM_APPLICATION) {
                // 团队新要求必须等待 APP_ADMIN 判断，不能直接替代当前团队记忆。
                create(job, scope, content, tasks, candidate, source, MemoryStatus.CONFLICTED, null);
            } else if (candidate.explicit()) {
                replaceExplicit(job, match.orElseThrow(), scope, content, tasks, candidate, source);
            } else {
                create(job, scope, content, tasks, candidate, source, MemoryStatus.CONFLICTED, null);
            }
            return;
        }
        if (match.isPresent()) {
            reinforce(job, match.orElseThrow(), candidate, source);
            return;
        }
        create(
                job,
                scope,
                content,
                tasks,
                candidate,
                source,
                scope != MemoryScope.TEAM_APPLICATION && candidate.explicit()
                        ? MemoryStatus.ACTIVE : MemoryStatus.CANDIDATE,
                null);
    }

    private Optional<MemoryScope> resolveScope(MemoryLearningJob job, String suggestion) {
        if (MemoryScope.TEAM_APPLICATION.name().equals(suggestion)) {
            return activeApplicationMember(job) ? Optional.of(MemoryScope.TEAM_APPLICATION) : Optional.empty();
        }
        if (MemoryScope.PERSONAL_APPLICATION.name().equals(suggestion)) {
            if (activeApplicationMember(job)) {
                return Optional.of(MemoryScope.PERSONAL_APPLICATION);
            }
            return Optional.empty();
        }
        return Optional.of(MemoryScope.PERSONAL_GLOBAL);
    }

    private boolean activeApplicationMember(MemoryLearningJob job) {
        return job.applicationId() != null
                && configuration.isActiveMember(new ApplicationId(job.applicationId()),
                        new com.enterprise.testagent.domain.user.UserId(job.userId()));
    }

    private Optional<QaMemory> findMatch(MemoryLearningJob job, MemoryScope scope, String content) {
        List<MemoryDocumentStore.StoredDocument> found = documents.search(new MemoryDocumentStore.SearchQuery(
                content,
                scope == MemoryScope.TEAM_APPLICATION ? null : "platform:" + job.userId(),
                scope == MemoryScope.TEAM_APPLICATION ? "qa-team:" + job.applicationId() : null,
                scope == MemoryScope.PERSONAL_GLOBAL ? null : job.applicationId(),
                scope.name(),
                5,
                properties.getCandidateMatchThreshold()));
        return found.stream()
                .filter(document -> document.score() == null
                        || document.score() >= properties.getCandidateMatchThreshold())
                .sorted(Comparator.comparingDouble(
                        (MemoryDocumentStore.StoredDocument document) ->
                                document.score() == null ? 0.0d : document.score()).reversed())
                .map(document -> repository.findByMem0MemoryId(document.id()))
                .flatMap(Optional::stream)
                .filter(memory -> sameOwner(memory, job, scope))
                .filter(memory -> memory.status() != MemoryStatus.ARCHIVED
                        && memory.status() != MemoryStatus.REJECTED
                        && memory.status() != MemoryStatus.SUPERSEDED)
                .findFirst();
    }

    private boolean sameOwner(QaMemory memory, MemoryLearningJob job, MemoryScope scope) {
        if (memory.scope() != scope) {
            return false;
        }
        if (scope == MemoryScope.TEAM_APPLICATION) {
            return memory.ownerUserId() == null && Objects.equals(job.applicationId(), memory.applicationId());
        }
        return job.userId().equals(memory.ownerUserId())
                && Objects.equals(scope == MemoryScope.PERSONAL_APPLICATION ? job.applicationId() : null,
                        memory.applicationId());
    }

    private void reinforce(
            MemoryLearningJob job,
            QaMemory current,
            MemoryDocumentStore.ExtractedCandidate candidate,
            MemorySource source) {
        List<MemoryEvidence> evidence = new ArrayList<>(repository.listEvidence(current.memoryId()));
        if (evidence.stream().anyMatch(item -> item.runId().equals(job.runId()))) {
            return;
        }
        Instant now = clock.instant();
        MemoryEvidence observed = evidence(job, current.memoryId(), candidate, source, now);
        repository.insertEvidence(observed);
        evidence.add(observed);
        Instant cutoff = now.minus(properties.getImplicitWindow());
        int sessions = Math.toIntExact(evidence.stream()
                .filter(item -> !item.observedAt().isBefore(cutoff))
                .map(MemoryEvidence::sessionId).distinct().count());
        int users = Math.toIntExact(evidence.stream()
                .filter(item -> !item.observedAt().isBefore(cutoff))
                .map(MemoryEvidence::observedUserId).distinct().count());
        MemoryStatus nextStatus = current.status();
        Instant confirmedAt = current.confirmedAt();
        if (current.scope() == MemoryScope.TEAM_APPLICATION) {
            if ((current.status() == MemoryStatus.CANDIDATE
                    || current.status() == MemoryStatus.PENDING_CONFIRMATION)
                    && sessions >= properties.getTeamSessionThreshold()
                    && users >= properties.getTeamUserThreshold()) {
                nextStatus = MemoryStatus.PENDING_CONFIRMATION;
            }
        } else if (candidate.explicit()) {
            nextStatus = MemoryStatus.ACTIVE;
            confirmedAt = now;
        } else if (current.status() == MemoryStatus.CANDIDATE
                || current.status() == MemoryStatus.PENDING_CONFIRMATION) {
            if (sessions >= properties.getImplicitSessionThreshold()) {
                nextStatus = MemoryStatus.ACTIVE;
                confirmedAt = now;
            }
        }
        QaMemory updated = new QaMemory(
                current.memoryId(), current.mem0MemoryId(), current.scope(), current.ownerUserId(),
                current.applicationId(), nextStatus, current.source(), current.taskTypes(),
                current.displaySummary(), Math.max(current.confidence(), candidate.confidence()),
                sessions, users, current.firstObservedAt(), now, confirmedAt,
                current.supersededByMemoryId(), current.createdByUserId(), current.version() + 1,
                current.vectorSyncStatus(), current.createdAt(), now);
        if (!repository.updateMemory(updated, current.version())) {
            throw new PlatformException(ErrorCode.CONFLICT, "记忆候选由并发学习任务更新");
        }
    }

    private void replaceExplicit(
            MemoryLearningJob job,
            QaMemory previous,
            MemoryScope scope,
            String content,
            List<QaTaskType> tasks,
            MemoryDocumentStore.ExtractedCandidate candidate,
            MemorySource source) {
        QaMemory replacement = create(
                job, scope, content, tasks, candidate, source, MemoryStatus.ACTIVE, null);
        Instant now = clock.instant();
        QaMemory superseded = new QaMemory(
                previous.memoryId(), previous.mem0MemoryId(), previous.scope(), previous.ownerUserId(),
                previous.applicationId(), MemoryStatus.SUPERSEDED, previous.source(), previous.taskTypes(),
                previous.displaySummary(), previous.confidence(), previous.distinctSessionCount(),
                previous.distinctUserCount(), previous.firstObservedAt(), previous.lastObservedAt(),
                previous.confirmedAt(), replacement.memoryId().value(), previous.createdByUserId(),
                previous.version() + 1, previous.vectorSyncStatus(), previous.createdAt(), now);
        try {
            if (!repository.updateMemory(superseded, previous.version())) {
                throw new PlatformException(ErrorCode.CONFLICT, "旧记忆由并发学习任务更新");
            }
        } catch (RuntimeException failure) {
            // 当前关系型事务会回滚 replacement；同步补偿外部 Mem0 文档，避免并发冲突留下孤儿向量。
            try {
                documents.delete(replacement.mem0MemoryId());
            } catch (RuntimeException ignored) {
                // 对账任务仍可依据没有治理记录的 documentId 清理，不能覆盖主冲突。
            }
            throw failure;
        }
    }

    private QaMemory create(
            MemoryLearningJob job,
            MemoryScope scope,
            String content,
            List<QaTaskType> tasks,
            MemoryDocumentStore.ExtractedCandidate candidate,
            MemorySource source,
            MemoryStatus status,
            String supersededBy) {
        MemoryDocumentStore.StoredDocument document = documents.add(new MemoryDocumentStore.AddDocument(
                content,
                scope == MemoryScope.TEAM_APPLICATION ? null : "platform:" + job.userId(),
                scope == MemoryScope.TEAM_APPLICATION ? "qa-team:" + job.applicationId() : null,
                scope == MemoryScope.PERSONAL_GLOBAL ? null : job.applicationId(),
                tasks,
                Map.of("scope", scope.name(), "source", source.name())));
        Instant now = clock.instant();
        MemoryId memoryId = new MemoryId("mem_" + UUID.randomUUID().toString().replace("-", ""));
        QaMemory memory = new QaMemory(
                memoryId, document.id(), scope,
                scope == MemoryScope.TEAM_APPLICATION ? null : job.userId(),
                scope == MemoryScope.PERSONAL_GLOBAL ? null : job.applicationId(),
                status, source, tasks, safety.displaySummary(content), candidate.confidence(),
                1, 1, now, now, status == MemoryStatus.ACTIVE ? now : null,
                supersededBy, job.userId(), 0L, "SYNCED", now, now);
        try {
            repository.insertMemory(memory);
            repository.insertEvidence(evidence(job, memoryId, candidate, source, now));
            if (scope == MemoryScope.TEAM_APPLICATION) {
                repository.insertReview(new MemoryReview(
                        "mrev_" + UUID.randomUUID().toString().replace("-", ""), memoryId,
                        job.applicationId(), job.userId(), null, "PENDING", null, now, null));
            }
            return memory;
        } catch (RuntimeException failure) {
            try {
                documents.delete(document.id());
            } catch (RuntimeException ignored) {
                // 数据库失败保持主异常；孤立派生文档由后续运维对账处理。
            }
            throw failure;
        }
    }

    private MemoryEvidence evidence(
            MemoryLearningJob job,
            MemoryId memoryId,
            MemoryDocumentStore.ExtractedCandidate candidate,
            MemorySource source,
            Instant now) {
        String summary = (candidate.explicit() ? "明确要求：" : "重复观察：")
                + safety.evidenceSummary(candidate.content());
        return new MemoryEvidence(
                "mev_" + UUID.randomUUID().toString().replace("-", ""), memoryId,
                job.runId(), job.sessionId(), job.userId(), source,
                safety.evidenceSummary(summary), now);
    }

    private List<QaTaskType> normalizeTasks(List<QaTaskType> values) {
        Set<QaTaskType> tasks = values == null ? Set.of() : values.stream()
                .filter(Objects::nonNull).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        return tasks.isEmpty() ? List.of(QaTaskType.GENERAL) : List.copyOf(tasks);
    }
}
