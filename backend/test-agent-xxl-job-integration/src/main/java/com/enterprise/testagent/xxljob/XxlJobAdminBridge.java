package com.enterprise.testagent.xxljob;

/**
 * 主 WebFlux 上下文向 Servlet Admin 子上下文暴露的最小桥接标记；后续扩展只传递认证端口，不共享数据库 Bean。
 */
public interface XxlJobAdminBridge {

    /** 原子消费一次性票据。 */
    java.util.Optional<XxlJobSsoIdentity> consumeTicket(String ticket);

    /** 每个 Admin 请求都据此确认平台 Token marker 仍然有效。 */
    boolean isPlatformSessionActive(String sessionDigest);
}
