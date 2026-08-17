package com.enterprise.testagent.persistence.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** runs 运行目标 MyBatis Mapper。 */
@Mapper
public interface RunRuntimeTargetMapper {

    RunRuntimeTargetRow findByRunId(@Param("runId") String runId);

    int updateTarget(
            @Param("runId") String runId,
            @Param("runtimeKind") String runtimeKind,
            @Param("localClientInstanceId") String localClientInstanceId);
}
