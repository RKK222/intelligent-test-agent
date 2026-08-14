package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.integration.tcds.TcdsCaseMaintenanceService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 当前登录用户维护 TCDS 案例的受控平台入口。 */
@RestController
@RequestMapping("/api/internal/platform/integration/tcds")
public class TcdsCaseMaintenanceController {

    private final TcdsCaseMaintenanceService service;

    public TcdsCaseMaintenanceController(TcdsCaseMaintenanceService service) {
        this.service = service;
    }

    /** 从认证主体取得统一认证号，阻塞的内网请求统一调度到 boundedElastic。 */
    @PostMapping("/test-cases")
    public Mono<ApiResponse<Void>> maintain(
            @Valid @RequestBody TcdsCaseMaintenanceDtos.MaintenanceRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> {
                    service.maintain(request.itemNo(), principal.unifiedAuthId(), request.toInputs(), traceId);
                    return ApiResponse.<Void>ok(null, traceId);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 当前登录用户实时查询 TCDS 任务类型，阻塞请求统一调度到 boundedElastic。 */
    @GetMapping("/task-types")
    public Mono<ApiResponse<List<TcdsCaseMaintenanceDtos.TaskTypeResponse>>> taskTypes(
            ServerWebExchange exchange) {
        AuthWebSupport.getAuthPrincipal(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> ApiResponse.ok(
                        service.getTaskTypes(traceId).stream()
                                .map(TcdsCaseMaintenanceDtos.TaskTypeResponse::from)
                                .toList(),
                        traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
