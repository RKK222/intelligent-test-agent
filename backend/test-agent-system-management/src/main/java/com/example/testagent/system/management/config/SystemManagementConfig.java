package com.example.testagent.system.management.config;

import com.example.testagent.domain.auth.TokenStore;
import com.example.testagent.domain.user.UserLoginLogRepository;
import com.example.testagent.domain.user.UserRepository;
import com.example.testagent.system.management.auth.AuthApplicationService;
import com.example.testagent.system.management.user.UserDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 系统管理模块的 Spring 配置，暴露业务服务 Bean。
 */
@Configuration
public class SystemManagementConfig {

    /**
     * 用户领域服务 Bean。
     */
    @Bean
    public UserDomainService userDomainService(UserRepository userRepository) {
        return new UserDomainService(userRepository);
    }

    /**
     * 认证应用服务 Bean。
     */
    @Bean
    public AuthApplicationService authApplicationService(
            UserDomainService userDomainService,
            TokenStore tokenStore,
            UserLoginLogRepository loginLogRepository) {
        return new AuthApplicationService(userDomainService, tokenStore, loginLogRepository);
    }
}
