package com.enterprise.testagent.persistence.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** OpenCode Observability 代次 MyBatis mapper；SQL 固定维护在 XML 中。 */
@Mapper
public interface OpencodeObservabilityGenerationMapper {

    int updateGeneration(@Param("processId") String processId, @Param("generation") String generation);

    String findGeneration(@Param("processId") String processId);
}
