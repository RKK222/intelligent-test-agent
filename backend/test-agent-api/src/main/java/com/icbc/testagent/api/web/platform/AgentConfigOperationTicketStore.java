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
 * Agent 配置进度 WebSocket ticket store；ticket 仅单实例、短期、一次性消费。
 */
@Component
class AgentConfigOperationTicketStore {

    private static final Duration DEFAULT_TTL = Duration.ofSeconds(60);

    private final Clock clock;
    private final Supplier<String> ticketFactory;
    private final Map<String, AgentConfigOperationTicket> tickets = new ConcurrentHashMap<>();

    AgentConfigOperationTicketStore() {
        this(Clock.systemUTC(), AgentConfigOperationTicketStore::newTicketId);
    }

    AgentConfigOperationTicketStore(Clock clock, Supplier<String> ticketFactory) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.ticketFactory = Objects.requireNonNull(ticketFactory, "ticketFactory must not be null");
    }

    AgentConfigOperationTicket issue(String operationId, String userId, boolean superAdmin, String traceId) {
        AgentConfigOperationTicket ticket = new AgentConfigOperationTicket(
                ticketFactory.get(),
                operationId,
                userId,
                superAdmin,
                traceId,
                clock.instant().plus(DEFAULT_TTL));
        tickets.put(ticket.ticket(), ticket);
        return ticket;
    }

    AgentConfigOperationTicket consume(String ticketValue, String origin) {
        AgentConfigOperationTicket ticket = tickets.remove(ticketValue);
        if (ticket == null) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "Agent 配置进度 ticket 不存在或已使用");
        }
        if (ticket.expiresAt().isBefore(clock.instant())) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "Agent 配置进度 ticket 已过期");
        }
        if (origin == null || origin.isBlank()) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "Agent 配置进度 WebSocket 缺少 Origin");
        }
        return ticket;
    }

    private static String newTicketId() {
        return "agt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
