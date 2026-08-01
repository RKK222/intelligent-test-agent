package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.configuration.management.UiTestPlatformConfigurationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * OpenCode UI 执行 Tool 的窄字段、只读平台配置入口。
 *
 * <p>该接口不使用应用层凭据，只允许受信任 worker 内网直连；公共入口必须由 Nginx 精确拒绝。</p>
 */
@RestController
public class UiTestToolConfigController {

    public static final String ENDPOINT_PATH = "/api/internal/agent/opencode/ui-test-tool/config";

    private final UiTestPlatformConfigurationService configurationService;

    public UiTestToolConfigController(UiTestPlatformConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    /**
     * 返回当前 UI 平台地址。鉴权和数据库读取均离开 WebFlux 事件线程执行；该入口不代理 UI 请求。
     */
    @GetMapping(ENDPOINT_PATH)
    public Mono<ApiResponse<UiTestToolConfigResponse>> current(ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> {
                    var configuration = configurationService.current();
                    return ApiResponse.ok(new UiTestToolConfigResponse(
                            configuration.configured(), configuration.baseUrl()), traceId);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** Tool 配置响应只包含执行所需最小字段。 */
    public record UiTestToolConfigResponse(boolean configured, String baseUrl) {
    }
}
