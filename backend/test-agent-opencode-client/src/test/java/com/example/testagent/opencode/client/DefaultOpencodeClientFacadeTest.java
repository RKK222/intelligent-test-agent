package com.example.testagent.opencode.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
import com.example.testagent.domain.event.RunEventDraft;
import com.example.testagent.domain.event.RunEventType;
import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.domain.node.ExecutionNodeId;
import com.example.testagent.domain.node.ExecutionNodeStatus;
import com.example.testagent.domain.run.RunId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DefaultOpencodeClientFacadeTest {

    private static final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");

    @Test
    void facadePropagatesTraceIdAndReturnsHealthResult() {
        FakeGateway gateway = new FakeGateway();
        OpencodeClientFacade facade = facade(gateway, Duration.ofSeconds(1), 0);

        OpencodeHealthResult result = facade.health(new OpencodeHealthCommand(node(), "trace_1234567890abcdef")).block();

        assertThat(result.available()).isTrue();
        assertThat(gateway.lastTraceId).isEqualTo("trace_1234567890abcdef");
    }

    @Test
    void facadeMapsTimeoutToPlatformTimeout() {
        FakeGateway gateway = new FakeGateway();
        gateway.health = Mono.never();
        OpencodeClientFacade facade = facade(gateway, Duration.ofMillis(20), 0);

        assertThatThrownBy(() -> facade.health(new OpencodeHealthCommand(node(), "trace_1234567890abcdef")).block())
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_TIMEOUT));
    }

    @Test
    void facadeRetriesRetryableRemoteErrors() {
        AtomicInteger attempts = new AtomicInteger();
        FakeGateway gateway = new FakeGateway();
        gateway.healthSupplier = () -> {
            if (attempts.incrementAndGet() == 1) {
                return Mono.error(remoteError(503));
            }
            return Mono.just(new OpencodeHealthResult(true, node().baseUrl()));
        };
        OpencodeClientFacade facade = facade(gateway, Duration.ofSeconds(1), 1);

        OpencodeHealthResult result = facade.health(new OpencodeHealthCommand(node(), "trace_1234567890abcdef")).block();

        assertThat(result.available()).isTrue();
        assertThat(attempts).hasValue(2);
    }

    @Test
    void facadeMapsRemoteUnavailableErrors() {
        FakeGateway gateway = new FakeGateway();
        gateway.health = Mono.error(remoteError(503));
        OpencodeClientFacade facade = facade(gateway, Duration.ofSeconds(1), 0);

        assertThatThrownBy(() -> facade.health(new OpencodeHealthCommand(node(), "trace_1234567890abcdef")).block())
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE));
    }

    @Test
    void facadeMapsCancelSessionThroughGateway() {
        FakeGateway gateway = new FakeGateway();
        OpencodeClientFacade facade = facade(gateway, Duration.ofSeconds(1), 0);

        OpencodeCancelResult result = facade.cancelSession(new OpencodeCancelCommand(
                node(),
                "ses_remote1234567890abcdef",
                "/tmp/demo",
                null,
                "trace_1234567890abcdef")).block();

        assertThat(result.cancelled()).isTrue();
        assertThat(gateway.lastOpencodeSessionId).isEqualTo("ses_remote1234567890abcdef");
        assertThat(gateway.lastWorkspace).isNull();
    }

    @Test
    void facadeCreatesOpencodeSessionAndPropagatesOptionalWorkspace() {
        FakeGateway gateway = new FakeGateway();
        OpencodeClientFacade facade = facade(gateway, Duration.ofSeconds(1), 0);

        OpencodeCreateSessionResult result = facade.createSession(new OpencodeCreateSessionCommand(
                node(),
                "/tmp/demo",
                null,
                "Demo session",
                "trace_1234567890abcdef")).block();

        assertThat(result.opencodeSessionId()).isEqualTo("ses_remote1234567890abcdef");
        assertThat(gateway.lastTraceId).isEqualTo("trace_1234567890abcdef");
        assertThat(gateway.lastDirectory).isEqualTo("/tmp/demo");
        assertThat(gateway.lastWorkspace).isNull();
        assertThat(gateway.lastTitle).isEqualTo("Demo session");
    }

    @Test
    void facadeStartsRunWithPromptAsyncAndPropagatesTraceId() {
        FakeGateway gateway = new FakeGateway();
        OpencodeClientFacade facade = facade(gateway, Duration.ofSeconds(1), 0);

        OpencodeStartRunResult result = facade.startRun(new OpencodeStartRunCommand(
                node(),
                "ses_remote1234567890abcdef",
                "/tmp/demo",
                null,
                "run the tests",
                "trace_1234567890abcdef")).block();

        assertThat(result.accepted()).isTrue();
        assertThat(gateway.lastTraceId).isEqualTo("trace_1234567890abcdef");
        assertThat(gateway.lastOpencodeSessionId).isEqualTo("ses_remote1234567890abcdef");
        assertThat(gateway.lastPrompt).isEqualTo("run the tests");
        assertThat(gateway.lastWorkspace).isNull();
    }

    @Test
    void facadeMapsRawOpencodeEventsToRunEventDrafts() throws Exception {
        FakeGateway gateway = new FakeGateway();
        ObjectMapper objectMapper = new ObjectMapper();
        gateway.events = Flux.just(objectMapper.readTree("""
                {"id":"evt_raw_1","type":"session.next.text.delta","properties":{"text":"hello"}}
                """));
        OpencodeClientFacade facade = facade(gateway, Duration.ofSeconds(1), 0);

        RunEventDraft draft = facade.streamRunEvents(new OpencodeStreamEventsCommand(
                node(),
                new RunId("run_1234567890abcdef"),
                "/tmp/demo",
                null,
                "trace_1234567890abcdef")).blockFirst();

        assertThat(draft.type()).isEqualTo(RunEventType.ASSISTANT_MESSAGE_DELTA);
        assertThat(draft.payload()).containsEntry("text", "hello");
        assertThat(draft.payload()).containsEntry("rawType", "session.next.text.delta");
    }

    @Test
    void facadeReadsSessionDiffWithoutLeakingGeneratedDtos() {
        FakeGateway gateway = new FakeGateway();
        OpencodeClientFacade facade = facade(gateway, Duration.ofSeconds(1), 0);

        OpencodeDiffResult result = facade.getDiff(new OpencodeDiffCommand(
                node(),
                "ses_remote1234567890abcdef",
                "/tmp/demo",
                null,
                "msg_remote1234567890abcdef",
                "trace_1234567890abcdef")).block();

        assertThat(result.files()).singleElement().satisfies(file -> {
            assertThat(file.path()).isEqualTo("tests/demo.spec.ts");
            assertThat(file.patch()).contains("@@");
            assertThat(file.additions()).isEqualTo(2);
            assertThat(file.deletions()).isEqualTo(1);
            assertThat(file.status()).isEqualTo("modified");
        });
        assertThat(gateway.lastOpencodeSessionId).isEqualTo("ses_remote1234567890abcdef");
        assertThat(gateway.lastMessageId).isEqualTo("msg_remote1234567890abcdef");
    }

    @Test
    void facadeRejectsDiffThroughSessionRevert() {
        FakeGateway gateway = new FakeGateway();
        OpencodeClientFacade facade = facade(gateway, Duration.ofSeconds(1), 0);

        OpencodeRejectDiffResult result = facade.rejectDiff(new OpencodeRejectDiffCommand(
                node(),
                "ses_remote1234567890abcdef",
                "/tmp/demo",
                null,
                "msg_remote1234567890abcdef",
                null,
                "trace_1234567890abcdef")).block();

        assertThat(result.rejected()).isTrue();
        assertThat(gateway.lastOpencodeSessionId).isEqualTo("ses_remote1234567890abcdef");
        assertThat(gateway.lastMessageId).isEqualTo("msg_remote1234567890abcdef");
    }

    private static OpencodeClientFacade facade(FakeGateway gateway, Duration timeout, int maxRetries) {
        return new DefaultOpencodeClientFacade(
                gateway,
                new OpencodeRunEventMapper(new ObjectMapper(), () -> NOW),
                timeout,
                maxRetries,
                Duration.ofMillis(1));
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

    private static WebClientResponseException remoteError(int status) {
        return WebClientResponseException.create(
                status,
                "remote error",
                HttpHeaders.EMPTY,
                new byte[0],
                null);
    }

    private static final class FakeGateway implements OpencodeSdkGateway {

        private Mono<OpencodeHealthResult> health = Mono.just(new OpencodeHealthResult(true, node().baseUrl()));
        private java.util.function.Supplier<Mono<OpencodeHealthResult>> healthSupplier;
        private Flux<JsonNode> events = Flux.empty();
        private String lastTraceId;
        private String lastOpencodeSessionId;
        private String lastDirectory;
        private String lastWorkspace;
        private String lastTitle;
        private String lastMessageId;

        @Override
        public Mono<OpencodeHealthResult> health(ExecutionNode node, String traceId) {
            lastTraceId = traceId;
            if (healthSupplier != null) {
                return healthSupplier.get();
            }
            return health;
        }

        @Override
        public Mono<OpencodeCreateSessionResult> createSession(
                ExecutionNode node,
                String directory,
                String workspace,
                String title,
                String traceId) {
            lastTraceId = traceId;
            lastDirectory = directory;
            lastWorkspace = workspace;
            lastTitle = title;
            return Mono.just(new OpencodeCreateSessionResult("ses_remote1234567890abcdef"));
        }

        @Override
        public Mono<OpencodeCancelResult> cancelSession(
                ExecutionNode node,
                String opencodeSessionId,
                String directory,
                String workspace,
                String traceId) {
            lastTraceId = traceId;
            lastOpencodeSessionId = opencodeSessionId;
            lastDirectory = directory;
            lastWorkspace = workspace;
            return Mono.just(new OpencodeCancelResult(true));
        }

        @Override
        public Mono<OpencodeStartRunResult> startRun(
                ExecutionNode node,
                String opencodeSessionId,
                String directory,
                String workspace,
                String prompt,
                String traceId) {
            lastTraceId = traceId;
            lastOpencodeSessionId = opencodeSessionId;
            lastDirectory = directory;
            lastWorkspace = workspace;
            lastPrompt = prompt;
            return Mono.just(new OpencodeStartRunResult(true));
        }

        @Override
        public Flux<JsonNode> streamEvents(ExecutionNode node, String directory, String workspace, String traceId) {
            lastTraceId = traceId;
            lastDirectory = directory;
            lastWorkspace = workspace;
            return events;
        }

        @Override
        public Mono<OpencodeDiffResult> getDiff(
                ExecutionNode node,
                String opencodeSessionId,
                String directory,
                String workspace,
                String messageId,
                String traceId) {
            lastTraceId = traceId;
            lastOpencodeSessionId = opencodeSessionId;
            lastDirectory = directory;
            lastWorkspace = workspace;
            lastMessageId = messageId;
            return Mono.just(new OpencodeDiffResult(List.of(new OpencodeDiffFile(
                    "tests/demo.spec.ts",
                    "@@ -1 +1 @@\n-old\n+new\n",
                    2,
                    1,
                    "modified"))));
        }

        @Override
        public Mono<OpencodeRejectDiffResult> rejectDiff(
                ExecutionNode node,
                String opencodeSessionId,
                String directory,
                String workspace,
                String messageId,
                String partId,
                String traceId) {
            lastTraceId = traceId;
            lastOpencodeSessionId = opencodeSessionId;
            lastDirectory = directory;
            lastWorkspace = workspace;
            lastMessageId = messageId;
            return Mono.just(new OpencodeRejectDiffResult(true));
        }

        private String lastPrompt;
    }
}
