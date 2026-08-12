package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionRoute;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.enterprise.testagent.opencode.runtime.process.LocalClientLifecycleResult;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessStartupService;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessStatusQueryService;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessStopService;
import com.enterprise.testagent.system.management.localclient.LocalClientInstanceApplicationService;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/** 用户显式控制本地 OpenCode；任意 Java 入口都精确转发到持有当前 generation 的 Java。 */
@RestController
public class LocalClientCommandController {

    private static final String PATH =
            "/api/internal/platform/local-opencode-client/instances/{clientInstanceId}/opencode/commands";

    private final LocalClientInstanceApplicationService instanceService;
    private final BackendJavaRouteResolver routeResolver;
    private final BackendHttpForwarder forwarder;
    private final OpencodeProcessStartupService startupService;
    private final OpencodeProcessStopService stopService;
    private final OpencodeProcessStatusQueryService statusQueryService;

    public LocalClientCommandController(
            LocalClientInstanceApplicationService instanceService,
            BackendJavaRouteResolver routeResolver,
            BackendHttpForwarder forwarder,
            OpencodeProcessStartupService startupService,
            OpencodeProcessStopService stopService,
            OpencodeProcessStatusQueryService statusQueryService) {
        this.instanceService = Objects.requireNonNull(instanceService);
        this.routeResolver = Objects.requireNonNull(routeResolver);
        this.forwarder = Objects.requireNonNull(forwarder);
        this.startupService = Objects.requireNonNull(startupService);
        this.stopService = Objects.requireNonNull(stopService);
        this.statusQueryService = Objects.requireNonNull(statusQueryService);
    }

    @PostMapping(PATH)
    public ApiResponse<CommandResponse> command(
            @PathVariable String clientInstanceId,
            @RequestBody CommandRequest request,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        LocalClientInstanceId instanceId = new LocalClientInstanceId(clientInstanceId);
        LocalClientConnectionRoute route = instanceService.requireOwnedOnline(
                AuthWebSupport.getAuthPrincipal(exchange).userId(), instanceId);
        BackendJavaProcess backend = routeResolver.requireBackend(route.backendProcessId());
        if (!routeResolver.isCurrent(backend.backendProcessId())) {
            if ("true".equalsIgnoreCase(exchange.getRequest().getHeaders()
                    .getFirst(BackendHttpForwarder.ROUTED_HEADER))) {
                throw new PlatformException(ErrorCode.CONFLICT, "本地客户端连接在转发期间已迁移");
            }
            return forwarder.forwardTyped(
                    exchange,
                    backend,
                    request,
                    new TypeReference<ApiResponse<CommandResponse>>() { });
        }
        String action = request == null || request.action() == null
                ? ""
                : request.action().trim().toUpperCase(Locale.ROOT);
        LocalClientLifecycleResult result = switch (action) {
            case "START" -> startupService.startLocalClientAndVerify(
                    instanceId, route.connectionGeneration(), traceId);
            case "RESTART" -> startupService.restartLocalClientAndVerify(
                    instanceId, route.connectionGeneration(), traceId);
            case "STOP" -> stopService.stopLocalClientAndVerify(
                    instanceId, route.connectionGeneration(), traceId);
            case "STATUS" -> statusQueryService.queryLocalClient(
                    instanceId, route.connectionGeneration(), traceId);
            default -> throw new PlatformException(ErrorCode.VALIDATION_ERROR, "本地 OpenCode 命令无效");
        };
        return ApiResponse.ok(CommandResponse.from(result), traceId);
    }

    public record CommandRequest(String action) {
    }

    public record CommandResponse(
            boolean success,
            String processStatus,
            Long processId,
            Instant processStartedAt,
            Integer opencodePort,
            boolean opencodeHealthy,
            String executable,
            String message) {

        static CommandResponse from(LocalClientLifecycleResult result) {
            return new CommandResponse(
                    result.success(),
                    result.processStatus().name(),
                    result.processId(),
                    result.processStartedAt(),
                    result.opencodePort(),
                    result.opencodeHealthy(),
                    result.executable(),
                    result.message());
        }
    }
}
