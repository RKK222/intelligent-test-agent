package com.enterprise.testagent.integration.skillhub;

import com.enterprise.testagent.domain.hub.SkillHubGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 外部 SkillHub HTTP 适配器装配。 */
@Configuration
@EnableConfigurationProperties(SkillHubProperties.class)
public class SkillHubIntegrationConfig {

    @Bean
    SkillHubGateway skillHubGateway(SkillHubProperties properties, ObjectMapper objectMapper) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new SkillHubHttpGateway(properties, client, objectMapper);
    }
}
