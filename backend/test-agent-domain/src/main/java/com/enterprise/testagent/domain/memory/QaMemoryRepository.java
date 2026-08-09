package com.enterprise.testagent.domain.memory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** QA 长期记忆治理、审核、Outbox 与使用记录的持久化端口。 */
public interface QaMemoryRepository {

    Optional<QaMemory> findById(MemoryId memoryId);

    Optional<QaMemory> findByMem0MemoryId(String mem0MemoryId);

    List<QaMemory> listPersonal(String userId, String applicationId, MemoryStatus status, int offset, int limit);

    long countPersonal(String userId, String applicationId, MemoryStatus status);

    List<QaMemory> listTeam(String userId, String applicationId, MemoryStatus status, int offset, int limit);

    long countTeam(String userId, String applicationId, MemoryStatus status);

    void insertMemory(QaMemory memory);

    boolean updateMemory(QaMemory memory, long expectedVersion);

    void insertEvidence(MemoryEvidence evidence);

    List<MemoryEvidence> listEvidence(MemoryId memoryId);

    void insertReview(MemoryReview review);

    List<MemoryReview> listReviews(MemoryId memoryId);

    Optional<String> findApplicationIdByRuntimeWorkspace(String workspaceId);

    boolean enqueueLearningJob(MemoryLearningJob job);

    List<MemoryLearningJob> claimLearningJobs(String workerId, Instant now, Instant leaseUntil, int limit);

    boolean completeLearningJob(String jobId, String workerId, Instant now);

    boolean retryLearningJob(
            String jobId, String workerId, String errorCode, Instant availableAt, Instant now, int maxAttempts);

    long countLearningJobs(String status);

    void insertUsage(MemoryUsage usage);

    default void insertUsages(List<MemoryUsage> usages) {
        usages.forEach(this::insertUsage);
    }

    List<MemoryUsage> listUsage(String userId, List<String> runIds);

    Optional<MemorySkillProposal> findSkillProposal(String proposalId);

    List<MemorySkillProposal> listSkillProposals(String userId, String applicationId, int offset, int limit);

    long countSkillProposals(String userId, String applicationId);

    void insertSkillProposal(MemorySkillProposal proposal);

    boolean updateSkillProposal(MemorySkillProposal proposal, long expectedVersion);

    boolean isWhitelisted(String userId);

    List<MemoryWhitelistEntry> listWhitelist(int offset, int limit);

    long countWhitelist();

    void upsertWhitelist(MemoryWhitelistEntry entry);

    void removeWhitelist(String userId);

    MemorySettings loadSettings();

    boolean updateSettings(MemorySettings settings, long expectedVersion);

    /** Redis grant 摘要端口与关系型治理端口分开，默认实现不承担短期凭证存储。 */
    default Duration learningLease() {
        return Duration.ofMinutes(2);
    }
}
