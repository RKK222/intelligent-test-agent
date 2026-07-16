package com.enterprise.testagent.opencode.runtime.run;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 旧客户端缺少 contextToken 时的兼容计数；Prometheus 会把 Counter 导出为
 * {@code legacy_run_without_context_total}，用于判断何时可安全关闭兼容开关。
 */
@Component
public class LegacyRunCompatibilityMetrics {

    static final String METER_NAME = "legacy_run_without_context";

    private final Counter counter;

    @Autowired
    public LegacyRunCompatibilityMetrics(Optional<MeterRegistry> meterRegistry) {
        this.counter = meterRegistry
                .map(registry -> Counter.builder(METER_NAME)
                        .description("缺少会话上下文 token 但被兼容开关放行的 Run 数量")
                        .register(registry))
                .orElse(null);
    }

    /** 测试和兼容构造路径没有 metrics registry 时安全 no-op。 */
    public void recordAcceptedLegacyRun() {
        if (counter != null) {
            counter.increment();
        }
    }
}
