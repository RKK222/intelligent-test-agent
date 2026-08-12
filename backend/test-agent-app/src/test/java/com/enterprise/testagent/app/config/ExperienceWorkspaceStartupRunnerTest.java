package com.enterprise.testagent.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.enterprise.testagent.workspace.ExperienceWorkspaceApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.OrderUtils;

class ExperienceWorkspaceStartupRunnerTest {

    @Test
    void initializesExperienceRepositoryAfterHighestPrecedenceParameterRunner() throws Exception {
        ExperienceWorkspaceApplicationService service = mock(ExperienceWorkspaceApplicationService.class);
        ExperienceWorkspaceStartupRunner runner = new ExperienceWorkspaceStartupRunner(service);

        runner.run(new DefaultApplicationArguments());

        verify(service).initializeLocalRepository();
        assertThat(OrderUtils.getOrder(ExperienceWorkspaceStartupRunner.class))
                .isEqualTo(Ordered.HIGHEST_PRECEDENCE + 10);
    }
}
