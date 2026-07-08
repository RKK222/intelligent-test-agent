package com.icbc.testagent.domain.configuration;

/**
 * 内部模型供应商配置被管理端修改事件，广播层负责通知所有 Java 进程重载。
 */
public record InternalModelProvidersUpdatedEvent(String traceId) {
}
