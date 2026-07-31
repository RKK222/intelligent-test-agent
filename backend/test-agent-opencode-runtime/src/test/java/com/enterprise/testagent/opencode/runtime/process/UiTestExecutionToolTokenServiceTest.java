package com.enterprise.testagent.opencode.runtime.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UiTestExecutionToolTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");
    private static final UserId USER_ID = new UserId("usr_ui_test_123456");

    @Test
    void issuedTokenRestoresActiveUser() {
        Fixture fixture = fixture(Clock.fixed(NOW, ZoneOffset.UTC));

        String token = fixture.service().issue(USER_ID);

        assertThat(fixture.service().authenticate("Bearer " + token)).isEqualTo(USER_ID);
    }

    @Test
    void tamperedAndExpiredTokensAreRejected() {
        Fixture issuer = fixture(Clock.fixed(NOW, ZoneOffset.UTC));
        String token = issuer.service().issue(USER_ID);

        assertUnauthenticated(() -> issuer.service().authenticate("Bearer " + token + "changed"));
        Fixture expired = fixture(Clock.fixed(NOW.plus(Duration.ofDays(8)), ZoneOffset.UTC));
        assertUnauthenticated(() -> expired.service().authenticate("Bearer " + token));
    }

    private void assertUnauthenticated(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    private Fixture fixture(Clock clock) {
        UserRepository userRepository = mock(UserRepository.class);
        User user = mock(User.class);
        when(user.userId()).thenReturn(USER_ID);
        when(user.canLogin()).thenReturn(true);
        when(userRepository.findByUserId(USER_ID)).thenReturn(Optional.of(user));
        ManagerControlSettings settings = new ManagerControlSettings(
                "manager-secret",
                "http://127.0.0.1:8080",
                new LinuxServerId("linux-1"),
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                Duration.ofSeconds(10),
                100);
        return new Fixture(new UiTestExecutionToolTokenService(settings, userRepository, clock));
    }

    private record Fixture(UiTestExecutionToolTokenService service) {
    }
}
