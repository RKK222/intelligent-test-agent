package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.enterprise.testagent.workspace.AppSourceApplicationService;
import java.util.List;
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
import reactor.core.publisher.Mono;

/**
 * 应用源码 HTTP 入口，只负责认证主体、当前用户进程服务器、traceId 和 DTO 转换。
 *
 * <p>所有成员关系、仓库关联、生命周期与 Git 校验都委托给业务服务；Controller 不查询 Repository、
 * Git 或文件系统。
 */
@RestController
@RequestMapping("/api/internal/platform/workspace-management")
public class AppSourceController {

    private final AppSourceApplicationService service;
    private final UserOpencodeProcessAssignmentService processAssignments;

    public AppSourceController(
            AppSourceApplicationService service,
            UserOpencodeProcessAssignmentService processAssignments) {
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.processAssignments = Objects.requireNonNull(processAssignments, "processAssignments must not be null");
    }

    @GetMapping("/applications/{appId}/app-source-repositories")
    public Mono<ApiResponse<Object>> repositories(
            @PathVariable String appId,
            ServerWebExchange exchange) {
        AuthPrincipal principal = principal(exchange);
        return RuntimeApiSupport.blockingObjectResponse(exchange, traceId -> service
                .listRepositories(appId, principal.userId(), appAdmin(principal), linuxServerId(principal, traceId))
                .stream()
                .map(AppSourceDtos::repository)
                .toList());
    }

    @GetMapping("/applications/{appId}/app-source-repositories/{repositoryId}/branches")
    public Mono<ApiResponse<Object>> branches(
            @PathVariable String appId,
            @PathVariable String repositoryId,
            ServerWebExchange exchange) {
        AuthPrincipal principal = principal(exchange);
        return RuntimeApiSupport.blockingObjectResponse(
                exchange,
                traceId -> service.listBranches(appId, repositoryId, principal.userId()));
    }

    @GetMapping("/applications/{appId}/app-source-repositories/{repositoryId}/tree")
    public Mono<ApiResponse<Object>> tree(
            @PathVariable String appId,
            @PathVariable String repositoryId,
            @RequestParam String branch,
            @RequestParam(defaultValue = ".") String path,
            ServerWebExchange exchange) {
        AuthPrincipal principal = principal(exchange);
        return RuntimeApiSupport.blockingObjectResponse(
                exchange,
                traceId -> service.listTree(appId, repositoryId, branch, path, principal.userId())
                        .stream()
                        .map(AppSourceDtos::treeNode)
                        .toList());
    }

    @PostMapping("/applications/{appId}/app-source-repositories/{repositoryId}/materializations")
    public Mono<ApiResponse<Object>> materialize(
            @PathVariable String appId,
            @PathVariable String repositoryId,
            @RequestBody AppSourceDtos.MaterializationRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = principal(exchange);
        boolean appAdmin = appAdmin(principal);
        return RuntimeApiSupport.blockingObjectResponse(exchange, traceId -> {
            var operation = service.materialize(
                    appId,
                    repositoryId,
                    new AppSourceApplicationService.MaterializationCommand(
                            request.operationId(),
                            request.expectedGeneration(),
                            request.branch(),
                            request.expectedTreeCommit(),
                            request.selectedPaths() == null
                                    ? List.of()
                                    : request.selectedPaths().stream()
                                            .map(path -> new AppSourceApplicationService.SelectedPathCommand(
                                                    path.path(), path.type()))
                                            .toList(),
                            request.purpose(),
                            request.retentionHours(),
                            Boolean.TRUE.equals(request.confirmReplace())),
                    principal.userId(),
                    appAdmin,
                    traceId);
            return AppSourceDtos.operation(
                    service.getOperation(operation.operationId(), principal.userId(), appAdmin));
        });
    }

    @PostMapping("/applications/{appId}/app-source-repositories/{repositoryId}/replica-retries")
    public Mono<ApiResponse<Object>> retry(
            @PathVariable String appId,
            @PathVariable String repositoryId,
            @RequestBody AppSourceDtos.RetryRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = principal(exchange);
        boolean appAdmin = appAdmin(principal);
        return RuntimeApiSupport.blockingObjectResponse(exchange, traceId -> {
            var operation = service.retry(
                    appId,
                    repositoryId,
                    new AppSourceApplicationService.RetryCommand(
                            request.operationId(), request.expectedGeneration()),
                    principal.userId(),
                    appAdmin,
                    traceId);
            return AppSourceDtos.operation(
                    service.getOperation(operation.operationId(), principal.userId(), appAdmin));
        });
    }

    @PostMapping("/applications/{appId}/app-source-repositories/{repositoryId}/open")
    public Mono<ApiResponse<Object>> open(
            @PathVariable String appId,
            @PathVariable String repositoryId,
            @RequestBody AppSourceDtos.OpenRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = principal(exchange);
        return RuntimeApiSupport.blockingObjectResponse(exchange, traceId -> AppSourceDtos.open(service.open(
                appId,
                repositoryId,
                request.generation(),
                principal.userId(),
                linuxServerId(principal, traceId))));
    }

    @GetMapping("/recent-app-source")
    public Mono<ApiResponse<Object>> recent(ServerWebExchange exchange) {
        AuthPrincipal principal = principal(exchange);
        return RuntimeApiSupport.blockingObjectResponse(exchange, traceId -> service
                .recent(principal.userId(), linuxServerId(principal, traceId))
                .map(AppSourceDtos::open)
                .orElse(null));
    }

    @DeleteMapping("/recent-app-source")
    public Mono<ApiResponse<Object>> clearRecent(ServerWebExchange exchange) {
        AuthPrincipal principal = principal(exchange);
        return RuntimeApiSupport.blockingObjectResponse(exchange, traceId -> {
            service.clearRecent(principal.userId(), linuxServerId(principal, traceId));
            return null;
        });
    }

    private AuthPrincipal principal(ServerWebExchange exchange) {
        return AuthWebSupport.getAuthPrincipal(exchange);
    }

    private boolean appAdmin(AuthPrincipal principal) {
        return AuthWebSupport.hasRole(principal, Dictionary.ROLE_APP_ADMIN);
    }

    private String linuxServerId(AuthPrincipal principal, String traceId) {
        return processAssignments
                .requireReadyProcess(principal.userId(), "opencode", traceId)
                .linuxServerId();
    }
}
