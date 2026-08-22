package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.opencode.runtime.observability.OpencodeObservabilityModels;
import com.enterprise.testagent.opencode.runtime.observability.OpencodeObservabilityTokenService;
import com.enterprise.testagent.opencode.runtime.observability.TraceArchiveService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class OpencodeObservabilityEventsControllerTest {

    @Test
    void shouldAdaptHttpPlainObjectsToLegacyJsonTreeWithoutLosingSkillIdentity() {
        var request = new OpencodeObservabilityEventsController.PluginBatchRequest(
                "1",
                new OpencodeObservabilityModels.RuntimeIdentity(
                        "SERVER_PROCESS", "generation-1", "process-1", "server-1", null),
                Instant.parse("2026-08-22T10:00:00Z"),
                0,
                true,
                List.of(Map.of(
                        "eventId", "evt-1",
                        "traceId", "trc_0123456789abcdef0123456789abcdef",
                        "payload", Map.of("skillName", "test-design"))));

        var model = request.toModel(new ObjectMapper());

        assertThat(model.events()).hasSize(1);
        assertThat(model.events().getFirst().path("payload").path("skillName").asText())
                .isEqualTo("test-design");
    }

    @Test
    void shouldReadCanonicalGenerationHeaderBeforeAuthentication() {
        var tokenService = mock(OpencodeObservabilityTokenService.class);
        var controller = new OpencodeObservabilityEventsController(
                tokenService, mock(TraceArchiveService.class), new ObjectMapper());
        var expected = new IllegalStateException("generation header captured");
        when(tokenService.authenticate("Bearer telemetry-token", "generation-1"))
                .thenThrow(expected);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer telemetry-token")
                .header(OpencodeObservabilityTokenService.GENERATION_HEADER_NAME, "generation-1"));
        var request = new OpencodeObservabilityEventsController.PluginBatchRequest(
                "1", null, null, 0, true, List.of());

        assertThatThrownBy(() -> controller.ingest(request, exchange)).isSameAs(expected);
        verify(tokenService).authenticate("Bearer telemetry-token", "generation-1");
    }
}
