package com.enterprise.testagent.xxljob;

/** 探测配置列表中是否至少有一个 XXL Admin 已完成独立 readiness 检查。 */
@FunctionalInterface
interface XxlJobAdminReadinessProbe {

    boolean isAnyReady(String adminAddresses);
}
