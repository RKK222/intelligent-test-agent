package com.enterprise.testagent.domain.workspace;

/**
 * 工作区状态用于持久化和后续 API 过滤；删除类语义后续以归档状态兼容旧数据。
 */
public enum WorkspaceStatus {
    ACTIVE,
    ARCHIVED
}
