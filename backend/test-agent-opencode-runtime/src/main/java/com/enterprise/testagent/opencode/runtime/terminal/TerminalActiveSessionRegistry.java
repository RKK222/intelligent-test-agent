package com.enterprise.testagent.opencode.runtime.terminal;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.session.SessionId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/**
 * 单实例 active PTY 注册表：workspace 按 session、root 终端按服务器和用户限制单连接。
 */
@Component
public class TerminalActiveSessionRegistry {

    private final Map<String, Lease> active = new ConcurrentHashMap<>();

    /**
     * 为 ticket 对应 Session 预留 active PTY，若已有租约则拒绝第二条连接。
     */
    public Lease reserve(TerminalTicket ticket) {
        String key = ticket.activeKey();
        Lease lease = new Lease(key, ticket.sessionId(), ticket.traceId());
        Lease existing = active.putIfAbsent(key, lease);
        if (existing != null) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    ticket.serverRoot() ? "当前用户已打开该服务器 root 终端" : "Session 已存在 active PTY",
                    Map.of("targetId", ticket.auditTargetId()));
        }
        return lease;
    }

    /**
     * 判断指定 Session 当前是否存在未释放的 PTY 租约。
     */
    public boolean isActive(SessionId sessionId) {
        return active.containsKey("workspace:" + sessionId.value());
    }

    public final class Lease implements AutoCloseable {
        private final String key;
        private final SessionId sessionId;
        private final String traceId;
        private final AtomicBoolean released = new AtomicBoolean(false);

        /**
         * 创建租约对象，生命周期由 WebSocket 会话关闭时释放。
         */
        private Lease(String key, SessionId sessionId, String traceId) {
            this.key = key;
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
                active.remove(key, this);
            }
        }
    }
}
