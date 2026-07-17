package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.workspace.ReferenceRepositoryApplicationService;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** 引用资产库 HTTP 入口，只负责 APP_ADMIN 鉴权、DTO、traceId 和阻塞任务调度。 */
@RestController
@RequestMapping("/api/internal/platform/workspace-management/applications/{appId}/reference-repositories")
public class ReferenceRepositoryController {

    private final ReferenceRepositoryApplicationService service;

    public ReferenceRepositoryController(ReferenceRepositoryApplicationService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    @GetMapping
    public Mono<ApiResponse<Object>> list(@PathVariable String appId, ServerWebExchange exchange) {
        requireAppAdmin(exchange);
        return RuntimeApiSupport.blockingObjectResponse(exchange, traceId -> service.list(appId));
    }

    @PostMapping("/{repositoryId}/initialize")
    public Mono<ApiResponse<Object>> initialize(
            @PathVariable String appId,
            @PathVariable String repositoryId,
            @RequestBody ReferenceRepositoryDtos.InitializeRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = requireAppAdmin(exchange);
        return RuntimeApiSupport.blockingObjectResponse(exchange, traceId -> service.initialize(
                appId, repositoryId, request.branch(), principal.userId(), traceId));
    }

    @PostMapping("/{repositoryId}/synchronize")
    public Mono<ApiResponse<Object>> synchronize(
            @PathVariable String appId,
            @PathVariable String repositoryId,
            ServerWebExchange exchange) {
        AuthPrincipal principal = requireAppAdmin(exchange);
        return RuntimeApiSupport.blockingObjectResponse(exchange, traceId -> service.synchronize(
                appId, repositoryId, principal.userId(), traceId));
    }

    @GetMapping("/{repositoryId}/status")
    public Mono<ApiResponse<Object>> status(
            @PathVariable String appId,
            @PathVariable String repositoryId,
            ServerWebExchange exchange) {
        requireAppAdmin(exchange);
        return RuntimeApiSupport.blockingObjectResponse(exchange, traceId -> service.status(appId, repositoryId));
    }

    @GetMapping("/{repositoryId}/tree")
    public Mono<ApiResponse<Object>> tree(
            @PathVariable String appId,
            @PathVariable String repositoryId,
            @RequestParam(defaultValue = "") String path,
            ServerWebExchange exchange) {
        requireAppAdmin(exchange);
        return RuntimeApiSupport.blockingObjectResponse(
                exchange, traceId -> service.tree(appId, repositoryId, path));
    }

    private AuthPrincipal requireAppAdmin(ServerWebExchange exchange) {
        return AuthWebSupport.requireRole(exchange, Dictionary.ROLE_APP_ADMIN);
    }
}
