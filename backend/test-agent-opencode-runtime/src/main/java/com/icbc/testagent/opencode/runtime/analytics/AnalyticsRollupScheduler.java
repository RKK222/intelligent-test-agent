package com.icbc.testagent.opencode.runtime.analytics;

import com.icbc.testagent.observability.TraceIdSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 运营分析定时汇总入口，多实例互斥由数据库锁表保证。
 */
@Component
@ConditionalOnProperty(prefix = "test-agent.analytics.rollup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AnalyticsRollupScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnalyticsRollupScheduler.class);

    private final AnalyticsRollupApplicationService service;

    public AnalyticsRollupScheduler(AnalyticsRollupApplicationService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${test-agent.analytics.rollup.fixed-delay-ms:300000}")
    public void run() {
        String traceId = TraceIdSupport.generate();
        try {
            service.rollupRecent(traceId);
        } catch (RuntimeException exception) {
            LOGGER.warn("Analytics rollup job failed, traceId={}", traceId, exception);
        }
    }
}
