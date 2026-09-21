package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.team.TeamScopeMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** 团队导出产物的短时一次性下载 ticket，仅保存在产物协调节点内存。 */
@Component
class TeamWorkspaceExportDownloadTicketStore {

    private final Clock clock;
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    TeamWorkspaceExportDownloadTicketStore(Clock clock) {
        this.clock = clock;
    }

    Ticket issue(
            String exportId,
            String actorUserId,
            TeamScopeMode scopeMode,
            String ownerUserId,
            List<String> targetUserIds) {
        String value = "ted_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        Ticket ticket = new Ticket(value, exportId, actorUserId, scopeMode, ownerUserId,
                List.copyOf(targetUserIds), clock.instant().plus(Duration.ofMinutes(2)));
        tickets.put(value, ticket);
        return ticket;
    }

    Ticket consume(String value) {
        Ticket ticket = value == null ? null : tickets.remove(value);
        if (ticket == null || !ticket.expiresAt().isAfter(clock.instant())) {
            throw new PlatformException(ErrorCode.UNAUTHENTICATED, "团队导出下载凭据无效或已使用");
        }
        return ticket;
    }

    record Ticket(
            String value,
            String exportId,
            String actorUserId,
            TeamScopeMode scopeMode,
            String ownerUserId,
            List<String> targetUserIds,
            Instant expiresAt) {
    }
}
