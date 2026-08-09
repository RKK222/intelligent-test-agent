package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.memory.MemoryEvidence;
import com.enterprise.testagent.domain.memory.MemoryId;
import com.enterprise.testagent.domain.memory.MemoryLearningJob;
import com.enterprise.testagent.domain.memory.MemoryReview;
import com.enterprise.testagent.domain.memory.MemoryScope;
import com.enterprise.testagent.domain.memory.MemorySettings;
import com.enterprise.testagent.domain.memory.MemorySkillProposal;
import com.enterprise.testagent.domain.memory.MemorySource;
import com.enterprise.testagent.domain.memory.MemoryStatus;
import com.enterprise.testagent.domain.memory.MemoryUsage;
import com.enterprise.testagent.domain.memory.MemoryWhitelistEntry;
import com.enterprise.testagent.domain.memory.QaMemory;
import com.enterprise.testagent.domain.memory.QaMemoryRepository;
import com.enterprise.testagent.domain.memory.QaTaskType;
import com.enterprise.testagent.persistence.mybatis.QaMemoryRows.EvidenceRow;
import com.enterprise.testagent.persistence.mybatis.QaMemoryRows.LearningJobRow;
import com.enterprise.testagent.persistence.mybatis.QaMemoryRows.MemoryRow;
import com.enterprise.testagent.persistence.mybatis.QaMemoryRows.ReviewRow;
import com.enterprise.testagent.persistence.mybatis.QaMemoryRows.SettingsRow;
import com.enterprise.testagent.persistence.mybatis.QaMemoryRows.SkillProposalRow;
import com.enterprise.testagent.persistence.mybatis.QaMemoryRows.UsageRow;
import com.enterprise.testagent.persistence.mybatis.QaMemoryRows.WhitelistRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** QA 长期记忆领域端口的 MyBatis XML 实现。 */
@Repository
public class MyBatisQaMemoryRepository implements QaMemoryRepository {

    private final QaMemoryMapper mapper;

    public MyBatisQaMemoryRepository(QaMemoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<QaMemory> findById(MemoryId memoryId) {
        return Optional.ofNullable(mapper.findMemory(memoryId.value())).map(this::toDomain);
    }

    @Override
    public Optional<QaMemory> findByMem0MemoryId(String mem0MemoryId) {
        if (mem0MemoryId == null || mem0MemoryId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.findMemoryByMem0Id(mem0MemoryId.trim())).map(this::toDomain);
    }

    @Override
    public List<QaMemory> listPersonal(
            String userId, String applicationId, MemoryStatus status, int offset, int limit) {
        return mapper.listPersonal(userId, applicationId, enumName(status), offset, limit)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public long countPersonal(String userId, String applicationId, MemoryStatus status) {
        return mapper.countPersonal(userId, applicationId, enumName(status));
    }

    @Override
    public List<QaMemory> listTeam(
            String userId, String applicationId, MemoryStatus status, int offset, int limit) {
        return mapper.listTeam(userId, applicationId, enumName(status), offset, limit)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public long countTeam(String userId, String applicationId, MemoryStatus status) {
        return mapper.countTeam(userId, applicationId, enumName(status));
    }

    @Override
    public void insertMemory(QaMemory memory) {
        mapper.insertMemory(toRow(memory));
    }

    @Override
    public boolean updateMemory(QaMemory memory, long expectedVersion) {
        return mapper.updateMemory(toRow(memory), expectedVersion) == 1;
    }

    @Override
    public void insertEvidence(MemoryEvidence evidence) {
        mapper.insertEvidence(new EvidenceRow(
                evidence.evidenceId(), evidence.memoryId().value(), evidence.runId(), evidence.sessionId(),
                evidence.observedUserId(), evidence.source().name(), evidence.summary(), evidence.observedAt()));
    }

    @Override
    public List<MemoryEvidence> listEvidence(MemoryId memoryId) {
        return mapper.listEvidence(memoryId.value()).stream().map(row -> new MemoryEvidence(
                row.evidenceId(), new MemoryId(row.memoryId()), row.runId(), row.sessionId(),
                row.observedUserId(), MemorySource.valueOf(row.source()), row.evidenceSummary(), row.observedAt()))
                .toList();
    }

    @Override
    public void insertReview(MemoryReview review) {
        mapper.insertReview(new ReviewRow(
                review.reviewId(), review.memoryId().value(), review.applicationId(), review.submittedByUserId(),
                review.reviewedByUserId(), review.decision(), review.comment(),
                review.submittedAt(), review.reviewedAt()));
    }

    @Override
    public List<MemoryReview> listReviews(MemoryId memoryId) {
        return mapper.listReviews(memoryId.value()).stream().map(row -> new MemoryReview(
                row.reviewId(), new MemoryId(row.memoryId()), row.applicationId(), row.submittedByUserId(),
                row.reviewedByUserId(), row.decision(), row.comment(), row.submittedAt(), row.reviewedAt()))
                .toList();
    }

    @Override
    public Optional<String> findApplicationIdByRuntimeWorkspace(String workspaceId) {
        return Optional.ofNullable(mapper.findApplicationIdByRuntimeWorkspace(workspaceId));
    }

    @Override
    public boolean enqueueLearningJob(MemoryLearningJob job) {
        return mapper.insertLearningJob(toRow(job)) == 1;
    }

    @Override
    @Transactional
    public List<MemoryLearningJob> claimLearningJobs(
            String workerId, Instant now, Instant leaseUntil, int limit) {
        List<MemoryLearningJob> claimed = new ArrayList<>();
        for (LearningJobRow candidate : mapper.listClaimableLearningJobs(now, Math.max(1, limit))) {
            if (mapper.claimLearningJob(candidate.jobId(), workerId, now, leaseUntil) == 1) {
                claimed.add(toDomain(mapper.findLearningJob(candidate.jobId())));
            }
        }
        return List.copyOf(claimed);
    }

    @Override
    public boolean completeLearningJob(String jobId, String workerId, Instant now) {
        return mapper.completeLearningJob(jobId, workerId, now) == 1;
    }

    @Override
    public boolean retryLearningJob(
            String jobId, String workerId, String errorCode, Instant availableAt, Instant now, int maxAttempts) {
        return mapper.retryLearningJob(jobId, workerId, errorCode, availableAt, now, maxAttempts) == 1;
    }

    @Override
    public long countLearningJobs(String status) {
        return mapper.countLearningJobs(status);
    }

    @Override
    public void insertUsage(MemoryUsage usage) {
        mapper.insertUsage(new UsageRow(
                usage.runId(), usage.memoryId().value(), usage.userId(), usage.applicationId(),
                usage.scope().name(), usage.rank(), usage.tokenCount(), usage.injectedAt()));
    }

    @Override
    @Transactional
    public void insertUsages(List<MemoryUsage> usages) {
        usages.forEach(this::insertUsage);
    }

    @Override
    public List<MemoryUsage> listUsage(String userId, List<String> runIds) {
        return mapper.listUsage(userId, runIds).stream().map(row -> new MemoryUsage(
                row.runId(), new MemoryId(row.memoryId()), row.userId(), row.applicationId(),
                MemoryScope.valueOf(row.scope()), row.rankIndex(), row.tokenCount(), row.injectedAt())).toList();
    }

    @Override
    public Optional<MemorySkillProposal> findSkillProposal(String proposalId) {
        return Optional.ofNullable(mapper.findSkillProposal(proposalId)).map(this::toDomain);
    }

    @Override
    public List<MemorySkillProposal> listSkillProposals(
            String userId, String applicationId, int offset, int limit) {
        return mapper.listSkillProposals(userId, applicationId, offset, limit)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public long countSkillProposals(String userId, String applicationId) {
        return mapper.countSkillProposals(userId, applicationId);
    }

    @Override
    public void insertSkillProposal(MemorySkillProposal proposal) {
        mapper.insertSkillProposal(toRow(proposal));
    }

    @Override
    public boolean updateSkillProposal(MemorySkillProposal proposal, long expectedVersion) {
        return mapper.updateSkillProposal(toRow(proposal), expectedVersion) == 1;
    }

    @Override
    public boolean isWhitelisted(String userId) {
        return mapper.isWhitelisted(userId);
    }

    @Override
    public List<MemoryWhitelistEntry> listWhitelist(int offset, int limit) {
        return mapper.listWhitelist(offset, limit).stream().map(row -> new MemoryWhitelistEntry(
                row.userId(), row.enabled(), row.updatedByUserId(), row.createdAt(), row.updatedAt())).toList();
    }

    @Override
    public long countWhitelist() {
        return mapper.countWhitelist();
    }

    @Override
    public void upsertWhitelist(MemoryWhitelistEntry entry) {
        mapper.upsertWhitelist(new WhitelistRow(
                entry.userId(), entry.enabled(), entry.updatedByUserId(), entry.createdAt(), entry.updatedAt()));
    }

    @Override
    public void removeWhitelist(String userId) {
        mapper.removeWhitelist(userId);
    }

    @Override
    public MemorySettings loadSettings() {
        SettingsRow row = mapper.loadSettings();
        return new MemorySettings(
                row.primaryChatModelId(), row.currentRunModelFallbackEnabled(), row.version(),
                row.updatedByUserId(), row.updatedAt());
    }

    @Override
    public boolean updateSettings(MemorySettings settings, long expectedVersion) {
        return mapper.updateSettings(new SettingsRow(
                settings.primaryChatModelId(), settings.currentRunModelFallbackEnabled(), settings.version(),
                settings.updatedByUserId(), settings.updatedAt()), expectedVersion) == 1;
    }

    private QaMemory toDomain(MemoryRow row) {
        return new QaMemory(
                new MemoryId(row.memoryId()), row.mem0MemoryId(), MemoryScope.valueOf(row.scope()),
                row.ownerUserId(), row.applicationId(), MemoryStatus.valueOf(row.status()),
                MemorySource.valueOf(row.source()), parseTaskTypes(row.taskTypesCsv()), row.displaySummary(),
                row.confidence().doubleValue(), row.distinctSessionCount(), row.distinctUserCount(),
                row.firstObservedAt(), row.lastObservedAt(), row.confirmedAt(), row.supersededByMemoryId(),
                row.createdByUserId(), row.version(), row.vectorSyncStatus(), row.createdAt(), row.updatedAt());
    }

    private MemoryRow toRow(QaMemory memory) {
        return new MemoryRow(
                memory.memoryId().value(), memory.mem0MemoryId(), memory.scope().name(), memory.ownerUserId(),
                memory.applicationId(), memory.status().name(), memory.source().name(),
                memory.taskTypes().stream().map(Enum::name).distinct().reduce((a, b) -> a + "," + b).orElse("GENERAL"),
                memory.displaySummary(), BigDecimal.valueOf(memory.confidence()), memory.distinctSessionCount(),
                memory.distinctUserCount(), memory.firstObservedAt(), memory.lastObservedAt(), memory.confirmedAt(),
                memory.supersededByMemoryId(), memory.createdByUserId(), memory.version(), memory.vectorSyncStatus(),
                memory.createdAt(), memory.updatedAt());
    }

    private MemoryLearningJob toDomain(LearningJobRow row) {
        return new MemoryLearningJob(
                row.jobId(), row.runId(), row.sessionId(), row.workspaceId(), row.userId(), row.applicationId(),
                row.agentId(), row.selectedModelId(), row.status(), row.attempts(), row.availableAt(),
                row.claimedBy(), row.leaseUntil(), row.lastErrorCode(), row.createdAt(), row.updatedAt());
    }

    private LearningJobRow toRow(MemoryLearningJob job) {
        return new LearningJobRow(
                job.jobId(), job.runId(), job.sessionId(), job.workspaceId(), job.userId(), job.applicationId(),
                job.agentId(), job.selectedModelId(), job.status(), job.attempts(), job.availableAt(),
                job.claimedBy(), job.leaseUntil(), job.lastErrorCode(), job.createdAt(), job.updatedAt());
    }

    private MemorySkillProposal toDomain(SkillProposalRow row) {
        return new MemorySkillProposal(
                row.proposalId(), new MemoryId(row.memoryId()), row.applicationId(), row.title(), row.skillMdDraft(),
                row.status(), row.createdByUserId(), row.reviewedByUserId(), row.publishedAssetId(),
                row.version(), row.createdAt(), row.updatedAt());
    }

    private SkillProposalRow toRow(MemorySkillProposal proposal) {
        return new SkillProposalRow(
                proposal.proposalId(), proposal.memoryId().value(), proposal.applicationId(), proposal.title(),
                proposal.skillMdDraft(), proposal.status(), proposal.createdByUserId(), proposal.reviewedByUserId(),
                proposal.publishedAssetId(), proposal.version(), proposal.createdAt(), proposal.updatedAt());
    }

    private List<QaTaskType> parseTaskTypes(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of(QaTaskType.GENERAL);
        }
        LinkedHashSet<QaTaskType> values = new LinkedHashSet<>();
        Arrays.stream(csv.split(",")).map(String::trim).filter(value -> !value.isEmpty())
                .map(QaTaskType::valueOf).forEach(values::add);
        return values.isEmpty() ? List.of(QaTaskType.GENERAL) : List.copyOf(values);
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
