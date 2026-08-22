package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.opencodeprocess.OpencodeObservabilityGenerationRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.domain.support.DomainValidation;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 使用独立 MyBatis SQL 保存不可被健康检查覆盖的 OpenCode Observability 代次。 */
@Repository
public class MyBatisOpencodeObservabilityGenerationRepository
        implements OpencodeObservabilityGenerationRepository {

    private final OpencodeObservabilityGenerationMapper mapper;

    public MyBatisOpencodeObservabilityGenerationRepository(OpencodeObservabilityGenerationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(OpencodeProcessId processId, String generation) {
        String normalized = DomainValidation.requireText(generation, "generation");
        if (mapper.updateGeneration(processId.value(), normalized) != 1) {
            throw new IllegalStateException("OpenCode Observability 进程代次写入目标不存在");
        }
    }

    @Override
    public Optional<String> findByProcessId(OpencodeProcessId processId) {
        return Optional.ofNullable(mapper.findGeneration(processId.value()))
                .map(String::trim)
                .filter(value -> !value.isBlank());
    }
}
