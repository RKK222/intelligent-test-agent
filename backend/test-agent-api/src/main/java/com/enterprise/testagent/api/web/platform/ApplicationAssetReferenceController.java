package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.workspace.ApplicationAssetReferenceService;
import java.util.Objects;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** 应用资产共享配置入口；读操作按有效成员校验，修改仍限管理员。 */
@RestController
@RequestMapping("/api/internal/platform/workspace-management/applications/{appId}/asset-reference-configurations")
public class ApplicationAssetReferenceController {
    private final ApplicationAssetReferenceService service;

    public ApplicationAssetReferenceController(ApplicationAssetReferenceService service) {
        this.service = Objects.requireNonNull(service);
    }

    @GetMapping
    public Mono<ApiResponse<Object>> list(@PathVariable String appId, ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        boolean superAdmin = AuthWebSupport.hasRole(principal, Dictionary.ROLE_SUPER_ADMIN);
        boolean admin = AuthWebSupport.hasRole(principal, Dictionary.ROLE_APP_ADMIN);
        return RuntimeApiSupport.blockingObjectResponse(exchange, traceId ->
                service.list(appId, principal.userId(), superAdmin, admin));
    }

    @PutMapping("/{repositoryId}")
    public Mono<ApiResponse<Object>> save(@PathVariable String appId, @PathVariable String repositoryId,
                                          @RequestBody SaveRequest request, ServerWebExchange exchange) {
        AuthWebSupport.requireRole(exchange, Dictionary.ROLE_APP_ADMIN);
        return RuntimeApiSupport.blockingObjectResponse(exchange, traceId -> service.save(
                appId, repositoryId, request.directoryPath(), request.merge(),
                request.description(), request.expectedVersion()));
    }

    @DeleteMapping("/{repositoryId}")
    public Mono<ApiResponse<Object>> delete(@PathVariable String appId, @PathVariable String repositoryId,
                                            @RequestParam String directoryPath,
                                            @RequestParam long expectedVersion,
                                            ServerWebExchange exchange) {
        AuthWebSupport.requireRole(exchange, Dictionary.ROLE_APP_ADMIN);
        return RuntimeApiSupport.blockingObjectResponse(exchange, traceId -> {
            service.delete(appId, repositoryId, directoryPath, expectedVersion);
            return null;
        });
    }

    /** expectedVersion=0 表示新建；更新必须提交刚读取到的版本。 */
    public record SaveRequest(String directoryPath, boolean merge, String description, long expectedVersion) { }
}
