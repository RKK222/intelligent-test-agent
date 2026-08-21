package com.enterprise.testagent.system.management.config;

import com.enterprise.testagent.domain.auth.TokenStore;
import com.enterprise.testagent.domain.dictionary.DictionaryRepository;
import com.enterprise.testagent.domain.dictionary.UserRoleRepository;
import com.enterprise.testagent.domain.user.UserLoginLogRepository;
import com.enterprise.testagent.domain.user.UserDeletionRepository;
import com.enterprise.testagent.domain.user.UserManagementQueryRepository;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.tcds.TcdsGateway;
import com.enterprise.testagent.system.management.auth.AuthApplicationService;
import com.enterprise.testagent.system.management.localclient.LocalClientReleaseCatalogProperties;
import com.enterprise.testagent.system.management.user.ThirdPartyUserApiClient;
import com.enterprise.testagent.system.management.user.UserDomainService;
import com.enterprise.testagent.system.management.user.UserManagementApplicationService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LocalClientReleaseCatalogProperties.class)
public class SystemManagementConfig {

    @Bean
    public ThirdPartyUserApiClient thirdPartyUserApiClient(TcdsGateway tcdsGateway) {
        return new ThirdPartyUserApiClient(tcdsGateway);
    }

    @Bean
    public UserDomainService userDomainService(
            UserRepository userRepository,
            ThirdPartyUserApiClient thirdPartyUserApiClient,
            UserRoleRepository userRoleRepository,
            DictionaryRepository dictionaryRepository) {
        return new UserDomainService(
                userRepository,
                thirdPartyUserApiClient,
                userRoleRepository,
                dictionaryRepository);
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
     * 用户管理应用服务 Bean，用于查询、测试造号、角色调整、删除和 TCDS 信息同步。
     */
    @Bean
    public UserManagementApplicationService userManagementApplicationService(
            UserDomainService userDomainService,
            UserRepository userRepository,
            UserManagementQueryRepository userManagementQueryRepository,
            UserDeletionRepository userDeletionRepository,
            UserRoleRepository userRoleRepository,
            DictionaryRepository dictionaryRepository,
            TokenStore tokenStore,
            ThirdPartyUserApiClient thirdPartyUserApiClient) {
        return new UserManagementApplicationService(
                userDomainService,
                userRepository,
                userManagementQueryRepository,
                userDeletionRepository,
                userRoleRepository,
                dictionaryRepository,
                tokenStore,
                thirdPartyUserApiClient);
    }
}
