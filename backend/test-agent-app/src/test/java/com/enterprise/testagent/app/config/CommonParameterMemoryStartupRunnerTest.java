package com.enterprise.testagent.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.enterprise.testagent.configuration.management.CommonParameterMemoryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.OrderUtils;

/** 验证 JVM 内存通用参数只能在数据库迁移完成后严格加载。 */
class CommonParameterMemoryStartupRunnerTest {

    @Test
    void loadsMemoryParametersAfterFlywayAndBeforeDefaultApplicationRunners() throws Exception {
        CommonParameterMemoryRegistry registry = mock(CommonParameterMemoryRegistry.class);
        CommonParameterMemoryStartupRunner runner = new CommonParameterMemoryStartupRunner(registry);

        runner.run(new DefaultApplicationArguments());

        verify(registry).loadOnStartup();
        assertThat(OrderUtils.getOrder(CommonParameterMemoryStartupRunner.class))
                .isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1)
                .isGreaterThan(OrderUtils.getOrder(DatabaseMigrationRunner.class))
                .isLessThan(0);
        assertThat(SmartInitializingSingleton.class.isAssignableFrom(CommonParameterMemoryRegistry.class))
                .isFalse();
    }
}
