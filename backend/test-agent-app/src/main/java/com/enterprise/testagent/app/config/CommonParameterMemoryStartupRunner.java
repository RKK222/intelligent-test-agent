package com.enterprise.testagent.app.config;

import com.enterprise.testagent.configuration.management.CommonParameterMemoryRegistry;
import java.util.Objects;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 数据库迁移完成后严格加载本 Java 进程的显式内存通用参数。
 *
 * <p>Spring Boot Flyway initializer 在 ApplicationRunner 之前完成迁移；这里保持最高优先级，
 * 确保内存参数早于 scheduler 等默认业务 Runner 加载。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CommonParameterMemoryStartupRunner implements ApplicationRunner {

    private final CommonParameterMemoryRegistry registry;

    public CommonParameterMemoryStartupRunner(CommonParameterMemoryRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    /** 迁移完成后加载；缺失或非法值继续抛错并阻止应用启动。 */
    @Override
    public void run(ApplicationArguments args) {
        registry.loadOnStartup();
    }
}
