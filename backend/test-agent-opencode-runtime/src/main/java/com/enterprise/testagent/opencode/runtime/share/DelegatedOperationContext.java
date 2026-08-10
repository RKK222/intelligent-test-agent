package com.enterprise.testagent.opencode.runtime.share;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.sessionshare.SessionShareId;
import com.enterprise.testagent.domain.support.DomainValidation;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 分享范围内的显式代操作上下文。
 *
 * <p>该对象与真实认证主体并存，actor 始终是真实登录人，executionOwner 才是 OpenCode 和工作区执行身份。
 */
public record DelegatedOperationContext(
        SessionShareId shareId,
        long shareVersion,
        UserId actorUserId,
        String actorUnifiedAuthId,
        String actorUsername,
        UserId executionOwnerUserId,
        SessionId sessionId,
        WorkspaceId workspaceId,
        boolean canChat,
        boolean delegated,
        boolean ownerAccess,
        Instant expiresAt) {

    public DelegatedOperationContext {
        Objects.requireNonNull(shareId, "shareId must not be null");
        if (shareVersion < 0) {
            throw new IllegalArgumentException("shareVersion must not be negative");
        }
        Objects.requireNonNull(actorUserId, "actorUserId must not be null");
        actorUnifiedAuthId = DomainValidation.requireText(actorUnifiedAuthId, "actorUnifiedAuthId");
        actorUsername = DomainValidation.requireText(actorUsername, "actorUsername");
        Objects.requireNonNull(executionOwnerUserId, "executionOwnerUserId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        expiresAt = DomainValidation.requireInstant(expiresAt, "expiresAt");
        if (ownerAccess == delegated) {
            throw new IllegalArgumentException("ownerAccess and delegated must be opposite");
        }
    }

    /** 精确校验 Session 范围，禁止把分享授权扩展到所属人的其它会话。 */
    public void requireSession(SessionId requestedSessionId) {
        if (!sessionId.equals(requestedSessionId)) {
            throw outOfScope("sessionId", requestedSessionId.value());
        }
    }

    /** 精确校验 Workspace 范围，禁止跨工作区代操作。 */
    public void requireWorkspace(WorkspaceId requestedWorkspaceId) {
        if (!workspaceId.equals(requestedWorkspaceId)) {
            throw outOfScope("workspaceId", requestedWorkspaceId.value());
        }
    }

    /** 要求当前成员具有对话/写入权限；所属人始终通过。 */
    public void requireChat() {
        if (!canChat) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "当前分享成员仅可查看");
        }
    }

    private PlatformException outOfScope(String field, String value) {
        return new PlatformException(
                ErrorCode.FORBIDDEN,
                "操作超出会话分享范围",
                Map.of(field, value, "shareId", shareId.value()));
    }
}
