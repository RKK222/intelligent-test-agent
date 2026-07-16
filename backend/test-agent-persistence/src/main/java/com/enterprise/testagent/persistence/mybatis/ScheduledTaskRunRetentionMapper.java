package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 定时任务运行记录保留策略 MyBatis mapper；删除 SQL 统一维护在 XML 中。
 */
@Mapper
public interface ScheduledTaskRunRetentionMapper {

    /**
     * 删除截止时间以前的已结束运行记录，活动状态由 XML 条件显式排除。
     */
    int deleteEndedBefore(@Param("cutoff") Instant cutoff);
}
