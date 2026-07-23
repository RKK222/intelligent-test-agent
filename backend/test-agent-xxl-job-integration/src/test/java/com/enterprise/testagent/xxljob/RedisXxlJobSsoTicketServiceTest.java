package com.enterprise.testagent.xxljob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.auth.TokenSessionMarkerStore;
import com.enterprise.testagent.domain.user.UserId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

class RedisXxlJobSsoTicketServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-20T00:00:00Z");

    @Test
    void issues256BitTicketWithoutPersistingRawPlatformToken() {
        Fixture fixture = fixture();

        XxlJobSsoTicketIssue issue = fixture.service().issue(principal());

        assertThat(issue.ticket()).matches("[A-Za-z0-9_-]{43}");
        assertThat(issue.expiresAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(issue.formAction()).isEqualTo("/xxl-job-admin/platform-sso/login");
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(fixture.values()).set(
                eq("test-agent:xxl-job:sso-ticket:" + issue.ticket()),
                json.capture(),
                eq(Duration.ofSeconds(60)));
        assertThat(json.getValue())
                .doesNotContain("raw-platform-token")
                .contains(TokenSessionMarkerStore.sha256("raw-platform-token"));
    }

    @Test
    void consumesTicketExactlyOnceAndRejectsExpiredPayload() {
        Fixture fixture = fixture();
        XxlJobSsoTicketIssue issue = fixture.service().issue(principal());
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(fixture.values()).set(any(), json.capture(), any(Duration.class));
        when(fixture.redis().execute(
                        any(RedisScript.class),
                        eq(List.of("test-agent:xxl-job:sso-ticket:" + issue.ticket()))))
                .thenReturn(json.getValue(), (String) null);

        assertThat(fixture.service().consume(issue.ticket()))
                .contains(new XxlJobSsoIdentity(
                        "usr_xxl_admin",
                        "平台管理员",
                        TokenSessionMarkerStore.sha256("raw-platform-token"),
                        NOW.plusSeconds(3600)));
        assertThat(fixture.service().consume(issue.ticket())).isEmpty();

        Clock expiredClock = Clock.fixed(NOW.plusSeconds(61), ZoneOffset.UTC);
        RedisXxlJobSsoTicketService expiredService = fixture.withClock(expiredClock);
        when(fixture.redis().execute(
                        any(RedisScript.class),
                        eq(List.of("test-agent:xxl-job:sso-ticket:" + issue.ticket()))))
                .thenReturn(json.getValue());
        assertThat(expiredService.consume(issue.ticket())).isEmpty();
    }

    private static Fixture fixture() {
        StringRedisTemplate redis = org.mockito.Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = org.mockito.Mockito.mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        XxlJobProperties properties = new XxlJobProperties();
        TokenSessionMarkerStore markers = org.mockito.Mockito.mock(TokenSessionMarkerStore.class);
        when(markers.digest("raw-platform-token"))
                .thenReturn(TokenSessionMarkerStore.sha256("raw-platform-token"));
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        return new Fixture(redis, values, properties, markers, mapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AuthPrincipal principal() {
        return new AuthPrincipal(
                "raw-platform-token",
                new UserId("usr_xxl_admin"),
                "平台管理员",
                "AUTH_XXL_ADMIN",
                List.of("SUPER_ADMIN"),
                NOW,
                NOW.plusSeconds(3600));
    }

    private record Fixture(
            StringRedisTemplate redis,
            ValueOperations<String, String> values,
            XxlJobProperties properties,
            TokenSessionMarkerStore markers,
            ObjectMapper mapper,
            Clock clock) {

        RedisXxlJobSsoTicketService service() {
            return withClock(clock);
        }

        RedisXxlJobSsoTicketService withClock(Clock newClock) {
            return new RedisXxlJobSsoTicketService(
                    redis, mapper, properties, markers, newClock, new SecureRandom(new byte[]{1, 2, 3, 4}));
        }
    }
}
