package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.integration.codeknowledge.TraceWeaveCodeKnowledgeService;
import com.enterprise.testagent.opencode.runtime.process.CodeKnowledgeToolTokenService;
import com.enterprise.testagent.workspace.CodeSourceQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class CodeKnowledgeToolControllerTest {

    private static final UserId USER_ID = new UserId("usr_code_reader");

    @Test
    void graphContextIncludesSourceBaselineAvailabilityForResolvedRepositories() {
        CodeKnowledgeToolTokenService tokens = mock(CodeKnowledgeToolTokenService.class);
        TraceWeaveCodeKnowledgeService graph = mock(TraceWeaveCodeKnowledgeService.class);
        CodeSourceQueryService source = mock(CodeSourceQueryService.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        when(tokens.authenticate("Bearer scoped-code-token"))
                .thenReturn(new CodeKnowledgeToolTokenService.Principal(USER_ID, List.of("USER")));
        var request = new CodeKnowledgeToolController.CodeKnowledgeToolRequest(
                "ses_1", "context", List.of("repo_orders"), null, null,
                null, null, null, null, null, null, null, null, null, null, null);
        var graphQuery = request.toQuery();
        when(graph.execute(USER_ID, graphQuery)).thenReturn(new TraceWeaveCodeKnowledgeService.Result(
                mapper.createObjectNode().put("operation", "context"), List.of("repo_orders")));
        when(source.context(USER_ID, "repo_orders")).thenReturn(new CodeSourceQueryService.SourceContext(
                "订单服务", "orders", new CodeSourceQueryService.SourceEvidence(
                        "repo_orders", "orders", "main", "a".repeat(40), 7L,
                        Instant.parse("2026-09-13T00:00:00Z"),
                        List.of(new CodeSourceQueryService.SourceSelection("src", "DIRECTORY")),
                        "IMMUTABLE_BASELINE"),
                true,
                null));
        CodeKnowledgeToolController controller = new CodeKnowledgeToolController(tokens, graph, source, mapper);

        var response = controller.executeGraph(request, exchange()).block();

        assertThat(response.data()).isInstanceOf(Map.class);
        assertThat(mapper.valueToTree(response.data()).path("sourceContexts").get(0)
                .path("evidence").path("generation").asLong()).isEqualTo(7L);
        verify(tokens).authenticate("Bearer scoped-code-token");
        verify(source).context(USER_ID, "repo_orders");
    }

    @Test
    void sourceReadUsesTokenUserAndRepositoryInsteadOfCallerWorkspacePath() {
        CodeKnowledgeToolTokenService tokens = mock(CodeKnowledgeToolTokenService.class);
        TraceWeaveCodeKnowledgeService graph = mock(TraceWeaveCodeKnowledgeService.class);
        CodeSourceQueryService source = mock(CodeSourceQueryService.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        when(tokens.authenticate("Bearer scoped-code-token"))
                .thenReturn(new CodeKnowledgeToolTokenService.Principal(USER_ID, List.of("USER")));
        var expected = new CodeSourceQueryService.SourceReadResult(
                new CodeSourceQueryService.SourceEvidence(
                        "repo_orders", "orders", "main", "b".repeat(40), 8L,
                        Instant.parse("2026-09-13T00:00:00Z"),
                        List.of(new CodeSourceQueryService.SourceSelection("src", "DIRECTORY")),
                        "IMMUTABLE_BASELINE"),
                "src/Order.java", 1, 1, 1, "c".repeat(64), "class Order {}");
        when(source.read(USER_ID, "repo_orders", "src/Order.java", 1, 20)).thenReturn(expected);
        CodeKnowledgeToolController controller = new CodeKnowledgeToolController(tokens, graph, source, mapper);

        var response = controller.executeSource(
                new CodeKnowledgeToolController.CodeSourceToolRequest(
                        "ses_1", "read", "repo_orders", "src/Order.java", null, 1, 20, null),
                exchange()).block();

        assertThat(response.data()).isEqualTo(expected);
        verify(source).read(USER_ID, "repo_orders", "src/Order.java", 1, 20);
    }

    @Test
    void graphContextOffersTheExistingPreparationEntryWhenNoSourceSnapshotExists() {
        CodeKnowledgeToolTokenService tokens = mock(CodeKnowledgeToolTokenService.class);
        TraceWeaveCodeKnowledgeService graph = mock(TraceWeaveCodeKnowledgeService.class);
        CodeSourceQueryService source = mock(CodeSourceQueryService.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        when(tokens.authenticate("Bearer scoped-code-token"))
                .thenReturn(new CodeKnowledgeToolTokenService.Principal(USER_ID, List.of("USER")));
        var request = new CodeKnowledgeToolController.CodeKnowledgeToolRequest(
                "ses_1", "context", List.of("repo_orders"), null, null,
                null, null, null, null, null, null, null, null, null, null, null);
        when(graph.execute(USER_ID, request.toQuery())).thenReturn(new TraceWeaveCodeKnowledgeService.Result(
                mapper.createObjectNode().put("operation", "context"), List.of("repo_orders")));
        when(source.context(USER_ID, "repo_orders"))
                .thenThrow(new PlatformException(ErrorCode.CONFLICT, "应用源码快照未就绪"));
        CodeKnowledgeToolController controller = new CodeKnowledgeToolController(tokens, graph, source, mapper);

        var response = controller.executeGraph(request, exchange()).block();

        JsonNode context = mapper.valueToTree(response.data()).path("sourceContexts").get(0);
        assertThat(context.path("available").asBoolean()).isFalse();
        assertThat(context.path("code").asText()).isEqualTo("CONFLICT");
        assertThat(context.path("preparationAction").asText()).isEqualTo("OPEN_APP_SOURCE_PREPARATION");
    }

    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.post("/api/internal/agent/opencode/code-knowledge-tool")
                .header("Authorization", "Bearer scoped-code-token"));
    }
}
