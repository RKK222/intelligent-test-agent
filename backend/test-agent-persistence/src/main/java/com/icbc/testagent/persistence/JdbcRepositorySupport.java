package com.icbc.testagent.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * JDBC Repository 的共享读取工具，集中处理 timestamp 到 Instant 的转换。
 */
abstract class JdbcRepositorySupport {

    /**
     * 从 ResultSet 读取可空 timestamp 并转换为领域层使用的 Instant。
     */
    protected Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    /**
     * PostgreSQL JDBC 不能稳定推断 Instant 参数类型，写入 timestamp 字段前统一显式转换。
     */
    protected Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
