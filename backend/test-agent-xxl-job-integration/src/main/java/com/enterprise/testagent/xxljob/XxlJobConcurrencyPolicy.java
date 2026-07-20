package com.enterprise.testagent.xxljob;

/** 平台任务的跨 Java 并发策略；单 Java 串行/丢弃仍由 XXL block strategy 保证。 */
public enum XxlJobConcurrencyPolicy {
    GLOBAL_MUTEX,
    ALLOW_OVERLAP
}
