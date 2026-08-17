package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 用户 SCM Git 姓名持久化行。 */
public record UserScmGitIdentityRow(
        String userId,
        String gitName,
        String source,
        String evidenceCommit,
        Instant evidenceAt,
        Instant verifiedAt) {
}
