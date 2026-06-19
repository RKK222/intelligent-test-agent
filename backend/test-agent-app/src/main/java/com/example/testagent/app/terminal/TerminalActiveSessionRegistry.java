package com.example.testagent.app.terminal;

import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
import com.example.testagent.domain.session.SessionId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/**
 * 单实例 active PTY 注册表，固定语义为同一平台 session 同时只允许一个交互式终端。
 */
@Component
public class TerminalActiveSessionRegistry {

    private final Map<SessionId, Lease> active = new ConcurrentHashMap<>();

    public Lease reserve(TerminalTicket ticket) {
        Lease lease = new Lease(ticket.sessionId(), ticket.traceId());
        Lease existing = active.putIfAbsent(ticket.sessionId(), lease);
        if (existing != null) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "Session 已存在 active PTY",
                    Map.of("sessionId", ticket.sessionId().value()));
        }
        return lease;
    }

    public boolean isActive(SessionId sessionId) {
        return active.containsKey(sessionId);
    }

    public final class Lease implements AutoCloseable {
        private final SessionId sessionId;
        private final String traceId;
        private final AtomicBoolean released = new AtomicBoolean(false);

        private Lease(SessionId sessionId, String traceId) {
            this.sessionId = sessionId;
            this.traceId = traceId;
        }

        public SessionId sessionId() {
            return sessionId;
        }

        public String traceId() {
            return traceId;
        }

        @Override
        public void close() {
            if (released.compareAndSet(false, true)) {
                active.remove(sessionId, this);
            }
        }
    }
}
