package com.enterprise.testagent.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 根据统一开关装配真实 memory-service 适配器或安全降级实现。 */
@Configuration
public class MemoryDocumentStoreConfiguration {
    @Bean
    public MemoryDocumentStore memoryDocumentStore(QaMemoryProperties properties, ObjectMapper objectMapper) {
        if (!properties.isEnabled()) {
            return new UnavailableMemoryDocumentStore();
        }
        return new HttpMemoryDocumentStore(properties, objectMapper);
    }
}
