package com.enterprise.testagent.persistence;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.auth.TokenSessionMarkerStore;
import com.enterprise.testagent.domain.user.UserId;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * 验证删除用户时只撤销目标用户 Token，并使用 SCAN 兼容历史已签发登录态。
 */
class RedisTokenStoreTest {

    @Test
    void deleteByUserIdsScansAndDeletesOnlyMatchingTokens() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        Cursor<String> cursor = mock(Cursor.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, true, false);
        when(cursor.next()).thenReturn("test-agent:token:target", "test-agent:token:other");

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        when(values.get("test-agent:token:target"))
                .thenReturn(objectMapper.writeValueAsString(principal("token-target", "usr_target")));
        when(values.get("test-agent:token:other"))
                .thenReturn(objectMapper.writeValueAsString(principal("token-other", "usr_other")));

        RedisTokenStore store = new RedisTokenStore(redisTemplate, objectMapper);
        store.deleteByUserIds(List.of(new UserId("usr_target")));

        verify(redisTemplate).delete(List.of("test-agent:token:target"));
        verify(redisTemplate).delete(List.of(
                "test-agent:token-session:" + TokenSessionMarkerStore.sha256("token-target")));
        verify(cursor).close();
    }

    private AuthPrincipal principal(String token, String userId) {
        Instant issuedAt = Instant.parse("2026-07-21T00:00:00Z");
        return new AuthPrincipal(
                token,
                new UserId(userId),
                userId,
                "AUTH_" + userId,
                List.of("USER"),
                issuedAt,
                issuedAt.plusSeconds(3600));
    }
}
