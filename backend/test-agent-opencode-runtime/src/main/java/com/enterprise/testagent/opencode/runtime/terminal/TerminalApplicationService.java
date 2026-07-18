package com.enterprise.testagent.opencode.runtime.terminal;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionRepository;
import com.enterprise.testagent.domain.session.SessionStatus;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.user.UserId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * PTY ticket 应用服务，只负责安全校验和 ticket 生命周期，不直接处理 WebSocket 帧。
 */
@Service
public class TerminalApplicationService {

    private static final int DEFAULT_COLS = 80;
    private static final int DEFAULT_ROWS = 24;
    private static final int MAX_COLS = 240;
    private static final int MAX_ROWS = 80;

    private final WorkspaceRepository workspaceRepository;
    private final SessionRepository sessionRepository;
    private final TerminalTicketStore ticketStore;
    private final TerminalTicketRateLimiter ticketRateLimiter;
    private final TerminalAuditLogger auditLogger;
    private final ManagedWorkspacePathResolver pathResolver;
    private final BackendInstanceIdentity backendIdentity;
    private final boolean serverTerminalEnabled;
    private final Path serverWorkingDirectory;

    /**
     * 创建 PTY ticket 应用服务，所有安全校验在签发 ticket 前完成。
     */
    @Autowired
    public TerminalApplicationService(
            WorkspaceRepository workspaceRepository,
            SessionRepository sessionRepository,
            TerminalTicketStore ticketStore,
            TerminalTicketRateLimiter ticketRateLimiter,
            TerminalAuditLogger auditLogger,
            ManagedWorkspacePathResolver pathResolver,
            BackendInstanceIdentity backendIdentity,
            @Value("${test-agent.terminal.server-enabled:false}") boolean serverTerminalEnabled,
            @Value("${test-agent.terminal.server-working-directory:${user.dir}}") String serverWorkingDirectory) {
        this(workspaceRepository, sessionRepository, ticketStore, ticketRateLimiter, auditLogger, pathResolver,
                backendIdentity, serverTerminalEnabled, Path.of(serverWorkingDirectory));
    }

    TerminalApplicationService(
            WorkspaceRepository workspaceRepository,
            SessionRepository sessionRepository,
            TerminalTicketStore ticketStore,
            TerminalTicketRateLimiter ticketRateLimiter,
            TerminalAuditLogger auditLogger,
            ManagedWorkspacePathResolver pathResolver,
            BackendInstanceIdentity backendIdentity,
            boolean serverTerminalEnabled,
            Path serverWorkingDirectory) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.ticketStore = Objects.requireNonNull(ticketStore, "ticketStore must not be null");
        this.ticketRateLimiter = Objects.requireNonNull(ticketRateLimiter, "ticketRateLimiter must not be null");
        this.auditLogger = Objects.requireNonNull(auditLogger, "auditLogger must not be null");
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver must not be null");
        this.backendIdentity = Objects.requireNonNull(backendIdentity, "backendIdentity must not be null");
        this.serverTerminalEnabled = serverTerminalEnabled;
        this.serverWorkingDirectory = Objects.requireNonNull(serverWorkingDirectory, "serverWorkingDirectory must not be null");
    }

    public TerminalApplicationService(
            WorkspaceRepository workspaceRepository,
            SessionRepository sessionRepository,
            TerminalTicketStore ticketStore,
            TerminalTicketRateLimiter ticketRateLimiter,
            TerminalAuditLogger auditLogger) {
        this(
                workspaceRepository,
                sessionRepository,
                ticketStore,
                ticketRateLimiter,
                auditLogger,
                ManagedWorkspacePathResolver.legacyOnly(),
                testBackendIdentity(),
                false,
                Path.of(System.getProperty("user.dir")));
    }

    /**
     * 签发当前部署服务器终端 ticket。shell 直接继承 Java 进程的系统用户和操作系统权限。
     */
    public TerminalTicketResponse createServerTicket(
            LinuxServerId linuxServerId,
            UserId userId,
            ServerTerminalTicketRequest request,
            String traceId) {
        if (!serverTerminalEnabled) {
            throw new PlatformException(ErrorCode.TERMINAL_UNAVAILABLE, "服务器终端未启用");
        }
        if (!backendIdentity.linuxServerId().equals(linuxServerId.value())) {
            throw new PlatformException(ErrorCode.TERMINAL_UNAVAILABLE, "请求未路由到目标服务器",
                    Map.of("linuxServerId", linuxServerId.value()));
        }
        String expectedConfirmation = "SERVER@" + linuxServerId.value();
        if (request == null || !expectedConfirmation.equals(request.confirmationText())) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "请输入完整确认文本",
                    Map.of("expected", expectedConfirmation));
        }
        Path cwd = serverWorkingDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(cwd) || !Files.isExecutable(Path.of("/bin/bash"))) {
            throw new PlatformException(ErrorCode.TERMINAL_UNAVAILABLE, "服务器终端工作目录或 /bin/bash 不可用");
        }
        ticketRateLimiter.acquireServer(linuxServerId, userId);
        TerminalTicket ticket = ticketStore.issue(TerminalTicketDraft.serverShell(
                linuxServerId,
                userId,
                cwd,
                clamp(request.cols(), DEFAULT_COLS, MAX_COLS),
                clamp(request.rows(), DEFAULT_ROWS, MAX_ROWS),
                traceId));
        auditLogger.ticketCreated(ticket);
        return new TerminalTicketResponse(ticket.ticket(), ticket.expiresAt(),
                "/api/internal/platform/opencode-runtime/management/linux-servers/"
                        + linuxServerId.value() + "/terminal/ws?ticket=" + ticket.ticket());
    }

    /**
     * 签发一次性 PTY ticket，校验 Session、Workspace、cwd、shell 和 ticket 创建频率。
     */
    public TerminalTicketResponse createTicket(SessionId sessionId, TerminalTicketRequest request, String traceId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Session 不存在", Map.of("sessionId", sessionId.value())));
        if (session.status() == SessionStatus.ARCHIVED) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "Session 不存在", Map.of("sessionId", sessionId.value()));
        }
        if (!session.hasOpencodeSessionMapping()) {
            throw new PlatformException(ErrorCode.CONFLICT, "Session 尚未绑定远端运行上下文", Map.of("sessionId", sessionId.value()));
        }
        WorkspaceId workspaceId = request.workspaceId() == null || request.workspaceId().isBlank()
                ? session.workspaceId()
                : new WorkspaceId(request.workspaceId());
        if (!session.workspaceId().equals(workspaceId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "Session 与 Workspace 不匹配", Map.of("sessionId", sessionId.value(), "workspaceId", workspaceId.value()));
        }
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Workspace 不存在", Map.of("workspaceId", workspaceId.value())));
        ticketRateLimiter.acquire(session.sessionId(), workspace.workspaceId());
        Path root = workspaceRoot(workspace);
        Path cwd = resolveCwd(root, request.cwd());
        TerminalTicket ticket = ticketStore.issue(new TerminalTicketDraft(
                session.sessionId(),
                workspace.workspaceId(),
                session.opencodeExecutionNodeId(),
                root,
                cwd,
                resolveShell(request.shell()),
                clamp(request.cols(), DEFAULT_COLS, MAX_COLS),
                clamp(request.rows(), DEFAULT_ROWS, MAX_ROWS),
                traceId));
        auditLogger.ticketCreated(ticket);
        return new TerminalTicketResponse(
                ticket.ticket(),
                ticket.expiresAt(),
                "/api/sessions/" + session.sessionId().value() + "/terminal/ws?ticket=" + ticket.ticket());
    }

    /**
     * 消费一次性 ticket，供 WebSocket upgrade 后创建受控 PTY 会话。
     */
    public TerminalTicket consumeTicket(SessionId sessionId, String ticket, String origin, String traceId) {
        return ticketStore.consume(sessionId, ticket, origin, traceId);
    }

    /** 消费目标服务器的一次性终端 ticket。 */
    public TerminalTicket consumeServerTicket(LinuxServerId linuxServerId, String ticket, String origin, String traceId) {
        return ticketStore.consumeServer(linuxServerId, ticket, origin, traceId);
    }

    /**
     * 解析 Workspace 根目录真实路径，根目录不可访问时阻止签发 ticket。
     */
    private Path workspaceRoot(Workspace workspace) {
        try {
            return pathResolver.resolve(workspace.rootPath()).toRealPath();
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.CONFLICT, "Workspace 根路径不可用", Map.of("workspaceId", workspace.workspaceId().value()), exception);
        }
    }

    /**
     * 解析并校验 PTY cwd，必须是 Workspace 根目录内已存在的目录。
     */
    private Path resolveCwd(Path root, String cwd) {
        Path candidate = root.resolve(cwd == null || cwd.isBlank() ? "." : cwd).normalize();
        if (!candidate.startsWith(root)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "PTY cwd 越出 Workspace", Map.of("cwd", cwd == null ? "." : cwd));
        }
        try {
            Path real = Files.exists(candidate) ? candidate.toRealPath() : candidate;
            if (!real.startsWith(root) || !Files.isDirectory(real)) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "PTY cwd 必须是 Workspace 内目录", Map.of("cwd", cwd == null ? "." : cwd));
            }
            return real;
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "PTY cwd 不可访问", Map.of("cwd", cwd == null ? "." : cwd), exception);
        }
    }

    /**
     * 解析后端允许的 shell；当前禁止前端覆盖 shell 可执行文件路径。
     */
    private String resolveShell(String requestedShell) {
        if (requestedShell != null && !requestedShell.isBlank()) {
            // 当前只允许前端使用平台默认 shell，避免把任意可执行文件路径暴露为 Web 输入。
            throw new PlatformException(ErrorCode.FORBIDDEN, "PTY shell 暂不允许前端覆盖", Map.of("shell", requestedShell));
        }
        String shell = System.getenv("SHELL");
        return shell == null || shell.isBlank() ? "/bin/sh" : shell;
    }

    /**
     * 规范化终端尺寸，缺失或非法时使用默认值，并限制最大值。
     */
    private int clamp(Integer value, int fallback, int max) {
        if (value == null || value <= 0) {
            return fallback;
        }
        return Math.min(value, max);
    }

    private static BackendInstanceIdentity testBackendIdentity() {
        return new BackendInstanceIdentity() {
            @Override public String instanceId() { return "test"; }
            @Override public String linuxServerId() { return "test"; }
            @Override public String backendProcessId() { return "test"; }
            @Override public String listenUrl() { return "http://127.0.0.1:8080"; }
        };
    }
}
