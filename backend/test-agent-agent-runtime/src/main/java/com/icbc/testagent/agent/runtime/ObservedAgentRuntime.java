package com.icbc.testagent.agent.runtime;

import com.icbc.testagent.domain.event.RunEventDraft;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/**
 * AgentRuntime 观测包装器，统一记录低基数日志和调用指标。
 */
final class ObservedAgentRuntime implements AgentRuntime {

    private static final Logger LOGGER = LoggerFactory.getLogger(ObservedAgentRuntime.class);

    private final AgentRuntime delegate;
    private final MeterRegistry meterRegistry;

    ObservedAgentRuntime(AgentRuntime delegate, MeterRegistry meterRegistry) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.meterRegistry = meterRegistry;
    }

    @Override
    public String agentId() {
        return delegate.agentId();
    }

    @Override
    public Mono<AgentCreateSessionResult> createSession(AgentCreateSessionCommand command) {
        return observe("createSession", command.traceId(), delegate.createSession(command));
    }

    @Override
    public Mono<Boolean> sessionExists(AgentSessionExistsCommand command) {
        return observe("sessionExists", command.traceId(), delegate.sessionExists(command));
    }

    @Override
    public Mono<AgentStartRunResult> startRun(AgentStartRunCommand command) {
        return observe("startRun", command.traceId(), delegate.startRun(command));
    }

    @Override
    public Mono<AgentCancelResult> cancelSession(AgentCancelCommand command) {
        return observe("cancelSession", command.traceId(), delegate.cancelSession(command));
    }

    @Override
    public Flux<RunEventDraft> streamRunEvents(AgentStreamEventsCommand command) {
        return observe("streamRunEvents", command.traceId(), delegate.streamRunEvents(command));
    }

    @Override
    public Mono<AgentDiffResult> getDiff(AgentDiffCommand command) {
        return observe("getDiff", command.traceId(), delegate.getDiff(command));
    }

    @Override
    public Mono<AgentRejectDiffResult> rejectDiff(AgentRejectDiffCommand command) {
        return observe("rejectDiff", command.traceId(), delegate.rejectDiff(command));
    }

    @Override
    public Mono<AgentRuntimeResult> runtime(AgentRuntimeCommand command) {
        return observe("runtime", command.traceId(), delegate.runtime(command));
    }

    @Override
    public Mono<AgentSessionMessagesResult> sessionMessages(AgentSessionMessagesCommand command) {
        return observe("sessionMessages", command.traceId(), delegate.sessionMessages(command));
    }

    private <T> Mono<T> observe(String operation, String traceId, Mono<T> source) {
        LOGGER.debug("Agent runtime call started, agentId={}, operation={}, traceId={}", agentId(), operation, traceId);
        return source.doFinally(signalType -> record(operation, result(signalType)));
    }

    private <T> Flux<T> observe(String operation, String traceId, Flux<T> source) {
        LOGGER.debug("Agent runtime stream started, agentId={}, operation={}, traceId={}", agentId(), operation, traceId);
        return source.doFinally(signalType -> record(operation, result(signalType)));
    }

    private String result(SignalType signalType) {
        if (signalType == SignalType.ON_ERROR) {
            return "failure";
        }
        if (signalType == SignalType.CANCEL) {
            return "cancelled";
        }
        return "success";
    }

    private void record(String operation, String result) {
        if (meterRegistry != null) {
            meterRegistry.counter(
                    "test_agent.agent_runtime.calls",
                    "agent",
                    agentId(),
                    "operation",
                    operation,
                    "result",
                    result).increment();
        }
    }
}
