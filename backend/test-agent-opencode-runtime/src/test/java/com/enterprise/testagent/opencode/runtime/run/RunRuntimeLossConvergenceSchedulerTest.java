package com.enterprise.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.session.ConversationSourceType;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.test.scheduler.VirtualTimeScheduler;

class RunRuntimeLossConvergenceSchedulerTest {

    @Test
    void schedulesExactlyOnceAndWaitsThirtySeconds() {
        RunRuntimeLossConvergenceService convergence = mock(RunRuntimeLossConvergenceService.class);
        VirtualTimeScheduler time = VirtualTimeScheduler.create();
        RunRuntimeLossConvergenceScheduler scheduler = new RunRuntimeLossConvergenceScheduler(convergence, time);
        RunRuntimeLossRequest request = request();
        AgentRuntime runtime = mock(AgentRuntime.class);
        ExecutionNode node = mock(ExecutionNode.class);

        assertThat(scheduler.schedule(request, runtime, node)).isTrue();
        assertThat(scheduler.schedule(request, runtime, node)).isFalse();
        time.advanceTimeBy(Duration.ofSeconds(29));
        verify(convergence, never()).converge(request, runtime, node);

        time.advanceTimeBy(Duration.ofSeconds(1));
        verify(convergence, timeout(1_000)).converge(request, runtime, node);
    }

    @Test
    void invokesRecoveredActionOnlyWhenRedisReturnsDuringGrace() {
        RunRuntimeLossConvergenceService convergence = mock(RunRuntimeLossConvergenceService.class);
        VirtualTimeScheduler time = VirtualTimeScheduler.create();
        RunRuntimeLossConvergenceScheduler scheduler = new RunRuntimeLossConvergenceScheduler(convergence, time);
        RunRuntimeLossRequest request = request();
        AgentRuntime runtime = mock(AgentRuntime.class);
        ExecutionNode node = mock(ExecutionNode.class);
        Runnable recoveredAction = mock(Runnable.class);
        when(convergence.converge(request, runtime, node)).thenReturn(new RunRuntimeLossConvergenceResult(
                RunRuntimeLossConvergenceResult.Outcome.RUNTIME_RECOVERED, false, false));

        assertThat(scheduler.schedule(request, runtime, node, recoveredAction)).isTrue();
        time.advanceTimeBy(Duration.ofSeconds(30));

        verify(recoveredAction, timeout(1_000)).run();
    }

    @Test
    void doesNotInvokeRecoveredActionAfterSafeTerminalConvergence() {
        RunRuntimeLossConvergenceService convergence = mock(RunRuntimeLossConvergenceService.class);
        VirtualTimeScheduler time = VirtualTimeScheduler.create();
        RunRuntimeLossConvergenceScheduler scheduler = new RunRuntimeLossConvergenceScheduler(convergence, time);
        RunRuntimeLossRequest request = request();
        AgentRuntime runtime = mock(AgentRuntime.class);
        ExecutionNode node = mock(ExecutionNode.class);
        Runnable recoveredAction = mock(Runnable.class);
        when(convergence.converge(request, runtime, node)).thenReturn(new RunRuntimeLossConvergenceResult(
                RunRuntimeLossConvergenceResult.Outcome.TERMINAL_APPLIED, true, false));

        assertThat(scheduler.schedule(request, runtime, node, recoveredAction)).isTrue();
        time.advanceTimeBy(Duration.ofSeconds(30));

        verify(recoveredAction, never()).run();
    }

    @Test
    void rechecksSafeConvergenceWhenRecoveredActionFails() {
        RunRuntimeLossConvergenceService convergence = mock(RunRuntimeLossConvergenceService.class);
        VirtualTimeScheduler time = VirtualTimeScheduler.create();
        RunRuntimeLossConvergenceScheduler scheduler = new RunRuntimeLossConvergenceScheduler(convergence, time);
        RunRuntimeLossRequest request = request();
        AgentRuntime runtime = mock(AgentRuntime.class);
        ExecutionNode node = mock(ExecutionNode.class);
        Runnable recoveredAction = mock(Runnable.class);
        when(convergence.converge(request, runtime, node))
                .thenReturn(new RunRuntimeLossConvergenceResult(
                        RunRuntimeLossConvergenceResult.Outcome.RUNTIME_RECOVERED, false, false))
                .thenReturn(new RunRuntimeLossConvergenceResult(
                        RunRuntimeLossConvergenceResult.Outcome.TERMINAL_APPLIED, true, false));
        org.mockito.Mockito.doThrow(new IllegalStateException("redis lost again"))
                .when(recoveredAction).run();

        assertThat(scheduler.schedule(request, runtime, node, recoveredAction)).isTrue();
        time.advanceTimeBy(Duration.ofSeconds(30));

        verify(convergence, timeout(1_000).times(2)).converge(request, runtime, node);
    }

    @Test
    void keepsInMemoryRetryWhenNeitherDatabaseNorRedisAcceptedTerminal() {
        RunRuntimeLossConvergenceService convergence = mock(RunRuntimeLossConvergenceService.class);
        VirtualTimeScheduler time = VirtualTimeScheduler.create();
        RunRuntimeLossConvergenceScheduler scheduler = new RunRuntimeLossConvergenceScheduler(convergence, time);
        RunRuntimeLossRequest request = request();
        AgentRuntime runtime = mock(AgentRuntime.class);
        ExecutionNode node = mock(ExecutionNode.class);
        when(convergence.converge(request, runtime, node))
                .thenReturn(new RunRuntimeLossConvergenceResult(
                        RunRuntimeLossConvergenceResult.Outcome.TERMINAL_PERSISTENCE_FAILED,
                        true,
                        false))
                .thenReturn(new RunRuntimeLossConvergenceResult(
                        RunRuntimeLossConvergenceResult.Outcome.TERMINAL_APPLIED,
                        true,
                        false));

        assertThat(scheduler.schedule(request, runtime, node)).isTrue();
        time.advanceTimeBy(Duration.ofSeconds(30));
        verify(convergence, timeout(1_000)).converge(request, runtime, node);
        assertThat(scheduler.schedule(request, runtime, node)).isFalse();

        time.advanceTimeBy(Duration.ofSeconds(5));
        verify(convergence, timeout(1_000).times(2)).converge(request, runtime, node);
    }

    private RunRuntimeLossRequest request() {
        return new RunRuntimeLossRequest(
                new RunId("run_0123456789abcdef0123456789abcdef"),
                new SessionId("ses_runtime_loss_scheduler"),
                new UserId("usr_runtime_loss_scheduler"),
                "opencode",
                "msg_runtime_loss_scheduler",
                "remote-runtime-loss-scheduler",
                "/srv/workspaces/runtime-loss",
                ConversationSourceType.MANUAL,
                null,
                "trace_runtime_loss_scheduler");
    }
}
