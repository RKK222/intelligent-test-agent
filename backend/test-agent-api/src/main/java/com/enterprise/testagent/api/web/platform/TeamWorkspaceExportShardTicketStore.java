package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** 单实例、60 秒、消费即删除的团队导出 shard 接收票据。 */
@Component
class TeamWorkspaceExportShardTicketStore {

    static final String TICKET_HEADER = "X-Test-Agent-Team-Export-Ticket";
    static final String SOURCE_SERVER_HEADER = "X-Test-Agent-Source-Linux-Server-Id";
    private static final Duration TTL = Duration.ofSeconds(60);

    private final Clock clock;
    private final Map<String, TeamWorkspaceExportShardTicket> tickets = new ConcurrentHashMap<>();

    TeamWorkspaceExportShardTicketStore(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    TeamWorkspaceExportShardTicket issue(String sourceLinuxServerId, Path target, long maxArchiveBytes) {
        Instant now = clock.instant();
        tickets.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        String value = "twes_" + UUID.randomUUID().toString().replace("-", "");
        TeamWorkspaceExportShardTicket ticket = new TeamWorkspaceExportShardTicket(
                value, sourceLinuxServerId, target.toAbsolutePath().normalize(), maxArchiveBytes, now.plus(TTL));
        tickets.put(value, ticket);
        return ticket;
    }

    TeamWorkspaceExportShardTicket consume(String value, String origin, String sourceLinuxServerId) {
        TeamWorkspaceExportShardTicket ticket = value == null ? null : tickets.remove(value);
        if (ticket == null
                || !ticket.expiresAt().isAfter(clock.instant())
                || !TeamWorkspaceExportShardController.INTERNAL_ORIGIN.equals(origin)
                || !ticket.sourceLinuxServerId().equals(sourceLinuxServerId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "团队导出 shard ticket 无效或已使用");
        }
        return ticket;
    }
}
