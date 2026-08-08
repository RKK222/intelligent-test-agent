package com.enterprise.testagent.opencode.runtime.internalmodel.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallOutcome;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunId;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskTriggerType;
import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import com.enterprise.testagent.scheduler.ScheduledTaskResult;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 覆盖探活任务元数据与 provider 不可达（httpStatus 为 null）时结果不抛 NPE。 */
class InternalModelProbeTaskHandlerTest {

    private static final String TRACE_ID = "trace_model_probe1234567890";
    private static final Instant FIRE_AT = Instant.parse("2026-08-07T02:00:00Z");

    @Test
    void exposesStableTaskMetadata() {
        InternalModelProbeTaskHandler handler = new InternalModelProbeTaskHandler(
                mock(InternalModelProviderProbeService.class));

        assertThat(handler.taskKey()).isEqualTo(new ScheduledTaskKey("opencode-runtime.internal-model-probe"));
        assertThat(handler.name()).isEqualTo("内部模型供应商探活");
        assertThat(handler.cronExpression()).isEqualTo("0 */5 * * * *");
        assertThat(handler.lockTtl()).isEqualTo(Duration.ofMinutes(4));
    }

    @Test
    void returnsDetailWithNullHttpStatusWithoutThrowing() {
        InternalModelProviderProbeService probeService = mock(InternalModelProviderProbeService.class);
        // 连接失败/供应商不可用时 httpStatus 为 null，之前 Map.of 会抛 NPE。
        InternalModelProviderProbeService.Result result = new InternalModelProviderProbeService.Result()
                .put("enterprise-deepseek", new InternalModelProviderProbeService.ProbeOutcome(
                        "enterprise-deepseek", InternalModelCallOutcome.UPSTREAM_CONNECT_FAILED, null, 42L))
                .put("enterprise-qwen", new InternalModelProviderProbeService.ProbeOutcome(
                        "enterprise-qwen", InternalModelCallOutcome.SUCCESS, 200, 7L));
        when(probeService.probeAll(TRACE_ID)).thenReturn(result);
        InternalModelProbeTaskHandler handler = new InternalModelProbeTaskHandler(probeService);

        ScheduledTaskResult taskResult = handler.run(context());

        Map<String, Object> rawOutcomes = taskResult.result();
        assertThat(rawOutcomes).containsKey("enterprise-deepseek");
        @SuppressWarnings("unchecked")
        Map<String, Object> failed = (Map<String, Object>) rawOutcomes.get("enterprise-deepseek");
        @SuppressWarnings("unchecked")
        Map<String, Object> ok = (Map<String, Object>) rawOutcomes.get("enterprise-qwen");
        assertThat(failed.get("outcome")).isEqualTo("UPSTREAM_CONNECT_FAILED");
        assertThat(failed).containsKey("httpStatus");
        assertThat(failed.get("httpStatus")).isNull();
        assertThat(failed.get("durationMillis")).isEqualTo(42L);
        assertThat(ok.get("httpStatus")).isEqualTo(200);
    }

    private ScheduledTaskContext context() {
        return new ScheduledTaskContext(
                new ScheduledTaskRunId("str_model_probe_run_1234567890"),
                new ScheduledTaskKey("opencode-runtime.internal-model-probe"),
                null,
                ScheduledTaskTriggerType.CRON,
                null,
                FIRE_AT,
                TRACE_ID,
                Map.of());
    }
}
