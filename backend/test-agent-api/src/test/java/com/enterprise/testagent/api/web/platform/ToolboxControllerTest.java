package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.toolbox.ToolboxClickEvent;
import com.enterprise.testagent.domain.toolbox.ToolboxClickRepository;
import com.enterprise.testagent.domain.toolbox.ToolboxClickTotal;
import com.enterprise.testagent.domain.toolbox.ToolboxClickWriteResult;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.integration.toolbox.ToolboxCatalogService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

class ToolboxControllerTest {

    private static final UserId USER_ID = new UserId("usr_toolbox_api");
    private static final String TRACE_ID = "trace_toolbox_api";

    @Test
    void ordinaryAuthenticatedUserCanReadCatalogAndRecordClick() {
        RecordingRepository repository = new RecordingRepository();
        WebTestClient client = client(new ToolboxCatalogService(repository), true);

        client.get().uri("/api/internal/platform/toolbox/tools")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.catalogVersion").isNotEmpty()
                .jsonPath("$.data.total").isEqualTo(193)
                .jsonPath("$.data.hotLimit").isEqualTo(10)
                .jsonPath("$.data.tools.length()").isEqualTo(193);

        client.post().uri("/api/internal/platform/toolbox/tools/{toolId}/clicks", "it-tools.hash-text")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"eventId\":\"evt_toolbox_api\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.toolId").isEqualTo("it-tools.hash-text")
                .jsonPath("$.data.clickCount").isEqualTo(8)
                .jsonPath("$.data.recorded").isEqualTo(true)
                .jsonPath("$.data.incremented").isEqualTo(true);

        assertThat(repository.recordedEvent).isNotNull();
        assertThat(repository.recordedEvent.toolId()).isEqualTo("it-tools.hash-text");
        assertThat(repository.recordedEvent.eventId()).isEqualTo("evt_toolbox_api");
        assertThat(repository.recordedEvent.userId()).isEqualTo(USER_ID);
        assertThat(repository.recordedEvent.traceId()).isEqualTo(TRACE_ID);
    }

    @Test
    void missingAuthenticationIsRejectedWithoutCallingService() {
        RecordingRepository repository = new RecordingRepository();

        client(new ToolboxCatalogService(repository), false).get().uri("/api/internal/platform/toolbox/tools")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED");

        assertThat(repository.calls).hasValue(0);
    }

    @Test
    void invalidEventIdUsesUnifiedValidationError() {
        RecordingRepository repository = new RecordingRepository();

        client(new ToolboxCatalogService(repository), true).post()
                .uri("/api/internal/platform/toolbox/tools/{toolId}/clicks", "it-tools.hash-text")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"eventId\":\"   \"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR");

        assertThat(repository.calls).hasValue(0);
    }

    @Test
    void unavailableToolUsesUnifiedNotFoundError() {
        RecordingRepository repository = new RecordingRepository();

        client(new ToolboxCatalogService(repository), true).post()
                .uri("/api/internal/platform/toolbox/tools/{toolId}/clicks", "it-tools.camera-recorder")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"eventId\":\"evt_missing\"}")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("NOT_FOUND")
                .jsonPath("$.traceId").isEqualTo(TRACE_ID);
        assertThat(repository.calls).hasValue(0);
    }

    private WebTestClient client(ToolboxCatalogService service, boolean authenticated) {
        var builder = WebTestClient.bindToController(new ToolboxController(service))
                .webFilter(new TraceIdWebFilter());
        if (authenticated) {
            AuthPrincipal principal = new AuthPrincipal(
                    "token",
                    USER_ID,
                    "toolbox-user",
                    "toolbox-user",
                    List.of(),
                    Instant.parse("2026-07-27T00:00:00Z"),
                    Instant.parse("2026-07-28T00:00:00Z"));
            builder.webFilter((exchange, chain) -> {
                exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                return chain.filter(exchange);
            });
        }
        return builder.controllerAdvice(new GlobalExceptionHandler()).build();
    }

    /** 不使用 Mockito，确保受限 JVM 中也能验证服务调用边界。 */
    private static final class RecordingRepository implements ToolboxClickRepository {

        private final AtomicInteger calls = new AtomicInteger();
        private ToolboxClickEvent recordedEvent;

        @Override
        public ToolboxClickWriteResult record(ToolboxClickEvent event, Duration countingWindow) {
            calls.incrementAndGet();
            recordedEvent = event;
            return new ToolboxClickWriteResult(8, true, true);
        }

        @Override
        public Map<String, ToolboxClickTotal> findTotals(List<String> toolIds) {
            calls.incrementAndGet();
            return Map.of("it-tools.hash-text", new ToolboxClickTotal(
                    "it-tools.hash-text", 7, Instant.parse("2026-07-27T00:00:00Z")));
        }
    }
}
