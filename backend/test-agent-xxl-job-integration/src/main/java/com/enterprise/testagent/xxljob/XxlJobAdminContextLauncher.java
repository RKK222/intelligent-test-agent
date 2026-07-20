package com.enterprise.testagent.xxljob;

import org.springframework.context.ConfigurableApplicationContext;

/** 启动独立 Servlet Admin 子上下文的可测试端口。 */
@FunctionalInterface
public interface XxlJobAdminContextLauncher {

    ConfigurableApplicationContext launch(XxlJobAdminBridge bridge);
}
