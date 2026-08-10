package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.notification.UserNotificationActionType;
import com.enterprise.testagent.domain.notification.UserNotificationId;
import com.enterprise.testagent.domain.notification.UserNotificationStatus;
import com.enterprise.testagent.domain.notification.UserNotificationType;
import com.enterprise.testagent.domain.notification.UserNotificationView;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.notification.UserNotificationApplicationService;
import com.enterprise.testagent.notification.UserNotificationChangeType;
import com.enterprise.testagent.notification.UserNotificationPage;
import com.enterprise.testagent.notification.UserNotificationStreamUpdate;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/** 通知中心分页、用户隔离、幂等已读和 SSE 事件名契约测试。 */
class UserNotificationControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-10T09:00:00Z");
    private static final UserId USER = new UserId("usr_notification_controller");
    private static final UserId OWNER = new UserId("usr_notification_owner");
    private static final UserNotificationId NOTIFICATION = new UserNotificationId("ntf_controller");
    private static final String TRACE_ID = "trace_notification_controller";

    @Test
    void listsOnlyCurrentUserProjectionWithUnreadCountAndNoInternalDedupKey() {
        UserNotificationApplicationService service = mock(UserNotificationApplicationService.class);
        when(service.list(
                eq(USER), eq(true), argThat(page -> page.page() == 2 && page.size() == 10)))
                .thenReturn(new UserNotificationPage(
                        new PageResponse<>(List.of(view()), 2, 10, 11), 3));

        authenticatedClient(service).get()
                .uri("/api/internal/platform/notification-center/notifications?page=2&size=10&unreadOnly=true")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.page").isEqualTo(2)
                .jsonPath("$.data.total").isEqualTo(11)
                .jsonPath("$.data.unreadCount").isEqualTo(3)
                .jsonPath("$.data.items[0].notificationId").isEqualTo(NOTIFICATION.value())
                .jsonPath("$.data.items[0].actionType").isEqualTo("SESSION_SHARE")
                .jsonPath("$.data.items[0].actionTargetId").isEqualTo("shr_controller")
                .jsonPath("$.data.items[0].actionAvailable").isEqualTo(true)
                .jsonPath("$.data.items[0].dedupKey").doesNotExist();
    }

    @Test
    void markReadUsesAuthenticatedRecipientAndUnauthenticatedRequestsAreRejected() {
        UserNotificationApplicationService service = mock(UserNotificationApplicationService.class);

        authenticatedClient(service).post()
                .uri("/api/internal/platform/notification-center/notifications/ntf_controller/read")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.notificationId").isEqualTo(NOTIFICATION.value())
                .jsonPath("$.data.read").isEqualTo(true);
        verify(service).markRead(USER, NOTIFICATION, TRACE_ID);

        WebTestClient.bindToController(new UserNotificationController(service))
                .webFilter(new TraceIdWebFilter())
                .controllerAdvice(new GlobalExceptionHandler())
                .build()
                .get()
                .uri("/api/internal/platform/notification-center/notifications")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void streamUsesSnapshotAndUpdatedEventNames() {
        UserNotificationApplicationService service = mock(UserNotificationApplicationService.class);
        when(service.stream(USER)).thenReturn(Flux.just(
                new UserNotificationStreamUpdate(
                        UserNotificationChangeType.SNAPSHOT, null, 2, NOW),
                new UserNotificationStreamUpdate(
                        UserNotificationChangeType.CREATED, NOTIFICATION, 3, NOW.plusSeconds(1))));
        UserNotificationController controller = new UserNotificationController(service);
        MockServerWebExchange exchange = authenticatedExchange(
                MockServerHttpRequest.get(
                        "/api/internal/platform/notification-center/notifications/events")
                        .header("X-Trace-Id", TRACE_ID)
                        .build());

        StepVerifier.create(controller.events(exchange).filter(event -> event.data() != null).take(2))
                .assertNext(event -> {
                    assertThat(event.event()).isEqualTo("user-notification.snapshot");
                    assertThat(event.data().unreadCount()).isEqualTo(2);
                })
                .assertNext(event -> {
                    assertThat(event.event()).isEqualTo("user-notification.updated");
                    assertThat(event.data().changeType()).isEqualTo("CREATED");
                    assertThat(event.data().notificationId()).isEqualTo(NOTIFICATION.value());
                })
                .verifyComplete();
    }

    private static UserNotificationView view() {
        return new UserNotificationView(
                NOTIFICATION,
                UserNotificationType.SESSION_SHARED,
                OWNER,
                "会话所属人 向你分享了对话",
                "通知中心接口测试 · 只读",
                UserNotificationActionType.SESSION_SHARE,
                "shr_controller",
                UserNotificationStatus.ACTIVE,
                null,
                true,
                true,
                NOW.plusSeconds(3600),
                null,
                NOW,
                NOW);
    }

    private static WebTestClient authenticatedClient(UserNotificationApplicationService service) {
        return WebTestClient.bindToController(new UserNotificationController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal());
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static MockServerWebExchange authenticatedExchange(MockServerHttpRequest request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal());
        return exchange;
    }

    private static AuthPrincipal principal() {
        return new AuthPrincipal(
                "token", USER, "当前用户", "ucid-notification-controller",
                List.of(), NOW, NOW.plusSeconds(3600));
    }
}
