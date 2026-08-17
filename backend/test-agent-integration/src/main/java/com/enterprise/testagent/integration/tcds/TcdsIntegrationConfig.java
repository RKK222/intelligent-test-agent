package com.enterprise.testagent.integration.tcds;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** TCDS HTTP 适配器装配。 */
@Configuration
@EnableConfigurationProperties(TcdsProperties.class)
public class TcdsIntegrationConfig {

    /** 禁止自动跟随重定向，由下载程序逐跳执行协议和次数校验。 */
    @Bean
    HttpClient tcdsHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean
    TcdsHttpRequestFactory tcdsHttpRequestFactory(TcdsProperties properties) {
        return new TcdsHttpRequestFactory(properties);
    }

    @Bean
    TcdsHttpGateway tcdsGateway(
            TcdsHttpRequestFactory requestFactory,
            HttpClient tcdsHttpClient,
            ObjectMapper objectMapper) {
        return new TcdsHttpGateway(requestFactory, tcdsHttpClient, objectMapper);
    }

    @Bean
    TcdsCaseMaintenanceService tcdsCaseMaintenanceService(
            TcdsHttpRequestFactory requestFactory,
            HttpClient tcdsHttpClient,
            ObjectMapper objectMapper) {
        return new TcdsCaseMaintenanceService(requestFactory, tcdsHttpClient, objectMapper);
    }
}
