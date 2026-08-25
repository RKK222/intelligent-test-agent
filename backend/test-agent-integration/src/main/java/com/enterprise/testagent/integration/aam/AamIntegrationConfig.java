package com.enterprise.testagent.integration.aam;

import com.enterprise.testagent.domain.auth.AamLoginTokenVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** AAM 登录验真适配器装配。 */
@Configuration
@EnableConfigurationProperties(AamProperties.class)
public class AamIntegrationConfig {

    /** AAM 调用禁止自动跟随重定向，也不配置任何自动重试。 */
    @Bean
    HttpClient aamHttpClient(AamProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean
    AamLoginTokenVerifier aamLoginTokenVerifier(
            AamProperties properties,
            @Qualifier("aamHttpClient") HttpClient httpClient,
            ObjectMapper objectMapper) {
        return new AamHttpLoginTokenVerifier(properties, httpClient, objectMapper);
    }
}
