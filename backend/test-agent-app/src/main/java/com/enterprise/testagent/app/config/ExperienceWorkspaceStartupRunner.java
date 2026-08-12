package com.enterprise.testagent.app.config;

import com.enterprise.testagent.workspace.ExperienceWorkspaceApplicationService;
import java.util.Objects;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Flyway 和通用参数内存加载完成后，为当前后端节点初始化唯一的本地体验仓库。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ExperienceWorkspaceStartupRunner implements ApplicationRunner {

    private final ExperienceWorkspaceApplicationService experienceWorkspaceService;

    public ExperienceWorkspaceStartupRunner(ExperienceWorkspaceApplicationService experienceWorkspaceService) {
        this.experienceWorkspaceService = Objects.requireNonNull(
                experienceWorkspaceService, "experienceWorkspaceService must not be null");
    }

    /** 初始化失败直接阻止节点进入可用状态，避免用户进入后才发现 Git 缺失。 */
    @Override
    public void run(ApplicationArguments args) {
        experienceWorkspaceService.initializeLocalRepository();
    }
}
