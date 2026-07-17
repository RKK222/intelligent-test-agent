package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutCoordinator;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 超管维护公共 Agent/Skill 发布成员；运行拓扑历史记录不再直接决定发布范围。 */
@RestController
public class PublicAgentConfigRolloutManagementController {

    private final PublicAgentConfigRolloutCoordinator coordinator;

    public PublicAgentConfigRolloutManagementController(PublicAgentConfigRolloutCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator must not be null");
    }

    /**
     * 显式退役已经停止 Java 与 manager 的服务器；现有发布任务会把该成员和残留目标置为退役终态。
     */
    @PostMapping("/api/internal/platform/opencode-runtime/management/linux-servers/{linuxServerId}/decommission")
    public Mono<ApiResponse<DecommissionResponse>> decommission(
            @PathVariable String linuxServerId,
            ServerWebExchange exchange) {
        AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        String traceId = RuntimeApiSupport.traceId(exchange);
        LinuxServerId parsed = parseLinuxServerId(linuxServerId);
        return Mono.fromCallable(() -> {
                    coordinator.decommissionServer(parsed.value());
                    return ApiResponse.ok(
                            new DecommissionResponse(parsed.value(), "DECOMMISSIONED"),
                            traceId);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private LinuxServerId parseLinuxServerId(String linuxServerId) {
        try {
            return new LinuxServerId(linuxServerId);
        } catch (IllegalArgumentException exception) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "Linux 服务器 ID 无效",
                    Map.of("linuxServerId", linuxServerId == null ? "" : linuxServerId),
                    exception);
        }
    }

    public record DecommissionResponse(String linuxServerId, String membershipStatus) {
    }
}
