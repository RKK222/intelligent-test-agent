package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.GitWorkspaceService;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.ApplicationWorkspace;
import com.enterprise.testagent.domain.configuration.ApplicationWorkspaceId;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Artifact;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Asset;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.AssetSummary;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.AssetType;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.BuiltinPushedRevision;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.BuiltinRevision;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.BuiltinSnapshot;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Dependency;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.ExternalSkill;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.ExternalSkillPackage;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.PushedAsset;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.PushedSnapshot;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.PushReferenceAction;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.PushReferenceDecision;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Reference;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.ReferenceUpdate;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Revision;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillCategory;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillHubUploadFile;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillHubUploadProgress;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillHubUploadRequest;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillSubcategory;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SourceKind;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.UpdateOperation;
import com.enterprise.testagent.domain.hub.AgentSkillHubPushIndexer;
import com.enterprise.testagent.domain.hub.AgentSkillHubRepository;
import com.enterprise.testagent.domain.hub.SkillHubGateway;
import com.enterprise.testagent.domain.hub.ProtectedAgentDefinitionResolver;
import com.enterprise.testagent.domain.hub.ProtectedAgentSelection;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspace;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.scheduler.ScheduledTaskLock;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Agent & Skill Hub 应用服务：从远端固定提交生成不可变制品，管理显式发布、依赖、引用和三方更新。
 */
@Service
public class AgentSkillHubApplicationService implements AgentSkillHubPushIndexer, ProtectedAgentDefinitionResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentSkillHubApplicationService.class);
    private static final String ENCODING = "GZIP_JSON_V1";
    private static final int MAX_FILES = 256;
    private static final long MAX_UNCOMPRESSED_BYTES = 20L * 1024L * 1024L;
    private static final int MAX_SKILLHUB_PICTURE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_CANONICAL_BYTES = 32 * 1024 * 1024;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String AGENT_FILE = "AGENT.md";
    private static final String PUBLIC_CONFIG_GIT_ROOT = "OPENCODE_PUBLIC_CONFIG_GIT_ROOT";
    private static final String BUILTIN_ASSET_PREFIX = "hub_builtin_";
    private static final String BUILTIN_REVISION_PREFIX = "hub_builtin_rev_";
    /** Hub 技术标识允许 OpenCode 目录安全使用的大小写字母、数字、点、下划线和短横线。 */
    private static final Pattern TECHNICAL_ID_PATTERN =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private static final ScheduledTaskKey SKILLHUB_SYNC_TASK_KEY =
            new ScheduledTaskKey("workspace-management.skillhub-catalog-sync");
    private static final AgentConfigMetadataParser METADATA_PARSER = new AgentConfigMetadataParser();

    private final AgentSkillHubRepository repository;
    private final ConfigurationManagementRepository configurationRepository;
    private final ManagedWorkspaceRepository managedWorkspaceRepository;
    private final CommonParameterValues commonParameterValues;
    private final GitWorkspaceService git;
    private final ObjectMapper objectMapper;
    private final ManagedWorkspacePathResolver pathResolver;
    private SkillHubGateway skillHubGateway;
    private ScheduledTaskLock scheduledTaskLock;

    @Autowired
    public AgentSkillHubApplicationService(
            AgentSkillHubRepository repository,
            ConfigurationManagementRepository configurationRepository,
            ManagedWorkspaceRepository managedWorkspaceRepository,
            CommonParameterValues commonParameterValues,
            ObjectMapper objectMapper) {
        this(repository, configurationRepository, managedWorkspaceRepository,
                commonParameterValues, new GitWorkspaceService(), objectMapper);
    }

    AgentSkillHubApplicationService(
            AgentSkillHubRepository repository,
            ConfigurationManagementRepository configurationRepository,
            ManagedWorkspaceRepository managedWorkspaceRepository,
            CommonParameterValues commonParameterValues,
            GitWorkspaceService git,
            ObjectMapper objectMapper) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.configurationRepository = Objects.requireNonNull(configurationRepository, "configurationRepository must not be null");
        this.managedWorkspaceRepository = Objects.requireNonNull(managedWorkspaceRepository, "managedWorkspaceRepository must not be null");
        this.commonParameterValues = Objects.requireNonNull(commonParameterValues, "commonParameterValues must not be null");
        this.git = Objects.requireNonNull(git, "git must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.pathResolver = new ManagedWorkspacePathResolver(this.commonParameterValues);
    }

    /** 测试构造器保持轻量；生产环境由 Spring 显式注入外部网关和 Redis 分布式锁。 */
    @Autowired(required = false)
    void setSkillHubGateway(SkillHubGateway skillHubGateway) {
        this.skillHubGateway = skillHubGateway;
    }

    @Autowired(required = false)
    void setScheduledTaskLock(ScheduledTaskLock scheduledTaskLock) {
        this.scheduledTaskLock = scheduledTaskLock;
    }

    /** 多节点只允许一个实例同步目录；Redis 不可用时不做本机锁降级。 */
    @Scheduled(
            initialDelayString = "${test-agent.skill-hub.sync-initial-delay:PT10S}",
            fixedDelayString = "${test-agent.skill-hub.sync-delay:PT10M}")
    public void reconcileExternalSkillHubCatalog() {
        if (skillHubGateway == null || !skillHubGateway.enabled() || scheduledTaskLock == null) return;
        scheduledTaskLock.acquire(SKILLHUB_SYNC_TASK_KEY, Duration.ofMinutes(2)).ifPresent(lease -> {
            try (lease) {
                syncExternalSkillHubCatalog();
            } catch (RuntimeException exception) {
                LOGGER.warn("event=skillhub_catalog_sync_failed error={}", exception.toString());
            }
        });
    }

    public AgentSkillHubResponses.ExternalSyncResponse syncExternalSkillHubCatalog() {
        ensureSkillHubEnabled();
        List<ExternalSkill> skills = skillHubGateway.listSkills();
        validateExternalCatalog(skills);
        Instant synchronizedAt = Instant.now();
        repository.replaceExternalCatalog(skills, synchronizedAt);
        return new AgentSkillHubResponses.ExternalSyncResponse(skills.size(), synchronizedAt);
    }

    /** 平台接收六个业务字段，并补入当前认证主体统一认证号后调用上游 /upload。 */
    public String uploadExternalSkillHub(SkillHubUploadRequest request) {
        ensureSkillHubEnabled();
        SkillHubUploadRequest validated = validateExternalUpload(request);
        return skillHubGateway.upload(validated).taskId();
    }

    /** 返回结构直接对应上游 result 中的 progress 和 message。 */
    public SkillHubUploadProgress externalSkillHubUploadProgress(String taskId) {
        ensureSkillHubEnabled();
        validateTaskId(taskId);
        return skillHubGateway.uploadProgress(taskId.trim());
    }

    @Override
    public void indexSuccessfulPush(ApplicationWorkspaceVersion version, Path repoRoot, Path workspaceRoot, String commitHash) {
        String prefix = repoRelativePrefix(repoRoot, workspaceRoot);
        List<String> files = git.listFilesAtCommit(repoRoot, commitHash, prefix + ".opencode");
        Map<String, byte[]> blobs = new LinkedHashMap<>();
        for (String file : files) {
            blobs.put(file, git.readFileAtCommit(repoRoot, commitHash, file));
        }
        List<PushedAsset> assets = new ArrayList<>();
        String agentsPrefix = prefix + ".opencode/agents/";
        String skillsPrefix = prefix + ".opencode/skills/";
        blobs.forEach((file, bytes) -> {
            if (file.startsWith(agentsPrefix) && file.substring(agentsPrefix.length()).matches("[^/]+\\.md")) {
                String technicalId = file.substring(agentsPrefix.length(), file.length() - 3);
                assets.add(pushedAsset(AssetType.AGENT, technicalId, Map.of(AGENT_FILE, bytes)));
            }
        });
        Map<String, Map<String, byte[]>> skills = new LinkedHashMap<>();
        blobs.forEach((file, bytes) -> {
            if (!file.startsWith(skillsPrefix)) return;
            String relative = file.substring(skillsPrefix.length());
            int slash = relative.indexOf('/');
            if (slash <= 0 || slash == relative.length() - 1) return;
            String technicalId = relative.substring(0, slash);
            skills.computeIfAbsent(technicalId, ignored -> new LinkedHashMap<>())
                    .put(relative.substring(slash + 1), bytes);
        });
        skills.forEach((technicalId, skillFiles) -> {
            if (skillFiles.containsKey("SKILL.md")) {
                assets.add(pushedAsset(AssetType.SKILL, technicalId, skillFiles));
            }
        });
        SnapshotReconciliation reconciliation = reconcilePushedReferences(
                version.applicationWorkspaceId().value(), blobs, prefix, assets);
        PushedSnapshot snapshot = new PushedSnapshot(
                version.appId().value(), version.applicationWorkspaceId().value(), version.versionId().value(),
                commitHash, Objects.requireNonNullElse(version.targetCommitUpdatedAt(), version.updatedAt()),
                reconciliation.assets());
        if (reconciliation.decisions().isEmpty()) repository.replacePushedSnapshot(snapshot);
        else repository.replacePushedSnapshot(snapshot, reconciliation.decisions());
    }

    /**
     * 周期对账只扫描仍启用、本机 READY 且精确等于 target commit 的应用版本副本；
     * 已退出正常入口的历史模板（包括旧自动化 worktree）不得再参与 Agent/Skill 投影。
     */
    @Scheduled(initialDelayString = "PT20S", fixedDelayString = "PT2M")
    public void reconcileLocalSnapshots() {
        for (var app : configurationRepository.findApplications(true)) {
            Set<ApplicationWorkspaceId> enabledWorkspaceIds = configurationRepository.findWorkspaces(app.appId())
                    .stream()
                    .filter(ApplicationWorkspace::enabled)
                    .map(ApplicationWorkspace::workspaceId)
                    .collect(Collectors.toSet());
            for (ApplicationWorkspaceVersion version : managedWorkspaceRepository.findVersionsByApplication(app.appId())) {
                if (!enabledWorkspaceIds.contains(version.applicationWorkspaceId())) continue;
                if (version.targetCommitHash() == null) continue;
                managedWorkspaceRepository.findVersionReplicaByRuntimeWorkspace(version.runtimeWorkspaceId())
                        .filter(replica -> "READY".equals(replica.syncStatus().name()))
                        .filter(replica -> version.targetCommitHash().equals(replica.currentCommitHash()))
                        .ifPresent(replica -> {
                            try {
                                indexSuccessfulPush(version, pathResolver.resolve(replica.repoRootPath()),
                                        pathResolver.resolve(replica.workspaceRootPath()), version.targetCommitHash());
                            } catch (RuntimeException exception) {
                                LOGGER.warn("event=hub_snapshot_reconcile_failed versionId={} commit={} error={}",
                                        version.versionId().value(), version.targetCommitHash(), exception.toString());
                            }
                        });
            }
        }
    }

    /**
     * 定时把公共配置当前分支的远端提交固化到数据库。fetch 使用共享仓库现有 Git 身份，失败时
     * 回退本地 HEAD，因此用户从其它 clone 直接 push 后也能被发现，且不会修改运行工作树。
     */
    @Scheduled(
            initialDelayString = "${test-agent.agent-skill-hub.builtin-reconcile-initial-delay:PT2S}",
            fixedDelayString = "${test-agent.agent-skill-hub.builtin-reconcile-delay:PT10M}")
    public void reconcilePublicBuiltinSnapshots() {
        Path repoRoot = publicConfigGitRoot();
        if (repoRoot == null || !git.isGitRepository(repoRoot)) {
            return;
        }
        try {
            String headCommit = publicSnapshotCommit(repoRoot);
            String indexedCommit = repository.findBuiltinSnapshotCommit().orElse(null);
            if (headCommit.equals(indexedCommit)) {
                return;
            }
            if (indexedCommit != null) {
                if (git.isAncestor(repoRoot, headCommit, indexedCommit)) {
                    LOGGER.debug("event=hub_public_builtin_stale_replica_skipped indexedCommit={} localCommit={}",
                            indexedCommit, headCommit);
                    return;
                }
                if (!git.isAncestor(repoRoot, indexedCommit, headCommit)) {
                    LOGGER.warn("event=hub_public_builtin_diverged_skipped indexedCommit={} localCommit={}",
                            indexedCommit, headCommit);
                    return;
                }
            }
            Instant indexedAt = Instant.now();
            BuiltinSnapshot snapshot = scanPublicBuiltinSnapshot(repoRoot, headCommit, indexedAt);
            if (repository.replaceBuiltinSnapshot(indexedCommit, snapshot)) {
                LOGGER.info("event=hub_public_builtin_snapshot_indexed commit={} assetCount={}",
                        headCommit, snapshot.revisions().size());
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("event=hub_public_builtin_reconcile_failed repoRoot={} error={}",
                    repoRoot, exception.toString());
        }
    }

    /** 只刷新 origin 引用，不 checkout/reset；远端认证不可用时仍允许公共 rollout 后的本地 HEAD 被索引。 */
    private String publicSnapshotCommit(Path repoRoot) {
        String localCommit = git.headCommit(repoRoot);
        String branch = git.currentBranch(repoRoot);
        if (branch == null || branch.isBlank() || "HEAD".equals(branch)) {
            return localCommit;
        }
        try {
            git.fetch(repoRoot, null);
            return git.resolveCommit(repoRoot, "origin/" + branch);
        } catch (RuntimeException exception) {
            LOGGER.debug("event=hub_public_builtin_remote_refresh_fallback branch={} localCommit={} error={}",
                    branch, localCommit, exception.toString());
            return localCommit;
        }
    }

    public AgentSkillHubResponses.PageResponse<AgentSkillHubResponses.AssetResponse> listAssets(
            String type, String category, String subcategory, String source, String keyword,
            boolean referencedOnly, int page, int size,
            String targetRuntimeWorkspaceId, UserId userId) {
        int normalizedPage = Math.max(1, page);
        int normalizedSize = Math.max(1, Math.min(MAX_PAGE_SIZE, size));
        AssetType assetType = type == null || type.isBlank() ? null : parseType(type);
        SkillCategory skillCategory = category == null || category.isBlank() ? null : parseCategory(category);
        SkillSubcategory skillSubcategory = subcategory == null || subcategory.isBlank()
                ? null : parseSubcategory(subcategory);
        SourceKind sourceKind = parseSourceFilter(source);
        validateClassificationFilter(assetType, skillCategory, skillSubcategory);
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        String targetWorkspaceId = targetApplicationWorkspaceId(targetRuntimeWorkspaceId, userId);
        if (referencedOnly && targetWorkspaceId == null) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "查看当前应用引用时必须选择个人工作区");
        }
        List<BuiltinRevision> builtins = referencedOnly || sourceKind == SourceKind.SKILLHUB ? List.of()
                : publicBuiltinSnapshots(assetType, normalizedKeyword, skillCategory, skillSubcategory);
        int offset = (normalizedPage - 1) * normalizedSize;
        List<AgentSkillHubResponses.AssetResponse> items = new ArrayList<>();
        if (offset < builtins.size()) {
            builtins.subList(offset, Math.min(builtins.size(), offset + normalizedSize)).stream()
                    .map(this::response).forEach(items::add);
        }
        int databaseOffset = Math.max(0, offset - builtins.size());
        int remaining = normalizedSize - items.size();
        if (remaining > 0) {
            listRepositoryAssets(assetType, skillCategory, skillSubcategory, sourceKind,
                            normalizedKeyword, userId.value(),
                            targetWorkspaceId, referencedOnly, databaseOffset, remaining).stream()
                    .map(this::response).forEach(items::add);
        }
        long databaseTotal = sourceKind == SourceKind.PLATFORM
                ? repository.countAssets(assetType, skillCategory, skillSubcategory,
                        normalizedKeyword, targetWorkspaceId, referencedOnly)
                : repository.countAssets(assetType, skillCategory, skillSubcategory, sourceKind,
                        normalizedKeyword, targetWorkspaceId, referencedOnly);
        return new AgentSkillHubResponses.PageResponse<>(
                List.copyOf(items),
                builtins.size() + databaseTotal,
                normalizedPage, normalizedSize);
    }

    private List<AssetSummary> listRepositoryAssets(
            AssetType assetType, SkillCategory category, SkillSubcategory subcategory, SourceKind sourceKind,
            String keyword, String userId, String targetWorkspaceId, boolean referencedOnly,
            int offset, int limit) {
        if (sourceKind == SourceKind.PLATFORM) {
            return repository.listAssets(assetType, category, subcategory, keyword, userId,
                    targetWorkspaceId, referencedOnly, offset, limit);
        }
        return repository.listAssets(assetType, category, subcategory, sourceKind, keyword, userId,
                targetWorkspaceId, referencedOnly, offset, limit);
    }

    /** 兼容既有 API/测试调用；未声明来源时只返回原平台来源。 */
    public AgentSkillHubResponses.PageResponse<AgentSkillHubResponses.AssetResponse> listAssets(
            String type, String category, String subcategory, String keyword,
            boolean referencedOnly, int page, int size,
            String targetRuntimeWorkspaceId, UserId userId) {
        return listAssets(type, category, subcategory, null, keyword, referencedOnly, page, size,
                targetRuntimeWorkspaceId, userId);
    }

    public AgentSkillHubResponses.PageResponse<AgentSkillHubResponses.AssetResponse> listAssets(
            String type, String keyword, int page, int size, UserId userId) {
        return listAssets(type, null, null, null, keyword, false, page, size, null, userId);
    }

    /**
     * 把当前用户可见的已发布 Agent 投影成运行目录项；目录项只有不可变修订 ID 和摘要，不返回正文。
     */
    @Override
    public List<ProtectedAgentDefinitionResolver.CatalogItem> listCatalog(UserId userId, WorkspaceId workspaceId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        List<ProtectedAgentDefinitionResolver.CatalogItem> items = new ArrayList<>();
        publicBuiltinSnapshots(AssetType.AGENT, null, null, null).stream()
                .map(this::protectedCatalogItem)
                .forEach(items::add);
        repository.listAssets(AssetType.AGENT, null, null, null, userId.value(), null, false, 0, MAX_PAGE_SIZE)
                .stream()
                .filter(summary -> summary.publishedRevision() != null && !summary.publishedRevision().deleted())
                .map(summary -> protectedCatalogItem(summary.asset(), summary.publishedRevision()))
                .forEach(items::add);
        return items.stream()
                .sorted(Comparator.comparing(ProtectedAgentDefinitionResolver.CatalogItem::displayName)
                        .thenComparing(ProtectedAgentDefinitionResolver.CatalogItem::revisionId))
                .toList();
    }

    /**
     * 解析已发布 Agent 及发布时冻结的 Skill 修订。数据库资产必须仍是当前发布修订，防止伪造 pushed 修订。
     */
    @Override
    public ProtectedAgentDefinitionResolver.Definition resolve(
            UserId userId,
            WorkspaceId workspaceId,
            String revisionId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        if (isBuiltinRevisionId(revisionId)) {
            BuiltinRevision revision = requireBuiltinRevision(revisionId);
            if (revision.assetType() != AssetType.AGENT) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "受保护运行只能选择 Agent 修订");
            }
            Map<String, String> agentFiles = textFiles(requireBuiltinArtifact(revision));
            return new ProtectedAgentDefinitionResolver.Definition(
                    revision.assetId(),
                    revision.technicalId(),
                    revision.revisionId(),
                    revision.artifactSha256(),
                    revision.contentSha256(),
                    firstText(revision.displayName(), revision.technicalId()),
                    agentFiles,
                    referencedBuiltinSkills(revision, agentFiles));
        }
        Revision revision = requireRevision(revisionId);
        Asset asset = requireAsset(revision.assetId());
        if (asset.assetType() != AssetType.AGENT
                || !revision.revisionId().equals(asset.latestPublishedRevisionId())
                || revision.deleted()
                || !isVisiblePublishedAsset(asset, revision, userId)) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "受保护 Agent 修订不存在或不可用");
        }
        List<ProtectedAgentDefinitionResolver.SkillDefinition> skills = repository.findDependencies(revision.revisionId())
                .stream()
                .map(this::protectedSkill)
                .toList();
        return new ProtectedAgentDefinitionResolver.Definition(
                asset.assetId(),
                asset.technicalId(),
                revision.revisionId(),
                revision.artifactSha256(),
                revision.contentSha256(),
                firstText(revision.displayName(), asset.technicalId()),
                textFiles(requireArtifact(revision)),
                skills);
    }

    private boolean isVisiblePublishedAsset(Asset asset, Revision revision, UserId userId) {
        return repository.listAssets(
                        AssetType.AGENT, null, null, asset.technicalId(), userId.value(), null, false, 0, MAX_PAGE_SIZE)
                .stream()
                .anyMatch(summary -> summary.asset().assetId().equals(asset.assetId())
                        && summary.publishedRevision() != null
                        && summary.publishedRevision().revisionId().equals(revision.revisionId()));
    }

    private ProtectedAgentDefinitionResolver.SkillDefinition protectedSkill(Dependency dependency) {
        Asset skillAsset = requireAsset(dependency.dependencyAssetId());
        Revision skillRevision = requireRevision(dependency.dependencyRevisionId());
        if (skillAsset.assetType() != AssetType.SKILL
                || !skillAsset.assetId().equals(skillRevision.assetId())
                || skillRevision.deleted()) {
            throw new PlatformException(ErrorCode.CONFLICT, "受保护 Agent 的 Skill 依赖已失效");
        }
        return new ProtectedAgentDefinitionResolver.SkillDefinition(
                skillAsset.assetId(),
                skillAsset.technicalId(),
                skillRevision.revisionId(),
                skillRevision.artifactSha256(),
                skillRevision.contentSha256(),
                firstText(skillRevision.displayName(), skillAsset.technicalId()),
                textFiles(requireArtifact(skillRevision)));
    }

    /**
     * 公共内置 Agent 没有独立依赖表；受保护运行只能从同一不可变 Git 提交中选择正文精确提到的 Skill 技术 ID。
     * 边界把连字符视为技术 ID 的一部分，避免 test-design 误命中 test-design-api。
     */
    private List<ProtectedAgentDefinitionResolver.SkillDefinition> referencedBuiltinSkills(
            BuiltinRevision agentRevision, Map<String, String> agentFiles) {
        String agentPrompt = agentFiles.getOrDefault(AGENT_FILE, "");
        return repository.listBuiltinRevisionsByCommit(agentRevision.sourceCommitHash()).stream()
                .filter(candidate -> candidate.assetType() == AssetType.SKILL)
                .filter(candidate -> containsTechnicalId(agentPrompt, candidate.technicalId()))
                .map(this::protectedBuiltinSkill)
                .toList();
    }

    private boolean containsTechnicalId(String content, String technicalId) {
        String boundary = "[A-Za-z0-9._-]";
        return java.util.regex.Pattern.compile(
                        "(?<!" + boundary + ")" + java.util.regex.Pattern.quote(technicalId)
                                + "(?!" + boundary + ")")
                .matcher(content)
                .find();
    }

    private ProtectedAgentDefinitionResolver.SkillDefinition protectedBuiltinSkill(BuiltinRevision revision) {
        return new ProtectedAgentDefinitionResolver.SkillDefinition(
                revision.assetId(),
                revision.technicalId(),
                revision.revisionId(),
                revision.artifactSha256(),
                revision.contentSha256(),
                firstText(revision.displayName(), revision.technicalId()),
                textFiles(requireBuiltinArtifact(revision)));
    }

    private ProtectedAgentDefinitionResolver.CatalogItem protectedCatalogItem(BuiltinRevision revision) {
        return new ProtectedAgentDefinitionResolver.CatalogItem(
                ProtectedAgentSelection.catalogId(revision.revisionId()),
                revision.revisionId(),
                firstText(revision.displayName(), revision.technicalId()),
                revision.description(),
                revision.contentSha256(),
                ProtectedAgentDefinitionResolver.CatalogSource.PUBLIC_GIT);
    }

    private ProtectedAgentDefinitionResolver.CatalogItem protectedCatalogItem(Asset asset, Revision revision) {
        return new ProtectedAgentDefinitionResolver.CatalogItem(
                ProtectedAgentSelection.catalogId(revision.revisionId()),
                revision.revisionId(),
                firstText(revision.displayName(), asset.technicalId()),
                revision.description(),
                revision.contentSha256(),
                ProtectedAgentDefinitionResolver.CatalogSource.APPLICATION_HUB);
    }

    /** 受保护运行只物化文本资产；二进制附件继续由不可变制品摘要审计，但不会进入模型上下文。 */
    private Map<String, String> textFiles(Artifact artifact) {
        Map<String, String> files = new LinkedHashMap<>();
        for (ArtifactFile file : decode(artifact).files()) {
            byte[] bytes = Base64.getDecoder().decode(file.contentBase64());
            if (isUtf8(bytes)) {
                files.put(file.path(), new String(bytes, StandardCharsets.UTF_8));
            }
        }
        return Map.copyOf(files);
    }

    public AgentSkillHubResponses.AssetDetailResponse getAsset(
            String assetId, String revisionId, String targetRuntimeWorkspaceId, UserId userId) {
        if (isBuiltinAssetId(assetId)) {
            BuiltinRevision snapshot = requireBuiltinAsset(assetId, revisionId);
            ArtifactEnvelope envelope = decode(requireBuiltinArtifact(snapshot));
            return new AgentSkillHubResponses.AssetDetailResponse(
                    response(snapshot), snapshot.revisionId(),
                    envelope.files().stream().map(file -> new AgentSkillHubResponses.ArtifactFileResponse(
                            file.path(), file.size(), file.sha256(), file.mediaType())).toList(),
                    List.of(), List.of());
        }
        Asset asset = requireAsset(assetId);
        String targetWorkspaceId = targetApplicationWorkspaceId(targetRuntimeWorkspaceId, userId);
        boolean unavailableReferenceView = !asset.sourceAvailable();
        if (unavailableReferenceView && targetWorkspaceId == null) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "Hub 资产不存在或来源已不可用");
        }
        AssetSummary summary = repository.listAssets(
                        asset.assetType(), null, null, asset.sourceKind(), asset.technicalId(), userId.value(),
                        targetWorkspaceId, unavailableReferenceView, 0, 100).stream()
                .filter(item -> item.asset().assetId().equals(assetId)).findFirst()
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Hub 资产不存在"));
        String selected = revisionId == null || revisionId.isBlank()
                ? asset.latestPushedRevisionId() : revisionId.trim();
        if (selected == null && asset.sourceKind() == SourceKind.SKILLHUB) {
            return new AgentSkillHubResponses.AssetDetailResponse(
                    response(summary), null, List.of(), List.of(), consumers(asset.assetId(), userId));
        }
        Revision revision = requireRevision(selected);
        if (!asset.assetId().equals(revision.assetId())) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "修订不属于指定 Hub 资产");
        }
        ArtifactEnvelope envelope = revision.deleted() ? new ArtifactEnvelope(List.of()) : decode(requireArtifact(revision));
        return new AgentSkillHubResponses.AssetDetailResponse(
                response(summary), selected,
                envelope.files().stream().map(file -> new AgentSkillHubResponses.ArtifactFileResponse(
                        file.path(), file.size(), file.sha256(), file.mediaType())).toList(),
                dependencies(revision.revisionId()), consumers(asset.assetId(), userId));
    }

    public AgentSkillHubResponses.AssetDetailResponse getAsset(String assetId, String revisionId, UserId userId) {
        return getAsset(assetId, revisionId, null, userId);
    }

    /** 显式预览才下载 ZIP；相同外部 ID+版本必须保持同一内容摘要。 */
    public AgentSkillHubResponses.AssetDetailResponse materializeExternalAsset(
            String assetId, String targetRuntimeWorkspaceId, UserId userId, String unifiedAuthId) {
        Asset asset = requireAsset(assetId);
        materializeExternalRevision(asset, unifiedAuthId);
        return getAsset(assetId, null, targetRuntimeWorkspaceId, userId);
    }

    public AgentSkillHubResponses.FileContentResponse readFile(String revisionId, String path) {
        if (isBuiltinRevisionId(revisionId)) {
            BuiltinRevision snapshot = requireBuiltinRevision(revisionId);
            ArtifactFile file = decode(requireBuiltinArtifact(snapshot)).files().stream()
                    .filter(candidate -> candidate.path().equals(normalizeArtifactPath(path)))
                    .findFirst().orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Hub 制品文件不存在"));
            byte[] bytes = Base64.getDecoder().decode(file.contentBase64());
            return new AgentSkillHubResponses.FileContentResponse(
                    snapshot.revisionId(), file.path(), utf8(bytes, file.path()), bytes.length, file.sha256());
        }
        Revision revision = requireRevision(revisionId);
        ArtifactFile file = decode(requireArtifact(revision)).files().stream()
                .filter(candidate -> candidate.path().equals(normalizeArtifactPath(path)))
                .findFirst().orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Hub 制品文件不存在"));
        byte[] bytes = Base64.getDecoder().decode(file.contentBase64());
        String content = utf8(bytes, file.path());
        return new AgentSkillHubResponses.FileContentResponse(revisionId, file.path(), content, bytes.length, file.sha256());
    }

    public AgentSkillHubResponses.PublishResponse publish(
            String assetId, List<String> dependencyAssetIds, UserId userId) {
        if (isBuiltinAssetId(assetId)) {
            throw new PlatformException(ErrorCode.CONFLICT, "平台内置 Agent/Skill 已全局生效，无需发布");
        }
        Asset asset = requireAsset(assetId);
        if (asset.sourceKind() == SourceKind.SKILLHUB) {
            throw new PlatformException(ErrorCode.CONFLICT, "SkillHub 外部 Skill 无需平台发布；修改后 push 会自动形成平台派生资产");
        }
        requireMember(asset.sourceAppId(), userId);
        Revision revision = requireRevision(asset.latestPushedRevisionId());
        if (revision.deleted()) throw new PlatformException(ErrorCode.CONFLICT, "已删除的 Agent/Skill 不能发布");
        LinkedHashSet<String> normalizedIds = dependencyAssetIds == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(dependencyAssetIds);
        if (normalizedIds.contains(assetId)) {
            throw new PlatformException(ErrorCode.CONFLICT, "Hub 资产不能依赖自身");
        }
        List<Dependency> dependencies = new ArrayList<>();
        Instant now = Instant.now();
        for (String dependencyAssetId : normalizedIds) {
            Asset dependencyAsset = requireAsset(dependencyAssetId);
            if (dependencyAsset.latestPublishedRevisionId() == null) {
                throw new PlatformException(ErrorCode.CONFLICT, "依赖尚未发布", Map.of("assetId", dependencyAssetId));
            }
            if (requireRevision(dependencyAsset.latestPushedRevisionId()).deleted()) {
                throw new PlatformException(ErrorCode.CONFLICT, "依赖已从远端删除", Map.of("assetId", dependencyAssetId));
            }
            dependencies.add(new Dependency(revision.revisionId(), dependencyAssetId,
                    dependencyAsset.latestPublishedRevisionId(), now));
        }
        ensureAcyclic(assetId, dependencies);
        repository.publish(assetId, revision.revisionId(), userId.value(), List.copyOf(dependencies), now);
        return new AgentSkillHubResponses.PublishResponse(assetId, revision.revisionId(), now, dependencies.size());
    }

    /** 由 API 层确认超级管理员身份后修改 Skill 分类；公共 Git 与应用推送分类都跨修订保留。 */
    public AgentSkillHubResponses.ClassificationResponse classifySkill(
            String assetId, String category, String subcategory, UserId userId) {
        SkillCategory normalizedCategory = parseCategory(category);
        SkillSubcategory normalizedSubcategory = subcategory == null || subcategory.isBlank()
                ? null : parseSubcategory(subcategory);
        validateExactClassification(normalizedCategory, normalizedSubcategory);
        Instant now = Instant.now();
        if (isBuiltinAssetId(assetId)) {
            BuiltinRevision revision = requireBuiltinAsset(assetId, null);
            if (revision.assetType() != AssetType.SKILL) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "只有 Skill 可以设置事项分类");
            }
            repository.updateBuiltinSkillClassification(
                    revision.assetId(), normalizedCategory, normalizedSubcategory, userId.value(), now);
        } else {
            Asset asset = requireAsset(assetId);
            if (asset.assetType() != AssetType.SKILL) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "只有 Skill 可以设置事项分类");
            }
            repository.updateSkillClassification(
                    asset.assetId(), normalizedCategory, normalizedSubcategory, userId.value(), now);
        }
        return new AgentSkillHubResponses.ClassificationResponse(
                assetId, normalizedCategory.name(),
                normalizedSubcategory == null ? null : normalizedSubcategory.name(), userId.value(), now);
    }

    public long countUpdates(String targetRuntimeWorkspaceId, UserId userId) {
        return repository.countUpdates(userId.value(), targetApplicationWorkspaceId(targetRuntimeWorkspaceId, userId));
    }

    public AgentSkillHubResponses.PageResponse<AgentSkillHubResponses.UpdateResponse> listUpdates(
            int page, int size, String targetRuntimeWorkspaceId, UserId userId) {
        int normalizedPage = Math.max(1, page);
        int normalizedSize = Math.max(1, Math.min(MAX_PAGE_SIZE, size));
        String targetWorkspaceId = targetApplicationWorkspaceId(targetRuntimeWorkspaceId, userId);
        List<ReferenceUpdate> updates = repository.listUpdates(
                userId.value(), targetWorkspaceId, (normalizedPage - 1) * normalizedSize, normalizedSize);
        return new AgentSkillHubResponses.PageResponse<>(updates.stream().map(this::response).toList(),
                repository.countUpdates(userId.value(), targetWorkspaceId), normalizedPage, normalizedSize);
    }

    /** 在目标个人 worktree 引用已发布修订及其精确依赖，所有碰撞检查通过后才写盘。 */
    public AgentSkillHubResponses.ReferenceResponse createReference(
            String assetId, String targetRuntimeWorkspaceId, String alias, UserId userId, String unifiedAuthId) {
        PersonalWorkspace personal = requireOwnedPersonal(targetRuntimeWorkspaceId, userId);
        Asset rootAsset = ensureExternalMaterialized(requireAsset(assetId), unifiedAuthId);
        if (!rootAsset.sourceAvailable()) {
            throw new PlatformException(ErrorCode.CONFLICT, "Agent/Skill 来源已不可用，不能新建引用");
        }
        if (requireRevision(rootAsset.latestPushedRevisionId()).deleted()) {
            throw new PlatformException(ErrorCode.CONFLICT, "Agent/Skill 已从远端删除，不能新建引用");
        }
        if (rootAsset.latestPublishedRevisionId() == null) {
            throw new PlatformException(ErrorCode.CONFLICT, "Agent/Skill 尚未发布，不能引用");
        }
        String rootAlias = normalizeTechnicalId(alias == null || alias.isBlank() ? rootAsset.technicalId() : alias);
        List<ImportRequest> imports = collectImports(rootAsset, rootAlias);
        Map<String, Reference> reusableReferences = reusablePendingRemovals(personal, imports);
        List<Path> created = materializeImports(personal, imports);
        try {
            Instant now = Instant.now();
            List<Reference> pendingReferences = imports.stream().map(request -> {
                String targetPath = targetPath(request.asset(), request.alias());
                Reference reusable = reusableReferences.get(request.asset().assetId());
                return new Reference(
                        reusable == null ? id("hub_ref_") : reusable.referenceId(),
                        request.asset().assetId(), personal.appId().value(),
                        personal.applicationWorkspaceId().value(), targetPath, request.alias(),
                        reusable == null ? null : reusable.activeRevisionId(),
                        request.revision().revisionId(), request.contentSha256(), "PENDING_PUSH",
                        reusable == null ? userId.value() : reusable.createdByUserId(),
                        reusable == null ? now : reusable.createdAt(), now);
            }).toList();
            List<Reference> savedReferences = repository.saveReferences(pendingReferences);
            Reference rootReference = savedReferences.stream()
                    .filter(reference -> reference.assetId().equals(rootAsset.assetId()))
                    .findFirst().orElseThrow();
            boolean reactivated = reusableReferences.containsKey(rootAsset.assetId());
            return referenceResponse(Objects.requireNonNull(rootReference), true,
                    reactivated
                            ? "已重新引用并写入当前个人 worktree；请在 Git Changes 中提交并推送后生效"
                            : "已写入当前个人 worktree；请在 Git Changes 中提交并推送后生效");
        } catch (RuntimeException exception) {
            rollbackCreated(created);
            throw exception;
        }
    }

    /** 取消先落到当前个人 worktree，只有后续成功 push 证明远端已删除后才解除引用关系。 */
    public AgentSkillHubResponses.ReferenceResponse removeReference(
            String assetId, String targetRuntimeWorkspaceId, UserId userId) {
        PersonalWorkspace personal = requireOwnedPersonal(targetRuntimeWorkspaceId, userId);
        Asset asset = requireAsset(assetId);
        List<Reference> references = repository.findReferencesByTargetAsset(
                personal.applicationWorkspaceId().value(), asset.assetId());
        if (references.isEmpty()) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "当前应用工作空间未引用该 Agent/Skill");
        }
        if (references.size() > 1) {
            throw new PlatformException(ErrorCode.CONFLICT, "当前工作空间存在多个同源引用，请按具体引用解除");
        }
        Reference reference = references.getFirst();
        if ("PENDING_REMOVE".equals(reference.status())) {
            return referenceResponse(reference, false, "取消引用已写入 worktree，等待 push 生效");
        }
        WriteBackup backup = stageRemoval(personal, reference);
        Instant now = Instant.now();
        Reference pendingRemoval = new Reference(
                reference.referenceId(), reference.assetId(), reference.targetAppId(),
                reference.targetApplicationWorkspaceId(), reference.targetPath(), reference.aliasTechnicalId(),
                reference.activeRevisionId(), null, null, "PENDING_REMOVE",
                reference.createdByUserId(), reference.createdAt(), now);
        try {
            Reference saved = repository.saveReference(pendingRemoval);
            commitWrite(backup);
            return referenceResponse(saved, true,
                    "已从当前个人 worktree 移除；请在 Git Changes 中提交并推送后正式解除引用");
        } catch (RuntimeException exception) {
            rollbackWrite(backup);
            throw exception;
        }
    }

    /** 启动三方更新；无冲突时原子写盘，有冲突时只保存操作，工作树保持不变。 */
    public AgentSkillHubResponses.UpdateOperationResponse startUpdate(
            String referenceId, String targetRuntimeWorkspaceId, UserId userId, String unifiedAuthId) {
        PersonalWorkspace personal = requireOwnedPersonal(targetRuntimeWorkspaceId, userId);
        Reference reference = requireReference(referenceId);
        requireReferenceTarget(reference, personal);
        Asset asset = ensureExternalMaterialized(requireAsset(reference.assetId()), unifiedAuthId);
        if (!asset.sourceAvailable()) {
            throw new PlatformException(ErrorCode.CONFLICT, "SkillHub 来源已不可用，不能更新引用");
        }
        Revision incoming = requireRevision(asset.latestPublishedRevisionId());
        Revision base = reference.activeRevisionId() == null ? null : requireRevision(reference.activeRevisionId());
        Map<String, byte[]> baseFiles = base == null ? Map.of() : importFiles(asset, base, reference.aliasTechnicalId());
        Map<String, byte[]> incomingFiles = importFiles(asset, incoming, reference.aliasTechnicalId());
        Map<String, byte[]> currentFiles = readCurrentFiles(personal, asset, reference.aliasTechnicalId());
        List<ConflictFile> merged = mergeFiles(baseFiles, currentFiles, incomingFiles);
        String operationId = id("hub_upd_");
        Instant now = Instant.now();
        boolean conflicted = merged.stream().anyMatch(ConflictFile::conflicted);
        UpdateOperation operation = new UpdateOperation(
                operationId, referenceId, personal.personalWorkspaceId().value(), reference.activeRevisionId(),
                incoming.revisionId(), conflicted ? "CONFLICT" : "COMPLETED", writeJson(merged),
                userId.value(), now, now);
        if (conflicted) {
            return operationResponse(repository.saveUpdateOperation(operation), merged, false);
        }
        WriteBackup backup = stageMergedFiles(personal, asset, reference.aliasTechnicalId(), merged);
        try {
            Reference pending = new Reference(reference.referenceId(), reference.assetId(), reference.targetAppId(),
                    reference.targetApplicationWorkspaceId(), reference.targetPath(), reference.aliasTechnicalId(),
                    reference.activeRevisionId(), incoming.revisionId(), digestFiles(incomingFiles), "PENDING_PUSH",
                    reference.createdByUserId(), reference.createdAt(), now);
            repository.saveReferenceAndUpdateOperation(pending, operation);
            commitWrite(backup);
        } catch (RuntimeException exception) {
            rollbackWrite(backup);
            throw exception;
        }
        return operationResponse(operation, merged, !conflicted);
    }

    public AgentSkillHubResponses.UpdateOperationResponse getUpdateOperation(String operationId, UserId userId) {
        UpdateOperation operation = requireOperation(operationId, userId);
        return operationResponse(operation, readConflicts(operation.conflictsJson()), false);
    }

    public AgentSkillHubResponses.UpdateOperationResponse resolveUpdateConflict(
            String operationId, String path, String resolution, String content, UserId userId) {
        UpdateOperation operation = requireOperation(operationId, userId);
        if (!"CONFLICT".equals(operation.status())) throw new PlatformException(ErrorCode.CONFLICT, "更新操作当前不可解决");
        String normalizedPath = normalizeArtifactPath(path);
        List<ConflictFile> files = new ArrayList<>(readConflicts(operation.conflictsJson()));
        boolean found = false;
        for (int index = 0; index < files.size(); index++) {
            ConflictFile file = files.get(index);
            if (!file.path().equals(normalizedPath)) continue;
            String mode = resolution == null ? "" : resolution.trim().toUpperCase(Locale.ROOT);
            String result = switch (mode) {
                case "CURRENT" -> file.currentContent();
                case "INCOMING" -> file.incomingContent();
                case "BOTH" -> join(file.currentContent(), file.incomingContent());
                case "MANUAL" -> content;
                case "DELETE" -> null;
                default -> throw new PlatformException(ErrorCode.VALIDATION_ERROR, "不支持的冲突解决方式");
            };
            if ("MANUAL".equals(mode) && content == null) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "手工解决内容不能为空");
            }
            files.set(index, file.resolved(mode, result));
            found = true;
        }
        if (!found) throw new PlatformException(ErrorCode.NOT_FOUND, "冲突文件不存在");
        String nextStatus = files.stream().anyMatch(ConflictFile::conflicted) ? "CONFLICT" : "RESOLVED";
        UpdateOperation saved = repository.saveUpdateOperation(new UpdateOperation(
                operation.operationId(), operation.referenceId(), operation.targetPersonalWorkspaceId(),
                operation.fromRevisionId(), operation.toRevisionId(), nextStatus, writeJson(files),
                operation.createdByUserId(), operation.createdAt(), Instant.now()));
        return operationResponse(saved, files, false);
    }

    public AgentSkillHubResponses.ReferenceResponse completeUpdate(String operationId, UserId userId) {
        UpdateOperation operation = requireOperation(operationId, userId);
        List<ConflictFile> files = readConflicts(operation.conflictsJson());
        if (!"RESOLVED".equals(operation.status())) {
            throw new PlatformException(ErrorCode.CONFLICT, "更新操作尚未完成冲突解决或已经结束");
        }
        if (files.stream().anyMatch(ConflictFile::conflicted)) {
            throw new PlatformException(ErrorCode.CONFLICT, "仍有未解决的 Hub 更新冲突");
        }
        PersonalWorkspace personal = managedWorkspaceRepository.findPersonalWorkspace(
                        new com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceId(operation.targetPersonalWorkspaceId()))
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "目标个人工作区不存在"));
        if (!personal.userId().equals(userId)) throw new PlatformException(ErrorCode.FORBIDDEN, "无权完成该更新");
        Reference reference = requireReference(operation.referenceId());
        Asset asset = requireAsset(reference.assetId());
        Map<String, byte[]> expectedCurrent = files.stream().filter(file -> file.currentContent() != null)
                .collect(Collectors.toMap(ConflictFile::path,
                        file -> conflictBytes(file.currentContent(), file.kind()), (a, b) -> b, LinkedHashMap::new));
        Map<String, byte[]> actualCurrent = readCurrentFiles(personal, asset, reference.aliasTechnicalId());
        if (!digestFiles(expectedCurrent).equals(digestFiles(actualCurrent))) {
            throw new PlatformException(ErrorCode.CONFLICT, "当前 worktree 在冲突确认后又发生变化，请重新发起更新");
        }
        Map<String, byte[]> resultFiles = files.stream().filter(file -> file.resultContent() != null)
                .collect(Collectors.toMap(ConflictFile::path,
                        this::conflictResultBytes, (a, b) -> b, LinkedHashMap::new));
        Instant now = Instant.now();
        Reference pending = new Reference(
                reference.referenceId(), reference.assetId(), reference.targetAppId(),
                reference.targetApplicationWorkspaceId(), reference.targetPath(), reference.aliasTechnicalId(),
                reference.activeRevisionId(), operation.toRevisionId(), digestFiles(resultFiles), "PENDING_PUSH",
                reference.createdByUserId(), reference.createdAt(), now);
        UpdateOperation completed = new UpdateOperation(operation.operationId(), operation.referenceId(),
                operation.targetPersonalWorkspaceId(), operation.fromRevisionId(), operation.toRevisionId(),
                "COMPLETED", operation.conflictsJson(), operation.createdByUserId(), operation.createdAt(), now);
        WriteBackup backup = stageMergedFiles(personal, asset, reference.aliasTechnicalId(), files);
        Reference saved;
        try {
            saved = repository.saveReferenceAndUpdateOperation(pending, completed);
            commitWrite(backup);
        } catch (RuntimeException exception) {
            rollbackWrite(backup);
            throw exception;
        }
        return referenceResponse(saved, true, "更新已写入当前个人 worktree；请提交并推送后生效");
    }

    public void abortUpdate(String operationId, UserId userId) {
        UpdateOperation operation = requireOperation(operationId, userId);
        if ("COMPLETED".equals(operation.status())) {
            throw new PlatformException(ErrorCode.CONFLICT, "已完成的更新不能取消");
        }
        repository.saveUpdateOperation(new UpdateOperation(operation.operationId(), operation.referenceId(),
                operation.targetPersonalWorkspaceId(), operation.fromRevisionId(), operation.toRevisionId(),
                "ABORTED", operation.conflictsJson(), operation.createdByUserId(), operation.createdAt(), Instant.now()));
    }

    private void validateExternalCatalog(List<ExternalSkill> skills) {
        if (skills == null) throw new PlatformException(ErrorCode.SKILLHUB_UNAVAILABLE, "SkillHub 目录为空");
        Set<String> names = new HashSet<>();
        Set<String> versions = new HashSet<>();
        for (ExternalSkill skill : skills) {
            if (skill == null || skill.id() <= 0 || skill.version() == null || skill.version().isBlank()
                    || !isValidTechnicalId(skill.name())) {
                throw new PlatformException(ErrorCode.SKILLHUB_UNAVAILABLE, "SkillHub 目录条目标识无效");
            }
            if (!names.add(skill.name()) || !versions.add(skill.id() + ":" + skill.version())) {
                throw new PlatformException(ErrorCode.SKILLHUB_UNAVAILABLE, "SkillHub 目录存在重复条目");
            }
        }
    }

    private void ensureSkillHubEnabled() {
        if (skillHubGateway == null || !skillHubGateway.enabled()) {
            throw new PlatformException(ErrorCode.SKILLHUB_UNAVAILABLE, "SkillHub 集成未启用");
        }
    }

    private SkillHubUploadRequest validateExternalUpload(SkillHubUploadRequest request) {
        if (request == null) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "SkillHub 上传请求不能为空");
        }
        String source = requireUploadText(request.source(), "source");
        String phase = request.phase() == null ? "" : request.phase().trim();
        String unifiedAuthId = requireUnifiedAuthId(request.userId());
        if (!Set.of("00", "01", "02", "03", "04", "05", "06").contains(phase)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "phase 必须是 00 至 06 的阶段编码");
        }
        SkillHubUploadFile skillPackage = requireUploadFile(request.skillPackage(), "file", MAX_UNCOMPRESSED_BYTES);
        if (!skillPackage.filename().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "file 必须是 ZIP 文件");
        }
        Map<String, byte[]> files = unzipExternalSkill(skillPackage.content());
        normalizeTechnicalId(externalSkillManifestName(files.get("SKILL.md")));
        requireUploadFile(request.safetyReportPicture(), "safetyReportPic", MAX_SKILLHUB_PICTURE_BYTES);
        requireUploadFile(request.directoryStructurePicture(), "directoryStructurePic", MAX_SKILLHUB_PICTURE_BYTES);
        requireUploadFile(request.runningEffectPicture(), "runningEffectPic", MAX_SKILLHUB_PICTURE_BYTES);
        return new SkillHubUploadRequest(
                source,
                phase,
                unifiedAuthId,
                request.skillPackage(),
                request.safetyReportPicture(),
                request.directoryStructurePicture(),
                request.runningEffectPicture());
    }

    private String requireUploadText(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 256 || normalized.indexOf('\0') >= 0) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, field + " 无效");
        }
        return normalized;
    }

    /** SkillHub 的 userId 明确指统一认证号，禁止平台内部 userId、空值或控制字符进入上游。 */
    private String requireUnifiedAuthId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 255
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "当前用户统一认证号无效");
        }
        return normalized;
    }

    private SkillHubUploadFile requireUploadFile(SkillHubUploadFile file, String field, long maxBytes) {
        if (file == null || file.size() == 0) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, field + " 不能为空");
        }
        if (file.size() > maxBytes) {
            throw new PlatformException(ErrorCode.PAYLOAD_TOO_LARGE, field + " 超过接口文档建议上限");
        }
        String filename = file.filename() == null ? "" : file.filename().trim();
        if (filename.isEmpty() || filename.length() > 255 || filename.indexOf('\r') >= 0
                || filename.indexOf('\n') >= 0 || filename.indexOf('/') >= 0 || filename.indexOf('\\') >= 0) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, field + " 文件名无效");
        }
        return file;
    }

    private void validateTaskId(String taskId) {
        String normalized = requireUploadText(taskId, "taskId");
        int separator = normalized.lastIndexOf('_');
        String timestamp = separator < 0 ? "" : normalized.substring(separator + 1);
        if (separator <= 0 || timestamp.length() < 10 || timestamp.length() > 17
                || !timestamp.chars().allMatch(Character::isDigit)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "taskId 格式无效");
        }
    }

    private Asset ensureExternalMaterialized(Asset asset, String unifiedAuthId) {
        if (asset.sourceKind() != SourceKind.SKILLHUB) return asset;
        if (!asset.sourceAvailable()) return asset;
        Revision current = asset.latestPublishedRevisionId() == null
                ? null : requireRevision(asset.latestPublishedRevisionId());
        if (current == null || !Objects.equals(current.externalSkillId(), asset.externalSkillId())
                || !Objects.equals(current.externalVersion(), asset.externalVersion())) {
            materializeExternalRevision(asset, unifiedAuthId);
            return requireAsset(asset.assetId());
        }
        return asset;
    }

    private Revision materializeExternalRevision(Asset asset, String unifiedAuthId) {
        if (asset.sourceKind() != SourceKind.SKILLHUB) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "只有 SkillHub 外部 Skill 支持按需下载");
        }
        if (!asset.sourceAvailable()) {
            throw new PlatformException(ErrorCode.CONFLICT, "SkillHub 条目已下架，不能下载或更新");
        }
        if (skillHubGateway == null || !skillHubGateway.enabled()
                || asset.externalSkillId() == null || asset.externalVersion() == null) {
            throw new PlatformException(ErrorCode.SKILLHUB_UNAVAILABLE, "SkillHub 集成未启用或目录元数据不完整");
        }
        ExternalSkillPackage downloaded = skillHubGateway.download(
                asset.externalSkillId(), asset.externalVersion(), requireUnifiedAuthId(unifiedAuthId));
        if (downloaded.id() != asset.externalSkillId()
                || !Objects.equals(downloaded.version(), asset.externalVersion())) {
            throw new PlatformException(ErrorCode.CONFLICT, "SkillHub 下载版本与当前目录不一致，请刷新后重试");
        }
        Map<String, byte[]> files = unzipExternalSkill(downloaded.content());
        validateExternalSkillManifest(asset.technicalId(), files.get("SKILL.md"));
        PushedAsset pushed = pushedAsset(AssetType.SKILL, asset.technicalId(), files);
        Revision existing = repository.findExternalRevision(
                asset.assetId(), downloaded.id(), downloaded.version()).orElse(null);
        if (existing != null && !existing.contentSha256().equals(pushed.contentSha256())) {
            throw new PlatformException(ErrorCode.CONFLICT,
                    "SkillHub 同一 ID 和版本返回了不同内容，已拒绝覆盖",
                    Map.of("assetId", asset.assetId(), "externalVersion", downloaded.version()));
        }
        return repository.saveExternalRevision(
                asset.assetId(), downloaded.id(), downloaded.version(), pushed.artifact(),
                pushed.contentSha256(), firstText(pushed.displayName(), asset.catalogDisplayName(), asset.technicalId()),
                firstText(pushed.description(), asset.catalogDescription()), Instant.now());
    }

    /** ZIP 只读入内存映射，不在文件系统展开，因此链接附件也不会被解释为链接。 */
    private Map<String, byte[]> unzipExternalSkill(byte[] zipBytes) {
        if (zipBytes == null || zipBytes.length == 0 || zipBytes.length > MAX_UNCOMPRESSED_BYTES) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "SkillHub 下载包大小无效");
        }
        Map<String, byte[]> files = new LinkedHashMap<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String path = normalizeArtifactPath(entry.getName());
                if (files.containsKey(path)) {
                    throw new PlatformException(ErrorCode.VALIDATION_ERROR,
                            "SkillHub 下载包包含重复路径", Map.of("path", path));
                }
                if (files.size() >= MAX_FILES) {
                    throw new PlatformException(ErrorCode.VALIDATION_ERROR,
                            "SkillHub 下载包文件数量超限", Map.of("maxFiles", MAX_FILES));
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = zip.read(buffer)) >= 0) {
                    total += read;
                    if (total > MAX_UNCOMPRESSED_BYTES) {
                        throw new PlatformException(ErrorCode.VALIDATION_ERROR,
                                "SkillHub 下载包解压后超过 20 MiB");
                    }
                    output.write(buffer, 0, read);
                }
                files.put(path, output.toByteArray());
            }
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "SkillHub 下载包不是有效 ZIP", Map.of(), exception);
        }
        if (files.isEmpty() || !files.containsKey("SKILL.md")) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "SkillHub 下载包根目录必须包含 SKILL.md");
        }
        return Map.copyOf(files);
    }

    private void validateExternalSkillManifest(String expectedName, byte[] skillMarkdown) {
        String declared = externalSkillManifestName(skillMarkdown);
        if (!expectedName.equals(declared)) {
            throw new PlatformException(ErrorCode.CONFLICT, "SkillHub SKILL.md name 与目录稳定 ID 不一致");
        }
    }

    private String externalSkillManifestName(byte[] skillMarkdown) {
        String content = utf8(skillMarkdown, "SKILL.md");
        var matcher = java.util.regex.Pattern.compile("(?m)^name\\s*:\\s*(.+?)\\s*$").matcher(content);
        if (!matcher.find()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "SkillHub SKILL.md 缺少 name");
        }
        String declared = matcher.group(1).trim();
        if ((declared.startsWith("\"") && declared.endsWith("\""))
                || (declared.startsWith("'") && declared.endsWith("'"))) {
            declared = declared.substring(1, declared.length() - 1);
        }
        return declared;
    }

    private PushedAsset pushedAsset(AssetType type, String technicalId, Map<String, byte[]> files) {
        String normalizedTechnicalId = normalizeTechnicalId(technicalId);
        Artifact artifact = encode(files);
        byte[] metadataBytes = files.get(type == AssetType.AGENT ? AGENT_FILE : "SKILL.md");
        AgentConfigMetadataParser.Metadata metadata = METADATA_PARSER.parse(
                utf8(metadataBytes, normalizedTechnicalId), type == AssetType.SKILL, normalizedTechnicalId);
        return new PushedAsset(type, normalizedTechnicalId, artifact, artifact.sha256(),
                metadata.displayName(), metadata.displayNameEn(), metadata.description());
    }

    private Artifact encode(Map<String, byte[]> sourceFiles) {
        if (sourceFiles.isEmpty() || sourceFiles.size() > MAX_FILES) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Hub 制品文件数量超限",
                    Map.of("maxFiles", MAX_FILES));
        }
        long total = sourceFiles.values().stream().mapToLong(bytes -> bytes.length).sum();
        if (total > MAX_UNCOMPRESSED_BYTES) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Hub 制品大小超限",
                    Map.of("maxUncompressedBytes", MAX_UNCOMPRESSED_BYTES));
        }
        List<ArtifactFile> files = sourceFiles.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    String path = normalizeArtifactPath(entry.getKey());
                    byte[] bytes = entry.getValue();
                    return new ArtifactFile(path, bytes.length, sha256(bytes), mediaType(path),
                            Base64.getEncoder().encodeToString(bytes));
                }).toList();
        try {
            byte[] canonical = objectMapper.writeValueAsBytes(new ArtifactEnvelope(files));
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
                gzip.write(canonical);
            }
            String manifest = objectMapper.writeValueAsString(files.stream().map(file ->
                    new ManifestFile(file.path(), file.size(), file.sha256(), file.mediaType())).toList());
            return new Artifact(sha256(canonical), ENCODING, compressed.toByteArray(), manifest,
                    total, compressed.size(), files.size(), Instant.now());
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "构造 Hub 不可变制品失败", Map.of(), exception);
        }
    }

    private ArtifactEnvelope decode(Artifact artifact) {
        if (!ENCODING.equals(artifact.encoding())) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "Hub 制品编码不受支持");
        }
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(artifact.content()))) {
            byte[] canonical = gzip.readNBytes(MAX_CANONICAL_BYTES + 1);
            if (canonical.length > MAX_CANONICAL_BYTES || gzip.read() != -1) {
                throw new PlatformException(ErrorCode.INTERNAL_ERROR, "Hub 制品解压后超过安全限制");
            }
            if (!artifact.sha256().equals(sha256(canonical))) {
                throw new PlatformException(ErrorCode.INTERNAL_ERROR, "Hub 制品完整性校验失败");
            }
            return objectMapper.readValue(canonical, ArtifactEnvelope.class);
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "读取 Hub 不可变制品失败", Map.of(), exception);
        }
    }

    private List<ImportRequest> collectImports(Asset rootAsset, String rootAlias) {
        List<ImportRequest> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        ArrayDeque<ImportRequest> queue = new ArrayDeque<>();
        queue.add(importRequest(rootAsset, rootAlias));
        while (!queue.isEmpty()) {
            ImportRequest request = queue.removeFirst();
            if (!visited.add(request.asset().assetId())) continue;
            result.add(request);
            repository.findDependencies(request.revision().revisionId()).forEach(dependency -> {
                Asset asset = requireAsset(dependency.dependencyAssetId());
                Revision exact = requireRevision(dependency.dependencyRevisionId());
                queue.add(new ImportRequest(asset, exact, asset.technicalId(),
                        importFiles(asset, exact, asset.technicalId()), null));
            });
        }
        return result.stream().map(request -> new ImportRequest(request.asset(), request.revision(), request.alias(),
                request.files(), digestFiles(request.files()))).toList();
    }

    private ImportRequest importRequest(Asset asset, String alias) {
        Revision revision = requireRevision(asset.latestPublishedRevisionId());
        return new ImportRequest(asset, revision, alias, importFiles(asset, revision, alias), null);
    }

    private Map<String, byte[]> importFiles(Asset asset, Revision revision, String alias) {
        Map<String, byte[]> files = decode(requireArtifact(revision)).files().stream()
                .collect(Collectors.toMap(ArtifactFile::path,
                        file -> Base64.getDecoder().decode(file.contentBase64()), (a, b) -> b, LinkedHashMap::new));
        if (asset.assetType() == AssetType.AGENT) {
            return Map.of(targetPath(asset, alias), files.get(AGENT_FILE));
        }
        Map<String, byte[]> patched = new LinkedHashMap<>();
        files.forEach((relative, bytes) -> patched.put(targetPath(asset, alias) + relative,
                "SKILL.md".equals(relative) ? patchSkillName(bytes, alias) : bytes));
        return patched;
    }

    private List<Path> materializeImports(PersonalWorkspace personal, List<ImportRequest> imports) {
        Path root = pathResolver.resolve(personal.workspaceRootPath()).toAbsolutePath().normalize();
        List<Path> targets = imports.stream().flatMap(request -> request.files().keySet().stream())
                .map(root::resolve).map(Path::normalize).toList();
        if (new HashSet<>(targets).size() != targets.size()) {
            throw new PlatformException(ErrorCode.CONFLICT, "Hub 资产及其依赖存在重复目标路径，请调整英文技术 ID");
        }
        for (ImportRequest request : imports) {
            Path assetRoot = root.resolve(targetPath(request.asset(), request.alias())).normalize();
            if (Files.exists(assetRoot, LinkOption.NOFOLLOW_LINKS)) {
                throw new PlatformException(ErrorCode.CONFLICT, "目标 Agent/Skill 已存在，请更换英文技术 ID",
                        Map.of("path", root.relativize(assetRoot).toString().replace('\\', '/')));
            }
        }
        for (Path target : targets) {
            if (!target.startsWith(root.resolve(".opencode").normalize())) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "Hub 引用目标路径越界");
            }
        }
        List<Path> created = new ArrayList<>();
        try {
            for (ImportRequest request : imports) {
                for (var entry : request.files().entrySet()) {
                    Path target = root.resolve(entry.getKey()).normalize();
                    Files.createDirectories(target.getParent());
                    Path temp = Files.createTempFile(target.getParent(), ".hub-", ".tmp");
                    Files.write(temp, entry.getValue());
                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
                    created.add(target);
                }
            }
            return created;
        } catch (Exception exception) {
            rollbackCreated(created);
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "写入 Hub 引用失败", Map.of(), exception);
        }
    }

    /** 取消待推送的记录可原位恢复；其它状态仍禁止重复引用，确保同一资产只有一条有效关系。 */
    private Map<String, Reference> reusablePendingRemovals(PersonalWorkspace personal, List<ImportRequest> imports) {
        Map<String, Reference> reusable = new HashMap<>();
        for (ImportRequest request : imports) {
            List<Reference> existing = repository.findReferencesByTargetAsset(
                    personal.applicationWorkspaceId().value(), request.asset().assetId());
            if (existing.isEmpty()) {
                continue;
            }
            if (existing.size() == 1 && "PENDING_REMOVE".equals(existing.getFirst().status())) {
                reusable.put(request.asset().assetId(), existing.getFirst());
            } else {
                throw new PlatformException(ErrorCode.CONFLICT, "当前应用工作空间已引用该 Agent/Skill",
                        Map.of("assetId", request.asset().assetId(), "targetPath", existing.getFirst().targetPath()));
            }
        }
        return Map.copyOf(reusable);
    }

    private List<ConflictFile> mergeFiles(Map<String, byte[]> base, Map<String, byte[]> current, Map<String, byte[]> incoming) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        paths.addAll(base.keySet()); paths.addAll(current.keySet()); paths.addAll(incoming.keySet());
        List<ConflictFile> result = new ArrayList<>();
        for (String path : paths.stream().sorted().toList()) {
            byte[] baseBytes = base.get(path); byte[] currentBytes = current.get(path); byte[] incomingBytes = incoming.get(path);
            if (java.util.Arrays.equals(currentBytes, baseBytes)) {
                result.add(conflict(path, baseBytes, currentBytes, incomingBytes, incomingBytes, false, "AUTO_INCOMING"));
                continue;
            }
            if (java.util.Arrays.equals(incomingBytes, baseBytes) || java.util.Arrays.equals(currentBytes, incomingBytes)) {
                result.add(conflict(path, baseBytes, currentBytes, incomingBytes, currentBytes, false, "AUTO_CURRENT"));
                continue;
            }
            if (baseBytes != null && currentBytes != null && incomingBytes != null
                    && isUtf8(baseBytes) && isUtf8(currentBytes) && isUtf8(incomingBytes)) {
                var merged = git.mergeText(utf8(baseBytes, path), utf8(currentBytes, path), utf8(incomingBytes, path));
                result.add(new ConflictFile(path, "TEXT", utf8(baseBytes, path), utf8(currentBytes, path),
                        utf8(incomingBytes, path), merged.content(), merged.conflicted(),
                        merged.conflicted() ? null : "AUTO_MERGED"));
            } else {
                result.add(conflict(path, baseBytes, currentBytes, incomingBytes, currentBytes, true, null));
            }
        }
        return result;
    }

    private ConflictFile conflict(String path, byte[] base, byte[] current, byte[] incoming, byte[] result,
                                  boolean conflicted, String resolution) {
        boolean text = (base == null || isUtf8(base)) && (current == null || isUtf8(current)) && (incoming == null || isUtf8(incoming));
        return new ConflictFile(path, text ? "TEXT" : "BINARY", conflictValue(base, text, path),
                conflictValue(current, text, path), conflictValue(incoming, text, path),
                conflictValue(result, text, path), conflicted, resolution);
    }

    private WriteBackup stageMergedFiles(PersonalWorkspace personal, Asset asset, String alias, List<ConflictFile> files) {
        Path root = pathResolver.resolve(personal.workspaceRootPath()).toAbsolutePath().normalize();
        Path assetRoot = root.resolve(targetPath(asset, alias)).normalize();
        Path backup = root.resolve(".opencode/.hub-backup-" + UUID.randomUUID()).normalize();
        boolean existed = Files.exists(assetRoot, LinkOption.NOFOLLOW_LINKS);
        try {
            if (existed) Files.move(assetRoot, backup, StandardCopyOption.ATOMIC_MOVE);
            for (ConflictFile file : files) {
                if (file.resultContent() == null) continue;
                Path target = root.resolve(file.path()).normalize();
                if (!target.startsWith(root.resolve(".opencode").normalize())) {
                    throw new PlatformException(ErrorCode.FORBIDDEN, "Hub 更新目标路径越界");
                }
                Files.createDirectories(target.getParent());
                Files.write(target, conflictResultBytes(file));
            }
            return new WriteBackup(assetRoot, backup, existed);
        } catch (Exception exception) {
            deleteRecursively(assetRoot);
            try {
                if (existed && Files.exists(backup)) Files.move(backup, assetRoot, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception rollback) {
                exception.addSuppressed(rollback);
            }
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "写入 Hub 更新结果失败", Map.of(), exception);
        }
    }

    private WriteBackup stageRemoval(PersonalWorkspace personal, Reference reference) {
        Path root = pathResolver.resolve(personal.workspaceRootPath()).toAbsolutePath().normalize();
        Path opencodeRoot = root.resolve(".opencode").normalize();
        Path assetRoot = root.resolve(reference.targetPath()).normalize();
        if (!assetRoot.startsWith(opencodeRoot) || assetRoot.equals(opencodeRoot)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "Hub 取消引用目标路径越界");
        }
        Path backup = opencodeRoot.resolve(".hub-backup-" + UUID.randomUUID()).normalize();
        boolean existed = Files.exists(assetRoot, LinkOption.NOFOLLOW_LINKS);
        try {
            if (existed) {
                Files.createDirectories(opencodeRoot);
                Files.move(assetRoot, backup, StandardCopyOption.ATOMIC_MOVE);
            }
            return new WriteBackup(assetRoot, backup, existed);
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "移除 Hub 引用文件失败", Map.of(), exception);
        }
    }

    private void commitWrite(WriteBackup backup) {
        deleteRecursively(backup.backupPath());
    }

    private void rollbackWrite(WriteBackup backup) {
        deleteRecursively(backup.assetRoot());
        if (!backup.existed() || !Files.exists(backup.backupPath(), LinkOption.NOFOLLOW_LINKS)) return;
        try {
            Files.move(backup.backupPath(), backup.assetRoot(), StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "Hub 更新数据库失败且工作树回滚失败", Map.of(), exception);
        }
    }

    private Map<String, byte[]> readCurrentFiles(PersonalWorkspace personal, Asset asset, String alias) {
        Path workspaceRoot = pathResolver.resolve(personal.workspaceRootPath()).toAbsolutePath().normalize();
        String relativeRoot = targetPath(asset, alias);
        Path root = workspaceRoot.resolve(relativeRoot).normalize();
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return Map.of();
        try {
            if (asset.assetType() == AssetType.AGENT) return Map.of(relativeRoot, Files.readAllBytes(root));
            try (var paths = Files.walk(root)) {
                return paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .sorted().collect(Collectors.toMap(
                                path -> relativeRoot + root.relativize(path).toString().replace('\\', '/'),
                                path -> readBytes(path), (a, b) -> b, LinkedHashMap::new));
            }
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "读取当前 Hub 引用失败", Map.of(), exception);
        }
    }

    private SnapshotReconciliation reconcilePushedReferences(
            String targetApplicationWorkspaceId, Map<String, byte[]> blobs,
            String prefix, List<PushedAsset> scannedAssets) {
        List<PushReferenceDecision> decisions = new ArrayList<>();
        Set<String> excludedIdentities = new HashSet<>();
        for (Reference reference : repository.findPendingReferences(targetApplicationWorkspaceId)) {
            String gitPath = prefix + reference.targetPath();
            Map<String, byte[]> committed = new LinkedHashMap<>();
            if (reference.targetPath().endsWith(".md")) {
                if (blobs.containsKey(gitPath)) committed.put(reference.targetPath(), blobs.get(gitPath));
            } else {
                blobs.forEach((path, bytes) -> {
                    if (path.startsWith(gitPath)) committed.put(path.substring(prefix.length()), bytes);
                });
            }
            if ("PENDING_REMOVE".equals(reference.status())) {
                if (committed.isEmpty()) {
                    decisions.add(new PushReferenceDecision(reference.referenceId(), PushReferenceAction.REMOVE,
                            null, null, null, null));
                }
                continue;
            }
            if (committed.isEmpty() || reference.pendingContentSha256() == null) continue;
            Asset sourceAsset = requireAsset(reference.assetId());
            boolean exact = reference.pendingContentSha256().equals(digestFiles(committed));
            if (exact) {
                decisions.add(new PushReferenceDecision(reference.referenceId(), PushReferenceAction.KEEP_SOURCE,
                        sourceAsset.assetType(), reference.aliasTechnicalId(), null, null));
                if (sourceAsset.sourceKind() == SourceKind.SKILLHUB) {
                    excludedIdentities.add(sourceAsset.assetType().name() + ":" + reference.aliasTechnicalId());
                }
            } else if (sourceAsset.sourceKind() == SourceKind.SKILLHUB) {
                decisions.add(new PushReferenceDecision(reference.referenceId(), PushReferenceAction.FORK_TO_PLATFORM,
                        sourceAsset.assetType(), reference.aliasTechnicalId(), sourceAsset.assetId(),
                        reference.pendingRevisionId()));
            }
        }
        List<PushedAsset> filtered = scannedAssets.stream()
                .filter(asset -> !excludedIdentities.contains(asset.assetType().name() + ":" + asset.technicalId()))
                .toList();
        return new SnapshotReconciliation(List.copyOf(filtered), List.copyOf(decisions));
    }

    private void ensureAcyclic(String rootAssetId, List<Dependency> nextDependencies) {
        Map<String, List<String>> override = Map.of(rootAssetId,
                nextDependencies.stream().map(Dependency::dependencyAssetId).toList());
        detectCycle(rootAssetId, new HashSet<>(), new HashSet<>(), override);
    }

    private void detectCycle(String assetId, Set<String> visiting, Set<String> visited,
                             Map<String, List<String>> override) {
        if (!visiting.add(assetId)) {
            throw new PlatformException(ErrorCode.CONFLICT, "Hub 发布依赖不能形成循环", Map.of("assetId", assetId));
        }
        if (visited.contains(assetId)) { visiting.remove(assetId); return; }
        Asset asset = requireAsset(assetId);
        List<String> dependencies = override.getOrDefault(assetId,
                asset.latestPublishedRevisionId() == null ? List.of() : repository.findDependencies(
                        asset.latestPublishedRevisionId()).stream().map(Dependency::dependencyAssetId).toList());
        dependencies.forEach(dependency -> detectCycle(dependency, visiting, visited, override));
        visiting.remove(assetId); visited.add(assetId);
    }

    private List<AgentSkillHubResponses.DependencyResponse> dependencies(String revisionId) {
        return repository.findDependencies(revisionId).stream().map(dependency -> {
            Asset asset = requireAsset(dependency.dependencyAssetId());
            Revision revision = requireRevision(dependency.dependencyRevisionId());
            return new AgentSkillHubResponses.DependencyResponse(asset.assetId(), revision.revisionId(),
                    asset.assetType().name(), asset.technicalId(), revision.displayName());
        }).toList();
    }

    /** 公共 Agent/Skill 都读取定时任务固化的数据库快照；Skill 分类来自跨修订保留的独立记录。 */
    private List<BuiltinRevision> publicBuiltinSnapshots(
            AssetType type, String keyword, SkillCategory category, SkillSubcategory subcategory) {
        String lowered = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
        return repository.listCurrentBuiltinRevisions().stream()
                .filter(snapshot -> type == null || snapshot.assetType() == type)
                .filter(snapshot -> category == null || snapshot.skillCategory() == category)
                .filter(snapshot -> subcategory == null || snapshot.skillSubcategory() == subcategory)
                .filter(snapshot -> lowered == null || java.util.stream.Stream.of(
                                snapshot.technicalId(), snapshot.displayName(), snapshot.displayNameEn(),
                                snapshot.description(), "平台内置", "公共配置")
                        .filter(Objects::nonNull)
                        .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(lowered)))
                .toList();
    }

    private BuiltinRevision requireBuiltinAsset(String assetId, String revisionId) {
        BuiltinIdentity identity = parseBuiltinAssetId(assetId);
        if (revisionId != null && !revisionId.isBlank()) {
            BuiltinRevision snapshot = requireBuiltinRevision(revisionId.trim());
            if (snapshot.assetType() != identity.type() || !snapshot.technicalId().equals(identity.technicalId())) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "平台内置修订不属于指定资产");
            }
            return snapshot;
        }
        return repository.findCurrentBuiltinRevision(assetId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "平台内置资产不存在"));
    }

    private BuiltinRevision requireBuiltinRevision(String revisionId) {
        if (!isBuiltinRevisionId(revisionId)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "平台内置修订 ID 无效");
        }
        return repository.findBuiltinRevision(revisionId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "平台内置修订不存在"));
    }

    private Artifact requireBuiltinArtifact(BuiltinRevision revision) {
        return repository.findArtifact(revision.artifactSha256())
                .orElseThrow(() -> new PlatformException(ErrorCode.INTERNAL_ERROR, "平台内置制品不存在"));
    }

    /** 从一个不会变化的 commit 构造完整公共快照；HEAD 后续移动不影响本次读取的一致性。 */
    private BuiltinSnapshot scanPublicBuiltinSnapshot(Path repoRoot, String commit, Instant indexedAt) {
        List<String> paths = git.listFilesAtCommit(repoRoot, commit, "opencode");
        List<BuiltinPushedRevision> revisions = new ArrayList<>();
        paths.stream()
                .filter(path -> path.startsWith("opencode/agents/"))
                .filter(path -> path.substring("opencode/agents/".length()).matches("[^/]+\\.md"))
                .forEach(path -> {
                    String technicalId = normalizeTechnicalId(
                            path.substring("opencode/agents/".length(), path.length() - 3));
                    revisions.add(builtinPushedRevision(
                            commit, AssetType.AGENT, technicalId,
                            Map.of(AGENT_FILE, git.readFileAtCommit(repoRoot, commit, path)), indexedAt));
                });
        Map<String, Map<String, byte[]>> skills = new LinkedHashMap<>();
        for (String path : paths) {
            if (!path.startsWith("opencode/skills/")) {
                continue;
            }
            String relative = path.substring("opencode/skills/".length());
            int slash = relative.indexOf('/');
            if (slash <= 0 || slash == relative.length() - 1) {
                continue;
            }
            String technicalId = normalizeTechnicalId(relative.substring(0, slash));
            skills.computeIfAbsent(technicalId, ignored -> new LinkedHashMap<>())
                    .put(relative.substring(slash + 1), git.readFileAtCommit(repoRoot, commit, path));
        }
        skills.forEach((technicalId, files) -> {
            if (files.containsKey("SKILL.md")) {
                revisions.add(builtinPushedRevision(commit, AssetType.SKILL, technicalId, files, indexedAt));
            }
        });
        return new BuiltinSnapshot(commit, indexedAt, List.copyOf(revisions));
    }

    private BuiltinPushedRevision builtinPushedRevision(
            String commit, AssetType type, String technicalId, Map<String, byte[]> files, Instant pushedAt) {
        PushedAsset pushed = pushedAsset(type, technicalId, files);
        BuiltinRevision revision = new BuiltinRevision(
                builtinRevisionId(commit, type, technicalId), builtinAssetId(type, technicalId), type, technicalId,
                commit, pushed.artifact().sha256(), pushed.contentSha256(), pushed.displayName(),
                pushed.displayNameEn(), pushed.description(), SkillCategory.OTHER, null, pushedAt);
        return new BuiltinPushedRevision(revision, pushed.artifact());
    }

    private Path publicConfigGitRoot() {
        return commonParameterValues.resolvedValue(PUBLIC_CONFIG_GIT_ROOT)
                .map(String::trim).filter(value -> !value.isEmpty()).map(Path::of).map(Path::normalize).orElse(null);
    }

    private boolean isBuiltinAssetId(String assetId) {
        return assetId != null && assetId.startsWith(BUILTIN_ASSET_PREFIX)
                && !assetId.startsWith(BUILTIN_REVISION_PREFIX);
    }

    private boolean isBuiltinRevisionId(String revisionId) {
        return revisionId != null && revisionId.startsWith(BUILTIN_REVISION_PREFIX);
    }

    private BuiltinIdentity parseBuiltinAssetId(String assetId) {
        if (!isBuiltinAssetId(assetId)) throw new PlatformException(ErrorCode.VALIDATION_ERROR, "平台内置资产 ID 无效");
        String encoded = assetId.substring(BUILTIN_ASSET_PREFIX.length());
        int separator = encoded.indexOf('_');
        if (separator <= 0) throw new PlatformException(ErrorCode.VALIDATION_ERROR, "平台内置资产 ID 无效");
        return new BuiltinIdentity(parseType(encoded.substring(0, separator)),
                decodeBuiltinTechnicalId(encoded.substring(separator + 1)));
    }

    private String builtinAssetId(AssetType type, String technicalId) {
        return BUILTIN_ASSET_PREFIX + type.name() + "_" + encodeBuiltinTechnicalId(technicalId);
    }

    private String builtinRevisionId(String commit, AssetType type, String technicalId) {
        return BUILTIN_REVISION_PREFIX + commit + "_" + type.name() + "_" + encodeBuiltinTechnicalId(technicalId);
    }

    private String encodeBuiltinTechnicalId(String technicalId) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(technicalId.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeBuiltinTechnicalId(String encoded) {
        try {
            return normalizeTechnicalId(new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "平台内置资产 ID 无效", Map.of(), exception);
        }
    }

    private AgentSkillHubResponses.AssetResponse response(BuiltinRevision snapshot) {
        return new AgentSkillHubResponses.AssetResponse(snapshot.assetId(), snapshot.assetType().name(),
                snapshot.technicalId(), snapshot.displayName(), snapshot.displayNameEn(), snapshot.description(),
                snapshot.skillCategory().name(), snapshot.skillSubcategory() == null
                        ? null : snapshot.skillSubcategory().name(),
                "platform", "平台内置", "public", "公共配置", snapshot.revisionId(), snapshot.revisionId(),
                true, true, false, false, false, null, 0, snapshot.pushedAt(), snapshot.pushedAt(),
                SourceKind.PLATFORM.name(), true, true, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    private AgentSkillHubResponses.AssetResponse response(AssetSummary summary) {
        Revision pushed = summary.pushedRevision();
        Revision published = summary.publishedRevision();
        Revision display = published == null ? pushed : published;
        return new AgentSkillHubResponses.AssetResponse(summary.asset().assetId(), summary.asset().assetType().name(),
                summary.asset().technicalId(), firstText(display == null ? null : display.displayName(),
                        summary.asset().catalogDisplayName(), summary.asset().technicalId()),
                display == null ? summary.asset().technicalId() : display.displayNameEn(),
                firstText(display == null ? null : display.description(), summary.asset().catalogDescription()),
                summary.asset().skillCategory().name(), summary.asset().skillSubcategory() == null
                        ? null : summary.asset().skillSubcategory().name(),
                summary.asset().sourceAppId(), firstText(summary.sourceAppName(),
                        summary.asset().sourceKind() == SourceKind.SKILLHUB ? "SkillHub" : null),
                summary.asset().sourceApplicationWorkspaceId(), firstText(summary.sourceWorkspaceName(),
                        summary.asset().sourceKind() == SourceKind.SKILLHUB ? "外部能力目录" : null),
                pushed == null ? null : pushed.revisionId(), published == null ? null : published.revisionId(),
                published != null, false, summary.updateAvailable(), isEffectiveReference(summary.referenceStatus()),
                pushed != null && pushed.deleted(), summary.referenceStatus(), summary.referenceCount(),
                pushed == null ? summary.asset().updatedAt() : pushed.pushedAt(),
                published == null ? null : published.publishedAt(),
                summary.asset().sourceKind().name(), summary.asset().sourceAvailable(), pushed != null,
                summary.asset().externalSkillId(), summary.asset().externalVersion(),
                summary.asset().externalSource(), summary.asset().externalTag(), summary.asset().externalPhase(),
                summary.asset().externalPhaseName(), summary.asset().externalContributor(),
                summary.externalContributorName(),
                summary.asset().externalDownloadCount(), summary.asset().forkedFromAssetId(),
                summary.asset().forkedFromRevisionId());
    }

    private List<AgentSkillHubResponses.ReferenceConsumerResponse> consumers(String assetId, UserId userId) {
        return repository.listReferenceConsumers(assetId, userId.value()).stream().map(consumer -> {
            Reference reference = consumer.reference();
            return new AgentSkillHubResponses.ReferenceConsumerResponse(
                    reference.referenceId(), reference.targetAppId(), consumer.targetAppName(),
                    reference.targetApplicationWorkspaceId(), consumer.targetWorkspaceName(),
                    reference.aliasTechnicalId(), reference.targetPath(), reference.status(), reference.updatedAt());
        }).toList();
    }

    private boolean isEffectiveReference(String status) {
        return "ACTIVE".equals(status) || "UPDATE_CONFLICT".equals(status);
    }

    private AgentSkillHubResponses.UpdateResponse response(ReferenceUpdate update) {
        return new AgentSkillHubResponses.UpdateResponse(update.reference().referenceId(), update.asset().assetId(),
                update.asset().technicalId(), update.latestRevision().displayName(), update.sourceAppName(),
                update.sourceWorkspaceName(), update.reference().activeRevisionId(), update.latestRevision().revisionId(),
                update.asset().sourceKind() == SourceKind.SKILLHUB ? update.asset().externalVersion() : null,
                update.asset().sourceAvailable(), update.reference().status(), update.reference().targetPath(),
                update.latestRevision().publishedAt());
    }

    private AgentSkillHubResponses.ReferenceResponse referenceResponse(Reference reference, boolean reload, String message) {
        return new AgentSkillHubResponses.ReferenceResponse(reference.referenceId(), reference.assetId(),
                reference.targetPath(), reference.aliasTechnicalId(), reference.activeRevisionId(),
                reference.pendingRevisionId(), reference.status(), reload, message);
    }

    private AgentSkillHubResponses.UpdateOperationResponse operationResponse(
            UpdateOperation operation, List<ConflictFile> files, boolean changed) {
        return new AgentSkillHubResponses.UpdateOperationResponse(operation.operationId(), operation.referenceId(),
                operation.status(), files.stream().map(file -> new AgentSkillHubResponses.ConflictFileResponse(
                        file.path(), file.kind(), publicConflictValue(file.baseContent(), file.kind()),
                        publicConflictValue(file.currentContent(), file.kind()),
                        publicConflictValue(file.incomingContent(), file.kind()),
                        publicConflictValue(file.resultContent(), file.kind()), file.conflicted(), file.resolution())).toList(), changed);
    }

    private PersonalWorkspace requireOwnedPersonal(String runtimeWorkspaceId, UserId userId) {
        PersonalWorkspace personal = managedWorkspaceRepository.findPersonalWorkspaceByRuntimeWorkspace(
                        new WorkspaceId(runtimeWorkspaceId))
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "请先切换到有效的个人工作区"));
        if (!personal.userId().equals(userId)) throw new PlatformException(ErrorCode.FORBIDDEN, "只能修改自己的个人工作区");
        requireMember(personal.appId().value(), userId);
        return personal;
    }

    private String targetApplicationWorkspaceId(String runtimeWorkspaceId, UserId userId) {
        if (runtimeWorkspaceId == null || runtimeWorkspaceId.isBlank()) return null;
        return requireOwnedPersonal(runtimeWorkspaceId.trim(), userId).applicationWorkspaceId().value();
    }

    private void requireReferenceTarget(Reference reference, PersonalWorkspace personal) {
        if (!reference.targetAppId().equals(personal.appId().value())
                || !reference.targetApplicationWorkspaceId().equals(personal.applicationWorkspaceId().value())) {
            throw new PlatformException(ErrorCode.CONFLICT, "引用不属于当前应用工作空间");
        }
    }

    private void requireMember(String appId, UserId userId) {
        if (!configurationRepository.isActiveMember(new ApplicationId(appId), userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "不是该应用的有效成员");
        }
    }

    private Asset requireAsset(String assetId) {
        if (assetId == null || assetId.isBlank()) throw new PlatformException(ErrorCode.VALIDATION_ERROR, "assetId 不能为空");
        return repository.findAsset(assetId.trim()).orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Hub 资产不存在"));
    }

    private Revision requireRevision(String revisionId) {
        if (revisionId == null || revisionId.isBlank()) throw new PlatformException(ErrorCode.CONFLICT, "Hub 修订不存在");
        return repository.findRevision(revisionId).orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Hub 修订不存在"));
    }

    private Artifact requireArtifact(Revision revision) {
        if (revision.deleted() || revision.artifactSha256() == null) throw new PlatformException(ErrorCode.NOT_FOUND, "Hub 修订已删除");
        return repository.findArtifact(revision.artifactSha256())
                .orElseThrow(() -> new PlatformException(ErrorCode.INTERNAL_ERROR, "Hub 制品不存在"));
    }

    private Reference requireReference(String referenceId) {
        return repository.findReference(referenceId).orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Hub 引用不存在"));
    }

    private UpdateOperation requireOperation(String operationId, UserId userId) {
        UpdateOperation operation = repository.findUpdateOperation(operationId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Hub 更新操作不存在"));
        if (!operation.createdByUserId().equals(userId.value())) throw new PlatformException(ErrorCode.FORBIDDEN, "无权访问该更新操作");
        return operation;
    }

    private AssetType parseType(String type) {
        try { return AssetType.valueOf(type.trim().toUpperCase(Locale.ROOT)); }
        catch (Exception exception) { throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Hub 资产类型无效"); }
    }

    private SkillCategory parseCategory(String category) {
        try { return SkillCategory.valueOf(category.trim().toUpperCase(Locale.ROOT)); }
        catch (Exception exception) { throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Skill 事项分类无效"); }
    }

    private SkillSubcategory parseSubcategory(String subcategory) {
        try { return SkillSubcategory.valueOf(subcategory.trim().toUpperCase(Locale.ROOT)); }
        catch (Exception exception) { throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Skill 具体事项无效"); }
    }

    /** 空参数保持旧客户端语义（仅平台）；新界面显式传 ALL 才合并双来源。 */
    private SourceKind parseSourceFilter(String source) {
        if (source == null || source.isBlank()) return SourceKind.PLATFORM;
        if ("ALL".equalsIgnoreCase(source.trim())) return null;
        try {
            return SourceKind.valueOf(source.trim().toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Skill 来源筛选无效");
        }
    }

    /** 列表允许只选一级分类；一旦给出二级事项，就必须与一级分类匹配。 */
    private void validateClassificationFilter(
            AssetType assetType, SkillCategory category, SkillSubcategory subcategory) {
        if ((category != null || subcategory != null) && assetType != AssetType.SKILL) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "事项分类筛选仅适用于 Skill");
        }
        if (subcategory != null) {
            if (category == null) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "筛选具体事项时必须指定一级分类");
            }
            validateSubcategoryBelongsToCategory(category, subcategory);
        }
    }

    /** 持久化分类必须落到唯一有效的末级事项，WORKER/OTHER 当前无二级事项。 */
    private void validateExactClassification(SkillCategory category, SkillSubcategory subcategory) {
        if (category == SkillCategory.TEST || category == SkillCategory.CODE) {
            if (subcategory == null) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "该一级分类必须选择具体事项");
            }
            validateSubcategoryBelongsToCategory(category, subcategory);
            return;
        }
        if (subcategory != null) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "该一级分类暂不支持二级事项");
        }
    }

    private void validateSubcategoryBelongsToCategory(
            SkillCategory category, SkillSubcategory subcategory) {
        boolean valid = switch (category) {
            case TEST -> subcategory == SkillSubcategory.TEST_DESIGN
                    || subcategory == SkillSubcategory.TEST_DATA_CONSTRUCTION
                    || subcategory == SkillSubcategory.TEST_EXECUTION
                    || subcategory == SkillSubcategory.TEST_ANALYSIS;
            case CODE -> subcategory == SkillSubcategory.WHITE_BOX_ANALYSIS;
            case WORKER, OTHER -> false;
        };
        if (!valid) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "具体事项与一级分类不匹配");
        }
    }

    private String repoRelativePrefix(Path repoRoot, Path workspaceRoot) {
        Path repo = repoRoot.toAbsolutePath().normalize();
        Path workspace = workspaceRoot.toAbsolutePath().normalize();
        if (!workspace.startsWith(repo)) throw new PlatformException(ErrorCode.INTERNAL_ERROR, "应用工作空间不在 Git 仓库内");
        String relative = repo.relativize(workspace).toString().replace('\\', '/');
        return relative.isEmpty() ? "" : relative + "/";
    }

    private String targetPath(Asset asset, String alias) {
        return asset.assetType() == AssetType.AGENT ? ".opencode/agents/" + alias + ".md" : ".opencode/skills/" + alias + "/";
    }

    private String normalizeTechnicalId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!isValidTechnicalId(normalized)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "英文技术 ID 格式无效");
        }
        return normalized;
    }

    private boolean isValidTechnicalId(String value) {
        return value != null && TECHNICAL_ID_PATTERN.matcher(value).matches();
    }

    private String normalizeArtifactPath(String value) {
        String normalized = value == null ? "" : value.replace('\\', '/').trim();
        boolean unsafeSegment = java.util.Arrays.stream(normalized.split("/", -1))
                .anyMatch(segment -> segment.isBlank() || ".".equals(segment) || "..".equals(segment));
        if (normalized.isEmpty() || normalized.startsWith("/") || unsafeSegment || normalized.indexOf('\0') >= 0) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Hub 制品路径无效");
        }
        return normalized;
    }

    private byte[] patchSkillName(byte[] bytes, String alias) {
        String content = utf8(bytes, "SKILL.md");
        String patched = content.replaceFirst("(?m)^name\\s*:\\s*.*$", "name: " + alias);
        if (patched.equals(content)) {
            patched = content.startsWith("---") ? content.replaceFirst("---\\R", "---\nname: " + alias + "\n")
                    : "---\nname: " + alias + "\n---\n" + content;
        }
        return patched.getBytes(StandardCharsets.UTF_8);
    }

    private String digestFiles(Map<String, byte[]> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                digest.update(entry.getKey().getBytes(StandardCharsets.UTF_8)); digest.update((byte) 0);
                digest.update(entry.getValue()); digest.update((byte) 0);
            });
            return hex(digest.digest());
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private String sha256(byte[] bytes) {
        try { return hex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value));
        return result.toString();
    }

    private boolean isUtf8(byte[] bytes) {
        try { StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)); return true; }
        catch (CharacterCodingException exception) { return false; }
    }

    private String utf8(byte[] bytes, String path) {
        if (bytes == null) return null;
        if (!isUtf8(bytes)) throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Hub 文件不是 UTF-8 文本", Map.of("path", path));
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private String nullableUtf8(byte[] bytes, String path) { return bytes == null ? null : utf8(bytes, path); }
    private String conflictValue(byte[] bytes, boolean text, String path) {
        if (bytes == null) return null;
        return text ? utf8(bytes, path) : "base64:" + Base64.getEncoder().encodeToString(bytes);
    }
    private String publicConflictValue(String value, String kind) {
        return "BINARY".equals(kind) ? null : value;
    }
    private byte[] conflictResultBytes(ConflictFile file) {
        return conflictBytes(file.resultContent(), file.kind());
    }
    private byte[] conflictBytes(String value, String kind) {
        if (value == null) return new byte[0];
        if ("BINARY".equals(kind) && value.startsWith("base64:")) {
            return Base64.getDecoder().decode(value.substring("base64:".length()));
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }
    private String mediaType(String path) { return path.toLowerCase(Locale.ROOT).endsWith(".md") ? "text/markdown" : "text/plain"; }
    private String firstText(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }
    private String join(String left, String right) { return (left == null ? "" : left) + (right == null ? "" : right); }
    private byte[] readBytes(Path path) { try { return Files.readAllBytes(path); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String writeJson(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private List<ConflictFile> readConflicts(String json) { try { return objectMapper.readValue(json, new TypeReference<>() {}); } catch (Exception e) { throw new PlatformException(ErrorCode.INTERNAL_ERROR, "Hub 更新操作损坏", Map.of(), e); } }
    private static String id(String prefix) { return prefix + UUID.randomUUID().toString().replace("-", ""); }

    private void rollbackCreated(List<Path> paths) { paths.stream().sorted(Comparator.reverseOrder()).forEach(this::deleteRecursively); }
    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(path)) { paths.sorted(Comparator.reverseOrder()).forEach(item -> { try { Files.deleteIfExists(item); } catch (Exception ignored) { } }); }
        catch (Exception ignored) { }
    }

    private record ManifestFile(String path, long size, String sha256, String mediaType) { }
    private record ArtifactEnvelope(List<ArtifactFile> files) { }
    private record ArtifactFile(String path, long size, String sha256, String mediaType, String contentBase64) { }
    private record BuiltinIdentity(AssetType type, String technicalId) { }
    private record SnapshotReconciliation(List<PushedAsset> assets, List<PushReferenceDecision> decisions) { }
    private record WriteBackup(Path assetRoot, Path backupPath, boolean existed) { }
    private record ImportRequest(Asset asset, Revision revision, String alias, Map<String, byte[]> files, String contentSha256) { }
    private record ConflictFile(String path, String kind, String baseContent, String currentContent,
                                String incomingContent, String resultContent, boolean conflicted, String resolution) {
        ConflictFile resolved(String mode, String result) {
            return new ConflictFile(path, kind, baseContent, currentContent, incomingContent, result, false, mode);
        }
    }
}
