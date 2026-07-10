package com.icbc.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.domain.agent.AgentSessionBinding;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.node.ExecutionNodeStatus;
import com.icbc.testagent.domain.run.ConversationRunContext;
import com.icbc.testagent.domain.run.RunStorageMode;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionStatus;
import com.icbc.testagent.domain.session.ConversationSourceType;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.WorkspaceStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RunStorageModeSelectorTest {

    private static final Instant NOW = Instant.parse("2026-07-10T08:00:00Z");
    private static final UserId USER_ID = new UserId("usr_summary_rollout");

    @Test
    void selectsRedisSummaryOnlyForEligibleContextBackedClientInStableBucket() {
        ConversationContextProperties properties = new ConversationContextProperties();
        properties.setEnabled(true);
        properties.setRolloutPercentage(100);
        RunStorageModeSelector selector = new RunStorageModeSelector(properties);
        StartRunInput eligible = input("ctx_token", "req_123");

        assertThat(selector.select(USER_ID, eligible, context())).isEqualTo(RunStorageMode.REDIS_SUMMARY);
        assertThat(selector.select(USER_ID, input(null, "req_123"), context())).isEqualTo(RunStorageMode.LEGACY_FULL);
        assertThat(selector.select(USER_ID, input("ctx_token", null), context())).isEqualTo(RunStorageMode.LEGACY_FULL);
        assertThat(selector.select(USER_ID, eligible, null)).isEqualTo(RunStorageMode.LEGACY_FULL);
        assertThat(selector.select(null, eligible, context())).isEqualTo(RunStorageMode.LEGACY_FULL);
    }

    @Test
    void rolloutUsesStableUserHashAndHonorsDisabledAndZeroBoundaries() {
        ConversationContextProperties properties = new ConversationContextProperties();
        properties.setEnabled(true);
        RunStorageModeSelector selector = new RunStorageModeSelector(properties);
        int bucket = selector.bucket(USER_ID);

        properties.setRolloutPercentage(bucket);
        assertThat(selector.select(USER_ID, input("ctx", "req"), context()))
                .isEqualTo(RunStorageMode.LEGACY_FULL);
        properties.setRolloutPercentage(bucket + 1);
        assertThat(selector.select(USER_ID, input("ctx", "req"), context()))
                .isEqualTo(RunStorageMode.REDIS_SUMMARY);
        properties.setEnabled(false);
        assertThat(selector.select(USER_ID, input("ctx", "req"), context()))
                .isEqualTo(RunStorageMode.LEGACY_FULL);
    }

    private StartRunInput input(String contextToken, String clientRequestId) {
        return new StartRunInput(
                new SessionId("ses_summary_rollout"),
                "run tests",
                java.util.List.of(),
                "msg_input_summary",
                "build",
                null,
                null,
                null,
                null,
                null,
                contextToken,
                clientRequestId);
    }

    private ConversationRunContext context() {
        WorkspaceId workspaceId = new WorkspaceId("wrk_summary_rollout");
        Session session = new Session(
                new SessionId("ses_summary_rollout"), workspaceId, "summary", SessionStatus.ACTIVE,
                NOW, NOW, "trace_summary", null, null, false, ConversationSourceType.MANUAL, null, USER_ID);
        Workspace workspace = new Workspace(
                workspaceId, "summary", "/tmp/summary", WorkspaceStatus.ACTIVE,
                NOW, NOW, "server-summary", "trace_summary");
        ExecutionNode node = new ExecutionNode(
                new ExecutionNodeId("node_ocp_summary"), "http://127.0.0.1:4096",
                ExecutionNodeStatus.READY, 0, 1, NOW);
        AgentSessionBinding binding = new AgentSessionBinding(
                session.sessionId(), "opencode", "remote-summary", node.executionNodeId(),
                NOW, NOW, "trace_summary");
        return new ConversationRunContext(
                USER_ID, "opencode", "ocp_summary", "server-summary",
                session, workspace, node, binding, 1, NOW.plusSeconds(600));
    }
}
