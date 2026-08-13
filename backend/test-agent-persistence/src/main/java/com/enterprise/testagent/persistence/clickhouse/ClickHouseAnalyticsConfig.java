package com.enterprise.testagent.persistence.clickhouse;

import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** 独立 ClickHouse 数据源，避免被 PostgreSQL 事务管理器和 mapper 扫描误用。 */
@Configuration
@ConditionalOnProperty(name = "test-agent.analytics.clickhouse.enabled", havingValue = "true")
@MapperScan(
        basePackages = "com.enterprise.testagent.persistence.clickhouse",
        sqlSessionFactoryRef = "clickHouseSqlSessionFactory")
public class ClickHouseAnalyticsConfig {

    @Bean
    public ClickHouseConnection clickHouseConnection(
            @Value("${test-agent.analytics.clickhouse.url}") String url,
            @Value("${test-agent.analytics.clickhouse.username:default}") String username,
            @Value("${test-agent.analytics.clickhouse.password:}") String password) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return new ClickHouseConnection(dataSource);
    }

    @Bean(name = "clickHouseSqlSessionFactory")
    public SqlSessionFactory clickHouseSqlSessionFactory(
            ClickHouseConnection connection) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(connection.dataSource());
        factory.setTypeHandlers(new ClickHouseInstantTypeHandler());
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis-clickhouse/**/*.xml"));
        SqlSessionFactory result = factory.getObject();
        result.getConfiguration().setMapUnderscoreToCamelCase(true);
        return result;
    }

    @Bean
    public ClickHouseSchemaMigrator clickHouseSchemaMigrator(ClickHouseConnection connection) {
        return new ClickHouseSchemaMigrator(connection.dataSource());
    }
}
