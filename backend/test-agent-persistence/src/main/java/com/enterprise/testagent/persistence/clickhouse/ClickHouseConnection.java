package com.enterprise.testagent.persistence.clickhouse;

import javax.sql.DataSource;

/** 包装 ClickHouse 连接，避免第二个通用 DataSource bean 干扰 PostgreSQL 自动配置。 */
public record ClickHouseConnection(DataSource dataSource) {
}
