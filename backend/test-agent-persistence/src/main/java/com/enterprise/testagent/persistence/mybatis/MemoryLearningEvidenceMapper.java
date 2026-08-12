package com.enterprise.testagent.persistence.mybatis;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** QA 记忆学习只读 mapper；SQL 只按 run_id 读取已有 USER/ASSISTANT 文本。 */
@Mapper
public interface MemoryLearningEvidenceMapper {
    List<MemoryLearningEvidenceRow> findByRunId(@Param("runId") String runId);
}
