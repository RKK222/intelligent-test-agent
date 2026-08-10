package com.enterprise.testagent.domain.sessionshare;

import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.support.DomainValidation;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 分享范围审计事件。
 *
 * <p>对象刻意不提供消息正文、文件正文、明文路径、Token 或终端输入字段；文件路径只能写入摘要。
 */
public record SessionShareAuditEvent(
        String auditEventId,
        SessionShareId shareId,
        SessionId sessionId,
        WorkspaceId workspaceId,
        UserId actorUserId,
        UserId executionOwnerUserId,
        String action,
        String resourceType,
        String resourceId,
        String resourcePathSha256,
        String outcome,
        String errorCode,
        String traceId,
        Instant occurredAt) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public SessionShareAuditEvent {
        auditEventId = DomainValidation.requireText(auditEventId, "auditEventId");
        Objects.requireNonNull(shareId, "shareId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(actorUserId, "actorUserId must not be null");
        Objects.requireNonNull(executionOwnerUserId, "executionOwnerUserId must not be null");
        action = DomainValidation.requireText(action, "action");
        resourceType = DomainValidation.requireText(resourceType, "resourceType");
        resourceId = normalize(resourceId);
        resourcePathSha256 = normalize(resourcePathSha256);
        if (resourcePathSha256 != null && !SHA_256.matcher(resourcePathSha256).matches()) {
            throw new IllegalArgumentException("resourcePathSha256 must be 64 lowercase hex characters");
        }
        outcome = DomainValidation.requireText(outcome, "outcome");
        errorCode = normalize(errorCode);
        traceId = DomainValidation.requireText(traceId, "traceId");
        occurredAt = DomainValidation.requireInstant(occurredAt, "occurredAt");
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
