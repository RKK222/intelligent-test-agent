package com.enterprise.testagent.xxljob;

/** 探测与当前 Java 同 JVM 的 XXL Admin 是否已完成独立 readiness 检查。 */
@FunctionalInterface
interface XxlJobAdminReadinessProbe {

    boolean isReady(String adminAddress);
}
