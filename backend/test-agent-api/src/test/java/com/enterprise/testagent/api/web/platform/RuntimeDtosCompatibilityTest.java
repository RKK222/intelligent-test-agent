package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.run.RunStorageMode;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionMessage;
import com.enterprise.testagent.domain.session.SessionMessageId;
import com.enterprise.testagent.domain.session.SessionMessageRole;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.event.RunEventSsePayload;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimeDtosCompatibilityTest {

    private static final Instant NOW = Instant.parse("2026-07-10T00:00:00Z");
    private static final Instant DETAILS_EXPIRE_AT = Instant.parse("2026-07-11T00:00:00Z");

    @Test
    void legacyRunMappingKeepsNewStorageMetadataNullable() {
        RuntimeDtos.RunResponse response = RuntimeDtos.RunResponse.from(run());

        assertThat(response.storageMode()).isNull();
        assertThat(response.clientRequestId()).isNull();
        assertThat(response.detailsAvailableUntil()).isNull();
    }

    @Test
    void runMappingCanExposeRedisSummaryStorageMetadata() {
        RuntimeDtos.RunResponse response = RuntimeDtos.RunResponse.from(
                run(), RunStorageMode.REDIS_SUMMARY, "req_1234567890abcdef", DETAILS_EXPIRE_AT);

        assertThat(response.storageMode()).isEqualTo("REDIS_SUMMARY");
        assertThat(response.clientRequestId()).isEqualTo("req_1234567890abcdef");
        assertThat(response.detailsAvailableUntil()).isEqualTo(DETAILS_EXPIRE_AT);
    }

    @Test
    void legacySessionMessageMappingKeepsSummaryMetadataNullable() {
        RuntimeDtos.SessionMessageResponse response = RuntimeDtos.SessionMessageResponse.from(message());

        assertThat(response.contentKind()).isNull();
        assertThat(response.summaryStatus()).isNull();
        assertThat(response.summaryVersion()).isNull();
    }

    @Test
    void sessionMessageMappingCanExposeSummaryMetadata() {
        RuntimeDtos.SessionMessageResponse response =
                RuntimeDtos.SessionMessageResponse.from(message(), "SUMMARY", "PARTIAL", 1);

        assertThat(response.contentKind()).isEqualTo("SUMMARY");
        assertThat(response.summaryStatus()).isEqualTo("PARTIAL");
        assertThat(response.summaryVersion()).isEqualTo(1);
    }

    @Test
    void legacyHistoryMappingDefaultsToFullReplayableRepresentation() {
        RuntimeDtos.SessionTreeMessagesResponse response =
                RuntimeDtos.SessionTreeMessagesResponse.from("ses_1234567890abcdef", List.of(snapshotEvent()));

        assertThat(response.historyRepresentation()).isEqualTo("FULL");
        assertThat(response.replayAvailable()).isTrue();
        assertThat(response.detailsAvailableUntil()).isNull();
    }

    @Test
    void historyMappingCanDescribeSummaryFallback() {
        RuntimeDtos.SessionTreeMessagesResponse response = RuntimeDtos.SessionTreeMessagesResponse.from(
                "ses_1234567890abcdef",
                List.of(),
                "SUMMARY",
                false,
                DETAILS_EXPIRE_AT);

        assertThat(response.historyRepresentation()).isEqualTo("SUMMARY");
        assertThat(response.replayAvailable()).isFalse();
        assertThat(response.detailsAvailableUntil()).isEqualTo(DETAILS_EXPIRE_AT);
    }

    @Test
    void runHistoryMappingKeepsLegacyDefaultsAndCanExposeSummaryFallback() {
        RuntimeDtos.RunSessionTreeMessagesResponse legacy =
                RuntimeDtos.RunSessionTreeMessagesResponse.from("run_1234567890abcdef", List.of(snapshotEvent()));
        RuntimeDtos.RunSessionTreeMessagesResponse summary = RuntimeDtos.RunSessionTreeMessagesResponse.from(
                "run_1234567890abcdef",
                List.of(),
                "SUMMARY",
                false,
                DETAILS_EXPIRE_AT);

        assertThat(legacy.historyRepresentation()).isEqualTo("FULL");
        assertThat(legacy.replayAvailable()).isTrue();
        assertThat(legacy.detailsAvailableUntil()).isNull();
        assertThat(summary.historyRepresentation()).isEqualTo("SUMMARY");
        assertThat(summary.replayAvailable()).isFalse();
        assertThat(summary.detailsAvailableUntil()).isEqualTo(DETAILS_EXPIRE_AT);
    }

    private static Run run() {
        return new Run(
                new RunId("run_1234567890abcdef"),
                new SessionId("ses_1234567890abcdef"),
                new WorkspaceId("wrk_1234567890abcdef"),
                RunStatus.SUCCEEDED,
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }

    private static SessionMessage message() {
        return new SessionMessage(
                new SessionMessageId("msg_1234567890abcdef"),
                new SessionId("ses_1234567890abcdef"),
                SessionMessageRole.ASSISTANT,
                "摘要内容",
                NOW,
                "trace_1234567890abcdef");
    }

    private static RunEventSsePayload snapshotEvent() {
        return new RunEventSsePayload(
                "snapshot:run_1234567890abcdef:message.updated",
                "run_1234567890abcdef",
                0L,
                "message.updated",
                "trace_1234567890abcdef",
                NOW,
                Map.of("sessionId", "ses_1234567890abcdef"));
    }
}
