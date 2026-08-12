package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.system.management.localclient.LocalClientCredentialApplicationService;
import com.enterprise.testagent.system.management.localclient.LocalClientCredentialResponses;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/** 当前用户唯一 client key 的设置接口；只有 copy 响应短暂携带明文并强制 no-store。 */
@RestController
public class LocalClientCredentialController {

    private static final String BASE =
            "/api/internal/platform/local-opencode-client/credentials/me";
    private final LocalClientCredentialApplicationService service;

    public LocalClientCredentialController(LocalClientCredentialApplicationService service) {
        this.service = service;
    }

    @GetMapping(BASE)
    public ApiResponse<LocalClientCredentialResponses.CredentialView> get(ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(service.get(userId(exchange)), traceId);
    }

    @PostMapping(BASE)
    public ApiResponse<LocalClientCredentialResponses.CredentialView> create(ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(service.create(userId(exchange), traceId), traceId);
    }

    @PostMapping(BASE + "/copy")
    public ApiResponse<LocalClientCredentialResponses.PlaintextKey> copy(ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        exchange.getResponse().getHeaders().setCacheControl(CacheControl.noStore());
        exchange.getResponse().getHeaders().setPragma("no-cache");
        exchange.getResponse().getHeaders().set("Referrer-Policy", "no-referrer");
        return ApiResponse.ok(service.copy(userId(exchange), traceId), traceId);
    }

    @PostMapping(BASE + "/rotate")
    public ApiResponse<LocalClientCredentialResponses.CredentialView> rotate(ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(service.rotate(userId(exchange), traceId), traceId);
    }

    @DeleteMapping(BASE)
    public ApiResponse<RevokedResponse> revoke(ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        service.revoke(userId(exchange), traceId);
        return ApiResponse.ok(new RevokedResponse(true), traceId);
    }

    private static UserId userId(ServerWebExchange exchange) {
        return AuthWebSupport.getAuthPrincipal(exchange).userId();
    }

    public record RevokedResponse(boolean revoked) {
    }
}
