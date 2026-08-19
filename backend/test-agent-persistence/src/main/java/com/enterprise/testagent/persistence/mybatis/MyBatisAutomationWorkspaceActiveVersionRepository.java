package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.configuration.ApplicationWorkspaceId;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionId;
import com.enterprise.testagent.domain.managedworkspace.AutomationWorkspaceActiveVersion;
import com.enterprise.testagent.domain.managedworkspace.AutomationWorkspaceActiveVersionRepository;
import com.enterprise.testagent.domain.user.UserId;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 自动化代码库当前版本仓储的 MyBatis XML 实现。 */
@Repository
public class MyBatisAutomationWorkspaceActiveVersionRepository
        implements AutomationWorkspaceActiveVersionRepository {

    private final AutomationWorkspaceActiveVersionMapper mapper;

    public MyBatisAutomationWorkspaceActiveVersionRepository(AutomationWorkspaceActiveVersionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<AutomationWorkspaceActiveVersion> find(ApplicationWorkspaceId applicationWorkspaceId) {
        return Optional.ofNullable(mapper.find(applicationWorkspaceId.value())).map(this::toDomain);
    }

    @Override
    public List<AutomationWorkspaceActiveVersion> findByApplicationWorkspaceIds(
            List<ApplicationWorkspaceId> applicationWorkspaceIds) {
        if (applicationWorkspaceIds == null || applicationWorkspaceIds.isEmpty()) {
            return List.of();
        }
        return mapper.findByApplicationWorkspaceIds(
                        applicationWorkspaceIds.stream().map(ApplicationWorkspaceId::value).distinct().toList())
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public AutomationWorkspaceActiveVersion activate(AutomationWorkspaceActiveVersion activeVersion) {
        Optional<AutomationWorkspaceActiveVersion> current = find(activeVersion.applicationWorkspaceId());
        if (current.filter(value -> value.versionId().equals(activeVersion.versionId())).isPresent()) {
            return current.orElseThrow();
        }
        if (current.isPresent()) {
            mapper.update(toRow(activeVersion));
        } else {
            try {
                if (mapper.insertIfAbsent(toRow(activeVersion)) != 1) {
                    mapper.update(toRow(activeVersion));
                }
            } catch (DuplicateKeyException concurrentActivation) {
                mapper.update(toRow(activeVersion));
            }
        }
        return find(activeVersion.applicationWorkspaceId()).orElseThrow();
    }

    @Override
    @Transactional
    public AutomationWorkspaceActiveVersion initializeIfAbsent(AutomationWorkspaceActiveVersion activeVersion) {
        try {
            mapper.insertIfAbsent(toRow(activeVersion));
        } catch (DuplicateKeyException concurrentInitialization) {
            // 并发首次创建由唯一键决定胜者；返回数据库当前激活版本。
        }
        return find(activeVersion.applicationWorkspaceId()).orElseThrow();
    }

    private AutomationWorkspaceActiveVersionRow toRow(AutomationWorkspaceActiveVersion value) {
        return new AutomationWorkspaceActiveVersionRow(
                value.applicationWorkspaceId().value(),
                value.versionId().value(),
                value.activatedBy() == null ? null : value.activatedBy().value(),
                value.activatedAt(),
                value.createdAt(),
                value.updatedAt());
    }

    private AutomationWorkspaceActiveVersion toDomain(AutomationWorkspaceActiveVersionRow row) {
        return new AutomationWorkspaceActiveVersion(
                new ApplicationWorkspaceId(row.applicationWorkspaceId()),
                new ApplicationWorkspaceVersionId(row.versionId()),
                row.activatedByUserId() == null ? null : new UserId(row.activatedByUserId()),
                row.activatedAt(),
                row.createdAt(),
                row.updatedAt());
    }
}
