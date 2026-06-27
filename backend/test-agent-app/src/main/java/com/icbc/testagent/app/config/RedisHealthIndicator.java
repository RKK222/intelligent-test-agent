package com.icbc.testagent.app.config;

import java.net.InetSocketAddress;
import java.net.Socket;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Redis 健康检查。Redis 是系统必需依赖，这里只做轻量 TCP 连通性探测。
 */
@Component("redisRequired")
public class RedisHealthIndicator implements HealthIndicator {

    private final TestAgentRuntimeProperties properties;

    /**
     * 注入 Redis 连接配置。
     */
    public RedisHealthIndicator(TestAgentRuntimeProperties properties) {
        this.properties = properties;
    }

    /**
     * 返回 Redis 健康状态，仅做 TCP 连接探测且不发送业务命令。
     */
    @Override
    public Health health() {
        TestAgentRuntimeProperties.Redis redis = properties.getRedis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(redis.getHost(), redis.getPort()), Math.toIntExact(redis.getTimeout().toMillis()));
            return Health.up()
                    .withDetail("redis", "required")
                    .withDetail("host", redis.getHost())
                    .withDetail("port", redis.getPort())
                    .build();
        } catch (Exception exception) {
            return Health.down(exception)
                    .withDetail("redis", "required")
                    .withDetail("host", redis.getHost())
                    .withDetail("port", redis.getPort())
                    .build();
        }
    }
}
