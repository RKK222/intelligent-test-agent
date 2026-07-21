package com.enterprise.testagent.xxljob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.enterprise.testagent.domain.auth.TokenSessionMarkerStore;
import com.enterprise.testagent.scheduler.ScheduledTaskLock;
import com.enterprise.testagent.scheduler.ScheduledTaskRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 验证存在包级测试构造器时，Spring 仍明确选择公开生产构造器。 */
class XxlJobSpringComponentWiringTest {

    @Test
    void wiresDeferredExecutorWithoutStartingItBeforeAdminReadiness() {
        new ApplicationContextRunner()
                .withPropertyValues("test-agent.xxl-job.enabled=true")
                .withUserConfiguration(XxlJobExecutorConfiguration.class)
                .withBean(XxlJobProperties.class, XxlJobSpringComponentWiringTest::enabledUnreachableProperties)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(XxlJobSpringExecutor.class);
                    assertThat(context).hasSingleBean(XxlJobExecutorLifecycle.class);
                    DeferredXxlJobSpringExecutor executor = context.getBean(DeferredXxlJobSpringExecutor.class);
                    assertThat(executor.isStarted()).isFalse();
                    assertThat(executor.getExecutorRegistryThreadHelper()).isNull();
                });
    }

    @Test
    void wiresRedisSsoTicketServiceThroughItsProductionConstructor() {
        new ApplicationContextRunner()
                .withUserConfiguration(RedisTicketServiceConfiguration.class)
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(XxlJobProperties.class, XxlJobProperties::new)
                .withBean(TokenSessionMarkerStore.class, () -> mock(TokenSessionMarkerStore.class))
                .withBean(Clock.class, Clock::systemUTC)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RedisXxlJobSsoTicketService.class);
                });
    }

    @Test
    void wiresScheduledTaskAdapterThroughItsProductionConstructor() {
        new ApplicationContextRunner()
                .withUserConfiguration(ScheduledTaskAdapterConfiguration.class)
                .withBean(ScheduledTaskRegistry.class, () -> mock(ScheduledTaskRegistry.class))
                .withBean(ScheduledTaskLock.class, () -> mock(ScheduledTaskLock.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(Clock.class, Clock::systemUTC)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(XxlJobScheduledTaskAdapter.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(RedisXxlJobSsoTicketService.class)
    static class RedisTicketServiceConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    @Import(XxlJobScheduledTaskAdapter.class)
    static class ScheduledTaskAdapterConfiguration {
    }

    private static XxlJobProperties enabledUnreachableProperties() {
        XxlJobProperties properties = new XxlJobProperties();
        properties.setEnabled(true);
        properties.getExecutor().setAdminAddresses("http://127.0.0.1:1/xxl-job-admin");
        return properties;
    }
}
