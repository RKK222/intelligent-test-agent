package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.system.management.localclient.LocalClientRolloutApplicationService;
import com.enterprise.testagent.system.management.localclient.LocalClientRolloutResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Objects;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/** 超级管理员维护本地客户端相关功能可见性的用户灰度名单。 */
@RestController
@RequestMapping("/api/internal/platform/local-opencode-client/admin/rollout-users")
public class LocalClientRolloutAdminController {

    private final LocalClientRolloutApplicationService service;

    public LocalClientRolloutAdminController(LocalClientRolloutApplicationService service) {
        this.service = Objects.requireNonNull(service);
    }

    @GetMapping
    public ApiResponse<PageResponse<LocalClientRolloutResponses.RolloutUserView>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ok(exchange, service.list(new PageRequest(page, size)));
    }

    @PostMapping
    public ApiResponse<LocalClientRolloutResponses.RolloutUserView> enable(
            @Valid @RequestBody RolloutUserRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = requireSuperAdmin(exchange);
        return ok(exchange, service.enable(new UserId(request.userId().trim()), principal.userId()));
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> disable(@PathVariable String userId, ServerWebExchange exchange) {
        AuthPrincipal principal = requireSuperAdmin(exchange);
        service.disable(new UserId(userId.trim()), principal.userId());
        return ok(exchange, null);
    }

    private static AuthPrincipal requireSuperAdmin(ServerWebExchange exchange) {
        return AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
    }

    private static <T> ApiResponse<T> ok(ServerWebExchange exchange, T data) {
        return ApiResponse.ok(data, RuntimeApiSupport.traceId(exchange));
    }

    public record RolloutUserRequest(@NotBlank String userId) {
    }
}
