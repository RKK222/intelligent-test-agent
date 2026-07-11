package com.icbc.testagent.opencode.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.node.ExecutionNodeStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class OpencodeRuntimeFacadeTest {

    private static final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");

    @Test
    void facadeRunsRuntimeGetThroughGatewayWithoutLeakingGeneratedDtos() {
        FakeGateway gateway = new FakeGateway();
        OpencodeClientFacade facade = new DefaultOpencodeClientFacade(
                gateway,
                new OpencodeRunEventMapper(new ObjectMapper(), () -> NOW),
                Duration.ofSeconds(1),
                0,
                Duration.ofMillis(1));

        OpencodeRuntimeResult result = facade.runtime(new OpencodeRuntimeCommand(
                node(),
                "GET",
                "/api/agent",
                "/tmp/demo",
                null,
                Map.of(),
                null,
                "trace_1234567890abcdef")).block();

        assertThat(result.body().isArray()).isTrue();
        assertThat(result.body().get(0).path("id").asText()).isEqualTo("build");
        assertThat(gateway.lastMethod).isEqualTo("GET");
        assertThat(gateway.lastPath).isEqualTo("/api/agent");
        assertThat(gateway.lastDirectory).isEqualTo("/tmp/demo");
        assertThat(gateway.lastWorkspace).isNull();
        assertThat(gateway.lastTraceId).isEqualTo("trace_1234567890abcdef");
    }

    @Test
    void facadeRunsRuntimePostWithBodyThroughGateway() {
        FakeGateway gateway = new FakeGateway();
        OpencodeClientFacade facade = new DefaultOpencodeClientFacade(
                gateway,
                new OpencodeRunEventMapper(new ObjectMapper(), () -> NOW),
                Duration.ofSeconds(1),
                0,
                Duration.ofMillis(1));

        facade.runtime(new OpencodeRuntimeCommand(
                node(),
                "POST",
                "/api/session/ses_remote1234567890abcdef/permission/req_1/reply",
                "/tmp/demo",
                null,
                Map.of(),
                Map.of("decision", "once"),
                "trace_1234567890abcdef")).block();

        assertThat(gateway.lastMethod).isEqualTo("POST");
        assertThat(gateway.lastBody).containsEntry("decision", "once");
    }

    private static ExecutionNode node() {
        return new ExecutionNode(
                new ExecutionNodeId("node_1234567890abcdef"),
                "http://127.0.0.1:4096",
                ExecutionNodeStatus.READY,
                0,
                4,
                100,
                NOW,
                Set.of("chat"),
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }

    private static final class FakeGateway implements OpencodeSdkGateway {

        private final ObjectMapper objectMapper = new ObjectMapper();
        private String lastMethod;
        private String lastPath;
        private String lastDirectory;
        private String lastWorkspace;
        private String lastTraceId;
        private Map<String, Object> lastBody;

        @Override
        public Mono<OpencodeHealthResult> health(ExecutionNode node, String traceId) {
            return Mono.just(new OpencodeHealthResult(true, node.baseUrl()));
        }

        @Override
        public Mono<OpencodeCreateSessionResult> createSession(ExecutionNode node, String directory, String workspace, String title, String traceId) {
            return Mono.just(new OpencodeCreateSessionResult("ses_remote1234567890abcdef"));
        }

        @Override
        public Mono<Boolean> sessionExists(ExecutionNode node, String opencodeSessionId, String traceId) {
            return Mono.just(true);
        }

        @Override
        public Mono<OpencodeCancelResult> cancelSession(ExecutionNode node, String opencodeSessionId, String directory, String workspace, String traceId) {
            return Mono.just(new OpencodeCancelResult(true));
        }

        @Override
        public Mono<OpencodeStartRunResult> startRun(
                ExecutionNode node,
                String opencodeSessionId,
                String directory,
                String workspace,
                String prompt,
                List<OpencodePromptPart> parts,
                String messageId,
                String agent,
                String system,
                String modelProviderId,
                String modelId,
                String variant,
                String traceId) {
            return Mono.just(new OpencodeStartRunResult(true));
        }

        @Override
        public Mono<OpencodeStartRunResult> startCommand(
                ExecutionNode node,
                String opencodeSessionId,
                String directory,
                String workspace,
                String command,
                String arguments,
                List<OpencodePromptPart> parts,
                String messageId,
                String agent,
                String modelProviderId,
                String modelId,
                String variant,
                String traceId) {
            return Mono.just(new OpencodeStartRunResult(true));
        }

        @Override
        public Flux<JsonNode> streamEvents(ExecutionNode node, String directory, String workspace, String traceId) {
            return Flux.empty();
        }

        @Override
        public Mono<OpencodeDiffResult> getDiff(ExecutionNode node, String opencodeSessionId, String directory, String workspace, String messageId, String traceId) {
            return Mono.just(new OpencodeDiffResult(List.of()));
        }

        @Override
        public Mono<OpencodeRejectDiffResult> rejectDiff(ExecutionNode node, String opencodeSessionId, String directory, String workspace, String messageId, String partId, String traceId) {
            return Mono.just(new OpencodeRejectDiffResult(true));
        }

        @Override
        public Mono<OpencodeRuntimeResult> runtime(
                ExecutionNode node,
                String method,
                String path,
                String directory,
                String workspace,
                Map<String, String> query,
                Object body,
                String traceId) {
            lastMethod = method;
            lastPath = path;
            lastDirectory = directory;
            lastWorkspace = workspace;
            lastTraceId = traceId;
            lastBody = body instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
            return Mono.just(new OpencodeRuntimeResult(objectMapper.valueToTree(List.of(Map.of("id", "build")))));
        }

        @Override
        public Mono<OpencodeSessionMessagesResult> sessionMessages(
                ExecutionNode node,
                String opencodeSessionId,
                int limit,
                String order,
                String cursor,
                String traceId) {
            return Mono.just(new OpencodeSessionMessagesResult(List.of(), null, null));
        }
    }
}
