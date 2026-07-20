package com.enterprise.testagent.xxljob.admin;

import com.xxl.job.admin.XxlJobAdminApplication;
import com.xxl.job.admin.framework.web.xxlsso.SimpleLoginStore;
import com.xxl.job.admin.framework.web.xxlsso.XxlSsoConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/** 独立 Servlet 子上下文入口；排除上游启动类和登录实现，其余 Admin 源码保持原样。 */
@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ComponentScan(
        basePackages = {"com.xxl.job.admin", "com.enterprise.testagent.xxljob.admin"},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {XxlJobAdminApplication.class, XxlSsoConfig.class, SimpleLoginStore.class}))
@MapperScan({"com.xxl.job.admin.business.mapper", "com.xxl.job.admin.framework.mapper", "com.enterprise.testagent.xxljob.admin"})
public class PlatformXxlJobAdminApplication {
}
