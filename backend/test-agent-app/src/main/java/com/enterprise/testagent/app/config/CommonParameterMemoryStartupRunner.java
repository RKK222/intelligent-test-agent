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
 * <p>项目使用 {@link DatabaseMigrationRunner} 在 ApplicationRunner 阶段执行 Flyway，
 * 因此这里必须紧随迁移运行，并早于 scheduler 等默认业务 Runner。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
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
