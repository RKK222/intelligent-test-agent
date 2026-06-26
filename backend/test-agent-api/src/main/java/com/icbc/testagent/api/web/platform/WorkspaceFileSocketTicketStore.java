package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
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
            boolean superAdmin,
            String mode,
            String traceId) {
        WorkspaceFileSocketTicket ticket = new WorkspaceFileSocketTicket(
                ticketFactory.get(),
                workspaceId,
                linuxServerId,
                agentLinuxServerId,
                superAdmin,
                mode,
                traceId,
                clock.instant().plus(DEFAULT_TTL));
        tickets.put(ticket.ticket(), ticket);
        return ticket;
    }

    WorkspaceFileSocketTicket consume(String ticketValue, String origin) {
        WorkspaceFileSocketTicket ticket = tickets.remove(ticketValue);
        if (ticket == null) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "文件 WebSocket ticket 不存在或已使用");
        }
        if (ticket.expiresAt().isBefore(clock.instant())) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "文件 WebSocket ticket 已过期");
        }
        if (origin == null || origin.isBlank()) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "文件 WebSocket 缺少 Origin");
        }
        return ticket;
    }

    private static String newTicketId() {
        return "wft_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
