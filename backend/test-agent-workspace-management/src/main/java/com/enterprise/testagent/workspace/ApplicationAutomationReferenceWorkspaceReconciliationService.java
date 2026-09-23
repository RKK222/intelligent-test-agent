package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceGeneration;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceRepository;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceRunLease;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceState;
import com.enterprise.testagent.domain.automationreference.AutomationReferenceGenerationStatus;
import com.enterprise.testagent.domain.automationreference.AutomationReferenceRunPreparation;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.reference.ApplicationAssetReference;
import com.enterprise.testagent.domain.reference.ApplicationAssetReferenceStore;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryRepository;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryReplicaStatus;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 把应用当前自动化配置对账到一个个人工作树的 JSONC，并返回本次可租用的精确共享代次。
 *
 * <p>本服务只负责配置发现和条件写，不重载 OpenCode、不持久化 Run 租约。工作区进入、generation READY
 * 与真实 Run 派发都复用这一入口，避免浏览器和 Java 分别维护补丁算法。
 */
@Service
public class ApplicationAutomationReferenceWorkspaceReconciliationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            ApplicationAutomationReferenceWorkspaceReconciliationService.class);
    private static final String OPENCODE_CONFIG_PATH = "opencode.jsonc";
    private static final int MAX_WRITE_ATTEMPTS = 2;

    private final ConfigurationManagementRepository configurationRepository;
    private final ApplicationAutomationReferenceRepository automationRepository;
    private final AutomationWorkspaceReferenceCatalogService catalogService;
    private final AgentConfigApplicationService agentConfigService;
    private final ApplicationAssetReferenceStore assetStore;
    private final ReferenceRepositoryRepository referenceRepository;
    private final AutomationReferenceWorkspaceJsoncReconciler jsoncReconciler;

    public ApplicationAutomationReferenceWorkspaceReconciliationService(
            ConfigurationManagementRepository configurationRepository,
            ApplicationAutomationReferenceRepository automationRepository,
            AutomationWorkspaceReferenceCatalogService catalogService,
            AgentConfigApplicationService agentConfigService,
            ObjectMapper objectMapper) {
        this(configurationRepository, automationRepository, catalogService, agentConfigService,
                null, null, objectMapper);
    }

    /** 生产装配同时对账应用共享资产和自动化引用；旧测试构造器保持兼容。 */
    @Autowired
    public ApplicationAutomationReferenceWorkspaceReconciliationService(
            ConfigurationManagementRepository configurationRepository,
            ApplicationAutomationReferenceRepository automationRepository,
            AutomationWorkspaceReferenceCatalogService catalogService,
            AgentConfigApplicationService agentConfigService,
            ApplicationAssetReferenceStore assetStore,
            ReferenceRepositoryRepository referenceRepository,
            ObjectMapper objectMapper) {
        this.configurationRepository = Objects.requireNonNull(configurationRepository);
        this.automationRepository = Objects.requireNonNull(automationRepository);
        this.catalogService = Objects.requireNonNull(catalogService);
        this.agentConfigService = Objects.requireNonNull(agentConfigService);
        this.assetStore = assetStore;
        this.referenceRepository = referenceRepository;
        this.jsoncReconciler = new AutomationReferenceWorkspaceJsoncReconciler(
                Objects.requireNonNull(objectMapper));
    }

    /** 对账当前工作树；不可用的单个仓库只返回局部告警，不能阻断其它引用和主工作区。 */
    public AutomationReferenceRunPreparation reconcile(Workspace workspace, UserId userId, String traceId) {
        Objects.requireNonNull(workspace, "workspace must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        ApplicationId appId = catalogService.resolveHostApplication(workspace.workspaceId()).orElse(null);
        if (appId == null) {
            return AutomationReferenceRunPreparation.empty();
        }
        if (!configurationRepository.isActiveMember(appId, userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "当前用户已无应用自动化引用访问权限");
        }
        if (workspace.linuxServerId() == null || workspace.linuxServerId().isBlank()) {
            throw new PlatformException(ErrorCode.CONFLICT, "当前工作区尚未绑定自动化引用副本服务器");
        }

        LinuxServerId linuxServerId = new LinuxServerId(workspace.linuxServerId());
        List<ApplicationAutomationReferenceRunLease> leases = new ArrayList<>();
        List<AutomationReferenceWorkspaceJsoncReconciler.Patch> patches = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (CodeRepository repository : automationRepositories(appId)) {
            ApplicationAutomationReferenceState state = automationRepository
                    .findState(appId, repository.repositoryId())
                    .orElse(null);
            if (state == null || state.activeGeneration() == null) {
                continue;
            }
            ApplicationAutomationReferenceGeneration generation = automationRepository
                    .findGeneration(appId, repository.repositoryId(), state.activeGeneration())
                    .orElse(null);
            if (generation == null
                    || generation.status() != AutomationReferenceGenerationStatus.READY
                    || !hasReadyReplica(generation, linuxServerId)) {
                warnings.add(repository.name() + "：当前服务器共享只读副本尚未就绪，已跳过该引用");
                continue;
            }
            leases.add(new ApplicationAutomationReferenceRunLease(
                    appId, repository.repositoryId(), generation.generation(), linuxServerId));
            patches.add(new AutomationReferenceWorkspaceJsoncReconciler.Patch(
                    appId.value(),
                    repository.repositoryId().value(),
                    generation.generation(),
                    generation.referenceAlias(),
                    AutomationReferencePathPolicy.logicalPath(appId, repository, generation),
                    AutomationReferencePathPolicy.directoryName(repository, generation.directoryPath()),
                    generation.description()));
        }

        List<AutomationReferenceWorkspaceJsoncReconciler.AssetPatch> assetPatches = new ArrayList<>();
        if (assetStore != null && referenceRepository != null) {
            var linkedAssets = configurationRepository.findRepositoriesByApplication(appId).stream()
                    .filter(repository -> CodeRepositoryType.APPLICATION_ASSET_REPOSITORY.value()
                            .equals(repository.repositoryType()))
                    .collect(java.util.stream.Collectors.toMap(CodeRepository::repositoryId,
                            repository -> repository, (first, ignored) -> first));
            for (ApplicationAssetReference asset : assetStore.list(appId)) {
                CodeRepository repository = linkedAssets.get(asset.repositoryId());
                if (repository == null) continue;
                var state = referenceRepository.findState(asset.repositoryId()).orElse(null);
                boolean localReady = state != null && state.status() == ReferenceRepositoryStatus.READY
                        && referenceRepository.findReplicas(asset.repositoryId()).stream().anyMatch(replica ->
                        replica.linuxServerId().equals(linuxServerId)
                                && replica.generation() == state.generation()
                                && replica.status() == ReferenceRepositoryReplicaStatus.READY
                                && state.targetCommitHash().equals(replica.currentCommitHash()));
                if (!localReady) {
                    warnings.add(asset.alias() + "：当前服务器资产副本尚未就绪，暂不可读取");
                }
                // 服务器临时不可用时保留声明和精确权限，副本恢复后无需用户重新配置。
                assetPatches.add(new AutomationReferenceWorkspaceJsoncReconciler.AssetPatch(
                        appId.value(), asset.repositoryId().value(), asset.alias(),
                        "{env:OPENCODE_REFERENCES_DIR}/" + repository.englishName() + "/" + asset.directoryPath(),
                        asset.sddFolderName(), asset.merge(), asset.description(),
                        localReady ? state.generation() : null));
            }
        }

        boolean changed = reconcileConfiguration(workspace, appId, patches, assetPatches);
        if (!warnings.isEmpty()) {
            LOGGER.warn(
                    "Automation references partially unavailable, appId={}, workspaceId={}, skippedCount={}, traceId={}",
                    appId.value(), workspace.workspaceId().value(), warnings.size(), traceId);
        }
        return new AutomationReferenceRunPreparation(leases, warnings, changed);
    }

    /**
     * 条件写冲突时只重新读取并重算一次。第二次仍冲突说明用户正持续编辑，必须返回冲突而不是覆盖。
     */
    private boolean reconcileConfiguration(
            Workspace workspace,
            ApplicationId appId,
            List<AutomationReferenceWorkspaceJsoncReconciler.Patch> patches,
            List<AutomationReferenceWorkspaceJsoncReconciler.AssetPatch> assetPatches) {
        boolean observedChange = false;
        for (int attempt = 0; attempt < MAX_WRITE_ATTEMPTS; attempt++) {
            ConfigurationSnapshot snapshot = readWorkspaceConfiguration(workspace);
            String reconciled = jsoncReconciler.reconcile(snapshot.content(), appId.value(), patches);
            reconciled = jsoncReconciler.reconcileAssets(reconciled, appId.value(), assetPatches);
            if (reconciled.equals(snapshot.content())) {
                return observedChange;
            }
            observedChange = true;
            boolean written = agentConfigService.writeWorkspaceAgentFileIfUnchanged(
                    workspace.workspaceId().value(),
                    OPENCODE_CONFIG_PATH,
                    snapshot.exists(),
                    snapshot.sha256(),
                    reconciled,
                    null);
            if (written) {
                return true;
            }
        }
        throw new PlatformException(
                ErrorCode.CONFLICT,
                "OpenCode 配置正在被同时修改，请稍后重试",
                Map.of("reason", "AUTOMATION_REFERENCE_CONFIG_CHANGED"));
    }

    private List<CodeRepository> automationRepositories(ApplicationId appId) {
        return configurationRepository.findRepositoriesByApplication(appId).stream()
                .filter(repository -> CodeRepositoryType.AUTOMATION_CODE_REPOSITORY.value()
                        .equals(repository.repositoryType()))
                .toList();
    }

    private boolean hasReadyReplica(
            ApplicationAutomationReferenceGeneration generation,
            LinuxServerId linuxServerId) {
        return automationRepository.findReplicas(
                        generation.appId(), generation.repositoryId(), generation.generation()).stream()
                .anyMatch(replica -> replica.linuxServerId().equals(linuxServerId)
                        && replica.status() == ReferenceRepositoryReplicaStatus.READY
                        && generation.branch().equals(replica.currentBranch())
                        && generation.targetCommitHash().equals(replica.currentCommitHash()));
    }

    private ConfigurationSnapshot readWorkspaceConfiguration(Workspace workspace) {
        try {
            String content = agentConfigService.readWorkspaceAgentFile(
                    workspace.workspaceId().value(), OPENCODE_CONFIG_PATH, null).content();
            return new ConfigurationSnapshot(true, content, sha256(content));
        } catch (PlatformException exception) {
            if (exception.errorCode() == ErrorCode.NOT_FOUND) {
                return new ConfigurationSnapshot(false, "", null);
            }
            throw exception;
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record ConfigurationSnapshot(boolean exists, String content, String sha256) {
    }
}
