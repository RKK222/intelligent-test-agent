package com.example.testagent.event;

import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
import com.example.testagent.domain.event.RunEvent;
import com.example.testagent.domain.event.RunEventRepository;
import com.example.testagent.domain.run.RunId;
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

    public RunEventReplayService(RunEventRepository runEventRepository) {
        this.runEventRepository = Objects.requireNonNull(runEventRepository, "runEventRepository must not be null");
    }

    public List<RunEvent> replayAfter(RunId runId, String lastEventId, int limit) {
        return runEventRepository.findByRunIdAfter(runId, resolveLastSeq(lastEventId), limit);
    }

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

    private PlatformException invalidLastEventId(String lastEventId) {
        return new PlatformException(
                ErrorCode.VALIDATION_ERROR,
                "Last-Event-ID 必须是非负数字",
                Map.of("lastEventId", lastEventId));
    }
}
