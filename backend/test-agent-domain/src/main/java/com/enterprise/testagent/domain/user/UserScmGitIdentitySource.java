package com.enterprise.testagent.domain.user;

/** SCM Git 姓名的可信证据来源；远端右控拒绝返回的当前姓名优先级高于历史提交。 */
public enum UserScmGitIdentitySource {
    ACCEPTED_COMMIT_HISTORY,
    REMOTE_REJECTION
}
