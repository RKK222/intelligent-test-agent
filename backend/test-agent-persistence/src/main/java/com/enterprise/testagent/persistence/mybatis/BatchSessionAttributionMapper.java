package com.enterprise.testagent.persistence.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 批量会话归因 MyBatis mapper；所有 SQL 均位于同名 XML。 */
@Mapper
public interface BatchSessionAttributionMapper {

    Integer lockCreateRequest(@Param("lockKey") String lockKey);

    String findSessionId(
            @Param("userId") String userId,
            @Param("itemRequestId") String itemRequestId);

    int markBatch(
            @Param("sessionId") String sessionId,
            @Param("userId") String userId,
            @Param("batchId") String batchId,
            @Param("itemRequestId") String itemRequestId);
}
