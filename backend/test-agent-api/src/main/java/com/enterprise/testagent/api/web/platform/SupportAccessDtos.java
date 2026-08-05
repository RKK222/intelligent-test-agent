package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.domain.supportaccess.SupportAccessAuditEvent;
import com.enterprise.testagent.domain.user.User;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** 超级管理员问题排查入口 DTO。 */
final class SupportAccessDtos {

    private SupportAccessDtos() {
    }

    record IssueGrantRequest(
            @NotBlank @Size(max = 128) String incidentId,
            @NotBlank @Size(max = 1000) String reason,
            @Min(5) @Max(240) int durationMinutes,
            @AssertTrue boolean readOnlyAcknowledged) {
    }

    record GrantResponse(String grantId, String grantToken, Instant expiresAt) {
    }

    record TargetResponse(
            String userId,
            String unifiedAuthId,
            String username,
            String status) {

        static TargetResponse from(User user) {
            return new TargetResponse(
                    user.userId().value(), user.unifiedAuthId(), user.username(), user.status().name());
        }
    }

    record SupportFileTicketRequest(@NotBlank String linuxServerId) {
    }

    record AuditEventResponse(
            String eventId,
            String grantId,
            String actorUserId,
            String actorUsername,
            String targetUserId,
            String targetUsername,
            String incidentId,
            String reason,
            String action,
            String resourceType,
            String resourceId,
            String pathDigest,
            String outcome,
            String errorCode,
            String traceId,
            String ipAddress,
            String userAgentDigest,
            Instant occurredAt) {

        static AuditEventResponse from(SupportAccessAuditEvent event) {
            return new AuditEventResponse(
                    event.eventId(), event.grantId(), event.actorUserId(), event.actorUsername(),
                    event.targetUserId(), event.targetUsername(), event.incidentId(), event.reason(), event.action(),
                    event.resourceType(), event.resourceId(), event.pathDigest(), event.outcome(), event.errorCode(),
                    event.traceId(), event.ipAddress(), event.userAgentDigest(), event.occurredAt());
        }
    }
}
