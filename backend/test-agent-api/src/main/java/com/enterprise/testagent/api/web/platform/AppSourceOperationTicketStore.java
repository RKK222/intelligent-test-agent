package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** 应用源码进度 ticket 的单实例短期存储；任何消费尝试都会使 ticket 失效。 */
@Component
class AppSourceOperationTicketStore {

    private static final Duration DEFAULT_TTL = Duration.ofSeconds(60);

    private final Clock clock;
    private final Supplier<String> ticketFactory;
    private final Map<String, AppSourceOperationTicket> tickets = new ConcurrentHashMap<>();

    AppSourceOperationTicketStore() {
        this(Clock.systemUTC(), AppSourceOperationTicketStore::newTicketId);
    }

    AppSourceOperationTicketStore(Clock clock, Supplier<String> ticketFactory) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.ticketFactory = Objects.requireNonNull(ticketFactory, "ticketFactory must not be null");
    }

    AppSourceOperationTicket issue(
            String operationId,
            String userId,
            boolean appAdmin,
            String issuerBackendProcessId,
            String traceId) {
        AppSourceOperationTicket ticket = new AppSourceOperationTicket(
                ticketFactory.get(),
                operationId,
                userId,
                appAdmin,
                issuerBackendProcessId,
                traceId,
                clock.instant().plus(DEFAULT_TTL));
        tickets.put(ticket.ticket(), ticket);
        return ticket;
    }

    AppSourceOperationTicket consume(
            String ticketValue,
            String expectedOperationId,
            String currentBackendProcessId,
            String origin) {
        AppSourceOperationTicket ticket = tickets.remove(ticketValue);
        if (ticket == null) {
            throw denied();
        }
        if (!ticket.expiresAt().isAfter(clock.instant())) {
            throw denied();
        }
        if (!Objects.equals(ticket.operationId(), expectedOperationId)) {
            throw denied();
        }
        if (!Objects.equals(ticket.issuerBackendProcessId(), currentBackendProcessId)) {
            throw denied();
        }
        if (origin == null || origin.isBlank()) {
            throw denied();
        }
        return ticket;
    }

    private PlatformException denied() {
        // 所有失败使用同一低敏响应，避免泄露 ticket 是否存在、过期或属于哪台 JVM。
        return new PlatformException(ErrorCode.FORBIDDEN, "应用源码进度 ticket 无效");
    }

    private static String newTicketId() {
        return "ast_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }
}
