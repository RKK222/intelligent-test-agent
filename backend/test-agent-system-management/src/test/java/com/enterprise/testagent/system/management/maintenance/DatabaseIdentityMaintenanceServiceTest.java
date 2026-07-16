package com.enterprise.testagent.system.management.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.maintenance.DatabaseIdentityMaintenancePort;
import com.enterprise.testagent.domain.maintenance.IdentityManagedTable;
import com.enterprise.testagent.domain.maintenance.IdentityStatus;
import com.enterprise.testagent.system.management.maintenance.DatabaseIdentityResponses.IdentityStatusDto;
import com.enterprise.testagent.system.management.maintenance.DatabaseIdentityResponses.RestartIdentityCommand;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatabaseIdentityMaintenanceServiceTest {

    @Test
    void listStatusesReturnsAllWhitelistedTables() {
        DatabaseIdentityMaintenancePort port = mock(DatabaseIdentityMaintenancePort.class);
        when(port.queryIdentityStatus(eq(IdentityManagedTable.USERS))).thenReturn(status("users", 8L, 8L, false));
        when(port.queryIdentityStatus(eq(IdentityManagedTable.USER_ROLES))).thenReturn(status("user_roles", 1000000L, 1000000L, false));
        when(port.queryIdentityStatus(eq(IdentityManagedTable.DICTIONARIES))).thenReturn(status("dictionaries", 16L, 16L, false));
        when(port.queryIdentityStatus(eq(IdentityManagedTable.USER_LOGIN_LOGS))).thenReturn(status("user_login_logs", 14L, 14L, false));

        DatabaseIdentityMaintenanceService service = new DatabaseIdentityMaintenanceService(port);
        List<IdentityStatusDto> statuses = service.listIdentityStatuses();

        assertThat(statuses).hasSize(4);
        assertThat(statuses).extracting(IdentityStatusDto::tableName)
                .containsExactly("users", "user_roles", "dictionaries", "user_login_logs");
        assertThat(statuses).noneMatch(IdentityStatusDto::conflict);
    }

    @Test
    void conflictFlagTrueWhenCurrentBehindMax() {
        DatabaseIdentityMaintenancePort port = mock(DatabaseIdentityMaintenancePort.class);
        // users 序列停在 2，但表里已有 id=8 → 错位；其余表正常
        when(port.queryIdentityStatus(eq(IdentityManagedTable.USERS))).thenReturn(status("users", 2L, 8L, true));
        when(port.queryIdentityStatus(eq(IdentityManagedTable.USER_ROLES))).thenReturn(status("user_roles", 1000000L, 1000000L, false));
        when(port.queryIdentityStatus(eq(IdentityManagedTable.DICTIONARIES))).thenReturn(status("dictionaries", 16L, 16L, false));
        when(port.queryIdentityStatus(eq(IdentityManagedTable.USER_LOGIN_LOGS))).thenReturn(status("user_login_logs", 14L, 14L, false));

        DatabaseIdentityMaintenanceService service = new DatabaseIdentityMaintenanceService(port);
        List<IdentityStatusDto> statuses = service.listIdentityStatuses();

        IdentityStatusDto users = statuses.stream()
                .filter(s -> "users".equals(s.tableName())).findFirst().orElseThrow();
        assertThat(users.conflict()).isTrue();
    }

    @Test
    void alignRestartsAtMaxPlusOne() {
        DatabaseIdentityMaintenancePort port = mock(DatabaseIdentityMaintenancePort.class);
        when(port.queryIdentityStatus(eq(IdentityManagedTable.USERS))).thenReturn(status("users", 2L, 8L, true));

        DatabaseIdentityMaintenanceService service = new DatabaseIdentityMaintenanceService(port);
        service.alignIdentity(IdentityManagedTable.USERS);

        verify(port).restartIdentity(eq(IdentityManagedTable.USERS), eq(9L));
    }

    @Test
    void alignOnEmptyTableRestartsAtOne() {
        DatabaseIdentityMaintenancePort port = mock(DatabaseIdentityMaintenancePort.class);
        when(port.queryIdentityStatus(eq(IdentityManagedTable.USER_LOGIN_LOGS)))
                .thenReturn(status("user_login_logs", null, null, false));

        DatabaseIdentityMaintenanceService service = new DatabaseIdentityMaintenanceService(port);
        service.alignIdentity(IdentityManagedTable.USER_LOGIN_LOGS);

        verify(port).restartIdentity(eq(IdentityManagedTable.USER_LOGIN_LOGS), eq(1L));
    }

    @Test
    void restartRejectsNonPositiveTarget() {
        DatabaseIdentityMaintenancePort port = mock(DatabaseIdentityMaintenancePort.class);
        DatabaseIdentityMaintenanceService service = new DatabaseIdentityMaintenanceService(port);

        assertThatThrownBy(() -> service.restartIdentity(
                new RestartIdentityCommand(IdentityManagedTable.USERS, 0L)))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> assertThat(((PlatformException) e).errorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void restartRejectsTargetNotGreaterThanMax() {
        DatabaseIdentityMaintenancePort port = mock(DatabaseIdentityMaintenancePort.class);
        when(port.queryIdentityStatus(eq(IdentityManagedTable.USERS))).thenReturn(status("users", 8L, 8L, false));
        DatabaseIdentityMaintenanceService service = new DatabaseIdentityMaintenanceService(port);

        assertThatThrownBy(() -> service.restartIdentity(
                new RestartIdentityCommand(IdentityManagedTable.USERS, 8L)))
                .isInstanceOf(PlatformException.class);
        verify(port, times(0)).restartIdentity(eq(IdentityManagedTable.USERS), eq(8L));
    }

    @Test
    void restartAcceptsTargetAboveMax() {
        DatabaseIdentityMaintenancePort port = mock(DatabaseIdentityMaintenancePort.class);
        when(port.queryIdentityStatus(eq(IdentityManagedTable.USERS))).thenReturn(status("users", 8L, 8L, false));
        DatabaseIdentityMaintenanceService service = new DatabaseIdentityMaintenanceService(port);

        service.restartIdentity(new RestartIdentityCommand(IdentityManagedTable.USERS, 1000000L));

        verify(port).restartIdentity(eq(IdentityManagedTable.USERS), eq(1000000L));
    }

    @Test
    void requireTableThrowsOnUnknownCode() {
        assertThatThrownBy(() -> DatabaseIdentityMaintenanceService.requireTable("unknown_table"))
                .isInstanceOf(PlatformException.class);
    }

    private IdentityStatus status(String tableName, Long current, Long max, boolean conflict) {
        return new IdentityStatus(tableName.toUpperCase(), tableName, current, max, conflict);
    }
}
