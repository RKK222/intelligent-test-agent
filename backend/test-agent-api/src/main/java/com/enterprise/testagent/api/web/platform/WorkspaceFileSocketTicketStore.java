package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.runtime.RuntimeKind;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 单实例工作空间文件 WebSocket ticket store。ticket 短期一次性消费，不放入 URL 之外的长期凭证。
 */
@Component
class WorkspaceFileSocketTicketStore {

    private static final Duration DEFAULT_TTL = Duration.ofSeconds(60);

    private final Clock clock;
    private final Supplier<String> ticketFactory;
    private final Map<String, WorkspaceFileSocketTicket> tickets = new ConcurrentHashMap<>();

    WorkspaceFileSocketTicketStore() {
        this(Clock.systemUTC(), WorkspaceFileSocketTicketStore::newTicketId);
    }

    WorkspaceFileSocketTicketStore(Clock clock, Supplier<String> ticketFactory) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.ticketFactory = Objects.requireNonNull(ticketFactory, "ticketFactory must not be null");
    }

    WorkspaceFileSocketTicket issue(
            String workspaceId,
            String linuxServerId,
            String agentLinuxServerId,
            boolean appSourceWorkspace,
            boolean superAdmin,
            boolean appAdmin,
            String userId,
            String unifiedAuthId,
            String mode,
            String scope,
            String worktreeId,
            String traceId) {
        WorkspaceFileSocketTicket ticket = new WorkspaceFileSocketTicket(
                ticketFactory.get(),
                workspaceId,
                linuxServerId,
                agentLinuxServerId,
                appSourceWorkspace,
                superAdmin,
                appAdmin,
                userId,
                mode,
                scope,
                worktreeId,
                false,
                null,
                null,
                null,
                null,
                traceId,
                clock.instant().plus(DEFAULT_TTL),
                null, null, null, null, false, null, null, unifiedAuthId);
        tickets.put(ticket.ticket(), ticket);
        return ticket;
    }

    WorkspaceFileSocketTicket issue(
            String workspaceId,
            String linuxServerId,
            String agentLinuxServerId,
            boolean appSourceWorkspace,
            boolean superAdmin,
            boolean appAdmin,
            String userId,
            String mode,
            String scope,
            String worktreeId,
            String traceId) {
        return issue(workspaceId, linuxServerId, agentLinuxServerId, appSourceWorkspace,
                superAdmin, appAdmin, userId, null, mode, scope, worktreeId, traceId);
    }

    WorkspaceFileSocketTicket issue(
            String workspaceId,
            String linuxServerId,
            String agentLinuxServerId,
            boolean superAdmin,
            boolean appAdmin,
            String userId,
            String mode,
            String scope,
            String worktreeId,
            String traceId) {
        return issue(workspaceId, linuxServerId, agentLinuxServerId, false, superAdmin, appAdmin,
                userId, null, mode, scope, worktreeId, traceId);
    }

    WorkspaceFileSocketTicket issue(
            String workspaceId,
            String linuxServerId,
            String agentLinuxServerId,
            boolean superAdmin,
            String mode,
            String scope,
            String worktreeId,
            String traceId) {
        return issue(workspaceId, linuxServerId, agentLinuxServerId, false, superAdmin, superAdmin, null,
                null, mode, scope, worktreeId, traceId);
    }

    WorkspaceFileSocketTicket consume(String ticketValue, String origin) {
        WorkspaceFileSocketTicket ticket = tickets.remove(ticketValue);
        requireUsable(ticket, origin);
        return ticket;
    }

    /** 本地 ticket 固定客户端实例、连接 generation 与根摘要，任一变化都会失效。 */
    WorkspaceFileSocketTicket issueLocal(
            String workspaceId,
            String userId,
            String mode,
            String clientInstanceId,
            long connectionGeneration,
            String rootDigest,
            boolean appAdmin,
            String traceId) {
        WorkspaceFileSocketTicket ticket = new WorkspaceFileSocketTicket(
                ticketFactory.get(), workspaceId, null, null, false, false, appAdmin,
                userId, mode, null, null, false, null, null, null, null,
                traceId, clock.instant().plus(DEFAULT_TTL), null, null, null, null,
                false, null, null, RuntimeKind.LOCAL_CLIENT, clientInstanceId,
                connectionGeneration, rootDigest, null);
        tickets.put(ticket.ticket(), ticket);
        return ticket;
    }

    /** 非消费式预检；不存在、过期、复用和缺少 Origin 使用同一脱敏错误。 */
    void validate(String ticketValue, String origin) {
        requireUsable(ticketValue == null ? null : tickets.get(ticketValue), origin);
    }

    private void requireUsable(WorkspaceFileSocketTicket ticket, String origin) {
        if (ticket == null || ticket.expiresAt().isBefore(clock.instant())
                || origin == null || origin.isBlank()) {
            throw new PlatformException(ErrorCode.UNAUTHENTICATED, "文件 WebSocket 未授权");
        }
    }

    /** 签发只读排查 ticket；只保存授权和平台会话摘要，不保存两类 Token 明文。 */
    WorkspaceFileSocketTicket issueSupportReadOnly(
            String workspaceId,
            String linuxServerId,
            String actorUserId,
            String targetUserId,
            String grantId,
            String grantTokenDigest,
            String actorSessionDigest,
            String traceId) {
        WorkspaceFileSocketTicket ticket = new WorkspaceFileSocketTicket(
                ticketFactory.get(), workspaceId, linuxServerId, null, false, true, false,
                actorUserId, "workspace", null, null, true, grantId, grantTokenDigest,
                actorSessionDigest, targetUserId, traceId, clock.instant().plus(DEFAULT_TTL),
                null, null, null, null, false, null, null);
        tickets.put(ticket.ticket(), ticket);
        return ticket;
    }

    /** 分享文件 ticket 同时绑定 actor、执行所属人、分享版本和权限快照。 */
    WorkspaceFileSocketTicket issueShared(
            String workspaceId,
            String linuxServerId,
            String agentLinuxServerId,
            String executionOwnerUserId,
            String actorUserId,
            String shareId,
            String shareSessionId,
            long shareVersion,
            boolean canChat,
            Instant shareExpiresAt,
            String traceId) {
        Instant expiresAt = clock.instant().plus(DEFAULT_TTL);
        if (shareExpiresAt.isBefore(expiresAt)) expiresAt = shareExpiresAt;
        WorkspaceFileSocketTicket ticket = new WorkspaceFileSocketTicket(
                ticketFactory.get(), workspaceId, linuxServerId, agentLinuxServerId,
                false, false, false, executionOwnerUserId, "workspace", null, null,
                false, null, null, null, null, traceId, expiresAt,
                shareId, shareVersion, actorUserId, executionOwnerUserId, canChat, shareExpiresAt,
                shareSessionId, null);
        tickets.put(ticket.ticket(), ticket);
        return ticket;
    }

    private static String newTicketId() {
        return "wft_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
