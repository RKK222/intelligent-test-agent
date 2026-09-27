package com.enterprise.testagent.persistence;

import com.enterprise.testagent.domain.team.TeamReviewModels.Scope;
import com.enterprise.testagent.domain.team.TeamReviewScopeStore;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

/** Redis 中仅保存来源逻辑身份，正文永不缓存；固定两小时自动回收。 */
@Repository
public class RedisTeamReviewScopeStore implements TeamReviewScopeStore {
    private static final String PREFIX = "test-agent:team-review:";
    private static final DefaultRedisScript<Long> BIND = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
            local previous = redis.call('GET', KEYS[2])
            if previous and previous ~= ARGV[1] then return 0 end
            redis.call('SET', KEYS[2], ARGV[1], 'PX', redis.call('PTTL', KEYS[1]))
            return 1
            """, Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RedisTeamReviewScopeStore(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public void save(Scope scope) {
        try {
            Duration ttl = Duration.between(Instant.now(), scope.expiresAt());
            if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException();
            redis.opsForValue().set(key(scope.id()), mapper.writeValueAsString(scope), ttl);
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.CONFLICT, "审阅范围暂时无法保存");
        }
    }

    public Optional<Scope> find(String id) {
        String json = redis.opsForValue().get(key(id));
        if (json == null) return Optional.empty();
        try {
            return Optional.of(mapper.readValue(json, Scope.class));
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.CONFLICT, "审阅范围无法恢复，请刷新");
        }
    }

    public boolean bindRun(String id, String runId) {
        return Long.valueOf(1).equals(redis.execute(BIND, List.of(key(id), key(id) + ":run"), runId));
    }

    private String key(String id) {
        if (id == null || !id.matches("trv_[a-f0-9]{32}"))
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "审阅范围标识无效");
        return PREFIX + id;
    }
}
