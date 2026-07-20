package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.auth.TokenSessionMarkerStore;
import com.enterprise.testagent.domain.user.UserId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisTokenStoreSessionMarkerTest {

    @Test
    void saveAndDeleteMaintainSha256SessionMarkerWithTokenLifetime() {
        StringRedisTemplate redis = org.mockito.Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = org.mockito.Mockito.mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        RedisTokenStore store = new RedisTokenStore(redis, new ObjectMapper().findAndRegisterModules());
        Instant issuedAt = Instant.parse("2026-07-20T00:00:00Z");
        AuthPrincipal principal = new AuthPrincipal(
                "secret-platform-token",
                new UserId("usr_xxl_marker"),
                "admin",
                "AUTH_XXL",
                List.of("SUPER_ADMIN"),
                issuedAt,
                issuedAt.plusSeconds(3600));

        store.save(principal);

        String digest = store.digest("secret-platform-token");
        assertThat(digest).hasSize(64).doesNotContain("secret-platform-token");
        verify(values).set(
                eq("test-agent:token-session:" + digest),
                eq("usr_xxl_marker"),
                eq(Duration.ofHours(1)));

        store.delete("secret-platform-token");

        verify(redis).delete("test-agent:token-session:" + digest);
    }

    @Test
    void validatesOnlyExistingDigestMarkers() {
        StringRedisTemplate redis = org.mockito.Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = org.mockito.Mockito.mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        RedisTokenStore store = new RedisTokenStore(redis, new ObjectMapper());
        String digest = TokenSessionMarkerStore.sha256("token");
        when(values.get("test-agent:token-session:" + digest)).thenReturn("usr_xxl_marker");

        assertThat(store.isActive(digest)).isTrue();
        assertThat(store.isActive(TokenSessionMarkerStore.sha256("expired"))).isFalse();
    }
}
