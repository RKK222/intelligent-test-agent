package com.enterprise.testagent.system.management.config;

import com.enterprise.testagent.domain.auth.TokenStore;
import com.enterprise.testagent.domain.dictionary.DictionaryRepository;
import com.enterprise.testagent.domain.dictionary.UserRoleRepository;
import com.enterprise.testagent.domain.user.UserLoginLogRepository;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.system.management.auth.AuthApplicationService;
import com.enterprise.testagent.system.management.user.ThirdPartyUserApiClient;
import com.enterprise.testagent.system.management.user.UserDomainService;
import com.enterprise.testagent.system.management.user.UserManagementApplicationService;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(ThirdPartyApiProperties.class)
public class SystemManagementConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(30).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());
        return new RestTemplate(factory);
    }

    @Bean
    public ThirdPartyUserApiClient thirdPartyUserApiClient(ThirdPartyApiProperties properties, RestTemplate restTemplate) {
        return new ThirdPartyUserApiClient(properties, restTemplate);
    }

    @Bean
    public UserDomainService userDomainService(UserRepository userRepository, ThirdPartyUserApiClient thirdPartyUserApiClient) {
        return new UserDomainService(userRepository, thirdPartyUserApiClient);
    }

    /**
     * 认证应用服务 Bean。
     */
    @Bean
    public AuthApplicationService authApplicationService(
            UserDomainService userDomainService,
            TokenStore tokenStore,
            UserLoginLogRepository loginLogRepository,
            UserRoleRepository userRoleRepository,
            DictionaryRepository dictionaryRepository) {
        return new AuthApplicationService(
                userDomainService,
                tokenStore,
                loginLogRepository,
                userRoleRepository,
                dictionaryRepository);
    }

    /**
     * 用户管理应用服务 Bean，用于查询用户、创建测试用户、调整角色和查询可选角色。
     */
    @Bean
    public UserManagementApplicationService userManagementApplicationService(
            UserDomainService userDomainService,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            DictionaryRepository dictionaryRepository) {
        return new UserManagementApplicationService(
                userDomainService,
                userRepository,
                userRoleRepository,
                dictionaryRepository);
    }
}
