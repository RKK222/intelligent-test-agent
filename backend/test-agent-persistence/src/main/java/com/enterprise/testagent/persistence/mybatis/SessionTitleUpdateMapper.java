package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Session 标题条件更新 mapper；SQL 保持在 XML 中以支持并发 compare-and-set。
 */
@Mapper
public interface SessionTitleUpdateMapper {

    int updateTitleIfCurrent(
            @Param("sessionId") String sessionId,
            @Param("expectedTitle") String expectedTitle,
            @Param("title") String title,
            @Param("updatedAt") Instant updatedAt,
            @Param("traceId") String traceId);
}
