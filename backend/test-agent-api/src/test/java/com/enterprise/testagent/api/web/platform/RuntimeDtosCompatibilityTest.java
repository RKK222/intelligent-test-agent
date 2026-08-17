package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunResend;
import com.enterprise.testagent.domain.run.RunResendId;
import com.enterprise.testagent.domain.run.RunResendPolicy;
import com.enterprise.testagent.domain.run.RunResendStatus;
import com.enterprise.testagent.domain.run.RunResendTrigger;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.run.RunStorageMode;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionMessage;
import com.enterprise.testagent.domain.session.SessionMessageId;
import com.enterprise.testagent.domain.session.SessionMessageRole;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.event.RunEventSsePayload;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    void runAndMessageMappingsExposeOptionalActualSenderUsername() {
        UserId actor = new UserId("usr_shared_dto_actor");
        RuntimeDtos.RunResponse runResponse = RuntimeDtos.RunResponse.from(
                run().withMessageSender(actor, "ucid-shared-dto", true), ignored -> "协作者");
        RuntimeDtos.SessionMessageResponse messageResponse = RuntimeDtos.SessionMessageResponse.from(
                message().withSender(actor, "ucid-shared-dto", true),
                null, null, null, null, ignored -> "协作者");

        assertThat(runResponse.messageSenderUsername()).isEqualTo("协作者");
        assertThat(messageResponse.senderUsername()).isEqualTo("协作者");
    }

    @Test
    void sharedResendAuditRepairsHistoricalOwnerAttributionInRunAndMessageResponses() {
        UserId owner = new UserId("usr_resend_dto_owner");
        UserId requester = new UserId("usr_resend_dto_requester");
        Run historicalRun = run().withMessageSender(owner, "ucid-owner", false);
        SessionMessage historicalMessage = message().withSender(owner, "ucid-owner", false);
        RunResend resend = new RunResend(
                new RunResendId("rsd_resend_dto"), historicalRun.sessionId(), owner,
                new RunId("run_resend_dto_source"), historicalRun.runId(),
                "msg_resend_dto_source", "msg_resend_dto_replacement",
                RunResendTrigger.MANUAL, 1, 0, RunResendPolicy.MAX_AUTOMATIC_ATTEMPTS,
                RunResendStatus.DISPATCHED, NOW, "linux-resend-dto",
                null, null, "request_resend_dto", "trace_resend_dto", null, NOW, NOW)
                .withRequester(requester, "ucid-requester", true);

        RuntimeDtos.RunResponse runResponse = RuntimeDtos.RunResponse.from(
                historicalRun, null, null, null, resend,
                userId -> userId.equals(requester) ? "wr" : "888888888");
        RuntimeDtos.SessionMessageResponse messageResponse = RuntimeDtos.SessionMessageResponse.from(
                historicalMessage, null, null, null, resend,
                userId -> userId.equals(requester) ? "wr" : "888888888");

        assertThat(runResponse.messageSenderUserId()).isEqualTo(requester.value());
        assertThat(runResponse.messageSenderUsername()).isEqualTo("wr");
        assertThat(runResponse.messageSenderUnifiedAuthId()).isEqualTo("ucid-requester");
        assertThat(runResponse.messageSentBySharedUser()).isTrue();
        assertThat(messageResponse.senderUserId()).isEqualTo(requester.value());
        assertThat(messageResponse.senderUsername()).isEqualTo("wr");
        assertThat(messageResponse.senderUnifiedAuthId()).isEqualTo("ucid-requester");
        assertThat(messageResponse.sentBySharedUser()).isTrue();
    }

    @Test
    void workspaceMappingExposesOnlyOpaqueWorkspacePath() {
        CommonParameterValues parameters = mock(CommonParameterValues.class);
        when(parameters.resolvedValue(ManagedWorkspacePathResolver.PARAM_OPENCODE_PERSONAL_WORKTREE_ROOT))
                .thenReturn(Optional.of("/data/.testagent/agent-opencode/workspace/personalworktree"));
        Workspace workspace = new Workspace(
                new WorkspaceId("wrk_physical_contract"),
                "personal",
                "personalworktree:20260806/usr_1/demo/feature_usr_1_default/workspace",
                NOW);

        RuntimeDtos.WorkspaceResponse response = RuntimeDtos.WorkspaceResponse.from(
                workspace,
                new ManagedWorkspacePathResolver(parameters));

        assertThat(response.rootPath()).isEqualTo("workspace:wrk_physical_contract");
        assertThat(response.physicalRootPath()).isNull();
        assertThat(response.toString()).doesNotContain("/data/.testagent");
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
        assertThat(response.events()).singleElement()
                .extracting(RuntimeDtos.RunSessionTreeEventResponse::traceId)
                .isEqualTo("trace_1234567890abcdef");
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
