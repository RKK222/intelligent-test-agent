package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.localclient.LocalClientVersionModels;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientUpdateCoordinator;
import com.enterprise.testagent.system.management.localclient.LocalClientReleaseCatalogClient;
import com.enterprise.testagent.system.management.localclient.LocalClientVersionPolicyApplicationService;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/** 超级管理员本地客户端发布、策略及 rollout 控制入口。 */
@RestController
@RequestMapping("/api/internal/platform/local-opencode-client/version-management")
public class LocalClientVersionManagementController {

    private final LocalClientReleaseCatalogClient catalog;
    private final LocalClientVersionPolicyApplicationService policies;
    private final LocalClientUpdateCoordinator updates;

    public LocalClientVersionManagementController(
            LocalClientReleaseCatalogClient catalog,
            LocalClientVersionPolicyApplicationService policies,
            LocalClientUpdateCoordinator updates) {
        this.catalog = Objects.requireNonNull(catalog);
        this.policies = Objects.requireNonNull(policies);
        this.updates = Objects.requireNonNull(updates);
    }

    @PostMapping("/releases/sync")
    public ApiResponse<LocalClientVersionManagementDtos.SyncView> sync(ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        LocalClientReleaseCatalogClient.SyncResult result = catalog.sync();
        return ApiResponse.ok(new LocalClientVersionManagementDtos.SyncView(
                result.synced(), result.unchanged(), result.discovered()), traceId(exchange));
    }

    @GetMapping("/releases")
    public ApiResponse<List<LocalClientVersionManagementDtos.ReleaseView>> releases(ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ApiResponse.ok(
                policies.releases().stream().map(LocalClientVersionManagementDtos::release).toList(),
                traceId(exchange));
    }

    @GetMapping("/global-policy")
    public ApiResponse<LocalClientVersionManagementDtos.GlobalPolicyView> globalPolicy(ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ApiResponse.ok(
                LocalClientVersionManagementDtos.globalPolicy(policies.globalPolicy()), traceId(exchange));
    }

    @PutMapping("/global-policy")
    public ApiResponse<LocalClientVersionManagementDtos.GlobalPolicyView> setGlobalPolicy(
            @RequestBody LocalClientVersionManagementDtos.SetTargetVersionRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal actor = requireSuperAdmin(exchange);
        return ApiResponse.ok(
                LocalClientVersionManagementDtos.globalPolicy(
                        policies.setGlobalTarget(request.targetVersion(), actor.userId())),
                traceId(exchange));
    }

    @GetMapping("/user-policies")
    public ApiResponse<List<LocalClientVersionManagementDtos.UserPolicyView>> userPolicies(
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ApiResponse.ok(
                policies.userPolicies().stream()
                        .filter(policy -> policy.targetVersion() != null)
                        .map(LocalClientVersionManagementDtos::userPolicy)
                        .toList(),
                traceId(exchange));
    }

    @PutMapping("/user-policies/{userId}")
    public ApiResponse<LocalClientVersionManagementDtos.UserPolicyView> setUserPolicy(
            @PathVariable String userId,
            @RequestBody LocalClientVersionManagementDtos.SetTargetVersionRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal actor = requireSuperAdmin(exchange);
        return ApiResponse.ok(
                LocalClientVersionManagementDtos.userPolicy(
                        policies.setUserTarget(userId(userId), request.targetVersion(), actor.userId())),
                traceId(exchange));
    }

    @DeleteMapping("/user-policies/{userId}")
    public ApiResponse<LocalClientVersionManagementDtos.UserPolicyView> clearUserPolicy(
            @PathVariable String userId,
            ServerWebExchange exchange) {
        AuthPrincipal actor = requireSuperAdmin(exchange);
        return ApiResponse.ok(
                LocalClientVersionManagementDtos.userPolicy(
                        policies.clearUserTarget(userId(userId), actor.userId())),
                traceId(exchange));
    }

    @PostMapping("/rollouts")
    public ApiResponse<LocalClientVersionManagementDtos.RolloutView> createRollout(
            @RequestBody LocalClientVersionManagementDtos.CreateRolloutRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal actor = requireSuperAdmin(exchange);
        LocalClientVersionModels.RolloutScope scope = scope(request.scope());
        UserId requestedUserId = scope == LocalClientVersionModels.RolloutScope.USER
                ? userId(request.userId()) : null;
        LocalClientUpdateCoordinator.RolloutResult result = updates.createRollout(
                scope, requestedUserId, actor.userId(), traceId(exchange));
        return ApiResponse.ok(
                LocalClientVersionManagementDtos.rollout(result.rollout(), result.attemptCount()),
                traceId(exchange));
    }

    @GetMapping("/rollouts")
    public ApiResponse<List<LocalClientVersionManagementDtos.RolloutView>> rollouts(ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ApiResponse.ok(
                updates.rollouts().stream()
                        .map(rollout -> LocalClientVersionManagementDtos.rollout(rollout, null))
                        .toList(),
                traceId(exchange));
    }

    @GetMapping("/rollouts/{rolloutId}/attempts")
    public ApiResponse<List<LocalClientVersionManagementDtos.AttemptView>> attempts(
            @PathVariable String rolloutId,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ApiResponse.ok(
                updates.attempts(rolloutId).stream()
                        .map(LocalClientVersionManagementDtos::attempt)
                        .toList(),
                traceId(exchange));
    }

    private static LocalClientVersionModels.RolloutScope scope(String value) {
        try {
            return LocalClientVersionModels.RolloutScope.valueOf(required(value, "scope").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw validation("rollout scope 仅支持 ALL_ONLINE 或 USER", exception);
        }
    }

    private static UserId userId(String value) {
        try {
            return new UserId(required(value, "userId"));
        } catch (IllegalArgumentException exception) {
            throw validation("userId 无效", exception);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw validation(field + " 不能为空", null);
        }
        return value.trim();
    }

    private static PlatformException validation(String message, Throwable cause) {
        return new PlatformException(ErrorCode.VALIDATION_ERROR, message, java.util.Map.of(), cause);
    }

    private static AuthPrincipal requireSuperAdmin(ServerWebExchange exchange) {
        return AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
    }

    private static String traceId(ServerWebExchange exchange) {
        return RuntimeApiSupport.traceId(exchange);
    }
}
