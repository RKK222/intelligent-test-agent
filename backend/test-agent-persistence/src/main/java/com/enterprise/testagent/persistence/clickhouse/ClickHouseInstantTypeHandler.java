package com.enterprise.testagent.persistence.clickhouse;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * ClickHouse 时间类型必须走驱动的 Instant 原生路径，避免 Timestamp 使用 JVM 本地时区二次偏移。
 */
public class ClickHouseInstantTypeHandler extends BaseTypeHandler<Instant> {

    @Override
    public void setNonNullParameter(
            PreparedStatement statement,
            int index,
            Instant parameter,
            JdbcType jdbcType) throws SQLException {
        statement.setObject(index, parameter);
    }

    @Override
    public Instant getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return resultSet.getObject(columnName, Instant.class);
    }

    @Override
    public Instant getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getObject(columnIndex, Instant.class);
    }

    @Override
    public Instant getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return statement.getObject(columnIndex, Instant.class);
    }
}
