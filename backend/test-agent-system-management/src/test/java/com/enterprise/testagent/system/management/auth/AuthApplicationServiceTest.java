package com.enterprise.testagent.system.management.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.auth.AamLoginTokenVerifier;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.auth.TokenStore;
import com.enterprise.testagent.domain.dictionary.DictionaryRepository;
import com.enterprise.testagent.domain.dictionary.UserRoleRepository;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserLoginLogRepository;
import com.enterprise.testagent.domain.user.UserStatus;
import com.enterprise.testagent.system.management.user.UserDomainService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class AuthApplicationServiceTest {

    private static final String USER_ID = "AUTH_001";
    private static final String AAM_TOKEN = "secret-aam-token";

    @Test
    void verifiesAamBeforeLookingUpUserAndIssuesIndependentPlatformToken() {
        Fixture fixture = new Fixture(activeUser());

        AuthPrincipal principal = fixture.service.loginByUnifiedAuthId(USER_ID, AAM_TOKEN, "127.0.0.1", "test-agent");

        InOrder order = inOrder(fixture.verifier, fixture.userDomainService, fixture.tokenStore, fixture.loginLogs);
        order.verify(fixture.verifier).verify(USER_ID, AAM_TOKEN);
        order.verify(fixture.userDomainService).findOrCreateByUnifiedAuthId(USER_ID);
        order.verify(fixture.tokenStore).save(any(AuthPrincipal.class));
        order.verify(fixture.loginLogs).save(any());
        assertThat(principal.token()).isNotBlank().isNotEqualTo(AAM_TOKEN);
        assertThat(principal.expiresAt()).isAfterOrEqualTo(principal.issuedAt().plusSeconds(86_399));
    }

    @Test
    void rejectedAamTokenHasNoUserRedisOrSuccessLogSideEffects() {
        Fixture fixture = new Fixture(activeUser());
        PlatformException rejected = new PlatformException(ErrorCode.UNAUTHENTICATED);
        org.mockito.Mockito.doThrow(rejected).when(fixture.verifier).verify(USER_ID, AAM_TOKEN);

        assertThatThrownBy(() -> fixture.service.loginByUnifiedAuthId(USER_ID, AAM_TOKEN, "127.0.0.1", "agent"))
                .isSameAs(rejected);

        verify(fixture.userDomainService, never()).findOrCreateByUnifiedAuthId(any());
        verify(fixture.tokenStore, never()).save(any());
        verify(fixture.loginLogs, never()).save(any());
    }

    @Test
    void disabledUserIsRejectedAfterVerificationWithoutCreatingSession() {
        User disabled = new User(
                new UserId("usr_disabled"), USER_ID, "disabled", "hash", null, null, null,
                UserStatus.INACTIVE, Instant.now(), Instant.now());
        Fixture fixture = new Fixture(disabled);

        assertThatThrownBy(() -> fixture.service.loginByUnifiedAuthId(USER_ID, AAM_TOKEN, null, null))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(fixture.verifier).verify(USER_ID, AAM_TOKEN);
        verify(fixture.tokenStore, never()).save(any());
        verify(fixture.loginLogs, never()).save(any());
    }

    @Test
    void redisSaveFailureNeverReturnsOrRecordsSuccessfulLogin() {
        Fixture fixture = new Fixture(activeUser());
        RuntimeException redisFailure = new RuntimeException("redis unavailable");
        org.mockito.Mockito.doThrow(redisFailure).when(fixture.tokenStore).save(any());

        assertThatThrownBy(() -> fixture.service.loginByUnifiedAuthId(USER_ID, AAM_TOKEN, null, null))
                .isSameAs(redisFailure);

        verify(fixture.loginLogs, never()).save(any());
    }

    @Test
    void successfulFirstLoginUsesUserReturnedByExistingCreateFlow() {
        Fixture fixture = new Fixture(activeUser());
        ArgumentCaptor<AuthPrincipal> principal = ArgumentCaptor.forClass(AuthPrincipal.class);

        fixture.service.loginByUnifiedAuthId(USER_ID, AAM_TOKEN, null, null);

        verify(fixture.userDomainService).findOrCreateByUnifiedAuthId(USER_ID);
        verify(fixture.tokenStore).save(principal.capture());
        assertThat(principal.getValue().userId()).isEqualTo(new UserId("usr_001"));
    }

    private static User activeUser() {
        return User.createNew("usr_001", USER_ID, "张三", "hash", null, null, null);
    }

    private static final class Fixture {
        private final AamLoginTokenVerifier verifier = mock(AamLoginTokenVerifier.class);
        private final UserDomainService userDomainService = mock(UserDomainService.class);
        private final TokenStore tokenStore = mock(TokenStore.class);
        private final UserLoginLogRepository loginLogs = mock(UserLoginLogRepository.class);
        private final UserRoleRepository userRoles = mock(UserRoleRepository.class);
        private final DictionaryRepository dictionaries = mock(DictionaryRepository.class);
        private final AuthApplicationService service;

        private Fixture(User user) {
            when(userDomainService.findOrCreateByUnifiedAuthId(USER_ID)).thenReturn(user);
            when(userRoles.findByUserId(user.userId())).thenReturn(List.of());
            service = new AuthApplicationService(
                    userDomainService, tokenStore, loginLogs, userRoles, dictionaries, verifier);
        }
    }
}
