package com.icbc.testagent.opencode.runtime.terminal;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.session.SessionId;
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

    /**
     * 为 ticket 对应 Session 预留 active PTY，若已有租约则拒绝第二条连接。
     */
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

    /**
     * 判断指定 Session 当前是否存在未释放的 PTY 租约。
     */
    public boolean isActive(SessionId sessionId) {
        return active.containsKey(sessionId);
    }

    public final class Lease implements AutoCloseable {
        private final SessionId sessionId;
        private final String traceId;
        private final AtomicBoolean released = new AtomicBoolean(false);

        /**
         * 创建租约对象，生命周期由 WebSocket 会话关闭时释放。
         */
        private Lease(SessionId sessionId, String traceId) {
            this.sessionId = sessionId;
            this.traceId = traceId;
        }

        /**
         * 返回租约绑定的平台 Session。
         */
        public SessionId sessionId() {
            return sessionId;
        }

        /**
         * 返回创建租约时的 traceId，供审计日志关联。
         */
        public String traceId() {
            return traceId;
        }

        /**
         * 释放租约；方法幂等，避免重复关闭误删后续新租约。
         */
        @Override
        public void close() {
            if (released.compareAndSet(false, true)) {
                active.remove(sessionId, this);
            }
        }
    }
}
