package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.reference.ApplicationAssetReference;
import com.enterprise.testagent.domain.reference.ApplicationAssetReferenceStore;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理应用级资产引用；普通成员只读取已配置目录，不参与资产库 Git 操作。 */
@Service
public class ApplicationAssetReferenceService {
    private final ConfigurationManagementRepository configurationRepository;
    private final ApplicationAssetReferenceStore store;
    private final ReferenceRepositoryApplicationService referenceService;
    private final Clock clock;

    public ApplicationAssetReferenceService(ConfigurationManagementRepository configurationRepository,
                                            ApplicationAssetReferenceStore store,
                                            ReferenceRepositoryApplicationService referenceService,
                                            Clock clock) {
        this.configurationRepository = Objects.requireNonNull(configurationRepository);
        this.store = Objects.requireNonNull(store);
        this.referenceService = Objects.requireNonNull(referenceService);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 应用成员只能看到安全的逻辑配置，管理员额外看到一次性迁移状态。 */
    public Listing list(String appId, UserId userId, boolean superAdmin, boolean admin) {
        ApplicationId applicationId = requireAccess(appId, userId, superAdmin);
        Map<CodeRepositoryId, CodeRepository> linkedAssets = configurationRepository
                .findRepositoriesByApplication(applicationId).stream()
                .filter(repository -> CodeRepositoryType.APPLICATION_ASSET_REPOSITORY.value()
                        .equals(repository.repositoryType()))
                .collect(Collectors.toMap(CodeRepository::repositoryId, Function.identity(), (first, ignored) -> first));
        List<Configuration> configurations = store.list(applicationId).stream()
                .filter(reference -> linkedAssets.containsKey(reference.repositoryId()))
                .map(reference -> view(reference, linkedAssets.get(reference.repositoryId())))
                .toList();
        return new Listing(configurations,
                admin && store.importPending(applicationId),
                admin ? store.conflicts(applicationId) : List.of());
    }

    /** 只允许管理员写入后端验证过的 READY 目录，禁止客户端提交路径或别名。 */
    @Transactional
    public Configuration save(String appId, String repositoryId, String directoryPath,
                              boolean merge, String description, long expectedVersion) {
        ApplicationId applicationId = requireEnabledApplication(appId);
        CodeRepository repository = requireLinkedAsset(applicationId, repositoryId);
        if (expectedVersion < 0) throw invalid("配置版本无效");
        String path = requireRelativePath(directoryPath);
        String parent = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";
        ReferenceRepositoryResponses.TreeNode selected = referenceService.tree(
                appId, repositoryId, parent).stream()
                .filter(node -> node.path().equals(path) && node.directory() && node.selectable())
                .findFirst().orElseThrow(() -> invalid("只能选择已就绪资产库中允许配置的目录"));
        String folder = selected.name();
        String alias = folder + "-" + repository.englishName();
        if (alias.length() > 128 || !alias.matches("[^/\\\\\\s`,]+")) {
            throw invalid("资产目录生成的引用别名无效");
        }
        String normalizedDescription = description == null ? "" : description.trim();
        if (normalizedDescription.isEmpty() || normalizedDescription.length() > 2000) {
            throw invalid("描述不能为空且不能超过 2000 字符");
        }
        CodeRepositoryId codeRepositoryId = repository.repositoryId();
        ApplicationAssetReference reference = new ApplicationAssetReference(
                applicationId, codeRepositoryId, path, alias, merge, folder,
                normalizedDescription, expectedVersion + 1, clock.instant());
        int changed = expectedVersion == 0 ? store.insert(reference) : store.update(reference, expectedVersion);
        if (changed != 1) throw conflict("资产引用已被其他管理员修改，请刷新后重试");
        // 来源仍待扫描时保留冲突提示；扫描全部完成后显式保存才视为人工裁决。
        if (!store.importPending(applicationId)) store.clearConflict(applicationId, alias);
        return view(reference, repository);
    }

    public void delete(String appId, String repositoryId, String directoryPath, long expectedVersion) {
        ApplicationId applicationId = requireEnabledApplication(appId);
        CodeRepository repository = requireLinkedAsset(applicationId, repositoryId);
        if (expectedVersion < 1 || store.delete(applicationId, repository.repositoryId(),
                requireRelativePath(directoryPath), expectedVersion) != 1) {
            throw conflict("资产引用已被其他管理员修改，请刷新后重试");
        }
    }

    public ApplicationId requireAccess(String appId, UserId userId, boolean superAdmin) {
        ApplicationId applicationId = requireEnabledApplication(appId);
        if (!superAdmin && !configurationRepository.isActiveMember(applicationId, userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "当前用户不是应用成员");
        }
        return applicationId;
    }

    ApplicationId requireEnabledApplication(String appId) {
        ApplicationId applicationId = new ApplicationId(appId);
        configurationRepository.findApplication(applicationId)
                .filter(application -> application.enabled())
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "应用不存在或未启用"));
        return applicationId;
    }

    CodeRepository requireLinkedAsset(ApplicationId appId, String repositoryId) {
        CodeRepository repository = linkedRepository(appId, new CodeRepositoryId(repositoryId));
        if (repository == null) throw invalid("资产库未关联当前应用");
        return repository;
    }

    private CodeRepository linkedRepository(ApplicationId appId, CodeRepositoryId repositoryId) {
        return configurationRepository.findRepositoriesByApplication(appId).stream()
                .filter(repository -> repository.repositoryId().equals(repositoryId)
                        && CodeRepositoryType.APPLICATION_ASSET_REPOSITORY.value().equals(repository.repositoryType()))
                .findFirst().orElse(null);
    }

    private Configuration view(ApplicationAssetReference reference, CodeRepository repository) {
        return new Configuration(reference.repositoryId().value(), repository.name(), reference.alias(),
                reference.directoryPath(), reference.merge(), reference.sddFolderName(),
                reference.description(), reference.version());
    }

    static String requireRelativePath(String path) {
        if (path == null || path.isBlank() || path.length() > 1000 || path.contains("\\")
                || path.startsWith("/") || path.endsWith("/") || path.contains("//")) {
            throw invalid("资产目录路径无效");
        }
        for (String segment : path.split("/")) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..") || segment.equals(".git")) {
                throw invalid("资产目录路径无效");
            }
        }
        return path;
    }

    private static PlatformException invalid(String message) {
        return new PlatformException(ErrorCode.VALIDATION_ERROR, message);
    }

    private static PlatformException conflict(String message) {
        return new PlatformException(ErrorCode.CONFLICT, message, Map.of());
    }

    public record Configuration(String repositoryId, String repositoryName, String alias,
                                String directoryPath, boolean merge, String sddFolderName,
                                String description, long version) { }

    public record Listing(List<Configuration> configurations, boolean importPending,
                          List<ApplicationAssetReferenceStore.ImportConflict> conflicts) { }
}
