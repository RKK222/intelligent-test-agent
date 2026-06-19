package com.example.testagent.app.terminal;

import com.example.testagent.app.support.RuntimeIdGenerator;
import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
import com.example.testagent.domain.session.SessionId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 单实例短期 ticket store。ticket 使用即删除，后续分布式部署可用 Redis 替换该组件。
 */
@Component
public class TerminalTicketStore {

    private static final Duration DEFAULT_TTL = Duration.ofSeconds(60);

    private final Clock clock;
    private final Supplier<String> ticketIdFactory;
    private final Map<String, TerminalTicket> tickets = new ConcurrentHashMap<>();

    public TerminalTicketStore() {
        this(Clock.systemUTC(), RuntimeIdGenerator::terminalTicketId);
    }

    TerminalTicketStore(Clock clock, Supplier<String> ticketIdFactory) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.ticketIdFactory = Objects.requireNonNull(ticketIdFactory, "ticketIdFactory must not be null");
    }

    public TerminalTicket issue(TerminalTicketDraft draft) {
        Instant expiresAt = clock.instant().plus(DEFAULT_TTL);
        TerminalTicket ticket = new TerminalTicket(
                ticketIdFactory.get(),
                draft.sessionId(),
                draft.workspaceId(),
                draft.executionNodeId(),
                draft.workspaceRoot(),
                draft.cwd(),
                draft.shell(),
                draft.cols(),
                draft.rows(),
                draft.traceId(),
                expiresAt);
        tickets.put(ticket.ticket(), ticket);
        return ticket;
    }

    public TerminalTicket consume(SessionId sessionId, String ticketValue, String origin, String traceId) {
        TerminalTicket ticket = tickets.remove(ticketValue);
        if (ticket == null) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "PTY ticket 不存在或已使用", Map.of("sessionId", sessionId.value()));
        }
        if (!ticket.sessionId().equals(sessionId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "PTY ticket 与 Session 不匹配", Map.of("sessionId", sessionId.value()));
        }
        if (ticket.expiresAt().isBefore(clock.instant())) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "PTY ticket 已过期", Map.of("sessionId", sessionId.value()));
        }
        if (origin == null || origin.isBlank()) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "PTY WebSocket 缺少 Origin", Map.of("sessionId", sessionId.value()));
        }
        return ticket;
    }
}
