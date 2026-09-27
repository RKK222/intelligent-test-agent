package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.enterprise.testagent.domain.team.TeamReviewModels.*;
import com.enterprise.testagent.domain.team.TeamScopeMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

/** Scope 只保存短效逻辑身份；跨 Run 绑定的原子结果必须严格判断。 */
class RedisTeamReviewScopeStoreTest {
    @Test void roundTripKeepsMetadataOnlyAndSetsTtl() throws Exception {
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var store = new RedisTeamReviewScopeStore(redis, mapper);
        String id = "trv_01234567890123456789012345678901";
        var scope = new Scope(id, "admin", "digest", TeamScopeMode.MY_TEAM, "admin", "version", null,
                List.of(new Source("member", "成员", "personal", "runtime", "server")), Instant.now().plusSeconds(7200));
        store.save(scope);
        verify(values).set(eq("test-agent:team-review:" + id), argThat(json -> !json.contains("contentBase64") && !json.contains("repoRootPath")), any(Duration.class));
        when(values.get("test-agent:team-review:" + id)).thenReturn(mapper.writeValueAsString(scope));
        assertThat(store.find(id)).contains(scope);
        assertThatThrownBy(() -> store.find("../scope")).hasMessageContaining("标识无效");
    }
    @Test void runBindDoesNotTreatMissingOrConflictingScopeAsSuccess() {
        var redis = mock(StringRedisTemplate.class);
        var store = new RedisTeamReviewScopeStore(redis, new ObjectMapper());
        String id = "trv_01234567890123456789012345678901";
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L, 1L);
        assertThat(store.bindRun(id, "run-a")).isFalse();
        assertThat(store.bindRun(id, "run-a")).isTrue();
    }
}
