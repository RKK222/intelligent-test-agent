package com.icbc.testagent.persistence.mybatis;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户历史会话 MyBatis mapper；SQL 维护在 XML 中，避免新增 JDBC SQL。
 */
@Mapper
public interface SessionHistoryMapper {

    List<SessionHistoryRow> findUserHistory(
            @Param("userId") String userId,
            @Param("queryPattern") String queryPattern,
            @Param("limit") int limit,
            @Param("offset") long offset);

    long countUserHistory(
            @Param("userId") String userId,
            @Param("queryPattern") String queryPattern);
}
