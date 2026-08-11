package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 既有 session_messages 的受限学习只读行，不含 parts、工具输出或凭据字段。 */
public record MemoryLearningEvidenceRow(String role, String content, Instant createdAt) {
}
