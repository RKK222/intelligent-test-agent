package com.enterprise.testagent.domain.localclient;

import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 本地客户端发布、版本策略、rollout 和 attempt 的关系型持久化端口。 */
public interface LocalClientVersionRepository {

    Optional<LocalClientVersionModels.Release> findRelease(String version);

    List<LocalClientVersionModels.Release> findReleases();

    /** 发布目录不可变；同版本重复插入必须失败，禁止覆盖已有制品元数据。 */
    void insertRelease(LocalClientVersionModels.Release release);

    long nextPolicyRevision();

    Optional<LocalClientVersionModels.GlobalPolicy> findGlobalPolicy();

    void saveGlobalPolicy(LocalClientVersionModels.GlobalPolicy policy);

    Optional<LocalClientVersionModels.UserPolicy> findUserPolicy(UserId userId);

    List<LocalClientVersionModels.UserPolicy> findUserPolicies();

    void saveUserPolicy(LocalClientVersionModels.UserPolicy policy);

    void savePolicyAudit(LocalClientVersionModels.PolicyAudit audit);

    void insertRollout(LocalClientVersionModels.Rollout rollout);

    List<LocalClientVersionModels.Rollout> findRollouts();

    Optional<LocalClientVersionModels.Rollout> findRollout(String rolloutId);

    /** 在当前事务中锁定 rollout 行，使 attempt 读取与汇总更新按 rollout 串行。 */
    Optional<LocalClientVersionModels.Rollout> findRolloutForUpdate(String rolloutId);

    boolean completeRollout(String rolloutId, String status, Instant completedAt);

    void insertAttempts(List<LocalClientVersionModels.Attempt> attempts);

    List<LocalClientVersionModels.Attempt> findAttemptsByRollout(String rolloutId);

    Optional<LocalClientVersionModels.Attempt> findAttempt(String commandId);

    /** 到期优先并按稳定顺序分页扫描，避免固定最老批次使后续 attempt 饥饿。 */
    List<LocalClientVersionModels.Attempt> findDispatchableAttempts(int limit, int offset, Instant now);

    /** 以期望状态做 CAS；普通终态不可改写，仅 deadline FAILED 可收敛到迟到成功或自动回滚。 */
    boolean transitionAttempt(
            String commandId,
            String expectedStatus,
            String status,
            String releaseDigest,
            String errorCode,
            Instant observedAt);
}
