package com.example.testagent.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * JDBC Repository 的共享读取工具，集中处理 timestamp 到 Instant 的转换。
 */
abstract class JdbcRepositorySupport {

    protected Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
