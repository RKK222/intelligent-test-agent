package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** scheduler 主表 MyBatis mapper；所有关系型 SQL 统一维护在 XML。 */
@Mapper
public interface ScheduledTaskMapper {

    int updateTask(Map<String, Object> params);

    int insertTask(Map<String, Object> params);

    Map<String, Object> findTaskByKey(@Param("taskKey") String taskKey);

    List<Map<String, Object>> findTasks(@Param("limit") int limit, @Param("offset") long offset);

    long countTasks();

    List<Map<String, Object>> findDueTasks(@Param("now") Instant now, @Param("limit") int limit);

    int updatePlan(Map<String, Object> params);

    int insertPlan(Map<String, Object> params);

    Map<String, Object> findPlanById(@Param("planId") String planId);

    int updateRun(Map<String, Object> params);

    int updateRunIfStatus(Map<String, Object> params);

    int insertRun(Map<String, Object> params);

    Map<String, Object> findRunById(@Param("taskRunId") String taskRunId);

    Map<String, Object> findActiveRunByTaskKey(
            @Param("taskKey") String taskKey,
            @Param("excludedTaskRunId") String excludedTaskRunId);

    List<Map<String, Object>> findPendingRuns(
            @Param("triggerType") String triggerType,
            @Param("executionAffinity") String executionAffinity,
            @Param("now") Instant now,
            @Param("limit") int limit);

    List<Map<String, Object>> findRuns(
            @Param("taskKey") String taskKey,
            @Param("status") String status,
            @Param("triggerType") String triggerType,
            @Param("requestedByUserId") String requestedByUserId,
            @Param("limit") int limit,
            @Param("offset") long offset);

    long countRuns(
            @Param("taskKey") String taskKey,
            @Param("status") String status,
            @Param("triggerType") String triggerType,
            @Param("requestedByUserId") String requestedByUserId);
}
