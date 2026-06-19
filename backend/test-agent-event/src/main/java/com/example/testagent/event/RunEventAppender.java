package com.example.testagent.event;

import com.example.testagent.domain.event.RunEvent;
import com.example.testagent.domain.event.RunEventDraft;
import com.example.testagent.domain.event.RunEventRepository;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * RunEvent 追加服务，负责把待追加事件交给持久化端口分配 eventId 和 seq。
 */
@Service
public class RunEventAppender {

    private final RunEventRepository runEventRepository;
    private final RunEventLiveBus liveBus;

    public RunEventAppender(RunEventRepository runEventRepository) {
        this(runEventRepository, null);
    }

    @Autowired
    public RunEventAppender(RunEventRepository runEventRepository, RunEventLiveBus liveBus) {
        this.runEventRepository = Objects.requireNonNull(runEventRepository, "runEventRepository must not be null");
        this.liveBus = liveBus;
    }

    public RunEvent append(RunEventDraft draft) {
        RunEvent event = runEventRepository.append(Objects.requireNonNull(draft, "draft must not be null"));
        if (liveBus != null) {
            liveBus.publishDurable(event);
        }
        return event;
    }
}
