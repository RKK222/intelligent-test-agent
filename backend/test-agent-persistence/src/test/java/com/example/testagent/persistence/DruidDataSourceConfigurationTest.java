package com.example.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 验证后端统一使用 Druid 管理 JDBC 连接池，并保持 Druid Web 控制台默认关闭。
 */
class DruidDataSourceConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DruidDataSourceAutoConfigure.class,
                    DataSourceAutoConfiguration.class))
            .withPropertyValues(
                    "spring.datasource.type=com.alibaba.druid.pool.DruidDataSource",
                    "spring.datasource.druid.url=jdbc:h2:mem:druid-config;MODE=PostgreSQL;DATABASE_TO_UPPER=false",
                    "spring.datasource.druid.username=sa",
                    "spring.datasource.druid.password=",
                    "spring.datasource.druid.driver-class-name=org.h2.Driver",
                    "spring.datasource.druid.validation-query=SELECT 1",
                    "spring.datasource.druid.test-while-idle=true",
                    "spring.datasource.druid.stat-view-servlet.enabled=false",
                    "spring.datasource.druid.web-stat-filter.enabled=false");

    @Test
    void datasourceUsesDruidPoolWithWebConsoleDisabled() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DataSource.class);
            assertThat(context.getBean(DataSource.class)).isInstanceOf(DruidDataSource.class);
            assertThat((DruidDataSource) context.getBean(DataSource.class))
                    .extracting(DruidDataSource::getUrl)
                    .isEqualTo("jdbc:h2:mem:druid-config;MODE=PostgreSQL;DATABASE_TO_UPPER=false");
            assertThat((DruidDataSource) context.getBean(DataSource.class))
                    .extracting(DruidDataSource::getValidationQuery)
                    .isEqualTo("SELECT 1");
            assertThat(context.getEnvironment()
                    .getProperty("spring.datasource.druid.stat-view-servlet.enabled", Boolean.class))
                    .isFalse();
            assertThat(context.getEnvironment()
                    .getProperty("spring.datasource.druid.web-stat-filter.enabled", Boolean.class))
                    .isFalse();
        });
    }
}
