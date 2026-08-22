package com.enterprise.testagent.localclient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 独立 loopback Observability relay：使用随机 token，先持久化未确认 spool，再由 WSS 调度器低优先级上传。
 */
final class LocalObservabilityRelay implements AutoCloseable {

    static final String EVENTS_PATH = "/api/internal/agent/opencode-observability/v1/events";
    static final int MAX_UPLOAD_CHUNK_BYTES = LocalObservabilitySettings.DEFAULT_CHUNK_BYTES;
    private static final int MAX_REQUEST_BYTES = 16 * 1024 * 1024;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Path spoolDirectory;
    private final Path blockedDirectory;
    private final HttpServer server;
    private final ExecutorService httpExecutor;
    private final ExecutorService writer;
    private final String localToken = token();
    private final long maxSpoolBytes;
    private final long maxMemoryQueueBytes;
    private final int uploadChunkBytes;
    private final AtomicLong queuedBytes = new AtomicLong();
    private final AtomicBoolean degraded = new AtomicBoolean();

    LocalObservabilityRelay(LocalClientStateStore stateStore) throws IOException {
        this(stateStore, LocalObservabilitySettings.defaults());
    }

    LocalObservabilityRelay(LocalClientStateStore stateStore, LocalObservabilitySettings settings) throws IOException {
        this(stateStore.stateDirectory().resolve("observability-spool"), settings);
    }

    LocalObservabilityRelay(Path spoolDirectory, long maxSpoolBytes) throws IOException {
        this(spoolDirectory, new LocalObservabilitySettings(
                LocalObservabilitySettings.DEFAULT_CHUNK_BYTES,
                LocalObservabilitySettings.MAX_IN_FLIGHT,
                LocalObservabilitySettings.DEFAULT_UPLOAD_BYTES_PER_SECOND,
                LocalObservabilitySettings.DEFAULT_IDLE_BEFORE_UPLOAD,
                LocalObservabilitySettings.DEFAULT_ACKNOWLEDGEMENT_TIMEOUT,
                maxSpoolBytes,
                LocalObservabilitySettings.DEFAULT_MEMORY_QUEUE_MAX_BYTES));
    }

    LocalObservabilityRelay(Path spoolDirectory, LocalObservabilitySettings settings) throws IOException {
        this.spoolDirectory = spoolDirectory.toAbsolutePath().normalize();
        this.blockedDirectory = this.spoolDirectory.resolve("blocked");
        this.maxSpoolBytes = settings.spoolMaxBytes();
        this.maxMemoryQueueBytes = settings.memoryQueueMaxBytes();
        this.uploadChunkBytes = settings.chunkBytes();
        createPrivateDirectory(this.spoolDirectory);
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 32);
        this.httpExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = Thread.ofPlatform().name("local-observability-writer").unstarted(runnable);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
        server.setExecutor(httpExecutor);
        server.createContext(EVENTS_PATH, this::handle);
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    String localToken() {
        return localToken;
    }

    boolean degraded() {
        return degraded.get();
    }

    /** 返回最早一个完整、摘要可验证的未确认分片，不把文件正文长期保留在内存。 */
    synchronized PendingChunk nextPending() {
        try (var files = Files.list(spoolDirectory)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::readPending)
                    .filter(java.util.Objects::nonNull)
                    // batchId 是随机值；同一 runtime/trace 必须按事件序号上传，不能按文件名碰运气。
                    .sorted(Comparator.comparing(PendingChunk::runtimeGeneration)
                            .thenComparing(PendingChunk::traceId)
                            .thenComparingLong(PendingChunk::firstSequence)
                            .thenComparing(PendingChunk::createdAt)
                            .thenComparing(PendingChunk::batchId))
                    .findFirst()
                    .orElse(null);
        } catch (IOException exception) {
            degraded.set(true);
            return null;
        }
    }

    synchronized byte[] readBody(PendingChunk pending) {
        try {
            byte[] bytes = Files.readAllBytes(dataPath(pending.batchId()));
            if (bytes.length != pending.contentLength() || !sha256(bytes).equals(pending.sha256())) {
                degraded.set(true);
                throw new IllegalStateException("observability spool checksum mismatch");
            }
            return bytes;
        } catch (IOException exception) {
            degraded.set(true);
            throw new IllegalStateException("failed to read observability spool", exception);
        }
    }

    /** 只有服务器 ACK 坐标和摘要全部匹配时才删除未确认分片。 */
    synchronized void acknowledge(String batchId, String traceId, long lastSequence, String sha256) {
        PendingChunk pending = readPending(metaPath(batchId));
        if (pending == null
                || !pending.traceId().equals(traceId)
                || pending.lastSequence() != lastSequence
                || !pending.sha256().equals(sha256)) {
            throw new IllegalArgumentException("trace acknowledgement does not match pending spool");
        }
        try {
            Files.delete(dataPath(batchId));
            Files.delete(metaPath(batchId));
        } catch (IOException exception) {
            degraded.set(true);
            throw new IllegalStateException("failed to acknowledge observability spool", exception);
        }
    }

    /**
     * 服务器确认同一序号区间已存在不同摘要时，分片不可能靠重试自愈。将其移入持久隔离区以免
     * 饿死其它 Trace；正文和 metadata 均保留且继续计入磁盘预算，绝不伪装成已 ACK 删除。
     */
    synchronized void quarantine(PendingChunk pending, String reason) {
        PendingChunk current = pending == null ? null : readMetadata(metaPath(pending.batchId()));
        if (current == null || !current.equals(pending)) {
            throw new IllegalArgumentException("observability quarantine does not match pending spool");
        }
        try {
            createPrivateDirectory(blockedDirectory);
            Path sourceData = dataPath(pending.batchId());
            Path blockedData = blockedPath(pending.batchId(), ".ndjson");
            Path sourceMeta = metaPath(pending.batchId());
            Path blockedMeta = blockedPath(pending.batchId(), ".json");
            // 先移 metadata：进程若在两次原子移动之间退出，根目录不会留下“可上传 metadata + 已搬走正文”
            // 这种会永久阻塞队头的半状态；正文仍留在 spool 并计入空间预算，重启后可人工恢复隔离。
            if (Files.isRegularFile(sourceMeta)) {
                Files.move(sourceMeta, blockedMeta, StandardCopyOption.ATOMIC_MOVE);
            }
            if (Files.isRegularFile(sourceData)) {
                Files.move(sourceData, blockedData, StandardCopyOption.ATOMIC_MOVE);
            }
            degraded.set(true);
            writeDegradedMarker(pending.traceId(), reason);
        } catch (IOException exception) {
            degraded.set(true);
            throw new IllegalStateException("failed to quarantine observability spool", exception);
        }
    }

    long backlogBytes() {
        try (var files = Files.walk(spoolDirectory, 2)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".ndjson"))
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException exception) {
                            return 0;
                        }
                    })
                    .sum();
        } catch (IOException exception) {
            degraded.set(true);
            return 0;
        }
    }

    long pendingChunkCount() {
        try (var files = Files.list(spoolDirectory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::readPending)
                    .filter(java.util.Objects::nonNull)
                    .count();
        } catch (IOException exception) {
            degraded.set(true);
            return 0;
        }
    }

    long blockedChunkCount() {
        if (!Files.isDirectory(blockedDirectory)) {
            return 0;
        }
        try (var files = Files.list(blockedDirectory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json")).count();
        } catch (IOException exception) {
            degraded.set(true);
            return 0;
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())
                    || !("Bearer " + localToken).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                respond(exchange, 401, "{\"error\":\"observability relay authentication failed\"}");
                return;
            }
            byte[] request = readBounded(exchange, MAX_REQUEST_BYTES);
            long queued = queuedBytes.addAndGet(request.length);
            if (queued > maxMemoryQueueBytes) {
                queuedBytes.addAndGet(-request.length);
                degraded.set(true);
                respond(exchange, 429, "{\"error\":\"observability relay queue full\"}");
                return;
            }
            CompletableFuture<Void> accepted = new CompletableFuture<>();
            writer.execute(() -> {
                try {
                    spool(request);
                    accepted.complete(null);
                } catch (Exception exception) {
                    degraded.set(true);
                    accepted.completeExceptionally(exception);
                } finally {
                    queuedBytes.addAndGet(-request.length);
                }
            });
            try {
                accepted.get(30, TimeUnit.SECONDS);
                respond(exchange, 202, "{\"accepted\":true}");
            } catch (Exception exception) {
                respond(exchange, 507, "{\"error\":\"observability spool unavailable\"}");
            }
        }
    }

    private synchronized void spool(byte[] request) throws IOException {
        JsonNode batch = objectMapper.readTree(request);
        if (!"1.0".equals(batch.path("schemaVersion").asText()) || !batch.path("events").isArray()) {
            throw new IllegalArgumentException("invalid observability batch");
        }
        JsonNode runtime = batch.path("runtime");
        String runtimeGeneration = required(runtime, "generation");
        String runtimeKind = required(runtime, "kind");
        Instant coverageStartAt = Instant.parse(required(batch, "coverageStartAt"));
        long droppedCount = Math.max(0, batch.path("droppedCount").asLong());
        boolean complete = batch.path("complete").asBoolean(false);
        Map<String, List<JsonNode>> groups = new java.util.LinkedHashMap<>();
        batch.path("events").forEach(event -> groups
                .computeIfAbsent(required(event, "traceId"), ignored -> new ArrayList<>())
                .add(event));
        for (Map.Entry<String, List<JsonNode>> group : groups.entrySet()) {
            writeTraceBatch(
                    group.getKey(), runtimeKind, runtimeGeneration, coverageStartAt,
                    droppedCount, complete, group.getValue());
        }
    }

    private void writeTraceBatch(
            String traceId,
            String runtimeKind,
            String runtimeGeneration,
            Instant coverageStartAt,
            long droppedCount,
            boolean complete,
            List<JsonNode> events) throws IOException {
        // 单个插件批次可能大于 WSS 预算；按事件边界切成不超过 256 KiB 的多个顺序分片。
        List<byte[]> chunks = new ArrayList<>();
        ByteArrayOutputStream current = new ByteArrayOutputStream(uploadChunkBytes);
        for (JsonNode event : events) {
            byte[] line = (objectMapper.writeValueAsString(event) + "\n").getBytes(StandardCharsets.UTF_8);
            if (line.length > uploadChunkBytes) {
                throw new IllegalArgumentException("single observability event exceeds local WSS chunk budget");
            }
            if (current.size() > 0 && current.size() + line.length > uploadChunkBytes) {
                chunks.add(current.toByteArray());
                current.reset();
            }
            current.write(line);
        }
        if (current.size() > 0) {
            chunks.add(current.toByteArray());
        }
        for (byte[] chunk : chunks) {
            List<JsonNode> chunkEvents = parseLines(chunk);
            long first = chunkEvents.stream().mapToLong(event -> event.path("globalSequence").asLong()).min().orElseThrow();
            long last = chunkEvents.stream().mapToLong(event -> event.path("globalSequence").asLong()).max().orElseThrow();
            String digest = sha256(chunk);
            PendingChunk existing = findExact(traceId, first, last, digest);
            if (existing != null && Files.isRegularFile(dataPath(existing.batchId()))) {
                continue;
            }
            long projected = backlogBytes() + chunk.length;
            if (projected > maxSpoolBytes) {
                degraded.set(true);
                writeDegradedMarker(traceId, "SPOOL_LIMIT_REACHED");
                throw new IllegalStateException("observability spool limit reached");
            }
            String batchId = existing == null
                    ? "obs_" + UUID.randomUUID().toString().replace("-", "")
                    : existing.batchId();
            PendingChunk pending = new PendingChunk(
                    batchId, traceId, runtimeKind, runtimeGeneration, coverageStartAt,
                    first, last, digest, chunk.length, droppedCount, complete, Instant.now());
            // 先落 metadata，再落正文；崩溃后重试可按 metadata 补齐同一分片，绝不自动清理未确认数据。
            atomicWrite(metaPath(batchId), objectMapper.writeValueAsBytes(pending));
            atomicWrite(dataPath(batchId), chunk);
        }
    }

    private PendingChunk findExact(String traceId, long first, long last, String sha256) {
        try (var files = Files.list(spoolDirectory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    // metadata 先于正文原子落盘；若进程恰在两次 move 之间退出，重启后仍复用原 batchId 补正文。
                    .map(this::readMetadata)
                    .filter(java.util.Objects::nonNull)
                    .filter(pending -> pending.traceId().equals(traceId)
                            && pending.firstSequence() == first
                            && pending.lastSequence() == last
                            && pending.sha256().equals(sha256))
                    .findFirst()
                    .orElse(null);
        } catch (IOException exception) {
            return null;
        }
    }

    private PendingChunk readPending(Path path) {
        PendingChunk pending = readMetadata(path);
        return pending != null && Files.isRegularFile(dataPath(pending.batchId())) ? pending : null;
    }

    private PendingChunk readMetadata(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }
        try {
            return objectMapper.readValue(path.toFile(), PendingChunk.class);
        } catch (Exception exception) {
            degraded.set(true);
            return null;
        }
    }

    private List<JsonNode> parseLines(byte[] chunk) {
        try {
            return java.util.Arrays.stream(new String(chunk, StandardCharsets.UTF_8).split("\\n"))
                    .filter(line -> !line.isBlank())
                    .map(line -> {
                        try {
                            return objectMapper.readTree(line);
                        } catch (IOException exception) {
                            throw new IllegalArgumentException("invalid observability event", exception);
                        }
                    })
                    .toList();
        } catch (RuntimeException exception) {
            throw exception;
        }
    }

    private byte[] readBounded(HttpExchange exchange, int maximum) throws IOException {
        try (var input = exchange.getRequestBody(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) continue;
                if (output.size() + read > maximum) {
                    throw new IOException("observability relay request too large");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private void atomicWrite(Path target, byte[] body) throws IOException {
        Path temporary = Files.createTempFile(spoolDirectory, ".spool-", ".tmp");
        try {
            Files.write(temporary, body, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // 目标 macOS/Linux 支持 POSIX；其它平台依赖用户目录 ACL。
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void writeDegradedMarker(String traceId, String reason) {
        try {
            atomicWrite(spoolDirectory.resolve("degraded.json"), objectMapper.writeValueAsBytes(Map.of(
                    "traceId", traceId,
                    "reason", reason,
                    "observedAt", Instant.now().toString())));
        } catch (IOException ignored) {
            // 磁盘完全不可写时只保留进程内 degraded 状态，绝不让异常传播到 OpenCode 对话线程。
        }
    }

    private Path metaPath(String batchId) {
        return checked(batchId, ".json");
    }

    private Path dataPath(String batchId) {
        return checked(batchId, ".ndjson");
    }

    private Path blockedPath(String batchId, String suffix) {
        Path rootPath = checked(batchId, suffix);
        Path path = blockedDirectory.resolve(rootPath.getFileName()).normalize();
        if (!path.startsWith(blockedDirectory)) {
            throw new IllegalArgumentException("invalid observability blocked spool path");
        }
        return path;
    }

    private Path checked(String batchId, String suffix) {
        if (batchId == null || !batchId.matches("obs_[a-f0-9]{32}")) {
            throw new IllegalArgumentException("invalid observability batch id");
        }
        Path path = spoolDirectory.resolve(batchId + suffix).normalize();
        if (!path.startsWith(spoolDirectory)) {
            throw new IllegalArgumentException("invalid observability spool path");
        }
        return path;
    }

    private String required(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            throw new IllegalArgumentException("observability batch missing " + field);
        }
        return value;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static void createPrivateDirectory(Path directory) throws IOException {
        Files.createDirectories(directory);
        try {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        } catch (UnsupportedOperationException ignored) {
            // 其它平台依赖用户目录 ACL。
        }
    }

    private static String token() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return "local_obs_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        writer.shutdown();
        try {
            writer.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        httpExecutor.close();
    }

    record PendingChunk(
            String batchId,
            String traceId,
            String runtimeKind,
            String runtimeGeneration,
            Instant coverageStartAt,
            long firstSequence,
            long lastSequence,
            String sha256,
            long contentLength,
            long droppedCount,
            boolean complete,
            Instant createdAt) {
    }
}
