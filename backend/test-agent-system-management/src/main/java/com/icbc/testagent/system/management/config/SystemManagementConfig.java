package com.icbc.testagent.system.management.config;

import com.icbc.testagent.domain.auth.TokenStore;
import com.icbc.testagent.domain.dictionary.DictionaryRepository;
import com.icbc.testagent.domain.dictionary.UserRoleRepository;
import com.icbc.testagent.domain.user.UserLoginLogRepository;
import com.icbc.testagent.domain.user.UserRepository;
import com.icbc.testagent.system.management.auth.AuthApplicationService;
import com.icbc.testagent.system.management.user.UserDomainService;
import com.icbc.testagent.system.management.user.UserManagementApplicationService;
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
