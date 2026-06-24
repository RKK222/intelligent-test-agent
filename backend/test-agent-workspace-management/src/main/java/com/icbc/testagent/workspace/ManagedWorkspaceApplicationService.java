package com.icbc.testagent.workspace;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.git.GitRemoteService;
import com.icbc.testagent.common.git.GitWorkspaceService;
import com.icbc.testagent.common.git.SshKeyCryptoService;
import com.icbc.testagent.common.id.RuntimeIdGenerator;
import com.icbc.testagent.domain.configuration.ApplicationDefinition;
import com.icbc.testagent.domain.configuration.ApplicationId;
import com.icbc.testagent.domain.configuration.ApplicationWorkspace;
import com.icbc.testagent.domain.configuration.ApplicationWorkspaceId;
import com.icbc.testagent.domain.configuration.CodeRepository;
import com.icbc.testagent.domain.configuration.CodeRepositoryId;
import com.icbc.testagent.domain.configuration.ConfigurationManagementRepository;
import com.icbc.testagent.domain.configuration.UserSshKey;
import com.icbc.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import com.icbc.testagent.domain.managedworkspace.ApplicationWorkspaceVersionId;
import com.icbc.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.icbc.testagent.domain.managedworkspace.ManagedWorkspaceStatus;
import com.icbc.testagent.domain.managedworkspace.PersonalWorkspace;
import com.icbc.testagent.domain.managedworkspace.PersonalWorkspaceId;
import com.icbc.testagent.domain.managedworkspace.UserWorkspaceBranchPreference;
import com.icbc.testagent.domain.managedworkspace.UserWorkspacePreference;
import com.icbc.testagent.domain.managedworkspace.WorkspaceSyncDirection;
import com.icbc.testagent.domain.managedworkspace.WorkspaceSyncRecord;
import com.icbc.testagent.domain.managedworkspace.WorkspaceSyncRecordId;
import com.icbc.testagent.domain.managedworkspace.WorkspaceSyncStatus;
import com.icbc.testagent.domain.user.User;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.user.UserRepository;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.WorkspaceRepository;
import com.icbc.testagent.domain.workspace.WorkspaceStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 托管工作区应用服务，负责把应用工作空间配置落到物理 Git 目录和运行态 Workspace。
 */
@Service
public class ManagedWorkspaceApplicationService {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d{8}$");
    // 兼容前端"yyyy年M月"格式（如 2024年1月 / 2024年12月），用于「+新增版本」场景。
    // 版本字符串允许原样落库，但分支名/路径需要走 sanitizeVersionForBranchAndPath 转为安全片段。
    private static final Pattern VERSION_PATTERN_YEAR_MONTH = Pattern.compile("^(\\d{4})年(\\d{1,2})月$");
    private static final Pattern SCP_LIKE_SSH_URL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9._-]+:.+");

    private final ConfigurationManagementRepository configurationRepository;
    private final ManagedWorkspaceRepository managedWorkspaceRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final GitRemoteService gitRemoteService;
    private final GitWorkspaceService gitWorkspaceService;
    private final SshKeyCryptoService sshKeyCryptoService;
    private final Path managedRoot;

    /**
     * Spring 构造器：绑定托管工作区根目录和 SSH key 加密密钥。
     */
    @Autowired
    public ManagedWorkspaceApplicationService(
            ConfigurationManagementRepository configurationRepository,
            ManagedWorkspaceRepository managedWorkspaceRepository,
            WorkspaceRepository workspaceRepository,
            UserRepository userRepository,
            @Value("${test-agent.managed-workspace.root:${TEST_AGENT_MANAGED_WORKSPACE_ROOT:}}") String managedRoot,
            @Value("${test-agent.security.ssh-key-encryption-key:${TEST_AGENT_SSH_KEY_ENCRYPTION_KEY:}}") String encryptionKey) {
        this(
                configurationRepository,
                managedWorkspaceRepository,
                workspaceRepository,
                userRepository,
                new GitRemoteService(),
                new GitWorkspaceService(),
                new SshKeyCryptoService(encryptionKey),
                managedRoot);
    }

    /**
     * 测试构造器：允许注入 fake Git 服务和临时根目录。
     */
    ManagedWorkspaceApplicationService(
            ConfigurationManagementRepository configurationRepository,
            ManagedWorkspaceRepository managedWorkspaceRepository,
            WorkspaceRepository workspaceRepository,
            UserRepository userRepository,
            GitRemoteService gitRemoteService,
            GitWorkspaceService gitWorkspaceService,
            SshKeyCryptoService sshKeyCryptoService,
            String managedRoot) {
        this.configurationRepository = Objects.requireNonNull(configurationRepository, "configurationRepository must not be null");
        this.managedWorkspaceRepository = Objects.requireNonNull(managedWorkspaceRepository, "managedWorkspaceRepository must not be null");
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.gitRemoteService = Objects.requireNonNull(gitRemoteService, "gitRemoteService must not be null");
        this.gitWorkspaceService = Objects.requireNonNull(gitWorkspaceService, "gitWorkspaceService must not be null");
        this.sshKeyCryptoService = Objects.requireNonNull(sshKeyCryptoService, "sshKeyCryptoService must not be null");
        this.managedRoot = resolveManagedRoot(managedRoot);
    }

    public List<ManagedWorkspaceResponses.ManagedApplicationResponse> listApplications(UserId userId) {
        return configurationRepository.findApplicationsByMember(userId).stream()
                .map(ManagedWorkspaceResponses.ManagedApplicationResponse::from)
                .toList();
    }

    public List<ManagedWorkspaceResponses.WorkspaceTemplateResponse> listTemplates(String appId, UserId userId) {
        ApplicationId applicationId = existingMemberApp(appId, userId).appId();
        return configurationRepository.findWorkspaces(applicationId).stream()
                .map(ManagedWorkspaceResponses.WorkspaceTemplateResponse::from)
                .toList();
    }

    public List<ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse> listVersions(String templateId, UserId userId) {
        ApplicationWorkspace template = existingTemplate(new ApplicationWorkspaceId(templateId));
        ensureMember(template.appId(), userId);
        return managedWorkspaceRepository.findVersions(template.workspaceId()).stream()
                .map(this::versionResponse)
                .toList();
    }

    public ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse createVersion(
            String appId,
            String templateId,
            String version,
            String branch,
            UserId userId,
            String traceId) {
        try {
            return doCreateVersion(appId, templateId, version, branch, userId, traceId);
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "创建应用版本工作区失败: " + exception.getMessage(), Map.of(), exception);
        }
    }

    private ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse doCreateVersion(
            String appId,
            String templateId,
            String version,
            String branch,
            UserId userId,
            String traceId) {
        ApplicationDefinition application = existingMemberApp(appId, userId);
        ApplicationWorkspace template = existingTemplate(new ApplicationWorkspaceId(templateId));
        if (!template.appId().equals(application.appId())) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "工作空间不属于当前应用", Map.of("workspaceId", templateId));
        }
        String normalizedVersion = normalizeVersion(version);
        Optional<ApplicationWorkspaceVersion> existing = managedWorkspaceRepository.findVersionByTemplateAndVersion(template.workspaceId(), normalizedVersion);
        if (existing.isPresent()) {
            markRecent(userId, application.appId(), existing.get().runtimeWorkspaceId());
            return versionResponse(existing.get());
        }
        CodeRepository repository = existingRepository(template.repositoryId());
        String resolvedBranch = resolveBranch(repository, normalizedVersion, branch, userId);
        Path repoRoot = appRepoRoot(normalizedVersion, repository);
        Path workspaceRoot = repoRoot.resolve(template.directoryPath()).normalize();
        String privateKey = privateKeyFor(repository, userId);
        prepareApplicationRepo(repository, resolvedBranch, repoRoot, workspaceRoot, privateKey);
        Workspace runtimeWorkspace = createRuntimeWorkspace(
                template.workspaceName() + "-" + normalizedVersion,
                workspaceRoot,
                traceId);
        Instant now = Instant.now();
        ApplicationWorkspaceVersion saved = managedWorkspaceRepository.saveVersion(new ApplicationWorkspaceVersion(
                new ApplicationWorkspaceVersionId(RuntimeIdGenerator.applicationWorkspaceVersionId()),
                template.workspaceId(),
                template.appId(),
                repository.repositoryId(),
                normalizedVersion,
                resolvedBranch,
                realPath(repoRoot).toString(),
                realPath(workspaceRoot).toString(),
                runtimeWorkspace.workspaceId(),
                userId,
                ManagedWorkspaceStatus.ACTIVE,
                now,
                now));
        markRecent(userId, application.appId(), runtimeWorkspace.workspaceId());
        return ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse.from(saved, runtimeWorkspace);
    }

    public List<ManagedWorkspaceResponses.PersonalWorkspaceResponse> listPersonalWorkspaces(String versionId, UserId userId) {
        ApplicationWorkspaceVersion version = existingVersion(new ApplicationWorkspaceVersionId(versionId));
        ensureMember(version.appId(), userId);
        return managedWorkspaceRepository.findPersonalWorkspaces(version.versionId(), userId).stream()
                .map(this::personalResponse)
                .toList();
    }

    public ManagedWorkspaceResponses.PersonalWorkspaceResponse createPersonalWorkspace(
            String versionId,
            String workspaceName,
            UserId userId,
            String traceId) {
        try {
            return doCreatePersonalWorkspace(versionId, workspaceName, userId, traceId);
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "创建个人工作区失败: " + exception.getMessage(), Map.of(), exception);
        }
    }

    private ManagedWorkspaceResponses.PersonalWorkspaceResponse doCreatePersonalWorkspace(
            String versionId,
            String workspaceName,
            UserId userId,
            String traceId) {
        ApplicationWorkspaceVersion version = existingVersion(new ApplicationWorkspaceVersionId(versionId));
        ensureMember(version.appId(), userId);
        ApplicationWorkspace template = existingTemplate(version.applicationWorkspaceId());
        User user = existingUser(userId);
        String normalizedName = requireText(workspaceName, "个人工作区名称不能为空", "workspaceName");
        PersonalWorkspaceId personalId = new PersonalWorkspaceId(RuntimeIdGenerator.personalWorkspaceId());
        String branch = version.branch() + "_" + sanitizeBranchPart(user.unifiedAuthId()) + "_" + personalId.value();
        Path repoRoot = personalRepoRoot(version, user, personalId);
        Path workspaceRoot = repoRoot.resolve(template.directoryPath()).normalize();
        CodeRepository repository = existingRepository(version.repositoryId());
        gitWorkspaceService.createWorktree(Path.of(version.repoRootPath()), repoRoot, branch, privateKeyFor(repository, userId));
        if (!Files.isDirectory(workspaceRoot)) {
            throw new PlatformException(ErrorCode.CONFLICT, "个人工作区目录不存在", Map.of("path", workspaceRoot.toString()));
        }
        Workspace runtimeWorkspace = createRuntimeWorkspace(normalizedName, workspaceRoot, traceId);
        Instant now = Instant.now();
        PersonalWorkspace saved = managedWorkspaceRepository.savePersonalWorkspace(new PersonalWorkspace(
                personalId,
                version.versionId(),
                version.appId(),
                version.applicationWorkspaceId(),
                userId,
                normalizedName,
                branch,
                realPath(repoRoot).toString(),
                realPath(workspaceRoot).toString(),
                runtimeWorkspace.workspaceId(),
                gitWorkspaceService.headCommit(repoRoot),
                ManagedWorkspaceStatus.ACTIVE,
                now,
                now));
        markRecent(userId, version.appId(), runtimeWorkspace.workspaceId());
        return ManagedWorkspaceResponses.PersonalWorkspaceResponse.from(saved, runtimeWorkspace);
    }

    public ManagedWorkspaceResponses.WorkspaceRuntimeResponse markRecentWorkspace(String workspaceId, UserId userId) {
        Workspace workspace = existingWorkspace(new WorkspaceId(workspaceId));
        ApplicationId appId = appIdForRuntimeWorkspace(workspace.workspaceId())
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "托管工作区不存在", Map.of("workspaceId", workspaceId)));
        ensureMember(appId, userId);
        markRecent(userId, appId, workspace.workspaceId());
        return ManagedWorkspaceResponses.WorkspaceRuntimeResponse.from(workspace);
    }

    public Optional<ManagedWorkspaceResponses.WorkspaceRuntimeResponse> recentWorkspace(UserId userId) {
        return managedWorkspaceRepository.findGlobalPreference(userId)
                .flatMap(preference -> workspaceRepository.findById(preference.workspaceId()))
                .map(ManagedWorkspaceResponses.WorkspaceRuntimeResponse::from);
    }

    public Optional<ManagedWorkspaceResponses.WorkspaceRuntimeResponse> recentWorkspace(String appId, UserId userId) {
        ApplicationId applicationId = existingMemberApp(appId, userId).appId();
        return managedWorkspaceRepository.findApplicationPreference(userId, applicationId)
                .flatMap(preference -> workspaceRepository.findById(preference.workspaceId()))
                .map(ManagedWorkspaceResponses.WorkspaceRuntimeResponse::from);
    }

    public ManagedWorkspaceResponses.WorkspaceDiffResponse diffPersonalWorkspace(String personalWorkspaceId, UserId userId) {
        PersonalWorkspace personal = existingPersonal(new PersonalWorkspaceId(personalWorkspaceId));
        ensurePersonalOwner(personal, userId);
        ApplicationWorkspaceVersion version = existingVersion(personal.versionId());
        return new ManagedWorkspaceResponses.WorkspaceDiffResponse(compareDirectories(
                Path.of(personal.workspaceRootPath()),
                Path.of(version.workspaceRootPath())));
    }

    public ManagedWorkspaceResponses.WorkspaceSyncResponse syncPersonalToApplication(
            String personalWorkspaceId,
            List<String> files,
            boolean force,
            UserId userId,
            String traceId) {
        PersonalWorkspace personal = existingPersonal(new PersonalWorkspaceId(personalWorkspaceId));
        ensurePersonalOwner(personal, userId);
        ApplicationWorkspaceVersion version = existingVersion(personal.versionId());
        List<String> normalizedFiles = normalizeFiles(files);
        WorkspaceSyncRecordId syncId = new WorkspaceSyncRecordId(RuntimeIdGenerator.workspaceSyncRecordId());
        try {
            copyFiles(Path.of(personal.workspaceRootPath()), Path.of(version.workspaceRootPath()), normalizedFiles);
            CodeRepository repository = existingRepository(version.repositoryId());
            gitWorkspaceService.commitFiles(
                    Path.of(version.repoRootPath()),
                    repoRelativeFiles(Path.of(version.repoRootPath()), Path.of(version.workspaceRootPath()), normalizedFiles),
                    "test-agent sync " + syncId.value(),
                    privateKeyFor(repository, userId));
            gitWorkspaceService.push(Path.of(version.repoRootPath()), version.branch(), force, privateKeyFor(repository, userId));
            saveSync(syncId, userId, personal.runtimeWorkspaceId(), version.runtimeWorkspaceId(), WorkspaceSyncDirection.PERSONAL_TO_APPLICATION, normalizedFiles, force, WorkspaceSyncStatus.SUCCEEDED, traceId);
            return new ManagedWorkspaceResponses.WorkspaceSyncResponse(syncId.value(), WorkspaceSyncStatus.SUCCEEDED.name(), normalizedFiles, force);
        } catch (RuntimeException exception) {
            saveSync(syncId, userId, personal.runtimeWorkspaceId(), version.runtimeWorkspaceId(), WorkspaceSyncDirection.PERSONAL_TO_APPLICATION, normalizedFiles, force, WorkspaceSyncStatus.FAILED, traceId);
            throw exception;
        }
    }

    public ManagedWorkspaceResponses.WorkspaceSyncResponse syncApplicationToPersonal(
            String personalWorkspaceId,
            List<String> files,
            UserId userId,
            String traceId) {
        PersonalWorkspace personal = existingPersonal(new PersonalWorkspaceId(personalWorkspaceId));
        ensurePersonalOwner(personal, userId);
        ApplicationWorkspaceVersion version = existingVersion(personal.versionId());
        List<String> normalizedFiles = normalizeFiles(files);
        WorkspaceSyncRecordId syncId = new WorkspaceSyncRecordId(RuntimeIdGenerator.workspaceSyncRecordId());
        copyFiles(Path.of(version.workspaceRootPath()), Path.of(personal.workspaceRootPath()), normalizedFiles);
        saveSync(syncId, userId, version.runtimeWorkspaceId(), personal.runtimeWorkspaceId(), WorkspaceSyncDirection.APPLICATION_TO_PERSONAL, normalizedFiles, false, WorkspaceSyncStatus.SUCCEEDED, traceId);
        return new ManagedWorkspaceResponses.WorkspaceSyncResponse(syncId.value(), WorkspaceSyncStatus.SUCCEEDED.name(), normalizedFiles, false);
    }

    /**
     * 记录当前用户在某 (appId, workspaceId) 维度下最近一次手动选择的 VCS 分支，
     * 用于下次进入同一工作区时把分支显示在 IDE 上；非托管工作区会抛 NOT_FOUND，
     * 由 Controller 决定是否向上抛错。
     */
    public ManagedWorkspaceResponses.BranchPreferenceResponse markRecentBranch(
            String appId,
            String workspaceId,
            String branch,
            UserId userId) {
        ApplicationId applicationId = existingMemberApp(appId, userId).appId();
        Workspace workspace = existingWorkspace(new WorkspaceId(workspaceId));
        ApplicationId workspaceAppId = appIdForRuntimeWorkspace(workspace.workspaceId())
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "工作区不属于任何应用", Map.of("workspaceId", workspaceId)));
        if (!applicationId.equals(workspaceAppId)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "工作区不属于当前应用", Map.of("appId", appId, "workspaceId", workspaceId));
        }
        String normalizedBranch = requireText(branch, "分支名不能为空", "branch");
        UserWorkspaceBranchPreference preference = new UserWorkspaceBranchPreference(
                userId,
                applicationId,
                workspace.workspaceId(),
                normalizedBranch,
                Instant.now());
        managedWorkspaceRepository.saveBranchPreference(preference);
        return ManagedWorkspaceResponses.BranchPreferenceResponse.from(preference);
    }

    /**
     * 查询当前用户在某 (appId, workspaceId) 维度下的最近 VCS 分支偏好；未设置时返回 Optional.empty。
     * 工作区不属于任何应用时返回 Optional.empty，由前端走"无偏好"分支。
     */
    public Optional<ManagedWorkspaceResponses.BranchPreferenceResponse> recentBranch(String appId, String workspaceId, UserId userId) {
        ApplicationId applicationId;
        try {
            applicationId = existingMemberApp(appId, userId).appId();
        } catch (PlatformException exception) {
            return Optional.empty();
        }
        Workspace workspace;
        try {
            workspace = existingWorkspace(new WorkspaceId(workspaceId));
        } catch (PlatformException exception) {
            return Optional.empty();
        }
        return managedWorkspaceRepository.findBranchPreference(userId, applicationId, workspace.workspaceId())
                .map(ManagedWorkspaceResponses.BranchPreferenceResponse::from);
    }

    private void prepareApplicationRepo(CodeRepository repository, String branch, Path repoRoot, Path workspaceRoot, String privateKey) {
        try {
            if (Files.exists(repoRoot)) {
                if (!Files.isDirectory(repoRoot)) {
                    throw new PlatformException(ErrorCode.CONFLICT, "工作区仓库路径已存在且不是目录", Map.of("path", repoRoot.toString()));
                }
                String origin = gitWorkspaceService.originUrl(repoRoot);
                String currentBranch = gitWorkspaceService.currentBranch(repoRoot);
                if (!repository.gitUrl().equals(origin) || !branch.equals(currentBranch)) {
                    throw new PlatformException(ErrorCode.CONFLICT, "已有目录不是目标代码库分支", Map.of("path", repoRoot.toString()));
                }
            } else {
                Files.createDirectories(repoRoot.getParent());
                gitWorkspaceService.cloneBranch(repository.gitUrl(), branch, repoRoot, privateKey);
            }
            if (!Files.isDirectory(workspaceRoot)) {
                throw new PlatformException(ErrorCode.CONFLICT, "应用工作区目录不存在", Map.of("path", workspaceRoot.toString()));
            }
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.GIT_UNAVAILABLE, "创建应用版本工作区失败", Map.of("path", repoRoot.toString()), exception);
        }
    }

    private String resolveBranch(CodeRepository repository, String version, String branch, UserId userId) {
        if (!repository.standard()) {
            return requireText(branch, "非标准代码库必须指定分支", "branch");
        }
        // yyyy年M月 格式的版本在分支名里需转为 yyyy-MM，避免 git ref 中出现中文 / 年月字面量。
        String branchFragment = sanitizeVersionForBranchAndPath(version);
        String expected = "feature_testagent_" + branchFragment;
        List<String> branches = gitRemoteService.listBranches(repository.gitUrl(), privateKeyFor(repository, userId));
        if (!branches.contains(expected)) {
            throw new PlatformException(ErrorCode.CONFLICT, repository.name() + "代码库无" + expected + "分支，请到开发者门户创建分支", Map.of("branch", expected));
        }
        return expected;
    }

    private Workspace createRuntimeWorkspace(String name, Path workspaceRoot, String traceId) {
        Instant now = Instant.now();
        Workspace workspace = new Workspace(
                new WorkspaceId(RuntimeIdGenerator.workspaceId()),
                name,
                realPath(workspaceRoot).toString(),
                WorkspaceStatus.ACTIVE,
                now,
                now,
                traceId);
        return workspaceRepository.save(workspace);
    }

    private ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse versionResponse(ApplicationWorkspaceVersion version) {
        return ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse.from(version, existingWorkspace(version.runtimeWorkspaceId()));
    }

    private ManagedWorkspaceResponses.PersonalWorkspaceResponse personalResponse(PersonalWorkspace personal) {
        return ManagedWorkspaceResponses.PersonalWorkspaceResponse.from(personal, existingWorkspace(personal.runtimeWorkspaceId()));
    }

    private void markRecent(UserId userId, ApplicationId appId, WorkspaceId workspaceId) {
        Instant now = Instant.now();
        managedWorkspaceRepository.savePreference(new UserWorkspacePreference(userId, null, workspaceId, now));
        managedWorkspaceRepository.savePreference(new UserWorkspacePreference(userId, appId, workspaceId, now));
    }

    private Optional<ApplicationId> appIdForRuntimeWorkspace(WorkspaceId workspaceId) {
        Optional<ApplicationWorkspaceVersion> version = managedWorkspaceRepository.findVersionByRuntimeWorkspace(workspaceId);
        if (version.isPresent()) {
            return Optional.of(version.get().appId());
        }
        return managedWorkspaceRepository.findPersonalWorkspaceByRuntimeWorkspace(workspaceId).map(PersonalWorkspace::appId);
    }

    private ApplicationDefinition existingMemberApp(String appId, UserId userId) {
        ApplicationDefinition application = configurationRepository.findApplication(new ApplicationId(appId))
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "应用不存在", Map.of("appId", appId)));
        if (!application.enabled()) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "应用未启用", Map.of("appId", appId));
        }
        ensureMember(application.appId(), userId);
        return application;
    }

    private void ensureMember(ApplicationId appId, UserId userId) {
        if (!configurationRepository.isActiveMember(appId, userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "无该应用工作区权限", Map.of("appId", appId.value()));
        }
    }

    private void ensurePersonalOwner(PersonalWorkspace personal, UserId userId) {
        if (!personal.userId().equals(userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "无个人工作区权限", Map.of("personalWorkspaceId", personal.personalWorkspaceId().value()));
        }
    }

    private ApplicationWorkspace existingTemplate(ApplicationWorkspaceId workspaceId) {
        return configurationRepository.findWorkspace(workspaceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "应用工作空间不存在", Map.of("workspaceId", workspaceId.value())));
    }

    private ApplicationWorkspaceVersion existingVersion(ApplicationWorkspaceVersionId versionId) {
        return managedWorkspaceRepository.findVersion(versionId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "应用版本工作区不存在", Map.of("versionId", versionId.value())));
    }

    private PersonalWorkspace existingPersonal(PersonalWorkspaceId personalWorkspaceId) {
        return managedWorkspaceRepository.findPersonalWorkspace(personalWorkspaceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "个人工作区不存在", Map.of("personalWorkspaceId", personalWorkspaceId.value())));
    }

    private CodeRepository existingRepository(CodeRepositoryId repositoryId) {
        return configurationRepository.findRepository(repositoryId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "代码库不存在", Map.of("repositoryId", repositoryId.value())));
    }

    private Workspace existingWorkspace(WorkspaceId workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Workspace 不存在", Map.of("workspaceId", workspaceId.value())));
    }

    private User existingUser(UserId userId) {
        return userRepository.findByUserId(userId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "用户不存在", Map.of("userId", userId.value())));
    }

    private String privateKeyFor(CodeRepository repository, UserId userId) {
        if (!requiresSshKey(repository.gitUrl())) {
            return null;
        }
        UserSshKey sshKey = configurationRepository.findSshKeys(userId).stream()
                .findFirst()
                .orElseThrow(() -> new PlatformException(ErrorCode.FORBIDDEN, "当前用户未配置 SSH key"));
        return sshKeyCryptoService.decrypt(sshKey.encryptedPrivateKey(), sshKey.encryptionNonce());
    }

    private Path appRepoRoot(String version, CodeRepository repository) {
        // 路径片段统一走 sanitizeVersionForBranchAndPath：yyyy年M月 → yyyy-MM，避免路径里出现中文。
        String pathFragment = sanitizeVersionForBranchAndPath(version);
        return managedRoot.resolve("appworkspace").resolve(pathFragment).resolve(repository.repositoryId().value()).normalize();
    }

    private Path personalRepoRoot(ApplicationWorkspaceVersion version, User user, PersonalWorkspaceId personalId) {
        return managedRoot.resolve("personalworktree")
                .resolve(sanitizeVersionForBranchAndPath(version.version()))
                .resolve(sanitizePathPart(user.unifiedAuthId()))
                .resolve(version.repositoryId().value())
                .resolve(personalId.value())
                .normalize();
    }

    private List<ManagedWorkspaceResponses.WorkspaceDiffFileResponse> compareDirectories(Path source, Path target) {
        Map<String, Path> sourceFiles = fileMap(source);
        Map<String, Path> targetFiles = fileMap(target);
        Set<String> all = new java.util.TreeSet<>();
        all.addAll(sourceFiles.keySet());
        all.addAll(targetFiles.keySet());
        List<ManagedWorkspaceResponses.WorkspaceDiffFileResponse> files = new ArrayList<>();
        for (String path : all) {
            Path sourceFile = sourceFiles.get(path);
            Path targetFile = targetFiles.get(path);
            if (sourceFile == null) {
                files.add(new ManagedWorkspaceResponses.WorkspaceDiffFileResponse(path, "deleted", false));
            } else if (targetFile == null) {
                files.add(new ManagedWorkspaceResponses.WorkspaceDiffFileResponse(path, "added", false));
            } else if (!sameContent(sourceFile, targetFile)) {
                files.add(new ManagedWorkspaceResponses.WorkspaceDiffFileResponse(path, "modified", true));
            }
        }
        return files;
    }

    private Map<String, Path> fileMap(Path root) {
        try (var stream = Files.walk(root)) {
            Map<String, Path> files = new LinkedHashMap<>();
            stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(path -> files.put(root.relativize(path).toString().replace('\\', '/'), path));
            return files;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.CONFLICT, "工作区目录不可用", Map.of("path", root.toString()), exception);
        }
    }

    private boolean sameContent(Path left, Path right) {
        try {
            return Files.mismatch(left, right) < 0;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.CONFLICT, "读取工作区文件失败", Map.of("path", left.toString()), exception);
        }
    }

    private void copyFiles(Path sourceRoot, Path targetRoot, List<String> files) {
        for (String file : files) {
            Path source = sourceRoot.resolve(file).normalize();
            Path target = targetRoot.resolve(file).normalize();
            if (!source.startsWith(sourceRoot) || !target.startsWith(targetRoot)) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "同步文件路径越权", Map.of("path", file));
            }
            try {
                if (!Files.exists(source)) {
                    Files.deleteIfExists(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception exception) {
                throw new PlatformException(ErrorCode.CONFLICT, "同步文件失败", Map.of("path", file), exception);
            }
        }
    }

    private List<String> repoRelativeFiles(Path repoRoot, Path workspaceRoot, List<String> files) {
        return files.stream()
                .map(file -> repoRoot.relativize(workspaceRoot.resolve(file).normalize()).toString().replace('\\', '/'))
                .toList();
    }

    private void saveSync(
            WorkspaceSyncRecordId syncId,
            UserId userId,
            WorkspaceId source,
            WorkspaceId target,
            WorkspaceSyncDirection direction,
            List<String> files,
            boolean force,
            WorkspaceSyncStatus status,
            String traceId) {
        managedWorkspaceRepository.saveSyncRecord(new WorkspaceSyncRecord(
                syncId,
                userId,
                source,
                target,
                direction,
                files,
                force,
                status,
                traceId,
                Instant.now()));
    }

    private List<String> normalizeFiles(List<String> files) {
        if (files == null || files.isEmpty()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "同步文件不能为空");
        }
        return files.stream().map(file -> normalizeRelativePath(file, "path")).distinct().toList();
    }

    private String normalizeRelativePath(String path, String field) {
        String value = requireText(path, "路径不能为空", field).replace('\\', '/');
        if (value.equals(".") || value.startsWith("/") || value.startsWith("../") || value.contains("/../")) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "路径无效", Map.of("path", path));
        }
        return value;
    }

    private String normalizeVersion(String version) {
        String value = requireText(version, "版本不能为空", "version");
        // 兼容两种格式：
        // - 历史数据：yyyyMMdd（8 位数字）
        // - 新增版本：yyyy年M月（如 2024年1月），用户在前端「+新增版本」选择后原样传入。
        if (VERSION_PATTERN.matcher(value).matches()) {
            return value;
        }
        if (VERSION_PATTERN_YEAR_MONTH.matcher(value).matches()) {
            return value;
        }
        throw new PlatformException(ErrorCode.VALIDATION_ERROR, "版本必须为 yyyyMMdd 或 yyyy年M月", Map.of("version", value));
    }

    /**
     * 把版本字符串转为分支名/路径安全片段。
     * - yyyyMMdd：原样使用。
     * - yyyy年M月：转为 yyyy-MM（如 2024年1月 → 2024-01，2024年12月 → 2024-12）。
     * 数据库中 version 字段保留用户原值（"2024年1月"），仅在拼分支 / 路径时调本方法做转换。
     */
    private String sanitizeVersionForBranchAndPath(String version) {
        java.util.regex.Matcher matcher = VERSION_PATTERN_YEAR_MONTH.matcher(version);
        if (matcher.matches()) {
            String year = matcher.group(1);
            String month = String.format("%02d", Integer.parseInt(matcher.group(2)));
            return year + "-" + month;
        }
        return version;
    }

    private String requireText(String value, String message, String field) {
        if (value == null || value.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, message, Map.of("field", field));
        }
        return value.trim();
    }

    private Path realPath(Path path) {
        try {
            return path.toRealPath();
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.CONFLICT, "工作区目录不可用", Map.of("path", path.toString()), exception);
        }
    }

    private Path resolveManagedRoot(String configuredRoot) {
        String root = configuredRoot == null || configuredRoot.isBlank()
                ? Path.of(System.getProperty("user.home"), "test-agent-data").toString()
                : configuredRoot.trim();
        return Path.of(root).toAbsolutePath().normalize();
    }

    private static boolean requiresSshKey(String gitUrl) {
        return gitUrl.startsWith("ssh://") || SCP_LIKE_SSH_URL.matcher(gitUrl).matches();
    }

    private static String sanitizePathPart(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String sanitizeBranchPart(String value) {
        return sanitizePathPart(value);
    }
}
