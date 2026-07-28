package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 工具点击 MyBatis mapper；所有关系型 SQL 均维护在同名 XML 中。 */
@Mapper
public interface ToolboxClickMapper {

    int insertEvent(ToolboxClickEventRow row);

    int claimCountingWindow(
            @Param("toolId") String toolId,
            @Param("userId") String userId,
            @Param("clickedAt") Instant clickedAt,
            @Param("windowStart") Instant windowStart);

    int incrementTotal(
            @Param("toolId") String toolId,
            @Param("countedAt") Instant countedAt);

    int markEventCounted(@Param("eventId") String eventId);

    ToolboxClickTotalRow findTotal(@Param("toolId") String toolId);

    List<ToolboxClickTotalRow> findTotals(@Param("toolIds") List<String> toolIds);

    ToolboxClickEventRow findEvent(@Param("eventId") String eventId);

    long countEvents();

    long countStates();
}
