package com.icbc.testagent.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.api.web.platform.AgentConfigProgressHub;
import com.icbc.testagent.event.RedisServerBroadcastPublisher;
import com.icbc.testagent.workspace.WorkspaceServerIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class ServerBroadcastContextTest {

    @Test
    void redisServerBroadcastAndAgentConfigProgressHubCanStartInSameContext() {
        new ApplicationContextRunner()
                .withPropertyValues("test-agent.server-broadcast.enabled=true")
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(WorkspaceServerIdentity.class, () -> new WorkspaceServerIdentity("linux-test"))
                .withBean(RedisServerBroadcastPublisher.class)
                .withBean(AgentConfigProgressHub.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(RedisServerBroadcastPublisher.class);
                    assertThat(context).hasSingleBean(AgentConfigProgressHub.class);
                });
    }
}
