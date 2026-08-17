package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.enterprise.testagent.persistence.mybatis.CommonParameterMapper;
import com.enterprise.testagent.persistence.mybatis.MyBatisPersistenceConfig;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/** 验证启用 ClickHouse 的第二个 MyBatis 工厂后，普通 mapper 仍固定使用 PostgreSQL 工厂。 */
class MyBatisMultipleSessionFactoryConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MyBatisPersistenceConfig.class, SecondarySessionFactoryConfig.class)
            .withBean(DataSource.class, () -> new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .generateUniqueName(true)
                    .build());

    @Test
    void bindsPostgresqlMappersToPrimarySessionFactory() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            SqlSessionFactory primary = context.getBean("sqlSessionFactory", SqlSessionFactory.class);
            SqlSessionFactory secondary = context.getBean("clickHouseSqlSessionFactory", SqlSessionFactory.class);
            MapperFactoryBean<?> mapperFactory = context.getBean("&commonParameterMapper", MapperFactoryBean.class);

            assertThat(context.getBean(SqlSessionFactory.class)).isSameAs(primary);
            assertThat(mapperFactory.getSqlSessionFactory()).isSameAs(primary).isNotSameAs(secondary);
            assertThat(context.getBean(CommonParameterMapper.class)).isNotNull();
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class SecondarySessionFactoryConfig {

        @Bean(name = "clickHouseSqlSessionFactory")
        SqlSessionFactory clickHouseSqlSessionFactory() {
            return mock(SqlSessionFactory.class);
        }
    }
}
