package com.icbc.testagent.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.domain.configuration.CommonParameterLoadSnapshot;
import com.icbc.testagent.domain.configuration.CommonParameterLoadSnapshotStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 通用参数加载快照 Redis 存储；每台后端实例写入自己的加载快照，30 秒过期，进程崩溃后短期自动消失。
 *
 * <p>与运行进程心跳存储同样的索引 + TTL 模式：写快照时同步维护索引 set，读取时按索引遍历并跳过已过期键。
 */
public class RedisCommonParameterLoadSnapshotStore implements CommonParameterLoadSnapshotStore {

    private static final Duration SNAPSHOT_TTL = Duration.ofSeconds(30);
    private static final String SNAPSHOT_KEY_PREFIX = "test-agent:common-param-snapshot:backend:";
    private static final String SNAPSHOT_INDEX_KEY = "test-agent:common-param-snapshot:index:backend";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 注入 Redis 字符串模板；ObjectMapper 注册 JavaTime 模块以序列化 Instant。
     */
    public RedisCommonParameterLoadSnapshotStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Override
    public void record(CommonParameterLoadSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        String id = snapshot.backendProcessId();
        if (id == null || id.isBlank()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(SNAPSHOT_KEY_PREFIX + id, encode(snapshot), SNAPSHOT_TTL);
            redisTemplate.opsForSet().add(SNAPSHOT_INDEX_KEY, id);
        } catch (RuntimeException exception) {
            // Redis 不可用不应阻断参数刷新主流程；调用方已对写入失败兜底，此处仅向上抛由其记录。
            throw exception;
        }
    }

    @Override
    public List<CommonParameterLoadSnapshot> liveSnapshots() {
        Set<String> indexed = redisTemplate.opsForSet().members(SNAPSHOT_INDEX_KEY);
        if (indexed == null || indexed.isEmpty()) {
            return List.of();
        }
        List<CommonParameterLoadSnapshot> result = new ArrayList<>();
        for (String id : indexed) {
            String key = SNAPSHOT_KEY_PREFIX + id;
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                redisTemplate.opsForSet().remove(SNAPSHOT_INDEX_KEY, id);
                continue;
            }
            try {
                result.add(objectMapper.readValue(value, CommonParameterLoadSnapshot.class));
            } catch (JsonProcessingException exception) {
                // 损坏快照不阻断管理页，删除索引等待下一次刷新重建。
                redisTemplate.delete(key);
                redisTemplate.opsForSet().remove(SNAPSHOT_INDEX_KEY, id);
            }
        }
        return result.stream()
                .sorted(Comparator.comparing(CommonParameterLoadSnapshot::backendProcessId))
                .toList();
    }

    private String encode(CommonParameterLoadSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化通用参数加载快照", exception);
        }
    }
}
