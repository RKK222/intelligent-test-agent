package com.enterprise.testagent.opencode.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.agent.runtime.AgentRuntimeCommand;
import com.enterprise.testagent.agent.runtime.AgentRuntimeRegistry;
import com.enterprise.testagent.agent.runtime.AgentRuntimeResult;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessConfigLinkService;
import com.fasterxml.jackson.databind.node.BooleanNode;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class PersonalAgentConfigRuntimeReloadServiceTest {

    private static final UserId USER_ID = new UserId("usr-1");
    private static final OpencodeProcessId PROCESS_ID = new OpencodeProcessId("ocp_1234567890abcdef");
    private static final Instant NOW = Instant.parse("2026-07-19T00:00:00Z");

    private final OpencodeProcessManagementRepository repository = mock(OpencodeProcessManagementRepository.class);
    private final BackendInstanceIdentity backendIdentity = mock(BackendInstanceIdentity.class);
    private final OpencodeProcessConfigLinkService configLinkService = mock(OpencodeProcessConfigLinkService.class);
    private final AgentRuntime runtime = mock(AgentRuntime.class);
    private final AgentRuntimeRegistry registry = mock(AgentRuntimeRegistry.class);
    private PersonalAgentConfigRuntimeReloadService service;

    @BeforeEach
    void setUp() {
        when(backendIdentity.linuxServerId()).thenReturn("linux-1");
        when(registry.require(AgentRuntimeRegistry.DEFAULT_AGENT_ID)).thenReturn(runtime);
        service = new PersonalAgentConfigRuntimeReloadService(repository, backendIdentity, configLinkService, registry);
    }

    @Test
    void switchesCurrentUserLinkToPublicWorktreeBeforeDisposingOnlyThatRuntime() {
        OpencodeServerProcess process = process("linux-1", "/session/usr-1/.testagent-runtime/current-public-config");
        when(repository.findUserBinding(USER_ID, "opencode")).thenReturn(Optional.of(binding()));
        when(repository.findOpencodeServerProcessById(PROCESS_ID)).thenReturn(Optional.of(process));
        when(configLinkService.isManagedConfigPath(process.sessionPath(), process.configPath())).thenReturn(true);
        when(runtime.runtime(any(AgentRuntimeCommand.class)))
                .thenReturn(Mono.just(new AgentRuntimeResult(BooleanNode.TRUE)));

        var result = service.reloadPublicPreview(USER_ID, "linux-1", "/worktrees/usr-1/opencode", "trace-1");

        assertThat(result.reloaded()).isTrue();
        verify(configLinkService).switchTo("/worktrees/usr-1/opencode", process.configPath());
        var command = org.mockito.ArgumentCaptor.forClass(AgentRuntimeCommand.class);
        verify(runtime).runtime(command.capture());
        assertThat(command.getValue().method()).isEqualTo("POST");
        assertThat(command.getValue().path()).isEqualTo("/global/dispose");
        assertThat(command.getValue().node().baseUrl()).isEqualTo(process.baseUrl());
    }

    @Test
    void reportsUninitializedProcessWithoutCreatingRuntimeFiles() {
        when(repository.findUserBinding(USER_ID, "opencode")).thenReturn(Optional.empty());

        var result = service.reloadPublicPreview(USER_ID, "linux-1", "/worktrees/usr-1/opencode", "trace-1");

        assertThat(result.reloaded()).isFalse();
        verify(configLinkService, never()).switchTo(any(), any());
        verify(runtime, never()).runtime(any());
    }

    @Test
    void rejectsLegacySharedPathInsteadOfMutatingAllUsers() {
        OpencodeServerProcess process = process("linux-1", "/shared/opencode");
        when(repository.findUserBinding(USER_ID, "opencode")).thenReturn(Optional.of(binding()));
        when(repository.findOpencodeServerProcessById(PROCESS_ID)).thenReturn(Optional.of(process));
        when(configLinkService.isManagedConfigPath(process.sessionPath(), process.configPath())).thenReturn(false);

        assertThatThrownBy(() -> service.reloadPublicPreview(
                        USER_ID, "linux-1", "/worktrees/usr-1/opencode", "trace-1"))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("受管方式重启");
        verify(configLinkService, never()).switchTo(any(), any());
        verify(runtime, never()).runtime(any());
    }

    private OpencodeServerProcess process(String linuxServerId, String configPath) {
        return new OpencodeServerProcess(
                PROCESS_ID,
                USER_ID,
                new LinuxServerId(linuxServerId),
                new OpencodeContainerId("container-1"),
                4096,
                123L,
                "http://127.0.0.1:4096",
                OpencodeServerProcessStatus.RUNNING,
                "/session/usr-1",
                configPath,
                NOW,
                NOW,
                "healthy",
                NOW,
                NOW,
                "trace-process");
    }

    private UserOpencodeProcessBinding binding() {
        return new UserOpencodeProcessBinding(
                USER_ID,
                "opencode",
                PROCESS_ID,
                new LinuxServerId("linux-1"),
                4096,
                UserOpencodeProcessBindingStatus.ACTIVE,
                NOW,
                NOW,
                "trace-binding");
    }
}
