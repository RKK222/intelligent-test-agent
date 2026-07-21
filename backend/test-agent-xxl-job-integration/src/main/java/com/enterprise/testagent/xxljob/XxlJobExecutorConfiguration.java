package com.enterprise.testagent.xxljob;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 在平台 WebFlux 主上下文中装配不带 Linux 服务器亲和的 XXL executor。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "test-agent.xxl-job", name = "enabled", havingValue = "true")
public class XxlJobExecutorConfiguration {

    @Bean
    DeferredXxlJobSpringExecutor xxlJobSpringExecutor(XxlJobProperties properties) {
        XxlJobProperties.Executor executor = properties.getExecutor();
        DeferredXxlJobSpringExecutor springExecutor = new DeferredXxlJobSpringExecutor();
        springExecutor.setAdminAddresses(executor.getAdminAddresses());
        springExecutor.setAccessToken(properties.getAccessToken());
        springExecutor.setEnabled(true);
        springExecutor.setAppname(executor.getAppName());
        springExecutor.setAddress(executor.getAddress());
        springExecutor.setIp(executor.getIp());
        springExecutor.setPort(executor.getPort());
        springExecutor.setLogPath(executor.getLogPath());
        springExecutor.setLogRetentionDays(executor.getLogRetentionDays());
        return springExecutor;
    }

    @Bean
    XxlJobAdminReadinessProbe xxlJobAdminReadinessProbe() {
        return new HttpXxlJobAdminReadinessProbe();
    }

    @Bean
    XxlJobExecutorLifecycle xxlJobExecutorLifecycle(
            XxlJobProperties properties,
            XxlJobAdminReadinessProbe readinessProbe,
            DeferredXxlJobSpringExecutor executor) {
        return new XxlJobExecutorLifecycle(properties, readinessProbe, executor);
    }
}
