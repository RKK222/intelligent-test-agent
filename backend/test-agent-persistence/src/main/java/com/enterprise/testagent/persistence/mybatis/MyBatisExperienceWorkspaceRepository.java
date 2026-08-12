package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.workspace.ExperienceWorkspaceBinding;
import com.enterprise.testagent.domain.workspace.ExperienceWorkspaceRepository;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 体验 Workspace MyBatis 仓储；Workspace 行与当前服务器绑定在同一事务内幂等登记。
 */
@Repository
public class MyBatisExperienceWorkspaceRepository implements ExperienceWorkspaceRepository {

    private final ExperienceWorkspaceMapper mapper;

    public MyBatisExperienceWorkspaceRepository(ExperienceWorkspaceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<ExperienceWorkspaceBinding> findCurrentByLinuxServerId(String linuxServerId) {
        return Optional.ofNullable(mapper.findCurrentByLinuxServerId(linuxServerId)).map(this::toDomain);
    }

    @Override
    @Transactional
    public boolean registerCurrentIfUnchanged(
            Workspace workspace,
            String configuredParameterValue,
            String traceId,
            Optional<ExperienceWorkspaceBinding> expectedCurrent) {
        mapper.insertWorkspaceIfAbsent(new ExperienceWorkspaceRow(
                workspace.workspaceId().value(),
                workspace.name(),
                workspace.rootPath(),
                workspace.status().name(),
                workspace.linuxServerId(),
                workspace.traceId(),
                workspace.createdAt(),
                workspace.updatedAt()));
        ExperienceWorkspaceBindingRow next = new ExperienceWorkspaceBindingRow(
                workspace.linuxServerId(),
                workspace.workspaceId().value(),
                configuredParameterValue,
                traceId,
                workspace.createdAt(),
                workspace.updatedAt());
        return expectedCurrent
                .map(this::toRow)
                .map(expected -> mapper.updateCurrentBindingIfMatches(next, expected) == 1)
                .orElseGet(() -> mapper.insertCurrentBindingIfAbsent(next) == 1);
    }

    private ExperienceWorkspaceBindingRow toRow(ExperienceWorkspaceBinding binding) {
        return new ExperienceWorkspaceBindingRow(
                binding.linuxServerId(),
                binding.workspaceId().value(),
                binding.configuredParameterValue(),
                binding.traceId(),
                binding.createdAt(),
                binding.updatedAt());
    }

    private ExperienceWorkspaceBinding toDomain(ExperienceWorkspaceBindingRow row) {
        return new ExperienceWorkspaceBinding(
                row.linuxServerId(),
                new WorkspaceId(row.workspaceId()),
                row.configuredParameterValue(),
                row.traceId(),
                row.createdAt(),
                row.updatedAt());
    }
}
