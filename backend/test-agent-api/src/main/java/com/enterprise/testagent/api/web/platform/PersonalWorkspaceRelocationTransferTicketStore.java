package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** 单实例、60 秒、消费即删除的内部搬迁 WebSocket ticket store。 */
@Component
class PersonalWorkspaceRelocationTransferTicketStore {

    static final String TICKET_HEADER = "X-Test-Agent-Relocation-Ticket";
    static final String SOURCE_SERVER_HEADER = "X-Test-Agent-Source-Linux-Server-Id";
    private static final Duration TTL = Duration.ofSeconds(60);

    private final Clock clock;
    private final Map<String, PersonalWorkspaceRelocationTransferTicket> tickets = new ConcurrentHashMap<>();

    PersonalWorkspaceRelocationTransferTicketStore(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    PersonalWorkspaceRelocationTransferTicket issue(
            PersonalWorkspaceRelocationTransferDtos.TicketRequest request, String traceId) {
        Instant now = clock.instant();
        // 目标实例可能在签票后连接前失联；签发新票时顺手回收过期项，避免长期重试造成内存累积。
        tickets.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        String value = "pwrt_" + UUID.randomUUID().toString().replace("-", "");
        PersonalWorkspaceRelocationTransferTicket ticket = new PersonalWorkspaceRelocationTransferTicket(
                value,
                request.relocationId().trim(),
                request.sourceLinuxServerId().trim(),
                request.targetLinuxServerId().trim(),
                request.snapshotSha256(),
                request.archiveSizeBytes(),
                traceId,
                now.plus(TTL));
        tickets.put(value, ticket);
        return ticket;
    }

    PersonalWorkspaceRelocationTransferTicket consume(
            String value, String origin, String sourceLinuxServerId) {
        if (value == null || value.isBlank()) {
            throw invalidTicket();
        }
        PersonalWorkspaceRelocationTransferTicket ticket = tickets.remove(value);
        if (ticket == null
                || !ticket.expiresAt().isAfter(clock.instant())
                || !PersonalWorkspaceRelocationTransferController.INTERNAL_ORIGIN.equals(origin)
                || !ticket.sourceLinuxServerId().equals(sourceLinuxServerId)) {
            throw invalidTicket();
        }
        return ticket;
    }

    private PlatformException invalidTicket() {
        return new PlatformException(ErrorCode.FORBIDDEN, "个人工作区搬迁 ticket 无效或已使用");
    }
}
