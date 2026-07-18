package com.enterprise.testagent.opencode.runtime.terminal;

import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
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

    /**
     * 使用系统时钟和统一 ID 生成器创建单实例 ticket store。
     */
    public TerminalTicketStore() {
        this(Clock.systemUTC(), RuntimeIdGenerator::terminalTicketId);
    }

    /**
     * 创建可注入时钟和 ID 工厂的 ticket store，便于测试过期和固定 ticket。
     */
    TerminalTicketStore(Clock clock, Supplier<String> ticketIdFactory) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.ticketIdFactory = Objects.requireNonNull(ticketIdFactory, "ticketIdFactory must not be null");
    }

    /**
     * 签发短期 ticket，并缓存完整内部状态供 WebSocket upgrade 消费。
     */
    public TerminalTicket issue(TerminalTicketDraft draft) {
        Instant expiresAt = clock.instant().plus(DEFAULT_TTL);
        TerminalTicket ticket = new TerminalTicket(
                ticketIdFactory.get(),
                draft.linuxServerId() == null ? TerminalTicket.TARGET_WORKSPACE : TerminalTicket.TARGET_SERVER_ROOT,
                draft.sessionId(),
                draft.workspaceId(),
                draft.executionNodeId(),
                draft.linuxServerId(),
                draft.userId(),
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

    /**
     * 一次性消费 ticket，校验 session、过期时间和 Origin；失败时 ticket 不可复用。
     */
    public TerminalTicket consume(SessionId sessionId, String ticketValue, String origin, String traceId) {
        TerminalTicket ticket = tickets.remove(ticketValue);
        if (ticket == null) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "PTY ticket 不存在或已使用", Map.of("sessionId", sessionId.value()));
        }
        if (ticket.serverRoot() || !ticket.sessionId().equals(sessionId)) {
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

    /**
     * 一次性消费服务器 root ticket，校验目标服务器、过期时间和浏览器 Origin。
     */
    public TerminalTicket consumeServer(LinuxServerId linuxServerId, String ticketValue, String origin, String traceId) {
        TerminalTicket ticket = tickets.remove(ticketValue);
        if (ticket == null) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "PTY ticket 不存在或已使用",
                    Map.of("linuxServerId", linuxServerId.value()));
        }
        if (!ticket.serverRoot() || !ticket.linuxServerId().equals(linuxServerId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "PTY ticket 与服务器不匹配",
                    Map.of("linuxServerId", linuxServerId.value()));
        }
        validateServerTicket(ticket, linuxServerId, origin);
        return ticket;
    }

    private void validateServerTicket(TerminalTicket ticket, LinuxServerId linuxServerId, String origin) {
        if (ticket.expiresAt().isBefore(clock.instant())) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "PTY ticket 已过期",
                    Map.of("linuxServerId", linuxServerId.value()));
        }
        if (origin == null || origin.isBlank()) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "PTY WebSocket 缺少 Origin",
                    Map.of("linuxServerId", linuxServerId.value()));
        }
    }
}
