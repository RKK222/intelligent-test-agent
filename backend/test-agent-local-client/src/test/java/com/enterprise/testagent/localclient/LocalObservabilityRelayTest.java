package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalObservabilityRelayTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldPersistUnacknowledgedChunkRecoverPartialWriteAndOnlyDeleteExactAck() throws Exception {
        Path spool = temporaryDirectory.resolve("spool");
        LocalObservabilityRelay.PendingChunk original;
        try (LocalObservabilityRelay relay = new LocalObservabilityRelay(spool, 1024 * 1024)) {
            assertThat(post(relay, "wrong-token").statusCode()).isEqualTo(401);
            assertThat(post(relay, relay.localToken()).statusCode()).isEqualTo(202);

            original = relay.nextPending();
            assertThat(original).isNotNull();
            assertThat(original.contentLength()).isLessThanOrEqualTo(LocalObservabilityRelay.MAX_UPLOAD_CHUNK_BYTES);
            assertThat(new String(relay.readBody(original), StandardCharsets.UTF_8))
                    .contains("test-design")
                    .doesNotContain("Bearer raw-secret");

            // 模拟 metadata 已完成原子落盘、正文尚未 move 时进程退出；重投必须复用同一个 batchId。
            Files.delete(spool.resolve(original.batchId() + ".ndjson"));
            assertThat(post(relay, relay.localToken()).statusCode()).isEqualTo(202);
            LocalObservabilityRelay.PendingChunk recovered = relay.nextPending();
            assertThat(recovered.batchId()).isEqualTo(original.batchId());
            assertThat(relay.readBody(recovered)).isNotEmpty();
        }

        // 重启后仍只读取服务器尚未确认的 spool，不依赖进程内状态。
        try (LocalObservabilityRelay restarted = new LocalObservabilityRelay(spool, 1024 * 1024)) {
            LocalObservabilityRelay.PendingChunk pending = restarted.nextPending();
            assertThat(pending.batchId()).isEqualTo(original.batchId());
            assertThatThrownBy(() -> restarted.acknowledge(
                    pending.batchId(), pending.traceId(), pending.lastSequence(), "0".repeat(64)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(restarted.nextPending()).isNotNull();

            restarted.acknowledge(
                    pending.batchId(), pending.traceId(), pending.lastSequence(), pending.sha256());
            assertThat(restarted.nextPending()).isNull();
            assertThat(restarted.backlogBytes()).isZero();
        }
    }

    @Test
    void shouldStopAcceptingWhenSpoolBudgetIsReachedWithoutDeletingExistingChunk() throws Exception {
        Path spool = temporaryDirectory.resolve("limited-spool");
        try (LocalObservabilityRelay relay = new LocalObservabilityRelay(
                spool, LocalObservabilityRelay.MAX_UPLOAD_CHUNK_BYTES)) {
            assertThat(post(relay, relay.localToken(), "x".repeat(140_000)).statusCode()).isEqualTo(202);
            LocalObservabilityRelay.PendingChunk first = relay.nextPending();

            HttpResponse<String> response = post(relay, relay.localToken(), "y".repeat(140_000));

            assertThat(response.statusCode()).isEqualTo(507);
            assertThat(relay.degraded()).isTrue();
            assertThat(relay.nextPending().batchId()).isEqualTo(first.batchId());
        }
    }

    private HttpResponse<String> post(LocalObservabilityRelay relay, String token) throws Exception {
        return post(relay, token, "已脱敏工具输出");
    }

    private HttpResponse<String> post(LocalObservabilityRelay relay, String token, String result) throws Exception {
        String body = """
                {
                  "schemaVersion":"1.0",
                  "runtime":{"kind":"LOCAL_CLIENT","generation":"generation-1"},
                  "coverageStartAt":"2026-08-22T00:00:00Z",
                  "droppedCount":0,
                  "complete":false,
                  "events":[{
                    "schemaVersion":"1.0",
                    "eventId":"evt_0000000000000000000000000000000000000001",
                    "traceId":"trc_00000000000000000000000000000001",
                    "type":"TOOL_EXECUTE_AFTER",
                    "timestamp":"2026-08-22T00:00:01Z",
                    "globalSequence":1,
                    "sessionSequence":1,
                    "sessionId":"session-1",
                    "callId":"call-1",
                    "payload":{"tool":"skill","skillName":"test-design","result":"%s"}
                  }]
                }
                """.formatted(result.replace("\\", "\\\\").replace("\"", "\\\""));
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(relay.baseUrl() + LocalObservabilityRelay.EVENTS_PATH))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
