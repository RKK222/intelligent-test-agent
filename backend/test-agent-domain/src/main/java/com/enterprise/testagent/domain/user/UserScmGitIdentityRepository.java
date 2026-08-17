package com.enterprise.testagent.domain.user;

import java.util.List;
import java.util.Optional;

/** 用户 SCM Git 姓名持久化端口。 */
public interface UserScmGitIdentityRepository {

    Optional<UserScmGitIdentity> findByUserId(UserId userId);

    /** 按 userId 游标分页，避免存量用户补偿时使用高 offset。 */
    List<UserScmGitIdentityCandidate> findSyncCandidatesAfter(String afterUserId, int limit);

    /** 批量写入远端已接受提交证据；不得覆盖更强的右控拒绝证据。 */
    int upsertAcceptedCommitIdentities(List<UserScmGitIdentity> identities);

    /** 写入右控明确返回的当前姓名；这是本表最高优先级证据。 */
    void upsertRemoteRejection(UserScmGitIdentity identity);
}
