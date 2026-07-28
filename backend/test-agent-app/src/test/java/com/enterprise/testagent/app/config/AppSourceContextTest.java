package com.enterprise.testagent.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.enterprise.testagent.common.git.SshKeyEncryptionService;
import com.enterprise.testagent.domain.appsource.AppSourceRepository;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.workspace.AppSourceApplicationService;
import com.enterprise.testagent.workspace.AppSourceCleanupResultRecorder;
import com.enterprise.testagent.workspace.AppSourceCleanupTaskHandler;
import com.enterprise.testagent.workspace.AppSourceCleanupWorker;
import com.enterprise.testagent.workspace.AppSourceGitAccessResolver;
import com.enterprise.testagent.workspace.AppSourceGitMaterializer;
import com.enterprise.testagent.workspace.AppSourceIndexManager;
import com.enterprise.testagent.workspace.AppSourceMaterializationRegistrar;
import com.enterprise.testagent.workspace.AppSourceReplicaResultRecorder;
import com.enterprise.testagent.workspace.AppSourceReplicaProgressRecorder;
import com.enterprise.testagent.workspace.AppSourceReplicaRetryRegistrar;
import com.enterprise.testagent.workspace.AppSourceReplicaTaskDispatcher;
import com.enterprise.testagent.workspace.AppSourceReplicaWorker;
import com.enterprise.testagent.workspace.AppSourceWorkspaceOpener;
import com.enterprise.testagent.workspace.DefaultAppSourceReplicaTaskDispatcher;
import com.enterprise.testagent.workspace.WorkspaceServerIdentity;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AppSourceContextTest {

    @Test
    void appSourceProductionConstructorsFormSingleRunnableBeanGraph() {
        new ApplicationContextRunner()
                .withBean(AppSourceRepository.class, () -> mock(AppSourceRepository.class))
                .withBean(ConfigurationManagementRepository.class, () -> mock(ConfigurationManagementRepository.class))
                .withBean(UserRepository.class, () -> mock(UserRepository.class))
                .withBean(OpencodeProcessManagementRepository.class, () -> mock(OpencodeProcessManagementRepository.class))
                .withBean(OpencodeProcessHeartbeatStore.class, () -> mock(OpencodeProcessHeartbeatStore.class))
                .withBean(WorkspaceRepository.class, () -> mock(WorkspaceRepository.class))
                .withBean(SshKeyEncryptionService.class, () -> mock(SshKeyEncryptionService.class))
                .withBean(ServerBroadcastPublisher.class, () -> mock(ServerBroadcastPublisher.class))
                .withBean(CommonParameterValues.class, () -> mock(CommonParameterValues.class))
                .withBean(ManagedWorkspacePathResolver.class, () -> new ManagedWorkspacePathResolver(
                        mock(CommonParameterValues.class)))
                .withBean(WorkspaceServerIdentity.class, () -> new WorkspaceServerIdentity("server-a"))
                .withBean(Clock.class, Clock::systemUTC)
                .withBean(AppSourceMaterializationRegistrar.class)
                .withBean(AppSourceReplicaRetryRegistrar.class)
                .withBean(AppSourceGitAccessResolver.class)
                .withBean(AppSourceGitMaterializer.class)
                .withBean(AppSourceReplicaProgressRecorder.class)
                .withBean(AppSourceReplicaResultRecorder.class)
                .withBean(AppSourceIndexManager.class)
                .withBean(AppSourceWorkspaceOpener.class)
                .withBean(AppSourceReplicaWorker.class)
                .withBean(DefaultAppSourceReplicaTaskDispatcher.class)
                .withBean(AppSourceCleanupResultRecorder.class)
                .withBean(AppSourceCleanupWorker.class)
                .withBean(AppSourceCleanupTaskHandler.class)
                .withBean(AppSourceApplicationService.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AppSourceApplicationService.class);
                    assertThat(context).hasSingleBean(AppSourceReplicaTaskDispatcher.class);
                    assertThat(context).hasSingleBean(AppSourceCleanupTaskHandler.class);
                });
    }
}
