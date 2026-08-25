package com.enterprise.testagent.persistence.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** sessions 运行目标 MyBatis Mapper。 */
@Mapper
public interface SessionRuntimeTargetMapper {

    SessionRuntimeTargetRow findBySessionId(@Param("sessionId") String sessionId);

    int updateTarget(
            @Param("sessionId") String sessionId,
            @Param("runtimeKind") String runtimeKind,
            @Param("localClientInstanceId") String localClientInstanceId);

    int rebindLocalClientTargets(
            @Param("workspaceId") String workspaceId,
            @Param("expectedClientInstanceId") String expectedClientInstanceId,
            @Param("replacementClientInstanceId") String replacementClientInstanceId);
}
