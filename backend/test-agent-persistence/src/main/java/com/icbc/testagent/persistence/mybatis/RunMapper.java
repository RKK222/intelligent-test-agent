package com.icbc.testagent.persistence.mybatis;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Run MyBatis mapper；SQL 必须维护在 XML 中，接口只声明入参与返回值。
 */
@Mapper
public interface RunMapper {

    int insert(@Param("row") RunRow row);

    int update(@Param("row") RunRow row);

    int updateIfStatus(
            @Param("row") RunRow row,
            @Param("expectedStatus") String expectedStatus);

    RunRow findById(@Param("runId") String runId);

    RunRow findLatestActiveBySessionId(@Param("sessionId") String sessionId);

    List<RunRow> findStaleActiveRuns(
            @Param("updatedBefore") Instant updatedBefore,
            @Param("limit") int limit);

    List<RunRow> findStaleActiveSideQuestionRuns(
            @Param("updatedBefore") Instant updatedBefore,
            @Param("limit") int limit);
}
