package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.sessionshare.SessionShare;
import com.enterprise.testagent.domain.sessionshare.SessionShareCandidate;
import com.enterprise.testagent.domain.sessionshare.SessionShareMembership;
import com.enterprise.testagent.domain.sessionshare.SessionShareParticipant;
import com.enterprise.testagent.domain.sessionshare.SharedSessionListItem;
import com.enterprise.testagent.opencode.runtime.share.DelegatedOperationContext;
import com.enterprise.testagent.opencode.runtime.session.SessionMessageRealtimeHub.SessionMessageChange;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** 会话协作分享 HTTP DTO；不直接序列化用户密码、消息正文或内部仓储对象。 */
final class SessionShareDtos {

    private SessionShareDtos() {
    }

    record PutSessionShareRequest(
            @PositiveOrZero Long expectedVersion,
            @NotNull Instant expiresAt,
            @NotNull @Size(max = 50) List<@Valid SessionShareMemberRequest> members) {
    }

    record SessionShareMemberRequest(@NotBlank String userId, boolean canChat) {
    }

    record SessionShareMemberResponse(
            String userId,
            String unifiedAuthId,
            String username,
            boolean canChat,
            String status,
            Instant sharedAt,
            Instant updatedAt,
            Instant removedAt) {

        static SessionShareMemberResponse from(SessionShareMembership membership) {
            return new SessionShareMemberResponse(
                    membership.userId().value(), membership.unifiedAuthId(), membership.username(),
                    membership.canChat(), membership.status().name(), membership.sharedAt(),
                    membership.updatedAt(), membership.removedAt());
        }
    }

    record SessionShareResponse(
            String shareId,
            String sharePath,
            String sessionId,
            String workspaceId,
            String ownerUserId,
            String status,
            Instant expiresAt,
            long version,
            List<SessionShareMemberResponse> members,
            Instant createdAt,
            Instant updatedAt,
            Instant revokedAt) {

        static SessionShareResponse from(SessionShare share) {
            if (share == null) {
                return null;
            }
            return new SessionShareResponse(
                    share.shareId().value(), "/s/" + share.shareId().value(), share.sessionId().value(),
                    share.workspaceId().value(), share.ownerUserId().value(), share.status().name(),
                    share.expiresAt(), share.version(),
                    share.memberships().stream().map(SessionShareMemberResponse::from).toList(),
                    share.createdAt(), share.updatedAt(), share.revokedAt());
        }
    }

    record SessionShareCandidateResponse(String userId, String unifiedAuthId, String username) {

        static SessionShareCandidateResponse from(SessionShareCandidate candidate) {
            return new SessionShareCandidateResponse(
                    candidate.userId().value(), candidate.unifiedAuthId(), candidate.username());
        }
    }

    record SharedSessionListResponse(
            String shareId,
            String sharePath,
            String sessionId,
            String workspaceId,
            String sessionTitle,
            String ownerUserId,
            String ownerUnifiedAuthId,
            String ownerUsername,
            Instant sharedAt,
            Instant expiresAt,
            boolean canChat,
            String status) {

        static SharedSessionListResponse from(SharedSessionListItem item) {
            return new SharedSessionListResponse(
                    item.shareId().value(), "/s/" + item.shareId().value(), item.sessionId().value(),
                    item.workspaceId().value(), item.sessionTitle(), item.ownerUserId().value(),
                    item.ownerUnifiedAuthId(), item.ownerUsername(), item.sharedAt(), item.expiresAt(),
                    item.canChat(), item.status().name());
        }
    }

    record SessionShareAccessResponse(
            String shareId,
            long version,
            String actorUserId,
            String actorUnifiedAuthId,
            String actorUsername,
            String executionOwnerUserId,
            String sessionId,
            String workspaceId,
            boolean canChat,
            boolean delegated,
            boolean ownerAccess,
            Instant expiresAt,
            List<SessionShareParticipantResponse> participants) {

        static SessionShareAccessResponse from(
                DelegatedOperationContext context,
                List<SessionShareParticipant> participants) {
            return new SessionShareAccessResponse(
                    context.shareId().value(), context.shareVersion(), context.actorUserId().value(),
                    context.actorUnifiedAuthId(), context.actorUsername(),
                    context.executionOwnerUserId().value(), context.sessionId().value(),
                    context.workspaceId().value(), context.canChat(), context.delegated(),
                    context.ownerAccess(), context.expiresAt(),
                    participants.stream().map(SessionShareParticipantResponse::from).toList());
        }
    }

    record SessionShareParticipantResponse(
            String userId,
            String unifiedAuthId,
            String username,
            boolean owner,
            boolean canChat,
            String status) {

        static SessionShareParticipantResponse from(SessionShareParticipant participant) {
            return new SessionShareParticipantResponse(
                    participant.userId().value(), participant.unifiedAuthId(), participant.username(),
                    participant.owner(), participant.canChat(), participant.owner()
                            ? "OWNER"
                            : participant.membershipStatus().name());
        }
    }

    /** 单个分享会话的实时运行与权限状态；失效帧后服务端立即关闭连接。 */
    record SessionShareRuntimeStateResponse(
            boolean active,
            String reason,
            String shareId,
            long version,
            String sessionId,
            String workspaceId,
            boolean canChat,
            Instant expiresAt,
            RuntimeDtos.RunResponse activeRun,
            Instant sessionUpdatedAt,
            SessionMessageChangeResponse messageChange,
            Instant generatedAt) {

        static SessionShareRuntimeStateResponse active(
                DelegatedOperationContext context,
                RuntimeDtos.RunResponse activeRun,
                Instant sessionUpdatedAt,
                SessionMessageChange messageChange,
                Instant generatedAt) {
            return new SessionShareRuntimeStateResponse(
                    true, null, context.shareId().value(), context.shareVersion(),
                    context.sessionId().value(), context.workspaceId().value(), context.canChat(),
                    context.expiresAt(), activeRun, sessionUpdatedAt,
                    SessionMessageChangeResponse.from(messageChange), generatedAt);
        }

        static SessionShareRuntimeStateResponse invalid(
                DelegatedOperationContext previous,
                String reason,
                Instant generatedAt) {
            return new SessionShareRuntimeStateResponse(
                    false, reason, previous.shareId().value(), previous.shareVersion(),
                    previous.sessionId().value(), previous.workspaceId().value(), false,
                    previous.expiresAt(), null, null, null, generatedAt);
        }
    }

    /** 消息变化只透传归并身份和修订号，正文仍由受鉴权的消息接口读取。 */
    record SessionMessageChangeResponse(
            String sessionId,
            String sourceRunId,
            String replacementRunId,
            String changeType,
            Instant revision) {

        static SessionMessageChangeResponse from(SessionMessageChange change) {
            if (change == null) return null;
            return new SessionMessageChangeResponse(
                    change.sessionId().value(),
                    change.sourceRunId().value(),
                    change.replacementRunId().value(),
                    change.changeType().name(),
                    change.revision());
        }
    }

    static PageResponse<SessionShareCandidateResponse> candidatePage(
            PageResponse<SessionShareCandidate> page) {
        return new PageResponse<>(
                page.items().stream().map(SessionShareCandidateResponse::from).toList(),
                page.page(), page.size(), page.total());
    }

    static PageResponse<SharedSessionListResponse> sharedSessionPage(
            PageResponse<SharedSessionListItem> page) {
        return new PageResponse<>(
                page.items().stream().map(SharedSessionListResponse::from).toList(),
                page.page(), page.size(), page.total());
    }
}
