package com.enterprise.testagent.xxljob;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 在平台 WebFlux 主上下文中装配不带 Linux 服务器亲和的 XXL executor。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "test-agent.xxl-job", name = "enabled", havingValue = "true")
public class XxlJobExecutorConfiguration {

    @Bean
    XxlJobSpringExecutor xxlJobSpringExecutor(XxlJobProperties properties) {
        XxlJobProperties.Executor executor = properties.getExecutor();
        XxlJobSpringExecutor springExecutor = new XxlJobSpringExecutor();
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
}
