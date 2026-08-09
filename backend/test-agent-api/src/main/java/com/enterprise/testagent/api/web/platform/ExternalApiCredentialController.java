package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.externalapi.ExternalApiScope;
import com.enterprise.testagent.system.management.externalapi.ExternalApiCredentialApplicationService;
import com.enterprise.testagent.system.management.externalapi.ExternalApiCredentialResponses;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
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

/** 系统管理 API Key 入口，全部操作仅限 SUPER_ADMIN。 */
@RestController
@RequestMapping("/api/internal/platform/system-management/api-keys")
public class ExternalApiCredentialController {

    private final ExternalApiCredentialApplicationService service;

    public ExternalApiCredentialController(ExternalApiCredentialApplicationService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    @GetMapping("/scopes")
    public ApiResponse<Object> scopes(ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ok(exchange, service.listScopes());
    }

    @GetMapping
    public ApiResponse<Object> list(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "enabled", required = false) Boolean enabled,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ok(exchange, service.list(keyword, enabled, pageRequest(page, size)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Object>> create(
            @RequestBody ExternalApiCredentialDtos.CreateRequest request,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        boolean enabled = requireEnabled(request.enabled());
        ExternalApiCredentialResponses.Created created = service.create(
                new ExternalApiCredentialResponses.CreateCommand(
                        request.toolCode(), request.toolName(), scopes(request.scopes()), enabled),
                RuntimeApiSupport.traceId(exchange));
        return secret(exchange, created);
    }

    @PatchMapping("/{credentialId}")
    public ApiResponse<Object> update(
            @PathVariable("credentialId") String credentialId,
            @RequestBody ExternalApiCredentialDtos.UpdateRequest request,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ok(exchange, service.update(
                credentialId,
                new ExternalApiCredentialResponses.UpdateCommand(
                        request.toolName(), scopes(request.scopes()), requireEnabled(request.enabled())),
                RuntimeApiSupport.traceId(exchange)));
    }

    @PostMapping("/{credentialId}/reveal")
    public ResponseEntity<ApiResponse<Object>> reveal(
            @PathVariable("credentialId") String credentialId,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return secret(exchange, service.reveal(credentialId));
    }

    @PostMapping("/{credentialId}/rotate")
    public ResponseEntity<ApiResponse<Object>> rotate(
            @PathVariable("credentialId") String credentialId,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return secret(exchange, service.rotate(credentialId, RuntimeApiSupport.traceId(exchange)));
    }

    @DeleteMapping("/{credentialId}")
    public ApiResponse<Object> delete(
            @PathVariable("credentialId") String credentialId,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        service.delete(credentialId, RuntimeApiSupport.traceId(exchange));
        return ok(exchange, null);
    }

    private static PageRequest pageRequest(Integer page, Integer size) {
        int resolvedPage = page == null ? 1 : page;
        int resolvedSize = size == null ? 20 : size;
        if (resolvedPage < 1 || resolvedSize < 1 || resolvedSize > 100) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "分页参数无效");
        }
        return new PageRequest(resolvedPage, resolvedSize);
    }

    private static Set<ExternalApiScope> scopes(Set<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("scopes must not be empty");
        }
        return values.stream().map(ExternalApiScope::fromValue).collect(Collectors.toUnmodifiableSet());
    }

    private static boolean requireEnabled(Boolean enabled) {
        if (enabled == null) {
            throw new IllegalArgumentException("enabled must not be null");
        }
        return enabled;
    }

    private static void requireSuperAdmin(ServerWebExchange exchange) {
        AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
    }

    private static ApiResponse<Object> ok(ServerWebExchange exchange, Object data) {
        return ApiResponse.ok(data, RuntimeApiSupport.traceId(exchange));
    }

    private static ResponseEntity<ApiResponse<Object>> secret(ServerWebExchange exchange, Object data) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(ok(exchange, data));
    }
}
