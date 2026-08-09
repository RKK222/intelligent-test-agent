package com.enterprise.testagent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.memory.MemoryScope;
import com.enterprise.testagent.domain.memory.MemoryStatus;
import com.enterprise.testagent.domain.memory.QaMemory;
import com.enterprise.testagent.domain.memory.QaMemoryRepository;
import com.enterprise.testagent.domain.memory.QaTaskType;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class QaMemoryApplicationServiceTest {
    private static final UserId USER = new UserId("usr_memory");
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");

    private QaMemoryRepository repository;
    private ConfigurationManagementRepository configuration;
    private MemoryDocumentStore documents;
    private QaMemoryApplicationService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(QaMemoryRepository.class);
        configuration = Mockito.mock(ConfigurationManagementRepository.class);
        documents = Mockito.mock(MemoryDocumentStore.class);
        QaMemoryProperties properties = new QaMemoryProperties();
        properties.setEnabled(true);
        when(repository.isWhitelisted(USER.value())).thenReturn(true);
        when(configuration.isActiveMember(any(ApplicationId.class), any(UserId.class))).thenReturn(true);
        when(documents.add(any())).thenReturn(new MemoryDocumentStore.StoredDocument(
                "mem0_1", "覆盖异常与边界", Map.of(), NOW));
        service = new QaMemoryApplicationService(
                repository, configuration, documents, new MemorySafetyPolicy(), properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void manualPersonalMemoryActivatesImmediatelyWithoutPersistingFullDocumentInGovernance() {
        var view = service.createPersonal(
                USER, MemoryScope.PERSONAL_GLOBAL, null,
                "覆盖异常与边界", List.of(QaTaskType.TEST_CASE_GENERATION));

        assertThat(view.status()).isEqualTo(MemoryStatus.ACTIVE);
        assertThat(view.content()).isEqualTo("覆盖异常与边界");
        ArgumentCaptor<QaMemory> memory = ArgumentCaptor.forClass(QaMemory.class);
        verify(repository).insertMemory(memory.capture());
        assertThat(memory.getValue().mem0MemoryId()).isEqualTo("mem0_1");
        assertThat(memory.getValue().displaySummary()).isEqualTo("覆盖异常与边界");
        assertThat(memory.getValue().ownerUserId()).isEqualTo(USER.value());
    }

    @Test
    void ordinaryTeamContributionStaysCandidateAndCreatesPendingReview() {
        var view = service.createTeamCandidate(
                USER, "app_memory", "分析结论必须附证据", List.of(QaTaskType.DEFECT_ANALYSIS));

        assertThat(view.scope()).isEqualTo(MemoryScope.TEAM_APPLICATION);
        assertThat(view.status()).isEqualTo(MemoryStatus.CANDIDATE);
        verify(repository).insertReview(any());
    }
}
