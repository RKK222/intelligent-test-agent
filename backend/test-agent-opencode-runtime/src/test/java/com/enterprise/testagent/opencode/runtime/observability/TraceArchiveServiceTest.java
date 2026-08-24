package com.enterprise.testagent.opencode.runtime.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.event.RunSessionScopeRepository;
import com.enterprise.testagent.domain.event.RunSessionScopeSession;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRepository;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.trace.TraceCatalogRepository;
import com.enterprise.testagent.domain.trace.TraceModels;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TraceArchiveServiceTest {

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void shouldArchiveRawEventsIdempotentlyAndCountSkillByStableCallIdentity() throws Exception {
        InMemoryCatalogRepository repository = new InMemoryCatalogRepository();
        TraceArchiveService service = service(repository);
        User user = User.createNew(
                "usr_00000000000000000000000001", "u-1", "张三", "hash", "总行", "研发", "测试");
        var identity = new OpencodeObservabilityModels.IngestionIdentity(user, "proc-1", null, "generation-1");
        var runtime = new OpencodeObservabilityModels.RuntimeIdentity(
                "SERVER_PROCESS", "generation-1", "proc-1", "server-1", null);
        String traceId = "trc_00000000000000000000000000000001";
        List<JsonNode> events = List.of(
                event(traceId, 1, "TOOL_EXECUTE_BEFORE", """
                        {"tool":"skill","skillName":"test-design","capabilityKind":"SKILL"}
                        """),
                event(traceId, 4, "TOOL_EXECUTE_AFTER", """
                        {"tool":"skill","skillName":"test-design","capabilityKind":"SKILL",\
                         "status":"SUCCEEDED","durationMs":37,"result":"完整工具输出 Authorization=[REDACTED]"}
                        """),
                event(traceId, 8, "OPENCODE_EVENT", """
                        {"event":{"type":"session.idle","properties":{"agent":"test-design-agent"}}}
                        """));
        var batch = new OpencodeObservabilityModels.PluginBatch(
                "1.0", runtime, Instant.parse("2026-08-22T00:00:00Z"), 0, true, events);

        var first = service.ingestPluginBatch(identity, batch).traces().getFirst();
        var duplicate = service.ingestPluginBatch(identity, batch).traces().getFirst();

        assertThat(first.duplicate()).isFalse();
        assertThat(duplicate.duplicate()).isTrue();
        TraceModels.Catalog catalog = repository.find(traceId).orElseThrow();
        assertThat(catalog.eventCount()).isEqualTo(3);
        assertThat(catalog.completeThrough()).isEqualTo(8);
        assertThat(catalog.complete()).isTrue();
        assertThat(catalog.coverageStartAt()).isAfter(batch.coverageStartAt());
        assertThat(repository.spans).hasSize(3);
        assertThat(repository.facts).hasSize(1);
        TraceModels.CapabilityFact fact = repository.facts.values().iterator().next();
        assertThat(fact.capabilityType()).isEqualTo("SKILL");
        assertThat(fact.capabilityName()).isEqualTo("test-design");
        assertThat(fact.callId()).isEqualTo("call-1");
        assertThat(fact.status()).isEqualTo("SUCCEEDED");

        var page = service.readRawEvents(traceId, 0, 100);
        assertThat(page.items()).hasSize(3);
        assertThat(page.items().get(1).path("payload").path("result").asText()).contains("完整工具输出");
        var record = service.readRecordEvents(
                traceId, events.get(1).path("eventId").asText(), 4);
        assertThat(record.items())
                .extracting(node -> node.path("type").asText())
                .containsExactly("TOOL_EXECUTE_BEFORE", "TOOL_EXECUTE_AFTER");
        try (InputStream compressed = service.openCompressedTrace(traceId);
                GZIPInputStream gzip = new GZIPInputStream(compressed)) {
            assertThat(new String(gzip.readAllBytes(), StandardCharsets.UTF_8))
                    .contains("test-design")
                    .contains("完整工具输出");
        }
        try (var paths = Files.walk(temporaryDirectory)) {
            assertThat(paths.noneMatch(path -> path.toString().contains(user.userId().value()))).isTrue();
        }
    }

    @Test
    void shouldIndexRealOpenCodeMessageTokensTimingAndNestedAgent() throws Exception {
        InMemoryCatalogRepository repository = new InMemoryCatalogRepository();
        TraceArchiveService service = service(repository);
        User user = User.createNew(
                "usr_00000000000000000000000009", "u-9", "性能用户", "hash", null, null, null);
        String traceId = "trc_00000000000000000000000000000009";
        JsonNode rawEvent = event(traceId, 1, "OPENCODE_EVENT", """
                {"event":{"type":"message.part.updated","properties":{
                  "sessionID":"session-1",
                  "time":1120,
                  "info":{"agent":"test-design-agent"},
                  "part":{"id":"part-1","type":"step-finish","messageID":"msg-1",
                    "reason":"stop","cost":0.25,
                    "tokens":{"total":174,"input":123,"output":45,"reasoning":6,"cache":{"read":88,"write":9}}}
                }}}
                """);
        JsonNode metricEvent = event(traceId, 2, "ASSISTANT_STEP_METRICS", """
                {"recordKind":"message","status":"COMPLETED","timingRecorded":true,
                 "startedAt":"1970-01-01T00:00:01Z","durationMs":125,"ttftMs":25,"decodeMs":100,
                 "tokensInput":123,"tokensOutput":45,"tokensReasoning":6,
                 "tokensCacheRead":88,"tokensCacheWrite":9,"tokensTotal":174,
                 "decodeTokens":45,"cost":0.25,"finishReason":"stop"}
                """);

        service.ingestPluginBatch(
                new OpencodeObservabilityModels.IngestionIdentity(user, "proc-9", null, "generation-9"),
                new OpencodeObservabilityModels.PluginBatch(
                        "1.0",
                        new OpencodeObservabilityModels.RuntimeIdentity(
                                "SERVER_PROCESS", "generation-9", "proc-9", "server-1", null),
                        Instant.parse("2026-08-22T00:00:00Z"),
                        0,
                        false,
                        List.of(rawEvent, metricEvent)));

        assertThat(repository.spans.values())
                .filteredOn(span -> span.type().equals("OPENCODE_EVENT"))
                .singleElement()
                .satisfies(span -> {
                    assertThat(span.durationMs()).isZero();
                    assertThat(span.tokensInput()).isEqualTo(123);
                    assertThat(span.tokensOutput()).isEqualTo(45);
                    assertThat(span.tokensReasoning()).isEqualTo(6);
                    assertThat(span.tokensCacheRead()).isEqualTo(88);
                    assertThat(span.tokensCacheWrite()).isEqualTo(9);
                    assertThat(span.tokensTotal()).isEqualTo(174);
                    assertThat(span.decodeTokens()).isEqualTo(45);
                    assertThat(span.cost()).isEqualTo(0.25);
                    assertThat(span.finishReason()).isEqualTo("stop");
                    assertThat(span.recordKind()).isEqualTo("message");
                    assertThat(span.startedAt()).isNull();
                });
        assertThat(repository.spans.values())
                .filteredOn(span -> span.type().equals("ASSISTANT_STEP_METRICS"))
                .singleElement()
                .satisfies(span -> {
                    assertThat(span.durationMs()).isEqualTo(125);
                    assertThat(span.startedAt()).isEqualTo(Instant.ofEpochMilli(1000));
                    assertThat(span.ttftMs()).isEqualTo(25);
                    assertThat(span.decodeMs()).isEqualTo(100);
                    assertThat(span.decodeTokens()).isEqualTo(45);
                    assertThat(span.cost()).isEqualTo(0.25);
                });
        assertThat(repository.find(traceId)).get()
                .extracting(TraceModels.Catalog::agentId)
                .isEqualTo("test-design-agent");
    }

    @Test
    void shouldIndexAgentFromTheOpenCode1184ChatMessageInput() throws Exception {
        InMemoryCatalogRepository repository = new InMemoryCatalogRepository();
        TraceArchiveService service = service(repository);
        User user = User.createNew(
                "usr_00000000000000000000000011", "u-11", "Agent 用户", "hash", null, null, null);
        String traceId = "trc_00000000000000000000000000000011";

        service.ingestPluginBatch(
                new OpencodeObservabilityModels.IngestionIdentity(user, "proc-11", null, "generation-11"),
                new OpencodeObservabilityModels.PluginBatch(
                        "1.0",
                        new OpencodeObservabilityModels.RuntimeIdentity(
                                "SERVER_PROCESS", "generation-11", "proc-11", "server-1", null),
                        Instant.parse("2026-08-22T00:00:00Z"),
                        0,
                        false,
                        List.of(event(traceId, 1, "CHAT_MESSAGE", """
                                {"recordKind":"user","input":{"sessionID":"session-1","agent":"build"}}
                                """))));

        assertThat(repository.find(traceId)).get()
                .extracting(TraceModels.Catalog::agentId)
                .isEqualTo("build");
    }

    @Test
    void shouldRejectDigestMismatchWithoutCreatingCatalog() throws Exception {
        InMemoryCatalogRepository repository = new InMemoryCatalogRepository();
        TraceArchiveService service = service(repository);
        User user = User.createNew(
                "usr_00000000000000000000000002", "u-2", "李四", "hash", null, null, null);
        JsonNode event = event(
                "trc_00000000000000000000000000000002", 1, "CHAT_MESSAGE", "{\"message\":\"hello\"}");
        byte[] ndjson = (objectMapper.writeValueAsString(event) + "\n").getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.ingestLocalChunk(
                new OpencodeObservabilityModels.IngestionIdentity(user, null, "client-1", "generation-2"),
                new OpencodeObservabilityModels.RuntimeIdentity(
                        "LOCAL_CLIENT", "generation-2", null, null, "client-1"),
                Instant.parse("2026-08-22T00:00:00Z"),
                0,
                0,
                false,
                "trc_00000000000000000000000000000002",
                1,
                1,
                "0".repeat(64),
                ndjson))
                .isInstanceOf(PlatformException.class);
        assertThat(repository.catalogs).isEmpty();
    }

    @Test
    void shouldArchiveLocalChunkAndPersistRealPendingSpoolCountUntilAck() throws Exception {
        InMemoryCatalogRepository repository = new InMemoryCatalogRepository();
        TraceArchiveService service = service(repository);
        User user = User.createNew(
                "usr_00000000000000000000000008", "u-8", "本地用户", "hash", null, null, null);
        String traceId = "trc_00000000000000000000000000000008";
        JsonNode event = event(traceId, 8, "OPENCODE_EVENT",
                "{\"event\":{\"type\":\"session.idle\",\"properties\":{}}}");
        byte[] ndjson = (objectMapper.writeValueAsString(event) + "\n").getBytes(StandardCharsets.UTF_8);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(ndjson));

        OpencodeObservabilityModels.TraceAck ack = service.ingestLocalChunk(
                new OpencodeObservabilityModels.IngestionIdentity(user, null, "client-8", "generation-8"),
                new OpencodeObservabilityModels.RuntimeIdentity(
                        "LOCAL_CLIENT", "generation-8", null, null, "client-8"),
                Instant.parse("2026-08-22T00:00:00Z"),
                0,
                3,
                true,
                traceId,
                8,
                8,
                digest,
                ndjson);

        assertThat(ack.completeThrough()).isEqualTo(8);
        assertThat(ack.sha256()).isEqualTo(digest);
        assertThat(repository.find(traceId)).get().satisfies(catalog -> {
            assertThat(catalog.pendingChunks()).isEqualTo(3);
            assertThat(catalog.runtimeKind()).isEqualTo("LOCAL_CLIENT");
            assertThat(catalog.archiveStatus()).isEqualTo("INCOMPLETE");
            assertThat(catalog.complete()).isFalse();
        });
        assertThat(service.readRawEvents(traceId, 0, 10).items()).singleElement()
                .extracting(node -> node.path("payload").path("event").path("type").asText())
                .isEqualTo("session.idle");
    }

    @Test
    void shouldFenceAReusedTraceIdFromAnotherRuntimeGeneration() throws Exception {
        InMemoryCatalogRepository repository = new InMemoryCatalogRepository();
        TraceArchiveService service = service(repository);
        User user = User.createNew(
                "usr_00000000000000000000000003", "u-3", "王五", "hash", null, null, null);
        String traceId = "trc_00000000000000000000000000000003";
        service.ingestPluginBatch(
                new OpencodeObservabilityModels.IngestionIdentity(user, "proc-3", null, "generation-3"),
                new OpencodeObservabilityModels.PluginBatch(
                        "1.0",
                        new OpencodeObservabilityModels.RuntimeIdentity(
                                "SERVER_PROCESS", "generation-3", "proc-3", "server-1", null),
                        Instant.parse("2026-08-22T00:00:00Z"),
                        0,
                        false,
                        List.of(event(traceId, 1, "CHAT_MESSAGE", "{\"message\":\"hello\"}"))));

        assertThatThrownBy(() -> service.ingestPluginBatch(
                new OpencodeObservabilityModels.IngestionIdentity(user, "proc-4", null, "generation-4"),
                new OpencodeObservabilityModels.PluginBatch(
                        "1.0",
                        new OpencodeObservabilityModels.RuntimeIdentity(
                                "SERVER_PROCESS", "generation-4", "proc-4", "server-1", null),
                        Instant.parse("2026-08-22T00:00:00Z"),
                        0,
                        false,
                        List.of(event(traceId, 2, "CHAT_MESSAGE", "{\"message\":\"world\"}")))))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("冻结");
    }

    @Test
    void shouldResumeSameTraceGenerationAfterBackendJvmRestartsOnFrozenStorageNode() throws Exception {
        InMemoryCatalogRepository repository = new InMemoryCatalogRepository();
        User user = User.createNew(
                "usr_00000000000000000000000010", "u-10", "续传用户", "hash", null, null, null);
        String traceId = "trc_00000000000000000000000000000010";
        var identity = new OpencodeObservabilityModels.IngestionIdentity(
                user, "proc-10", null, "generation-10");
        var runtime = new OpencodeObservabilityModels.RuntimeIdentity(
                "SERVER_PROCESS", "generation-10", "proc-10", "server-1", null);

        service(repository, mock(RunSessionScopeRepository.class), mock(RunRepository.class), "backend-process-before")
                .ingestPluginBatch(identity, new OpencodeObservabilityModels.PluginBatch(
                        "1.0", runtime, Instant.parse("2026-08-22T00:00:00Z"), 0, false,
                        List.of(event(traceId, 1, "CHAT_MESSAGE", "{\"message\":\"before\"}"))));
        service(repository, mock(RunSessionScopeRepository.class), mock(RunRepository.class), "backend-process-after")
                .ingestPluginBatch(identity, new OpencodeObservabilityModels.PluginBatch(
                        "1.0", runtime, Instant.parse("2026-08-22T00:00:00Z"), 0, false,
                        List.of(event(traceId, 2, "CHAT_MESSAGE", "{\"message\":\"after\"}"))));

        assertThat(repository.find(traceId)).get().satisfies(catalog -> {
            assertThat(catalog.eventCount()).isEqualTo(2);
            assertThat(catalog.completeThrough()).isEqualTo(2);
            assertThat(catalog.backendProcessId()).isEqualTo("backend-process-after");
            assertThat(catalog.linuxServerId()).isEqualTo("linux-server-1");
        });
    }

    @Test
    void shouldCorrelatePluginSessionToOwnedPlatformRun() throws Exception {
        InMemoryCatalogRepository repository = new InMemoryCatalogRepository();
        RunSessionScopeRepository scopeRepository = mock(RunSessionScopeRepository.class);
        RunRepository runRepository = mock(RunRepository.class);
        User user = User.createNew(
                "usr_00000000000000000000000004", "u-4", "赵六", "hash", null, null, null);
        RunId runId = new RunId("run_correlated000000000000000001");
        RunSessionScopeSession scope = new RunSessionScopeSession(
                runId,
                "session-1",
                "session-1",
                null,
                false,
                "ROOT",
                null,
                null,
                null,
                "trace-correlated-1",
                Instant.parse("2026-08-22T00:00:00Z"),
                Instant.parse("2026-08-22T00:00:00Z"),
                Map.of());
        Run run = new Run(
                runId,
                new SessionId("ses_correlated000000000000000001"),
                new WorkspaceId("wrk_correlated000000000000000001"),
                RunStatus.RUNNING,
                Instant.parse("2026-08-22T00:00:00Z"),
                Instant.parse("2026-08-22T00:00:00Z"),
                "trace-correlated-1",
                null,
                null,
                null,
                null,
                user.userId(),
                "test-design-orchestrator",
                "deepseek/test");
        when(scopeRepository.findLatestBySessionId("session-1")).thenReturn(Optional.of(scope));
        when(runRepository.findById(runId)).thenReturn(Optional.of(run));
        TraceArchiveService service = service(repository, scopeRepository, runRepository);
        JsonNode event = event(
                "trc_00000000000000000000000000000004", 1, "CHAT_MESSAGE", "{\"message\":\"hello\"}");
        ((com.fasterxml.jackson.databind.node.ObjectNode) event).remove("runId");

        service.ingestPluginBatch(
                new OpencodeObservabilityModels.IngestionIdentity(user, "proc-4", null, "generation-4"),
                new OpencodeObservabilityModels.PluginBatch(
                        "1.0",
                        new OpencodeObservabilityModels.RuntimeIdentity(
                                "SERVER_PROCESS", "generation-4", "proc-4", "server-1", null),
                        Instant.parse("2026-08-22T00:00:00Z"),
                        0,
                        false,
                        List.of(event)));

        assertThat(repository.find("trc_00000000000000000000000000000004"))
                .get()
                .satisfies(catalog -> {
                    assertThat(catalog.runId()).isEqualTo(runId.value());
                    assertThat(catalog.agentId()).isEqualTo("test-design-orchestrator");
                });
        assertThat(repository.spans.values())
                .singleElement()
                .extracting(TraceModels.Span::runId)
                .isEqualTo(runId.value());
    }

    private TraceArchiveService service(InMemoryCatalogRepository repository) {
        return service(repository, mock(RunSessionScopeRepository.class), mock(RunRepository.class));
    }

    private TraceArchiveService service(
            InMemoryCatalogRepository repository,
            RunSessionScopeRepository scopeRepository,
            RunRepository runRepository) {
        return service(repository, scopeRepository, runRepository, "backend-process-1");
    }

    private TraceArchiveService service(
            InMemoryCatalogRepository repository,
            RunSessionScopeRepository scopeRepository,
            RunRepository runRepository,
            String backendProcessId) {
        BackendJavaRouteResolver routeResolver = mock(BackendJavaRouteResolver.class);
        when(routeResolver.currentBackendProcessIdValue()).thenReturn(backendProcessId);
        when(routeResolver.currentLinuxServerIdValue()).thenReturn("linux-server-1");
        TraceArchiveSettings settings = new TraceArchiveSettings(
                mock(CommonParameterValues.class), temporaryDirectory.toString(), 64L * 1024 * 1024);
        return new TraceArchiveService(
                objectMapper,
                repository,
                scopeRepository,
                runRepository,
                routeResolver,
                settings,
                new SimpleMeterRegistry());
    }

    private JsonNode event(String traceId, long sequence, String type, String payload) throws Exception {
        String source = """
                {
                  "schemaVersion":"1.0",
                  "eventId":"evt_%040x",
                  "traceId":"%s",
                  "type":"%s",
                  "timestamp":"2026-08-22T00:00:%02dZ",
                  "globalSequence":%d,
                  "sessionSequence":%d,
                  "sessionId":"session-1",
                  "runId":"run-1",
                  "callId":"call-1",
                  "payload":%s
                }
                """.formatted(sequence, traceId, type, sequence, sequence, sequence, payload);
        return objectMapper.readTree(source);
    }

    private static final class InMemoryCatalogRepository implements TraceCatalogRepository {
        private final Map<String, TraceModels.Catalog> catalogs = new LinkedHashMap<>();
        private final Map<String, TraceModels.Span> spans = new LinkedHashMap<>();
        private final Map<String, TraceModels.CapabilityFact> facts = new LinkedHashMap<>();

        @Override
        public void save(
                TraceModels.Catalog catalog,
                List<TraceModels.Span> newSpans,
                List<TraceModels.CapabilityFact> capabilityFacts,
                Instant ingestedAt) {
            catalogs.put(catalog.traceId(), catalog);
            newSpans.forEach(span -> spans.put(span.eventId(), span));
            capabilityFacts.forEach(fact -> facts.merge(
                    fact.eventId(), fact,
                    (left, right) -> right.version() >= left.version() ? right : left));
        }

        @Override
        public Optional<TraceModels.Catalog> find(String traceId) {
            return Optional.ofNullable(catalogs.get(traceId));
        }

        @Override
        public PageResponse<TraceModels.Catalog> search(TraceModels.Filter filter) {
            return new PageResponse<>(new ArrayList<>(catalogs.values()), 1, Math.max(1, catalogs.size()), catalogs.size());
        }

        @Override
        public TraceModels.EventPage events(String traceId, long afterSequence, int limit) {
            return new TraceModels.EventPage(List.of(), 0, afterSequence);
        }

        @Override
        public TraceModels.EventPage trajectory(String traceId, long afterSequence, int limit) {
            return new TraceModels.EventPage(List.of(), 0, afterSequence);
        }
    }
}
