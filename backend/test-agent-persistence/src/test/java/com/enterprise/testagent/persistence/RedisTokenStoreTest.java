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
 * 验证删除用户时只扫描 v2 命名空间并撤销目标用户平台 Token。
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
        when(cursor.next()).thenReturn("test-agent:token:v2:target", "test-agent:token:v2:other");

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        when(values.get("test-agent:token:v2:target"))
                .thenReturn(objectMapper.writeValueAsString(principal("token-target", "usr_target")));
        when(values.get("test-agent:token:v2:other"))
                .thenReturn(objectMapper.writeValueAsString(principal("token-other", "usr_other")));

        RedisTokenStore store = new RedisTokenStore(redisTemplate, objectMapper);
        store.deleteByUserIds(List.of(new UserId("usr_target")));

        verify(redisTemplate).delete(List.of("test-agent:token:v2:target"));
        verify(redisTemplate).delete(List.of(
                "test-agent:token-session:v2:" + TokenSessionMarkerStore.sha256("token-target")));
        verify(cursor).close();
    }

    @Test
    void legacyV1TokenCannotBeReadByV2Authentication() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        when(values.get("test-agent:token:legacy"))
                .thenReturn(objectMapper.writeValueAsString(principal("legacy", "usr_target")));

        RedisTokenStore store = new RedisTokenStore(redisTemplate, objectMapper);

        org.assertj.core.api.Assertions.assertThat(store.findByToken("legacy")).isEmpty();
        verify(values).get("test-agent:token:v2:legacy");
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
