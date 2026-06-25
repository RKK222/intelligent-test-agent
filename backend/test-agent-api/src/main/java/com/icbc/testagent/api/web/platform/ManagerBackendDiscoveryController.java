package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.api.web.common.RuntimeApiSupport;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerBackendDiscoveryService;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * opencode-manager 后端实例发现入口，使用独立 manager token 保护内部控制面。
 */
@RestController
public class ManagerBackendDiscoveryController {

    private final ManagerBackendDiscoveryService discoveryService;
    private final ManagerControlSettings settings;

    /**
     * 注入 discovery 服务和控制面鉴权配置。
     */
    public ManagerBackendDiscoveryController(
            ManagerBackendDiscoveryService discoveryService,
            ManagerControlSettings settings) {
        this.discoveryService = Objects.requireNonNull(discoveryService, "discoveryService must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
    }

    /**
     * 返回所有可被 manager 直连的后端实例 WebSocket 地址。
     */
    @GetMapping("/api/internal/platform/opencode-runtime/manager-backends")
    public Mono<ApiResponse<List<RuntimeDtos.ManagerBackendResponse>>> discover(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            ServerWebExchange exchange) {
        if (!settings.tokenMatches(authorization)) {
            throw new PlatformException(ErrorCode.UNAUTHENTICATED, "manager token 无效");
        }
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> ApiResponse.ok(
                        discoveryService.discover(traceId).stream()
                                .map(RuntimeDtos.ManagerBackendResponse::from)
                                .toList(),
                        traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
