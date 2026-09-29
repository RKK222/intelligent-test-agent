package com.enterprise.testagent.opencode.runtime.observability;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.trace.TraceCatalogRepository;
import com.enterprise.testagent.domain.trace.TraceModels;
import com.enterprise.testagent.domain.event.RunSessionScopeRepository;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRepository;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import java.util.zip.GZIPInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 服务器 Trace 权威归档。正文按不可变 gzip NDJSON 分片原子落盘，ClickHouse 只写目录和 span 元数据。
 */
@Service
public class TraceArchiveService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TraceArchiveService.class);
    private static final Pattern TRACE_ID = Pattern.compile("trc_[a-f0-9]{32}");
    private static final Pattern EVENT_ID = Pattern.compile("evt_[a-f0-9]{40}");
    private static final Pattern SHA256 = Pattern.compile("[a-f0-9]{64}");
    private static final int MAX_EVENTS_PER_BATCH = 512;
    private static final int MAX_EVENT_BYTES = 16 * 1024 * 1024;
    private static final String SOURCE = "OPENCODE_PLUGIN";

    private final ObjectMapper objectMapper;
    private final TraceCatalogRepository catalogRepository;
    private final RunSessionScopeRepository runSessionScopeRepository;
    private final RunRepository runRepository;
    private final BackendJavaRouteResolver routeResolver;
    private final TraceArchiveSettings settings;
    private final Clock clock;
    private final Counter writeFailures;
    private final Counter droppedEvents;
    private final AtomicLong archiveBacklog = new AtomicLong();
    private final AtomicLong lastSuccessEpochSeconds = new AtomicLong();
    private final AtomicLong diskUsableBytes = new AtomicLong();
    // 固定条带锁避免同一 trace 有等待者时提前移除 Map 锁，导致第三个写入并发穿透。
    private final Object[] traceLocks = createTraceLocks();

    @Autowired
    public TraceArchiveService(
            ObjectMapper objectMapper,
            TraceCatalogRepository catalogRepository,
            RunSessionScopeRepository runSessionScopeRepository,
            RunRepository runRepository,
            BackendJavaRouteResolver routeResolver,
            TraceArchiveSettings settings,
            MeterRegistry meterRegistry) {
        this(objectMapper, catalogRepository, runSessionScopeRepository, runRepository,
                routeResolver, settings, Clock.systemUTC(), meterRegistry);
    }

    TraceArchiveService(
            ObjectMapper objectMapper,
            TraceCatalogRepository catalogRepository,
            RunSessionScopeRepository runSessionScopeRepository,
            RunRepository runRepository,
            BackendJavaRouteResolver routeResolver,
            TraceArchiveSettings settings,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.catalogRepository = Objects.requireNonNull(catalogRepository, "catalogRepository must not be null");
        this.runSessionScopeRepository = Objects.requireNonNull(
                runSessionScopeRepository, "runSessionScopeRepository must not be null");
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.routeResolver = Objects.requireNonNull(routeResolver, "routeResolver must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.writeFailures = meterRegistry.counter("testagent.trace.archive.write.failures");
        this.droppedEvents = meterRegistry.counter("testagent.trace.events.dropped");
        meterRegistry.gauge("testagent.trace.archive.backlog", archiveBacklog);
        meterRegistry.gauge("testagent.trace.archive.last.success.epoch.seconds", lastSuccessEpochSeconds);
        meterRegistry.gauge("testagent.trace.archive.disk.usable.bytes", diskUsableBytes);
    }

    /** 服务端插件批次按 traceId 拆分，任何一个 Trace 的原子性不依赖其它会话。 */
    public OpencodeObservabilityModels.BatchAck ingestPluginBatch(
            OpencodeObservabilityModels.IngestionIdentity identity,
            OpencodeObservabilityModels.PluginBatch batch) {
        validateBatch(identity, batch);
        Map<String, List<JsonNode>> grouped = batch.events().stream()
                .collect(Collectors.groupingBy(
                        event -> requiredText(event, "traceId"),
                        java.util.LinkedHashMap::new,
                        Collectors.toList()));
        List<OpencodeObservabilityModels.TraceAck> acknowledgements = new ArrayList<>();
        for (Map.Entry<String, List<JsonNode>> entry : grouped.entrySet()) {
            byte[] ndjson = ndjson(entry.getValue());
            acknowledgements.add(archive(
                    identity,
                    batch.runtime(),
                    batch.coverageStartAt(),
                    batch.droppedCount(),
                    0L,
                    batch.complete(),
                    entry.getKey(),
                    firstSequence(entry.getValue()),
                    lastSequence(entry.getValue()),
                    sha256(ndjson),
                    ndjson,
                    entry.getValue()));
        }
        return new OpencodeObservabilityModels.BatchAck(acknowledgements);
    }

    /** 本地 WSS 分片已由 relay 脱敏并转为 NDJSON；服务端仍逐行校验 Trace 和序号。 */
    public OpencodeObservabilityModels.TraceAck ingestLocalChunk(
            OpencodeObservabilityModels.IngestionIdentity identity,
            OpencodeObservabilityModels.RuntimeIdentity runtime,
            Instant coverageStartAt,
            long droppedCount,
            long pendingChunks,
            boolean complete,
            String traceId,
            long firstSequence,
            long lastSequence,
            String expectedSha256,
            byte[] ndjson) {
        validateRuntime(identity, runtime);
        validateChunk(traceId, firstSequence, lastSequence, expectedSha256, ndjson);
        List<JsonNode> events = parseNdjson(ndjson);
        if (events.isEmpty()
                || events.stream().anyMatch(event -> !traceId.equals(requiredText(event, "traceId")))
                || firstSequence(events) != firstSequence
                || lastSequence(events) != lastSequence) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Trace 分片序号或身份不一致");
        }
        return archive(identity, runtime, coverageStartAt, droppedCount, pendingChunks, complete, traceId,
                firstSequence, lastSequence, expectedSha256, ndjson, events);
    }

    /** 返回按序拼接的 gzip member 流；HTTP 层可直接以 application/gzip 流式下载。 */
    public InputStream openCompressedTrace(String traceId) {
        validateTraceId(traceId);
        try {
            Manifest manifest = readManifest(traceDirectory(traceId), traceId);
            Vector<InputStream> streams = new Vector<>();
            for (Chunk chunk : manifest.chunks().stream()
                    .sorted(Comparator.comparingLong(Chunk::firstSequence))
                    .toList()) {
                streams.add(Files.newInputStream(
                        checkedChild(traceDirectory(traceId), chunk.fileName()),
                        StandardOpenOption.READ));
            }
            Enumeration<InputStream> enumeration = streams.elements();
            return new SequenceInputStream(enumeration);
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw contentUnavailable(exception);
        }
    }

    /** 按全局序号从压缩分片流式读取正文事件，单次最多 500 条，避免详情页加载整条 Trace。 */
    public OpencodeObservabilityModels.RawEventPage readRawEvents(
            String traceId,
            long afterSequence,
            int limit) {
        validateTraceId(traceId);
        int boundedLimit = Math.max(1, Math.min(500, limit));
        try {
            Path directory = traceDirectory(traceId);
            Manifest manifest = readManifest(directory, traceId);
            List<JsonNode> result = new ArrayList<>();
            for (Chunk chunk : manifest.chunks().stream()
                    .sorted(Comparator.comparingLong(Chunk::firstSequence))
                    .toList()) {
                if (chunk.lastSequence() <= afterSequence) {
                    continue;
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        new GZIPInputStream(Files.newInputStream(
                                checkedChild(directory, chunk.fileName()),
                                StandardOpenOption.READ)),
                        StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null && result.size() < boundedLimit) {
                        if (line.isBlank()) {
                            continue;
                        }
                        JsonNode event = objectMapper.readTree(line);
                        if (longValue(event, "globalSequence") > afterSequence) {
                            result.add(event);
                        }
                    }
                }
                if (result.size() >= boundedLimit) {
                    break;
                }
            }
            return new OpencodeObservabilityModels.RawEventPage(
                    result, manifest.completeThrough(), manifest.complete());
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw contentUnavailable(exception);
        }
    }

    /**
     * 只读取一条 DSH 语义记录的正文。Assistant 按 messageId 汇聚 2.0.18 流式 part，Tool 按 callId
     * 汇聚 before/after；其它类型只返回目标事件及其正文分片，避免点击记录时下载整条 Trace。
     */
    public OpencodeObservabilityModels.RawEventPage readRecordEvents(
            String traceId,
            String eventId,
            long globalSequence) {
        validateTraceId(traceId);
        if (eventId == null || !EVENT_ID.matcher(eventId).matches() || globalSequence < 1) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Trace 记录身份无效");
        }
        try {
            Path directory = traceDirectory(traceId);
            Manifest manifest = readManifest(directory, traceId);
            List<Chunk> chunks = manifest.chunks().stream()
                    .sorted(Comparator.comparingLong(Chunk::firstSequence))
                    .toList();
            JsonNode target = null;
            for (Chunk chunk : chunks) {
                if (globalSequence < chunk.firstSequence() || globalSequence > chunk.lastSequence()) {
                    continue;
                }
                target = readChunkEvents(directory, chunk).stream()
                        .filter(event -> globalSequence == longValue(event, "globalSequence")
                                && eventId.equals(text(event, "eventId")))
                        .findFirst()
                        .orElse(null);
                if (target != null) {
                    break;
                }
            }
            if (target == null) {
                throw new PlatformException(ErrorCode.NOT_FOUND, "Trace 记录不存在");
            }

            String targetType = text(target, "type");
            String callId = text(target, "callId");
            boolean toolRecord = !blank(callId) && targetType.startsWith("TOOL_EXECUTE_");
            // Tool 正文以 callId 为权威；不能再按同 messageId 扩大到整轮 Assistant 流式分片。
            String messageId = toolRecord ? "" : text(target, "messageId");
            boolean includeToolTerminalFallback = "TOOL_EXECUTE_BEFORE".equals(targetType);
            Set<String> fragmentGroups = new HashSet<>();
            Set<String> includedEventIds = new HashSet<>();
            List<JsonNode> result = new ArrayList<>();
            for (Chunk chunk : chunks) {
                // 没有关联标识的 System/User/Context 只可能在目标事件之后携带正文分片。
                if (blank(messageId) && blank(callId) && chunk.lastSequence() < globalSequence) {
                    continue;
                }
                for (JsonNode event : readChunkEvents(directory, chunk)) {
                    String candidateEventId = text(event, "eventId");
                    String fragmentGroup = text(event.path("payload"), "fragmentGroupId");
                    String candidateType = text(event, "type");
                    boolean callIdentityMatch = toolRecord
                            && callId.equals(text(event, "callId"))
                            && (candidateType.startsWith("TOOL_EXECUTE_")
                                    || (includeToolTerminalFallback && "OPENCODE_EVENT".equals(candidateType)));
                    boolean identityMatch = eventId.equals(candidateEventId)
                            || (!blank(messageId) && messageId.equals(text(event, "messageId")))
                            || callIdentityMatch;
                    boolean fragmentMatch = !blank(fragmentGroup) && fragmentGroups.contains(fragmentGroup);
                    if (!identityMatch && !fragmentMatch) {
                        continue;
                    }
                    if (includedEventIds.add(candidateEventId)) {
                        result.add(event);
                    }
                    String primaryFragmentGroup = text(event.path("payload").path("fragmentedPayload"),
                            "fragmentGroupId");
                    if (!blank(primaryFragmentGroup)) {
                        fragmentGroups.add(primaryFragmentGroup);
                    }
                }
            }
            result.sort(Comparator.comparingLong(event -> longValue(event, "globalSequence")));
            return new OpencodeObservabilityModels.RawEventPage(
                    result, manifest.completeThrough(), manifest.complete());
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw contentUnavailable(exception);
        }
    }

    private List<JsonNode> readChunkEvents(Path directory, Chunk chunk) throws IOException {
        List<JsonNode> events = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(
                        checkedChild(directory, chunk.fileName()),
                        StandardOpenOption.READ)),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    events.add(objectMapper.readTree(line));
                }
            }
        }
        return events;
    }

    private OpencodeObservabilityModels.TraceAck archive(
            OpencodeObservabilityModels.IngestionIdentity identity,
            OpencodeObservabilityModels.RuntimeIdentity runtime,
            Instant coverageStartAt,
            long batchDroppedCount,
            long batchPendingChunks,
            boolean batchComplete,
            String traceId,
            long firstSequence,
            long lastSequence,
            String rawSha256,
            byte[] ndjson,
            List<JsonNode> events) {
        validateChunk(traceId, firstSequence, lastSequence, rawSha256, ndjson);
        Object lock = traceLocks[(traceId.hashCode() & Integer.MAX_VALUE) % traceLocks.length];
        archiveBacklog.incrementAndGet();
        synchronized (lock) {
            try {
                Path directory = traceDirectory(identity.user(), traceId);
                Files.createDirectories(directory);
                warnDiskWatermark(directory, ndjson.length);
                Manifest existing = readManifestIfPresent(directory);
                if (existing != null) {
                    validateFrozenNode(existing, identity, runtime);
                    Chunk duplicate = existing.chunks().stream()
                            .filter(chunk -> chunk.firstSequence() == firstSequence
                                    && chunk.lastSequence() == lastSequence)
                            .findFirst()
                            .orElse(null);
                    if (duplicate != null && !duplicate.rawSha256().equals(rawSha256)) {
                        throw new PlatformException(
                                ErrorCode.CONFLICT,
                                "Trace 分片序号已由不同摘要占用",
                                Map.of("reason", "TRACE_CHUNK_DIGEST_CONFLICT"));
                    }
                    if (duplicate != null) {
                        persistCatalog(identity, runtime, coverageStartAt, batchDroppedCount, batchPendingChunks, batchComplete,
                                existing, events, false);
                        lastSuccessEpochSeconds.set(clock.instant().getEpochSecond());
                        return ack(traceId, duplicate, existing, true);
                    }
                }

                byte[] compressed = gzip(ndjson);
                String fileName = "%020d-%020d-%s.ndjson.gz".formatted(
                        firstSequence, lastSequence, rawSha256.substring(0, 16));
                Path target = checkedChild(directory, fileName);
                if (Files.isRegularFile(target)) {
                    // 崩溃可能发生在正文原子 move 之后、manifest move 之前；只接受正文摘要完全一致的孤儿分片。
                    byte[] recovered = gunzip(Files.readAllBytes(target));
                    if (!MessageDigest.isEqual(sha256(recovered).getBytes(StandardCharsets.US_ASCII),
                            rawSha256.getBytes(StandardCharsets.US_ASCII))) {
                        throw new PlatformException(ErrorCode.CONFLICT, "Trace 孤儿分片摘要冲突");
                    }
                    compressed = Files.readAllBytes(target);
                } else {
                    Path temporary = Files.createTempFile(directory, ".trace-", ".tmp");
                    try {
                        Files.write(temporary, compressed, StandardOpenOption.TRUNCATE_EXISTING);
                        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                    } finally {
                        Files.deleteIfExists(temporary);
                    }
                }
                Chunk chunk = new Chunk(
                        firstSequence,
                        lastSequence,
                        rawSha256,
                        sha256(compressed),
                        ndjson.length,
                        compressed.length,
                        events.size(),
                        fileName,
                        clock.instant());
                Manifest updated = appendManifest(existing, identity, runtime, traceId, chunk,
                        coverageStartAt, batchDroppedCount, batchPendingChunks, batchComplete, events);
                writeManifest(directory, updated);
                persistCatalog(identity, runtime, coverageStartAt, batchDroppedCount, batchPendingChunks, batchComplete,
                        updated, events, false);
                lastSuccessEpochSeconds.set(clock.instant().getEpochSecond());
                return ack(traceId, chunk, updated, false);
            } catch (PlatformException exception) {
                writeFailures.increment();
                throw exception;
            } catch (Exception exception) {
                writeFailures.increment();
                throw contentUnavailable(exception);
            } finally {
                archiveBacklog.decrementAndGet();
            }
        }
    }

    private void persistCatalog(
            OpencodeObservabilityModels.IngestionIdentity identity,
            OpencodeObservabilityModels.RuntimeIdentity runtime,
            Instant coverageStartAt,
            long batchDroppedCount,
            long batchPendingChunks,
            boolean batchComplete,
            Manifest manifest,
            List<JsonNode> events,
            boolean duplicate) {
        User user = identity.user();
        JsonNode first = events.get(0);
        RunCorrelation correlation = resolveRunCorrelation(user, events);
        TraceModels.Catalog previousCatalog = catalogRepository.find(manifest.traceId()).orElse(null);
        String runId = firstNonBlank(correlation.runId(),
                previousCatalog == null ? null : previousCatalog.runId());
        List<TraceModels.Span> spans = duplicate ? List.of() : events.stream()
                .map(event -> toSpan(event, runId))
                .toList();
        List<TraceModels.CapabilityFact> facts = spans.stream()
                .filter(span -> !blank(span.capabilityKind()) && !blank(span.capabilityName()) && !blank(span.callId()))
                .map(span -> toCapabilityFact(user, span))
                .toList();
        if (correlation.run() != null && facts.stream().anyMatch(this::isConciseOutputSkill)) {
            runRepository.markConciseOutputSelected(correlation.run().runId());
        }
        String sessionId = firstNonBlank(events, event -> text(event, "sessionId"));
        boolean terminal = manifest.complete() || events.stream().anyMatch(this::isTerminalEvent);
        long dropped = Math.max(manifest.droppedCount(), batchDroppedCount);
        if (dropped > 0) {
            droppedEvents.increment(dropped);
        }
        long pendingChunks = Math.max(0, batchPendingChunks);
        boolean complete = terminal && batchComplete && dropped == 0 && pendingChunks == 0;
        Instant now = clock.instant();
        // 以服务器首次成功接收该插件 Trace 的时刻冻结覆盖起点，避免插件启动后到首批落库前出现旧事实空窗。
        Instant effectiveCoverageStartAt = previousCatalog == null
                ? now
                : previousCatalog.coverageStartAt();
        TraceModels.Catalog catalog = new TraceModels.Catalog(
                manifest.traceId(),
                user.userId().value(),
                user.username(),
                value(user.organization()),
                value(user.rdDepartment()),
                value(user.department()),
                value(runtime.kind()),
                SOURCE,
                value(identity.processId()),
                value(identity.clientInstanceId()),
                manifest.backendProcessId(),
                manifest.linuxServerId(),
                value(sessionId),
                value(runId),
                firstNonBlank(
                        firstNonBlank(events, this::agentName),
                        firstNonBlank(correlation.agentId(),
                                previousCatalog == null ? "unknown" : previousCatalog.agentId())),
                terminal ? "COMPLETED" : "ACTIVE",
                dropped > 0 || pendingChunks > 0 ? "INCOMPLETE" : "ARCHIVED",
                manifest.createdAt(),
                now,
                effectiveCoverageStartAt,
                manifest.completeThrough(),
                manifest.eventCount(),
                manifest.archivedBytes(),
                dropped,
                pendingChunks,
                complete,
                true);
        catalogRepository.save(catalog, spans, facts, now);
    }

    /**
     * OpenCode Hook 不知道平台 Run ID；复用既有 Run session scope 反查，并校验该 Run 属于令牌用户。
     */
    private RunCorrelation resolveRunCorrelation(User user, List<JsonNode> events) {
        String eventRunId = firstNonBlank(events, event -> text(event, "runId"));
        String remoteSessionId = firstNonBlank(events, event -> text(event, "sessionId"));
        Run run = findOwnedRun(user, eventRunId);
        if (run == null && !blank(remoteSessionId)) {
            run = runSessionScopeRepository.findLatestBySessionId(remoteSessionId)
                    .flatMap(scope -> runRepository.findById(scope.runId()))
                    .filter(candidate -> ownedBy(user, candidate))
                    .orElse(null);
        }
        return run == null
                ? new RunCorrelation(eventRunId, null, null)
                : new RunCorrelation(run.runId().value(), run.agentId(), run);
    }

    /** 只认 OpenCode 已归类的 SKILL 事实，避免从 prompt 或工具正文猜测用户是否选择 Skill。 */
    private boolean isConciseOutputSkill(TraceModels.CapabilityFact fact) {
        return "SKILL".equalsIgnoreCase(fact.capabilityType())
                && "concise-output".equalsIgnoreCase(fact.capabilityName());
    }

    private Run findOwnedRun(User user, String runId) {
        if (blank(runId)) {
            return null;
        }
        try {
            return runRepository.findById(new RunId(runId))
                    .filter(candidate -> ownedBy(user, candidate))
                    .orElse(null);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean ownedBy(User user, Run run) {
        return run.triggeredByUserId() != null && run.triggeredByUserId().equals(user.userId());
    }

    private TraceModels.Span toSpan(JsonNode event, String resolvedRunId) {
        JsonNode payload = event.path("payload");
        String type = text(event, "type");
        String capabilityKind = text(payload, "capabilityKind");
        String capabilityName = "SKILL".equals(capabilityKind)
                ? text(payload, "skillName")
                : text(payload, "tool");
        if ("AGENT".equals(capabilityKind)) {
            capabilityName = firstNonBlank(text(payload, "agentName"), capabilityName);
        }
        return new TraceModels.Span(
                requiredText(event, "traceId"),
                requiredText(event, "eventId"),
                value(type),
                lane(type, payload),
                recordKind(type, payload),
                parseInstant(text(event, "timestamp")),
                startedAt(payload),
                longValue(event, "globalSequence"),
                longValue(event, "sessionSequence"),
                text(event, "sessionId"),
                firstNonBlank(text(event, "runId"), resolvedRunId),
                text(event, "turnId"),
                text(event, "stepId"),
                text(event, "messageId"),
                text(event, "callId"),
                text(event, "parentId"),
                value(capabilityKind),
                value(capabilityName),
                status(type, payload),
                durationMs(payload),
                token(payload, "tokensInput", "input"),
                token(payload, "tokensOutput", "output"),
                token(payload, "tokensReasoning", "reasoning"),
                cacheToken(payload, "tokensCacheRead", "read"),
                cacheToken(payload, "tokensCacheWrite", "write"),
                totalTokens(payload),
                nullableLong(payload, "ttftMs"),
                nullableLong(payload, "decodeMs"),
                firstPositive(longValue(payload, "decodeTokens"), token(payload, "tokensOutput", "output")),
                cost(payload),
                finishReason(payload),
                SOURCE);
    }

    private TraceModels.CapabilityFact toCapabilityFact(User user, TraceModels.Span span) {
        String stableCall = "capability:" + firstNonBlank(span.runId(), span.traceId())
                + ":" + value(span.sessionId()) + ":" + span.callId();
        long version = span.globalSequence() * 10 + ("TOOL_EXECUTE_AFTER".equals(span.type()) ? 1 : 0);
        return new TraceModels.CapabilityFact(
                stableCall,
                Math.max(1, version),
                span.occurredAt(),
                user.userId().value(),
                user.username(),
                value(user.organization()),
                value(user.rdDepartment()),
                value(user.department()),
                value(span.sessionId()),
                value(span.runId()),
                span.callId(),
                span.capabilityKind(),
                span.capabilityName(),
                span.status(),
                span.durationMs(),
                SOURCE);
    }

    private Manifest appendManifest(
            Manifest existing,
            OpencodeObservabilityModels.IngestionIdentity identity,
            OpencodeObservabilityModels.RuntimeIdentity runtime,
            String traceId,
            Chunk chunk,
            Instant coverageStartAt,
            long droppedCount,
            long pendingChunks,
            boolean batchComplete,
            List<JsonNode> events) {
        List<Chunk> chunks = new ArrayList<>(existing == null ? List.of() : existing.chunks());
        chunks.add(chunk);
        chunks.sort(Comparator.comparingLong(Chunk::firstSequence));
        long eventCount = chunks.stream().mapToLong(Chunk::eventCount).sum();
        long archivedBytes = chunks.stream().mapToLong(Chunk::compressedBytes).sum();
        long completeThrough = chunks.stream().mapToLong(Chunk::lastSequence).max().orElse(0);
        Instant createdAt = existing == null
                ? (coverageStartAt == null ? parseInstant(text(events.get(0), "timestamp")) : coverageStartAt)
                : existing.createdAt();
        return new Manifest(
                "1.0",
                traceId,
                routeResolver.currentBackendProcessIdValue(),
                routeResolver.currentLinuxServerIdValue(),
                identity.user().userId().value(),
                value(runtime.kind()),
                identity.generation(),
                createdAt,
                clock.instant(),
                completeThrough,
                eventCount,
                archivedBytes,
                Math.max(existing == null ? 0 : existing.droppedCount(), droppedCount),
                (existing != null && existing.complete())
                        || (events.stream().anyMatch(this::isTerminalEvent)
                            && batchComplete && droppedCount == 0 && pendingChunks == 0),
                List.copyOf(chunks));
    }

    private void validateBatch(
            OpencodeObservabilityModels.IngestionIdentity identity,
            OpencodeObservabilityModels.PluginBatch batch) {
        if (batch == null || !"1.0".equals(batch.schemaVersion())) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "不支持的 Observability schema version");
        }
        validateRuntime(identity, batch.runtime());
        if (batch.events().isEmpty() || batch.events().size() > MAX_EVENTS_PER_BATCH) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Observability 批次事件数量无效");
        }
        for (JsonNode event : batch.events()) {
            validateEvent(event);
        }
    }

    private void validateRuntime(
            OpencodeObservabilityModels.IngestionIdentity identity,
            OpencodeObservabilityModels.RuntimeIdentity runtime) {
        if (identity == null || identity.user() == null || runtime == null
                || blank(runtime.kind()) || blank(runtime.generation())
                || !MessageDigest.isEqual(
                        identity.generation().getBytes(StandardCharsets.UTF_8),
                        runtime.generation().getBytes(StandardCharsets.UTF_8))) {
            throw new PlatformException(ErrorCode.UNAUTHENTICATED, "Observability runtime identity 不匹配");
        }
        if (!blank(identity.processId()) && !identity.processId().equals(runtime.processId())) {
            throw new PlatformException(ErrorCode.UNAUTHENTICATED, "Observability process identity 不匹配");
        }
        if (!blank(identity.clientInstanceId()) && !identity.clientInstanceId().equals(runtime.clientInstanceId())) {
            throw new PlatformException(ErrorCode.UNAUTHENTICATED, "Observability client identity 不匹配");
        }
    }

    private void validateEvent(JsonNode event) {
        validateTraceId(requiredText(event, "traceId"));
        if (blank(requiredText(event, "eventId"))
                || longValue(event, "globalSequence") < 1
                || longValue(event, "sessionSequence") < 1
                || blank(requiredText(event, "sessionId"))) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Observability 事件身份或序号无效");
        }
        parseInstant(requiredText(event, "timestamp"));
    }

    private void validateChunk(
            String traceId,
            long firstSequence,
            long lastSequence,
            String expectedSha256,
            byte[] ndjson) {
        validateTraceId(traceId);
        if (firstSequence < 1 || lastSequence < firstSequence
                || ndjson == null || ndjson.length == 0 || ndjson.length > MAX_EVENT_BYTES
                || expectedSha256 == null || !SHA256.matcher(expectedSha256).matches()
                || !MessageDigest.isEqual(
                        expectedSha256.getBytes(StandardCharsets.US_ASCII),
                        sha256(ndjson).getBytes(StandardCharsets.US_ASCII))) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Trace 分片长度、序号或摘要无效");
        }
    }

    private void validateFrozenNode(
            Manifest manifest,
            OpencodeObservabilityModels.IngestionIdentity identity,
            OpencodeObservabilityModels.RuntimeIdentity runtime) {
        // 归档只冻结到持久化存储节点；同节点 Java 重启后必须允许原 generation 断点续传。
        if (!manifest.linuxServerId().equals(routeResolver.currentLinuxServerIdValue())
                || !manifest.userId().equals(identity.user().userId().value())
                || !manifest.runtimeKind().equals(value(runtime.kind()))
                || !MessageDigest.isEqual(
                        manifest.generation().getBytes(StandardCharsets.UTF_8),
                        identity.generation().getBytes(StandardCharsets.UTF_8))) {
            throw new PlatformException(ErrorCode.CONFLICT, "Trace 归档节点或身份已经冻结");
        }
    }

    private Path traceDirectory(User user, String traceId) {
        validateTraceId(traceId);
        Path userDirectory = settings.archiveRoot().resolve(sha256(user.userId().value()).substring(0, 32));
        return checkedChild(userDirectory, traceId);
    }

    private Path traceDirectory(String traceId) {
        TraceModels.Catalog catalog = catalogRepository.find(traceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Trace 不存在"));
        Path userDirectory = settings.archiveRoot().resolve(sha256(catalog.userId()).substring(0, 32));
        return checkedChild(userDirectory, traceId);
    }

    private Path checkedChild(Path parent, String child) {
        Path normalizedParent = parent.toAbsolutePath().normalize();
        Path resolved = normalizedParent.resolve(child).normalize();
        if (!resolved.startsWith(normalizedParent)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "非法 Trace 归档标识");
        }
        return resolved;
    }

    private Manifest readManifestIfPresent(Path directory) throws IOException {
        Path manifest = checkedChild(directory, "manifest.json");
        return Files.exists(manifest) ? objectMapper.readValue(manifest.toFile(), Manifest.class) : null;
    }

    private Manifest readManifest(Path directory, String traceId) throws IOException {
        Manifest manifest = readManifestIfPresent(directory);
        if (manifest == null || !traceId.equals(manifest.traceId())) {
            throw new PlatformException(ErrorCode.TRACE_CONTENT_UNAVAILABLE, "Trace 正文尚未归档");
        }
        return manifest;
    }

    private void writeManifest(Path directory, Manifest manifest) throws IOException {
        Path temporary = Files.createTempFile(directory, ".manifest-", ".tmp");
        try {
            objectMapper.writeValue(temporary.toFile(), manifest);
            Files.move(temporary, checkedChild(directory, "manifest.json"),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void warnDiskWatermark(Path directory, long incomingBytes) throws IOException {
        FileStore store = Files.getFileStore(directory);
        long usable = store.getUsableSpace();
        diskUsableBytes.set(usable);
        if (usable < settings.warningFreeBytes()) {
            LOGGER.warn("event=trace_archive_disk_watermark usableBytes={} warningFreeBytes={}",
                    usable, settings.warningFreeBytes());
        }
        if (usable < incomingBytes * 2L + 10L * 1024 * 1024) {
            throw new PlatformException(ErrorCode.TRACE_CONTENT_UNAVAILABLE, "Trace 归档磁盘空间不足");
        }
    }

    private List<JsonNode> parseNdjson(byte[] ndjson) {
        try {
            List<JsonNode> events = new ArrayList<>();
            for (String line : new String(ndjson, StandardCharsets.UTF_8).split("\\n")) {
                if (!line.isBlank()) {
                    JsonNode event = objectMapper.readTree(line);
                    validateEvent(event);
                    events.add(event);
                }
            }
            if (events.size() > MAX_EVENTS_PER_BATCH) {
                throw new PlatformException(ErrorCode.PAYLOAD_TOO_LARGE, "Trace 分片事件数量超过上限");
            }
            return events;
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Trace 分片不是有效 NDJSON");
        }
    }

    private byte[] ndjson(List<JsonNode> events) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            for (JsonNode event : events) {
                byte[] bytes = objectMapper.writeValueAsBytes(event);
                output.write(bytes);
                output.write('\n');
            }
            byte[] result = output.toByteArray();
            if (result.length > MAX_EVENT_BYTES) {
                throw new PlatformException(ErrorCode.PAYLOAD_TOO_LARGE, "Observability 批次超过上限");
            }
            return result;
        } catch (IOException exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "Observability 事件序列化失败");
        }
    }

    private byte[] gzip(byte[] value) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output, 64 * 1024)) {
            gzip.write(value);
        }
        return output.toByteArray();
    }

    private byte[] gunzip(byte[] value) throws IOException {
        try (GZIPInputStream input = new GZIPInputStream(new java.io.ByteArrayInputStream(value))) {
            return input.readAllBytes();
        }
    }

    private long firstSequence(List<JsonNode> events) {
        return events.stream().mapToLong(event -> longValue(event, "globalSequence")).min().orElseThrow();
    }

    private long lastSequence(List<JsonNode> events) {
        return events.stream().mapToLong(event -> longValue(event, "globalSequence")).max().orElseThrow();
    }

    private String lane(String type, JsonNode payload) {
        if (type == null) {
            return "MODEL";
        }
        if ("PAYLOAD_FRAGMENT".equals(type)) {
            return firstNonBlank(text(payload, "originalLane"), "MODEL");
        }
        if (type.startsWith("TOOL_")) {
            return "TOOLS";
        }
        if (type.equals("CHAT_MESSAGE") || type.equals("SYSTEM_PROMPT") || type.equals("CONTEXT_MESSAGES")) {
            return "INPUT";
        }
        String opencodeType = text(payload.path("event"), "type");
        return opencodeType != null && opencodeType.contains("tool") ? "TOOLS" : "MODEL";
    }

    private String status(String type, JsonNode payload) {
        String explicit = text(payload, "status");
        if (!blank(explicit)) {
            return explicit;
        }
        return "TOOL_EXECUTE_BEFORE".equals(type) ? "STARTED" : "COMPLETED";
    }

    /** DSH Trajectory 的闭集记录类型；插件派生类型优先，其余从 2.0.18 事件结构确定。 */
    private String recordKind(String type, JsonNode payload) {
        String explicit = text(payload, "recordKind");
        if (!blank(explicit)) {
            return explicit;
        }
        if ("SYSTEM_PROMPT".equals(type)) {
            return "system";
        }
        if ("CHAT_MESSAGE".equals(type)) {
            return "user";
        }
        if ("CONTEXT_MESSAGES".equals(type)) {
            return "context";
        }
        if (type != null && type.startsWith("TOOL_")) {
            return "tool";
        }
        String partType = text(payload.path("event").path("properties").path("part"), "type");
        return "compaction".equals(partType) ? "compacted" : "message";
    }

    private boolean isTerminalEvent(JsonNode event) {
        JsonNode nested = event.path("payload").path("event");
        String eventType = text(nested, "type");
        return "session.idle".equals(eventType)
                || "session.deleted".equals(eventType)
                || "session.error".equals(eventType);
    }

    private String agentName(JsonNode event) {
        JsonNode payload = event.path("payload");
        JsonNode properties = payload.path("event").path("properties");
        return firstNonBlank(
                text(payload, "agentName"),
                firstNonBlank(
                        // OpenCode 2.0.18 chat.message 公开契约把 Agent 放在 input.agent；
                        // 新插件同时提升为 payload.agentName，以下路径用于兼容已上传的旧批次。
                        text(payload.path("input"), "agent"),
                        firstNonBlank(
                                text(payload.path("message").path("info"), "agent"),
                                firstNonBlank(text(properties, "agent"), text(properties.path("info"), "agent")))));
    }

    private long token(JsonNode payload, String directName, String usageName) {
        long direct = longValue(payload, directName);
        if (direct > 0) {
            return direct;
        }
        long usage = longValue(payload.path("usage"), usageName);
        if (usage > 0) {
            return usage;
        }
        JsonNode properties = payload.path("event").path("properties");
        long partToken = longValue(properties.path("part").path("tokens"), usageName);
        return partToken > 0 ? partToken : longValue(properties.path("info").path("tokens"), usageName);
    }

    private long cacheToken(JsonNode payload, String directName, String cacheName) {
        long direct = longValue(payload, directName);
        if (direct > 0) {
            return direct;
        }
        JsonNode properties = payload.path("event").path("properties");
        long fromPart = longValue(properties.path("part").path("tokens").path("cache"), cacheName);
        return fromPart > 0
                ? fromPart
                : longValue(properties.path("info").path("tokens").path("cache"), cacheName);
    }

    private long totalTokens(JsonNode payload) {
        long direct = longValue(payload, "tokensTotal");
        if (direct > 0) {
            return direct;
        }
        JsonNode properties = payload.path("event").path("properties");
        long eventTotal = firstPositive(
                longValue(properties.path("part").path("tokens"), "total"),
                longValue(properties.path("info").path("tokens"), "total"));
        return eventTotal > 0
                ? eventTotal
                : token(payload, "tokensInput", "input")
                    + token(payload, "tokensOutput", "output")
                    + token(payload, "tokensReasoning", "reasoning");
    }

    private Double cost(JsonNode payload) {
        Double direct = nullableDouble(payload, "cost");
        if (direct != null) {
            return direct;
        }
        JsonNode properties = payload.path("event").path("properties");
        Double partCost = nullableDouble(properties.path("part"), "cost");
        return partCost == null ? nullableDouble(properties.path("info"), "cost") : partCost;
    }

    private String finishReason(JsonNode payload) {
        JsonNode properties = payload.path("event").path("properties");
        return firstNonBlank(
                text(payload, "finishReason"),
                firstNonBlank(text(properties.path("part"), "reason"), text(properties.path("info"), "finish")));
    }

    private Instant startedAt(JsonNode payload) {
        String direct = text(payload, "startedAt");
        if (!blank(direct)) {
            return parseInstant(direct);
        }
        JsonNode properties = payload.path("event").path("properties");
        JsonNode part = properties.path("part");
        long epochMs = firstPositive(
                longValue(part.path("state").path("time"), "start"),
                longValue(part.path("time"), "start"),
                longValue(properties.path("info").path("time"), "created"));
        return epochMs > 0 ? Instant.ofEpochMilli(epochMs) : null;
    }

    private long firstPositive(long... values) {
        for (long candidate : values) {
            if (candidate > 0) {
                return candidate;
            }
        }
        return 0;
    }

    private Long nullableLong(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isNumber() ? null : Math.max(0L, value.asLong());
    }

    private Double nullableDouble(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isNumber() || !Double.isFinite(value.asDouble())
                ? null
                : Math.max(0D, value.asDouble());
    }

    /** 兼容插件派生耗时和 OpenCode 2.0.18 message/part 的真实毫秒时间结构。 */
    private long durationMs(JsonNode payload) {
        long direct = longValue(payload, "durationMs");
        if (direct > 0) {
            return direct;
        }
        JsonNode properties = payload.path("event").path("properties");
        JsonNode part = properties.path("part");
        long fromPartState = elapsed(part.path("state").path("time"));
        if (fromPartState > 0) {
            return fromPartState;
        }
        long fromPart = elapsed(part.path("time"));
        if (fromPart > 0) {
            return fromPart;
        }
        return elapsed(properties.path("info").path("time"));
    }

    private long elapsed(JsonNode time) {
        long start = longValue(time, "start");
        if (start <= 0) {
            start = longValue(time, "created");
        }
        long end = longValue(time, "end");
        if (end <= 0) {
            end = longValue(time, "completed");
        }
        return start > 0 && end >= start ? end - start : 0;
    }

    private String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (blank(value)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Observability 事件缺少 " + field);
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText(null);
    }

    private long longValue(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return 0;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? 0 : value.asLong(0);
    }

    private Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Observability 时间戳无效");
        }
    }

    private String firstNonBlank(List<JsonNode> events, Function<JsonNode, String> mapper) {
        return firstNonBlank(events, mapper, "");
    }

    private String firstNonBlank(List<JsonNode> events, Function<JsonNode, String> mapper, String fallback) {
        return events.stream().map(mapper).filter(value -> !blank(value)).findFirst().orElse(fallback);
    }

    private String firstNonBlank(String first, String second) {
        return !blank(first) ? first : value(second);
    }

    private record RunCorrelation(String runId, String agentId, Run run) {
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private void validateTraceId(String traceId) {
        if (traceId == null || !TRACE_ID.matcher(traceId).matches()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Trace ID 无效");
        }
    }

    private String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JRE 不支持 SHA-256", exception);
        }
    }

    private OpencodeObservabilityModels.TraceAck ack(
            String traceId,
            Chunk chunk,
            Manifest manifest,
            boolean duplicate) {
        return new OpencodeObservabilityModels.TraceAck(
                traceId,
                chunk.firstSequence(),
                chunk.lastSequence(),
                chunk.rawSha256(),
                chunk.rawBytes(),
                manifest.completeThrough(),
                duplicate,
                manifest.droppedCount() > 0 ? "INCOMPLETE" : "ARCHIVED",
                clock.instant());
    }

    private PlatformException contentUnavailable(Exception cause) {
        return new PlatformException(
                ErrorCode.TRACE_CONTENT_UNAVAILABLE,
                "Trace 正文归档暂不可用",
                Map.of(),
                cause);
    }

    private static Object[] createTraceLocks() {
        Object[] locks = new Object[256];
        java.util.Arrays.setAll(locks, ignored -> new Object());
        return locks;
    }

    private record Chunk(
            long firstSequence,
            long lastSequence,
            String rawSha256,
            String compressedSha256,
            long rawBytes,
            long compressedBytes,
            long eventCount,
            String fileName,
            Instant archivedAt) {
    }

    private record Manifest(
            String schemaVersion,
            String traceId,
            String backendProcessId,
            String linuxServerId,
            String userId,
            String runtimeKind,
            String generation,
            Instant createdAt,
            Instant updatedAt,
            long completeThrough,
            long eventCount,
            long archivedBytes,
            long droppedCount,
            boolean complete,
            List<Chunk> chunks) {

        private Manifest {
            chunks = chunks == null ? List.of() : List.copyOf(chunks);
        }
    }
}
