package com.icbc.testagent.event;

import com.icbc.testagent.domain.event.RunEvent;
import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventRepository;
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

    /**
     * 构造仅持久化的追加服务，适用于不需要实时推送的测试或降级场景。
     */
    public RunEventAppender(RunEventRepository runEventRepository) {
        this(runEventRepository, null);
    }

    /**
     * 构造追加服务；liveBus 可为空，为空时只写入 durable Repository，不做进程内实时广播。
     */
    @Autowired
    public RunEventAppender(RunEventRepository runEventRepository, RunEventLiveBus liveBus) {
        this.runEventRepository = Objects.requireNonNull(runEventRepository, "runEventRepository must not be null");
        this.liveBus = liveBus;
    }

    /**
     * 追加一条 durable RunEvent，持久化层负责分配 eventId/seq，成功后再发布到实时通道。
     */
    public RunEvent append(RunEventDraft draft) {
        RunEvent event = runEventRepository.append(Objects.requireNonNull(draft, "draft must not be null"));
        if (liveBus != null) {
            liveBus.publishDurable(event);
        }
        return event;
    }
}
