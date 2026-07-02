package com.icbc.testagent.app.config;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Redis 启动连通性检查器。
 *
 * <p>Redis 是系统必需依赖（Token 存储、运行态心跳等），但 Lettuce 默认是懒连接，
 * 启动阶段不会立即暴露 Redis 不可达问题，运行时才以 1 秒超时错误散落在各业务日志里。
 * 本检查器在应用启动早期主动做一次 TCP 探测，失败时以 ERROR 级别打印清晰的 host:port，
 * 便于在启动日志中第一时间定位 Redis 网络问题，而不是从晦涩的 Reactor 堆栈里反推。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RedisStartupHealthCheck implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisStartupHealthCheck.class);

    private final DataRedisProperties properties;

    /**
     * 注入 Spring 标准 Redis 连接配置。
     */
    public RedisStartupHealthCheck(DataRedisProperties properties) {
        this.properties = properties;
    }

    /**
     * 启动时探测 Redis TCP 连通性；不可达时打印 host:port 的 ERROR 日志，但不阻断启动，
     * 让后续业务初始化按原有懒连接行为继续，错误仍会被运行时健康检查捕获。
     */
    @Override
    public void run(ApplicationArguments args) {
        String host = properties.getHost();
        int port = properties.getPort();
        long timeoutMillis = Math.max(500, redisConnectTimeoutMillis());

        // host 为空时表示未配置 Redis，跳过探测，交由各消费方按可选依赖处理。
        if (host == null || host.isBlank()) {
            LOGGER.info("Redis 未配置 host，跳过启动连通性探测。");
            return;
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), Math.toIntExact(timeoutMillis));
            LOGGER.info("Redis 连通性探测成功: host={}, port={}", host, port);
        } catch (Exception exception) {
            LOGGER.error("Redis 连接失败，请检查 Redis 是否可达: host={}, port={}（超时 {}ms）",
                    host, port, timeoutMillis, exception);
        }
    }

    private long redisConnectTimeoutMillis() {
        Duration timeout = properties.getConnectTimeout() != null
                ? properties.getConnectTimeout()
                : properties.getTimeout();
        return timeout == null ? 1000 : Math.max(1, timeout.toMillis());
    }
}
