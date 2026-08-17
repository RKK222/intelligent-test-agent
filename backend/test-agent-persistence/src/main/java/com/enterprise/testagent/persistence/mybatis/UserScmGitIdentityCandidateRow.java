package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 配置 SSH Key 用户的 SCM Git 姓名补偿查询行。 */
public record UserScmGitIdentityCandidateRow(
        String userId,
        String unifiedAuthId,
        String gitName,
        String source,
        String evidenceCommit,
        Instant evidenceAt,
        Instant verifiedAt) {
}
