package com.enterprise.testagent.domain.workspace;

import com.enterprise.testagent.domain.support.DomainValidation;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/** 用户维度的工作空间 Git 远端访问巡检结果。 */
public record WorkspaceGitAccessCheck(
        UserId userId,
        TargetKind targetKind,
        String targetId,
        Status status,
        String reason,
        String message,
        Instant checkedAt) {

    public WorkspaceGitAccessCheck {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(targetKind, "targetKind must not be null");
        targetId = DomainValidation.requireText(targetId, "targetId");
        Objects.requireNonNull(status, "status must not be null");
        reason = normalizeOptional(reason);
        message = normalizeOptional(message);
        checkedAt = DomainValidation.requireInstant(checkedAt, "checkedAt");
        if (status == Status.ACCESSIBLE && (reason != null || message != null)) {
            throw new IllegalArgumentException("accessible Git check must not carry failure details");
        }
        if (status != Status.ACCESSIBLE && (reason == null || message == null)) {
            throw new IllegalArgumentException("non-accessible Git check must carry safe failure details");
        }
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 巡检目标分别对应应用工作空间模板和用户本地客户端工作区。 */
    public enum TargetKind {
        APPLICATION_WORKSPACE,
        LOCAL_WORKSPACE
    }

    /** UNKNOWN 表示 Git 能力不适用或暂时无法得出权限结论，前端不得据此禁用工作区。 */
    public enum Status {
        ACCESSIBLE,
        INACCESSIBLE,
        UNKNOWN
    }
}
