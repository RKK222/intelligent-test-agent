package com.icbc.testagent.persistence.mybatis;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * RunEvent MyBatis mapper；新增 SQL 必须维护在 XML 中，接口只声明参数。
 */
@Mapper
public interface RunEventMapper {

    Long nextSeq(@Param("runId") String runId);

    void insert(@Param("row") RunEventRow row);

    RunEventRow findByRunIdAndSeq(
            @Param("runId") String runId,
            @Param("seq") long seq);

    RunEventRow findByRunIdSessionIdAndRawEventId(
            @Param("runId") String runId,
            @Param("sessionId") String sessionId,
            @Param("rawEventId") String rawEventId);

    List<RunEventRow> findByRunIdAfter(
            @Param("runId") String runId,
            @Param("lastSeq") long lastSeq,
            @Param("limit") int limit);
}
