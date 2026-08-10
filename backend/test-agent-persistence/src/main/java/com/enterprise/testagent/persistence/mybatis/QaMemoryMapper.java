package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.persistence.mybatis.QaMemoryRows.EvidenceRow;
import com.enterprise.testagent.persistence.mybatis.QaMemoryRows.LearningJobRow;
import com.enterprise.testagent.persistence.mybatis.QaMemoryRows.MemoryRow;
import com.enterprise.testagent.persistence.mybatis.QaMemoryRows.ReviewRow;
import com.enterprise.testagent.persistence.mybatis.QaMemoryRows.SettingsRow;
import com.enterprise.testagent.persistence.mybatis.QaMemoryRows.SkillProposalRow;
import com.enterprise.testagent.persistence.mybatis.QaMemoryRows.UsageRow;
import com.enterprise.testagent.persistence.mybatis.QaMemoryRows.WhitelistRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** QA 记忆治理 MyBatis mapper；所有关系型 SQL 均维护在 XML。 */
@Mapper
public interface QaMemoryMapper {
    MemoryRow findMemory(@Param("memoryId") String memoryId);
    MemoryRow findMemoryForUpdate(@Param("memoryId") String memoryId);
    MemoryRow findMemoryByMem0Id(@Param("mem0MemoryId") String mem0MemoryId);
    List<MemoryRow> listPersonal(@Param("userId") String userId,
                                 @Param("applicationId") String applicationId,
                                 @Param("status") String status,
                                 @Param("offset") int offset, @Param("limit") int limit);
    long countPersonal(@Param("userId") String userId,
                       @Param("applicationId") String applicationId,
                       @Param("status") String status);
    List<MemoryRow> listTeam(@Param("userId") String userId,
                             @Param("applicationId") String applicationId,
                             @Param("status") String status,
                             @Param("offset") int offset, @Param("limit") int limit);
    long countTeam(@Param("userId") String userId,
                   @Param("applicationId") String applicationId,
                   @Param("status") String status);
    int insertMemory(MemoryRow row);
    int insertMemoryIfAbsentByMem0Id(MemoryRow row);
    int updateMemory(@Param("row") MemoryRow row, @Param("expectedVersion") long expectedVersion);
    int insertEvidence(EvidenceRow row);
    List<EvidenceRow> listEvidence(@Param("memoryId") String memoryId);
    int insertReview(ReviewRow row);
    List<ReviewRow> listReviews(@Param("memoryId") String memoryId);
    String findApplicationIdByRuntimeWorkspace(@Param("workspaceId") String workspaceId);
    int insertLearningJob(LearningJobRow row);
    List<LearningJobRow> listClaimableLearningJobs(@Param("now") Instant now, @Param("limit") int limit);
    int claimLearningJob(@Param("jobId") String jobId, @Param("workerId") String workerId,
                         @Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil);
    LearningJobRow findLearningJob(@Param("jobId") String jobId);
    int completeLearningJob(@Param("jobId") String jobId, @Param("workerId") String workerId,
                            @Param("now") Instant now);
    int retryLearningJob(@Param("jobId") String jobId, @Param("workerId") String workerId,
                         @Param("errorCode") String errorCode, @Param("availableAt") Instant availableAt,
                         @Param("now") Instant now, @Param("maxAttempts") int maxAttempts);
    long countLearningJobs(@Param("status") String status);
    int insertUsage(UsageRow row);
    List<UsageRow> listUsage(@Param("userId") String userId, @Param("runIds") List<String> runIds);
    SkillProposalRow findSkillProposal(@Param("proposalId") String proposalId);
    List<SkillProposalRow> listSkillProposals(@Param("userId") String userId,
                                              @Param("applicationId") String applicationId,
                                              @Param("offset") int offset, @Param("limit") int limit);
    long countSkillProposals(@Param("userId") String userId, @Param("applicationId") String applicationId);
    int insertSkillProposal(SkillProposalRow row);
    int updateSkillProposal(@Param("row") SkillProposalRow row, @Param("expectedVersion") long expectedVersion);
    boolean isWhitelisted(@Param("userId") String userId);
    List<WhitelistRow> listWhitelist(@Param("offset") int offset, @Param("limit") int limit);
    long countWhitelist();
    int upsertWhitelist(WhitelistRow row);
    int removeWhitelist(@Param("userId") String userId);
    SettingsRow loadSettings();
    int updateSettings(@Param("row") SettingsRow row, @Param("expectedVersion") long expectedVersion);
}
