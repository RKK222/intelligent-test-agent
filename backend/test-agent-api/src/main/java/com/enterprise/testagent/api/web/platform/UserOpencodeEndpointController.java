package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.runtime.RuntimeKind;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAvailability;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessStatusResponse;
import com.enterprise.testagent.system.management.localclient.LocalClientInstanceApplicationService;
import com.enterprise.testagent.system.management.localclient.LocalClientInstanceResponses;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 聚合当前用户的服务端 OpenCode 与全部本地客户端实例，供头像菜单统一展示。 */
@RestController
public class UserOpencodeEndpointController {

    private static final Map<String, Boolean> SERVER_CAPABILITIES = serverCapabilities();

    private final UserOpencodeProcessAssignmentService processAssignmentService;
    private final LocalClientInstanceApplicationService localClientInstanceService;

    public UserOpencodeEndpointController(
            UserOpencodeProcessAssignmentService processAssignmentService,
            LocalClientInstanceApplicationService localClientInstanceService) {
        this.processAssignmentService = Objects.requireNonNull(processAssignmentService);
        this.localClientInstanceService = Objects.requireNonNull(localClientInstanceService);
    }

    /** 只读查询不会启动任一进程；服务端实例始终排在本地实例之前。 */
    @GetMapping("/api/internal/agent/{agentId}/opencode-endpoints/me")
    public Mono<ApiResponse<List<EndpointView>>> list(
            @PathVariable String agentId,
            ServerWebExchange exchange) {
        String normalizedAgentId = agentId == null ? "" : agentId.trim().toLowerCase();
        if (!"opencode".equals(normalizedAgentId)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "当前只支持 OpenCode 实例列表");
        }
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> {
                    List<EndpointView> endpoints = new ArrayList<>();
                    endpoints.add(server(processAssignmentService.status(userId, normalizedAgentId, traceId)));
                    localClientInstanceService.list(userId).stream().map(EndpointView::local).forEach(endpoints::add);
                    return ApiResponse.ok(List.copyOf(endpoints), traceId);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private static EndpointView server(UserOpencodeProcessStatusResponse response) {
        boolean online = response.status() == UserOpencodeProcessAvailability.READY;
        return new EndpointView(
                RuntimeKind.SERVER_PROCESS,
                response.processId() == null ? "server-opencode" : response.processId(),
                "服务端 OpenCode",
                online,
                response.serviceStatus().name(),
                null,
                null,
                null,
                null,
                0,
                List.of(),
                response.backendJavaServerIp(),
                response.port(),
                online,
                response.checkedAt(),
                response.linuxServerId(),
                response.containerId(),
                response.serviceAddress(),
                SERVER_CAPABILITIES);
    }

    private static Map<String, Boolean> serverCapabilities() {
        Map<String, Boolean> values = new LinkedHashMap<>();
        values.put("chat", true);
        values.put("fileManagement", true);
        values.put("nightExecution", true);
        values.put("terminal", true);
        values.put("gitPublish", true);
        values.put("agentConfig", true);
        values.put("attachments", true);
        values.put("collaboration", true);
        return Map.copyOf(values);
    }

    /** 统一实例投影不会包含 client key、模型授权或本地根目录。 */
    public record EndpointView(
            RuntimeKind runtimeKind,
            String endpointId,
            String displayName,
            boolean online,
            String processStatus,
            String platform,
            String architecture,
            String clientVersion,
            String opencodeVersion,
            long connectionGeneration,
            List<String> reportedAddresses,
            String observedRemoteAddress,
            Integer port,
            boolean healthy,
            Instant lastHeartbeatAt,
            String linuxServerId,
            String containerId,
            String serviceAddress,
            Map<String, Boolean> capabilities) {

        static EndpointView local(LocalClientInstanceResponses.InstanceView instance) {
            return new EndpointView(
                    RuntimeKind.LOCAL_CLIENT,
                    instance.clientInstanceId(),
                    instance.clientName(),
                    instance.online(),
                    instance.processStatus(),
                    instance.platform(),
                    instance.architecture(),
                    instance.clientVersion(),
                    instance.opencodeVersion(),
                    instance.connectionGeneration(),
                    instance.reportedAddresses(),
                    instance.observedRemoteAddress(),
                    instance.opencodePort(),
                    instance.opencodeHealthy(),
                    instance.lastHeartbeatAt(),
                    null,
                    null,
                    null,
                    instance.capabilities());
        }
    }
}
