package com.enterprise.testagent.api.web.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.opencode.runtime.terminal.TerminalApplicationService;
import com.enterprise.testagent.opencode.runtime.terminal.TerminalTicketRequest;
import com.enterprise.testagent.opencode.runtime.terminal.TerminalTicketResponse;
import com.enterprise.testagent.opencode.runtime.terminal.ServerTerminalTicketRequest;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import java.util.function.Function;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * PTY terminal HTTP 入口。WebSocket upgrade 由 TerminalWebSocketHandler 处理。
 */
@RestController
public class TerminalController {

    private final TerminalApplicationService terminalService;
    private final CurrentBackendWebSocketUrlFactory webSocketUrlFactory;
    private final RuntimeManagementBackendRoutingService backendRoutingService;

    /**
     * 注入终端应用服务，HTTP 层只负责发放短期 WebSocket ticket。
     */
    @Autowired
    public TerminalController(
            TerminalApplicationService terminalService,
            CurrentBackendWebSocketUrlFactory webSocketUrlFactory,
            RuntimeManagementBackendRoutingService backendRoutingService) {
        this.terminalService = terminalService;
        this.webSocketUrlFactory = webSocketUrlFactory;
        this.backendRoutingService = backendRoutingService;
    }

    TerminalController(TerminalApplicationService terminalService, CurrentBackendWebSocketUrlFactory webSocketUrlFactory) {
        this(terminalService, webSocketUrlFactory, null);
    }

    /**
     * 为超级管理员签发目标服务器 root 终端 ticket；远端服务器通过公共 Java 路由器转发。
     */
    @PostMapping("/api/internal/platform/opencode-runtime/management/linux-servers/{linuxServerId}/terminal/tickets")
    public Mono<ApiResponse<TerminalTicketResponse>> createServerTicket(
            @PathVariable String linuxServerId,
            @RequestBody ServerTerminalTicketRequest request,
            ServerWebExchange exchange) {
        var principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        LinuxServerId parsedLinuxServerId = new LinuxServerId(linuxServerId);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> {
                    var forwardTarget = backendRoutingService.forwardTargetForLinuxServer(exchange, parsedLinuxServerId);
                    if (forwardTarget.isPresent()) {
                        return backendRoutingService.forward(
                                exchange,
                                forwardTarget.get(),
                                request,
                                new TypeReference<ApiResponse<TerminalTicketResponse>>() {});
                    }
                    TerminalTicketResponse response = terminalService.createServerTicket(
                            parsedLinuxServerId, principal.userId(), request, traceId);
                    String pathAndQuery = "/api/internal/platform/opencode-runtime/management/linux-servers/"
                            + parsedLinuxServerId.value() + "/terminal/ws?ticket=" + response.ticket();
                    return ApiResponse.ok(new TerminalTicketResponse(
                            response.ticket(), response.expiresAt(), webSocketUrlFactory.serverRootUrl(pathAndQuery)), traceId);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 创建终端连接 ticket，允许空请求体以便前端使用默认 cwd、shell 和窗口尺寸。
     */
    @PostMapping("/api/internal/platform/opencode-runtime/sessions/{sessionId}/terminal/tickets")
    public Mono<ApiResponse<TerminalTicketResponse>> createTicket(
            @PathVariable String sessionId,
            @RequestBody(required = false) TerminalTicketRequest request,
            ServerWebExchange exchange) {
        TerminalTicketRequest resolved = request == null ? new TerminalTicketRequest(null, null, null, null, null) : request;
        return blockingResponse(exchange, traceId -> terminalTicketResponse(
                sessionId,
                terminalService.createTicket(new SessionId(sessionId), resolved, traceId)));
    }

    /**
     * 将 ticket 创建的阻塞校验逻辑移出 WebFlux event-loop。
     */
    private <T> Mono<ApiResponse<T>> blockingResponse(ServerWebExchange exchange, Function<String, T> action) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> ApiResponse.ok(action.apply(traceId), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 返回签发 ticket 的当前 Java 绝对地址，避免多后台负载均衡把 upgrade 分配到其他 JVM。
     */
    private TerminalTicketResponse terminalTicketResponse(
            String sessionId,
            TerminalTicketResponse response) {
        String pathAndQuery = "/api/internal/platform/opencode-runtime/sessions/"
                + sessionId + "/terminal/ws?ticket=" + response.ticket();
        return new TerminalTicketResponse(
                response.ticket(),
                response.expiresAt(),
                webSocketUrlFactory.absoluteUrl(pathAndQuery));
    }
}
