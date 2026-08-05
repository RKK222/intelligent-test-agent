package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.enterprise.testagent.domain.supportaccess.SupportAccessAuditEvent;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.workspace.Workspace;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;

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

    /** 最近工单只用于前端表单建议，不表示已有授权仍然有效。 */
    record IncidentSuggestionResponse(String incidentId) {
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

    /**
     * 排查工作区响应额外携带公共 Java 路由可用性；不改变原工作区的服务器归属。
     */
    record WorkspaceResponse(
            String workspaceId,
            String name,
            String rootPath,
            String status,
            String linuxServerId,
            Instant createdAt,
            Instant updatedAt,
            String backendAvailability,
            Instant backendLastHeartbeatAt) {

        static WorkspaceResponse from(
                Workspace workspace,
                Map<String, BackendJavaProcess> liveBackends,
                String currentLinuxServerId,
                boolean backendStateKnown) {
            String linuxServerId = workspace.linuxServerId();
            BackendJavaProcess backend = linuxServerId == null ? null : liveBackends.get(linuxServerId);
            String availability;
            if (linuxServerId == null) {
                availability = "UNBOUND";
            } else if (linuxServerId.equals(currentLinuxServerId)) {
                availability = "ONLINE";
            } else if (!backendStateKnown) {
                availability = "UNKNOWN";
            } else if (backend != null && backend.status() == BackendJavaProcessStatus.READY) {
                availability = "ONLINE";
            } else {
                availability = "OFFLINE";
            }
            return new WorkspaceResponse(
                    workspace.workspaceId().value(),
                    workspace.name(),
                    workspace.rootPath(),
                    workspace.status().name(),
                    linuxServerId,
                    workspace.createdAt(),
                    workspace.updatedAt(),
                    availability,
                    backend == null ? null : backend.lastHeartbeatAt());
        }
    }

    static PageResponse<WorkspaceResponse> workspacePage(
            PageResponse<Workspace> page,
            Map<String, BackendJavaProcess> liveBackends,
            String currentLinuxServerId,
            boolean backendStateKnown) {
        return new PageResponse<>(
                page.items().stream()
                        .map(workspace -> WorkspaceResponse.from(
                                workspace, liveBackends, currentLinuxServerId, backendStateKnown))
                        .toList(),
                page.page(),
                page.size(),
                page.total());
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
