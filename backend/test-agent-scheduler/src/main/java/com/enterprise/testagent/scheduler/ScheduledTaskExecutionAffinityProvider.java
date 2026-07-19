package com.enterprise.testagent.scheduler;

/** 提供当前 Java 可认领的 USER_PLAN 执行亲和键。 */
@FunctionalInterface
public interface ScheduledTaskExecutionAffinityProvider {
    String currentAffinity();
}
