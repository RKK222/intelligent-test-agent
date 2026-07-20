package com.enterprise.testagent.xxljob.admin;

import com.xxl.sso.core.auth.interceptor.XxlSsoWebInterceptor;
import com.xxl.sso.core.bootstrap.XxlSsoBootstrap;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 替换上游 SimpleLoginStore 配置，使用平台会话 marker 校验。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PlatformXxlSsoConfiguration implements WebMvcConfigurer {

    static final String TOKEN_KEY = "test_agent_xxl_login";

    @Bean(initMethod = "start", destroyMethod = "stop")
    XxlSsoBootstrap platformXxlSsoBootstrap(PlatformXxlLoginStore loginStore) {
        XxlSsoBootstrap bootstrap = new XxlSsoBootstrap();
        bootstrap.setLoginStore(loginStore);
        bootstrap.setTokenKey(TOKEN_KEY);
        bootstrap.setTokenTimeout(Duration.ofDays(1).toMillis());
        return bootstrap;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        XxlSsoWebInterceptor interceptor = new XxlSsoWebInterceptor(
                "/static/**,/platform-sso/**,/error,/actuator/**",
                "/platform-sso/required");
        registry.addInterceptor(interceptor).addPathPatterns("/**");
    }
}
