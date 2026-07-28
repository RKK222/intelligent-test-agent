package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.workspace.AppSourceApplicationService;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** 应用源码操作快照与进度 WebSocket ticket HTTP 入口。 */
@RestController
@RequestMapping("/api/internal/platform/workspace-management/app-source-operations")
public class AppSourceOperationController {

    private final AppSourceApplicationService appSources;
    private final AppSourceOperationTicketService tickets;

    public AppSourceOperationController(
            AppSourceApplicationService appSources,
            AppSourceOperationTicketService tickets) {
        this.appSources = Objects.requireNonNull(appSources, "appSources must not be null");
        this.tickets = Objects.requireNonNull(tickets, "tickets must not be null");
    }

    @GetMapping("/{operationId}")
    public Mono<ApiResponse<Object>> operation(
            @PathVariable String operationId,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        boolean appAdmin = AuthWebSupport.hasRole(principal, Dictionary.ROLE_APP_ADMIN);
        return RuntimeApiSupport.blockingObjectResponse(
                exchange,
                traceId -> AppSourceDtos.operation(
                        appSources.getOperation(operationId, principal.userId(), appAdmin)));
    }

    @PostMapping("/{operationId}/ticket")
    public Mono<ApiResponse<Object>> ticket(
            @PathVariable String operationId,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        return RuntimeApiSupport.blockingObjectResponse(
                exchange,
                traceId -> tickets.createTicket(principal, operationId, traceId));
    }
}
