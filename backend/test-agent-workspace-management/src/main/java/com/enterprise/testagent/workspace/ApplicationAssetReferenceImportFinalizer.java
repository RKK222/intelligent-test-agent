package com.enterprise.testagent.workspace;

import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.reference.ApplicationAssetReference;
import com.enterprise.testagent.domain.reference.ApplicationAssetReferenceStore;
import com.enterprise.testagent.domain.reference.ApplicationAssetReferenceStore.ImportCandidate;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 所有升级前来源完成扫描后，以应用为单位原子发布无冲突候选。 */
@Service
public class ApplicationAssetReferenceImportFinalizer {
    private final ApplicationAssetReferenceStore store;
    private final Clock clock;

    public ApplicationAssetReferenceImportFinalizer(ApplicationAssetReferenceStore store, Clock clock) {
        this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public void finalizeApplication(ApplicationId appId) {
        if (!store.claimImportApplication(appId, clock.instant())) return;
        Set<String> conflictedAliases = store.conflicts(appId).stream()
                .map(ApplicationAssetReferenceStore.ImportConflict::alias)
                .collect(Collectors.toSet());
        Map<String, List<ImportCandidate>> byAlias = store.candidates(appId).stream()
                .collect(Collectors.groupingBy(ImportCandidate::alias, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<String, List<ImportCandidate>> group : byAlias.entrySet()) {
            if (conflictedAliases.contains(group.getKey())) continue;
            ImportCandidate first = group.getValue().get(0);
            boolean consistent = group.getValue().stream().allMatch(candidate ->
                    candidate.repositoryId().equals(first.repositoryId())
                            && candidate.directoryPath().equals(first.directoryPath())
                            && candidate.merge() == first.merge()
                            && candidate.sddFolderName().equals(first.sddFolderName())
                            && candidate.description().equals(first.description()));
            if (!consistent) {
                store.recordConflict(appId, group.getKey(), "多个管理员或版本的旧配置不一致，未自动发布", clock.instant());
                continue;
            }
            ApplicationAssetReference existing = store.list(appId).stream()
                    .filter(reference -> reference.alias().equals(first.alias())
                            || reference.repositoryId().equals(first.repositoryId())
                            && reference.directoryPath().equals(first.directoryPath()))
                    .findFirst().orElse(null);
            if (existing != null) {
                if (!existing.alias().equals(first.alias()) || !existing.directoryPath().equals(first.directoryPath())
                        || existing.merge() != first.merge()
                        || !existing.description().equals(first.description())) {
                    store.recordConflict(appId, group.getKey(), "共享配置已由管理员修改，未覆盖", clock.instant());
                }
                continue;
            }
            int inserted = store.insert(new ApplicationAssetReference(appId, first.repositoryId(),
                    first.directoryPath(), first.alias(), first.merge(), first.sddFolderName(),
                    first.description(), 1L, clock.instant()));
            if (inserted != 1) {
                store.recordConflict(appId, group.getKey(), "共享配置已存在，未覆盖", clock.instant());
            }
        }
    }
}
