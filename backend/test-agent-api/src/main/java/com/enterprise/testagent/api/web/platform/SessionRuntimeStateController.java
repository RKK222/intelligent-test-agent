package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.session.SessionRuntimeStateApplicationService;
import java.time.Duration;
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
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(25);

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
     * fetch SSE 状态通道。首帧为 snapshot，后续变更推送 updated；空闲期发送注释心跳维持代理连接。
     */
    @GetMapping(
            value = "/api/internal/platform/opencode-runtime/sessions/runtime-state/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<RuntimeDtos.SessionRuntimeStateResponse>> streamRuntimeState(
            ServerWebExchange exchange) {
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        Flux<ServerSentEvent<RuntimeDtos.SessionRuntimeStateResponse>> stateEvents = service.stream(userId)
                .map(RuntimeDtos.SessionRuntimeStateResponse::from)
                .index()
                .map(tuple -> {
                    RuntimeDtos.SessionRuntimeStateResponse data = tuple.getT2();
                    return ServerSentEvent.builder(data)
                            .event(tuple.getT1() == 0 ? SNAPSHOT_EVENT : UPDATED_EVENT)
                            .id(data.generatedAt() == null ? null : data.generatedAt().toString())
                            .build();
                });
        return stateEvents.mergeWith(heartbeat(HEARTBEAT_INTERVAL));
    }

    /**
     * 心跳使用 SSE comment，不产生 data/event/id，旧客户端会按协议忽略且不会误更新业务状态。
     */
    static Flux<ServerSentEvent<RuntimeDtos.SessionRuntimeStateResponse>> heartbeat(Duration interval) {
        if (interval == null || interval.isZero() || interval.isNegative()) {
            return Flux.error(new IllegalArgumentException("heartbeat interval must be positive"));
        }
        return Flux.interval(interval)
                .map(ignored -> ServerSentEvent.<RuntimeDtos.SessionRuntimeStateResponse>builder()
                        .comment("heartbeat")
                        .build());
    }
}
