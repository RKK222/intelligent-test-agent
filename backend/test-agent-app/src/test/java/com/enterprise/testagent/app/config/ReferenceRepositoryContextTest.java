package com.enterprise.testagent.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.enterprise.testagent.api.web.platform.ReferenceRepositoryController;
import com.enterprise.testagent.common.git.SshKeyEncryptionService;
import com.enterprise.testagent.configuration.management.ConfigurationManagementApplicationService;
import com.enterprise.testagent.configuration.management.GitCloneCacheService;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.dictionary.DictionaryRepository;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryRepository;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.persistence.mybatis.MyBatisReferenceRepositoryRepository;
import com.enterprise.testagent.persistence.mybatis.ReferenceRepositoryMapper;
import com.enterprise.testagent.workspace.ReferenceRepositoryApplicationService;
import com.enterprise.testagent.workspace.ReferenceRepositoryReplicaReconciler;
import com.enterprise.testagent.workspace.WorkspaceServerIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ReferenceRepositoryContextTest {

    @Test
    void productionConstructorWiresControllerServiceRepositoryAndReconcilerWithoutGitBean() {
        new ApplicationContextRunner()
                .withPropertyValues("test-agent.reference-repository.replica-reconciler.enabled=false")
                .withBean(ConfigurationManagementRepository.class, () -> mock(ConfigurationManagementRepository.class))
                .withBean(DictionaryRepository.class, () -> mock(DictionaryRepository.class))
                .withBean(UserRepository.class, () -> mock(UserRepository.class))
                .withBean(GitCloneCacheService.class, () -> mock(GitCloneCacheService.class))
                .withBean(ManagedWorkspaceRepository.class, () -> mock(ManagedWorkspaceRepository.class))
                .withBean(OpencodeProcessHeartbeatStore.class, () -> mock(OpencodeProcessHeartbeatStore.class))
                .withBean(CommonParameterValues.class, () -> mock(CommonParameterValues.class))
                .withBean(SshKeyEncryptionService.class, () -> mock(SshKeyEncryptionService.class))
                .withBean(WorkspaceServerIdentity.class, () -> new WorkspaceServerIdentity("linux-test"))
                .withBean(ServerBroadcastPublisher.class, () -> mock(ServerBroadcastPublisher.class))
                .withBean(ReferenceRepositoryMapper.class, () -> mock(ReferenceRepositoryMapper.class))
                .withBean(MyBatisReferenceRepositoryRepository.class)
                .withBean(ConfigurationManagementApplicationService.class)
                .withBean(ReferenceRepositoryApplicationService.class)
                .withBean(ReferenceRepositoryReplicaReconciler.class)
                .withBean(ReferenceRepositoryController.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ReferenceRepositoryController.class);
                    assertThat(context).hasSingleBean(ConfigurationManagementApplicationService.class);
                    assertThat(context).hasSingleBean(ReferenceRepositoryApplicationService.class);
                    assertThat(context).hasSingleBean(ReferenceRepositoryRepository.class);
                    assertThat(context).hasSingleBean(ReferenceRepositoryReplicaReconciler.class);
                });
    }
}
