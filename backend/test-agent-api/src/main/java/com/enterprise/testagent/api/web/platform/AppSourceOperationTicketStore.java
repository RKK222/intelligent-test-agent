package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** 应用源码进度 ticket 的单实例短期存储；正确 Origin 的消费尝试按绑定身份原子失效。 */
@Component
class AppSourceOperationTicketStore {

    private static final Duration DEFAULT_TTL = Duration.ofSeconds(60);
    private static final int DEFAULT_MAXIMUM_SIZE = 10_000;

    private final Clock clock;
    private final Supplier<String> ticketFactory;
    private final int maximumSize;
    private final Map<String, AppSourceOperationTicket> tickets = new ConcurrentHashMap<>();

    AppSourceOperationTicketStore() {
        this(Clock.systemUTC(), AppSourceOperationTicketStore::newTicketId, DEFAULT_MAXIMUM_SIZE);
    }

    AppSourceOperationTicketStore(Clock clock, Supplier<String> ticketFactory) {
        this(clock, ticketFactory, DEFAULT_MAXIMUM_SIZE);
    }

    AppSourceOperationTicketStore(Clock clock, Supplier<String> ticketFactory, int maximumSize) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.ticketFactory = Objects.requireNonNull(ticketFactory, "ticketFactory must not be null");
        if (maximumSize < 1) {
            throw new IllegalArgumentException("maximumSize must be positive");
        }
        this.maximumSize = maximumSize;
    }

    synchronized AppSourceOperationTicket issue(
            String operationId,
            String userId,
            boolean appAdmin,
            String issuerBackendProcessId,
            String origin,
            String traceId) {
        cleanupExpired(clock.instant());
        if (tickets.size() >= maximumSize) {
            // 有效票达到硬上限时明确拒绝新签发，不能静默驱逐仍可使用的安全凭据。
            throw new PlatformException(ErrorCode.RATE_LIMITED, "应用源码进度 ticket 签发过于频繁");
        }
        AppSourceOperationTicket ticket = new AppSourceOperationTicket(
                ticketFactory.get(),
                operationId,
                userId,
                appAdmin,
                issuerBackendProcessId,
                AppSourceWebSocketOrigin.canonicalize(origin),
                traceId,
                clock.instant().plus(DEFAULT_TTL));
        if (tickets.putIfAbsent(ticket.ticket(), ticket) != null) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "应用源码进度 ticket 签发失败");
        }
        return ticket;
    }

    AppSourceOperationTicket consume(
            String ticketValue,
            String expectedOperationId,
            String currentBackendProcessId,
            String origin) {
        String canonicalOrigin;
        try {
            canonicalOrigin = AppSourceWebSocketOrigin.canonicalize(origin);
        } catch (PlatformException exception) {
            throw denied();
        }
        cleanupExpired(clock.instant());
        AtomicReference<AppSourceOperationTicket> consumed = new AtomicReference<>();
        tickets.compute(ticketValue, (ignored, ticket) -> {
            if (ticket == null) {
                return null;
            }
            if (!Objects.equals(ticket.origin(), canonicalOrigin)) {
                // 错误来源不得消费或烧毁泄露票，正确来源随后仍可完成唯一一次原子消费。
                return ticket;
            }
            if (!Objects.equals(ticket.operationId(), expectedOperationId)
                    || !Objects.equals(ticket.issuerBackendProcessId(), currentBackendProcessId)) {
                return null;
            }
            consumed.set(ticket);
            return null;
        });
        if (consumed.get() == null) {
            throw denied();
        }
        return consumed.get();
    }

    private void cleanupExpired(java.time.Instant now) {
        tickets.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private PlatformException denied() {
        // 所有失败使用同一低敏响应，避免泄露 ticket 是否存在、过期或属于哪台 JVM。
        return new PlatformException(ErrorCode.FORBIDDEN, "应用源码进度 ticket 无效");
    }

    private static String newTicketId() {
        return "ast_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }
}
