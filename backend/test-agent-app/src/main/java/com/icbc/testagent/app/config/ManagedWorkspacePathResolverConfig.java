package com.icbc.testagent.app.config;

import com.icbc.testagent.domain.configuration.CommonParameterValues;
import com.icbc.testagent.domain.workspace.ManagedWorkspacePathResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 托管工作区路径解析器装配。解析器依赖通用参数只读视图，供 workspace、runtime、terminal 等模块统一复用。
 */
@Configuration
class ManagedWorkspacePathResolverConfig {

    @Bean
    ManagedWorkspacePathResolver managedWorkspacePathResolver(CommonParameterValues commonParameterValues) {
        return new ManagedWorkspacePathResolver(commonParameterValues);
    }
}
