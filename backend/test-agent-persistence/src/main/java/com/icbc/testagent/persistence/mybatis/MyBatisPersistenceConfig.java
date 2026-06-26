package com.icbc.testagent.persistence.mybatis;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 持久层配置，统一扫描 persistence 模块内部 mapper 接口。
 */
@Configuration
@MapperScan("com.icbc.testagent.persistence.mybatis")
public class MyBatisPersistenceConfig {
}
