package com.enterprise.testagent.xxljob;

import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 在平台 WebFlux 主上下文中装配不带 Linux 服务器亲和的 XXL executor。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "test-agent.xxl-job", name = "enabled", havingValue = "true")
public class XxlJobExecutorConfiguration {

    @Bean
    XxlJobEndpointResolver xxlJobEndpointResolver() {
        return new XxlJobEndpointResolver();
    }

    @Bean
    XxlJobEndpointResolver.Endpoints xxlJobEndpoints(
            XxlJobProperties properties,
            BackendInstanceIdentity backendIdentity,
            XxlJobEndpointResolver endpointResolver) {
        return endpointResolver.resolve(properties, backendIdentity);
    }

    @Bean
    DeferredXxlJobSpringExecutor xxlJobSpringExecutor(
            XxlJobProperties properties,
            XxlJobEndpointResolver.Endpoints endpoints) {
        XxlJobProperties.Executor executor = properties.getExecutor();
        DeferredXxlJobSpringExecutor springExecutor = new DeferredXxlJobSpringExecutor();
        springExecutor.setAdminAddresses(endpoints.adminAddress());
        springExecutor.setAccessToken(properties.getAccessToken());
        springExecutor.setEnabled(true);
        springExecutor.setAppname(executor.getAppName());
        springExecutor.setAddress(endpoints.executorAddress());
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
            XxlJobEndpointResolver.Endpoints endpoints,
            XxlJobAdminReadinessProbe readinessProbe,
            DeferredXxlJobSpringExecutor executor) {
        return new XxlJobExecutorLifecycle(properties, endpoints, readinessProbe, executor);
    }
}
