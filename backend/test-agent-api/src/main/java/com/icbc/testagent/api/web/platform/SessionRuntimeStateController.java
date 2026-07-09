package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.RuntimeApiSupport;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.opencode.runtime.session.SessionRuntimeStateApplicationService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 用户级会话运行状态 Controller，供历史入口展示后台运行计数和 ask 提醒。
 */
@RestController
public class SessionRuntimeStateController {

    private static final String SNAPSHOT_EVENT = "session-runtime.snapshot";
    private static final String UPDATED_EVENT = "session-runtime.updated";

    private final SessionRuntimeStateApplicationService service;

    public SessionRuntimeStateController(SessionRuntimeStateApplicationService service) {
        this.service = service;
    }

    /**
     * 查询当前登录用户的会话运行态快照；阻塞式仓储查询 offload 到 boundedElastic。
     */
    @GetMapping("/api/internal/platform/opencode-runtime/sessions/runtime-state")
    public Mono<ApiResponse<RuntimeDtos.SessionRuntimeStateResponse>> getRuntimeState(ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        return Mono.fromCallable(() -> ApiResponse.ok(
                        RuntimeDtos.SessionRuntimeStateResponse.from(service.snapshot(userId)),
                        traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * fetch SSE 状态通道。首帧为 snapshot，后续 run/question 触发或兜底轮询变更时推送 updated。
     */
    @GetMapping(
            value = "/api/internal/platform/opencode-runtime/sessions/runtime-state/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<RuntimeDtos.SessionRuntimeStateResponse>> streamRuntimeState(
            ServerWebExchange exchange) {
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        return service.stream(userId)
                .map(RuntimeDtos.SessionRuntimeStateResponse::from)
                .index()
                .map(tuple -> {
                    RuntimeDtos.SessionRuntimeStateResponse data = tuple.getT2();
                    return ServerSentEvent.builder(data)
                            .event(tuple.getT1() == 0 ? SNAPSHOT_EVENT : UPDATED_EVENT)
                            .id(data.generatedAt() == null ? null : data.generatedAt().toString())
                            .build();
                });
    }
}
