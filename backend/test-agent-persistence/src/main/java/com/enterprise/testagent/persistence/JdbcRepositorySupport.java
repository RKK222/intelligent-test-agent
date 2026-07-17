package com.enterprise.testagent.persistence;

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
     * 从 ResultSet 读取可空 timestamp，字段为空时使用调用方提供的兼容默认值。
     */
    protected Instant instantOrDefault(ResultSet resultSet, String column, Instant defaultValue) throws SQLException {
        Instant value = instant(resultSet, column);
        return value == null ? defaultValue : value;
    }

    /**
     * PostgreSQL JDBC 不能稳定推断 Instant 参数类型，写入 timestamp 字段前统一显式转换。
     */
    protected Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
