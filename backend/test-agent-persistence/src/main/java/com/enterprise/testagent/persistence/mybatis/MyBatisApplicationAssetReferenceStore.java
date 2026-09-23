package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.reference.ApplicationAssetReference;
import com.enterprise.testagent.domain.reference.ApplicationAssetReferenceStore;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 共享资产配置与旧工作区扫描结果的持久化适配器。 */
@Repository
public class MyBatisApplicationAssetReferenceStore implements ApplicationAssetReferenceStore {
    private final ApplicationAssetReferenceMapper mapper;

    public MyBatisApplicationAssetReferenceStore(ApplicationAssetReferenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ApplicationAssetReference> list(ApplicationId appId) {
        return mapper.list(appId.value()).stream().map(row -> new ApplicationAssetReference(
                new ApplicationId(row.appId()), new CodeRepositoryId(row.repositoryId()),
                row.directoryPath(), row.alias(), row.mergeEnabled(), row.sddFolderName(),
                row.description(), row.version(), row.updatedAt())).toList();
    }

    @Override
    public int insert(ApplicationAssetReference reference) {
        return mapper.insert(row(reference));
    }

    @Override
    public int update(ApplicationAssetReference reference, long expectedVersion) {
        return mapper.update(row(reference), expectedVersion);
    }

    @Override
    public int delete(ApplicationId appId, CodeRepositoryId repositoryId, String directoryPath, long expectedVersion) {
        return mapper.delete(appId.value(), repositoryId.value(), directoryPath, expectedVersion);
    }

    @Override
    public List<ImportSource> claimLocalSources(String linuxServerId, Instant now, Instant leaseUntil, int limit) {
        return mapper.claimLocalSources(linuxServerId, now, leaseUntil, limit).stream()
                .map(row -> new ImportSource(row.workspaceId(), new ApplicationId(row.appId()), row.linuxServerId()))
                .toList();
    }

    @Override
    @Transactional
    public void finishSource(ImportSource source, List<ImportCandidate> candidates, Instant now) {
        mapper.deleteCandidates(source.workspaceId());
        for (ImportCandidate candidate : candidates) {
            mapper.insertCandidate(new ApplicationAssetReferenceMapper.ImportCandidateRow(
                    candidate.workspaceId(), candidate.appId().value(), candidate.repositoryId().value(),
                    candidate.directoryPath(), candidate.alias(), candidate.merge(),
                    candidate.sddFolderName(), candidate.description()));
        }
        mapper.finishSource(source.workspaceId(), now);
    }

    @Override
    public void retrySource(ImportSource source, String safeError, Instant retryAt) {
        mapper.retrySource(source.workspaceId(), safeError, retryAt);
    }

    @Override
    public List<ApplicationId> readyImportApplications() {
        return mapper.readyImportApplications().stream().map(ApplicationId::new).toList();
    }

    @Override
    public boolean claimImportApplication(ApplicationId appId, Instant now) {
        return mapper.claimImportApplication(appId.value(), now) == 1;
    }

    @Override
    public List<ImportCandidate> candidates(ApplicationId appId) {
        return mapper.candidates(appId.value()).stream().map(row -> new ImportCandidate(
                row.workspaceId(), new ApplicationId(row.appId()), new CodeRepositoryId(row.repositoryId()),
                row.directoryPath(), row.alias(), row.mergeEnabled(), row.sddFolderName(),
                row.description())).toList();
    }

    @Override
    public void recordConflict(ApplicationId appId, String alias, String reason, Instant now) {
        mapper.recordConflict(appId.value(), alias, reason, now);
    }

    @Override
    public void clearConflict(ApplicationId appId, String alias) {
        mapper.clearConflict(appId.value(), alias);
    }

    @Override
    public List<ImportConflict> conflicts(ApplicationId appId) {
        return mapper.conflicts(appId.value()).stream()
                .map(row -> new ImportConflict(row.alias(), row.reason())).toList();
    }

    @Override
    public boolean importPending(ApplicationId appId) {
        return mapper.countPendingImport(appId.value()) > 0;
    }

    private ApplicationAssetReferenceMapper.ConfigurationRow row(ApplicationAssetReference reference) {
        return new ApplicationAssetReferenceMapper.ConfigurationRow(
                reference.appId().value(), reference.repositoryId().value(), reference.directoryPath(),
                reference.alias(), reference.merge(), reference.sddFolderName(),
                reference.description(), reference.version(), reference.updatedAt());
    }
}
