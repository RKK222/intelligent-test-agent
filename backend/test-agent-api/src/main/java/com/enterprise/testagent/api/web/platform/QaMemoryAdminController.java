package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.memory.MemoryViews.AdminHealthView;
import com.enterprise.testagent.memory.MemoryViews.Page;
import com.enterprise.testagent.memory.MemoryViews.SettingsView;
import com.enterprise.testagent.memory.MemoryViews.WhitelistView;
import com.enterprise.testagent.memory.QaMemoryApplicationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/** 系统管理中的记忆健康、固定 CHAT 模型、Embedding profile、队列和白名单入口。 */
@RestController
@RequestMapping("/api/internal/platform/system-management/memory")
public class QaMemoryAdminController {
    private final QaMemoryApplicationService service;

    public QaMemoryAdminController(QaMemoryApplicationService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public ApiResponse<AdminHealthView> health(ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ok(exchange, service.adminHealth());
    }

    @GetMapping("/settings")
    public ApiResponse<SettingsView> settings(ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ok(exchange, service.settings());
    }

    @PatchMapping("/settings")
    public ApiResponse<SettingsView> updateSettings(
            @RequestBody QaMemoryDtos.SettingsRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = requireSuperAdmin(exchange);
        return ok(exchange, service.updateSettings(
                request.primaryChatModelId(), request.currentRunModelFallbackEnabled(),
                request.expectedVersion(), principal.userId().value()));
    }

    @GetMapping("/whitelist")
    public ApiResponse<Page<WhitelistView>> whitelist(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int size,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ok(exchange, service.listWhitelist(page, size));
    }

    @PostMapping("/whitelist")
    public ApiResponse<WhitelistView> enable(
            @RequestBody QaMemoryDtos.WhitelistRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = requireSuperAdmin(exchange);
        return ok(exchange, service.enableUser(request.userId(), principal.userId().value()));
    }

    @DeleteMapping("/whitelist/{userId}")
    public ApiResponse<Void> disable(@PathVariable String userId, ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        service.disableUser(userId);
        return ok(exchange, null);
    }

    private AuthPrincipal requireSuperAdmin(ServerWebExchange exchange) {
        return AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
    }

    private <T> ApiResponse<T> ok(ServerWebExchange exchange, T data) {
        return ApiResponse.ok(data, RuntimeApiSupport.traceId(exchange));
    }
}
