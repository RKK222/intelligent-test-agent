package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.integration.toolbox.ToolboxCatalogService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 所有登录用户可访问的离线工具目录与点击上报入口。 */
@RestController
@RequestMapping("/api/internal/platform/toolbox")
public class ToolboxController {

    private final ToolboxCatalogService service;

    public ToolboxController(ToolboxCatalogService service) {
        this.service = service;
    }

    /** 查询完整离线目录与实时热门排名，不要求任何平台角色。 */
    @GetMapping("/tools")
    public Mono<ApiResponse<ToolboxDtos.CatalogResponse>> tools(ServerWebExchange exchange) {
        AuthWebSupport.getAuthPrincipal(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> ApiResponse.ok(ToolboxDtos.CatalogResponse.from(service.catalog()), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 使用服务端认证身份和 traceId 记录点击，客户端只提供幂等 eventId。 */
    @PostMapping("/tools/{toolId}/clicks")
    public Mono<ApiResponse<ToolboxDtos.ClickResponse>> recordClick(
            @PathVariable String toolId,
            @Valid @RequestBody ToolboxDtos.ClickRequest request,
            ServerWebExchange exchange) {
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> ApiResponse.ok(
                        ToolboxDtos.ClickResponse.from(
                                service.recordClick(toolId, request.eventId(), userId, traceId)),
                        traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
