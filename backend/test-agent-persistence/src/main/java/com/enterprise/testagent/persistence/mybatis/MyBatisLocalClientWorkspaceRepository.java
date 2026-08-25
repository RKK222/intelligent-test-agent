package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientWorkspaceBinding;
import com.enterprise.testagent.domain.localclient.LocalClientWorkspaceRepository;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 本地工作区绑定 MyBatis 仓储实现。 */
@Repository
public class MyBatisLocalClientWorkspaceRepository implements LocalClientWorkspaceRepository {

    private final LocalClientMapper mapper;

    public MyBatisLocalClientWorkspaceRepository(LocalClientMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<LocalClientWorkspaceBinding> findByWorkspaceId(WorkspaceId workspaceId) {
        return Optional.ofNullable(mapper.findWorkspaceById(workspaceId.value())).map(this::toDomain);
    }

    @Override
    public Optional<LocalClientWorkspaceBinding> findByOwnerClientAndRootDigest(
            UserId userId,
            LocalClientInstanceId clientInstanceId,
            String rootDigest) {
        return Optional.ofNullable(mapper.findWorkspaceByOwnerClientAndRootDigest(
                        userId.value(), clientInstanceId.value(), rootDigest))
                .map(this::toDomain);
    }

    @Override
    public List<LocalClientWorkspaceBinding> findByClientInstanceId(LocalClientInstanceId clientInstanceId) {
        return mapper.findWorkspacesByClientInstanceId(clientInstanceId.value()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<LocalClientWorkspaceBinding> findByOwnerRootIdentity(
            UserId userId,
            String rootDigest,
            String fileSystemIdentity) {
        return mapper.findWorkspacesByOwnerRootIdentity(
                        userId.value(), rootDigest, fileSystemIdentity).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void lockRegistration(UserId userId, LocalClientInstanceId clientInstanceId) {
        if (mapper.lockWorkspaceRegistration(userId.value(), clientInstanceId.value()) == null) {
            throw new IllegalStateException("local client instance disappeared before workspace registration");
        }
    }

    @Override
    public void save(LocalClientWorkspaceBinding binding) {
        int updated = mapper.upsertWorkspace(new LocalClientWorkspaceRow(
                binding.workspaceId().value(),
                binding.userId().value(),
                binding.clientInstanceId().value(),
                binding.normalizedRootPath(),
                binding.rootDigest(),
                binding.fileSystemIdentity(),
                binding.createdAt(),
                binding.updatedAt()));
        if (updated != 1) {
            throw new IllegalStateException("local client workspace owner changed during upsert");
        }
    }

    @Override
    public boolean rebind(
            LocalClientWorkspaceBinding binding,
            LocalClientInstanceId expectedClientInstanceId) {
        return mapper.rebindWorkspace(
                        binding.workspaceId().value(),
                        binding.userId().value(),
                        expectedClientInstanceId.value(),
                        binding.clientInstanceId().value(),
                        binding.normalizedRootPath(),
                        binding.rootDigest(),
                        binding.fileSystemIdentity(),
                        binding.updatedAt()) == 1;
    }

    @Override
    public boolean deleteByWorkspaceId(WorkspaceId workspaceId) {
        return mapper.deleteWorkspaceById(workspaceId.value()) > 0;
    }

    private LocalClientWorkspaceBinding toDomain(LocalClientWorkspaceRow row) {
        return new LocalClientWorkspaceBinding(
                new WorkspaceId(row.workspaceId()),
                new UserId(row.userId()),
                new LocalClientInstanceId(row.clientInstanceId()),
                row.normalizedRootPath(),
                row.rootDigest(),
                row.fileSystemIdentity(),
                row.createdAt(),
                row.updatedAt());
    }
}
