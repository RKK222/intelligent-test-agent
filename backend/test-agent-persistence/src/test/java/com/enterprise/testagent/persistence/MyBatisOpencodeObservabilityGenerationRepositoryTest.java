package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.persistence.mybatis.MyBatisOpencodeObservabilityGenerationRepository;
import com.enterprise.testagent.persistence.mybatis.OpencodeObservabilityGenerationMapper;
import org.junit.jupiter.api.Test;

class MyBatisOpencodeObservabilityGenerationRepositoryTest {

    private static final OpencodeProcessId PROCESS_ID = new OpencodeProcessId("ocp_observability_generation");

    @Test
    void savesAndReadsGenerationThroughMapper() {
        OpencodeObservabilityGenerationMapper mapper = mock(OpencodeObservabilityGenerationMapper.class);
        when(mapper.updateGeneration(PROCESS_ID.value(), "generation-1")).thenReturn(1);
        when(mapper.findGeneration(PROCESS_ID.value())).thenReturn("generation-1");
        var repository = new MyBatisOpencodeObservabilityGenerationRepository(mapper);

        repository.save(PROCESS_ID, "generation-1");

        assertThat(repository.findByProcessId(PROCESS_ID)).contains("generation-1");
        verify(mapper).updateGeneration(PROCESS_ID.value(), "generation-1");
    }

    @Test
    void rejectsMissingProcessInsteadOfPretendingGenerationWasPersisted() {
        OpencodeObservabilityGenerationMapper mapper = mock(OpencodeObservabilityGenerationMapper.class);
        when(mapper.updateGeneration(PROCESS_ID.value(), "generation-1")).thenReturn(0);
        var repository = new MyBatisOpencodeObservabilityGenerationRepository(mapper);

        assertThatThrownBy(() -> repository.save(PROCESS_ID, "generation-1"))
                .isInstanceOf(IllegalStateException.class);
    }
}
