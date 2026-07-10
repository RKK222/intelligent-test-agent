package com.icbc.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.agent.AgentSessionBinding;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.node.ExecutionNodeStatus;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.icbc.testagent.domain.run.ConversationRunContext;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionStatus;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.WorkspaceStatus;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessProbeStatus;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessStatusProbe;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessStatusQueryService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConversationRunContextResolverTest {

    private static final Instant NOW = Instant.parse("2026-07-10T00:00:00Z");
    private static final UserId USER_ID = new UserId("usr_1234567890abcdef");
    private static final SessionId SESSION_ID = new SessionId("ses_1234567890abcdef");

    @Test
    void tokenIsRequiredAndResolvedByRuntimeOwnedPolicy() {
        ConversationContextApplicationService contextService = mock(ConversationContextApplicationService.class);
        OpencodeProcessStatusQueryService statusQueryService = mock(OpencodeProcessStatusQueryService.class);
        ConversationContextProperties properties = new ConversationContextProperties();
        properties.setLegacyRunWithoutContextEnabled(false);
        ConversationRunContextResolver resolver = new ConversationRunContextResolver(
                contextService,
                properties,
                statusQueryService);
        StartRunInput missing = new StartRunInput(
                SESSION_ID,
                "run tests",
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "request-1");

        assertThatThrownBy(() -> resolver.resolve(USER_ID, "opencode", missing))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONVERSATION_CONTEXT_REQUIRED));
        verifyNoInteractions(contextService);

        ConversationRunContext context = context();
        StartRunInput withToken = new StartRunInput(
                SESSION_ID,
                "run tests",
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "ctx_secret",
                "request-1");
        when(contextService.require("ctx_secret", USER_ID, "opencode", SESSION_ID)).thenReturn(context);
        when(statusQueryService.querySnapshot(context.processSnapshot(), "trace_test"))
                .thenReturn(runningProbe(context.processSnapshot()));

        assertThat(resolver.resolve(USER_ID, "opencode", withToken, "trace_test")).containsSame(context);
        verify(contextService).require("ctx_secret", USER_ID, "opencode", SESSION_ID);
        verify(statusQueryService).querySnapshot(context.processSnapshot(), "trace_test");
    }

    @Test
    void unavailableCachedProcessInvalidatesTokenAndRejectsRun() {
        ConversationContextApplicationService contextService = mock(ConversationContextApplicationService.class);
        OpencodeProcessStatusQueryService statusQueryService = mock(OpencodeProcessStatusQueryService.class);
        ConversationContextProperties properties = new ConversationContextProperties();
        ConversationRunContextResolver resolver = new ConversationRunContextResolver(
                contextService,
                properties,
                statusQueryService);
        ConversationRunContext context = context();
        StartRunInput input = new StartRunInput(
                SESSION_ID,
                "run tests",
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "ctx_secret",
                "request-1");
        when(contextService.require("ctx_secret", USER_ID, "opencode", SESSION_ID)).thenReturn(context);
        when(statusQueryService.querySnapshot(context.processSnapshot(), "trace_test"))
                .thenReturn(new OpencodeProcessStatusProbe(
                        OpencodeProcessProbeStatus.NOT_STARTED,
                        Optional.of(context.processSnapshot()),
                        "NOT_RUNNING",
                        "NOT_RUNNING",
                        "process is not running",
                        NOW,
                        true,
                        ErrorCode.OPENCODE_UNAVAILABLE));

        assertThatThrownBy(() -> resolver.resolve(USER_ID, "opencode", input, "trace_test"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE));

        verify(contextService).invalidateProcess(context.processId());
    }

    @Test
    void transientStaleProbeRejectsOnlyCurrentRunWithoutInvalidatingContext() {
        ConversationContextApplicationService contextService = mock(ConversationContextApplicationService.class);
        OpencodeProcessStatusQueryService statusQueryService = mock(OpencodeProcessStatusQueryService.class);
        ConversationContextProperties properties = new ConversationContextProperties();
        ConversationRunContextResolver resolver = new ConversationRunContextResolver(
                contextService,
                properties,
                statusQueryService);
        ConversationRunContext context = context();
        StartRunInput input = new StartRunInput(
                SESSION_ID,
                "run tests",
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "ctx_secret",
                "request-1");
        when(contextService.require("ctx_secret", USER_ID, "opencode", SESSION_ID)).thenReturn(context);
        when(statusQueryService.querySnapshot(context.processSnapshot(), "trace_test"))
                .thenReturn(new OpencodeProcessStatusProbe(
                        OpencodeProcessProbeStatus.STALE,
                        Optional.of(context.processSnapshot()),
                        "STALE",
                        "STALE",
                        "manager command timeout",
                        NOW,
                        false,
                        ErrorCode.OPENCODE_TIMEOUT));

        assertThatThrownBy(() -> resolver.resolve(USER_ID, "opencode", input, "trace_test"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE));

        verify(contextService, never()).invalidateProcess(context.processId());
    }

    @Test
    void legacyCompatibilityAllowsMissingToken() {
        ConversationContextApplicationService contextService = mock(ConversationContextApplicationService.class);
        ConversationContextProperties properties = new ConversationContextProperties();
        properties.setLegacyRunWithoutContextEnabled(true);
        ConversationRunContextResolver resolver = new ConversationRunContextResolver(contextService, properties);

        Optional<ConversationRunContext> resolved = resolver.resolve(
                USER_ID,
                "opencode",
                StartRunInput.ofPrompt(SESSION_ID, "run tests"));

        assertThat(resolved).isEmpty();
        verifyNoInteractions(contextService);
    }

    private static ConversationRunContext context() {
        Workspace workspace = new Workspace(
                new WorkspaceId("wrk_1234567890abcdef"),
                "demo",
                "/srv/workspaces/demo",
                WorkspaceStatus.ACTIVE,
                NOW,
                NOW,
                "server-a",
                "trace_test");
        Session session = new Session(
                SESSION_ID,
                workspace.workspaceId(),
                "session",
                SessionStatus.ACTIVE,
                NOW,
                NOW,
                "trace_test",
                "remote-session-1",
                new ExecutionNodeId("node_ocp_1234567890abcdef"));
        ExecutionNode node = new ExecutionNode(
                new ExecutionNodeId("node_ocp_1234567890abcdef"),
                "http://10.0.0.1:4096",
                ExecutionNodeStatus.READY,
                0,
                1,
                100,
                NOW,
                Set.of("opencode"),
                NOW,
                NOW,
                "trace_test");
        AgentSessionBinding binding = new AgentSessionBinding(
                SESSION_ID,
                "opencode",
                "remote-session-1",
                node.executionNodeId(),
                NOW,
                NOW,
                "trace_test");
        return new ConversationRunContext(
                USER_ID,
                "opencode",
                "ocp_1234567890abcdef",
                "server-a",
                process(),
                session,
                workspace,
                node,
                binding,
                1,
                NOW.plusSeconds(3600));
    }

    private static OpencodeServerProcess process() {
        return new OpencodeServerProcess(
                new OpencodeProcessId("ocp_1234567890abcdef"),
                USER_ID,
                new LinuxServerId("server-a"),
                new OpencodeContainerId("ctr_1234567890abcdef"),
                4096,
                12345L,
                "http://10.0.0.1:4096",
                OpencodeServerProcessStatus.RUNNING,
                "/data/opencode/session/4096",
                "/data/opencode/.config/opencode",
                NOW.minusSeconds(60),
                NOW,
                "ok",
                NOW.minusSeconds(60),
                NOW,
                "trace_test");
    }

    private static OpencodeProcessStatusProbe runningProbe(OpencodeServerProcess process) {
        return new OpencodeProcessStatusProbe(
                OpencodeProcessProbeStatus.RUNNING,
                Optional.of(process),
                "RUNNING",
                "HEALTHY",
                "ok",
                NOW,
                false,
                null);
    }
}
