package com.enterprise.testagent.opencode.runtime.terminal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * PTY 结构化审计日志，只记录长度、状态和业务 ID，不记录完整 input/output 明文。
 */
@Component
public class TerminalAuditLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(TerminalAuditLogger.class);

    /**
     * 记录 ticket 创建，不输出 ticket 明文。
     */
    public void ticketCreated(TerminalTicket ticket) {
        LOGGER.info(
                "event=pty.ticket.created traceId={} targetType={} targetId={} userId={} expiresAt={}",
                ticket.traceId(),
                ticket.targetType(),
                ticket.auditTargetId(),
                userId(ticket),
                ticket.expiresAt());
    }

    /**
     * 记录 WebSocket upgrade 被接受。
     */
    public void upgradeAccepted(TerminalTicket ticket) {
        LOGGER.info(
                "event=pty.upgrade.accepted traceId={} targetType={} targetId={} userId={}",
                ticket.traceId(),
                ticket.targetType(), ticket.auditTargetId(), userId(ticket));
    }

    /**
     * 记录 WebSocket upgrade 被拒绝，只输出稳定错误码。
     */
    public void upgradeRejected(TerminalTicket ticket, String code) {
        LOGGER.warn(
                "event=pty.upgrade.rejected traceId={} targetType={} targetId={} code={}",
                ticket.traceId(),
                ticket.targetType(), ticket.auditTargetId(),
                code);
    }

    /**
     * 记录输入帧长度，不记录输入内容。
     */
    public void input(TerminalTicket ticket, int bytes) {
        LOGGER.info(
                "event=pty.input traceId={} targetType={} targetId={} bytes={}",
                ticket.traceId(),
                ticket.targetType(), ticket.auditTargetId(),
                Math.max(0, bytes));
    }

    /**
     * 记录输入被拒绝的稳定错误码和字节数。
     */
    public void inputRejected(TerminalTicket ticket, String code, int bytes) {
        LOGGER.warn(
                "event=pty.input.rejected traceId={} targetType={} targetId={} code={} bytes={}",
                ticket.traceId(),
                ticket.targetType(), ticket.auditTargetId(),
                code,
                Math.max(0, bytes));
    }

    /**
     * 记录终端尺寸变化，不记录终端内容。
     */
    public void resize(TerminalTicket ticket, Integer cols, Integer rows) {
        LOGGER.info(
                "event=pty.resize traceId={} targetType={} targetId={} cols={} rows={}",
                ticket.traceId(),
                ticket.targetType(), ticket.auditTargetId(),
                cols,
                rows);
    }

    /**
     * 记录 PTY 连接关闭原因，空原因归一为 unknown。
     */
    public void close(TerminalTicket ticket, String reason) {
        LOGGER.info(
                "event=pty.close traceId={} targetType={} targetId={} reason={}",
                ticket.traceId(),
                ticket.targetType(), ticket.auditTargetId(),
                reason == null || reason.isBlank() ? "unknown" : reason);
    }

    /**
     * 记录 PTY 会话超时。
     */
    public void timeout(TerminalTicket ticket, String message) {
        LOGGER.warn(
                "event=pty.timeout traceId={} targetType={} targetId={} reason={}",
                ticket.traceId(),
                ticket.targetType(), ticket.auditTargetId(),
                message);
    }

    /**
     * 记录本地进程退出码。
     */
    public void exit(TerminalTicket ticket, int exitCode) {
        LOGGER.info(
                "event=pty.exit traceId={} targetType={} targetId={} exitCode={}",
                ticket.traceId(),
                ticket.targetType(), ticket.auditTargetId(),
                exitCode);
    }

    private String userId(TerminalTicket ticket) {
        return ticket.userId() == null ? "-" : ticket.userId().value();
    }
}
