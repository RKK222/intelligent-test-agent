package com.example.testagent.app.config;

import java.net.InetSocketAddress;
import java.net.Socket;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Redis 可选健康检查。未启用时显式报告 disabled；启用时只做轻量 TCP 连通性探测。
 */
@Component("redisOptional")
public class RedisOptionalHealthIndicator implements HealthIndicator {

    private final TestAgentRuntimeProperties properties;

    /**
     * 注入运行配置，Redis 未启用时不会尝试网络连接。
     */
    public RedisOptionalHealthIndicator(TestAgentRuntimeProperties properties) {
        this.properties = properties;
    }

    /**
     * 返回 Redis 可选健康状态，启用时仅做 TCP 连接探测且不发送业务命令。
     */
    @Override
    public Health health() {
        TestAgentRuntimeProperties.Redis redis = properties.getRedis();
        if (!redis.isEnabled()) {
            return Health.up().withDetail("redis", "disabled").build();
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(redis.getHost(), redis.getPort()), Math.toIntExact(redis.getTimeout().toMillis()));
            return Health.up()
                    .withDetail("redis", "enabled")
                    .withDetail("host", redis.getHost())
                    .withDetail("port", redis.getPort())
                    .build();
        } catch (Exception exception) {
            return Health.down(exception)
                    .withDetail("redis", "enabled")
                    .withDetail("host", redis.getHost())
                    .withDetail("port", redis.getPort())
                    .build();
        }
    }
}
