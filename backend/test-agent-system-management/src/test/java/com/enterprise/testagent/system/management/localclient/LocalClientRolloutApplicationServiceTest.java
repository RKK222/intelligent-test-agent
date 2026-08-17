package com.enterprise.testagent.system.management.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.domain.localclient.LocalClientRolloutEntry;
import com.enterprise.testagent.domain.localclient.LocalClientRolloutRepository;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.user.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LocalClientRolloutApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-17T11:34:14Z");
    private static final UserId TARGET = new UserId("usr_target");
    private static final UserId ADMIN = new UserId("usr_admin");

    private final LocalClientRolloutRepository repository = org.mockito.Mockito.mock(LocalClientRolloutRepository.class);
    private final UserRepository users = org.mockito.Mockito.mock(UserRepository.class);
    private final LocalClientRolloutApplicationService service = new LocalClientRolloutApplicationService(
            repository, users, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void defaultIsFailClosedAndListOnlyUsesEnabledRows() {
        when(repository.isEnabled(TARGET)).thenReturn(false);
        when(repository.findEnabledPage(0, 50)).thenReturn(List.of());
        when(repository.countEnabled()).thenReturn(0L);

        assertThat(service.isDownloadAllowed(TARGET)).isFalse();
        assertThat(service.list(new PageRequest(1, 50)).items()).isEmpty();
    }

    @Test
    void superAdminCanEnableAnActivePlatformUser() {
        when(users.findByUserId(TARGET)).thenReturn(Optional.of(user(UserStatus.ACTIVE)));
        when(repository.findByUserId(TARGET))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new LocalClientRolloutEntry(TARGET, true, ADMIN, NOW, NOW)));

        LocalClientRolloutResponses.RolloutUserView view = service.enable(TARGET, ADMIN);

        ArgumentCaptor<LocalClientRolloutEntry> entry = ArgumentCaptor.forClass(LocalClientRolloutEntry.class);
        verify(repository).save(entry.capture());
        assertThat(entry.getValue().enabled()).isTrue();
        assertThat(entry.getValue().updatedByUserId()).isEqualTo(ADMIN);
        assertThat(view.userId()).isEqualTo(TARGET.value());
    }

    @Test
    void inactiveOrMissingUsersCannotBeEnabled() {
        when(users.findByUserId(TARGET)).thenReturn(Optional.of(user(UserStatus.INACTIVE)));

        assertThatThrownBy(() -> service.enable(TARGET, ADMIN))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        when(users.findByUserId(TARGET)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.enable(TARGET, ADMIN))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void disablingKeepsAnAuditUpdateAndRejectsUnknownEntries() {
        when(repository.disable(TARGET, ADMIN, NOW)).thenReturn(true);
        service.disable(TARGET, ADMIN);
        verify(repository).disable(TARGET, ADMIN, NOW);

        when(repository.disable(TARGET, ADMIN, NOW)).thenReturn(false);
        assertThatThrownBy(() -> service.disable(TARGET, ADMIN))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    private static User user(UserStatus status) {
        return new User(
                TARGET, "AUTH_TARGET", "target", "bcrypt", "org", "rd", "dept",
                status, NOW, NOW);
    }
}
