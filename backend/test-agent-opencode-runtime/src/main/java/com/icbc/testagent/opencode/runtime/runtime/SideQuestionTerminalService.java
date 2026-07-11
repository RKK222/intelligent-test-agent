package com.icbc.testagent.opencode.runtime.runtime;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunRepository;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.event.RunEventAppender;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 旁路问答唯一终态写入服务，以数据库 CAS 决定胜者，并在同一事务内追加唯一终态事件。
 */
@Service
public class SideQuestionTerminalService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SideQuestionTerminalService.class);

    private final RunRepository runRepository;
    private final RunEventAppender runEventAppender;

    /** 注入 Run 持久化端口与既有 RunEvent 追加器。 */
    public SideQuestionTerminalService(RunRepository runRepository, RunEventAppender runEventAppender) {
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.runEventAppender = Objects.requireNonNull(runEventAppender, "runEventAppender must not be null");
    }

    /**
     * 尝试写入成功终态；只有持久化 CAS 胜者才追加 {@code run.succeeded}。
     */
    @Transactional
    public boolean succeed(RunId runId, Map<String, Object> payload, String traceId) {
        LinkedHashMap<String, Object> safePayload = new LinkedHashMap<>();
        if (payload != null) {
            safePayload.putAll(payload);
        }
        safePayload.put("sideQuestion", true);
        return complete(runId, RunStatus.SUCCEEDED, RunEventType.RUN_SUCCEEDED, Map.copyOf(safePayload), traceId);
    }

    /**
     * 尝试写入失败终态；调用方只能传统一安全文案，异常详情和远端正文不会进入事件。
     */
    @Transactional
    public boolean fail(RunId runId, String safeMessage, String traceId) {
        String message = safeMessage == null || safeMessage.isBlank() ? "旁路问答暂时失败" : safeMessage.trim();
        return complete(
                runId,
                RunStatus.FAILED,
                RunEventType.RUN_FAILED,
                Map.of("sideQuestion", true, "message", message),
                traceId);
    }

    private boolean complete(
            RunId runId,
            RunStatus terminalStatus,
            RunEventType eventType,
            Map<String, Object> payload,
            String traceId) {
        Objects.requireNonNull(runId, "runId must not be null");
        Run current = runRepository.findById(runId)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.NOT_FOUND,
                        "Run 不存在",
                        Map.of("runId", runId.value())));
        if (current.status().isTerminal()) {
            return false;
        }

        Instant now = Instant.now();
        Run candidate = current.applyTerminalFact(terminalStatus, now);
        Run saved = runRepository.saveIfStatus(candidate, current.status());
        // MyBatis 端口在 CAS 成功时原样返回 candidate；失败时返回数据库中的最新快照。
        if (saved != candidate) {
            LOGGER.info(
                    "event=side_question_terminal_cas_lost runId={} expectedStatus={} actualStatus={} traceId={}",
                    runId.value(),
                    current.status().name(),
                    saved.status().name(),
                    traceId);
            return false;
        }
        runEventAppender.append(new RunEventDraft(runId, eventType, traceId, now, payload));
        return true;
    }
}
