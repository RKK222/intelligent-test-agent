package com.icbc.testagent.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.domain.broadcast.ServerBroadcastEvent;
import com.icbc.testagent.domain.broadcast.ServerBroadcastHandler;
import com.icbc.testagent.domain.broadcast.ServerBroadcastPublisher;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * 基于 Redis pub/sub 的服务器广播实现，用于把工作区版本同步等内部事件 fan-out 到其他后端。
 */
@Component
@ConditionalOnProperty(prefix = "test-agent.server-broadcast", name = "enabled", havingValue = "true")
public class RedisServerBroadcastPublisher implements ServerBroadcastPublisher, SmartLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisServerBroadcastPublisher.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String channel;
    private final String instanceId;
    private final List<ServerBroadcastHandler> handlers;
    private final RedisMessageListenerContainer listenerContainer;
    private final MessageListener messageListener = this::onMessage;
    private volatile boolean running;

    /**
     * 注入 Redis 连接、JSON mapper 和业务 handler；实例 ID 为空时生成进程内随机值。
     */
    public RedisServerBroadcastPublisher(
            StringRedisTemplate redisTemplate,
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper,
            List<ServerBroadcastHandler> handlers,
            @Value("${test-agent.server-broadcast.channel:test-agent:server-broadcast}") String channel,
            @Value("${test-agent.server-broadcast.instance-id:}") String configuredInstanceId) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.handlers = handlers == null ? List.of() : List.copyOf(handlers);
        this.channel = Objects.requireNonNull(channel, "channel must not be null");
        this.instanceId = configuredInstanceId == null || configuredInstanceId.isBlank()
                ? UUID.randomUUID().toString()
                : configuredInstanceId;
        this.listenerContainer = new RedisMessageListenerContainer();
        this.listenerContainer.setConnectionFactory(Objects.requireNonNull(connectionFactory, "connectionFactory must not be null"));
    }

    @Override
    public String instanceId() {
        return instanceId;
    }

    /**
     * 发布事件到 Redis；异常仅记录脱敏告警，避免影响本机已完成的工作区操作。
     */
    @Override
    public void publish(ServerBroadcastEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        try {
            redisTemplate.convertAndSend(channel, objectMapper.writeValueAsString(event));
        } catch (RuntimeException | JsonProcessingException exception) {
            LOGGER.warn(
                    "Failed to publish server broadcast event, type={}, eventId={}",
                    event.type(),
                    event.eventId(),
                    exception);
        }
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        try {
            listenerContainer.afterPropertiesSet();
            listenerContainer.addMessageListener(messageListener, new ChannelTopic(channel));
            listenerContainer.start();
            running = true;
        } catch (RuntimeException exception) {
            running = false;
            LOGGER.warn("Redis server broadcast bus is unavailable, channel={}", channel, exception);
        }
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        try {
            listenerContainer.removeMessageListener(messageListener, new ChannelTopic(channel));
            listenerContainer.stop();
        } finally {
            running = false;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void onMessage(Message message, byte[] pattern) {
        try {
            ServerBroadcastEvent event = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8),
                    ServerBroadcastEvent.class);
            if (instanceId.equals(event.originInstanceId())) {
                return;
            }
            for (ServerBroadcastHandler handler : handlers) {
                if (!handler.supports(event.type())) {
                    continue;
                }
                try {
                    handler.handle(event);
                } catch (RuntimeException exception) {
                    LOGGER.warn(
                            "Server broadcast handler failed, type={}, eventId={}",
                            event.type(),
                            event.eventId(),
                            exception);
                }
            }
        } catch (RuntimeException | JsonProcessingException exception) {
            LOGGER.warn("Failed to consume server broadcast event, channel={}", channel, exception);
        }
    }
}
