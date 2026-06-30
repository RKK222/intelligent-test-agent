package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import java.util.Objects;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 用户 opencode 进程 HTTP 后端路由过滤器。
 *
 * <p>认证过滤器写入用户主体后，本过滤器根据用户 ACTIVE binding 判断请求是否应转发到
 * binding 所属服务器的 Java；目标 Java 仍会执行同一套鉴权和业务校验。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 25)
class UserOpencodeBackendRoutingWebFilter implements WebFilter {

    static final String ROUTED_HEADER = "X-Test-Agent-Backend-Routed";

    private final UserOpencodeBackendRoutingService routingService;

    UserOpencodeBackendRoutingWebFilter(UserOpencodeBackendRoutingService routingService) {
        this.routingService = Objects.requireNonNull(routingService, "routingService must not be null");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return AuthWebSupport.getOptionalAuthPrincipal(exchange)
                .flatMap(principal -> routeTarget(exchange, principal))
                .map(target -> routingService.forward(exchange, target.principal(), target.linuxServerId()))
                .orElseGet(() -> chain.filter(exchange));
    }

    private java.util.Optional<RouteTarget> routeTarget(ServerWebExchange exchange, AuthPrincipal principal) {
        return routingService.targetLinuxServerId(exchange, principal)
                .map(linuxServerId -> new RouteTarget(principal, linuxServerId));
    }

    private record RouteTarget(AuthPrincipal principal, String linuxServerId) {
    }
}
