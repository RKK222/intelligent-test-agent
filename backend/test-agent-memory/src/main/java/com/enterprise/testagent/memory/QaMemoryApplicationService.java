package com.enterprise.testagent.memory;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.AssetType;
import com.enterprise.testagent.domain.hub.AgentSkillHubRepository;
import com.enterprise.testagent.domain.memory.MemoryEvidence;
import com.enterprise.testagent.domain.memory.MemoryId;
import com.enterprise.testagent.domain.memory.MemoryReview;
import com.enterprise.testagent.domain.memory.MemoryScope;
import com.enterprise.testagent.domain.memory.MemorySettings;
import com.enterprise.testagent.domain.memory.MemorySkillProposal;
import com.enterprise.testagent.domain.memory.MemorySkillProposalStatus;
import com.enterprise.testagent.domain.memory.MemorySource;
import com.enterprise.testagent.domain.memory.MemoryStatus;
import com.enterprise.testagent.domain.memory.MemoryUsage;
import com.enterprise.testagent.domain.memory.MemoryWhitelistEntry;
import com.enterprise.testagent.domain.memory.QaMemory;
import com.enterprise.testagent.domain.memory.QaMemoryRepository;
import com.enterprise.testagent.domain.memory.QaTaskType;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.memory.MemoryViews.AdminHealthView;
import com.enterprise.testagent.memory.MemoryViews.EmbeddingProfile;
import com.enterprise.testagent.memory.MemoryViews.MemoryEvidenceView;
import com.enterprise.testagent.memory.MemoryViews.MemoryUsageView;
import com.enterprise.testagent.memory.MemoryViews.MemoryView;
import com.enterprise.testagent.memory.MemoryViews.Page;
import com.enterprise.testagent.memory.MemoryViews.SettingsView;
import com.enterprise.testagent.memory.MemoryViews.SkillProposalView;
import com.enterprise.testagent.memory.MemoryViews.WhitelistView;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 个人/团队记忆治理、审核、使用记录和 Skill 提案的唯一业务入口。 */
@Service
public class QaMemoryApplicationService {
    private static final EmbeddingProfile LOCAL_BGE_PROFILE = new EmbeddingProfile(
            "LOCAL_BGE", "BAAI/bge-small-zh-v1.5",
            "7999e1d3359715c523056ef9478215996d62a620", 512,
            "CPU", true, "qa-memory-bge-small-zh-v1.5-r7999e1d-v1");

    private final QaMemoryRepository repository;
    private final ConfigurationManagementRepository configuration;
    private final AgentSkillHubRepository skillHubRepository;
    private final MemoryDocumentStore documents;
    private final MemorySafetyPolicy safety;
    private final QaMemoryProperties properties;
    private final Clock clock;

    @Autowired
    public QaMemoryApplicationService(
            QaMemoryRepository repository,
            ConfigurationManagementRepository configuration,
            AgentSkillHubRepository skillHubRepository,
            MemoryDocumentStore documents,
            MemorySafetyPolicy safety,
            QaMemoryProperties properties) {
        this(repository, configuration, skillHubRepository, documents, safety, properties, Clock.systemUTC());
    }

    QaMemoryApplicationService(
            QaMemoryRepository repository,
            ConfigurationManagementRepository configuration,
            AgentSkillHubRepository skillHubRepository,
            MemoryDocumentStore documents,
            MemorySafetyPolicy safety,
            QaMemoryProperties properties,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.configuration = Objects.requireNonNull(configuration);
        this.skillHubRepository = Objects.requireNonNull(skillHubRepository);
        this.documents = Objects.requireNonNull(documents);
        this.safety = Objects.requireNonNull(safety);
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
    }

    public boolean availableFor(UserId userId) {
        return properties.isEnabled() && repository.isWhitelisted(userId.value());
    }

    public Page<MemoryView> listPersonal(
            UserId userId, String applicationId, MemoryStatus status, int page, int size) {
        requireEnabled(userId);
        PageWindow window = pageWindow(page, size);
        List<QaMemory> memories = repository.listPersonal(
                userId.value(), optional(applicationId), status, window.offset(), window.size());
        return new Page<>(memories.stream().map(this::view).toList(),
                repository.countPersonal(userId.value(), optional(applicationId), status),
                window.page(), window.size());
    }

    public Page<MemoryView> listTeam(
            UserId userId, String applicationId, MemoryStatus status, int page, int size) {
        requireEnabled(userId);
        if (applicationId != null && !applicationId.isBlank()) {
            requireMember(userId, applicationId);
        }
        PageWindow window = pageWindow(page, size);
        List<QaMemory> memories = repository.listTeam(
                userId.value(), optional(applicationId), status, window.offset(), window.size());
        return new Page<>(memories.stream().map(this::view).toList(),
                repository.countTeam(userId.value(), optional(applicationId), status),
                window.page(), window.size());
    }

    public MemoryView get(UserId userId, String memoryId) {
        requireEnabled(userId);
        return view(requireAccessible(userId, new MemoryId(memoryId)));
    }

    public MemoryView createPersonal(
            UserId userId, MemoryScope scope, String applicationId, String content, List<QaTaskType> taskTypes) {
        requireEnabled(userId);
        if (scope != MemoryScope.PERSONAL_GLOBAL && scope != MemoryScope.PERSONAL_APPLICATION) {
            throw validation("个人记忆范围只支持全局或指定应用");
        }
        String appId = scope == MemoryScope.PERSONAL_APPLICATION
                ? requireApplication(applicationId) : null;
        if (appId != null) {
            requireMember(userId, appId);
        }
        String safeContent = safety.requireSafeContent(content);
        List<QaTaskType> normalizedTasks = normalizeTasks(taskTypes);
        MemoryDocumentStore.StoredDocument document = documents.add(new MemoryDocumentStore.AddDocument(
                safeContent, "platform:" + userId.value(), null, appId, normalizedTasks,
                Map.of("scope", scope.name(), "source", MemorySource.MANUAL.name())));
        Instant now = clock.instant();
        QaMemory memory = new QaMemory(
                new MemoryId(id("mem_")), document.id(), scope, userId.value(), appId,
                MemoryStatus.ACTIVE, MemorySource.MANUAL, normalizedTasks, safety.displaySummary(safeContent),
                1.0d, 0, 1, now, now, now, null, userId.value(), 0L, "SYNCED", now, now);
        try {
            repository.insertMemory(memory);
        } catch (RuntimeException failure) {
            deleteBestEffort(document.id());
            throw failure;
        }
        return MemoryView.from(memory, safeContent, true);
    }

    public MemoryView createTeamCandidate(
            UserId userId, String applicationId, String content, List<QaTaskType> taskTypes) {
        return createTeam(userId, applicationId, content, taskTypes, false);
    }

    public MemoryView createTeamDirect(
            UserId adminUserId, String applicationId, String content, List<QaTaskType> taskTypes) {
        return createTeam(adminUserId, applicationId, content, taskTypes, true);
    }

    private MemoryView createTeam(
            UserId userId, String applicationId, String content, List<QaTaskType> taskTypes, boolean direct) {
        requireEnabled(userId);
        String appId = requireApplication(applicationId);
        requireMember(userId, appId);
        String safeContent = safety.requireSafeContent(content);
        List<QaTaskType> normalizedTasks = normalizeTasks(taskTypes);
        MemorySource source = direct ? MemorySource.ADMIN_CREATED : MemorySource.TEAM_PROPOSAL;
        MemoryDocumentStore.StoredDocument document = documents.add(new MemoryDocumentStore.AddDocument(
                safeContent, null, "qa-team:" + appId, appId, normalizedTasks,
                Map.of("scope", MemoryScope.TEAM_APPLICATION.name(), "source", source.name())));
        Instant now = clock.instant();
        QaMemory memory = new QaMemory(
                new MemoryId(id("mem_")), document.id(), MemoryScope.TEAM_APPLICATION, null, appId,
                direct ? MemoryStatus.ACTIVE : MemoryStatus.CANDIDATE, source, normalizedTasks,
                safety.displaySummary(safeContent), direct ? 1.0d : 0.5d, 0, 1,
                now, now, direct ? now : null, null, userId.value(), 0L, "SYNCED", now, now);
        try {
            repository.insertMemory(memory);
            if (!direct) {
                repository.insertReview(new MemoryReview(
                        id("mrev_"), memory.memoryId(), appId, userId.value(), null,
                        "PENDING", null, now, null));
            }
        } catch (RuntimeException failure) {
            deleteBestEffort(document.id());
            throw failure;
        }
        return MemoryView.from(memory, safeContent, true);
    }

    public MemoryView update(
            UserId userId, String memoryId, String content, List<QaTaskType> taskTypes,
            long expectedVersion, boolean appAdmin) {
        requireEnabled(userId);
        QaMemory current = requireAccessible(userId, new MemoryId(memoryId));
        requireMutationAuthority(userId, current, appAdmin);
        requireVersion(current, expectedVersion);
        String safeContent = content == null ? documentContent(current) : safety.requireSafeContent(content);
        List<QaTaskType> normalizedTasks = taskTypes == null ? current.taskTypes() : normalizeTasks(taskTypes);
        if (current.mem0MemoryId() == null) {
            throw new PlatformException(ErrorCode.MEMORY_UNAVAILABLE, "记忆正文尚未同步到 Mem0");
        }
        documents.update(current.mem0MemoryId(), safeContent, Map.of(
                "scope", current.scope().name(), "taskTypes", normalizedTasks.stream().map(Enum::name).toList()));
        Instant now = clock.instant();
        QaMemory updated = copy(current, current.status(), normalizedTasks, safety.displaySummary(safeContent),
                current.confirmedAt(), current.supersededByMemoryId(), expectedVersion + 1, "SYNCED", now);
        persistExpected(updated, expectedVersion);
        return MemoryView.from(updated, safeContent, true);
    }

    public MemoryView confirmPersonal(UserId userId, String memoryId, long expectedVersion) {
        requireEnabled(userId);
        QaMemory current = requireAccessible(userId, new MemoryId(memoryId));
        if (current.scope() == MemoryScope.TEAM_APPLICATION || !userId.value().equals(current.ownerUserId())) {
            throw forbidden("只能确认自己的个人记忆");
        }
        if (current.status() != MemoryStatus.CANDIDATE
                && current.status() != MemoryStatus.PENDING_CONFIRMATION
                && current.status() != MemoryStatus.CONFLICTED
                && current.status() != MemoryStatus.PAUSED) {
            throw conflict("当前记忆状态不能确认");
        }
        requireVersion(current, expectedVersion);
        Instant now = clock.instant();
        QaMemory updated = copy(current, MemoryStatus.ACTIVE, current.taskTypes(), current.displaySummary(),
                now, current.supersededByMemoryId(), expectedVersion + 1, current.vectorSyncStatus(), now);
        persistExpected(updated, expectedVersion);
        return view(updated);
    }

    public MemoryView pausePersonal(UserId userId, String memoryId, long expectedVersion) {
        requireEnabled(userId);
        QaMemory current = requireAccessible(userId, new MemoryId(memoryId));
        if (current.scope() == MemoryScope.TEAM_APPLICATION || !userId.value().equals(current.ownerUserId())) {
            throw forbidden("只能暂停自己的个人记忆");
        }
        if (current.status() != MemoryStatus.ACTIVE) {
            throw conflict("只有生效记忆可以暂停");
        }
        requireVersion(current, expectedVersion);
        Instant now = clock.instant();
        QaMemory updated = copy(current, MemoryStatus.PAUSED, current.taskTypes(), current.displaySummary(),
                current.confirmedAt(), current.supersededByMemoryId(), expectedVersion + 1,
                current.vectorSyncStatus(), now);
        persistExpected(updated, expectedVersion);
        return view(updated);
    }

    public MemoryView archive(
            UserId userId, String memoryId, long expectedVersion, boolean appAdmin) {
        requireEnabled(userId);
        QaMemory current = requireAccessible(userId, new MemoryId(memoryId));
        requireMutationAuthority(userId, current, appAdmin);
        requireVersion(current, expectedVersion);
        Instant now = clock.instant();
        QaMemory updated = copy(current, MemoryStatus.ARCHIVED, current.taskTypes(), current.displaySummary(),
                current.confirmedAt(), current.supersededByMemoryId(), expectedVersion + 1,
                current.vectorSyncStatus(), now);
        persistExpected(updated, expectedVersion);
        return view(updated);
    }

    public MemoryView reviewTeam(
            UserId adminUserId, String memoryId, String decision, String comment, long expectedVersion) {
        requireEnabled(adminUserId);
        QaMemory current = requireAccessible(adminUserId, new MemoryId(memoryId));
        if (current.scope() != MemoryScope.TEAM_APPLICATION) {
            throw validation("只有团队候选需要审核");
        }
        requireMember(adminUserId, current.applicationId());
        requireVersion(current, expectedVersion);
        if (current.status() != MemoryStatus.CANDIDATE
                && current.status() != MemoryStatus.PENDING_CONFIRMATION
                && current.status() != MemoryStatus.CONFLICTED) {
            throw conflict("团队候选已处理或状态已变化");
        }
        String normalized = decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT);
        MemoryStatus status = switch (normalized) {
            case "APPROVE", "APPROVED" -> MemoryStatus.ACTIVE;
            case "REJECT", "REJECTED" -> MemoryStatus.REJECTED;
            default -> throw validation("审核决定只支持 APPROVE 或 REJECT");
        };
        Instant now = clock.instant();
        QaMemory updated = copy(current, status, current.taskTypes(), current.displaySummary(),
                status == MemoryStatus.ACTIVE ? now : current.confirmedAt(), current.supersededByMemoryId(),
                expectedVersion + 1, current.vectorSyncStatus(), now);
        persistExpected(updated, expectedVersion);
        repository.insertReview(new MemoryReview(
                id("mrev_"), current.memoryId(), current.applicationId(), current.createdByUserId(),
                adminUserId.value(), status == MemoryStatus.ACTIVE ? "APPROVED" : "REJECTED",
                optional(comment), now, now));
        return view(updated);
    }

    public List<MemoryEvidenceView> evidence(UserId userId, String memoryId) {
        requireEnabled(userId);
        QaMemory memory = requireAccessible(userId, new MemoryId(memoryId));
        return repository.listEvidence(memory.memoryId()).stream().map(MemoryEvidenceView::from).toList();
    }

    public List<MemoryUsageView> usage(UserId userId, List<String> runIds) {
        requireEnabled(userId);
        List<String> requested = runIds == null ? List.of() : runIds.stream().filter(Objects::nonNull).distinct().limit(200).toList();
        return repository.listUsage(userId.value(), requested).stream().map(MemoryUsageView::from).toList();
    }

    public SkillProposalView createSkillProposal(
            UserId userId, String memoryId, String applicationId, String title) {
        requireEnabled(userId);
        QaMemory memory = requireAccessible(userId, new MemoryId(memoryId));
        if (memory.status() != MemoryStatus.ACTIVE) {
            throw conflict("只有已生效记忆可以沉淀为 Skill");
        }
        String appId = requireApplication(applicationId);
        requireMember(userId, appId);
        if (memory.scope() == MemoryScope.TEAM_APPLICATION && !appId.equals(memory.applicationId())) {
            throw forbidden("团队记忆只能在所属应用中沉淀为 Skill");
        }
        String safeTitle = normalizeSkillTitle(title, memory.displaySummary());
        Instant now = clock.instant();
        MemorySkillProposal proposal = new MemorySkillProposal(
                id("msp_"), memory.memoryId(), appId, safeTitle, "",
                MemorySkillProposalStatus.PENDING_REVIEW,
                userId.value(), null, null, 0L, now, now);
        repository.insertSkillProposal(proposal);
        return SkillProposalView.from(proposal);
    }

    public Page<SkillProposalView> listSkillProposals(
            UserId userId, String applicationId, int page, int size) {
        requireEnabled(userId);
        PageWindow window = pageWindow(page, size);
        return new Page<>(repository.listSkillProposals(
                        userId.value(), optional(applicationId), window.offset(), window.size())
                        .stream().map(SkillProposalView::from).toList(),
                repository.countSkillProposals(userId.value(), optional(applicationId)),
                window.page(), window.size());
    }

    public SkillProposalView updateSkillProposal(
            UserId userId, String proposalId, String title, String skillMdDraft,
            long expectedVersion, boolean appAdmin) {
        requireEnabled(userId);
        MemorySkillProposal current = repository.findSkillProposal(proposalId)
                .orElseThrow(() -> notFound("Skill 提案不存在"));
        requireMember(userId, current.applicationId());
        if (!appAdmin && !userId.value().equals(current.createdByUserId())) {
            throw forbidden("无权编辑该 Skill 提案");
        }
        if (current.version() != expectedVersion || current.status() != MemorySkillProposalStatus.DRAFT) {
            throw conflict("Skill 提案版本或状态已变化");
        }
        String safeDraft = safety.requireSafeSkillDraft(skillMdDraft);
        Instant now = clock.instant();
        MemorySkillProposal updated = new MemorySkillProposal(
                current.proposalId(), current.memoryId(), current.applicationId(),
                normalizeSkillTitle(title, current.title()), safeDraft, current.status(),
                current.createdByUserId(), current.reviewedByUserId(), current.publishedAssetId(),
                expectedVersion + 1, current.createdAt(), now);
        if (!repository.updateSkillProposal(updated, expectedVersion)) {
            throw conflict("Skill 提案已被其他操作修改");
        }
        return SkillProposalView.from(updated);
    }

    /** APP_ADMIN 审核通过后才根据派生记忆生成可编辑草稿；不会写文件、提交 Git 或发布 Hub。 */
    public SkillProposalView reviewSkillProposal(
            UserId adminUserId, String proposalId, String decision, long expectedVersion) {
        requireEnabled(adminUserId);
        MemorySkillProposal current = requireSkillProposal(proposalId);
        requireMember(adminUserId, current.applicationId());
        requireSkillVersion(current, expectedVersion);
        if (current.status() != MemorySkillProposalStatus.PENDING_REVIEW) {
            throw conflict("Skill 提案已审核或状态已变化");
        }
        String normalized = decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT);
        MemorySkillProposalStatus status;
        String draft;
        if ("APPROVE".equals(normalized) || "APPROVED".equals(normalized)) {
            QaMemory memory = repository.findById(current.memoryId())
                    .orElseThrow(() -> notFound("提案来源记忆不存在"));
            if (memory.status() != MemoryStatus.ACTIVE) {
                throw conflict("来源记忆已不再生效，不能生成 Skill 草稿");
            }
            status = MemorySkillProposalStatus.DRAFT;
            draft = generateSkillDraft(current.title(), memory);
        } else if ("REJECT".equals(normalized) || "REJECTED".equals(normalized)) {
            status = MemorySkillProposalStatus.REJECTED;
            draft = "";
        } else {
            throw validation("审核决定只支持 APPROVE 或 REJECT");
        }
        Instant now = clock.instant();
        MemorySkillProposal updated = new MemorySkillProposal(
                current.proposalId(), current.memoryId(), current.applicationId(), current.title(), draft,
                status, current.createdByUserId(), adminUserId.value(), null,
                expectedVersion + 1, current.createdAt(), now);
        persistSkillExpected(updated, expectedVersion);
        return SkillProposalView.from(updated);
    }

    /** 既有 Skill 发布流程完成后显式关联资产；本接口本身绝不提交或发布。 */
    public SkillProposalView linkPublishedSkill(
            UserId adminUserId, String proposalId, String publishedAssetId, long expectedVersion) {
        requireEnabled(adminUserId);
        MemorySkillProposal current = requireSkillProposal(proposalId);
        requireMember(adminUserId, current.applicationId());
        requireSkillVersion(current, expectedVersion);
        if (current.status() != MemorySkillProposalStatus.DRAFT) {
            throw conflict("只有审核通过的可编辑草稿可以关联已发布 Skill");
        }
        String assetId = optional(publishedAssetId);
        if (assetId == null || assetId.length() > 128 || !assetId.matches("[A-Za-z0-9._:-]+")) {
            throw validation("publishedAssetId 格式不正确");
        }
        var asset = skillHubRepository.findAsset(assetId)
                .orElseThrow(() -> notFound("已发布 Skill 资产不存在"));
        if (asset.assetType() != AssetType.SKILL
                || !current.applicationId().equals(asset.sourceAppId())
                || asset.latestPublishedRevisionId() == null) {
            throw conflict("资产不是该应用已经发布的 Skill");
        }
        Instant now = clock.instant();
        MemorySkillProposal updated = new MemorySkillProposal(
                current.proposalId(), current.memoryId(), current.applicationId(), current.title(),
                current.skillMdDraft(), MemorySkillProposalStatus.PUBLISHED, current.createdByUserId(),
                adminUserId.value(), assetId, expectedVersion + 1, current.createdAt(), now);
        persistSkillExpected(updated, expectedVersion);
        return SkillProposalView.from(updated);
    }

    public SkillProposalView archiveSkillProposal(
            UserId userId, String proposalId, long expectedVersion, boolean appAdmin) {
        requireEnabled(userId);
        MemorySkillProposal current = requireSkillProposal(proposalId);
        requireMember(userId, current.applicationId());
        if (!appAdmin && !userId.value().equals(current.createdByUserId())) {
            throw forbidden("无权归档该 Skill 提案");
        }
        requireSkillVersion(current, expectedVersion);
        if (current.status() == MemorySkillProposalStatus.ARCHIVED) {
            throw conflict("Skill 提案已经归档");
        }
        Instant now = clock.instant();
        MemorySkillProposal updated = new MemorySkillProposal(
                current.proposalId(), current.memoryId(), current.applicationId(), current.title(),
                current.skillMdDraft(), MemorySkillProposalStatus.ARCHIVED, current.createdByUserId(),
                current.reviewedByUserId(), current.publishedAssetId(), expectedVersion + 1,
                current.createdAt(), now);
        persistSkillExpected(updated, expectedVersion);
        return SkillProposalView.from(updated);
    }

    public Page<WhitelistView> listWhitelist(int page, int size) {
        PageWindow window = pageWindow(page, size);
        return new Page<>(repository.listWhitelist(window.offset(), window.size()).stream()
                        .map(WhitelistView::from).toList(), repository.countWhitelist(), window.page(), window.size());
    }

    public WhitelistView enableUser(String userId, String adminUserId) {
        Instant now = clock.instant();
        MemoryWhitelistEntry entry = new MemoryWhitelistEntry(userId, true, adminUserId, now, now);
        repository.upsertWhitelist(entry);
        return WhitelistView.from(entry);
    }

    public void disableUser(String userId) {
        repository.removeWhitelist(userId);
    }

    public SettingsView settings() {
        return SettingsView.from(repository.loadSettings());
    }

    public SettingsView updateSettings(
            String primaryChatModelId, boolean fallbackEnabled, long expectedVersion, String adminUserId) {
        MemorySettings current = repository.loadSettings();
        if (current.version() != expectedVersion) {
            throw conflict("记忆设置已被其他管理员修改");
        }
        MemorySettings updated = new MemorySettings(
                optional(primaryChatModelId), fallbackEnabled, expectedVersion + 1, adminUserId, clock.instant());
        if (!repository.updateSettings(updated, expectedVersion)) {
            throw conflict("记忆设置已被其他管理员修改");
        }
        return SettingsView.from(updated);
    }

    public AdminHealthView adminHealth() {
        MemorySettings settings = repository.loadSettings();
        return new AdminHealthView(
                properties.isEnabled(), documents.health(), LOCAL_BGE_PROFILE,
                settings.primaryChatModelId(), settings.currentRunModelFallbackEnabled(),
                Math.toIntExact(repository.countLearningJobs("PENDING")),
                Math.toIntExact(repository.countLearningJobs("PROCESSING")),
                Math.toIntExact(repository.countLearningJobs("DEAD")));
    }

    private MemoryView view(QaMemory memory) {
        if (memory.mem0MemoryId() == null) {
            return MemoryView.from(memory, memory.displaySummary(), false);
        }
        try {
            Optional<MemoryDocumentStore.StoredDocument> document = documents.get(memory.mem0MemoryId());
            return document.map(value -> MemoryView.from(memory, value.content(), true))
                    .orElseGet(() -> MemoryView.from(memory, memory.displaySummary(), false));
        } catch (PlatformException failure) {
            if (failure.errorCode() != ErrorCode.MEMORY_UNAVAILABLE
                    && failure.errorCode() != ErrorCode.MEMORY_TIMEOUT) {
                throw failure;
            }
            return MemoryView.from(memory, memory.displaySummary(), false);
        }
    }

    private String documentContent(QaMemory memory) {
        if (memory.mem0MemoryId() == null) {
            throw new PlatformException(ErrorCode.MEMORY_UNAVAILABLE, "记忆正文尚未同步到 Mem0");
        }
        return documents.get(memory.mem0MemoryId()).map(MemoryDocumentStore.StoredDocument::content)
                .orElseThrow(() -> new PlatformException(ErrorCode.MEMORY_UNAVAILABLE, "Mem0 中未找到记忆正文"));
    }

    private MemorySkillProposal requireSkillProposal(String proposalId) {
        return repository.findSkillProposal(proposalId)
                .orElseThrow(() -> notFound("Skill 提案不存在"));
    }

    private void requireSkillVersion(MemorySkillProposal proposal, long expectedVersion) {
        if (expectedVersion < 0 || proposal.version() != expectedVersion) {
            throw conflict("Skill 提案已被其他操作修改，请刷新后重试");
        }
    }

    private void persistSkillExpected(MemorySkillProposal proposal, long expectedVersion) {
        if (!repository.updateSkillProposal(proposal, expectedVersion)) {
            throw conflict("Skill 提案已被其他操作修改，请刷新后重试");
        }
    }

    private String generateSkillDraft(String title, QaMemory memory) {
        String content = documentContent(memory);
        String draft = "---\nname: " + slug(title) + "\ndescription: " + yamlText(title)
                + "\n---\n\n# " + title + "\n\n## 适用场景\n\n"
                + taskDescription(memory.taskTypes()) + "\n\n## 工作要求\n\n" + content + "\n";
        return safety.requireSafeSkillDraft(draft);
    }

    private String normalizeSkillTitle(String value, String fallback) {
        String title = value == null || value.isBlank() ? fallback : value.trim();
        if (title == null || title.isBlank()) {
            throw validation("Skill 标题不能为空");
        }
        String normalized = safety.displaySummary(title);
        if (normalized.codePointCount(0, normalized.length()) > 120) {
            int end = normalized.offsetByCodePoints(0, 120);
            normalized = normalized.substring(0, end);
        }
        return normalized;
    }

    private QaMemory requireAccessible(UserId userId, MemoryId memoryId) {
        QaMemory memory = repository.findById(memoryId).orElseThrow(() -> notFound("记忆不存在"));
        if (memory.scope() == MemoryScope.TEAM_APPLICATION) {
            requireMember(userId, memory.applicationId());
        } else if (!userId.value().equals(memory.ownerUserId())) {
            throw forbidden("无权访问该个人记忆");
        }
        return memory;
    }

    private void requireMutationAuthority(UserId userId, QaMemory memory, boolean appAdmin) {
        if (memory.scope() == MemoryScope.TEAM_APPLICATION) {
            if (!appAdmin) {
                throw forbidden("团队记忆只能由 APP_ADMIN 修改");
            }
            requireMember(userId, memory.applicationId());
        } else if (!userId.value().equals(memory.ownerUserId())) {
            throw forbidden("无权修改该个人记忆");
        }
    }

    private void requireEnabled(UserId userId) {
        if (!properties.isEnabled() || !repository.isWhitelisted(userId.value())) {
            throw forbidden("当前用户未开通长期记忆能力");
        }
    }

    private void requireMember(UserId userId, String applicationId) {
        if (!configuration.isActiveMember(new ApplicationId(requireApplication(applicationId)), userId)) {
            throw forbidden("当前用户不是该应用的有效成员");
        }
    }

    private String requireApplication(String value) {
        if (value == null || value.isBlank()) {
            throw validation("applicationId 不能为空");
        }
        return value.trim();
    }

    private void requireVersion(QaMemory memory, long expectedVersion) {
        if (expectedVersion < 0 || memory.version() != expectedVersion) {
            throw conflict("记忆已被其他操作修改，请刷新后重试");
        }
    }

    private void persistExpected(QaMemory updated, long expectedVersion) {
        if (!repository.updateMemory(updated, expectedVersion)) {
            throw conflict("记忆已被其他操作修改，请刷新后重试");
        }
    }

    private QaMemory copy(
            QaMemory value, MemoryStatus status, List<QaTaskType> taskTypes, String summary,
            Instant confirmedAt, String supersededBy, long version, String vectorStatus, Instant updatedAt) {
        return new QaMemory(
                value.memoryId(), value.mem0MemoryId(), value.scope(), value.ownerUserId(), value.applicationId(),
                status, value.source(), taskTypes, summary, value.confidence(), value.distinctSessionCount(),
                value.distinctUserCount(), value.firstObservedAt(), value.lastObservedAt(), confirmedAt,
                supersededBy, value.createdByUserId(), version, vectorStatus, value.createdAt(), updatedAt);
    }

    private void deleteBestEffort(String documentId) {
        try {
            documents.delete(documentId);
        } catch (RuntimeException ignored) {
            // 主事务失败时只做补偿尝试，不能把第三方错误覆盖为数据库错误。
        }
    }

    private PageWindow pageWindow(int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(100, size));
        return new PageWindow(safePage, safeSize, (safePage - 1) * safeSize);
    }

    private List<QaTaskType> normalizeTasks(List<QaTaskType> taskTypes) {
        return taskTypes == null || taskTypes.isEmpty()
                ? List.of(QaTaskType.GENERAL) : taskTypes.stream().filter(Objects::nonNull).distinct().toList();
    }

    private String taskDescription(List<QaTaskType> taskTypes) {
        return taskTypes.contains(QaTaskType.GENERAL)
                ? "适用于通用测试工作。"
                : "适用于：" + String.join("、", taskTypes.stream().map(Enum::name).toList()) + "。";
    }

    private String slug(String value) {
        String slug = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\p{IsHan}]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.isBlank() ? "qa-memory-skill" : slug;
    }

    private String yamlText(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String id(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private PlatformException validation(String message) { return new PlatformException(ErrorCode.VALIDATION_ERROR, message); }
    private PlatformException forbidden(String message) { return new PlatformException(ErrorCode.FORBIDDEN, message); }
    private PlatformException conflict(String message) { return new PlatformException(ErrorCode.CONFLICT, message); }
    private PlatformException notFound(String message) { return new PlatformException(ErrorCode.NOT_FOUND, message); }

    private record PageWindow(int page, int size, int offset) {
    }
}
