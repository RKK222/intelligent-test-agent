package com.enterprise.testagent.domain.configuration;

/** 内部模型目录变更后的进程内事件。 */
public record InternalModelCatalogUpdatedEvent(String providerId, String traceId) {
}
