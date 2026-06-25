package com.icbc.testagent.scheduler;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 调度框架基础 Bean 配置，集中提供可替换的系统时钟。
 */
@Configuration
public class SchedulerConfiguration {

    /**
     * 默认使用 UTC 系统时钟；测试可注入固定 Clock。
     */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock schedulerClock() {
        return Clock.systemUTC();
    }
}
