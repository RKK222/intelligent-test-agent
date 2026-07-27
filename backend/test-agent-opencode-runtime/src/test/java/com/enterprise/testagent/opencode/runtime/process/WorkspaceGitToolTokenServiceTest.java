package com.enterprise.testagent.opencode.runtime.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.dictionary.DictId;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.dictionary.DictionaryRepository;
import com.enterprise.testagent.domain.dictionary.UserRole;
import com.enterprise.testagent.domain.dictionary.UserRoleRepository;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WorkspaceGitToolTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-27T00:00:00Z");
    private static final UserId USER_ID = new UserId("usr_1234567890abcdef");

    @Test
    void issuedTokenRestoresCurrentUserRoles() {
        Fixture fixture = fixture(Clock.fixed(NOW, ZoneOffset.UTC));
        String token = fixture.service().issue(USER_ID);

        var principal = fixture.service().authenticate("Bearer " + token);

        assertThat(principal.userId()).isEqualTo(USER_ID);
        assertThat(principal.roles()).containsExactly(Dictionary.ROLE_APP_ADMIN);
    }

    @Test
    void tamperedTokenIsRejected() {
        Fixture fixture = fixture(Clock.fixed(NOW, ZoneOffset.UTC));
        String token = fixture.service().issue(USER_ID);

        assertThatThrownBy(() -> fixture.service().authenticate("Bearer " + token + "changed"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    void expiredTokenIsRejected() {
        Fixture issuer = fixture(Clock.fixed(NOW, ZoneOffset.UTC));
        String token = issuer.service().issue(USER_ID);
        Fixture verifier = fixture(Clock.fixed(NOW.plus(Duration.ofDays(8)), ZoneOffset.UTC));

        assertThatThrownBy(() -> verifier.service().authenticate("Bearer " + token))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    private Fixture fixture(Clock clock) {
        UserRepository userRepository = mock(UserRepository.class);
        UserRoleRepository roleRepository = mock(UserRoleRepository.class);
        DictionaryRepository dictionaryRepository = mock(DictionaryRepository.class);
        User user = mock(User.class);
        DictId dictId = new DictId("dict_role_app_admin");
        Dictionary dictionary = mock(Dictionary.class);
        when(user.userId()).thenReturn(USER_ID);
        when(user.canLogin()).thenReturn(true);
        when(userRepository.findByUserId(USER_ID)).thenReturn(Optional.of(user));
        when(roleRepository.findByUserId(USER_ID))
                .thenReturn(List.of(new UserRole(USER_ID, dictId, NOW)));
        when(dictionary.dictKey()).thenReturn(Dictionary.DICT_KEY_ROLE);
        when(dictionary.dictValue()).thenReturn(Dictionary.ROLE_APP_ADMIN);
        when(dictionaryRepository.findByDictId(dictId)).thenReturn(Optional.of(dictionary));
        ManagerControlSettings settings = new ManagerControlSettings(
                "manager-secret",
                "http://127.0.0.1:8080",
                new LinuxServerId("linux-1"),
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                Duration.ofSeconds(10),
                100);
        return new Fixture(new WorkspaceGitToolTokenService(
                settings, userRepository, roleRepository, dictionaryRepository, clock));
    }

    private record Fixture(WorkspaceGitToolTokenService service) {
    }
}
