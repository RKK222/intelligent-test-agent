package com.example.testagent.app.terminal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * PTY 结构化审计日志，只记录长度、状态和业务 ID，不记录完整 input/output 明文。
 */
@Component
public class TerminalAuditLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(TerminalAuditLogger.class);

    public void ticketCreated(TerminalTicket ticket) {
        LOGGER.info(
                "event=pty.ticket.created traceId={} sessionId={} workspaceId={} cwd={} expiresAt={}",
                ticket.traceId(),
                ticket.sessionId().value(),
                ticket.workspaceId().value(),
                ticket.cwd(),
                ticket.expiresAt());
    }

    public void upgradeAccepted(TerminalTicket ticket) {
        LOGGER.info(
                "event=pty.upgrade.accepted traceId={} sessionId={} workspaceId={} cwd={}",
                ticket.traceId(),
                ticket.sessionId().value(),
                ticket.workspaceId().value(),
                ticket.cwd());
    }

    public void upgradeRejected(TerminalTicket ticket, String code) {
        LOGGER.warn(
                "event=pty.upgrade.rejected traceId={} sessionId={} workspaceId={} code={}",
                ticket.traceId(),
                ticket.sessionId().value(),
                ticket.workspaceId().value(),
                code);
    }

    public void input(TerminalTicket ticket, int bytes) {
        LOGGER.info(
                "event=pty.input traceId={} sessionId={} workspaceId={} bytes={}",
                ticket.traceId(),
                ticket.sessionId().value(),
                ticket.workspaceId().value(),
                Math.max(0, bytes));
    }

    public void inputRejected(TerminalTicket ticket, String code, int bytes) {
        LOGGER.warn(
                "event=pty.input.rejected traceId={} sessionId={} workspaceId={} code={} bytes={}",
                ticket.traceId(),
                ticket.sessionId().value(),
                ticket.workspaceId().value(),
                code,
                Math.max(0, bytes));
    }

    public void resize(TerminalTicket ticket, Integer cols, Integer rows) {
        LOGGER.info(
                "event=pty.resize traceId={} sessionId={} workspaceId={} cols={} rows={}",
                ticket.traceId(),
                ticket.sessionId().value(),
                ticket.workspaceId().value(),
                cols,
                rows);
    }

    public void close(TerminalTicket ticket, String reason) {
        LOGGER.info(
                "event=pty.close traceId={} sessionId={} workspaceId={} reason={}",
                ticket.traceId(),
                ticket.sessionId().value(),
                ticket.workspaceId().value(),
                reason == null || reason.isBlank() ? "unknown" : reason);
    }

    public void timeout(TerminalTicket ticket, String message) {
        LOGGER.warn(
                "event=pty.timeout traceId={} sessionId={} workspaceId={} reason={}",
                ticket.traceId(),
                ticket.sessionId().value(),
                ticket.workspaceId().value(),
                message);
    }

    public void exit(TerminalTicket ticket, int exitCode) {
        LOGGER.info(
                "event=pty.exit traceId={} sessionId={} workspaceId={} exitCode={}",
                ticket.traceId(),
                ticket.sessionId().value(),
                ticket.workspaceId().value(),
                exitCode);
    }
}
