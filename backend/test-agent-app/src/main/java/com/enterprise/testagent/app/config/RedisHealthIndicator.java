package com.enterprise.testagent.app.config;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Redis 健康检查。Redis 是系统必需依赖，这里只做轻量 TCP 连通性探测。
 */
@Component("redisRequired")
public class RedisHealthIndicator implements HealthIndicator {

    private final DataRedisProperties properties;

    /**
     * 注入 Spring 标准 Redis 连接配置。
     */
    public RedisHealthIndicator(DataRedisProperties properties) {
        this.properties = properties;
    }

    /**
     * 返回 Redis 健康状态，仅做 TCP 连接探测且不发送业务命令。
     */
    @Override
    public Health health() {
        String host = properties.getHost();
        int port = properties.getPort();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), redisConnectTimeoutMillis());
            return Health.up()
                    .withDetail("redis", "required")
                    .withDetail("host", host)
                    .withDetail("port", port)
                    .build();
        } catch (Exception exception) {
            return Health.down(exception)
                    .withDetail("redis", "required")
                    .withDetail("host", host)
                    .withDetail("port", port)
                    .build();
        }
    }

    private int redisConnectTimeoutMillis() {
        Duration timeout = properties.getConnectTimeout() != null
                ? properties.getConnectTimeout()
                : properties.getTimeout();
        long timeoutMillis = timeout == null ? 1000 : timeout.toMillis();
        return Math.toIntExact(Math.max(1, timeoutMillis));
    }
}
