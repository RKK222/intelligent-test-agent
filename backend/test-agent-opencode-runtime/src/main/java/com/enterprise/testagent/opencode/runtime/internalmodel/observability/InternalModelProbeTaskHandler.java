package com.enterprise.testagent.opencode.runtime.internalmodel.observability;

import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import com.enterprise.testagent.scheduler.ScheduledTaskHandler;
import com.enterprise.testagent.scheduler.ScheduledTaskResult;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 定期对内部模型 provider 探活，主动发现端点不可达，而不是等用户调用失败。 */
@Component
public class InternalModelProbeTaskHandler implements ScheduledTaskHandler {

    static final ScheduledTaskKey TASK_KEY = new ScheduledTaskKey("opencode-runtime.internal-model-probe");
    static final String CRON = "0 */5 * * * *";
    static final Duration LOCK_TTL = Duration.ofMinutes(4);

    private final InternalModelProviderProbeService probeService;

    public InternalModelProbeTaskHandler(InternalModelProviderProbeService probeService) {
        this.probeService = probeService;
    }

    @Override
    public ScheduledTaskKey taskKey() {
        return TASK_KEY;
    }

    @Override
    public String name() {
        return "内部模型供应商探活";
    }

    @Override
    public String cronExpression() {
        return CRON;
    }

    @Override
    public Duration lockTtl() {
        return LOCK_TTL;
    }

    @Override
    public ScheduledTaskResult run(ScheduledTaskContext context) {
        context.throwIfStopRequested();
        InternalModelProviderProbeService.Result result = probeService.probeAll(context.traceId());
        context.throwIfStopRequested();
        Map<String, Object> payload = new LinkedHashMap<>();
        result.outcomes().forEach((providerId, outcome) -> payload.put(
                providerId,
                Map.of(
                        "outcome", outcome.outcome().name(),
                        "httpStatus", outcome.httpStatus(),
                        "durationMillis", outcome.durationMillis())));
        return ScheduledTaskResult.of(payload);
    }
}
