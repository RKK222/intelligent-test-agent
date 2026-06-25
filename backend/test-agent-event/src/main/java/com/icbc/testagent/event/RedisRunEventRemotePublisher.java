package com.icbc.testagent.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.domain.run.RunId;
import java.nio.charset.StandardCharsets;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * 基于 Redis pub/sub 的 RunEvent 跨实例广播实现，仅在显式开启时作为实时增强通道。
 */
@Component
@ConditionalOnProperty(prefix = "test-agent.run-event.redis-bus", name = "enabled", havingValue = "true")
public class RedisRunEventRemotePublisher implements RunEventRemotePublisher, SmartLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisRunEventRemotePublisher.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String channel;
    private final String instanceId;
    private final RedisMessageListenerContainer listenerContainer;
    private final MessageListener messageListener = this::onMessage;
    private final Sinks.Many<RunEventLiveEvent> sink = Sinks.many().multicast().directBestEffort();
    private volatile boolean running;

    /**
     * 注入 Redis 模板和连接工厂，channel 与 instanceId 允许通过环境变量覆盖。
     */
    public RedisRunEventRemotePublisher(
            StringRedisTemplate redisTemplate,
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper,
            @Value("${test-agent.run-event.redis-bus.channel:test-agent:run-events}") String channel,
            @Value("${test-agent.run-event.redis-bus.instance-id:}") String configuredInstanceId) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.channel = Objects.requireNonNull(channel, "channel must not be null");
        this.instanceId = configuredInstanceId == null || configuredInstanceId.isBlank()
                ? UUID.randomUUID().toString()
                : configuredInstanceId;
        this.listenerContainer = new RedisMessageListenerContainer();
        this.listenerContainer.setConnectionFactory(Objects.requireNonNull(
                connectionFactory, "connectionFactory must not be null"));
    }

    /**
     * 将本机事件序列化后发布到 Redis channel；失败只记录脱敏告警，不影响本机 SSE。
     */
    @Override
    public void publish(RunEventLiveEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        try {
            redisTemplate.convertAndSend(
                    channel,
                    objectMapper.writeValueAsString(new RedisRunEventEnvelope(instanceId, event.durable(), event.payload())));
        } catch (RuntimeException | JsonProcessingException exception) {
            LOGGER.warn(
                    "Failed to publish run event to Redis bus, runId={}, eventId={}",
                    event.payload().runId(),
                    event.payload().eventId(),
                    exception);
        }
    }

    /**
     * 订阅 Redis 转发到本机的事件，并按 runId 过滤给 SSE 连接。
     */
    @Override
    public Flux<RunEventLiveEvent> stream(RunId runId) {
        Objects.requireNonNull(runId, "runId must not be null");
        return sink.asFlux().filter(event -> runId.value().equals(event.payload().runId()));
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
            LOGGER.warn("Redis run event bus is unavailable, fallback to local live bus channel={}", channel, exception);
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
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            RedisRunEventEnvelope envelope = objectMapper.readValue(body, RedisRunEventEnvelope.class);
            if (instanceId.equals(envelope.originInstanceId())) {
                return;
            }
            RunEventLiveEvent event = envelope.durable()
                    ? RunEventLiveEvent.durable(envelope.payload())
                    : RunEventLiveEvent.transientOnly(envelope.payload());
            sink.tryEmitNext(event);
        } catch (RuntimeException | JsonProcessingException exception) {
            LOGGER.warn("Failed to consume run event from Redis bus channel={}", channel, exception);
        }
    }

    private record RedisRunEventEnvelope(String originInstanceId, boolean durable, RunEventSsePayload payload) {}
}
