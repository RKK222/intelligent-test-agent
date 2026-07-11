package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.agent.runtime.AgentRuntime;
import com.icbc.testagent.agent.runtime.AgentRuntimeRegistry;
import com.icbc.testagent.agent.runtime.AgentSessionMessage;
import com.icbc.testagent.agent.runtime.AgentSessionMessagesCommand;
import com.icbc.testagent.agent.runtime.AgentSessionMessagesResult;
import com.icbc.testagent.domain.node.ExecutionNode;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 通过 OpenCode session messages 精确查找稳定 dispatchMessageId，分页未穷尽时返回 UNKNOWN。 */
@Component
@Order(0)
public class AgentRuntimeRunDispatchAcceptanceProbe implements RunDispatchAcceptanceProbe {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentRuntimeRunDispatchAcceptanceProbe.class);
    private static final int PAGE_SIZE = 200;
    private static final int MAX_MESSAGES = 2_000;
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final AgentRuntimeRegistry runtimes;
    private final RunRecoveryExecutionNodeResolver nodes;

    AgentRuntimeRunDispatchAcceptanceProbe(
            AgentRuntimeRegistry runtimes,
            RunRecoveryExecutionNodeResolver nodes) {
        this.runtimes = runtimes;
        this.nodes = nodes;
    }

    @Override
    public RunDispatchAcceptance probe(RunDispatchProbeRequest request) {
        Optional<ExecutionNode> node = nodes.resolve(
                request.executionNodeId(), request.executionNodeBaseUrl());
        if (node.isEmpty()) {
            return RunDispatchAcceptance.UNKNOWN;
        }
        try {
            AgentRuntime runtime = runtimes.require(request.agentId());
            Set<String> seenCursors = new HashSet<>();
            String cursor = null;
            int loaded = 0;
            while (loaded < MAX_MESSAGES) {
                AgentSessionMessagesResult result = runtime.sessionMessages(new AgentSessionMessagesCommand(
                                node.orElseThrow(), request.remoteSessionId(), PAGE_SIZE, "desc", cursor, request.traceId()))
                        .block(REQUEST_TIMEOUT);
                if (result == null) {
                    return RunDispatchAcceptance.UNKNOWN;
                }
                for (AgentSessionMessage message : result.messages()) {
                    if (matches(message.message(), request.dispatchMessageId())) {
                        return RunDispatchAcceptance.ACCEPTED;
                    }
                }
                loaded += result.messages().size();
                String next = result.nextCursor();
                if (next == null || result.messages().isEmpty()) {
                    return RunDispatchAcceptance.NOT_ACCEPTED;
                }
                if (!seenCursors.add(next)) {
                    return RunDispatchAcceptance.UNKNOWN;
                }
                cursor = next;
            }
            return RunDispatchAcceptance.UNKNOWN;
        } catch (RuntimeException exception) {
            LOGGER.warn("OpenCode dispatch 接收状态探测失败，runId={}, traceId={}, exceptionType={}",
                    request.runId().value(), request.traceId(), exception.getClass().getSimpleName());
            return RunDispatchAcceptance.UNKNOWN;
        }
    }

    private boolean matches(Map<String, Object> message, String dispatchMessageId) {
        return dispatchMessageId.equals(text(message, "id"))
                || dispatchMessageId.equals(text(message, "messageID"))
                || dispatchMessageId.equals(text(message, "messageId"));
    }

    private String text(Map<String, Object> value, String key) {
        Object candidate = value == null ? null : value.get(key);
        return candidate instanceof String text && !text.isBlank() ? text : null;
    }
}
