package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.appsource.AppSourceOperation;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStep;
import com.enterprise.testagent.domain.appsource.AppSourceReplica;
import com.enterprise.testagent.domain.appsource.AppSourceReplicaStatus;
import com.enterprise.testagent.domain.appsource.AppSourceRepository;
import com.enterprise.testagent.domain.appsource.AppSourceRepositorySlot;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AppSourceReplicaRetryRegistrarTest {

    private static final Instant NOW = Instant.parse("2026-07-28T08:00:00Z");
    private static final CodeRepositoryId REPOSITORY_ID = new CodeRepositoryId("repo_retry");
    private static final LinuxServerId SERVER_ID = new LinuxServerId("server-a");

    @Test
    void secondOperationCannotRegisterWhileSameGenerationServerRetryIsStillPending() {
        AppSourceRepository repository = mock(AppSourceRepository.class);
        AtomicReference<AppSourceOperation> latest = new AtomicReference<>();
        List<AppSourceOperationStep> steps = new ArrayList<>();
        when(repository.lockRepositoryForAppSource(REPOSITORY_ID)).thenReturn(true);
        when(repository.findSlotForUpdate(REPOSITORY_ID)).thenReturn(Optional.of(new AppSourceRepositorySlot(
                REPOSITORY_ID, 3L, null, 4L, "op-download", 2L, NOW.minusSeconds(60), NOW)));
        when(repository.findOperation(any())).thenReturn(Optional.empty());
        when(repository.findLatestOperation(REPOSITORY_ID)).thenAnswer(invocation -> Optional.ofNullable(latest.get()));
        when(repository.findInFlightOperationForReplica(REPOSITORY_ID, 3L, SERVER_ID))
                .thenAnswer(invocation -> Optional.ofNullable(latest.get()));
        when(repository.findSteps(any())).thenAnswer(invocation -> steps.stream()
                .filter(step -> step.operationId().equals(invocation.getArgument(0)))
                .toList());
        when(repository.findReplica(REPOSITORY_ID, 3L, SERVER_ID)).thenReturn(Optional.of(new AppSourceReplica(
                REPOSITORY_ID, 3L, SERVER_ID, null, AppSourceReplicaStatus.FAILED,
                null, null, 1, NOW, "GIT_UNAVAILABLE", "failed", NOW.minusSeconds(60), NOW)));
        doAnswer(invocation -> {
            latest.set(invocation.getArgument(0));
            return null;
        }).when(repository).saveOperation(any());
        doAnswer(invocation -> steps.add(invocation.getArgument(0))).when(repository).upsertStep(any());
        AppSourceReplicaRetryRegistrar registrar = new AppSourceReplicaRetryRegistrar(repository);

        AppSourceOperation first = registrar.register(request("op-retry-1", "hash-1"));

        assertThatThrownBy(() -> registrar.register(request("op-retry-2", "hash-2")))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        assertThat(latest.get()).isEqualTo(first);
        assertThat(steps).extracting(AppSourceOperationStep::operationId).containsOnly(first.operationId());
    }

    private AppSourceReplicaRetryRegistrar.RetryRequest request(String operationId, String requestHash) {
        return new AppSourceReplicaRetryRegistrar.RetryRequest(
                operationId, new ApplicationId("app-1"), REPOSITORY_ID, 3L, new UserId("user-1"),
                requestHash, Set.of(SERVER_ID), "trace-retry", NOW);
    }
}
