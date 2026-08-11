package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.notification.UserNotificationId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.notification.UserNotificationApplicationService;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

/** 工作台通用站内通知 HTTP 与用户级 SSE 入口。 */
@RestController
public class UserNotificationController {

    private static final String BASE = "/api/internal/platform/notification-center/notifications";
    private final UserNotificationApplicationService service;

    public UserNotificationController(UserNotificationApplicationService service) {
        this.service = service;
    }

    /** 分页读取当前用户通知；unreadCount 始终表示全部通知的权威未读数。 */
    @GetMapping(BASE)
    public ApiResponse<UserNotificationDtos.UserNotificationPageResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        UserId actor = AuthWebSupport.getAuthPrincipal(exchange).userId();
        return ApiResponse.ok(UserNotificationDtos.UserNotificationPageResponse.from(
                service.list(actor, unreadOnly, RuntimeApiSupport.pageRequest(page, size))), traceId);
    }

    /** 通用通知已读入口；分享通知的工作台点击仍以分享访问成功为最终已读事实。 */
    @PostMapping(BASE + "/{notificationId}/read")
    public ApiResponse<UserNotificationDtos.MarkReadResponse> markRead(
            @PathVariable String notificationId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        UserId actor = AuthWebSupport.getAuthPrincipal(exchange).userId();
        UserNotificationId requested = new UserNotificationId(notificationId);
        service.markRead(actor, requested, traceId);
        return ApiResponse.ok(new UserNotificationDtos.MarkReadResponse(requested.value(), true), traceId);
    }

    /** 用户级通知变化流；正文不经广播或 SSE 传输，客户端收到信号后读取分页接口。 */
    @GetMapping(value = BASE + "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<UserNotificationDtos.UserNotificationStreamResponse>> events(
            ServerWebExchange exchange) {
        UserId actor = AuthWebSupport.getAuthPrincipal(exchange).userId();
        Flux<ServerSentEvent<UserNotificationDtos.UserNotificationStreamResponse>> states = service.stream(actor)
                .map(update -> ServerSentEvent.builder(UserNotificationDtos.UserNotificationStreamResponse.from(update))
                        .event(update.changeType().name().equals("SNAPSHOT")
                                ? "user-notification.snapshot"
                                : "user-notification.updated")
                        .id(update.generatedAt().toString())
                        .build());
        return states.publish(shared -> shared.mergeWith(
                Flux.interval(Duration.ofSeconds(25))
                        .map(ignored -> ServerSentEvent
                                .<UserNotificationDtos.UserNotificationStreamResponse>builder()
                                .comment("heartbeat")
                                .build())
                        .takeUntilOther(shared.ignoreElements())));
    }
}
