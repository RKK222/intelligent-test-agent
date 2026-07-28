package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.appsource.AppSourceOperationType;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStep;
import com.enterprise.testagent.domain.appsource.AppSourcePathType;
import com.enterprise.testagent.domain.appsource.AppSourcePurpose;
import com.enterprise.testagent.domain.appsource.AppSourceRepository;
import com.enterprise.testagent.domain.appsource.AppSourceRepositorySlot;
import com.enterprise.testagent.domain.appsource.AppSourceSelectedPath;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AppSourceMaterializationRegistrarTest {

    private static final Instant NOW = Instant.parse("2026-07-28T03:00:00Z");

    @Test
    void insertsAllCleanupTasksBeforeAnyOtherPersistentWrite() {
        AppSourceRepository repository = mock(AppSourceRepository.class);
        CodeRepositoryId repositoryId = new CodeRepositoryId("repo_source");
        when(repository.lockRepositoryForAppSource(repositoryId)).thenReturn(true);
        when(repository.findSlotForUpdate(repositoryId)).thenReturn(Optional.of(new AppSourceRepositorySlot(
                repositoryId, 2L, null, 3L, "op_old", 4L, NOW.minusSeconds(60), NOW.minusSeconds(30))));
        when(repository.updateSlotIfVersion(any(), any(Long.class))).thenReturn(true);
        when(repository.insertReplicaIfAbsent(any())).thenReturn(true);
        when(repository.upsertStep(any())).thenReturn(true);
        List<String> writes = new ArrayList<>();
        doAnswer(invocation -> {
            writes.add("cleanup:" + ((List<?>) invocation.getArgument(0)).size());
            return null;
        }).when(repository).insertCleanupTasks(any());
        doAnswer(invocation -> {
            writes.add("snapshot");
            return null;
        }).when(repository).saveSnapshot(any());
        doAnswer(invocation -> {
            writes.add("operation");
            return null;
        }).when(repository).saveOperation(any());
        doAnswer(invocation -> {
            writes.add("replica");
            return true;
        }).when(repository).insertReplicaIfAbsent(any());
        doAnswer(invocation -> {
            writes.add("step");
            return true;
        }).when(repository).upsertStep(any());
        doAnswer(invocation -> {
            writes.add("slot");
            return true;
        }).when(repository).updateSlotIfVersion(any(), any(Long.class));

        AppSourceMaterializationRegistrar registrar = new AppSourceMaterializationRegistrar(repository);
        AppSourceMaterializationRegistrar.RegistrationResult result = registrar.register(request(
                new LinkedHashSet<>(List.of(new LinuxServerId("server-a"), new LinuxServerId("server-b")))));

        assertThat(result.operation().targetGeneration()).isEqualTo(3L);
        assertThat(writes).startsWith("cleanup:2");
        assertThat(writes).containsSubsequence("snapshot", "operation", "replica", "step", "slot");
    }

    @Test
    void registersCompleteDeterministicTimelineForEveryTargetServer() {
        AppSourceRepository repository = mock(AppSourceRepository.class);
        CodeRepositoryId repositoryId = new CodeRepositoryId("repo_source");
        when(repository.lockRepositoryForAppSource(repositoryId)).thenReturn(true);
        when(repository.findSlotForUpdate(repositoryId)).thenReturn(Optional.of(new AppSourceRepositorySlot(
                repositoryId, 2L, null, 3L, "op_old", 4L, NOW.minusSeconds(60), NOW.minusSeconds(30))));
        when(repository.updateSlotIfVersion(any(), any(Long.class))).thenReturn(true);
        when(repository.insertReplicaIfAbsent(any())).thenReturn(true);
        List<AppSourceOperationStep> steps = new ArrayList<>();
        doAnswer(invocation -> steps.add(invocation.getArgument(0))).when(repository).upsertStep(any());
        AppSourceMaterializationRegistrar registrar = new AppSourceMaterializationRegistrar(repository);

        registrar.register(request(new LinkedHashSet<>(List.of(
                new LinuxServerId("server-a"), new LinuxServerId("server-b")))));

        List<String> expectedCodes = List.of(
                "QUEUED", "LEASE_CLAIM", "LOCAL_LOCK", "STAGING", "SHALLOW_CLONE",
                "FETCH_FIXED_COMMIT", "SPARSE_CHECKOUT", "VALIDATE", "REMOVE_GIT_METADATA",
                "WRITE_INDEX", "ATOMIC_REPLACE", "REGISTER_WORKSPACE", "COMPLETE");
        for (LinuxServerId serverId : List.of(new LinuxServerId("server-a"), new LinuxServerId("server-b"))) {
            List<AppSourceOperationStep> serverSteps = steps.stream()
                    .filter(step -> step.linuxServerId().equals(serverId))
                    .toList();
            assertThat(serverSteps).extracting(AppSourceOperationStep::stepCode)
                    .containsExactlyElementsOf(expectedCodes);
            assertThat(serverSteps).extracting(AppSourceOperationStep::sequence)
                    .containsExactlyElementsOf(java.util.stream.IntStream.range(0, expectedCodes.size()).boxed().toList());
            assertThat(serverSteps).extracting(AppSourceOperationStep::stepId).doesNotHaveDuplicates();
        }
    }

    private AppSourceMaterializationRegistrar.RegistrationRequest request(LinkedHashSet<LinuxServerId> targets) {
        return new AppSourceMaterializationRegistrar.RegistrationRequest(
                "aso_new",
                new ApplicationId("app_1"),
                new CodeRepositoryId("repo_source"),
                "billing-service",
                new UserId("usr_1"),
                AppSourceOperationType.UPDATE,
                "a".repeat(64),
                2L,
                "main",
                "b".repeat(40),
                List.of(new AppSourceSelectedPath("src", AppSourcePathType.DIRECTORY)),
                AppSourcePurpose.TEAM,
                NOW.plusSeconds(7200),
                targets,
                "trace_app_source",
                NOW);
    }
}
