package com.enterprise.testagent.persistence.mybatis;

/** 分享候选用户最小查询行模型。 */
public record SessionShareCandidateRow(String userId, String unifiedAuthId, String username) {
}
