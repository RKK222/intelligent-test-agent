package com.enterprise.testagent.persistence.mybatis;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Run session scope MyBatis mapper；SQL 只能维护在 XML 中。
 */
@Mapper
public interface RunSessionScopeMapper {

    void upsertScope(@Param("row") RunSessionScopeRow row);

    void upsertSession(@Param("row") RunSessionScopeSessionRow row);

    List<RunSessionScopeSessionRow> findSessionsByRunId(@Param("runId") String runId);

    List<RunSessionScopeSessionRow> findSessionsByRootSessionId(@Param("rootSessionId") String rootSessionId);

    RunSessionScopeSessionRow findSession(
            @Param("runId") String runId,
            @Param("sessionId") String sessionId);
}
