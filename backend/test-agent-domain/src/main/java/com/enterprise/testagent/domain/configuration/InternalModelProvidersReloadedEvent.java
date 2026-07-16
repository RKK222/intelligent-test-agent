package com.enterprise.testagent.domain.configuration;

/**
 * 内部模型供应商配置已刷新事件，运行态监听后从数据库重载到 JVM 内存。
 */
public record InternalModelProvidersReloadedEvent(String traceId, String reason) {
}
