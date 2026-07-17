package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.domain.event.RunEventDraft;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 为终态事件补齐 Lua outbox 所需的低敏控制字段，不改变事件类型、时间、trace 或 scope。 */
final class RunTerminalProjectionOutboxPayload {

    private RunTerminalProjectionOutboxPayload() {
    }

    /** 返回携带终态来源、原因、安全错误说明和远端停止确认的事件副本。 */
    static RunEventDraft enrich(
            RunEventDraft draft,
            String terminalSource,
            String terminalReasonCode,
            String safeErrorMessage,
            boolean remoteStopConfirmed) {
        Objects.requireNonNull(draft, "draft must not be null");
        return new RunEventDraft(
                draft.runId(),
                draft.type(),
                draft.traceId(),
                draft.occurredAt(),
                payload(
                        draft.payload(),
                        terminalSource,
                        terminalReasonCode,
                        safeErrorMessage,
                        remoteStopConfirmed),
                draft.scopeContext());
    }

    /** 合并既有 payload；可空错误说明不写入，避免把 null 交给不可变 Map。 */
    static Map<String, Object> payload(
            Map<String, Object> base,
            String terminalSource,
            String terminalReasonCode,
            String safeErrorMessage,
            boolean remoteStopConfirmed) {
        if (terminalSource == null || terminalSource.isBlank()) {
            throw new IllegalArgumentException("terminalSource must not be blank");
        }
        if (terminalReasonCode == null || terminalReasonCode.isBlank()) {
            throw new IllegalArgumentException("terminalReasonCode must not be blank");
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        if (base != null) {
            result.putAll(base);
        }
        result.put("terminalSource", terminalSource);
        result.put("terminalReasonCode", terminalReasonCode);
        if (safeErrorMessage != null && !safeErrorMessage.isBlank()) {
            result.put("safeErrorMessage", safeErrorMessage);
        }
        result.put("remoteStopConfirmed", remoteStopConfirmed);
        return Map.copyOf(result);
    }
}
