package com.enterprise.testagent.persistence;

import com.enterprise.testagent.domain.workflowcapability.WorkflowCapabilityStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 装配工作流共享能力的独立Redis key空间。 */
@Configuration
public class WorkflowCapabilityStoreConfig {

    @Bean
    public WorkflowCapabilityStore workflowCapabilityStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        return new RedisWorkflowCapabilityStore(redisTemplate, objectMapper);
    }
}
