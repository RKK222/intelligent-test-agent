package com.icbc.testagent.event;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.event.RunEvent;
import com.icbc.testagent.domain.event.RunEventRepository;
import com.icbc.testagent.domain.run.RunId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * RunEvent 回放服务，集中处理 Last-Event-ID 到 seq 的转换和增量读取。
 */
@Service
public class RunEventReplayService {

    private final RunEventRepository runEventRepository;

    /**
     * 构造事件回放服务；Repository 只通过 domain 端口访问，避免依赖 JDBC 实现。
     */
    public RunEventReplayService(RunEventRepository runEventRepository) {
        this.runEventRepository = Objects.requireNonNull(runEventRepository, "runEventRepository must not be null");
    }

    /**
     * 按 Last-Event-ID 之后的 seq 增量回放事件；limit 由入口层控制，避免一次性读取过多事件。
     */
    public List<RunEvent> replayAfter(RunId runId, String lastEventId, int limit) {
        return runEventRepository.findByRunIdAfter(runId, resolveLastSeq(lastEventId), limit);
    }

    /**
     * 按 root session 回放持久化事件，用于 Session 级历史树补齐 permission/question/todo 等状态。
     */
    public List<RunEvent> replayByRootSessionIdAfter(String rootSessionId, String lastEventId, int limit) {
        if (rootSessionId == null || rootSessionId.isBlank()) {
            return List.of();
        }
        return runEventRepository.findByRootSessionIdAfter(rootSessionId, resolveLastSeq(lastEventId), limit);
    }

    /**
     * 将 SSE Last-Event-ID 转换为 durable seq；空值表示从 0 开始，非法值返回统一参数错误。
     */
    public long resolveLastSeq(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return 0L;
        }
        try {
            long seq = Long.parseLong(lastEventId.trim());
            if (seq < 0) {
                throw invalidLastEventId(lastEventId);
            }
            return seq;
        } catch (NumberFormatException exception) {
            throw invalidLastEventId(lastEventId);
        }
    }

    /**
     * 构造 Last-Event-ID 参数错误，details 中只包含原始游标字符串，不泄露内部查询信息。
     */
    private PlatformException invalidLastEventId(String lastEventId) {
        return new PlatformException(
                ErrorCode.VALIDATION_ERROR,
                "Last-Event-ID 必须是非负数字",
                Map.of("lastEventId", lastEventId));
    }
}
