package com.icbc.testagent.persistence;

import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 运行进程心跳存储；心跳 key 5 分钟过期，管理页据此排除僵死进程。
 */
public class RedisOpencodeProcessHeartbeatStore implements OpencodeProcessHeartbeatStore {

    private static final Duration HEARTBEAT_TTL = Duration.ofMinutes(5);
    private static final String BACKEND_KEY_PREFIX = "test-agent:runtime-heartbeat:backend:";
    private static final String OPENCODE_KEY_PREFIX = "test-agent:runtime-heartbeat:opencode:";
    private static final String BACKEND_INDEX_KEY = "test-agent:runtime-heartbeat:index:backend";
    private static final String OPENCODE_INDEX_KEY = "test-agent:runtime-heartbeat:index:opencode";

    private final StringRedisTemplate redisTemplate;

    /**
     * 注入 Redis 字符串模板；value 保存 epoch millis，TTL 才是活跃判定主依据。
     */
    public RedisOpencodeProcessHeartbeatStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public void recordBackendHeartbeat(BackendProcessId backendProcessId, Instant heartbeatAt) {
        String id = backendProcessId.value();
        redisTemplate.opsForValue().set(BACKEND_KEY_PREFIX + id, String.valueOf(heartbeatAt.toEpochMilli()), HEARTBEAT_TTL);
        redisTemplate.opsForSet().add(BACKEND_INDEX_KEY, id);
    }

    @Override
    public void recordOpencodeHeartbeat(OpencodeProcessId processId, Instant heartbeatAt) {
        String id = processId.value();
        redisTemplate.opsForValue().set(OPENCODE_KEY_PREFIX + id, String.valueOf(heartbeatAt.toEpochMilli()), HEARTBEAT_TTL);
        redisTemplate.opsForSet().add(OPENCODE_INDEX_KEY, id);
    }

    @Override
    public Set<BackendProcessId> liveBackendProcessIds() {
        Set<String> ids = liveIds(BACKEND_INDEX_KEY, BACKEND_KEY_PREFIX);
        Set<BackendProcessId> result = new LinkedHashSet<>();
        for (String id : ids) {
            result.add(new BackendProcessId(id));
        }
        return result;
    }

    @Override
    public Set<OpencodeProcessId> liveOpencodeProcessIds() {
        Set<String> ids = liveIds(OPENCODE_INDEX_KEY, OPENCODE_KEY_PREFIX);
        Set<OpencodeProcessId> result = new LinkedHashSet<>();
        for (String id : ids) {
            result.add(new OpencodeProcessId(id));
        }
        return result;
    }

    @Override
    public void cleanupExpiredHeartbeats() {
        cleanupIndex(BACKEND_INDEX_KEY, BACKEND_KEY_PREFIX);
        cleanupIndex(OPENCODE_INDEX_KEY, OPENCODE_KEY_PREFIX);
    }

    private Set<String> liveIds(String indexKey, String keyPrefix) {
        Set<String> indexed = redisTemplate.opsForSet().members(indexKey);
        if (indexed == null || indexed.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String id : indexed) {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(keyPrefix + id))) {
                result.add(id);
            }
        }
        return result;
    }

    private void cleanupIndex(String indexKey, String keyPrefix) {
        Set<String> indexed = redisTemplate.opsForSet().members(indexKey);
        if (indexed == null || indexed.isEmpty()) {
            return;
        }
        for (String id : indexed) {
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(keyPrefix + id))) {
                redisTemplate.opsForSet().remove(indexKey, id);
            }
        }
    }
}
