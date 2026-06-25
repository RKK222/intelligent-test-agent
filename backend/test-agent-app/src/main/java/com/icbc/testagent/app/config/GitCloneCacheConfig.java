package com.icbc.testagent.app.config;

import com.icbc.testagent.configuration.management.GitCloneCacheService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Git 克隆缓存配置。
 */
@Configuration
public class GitCloneCacheConfig {

    /**
     * 创建 Git 克隆缓存服务 Bean。
     */
    @Bean
    public GitCloneCacheService gitCloneCacheService(TestAgentRuntimeProperties properties) {
        TestAgentRuntimeProperties.GitCloneCache config = properties.getGitCloneCache();
        return new GitCloneCacheService(
                config.getCacheRoot(),
                config.getCacheExpiry(),
                config.getCloneTimeout());
    }
}
