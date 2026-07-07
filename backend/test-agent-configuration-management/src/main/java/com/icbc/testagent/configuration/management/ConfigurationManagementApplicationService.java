package com.icbc.testagent.configuration.management;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.git.GitRemoteService;
import com.icbc.testagent.common.id.RuntimeIdGenerator;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.configuration.management.ConfigurationManagementResponses.ApplicationMemberResponse;
import com.icbc.testagent.configuration.management.ConfigurationManagementResponses.ApplicationResponse;
import com.icbc.testagent.configuration.management.ConfigurationManagementResponses.ApplicationWorkspaceResponse;
import com.icbc.testagent.configuration.management.ConfigurationManagementResponses.CodeRepositoryResponse;
import com.icbc.testagent.configuration.management.ConfigurationManagementResponses.RepositoryDeploymentOptionResponse;
import com.icbc.testagent.configuration.management.ConfigurationManagementResponses.RepositoryDeploymentOptionsResponse;
import com.icbc.testagent.configuration.management.ConfigurationManagementResponses.RepositoryTreeNodeResponse;
import com.icbc.testagent.configuration.management.ConfigurationManagementResponses.RepositoryTreeResponse;
import com.icbc.testagent.configuration.management.ConfigurationManagementResponses.RepositoryTypeOptionResponse;
import com.icbc.testagent.common.git.SshKeyEncryptionService;
import com.icbc.testagent.configuration.management.ConfigurationManagementResponses.SshKeyResponse;
import com.icbc.testagent.configuration.management.ConfigurationManagementResponses.UserResponse;
import com.icbc.testagent.common.git.GitRemoteService.RemoteTreeNode;
import com.icbc.testagent.domain.configuration.ApplicationDefinition;
import com.icbc.testagent.domain.configuration.ApplicationId;
import com.icbc.testagent.domain.configuration.ApplicationMember;
import com.icbc.testagent.domain.configuration.ApplicationWorkspace;
import com.icbc.testagent.domain.configuration.ApplicationWorkspaceId;
import com.icbc.testagent.domain.configuration.CodeRepository;
import com.icbc.testagent.domain.configuration.CodeRepositoryDeploymentMode;
import com.icbc.testagent.domain.configuration.CodeRepositoryId;
import com.icbc.testagent.domain.configuration.CodeRepositoryType;
import com.icbc.testagent.domain.configuration.ConfigurationManagementRepository;
import com.icbc.testagent.domain.configuration.SshKeyId;
import com.icbc.testagent.domain.configuration.UserSshKey;
import com.icbc.testagent.domain.dictionary.Dictionary;
import com.icbc.testagent.domain.dictionary.DictionaryRepository;
import com.icbc.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.icbc.testagent.domain.user.User;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.user.UserRepository;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 应用配置管理应用服务，集中编排配置持久化、Git 远端读取和个人 SSH key 加密。
 */
@Service
public class ConfigurationManagementApplicationService {

    private static final Pattern SCP_LIKE_SSH_URL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9._-]+:.+");
    private static final Pattern REPOSITORY_ENGLISH_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,126}[A-Za-z0-9])?$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s");
    private final ConfigurationManagementRepository configurationRepository;
    private final DictionaryRepository dictionaryRepository;
    private final UserRepository userRepository;
    private final GitRemoteService gitRemoteService;
    private final GitCloneCacheService gitCloneCacheService;
    private final SshKeyEncryptionService sshKeyEncryptionService;
    private final ManagedWorkspaceRepository managedWorkspaceRepository;
    private final String defaultRepositoryDeploymentMode;

    /**
     * 注入领域端口和公共 Git/加密服务。
     */
    @Autowired
    public ConfigurationManagementApplicationService(
            ConfigurationManagementRepository configurationRepository,
            DictionaryRepository dictionaryRepository,
            UserRepository userRepository,
            GitCloneCacheService gitCloneCacheService,
            SshKeyEncryptionService sshKeyEncryptionService,
            ManagedWorkspaceRepository managedWorkspaceRepository,
            @Value("${test-agent.deployment.mode:external}") String deploymentMode) {
        this(configurationRepository, dictionaryRepository, userRepository, new GitRemoteService(), gitCloneCacheService, sshKeyEncryptionService, managedWorkspaceRepository, deploymentMode);
    }

    /**
     * 测试可注入 fake Git 服务，避免执行真实远端命令。
     */
    ConfigurationManagementApplicationService(
            ConfigurationManagementRepository configurationRepository,
            DictionaryRepository dictionaryRepository,
            UserRepository userRepository,
            GitCloneCacheService gitCloneCacheService,
            SshKeyEncryptionService sshKeyEncryptionService,
            ManagedWorkspaceRepository managedWorkspaceRepository) {
        this(configurationRepository, dictionaryRepository, userRepository, new GitRemoteService(), gitCloneCacheService, sshKeyEncryptionService, managedWorkspaceRepository, "external");
    }

    /**
     * 测试可注入 fake Git 服务，避免执行真实远端命令。
     */
    ConfigurationManagementApplicationService(
            ConfigurationManagementRepository configurationRepository,
            DictionaryRepository dictionaryRepository,
            UserRepository userRepository,
            GitRemoteService gitRemoteService,
            GitCloneCacheService gitCloneCacheService,
            SshKeyEncryptionService sshKeyEncryptionService,
            ManagedWorkspaceRepository managedWorkspaceRepository) {
        this(configurationRepository, dictionaryRepository, userRepository, gitRemoteService, gitCloneCacheService, sshKeyEncryptionService, managedWorkspaceRepository, "external");
    }

    ConfigurationManagementApplicationService(
            ConfigurationManagementRepository configurationRepository,
            DictionaryRepository dictionaryRepository,
            UserRepository userRepository,
            GitRemoteService gitRemoteService,
            GitCloneCacheService gitCloneCacheService,
            SshKeyEncryptionService sshKeyEncryptionService,
            ManagedWorkspaceRepository managedWorkspaceRepository,
            String deploymentMode) {
        this.configurationRepository = Objects.requireNonNull(configurationRepository, "configurationRepository must not be null");
        this.dictionaryRepository = Objects.requireNonNull(dictionaryRepository, "dictionaryRepository must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.gitRemoteService = Objects.requireNonNull(gitRemoteService, "gitRemoteService must not be null");
        this.gitCloneCacheService = Objects.requireNonNull(gitCloneCacheService, "gitCloneCacheService must not be null");
        this.sshKeyEncryptionService = Objects.requireNonNull(sshKeyEncryptionService, "sshKeyEncryptionService must not be null");
        this.managedWorkspaceRepository = Objects.requireNonNull(managedWorkspaceRepository, "managedWorkspaceRepository must not be null");
        this.defaultRepositoryDeploymentMode = CodeRepositoryDeploymentMode.fromDeploymentProperty(deploymentMode).value();
    }

    public List<ApplicationResponse> listApplications(Boolean enabledOnly) {
        return configurationRepository.findApplications(enabledOnly).stream()
                .map(this::applicationResponse)
                .toList();
    }

    public List<ApplicationMemberResponse> listMembers(String appId) {
        ApplicationId id = existingEnabledApp(appId).appId();
        return configurationRepository.findActiveMembers(id).stream()
                .map(member -> existingUser(member.userId()))
                .map(this::memberResponse)
                .toList();
    }

    public ApplicationMemberResponse addMember(String appId, String userId) {
        ApplicationId applicationId = existingEnabledApp(appId).appId();
        User user = existingUser(new UserId(userId));
        configurationRepository.saveMember(ApplicationMember.active(applicationId, user.userId(), Instant.now()));
        return memberResponse(user);
    }

    public void removeMember(String appId, String userId) {
        configurationRepository.deleteMember(new ApplicationId(appId), new UserId(userId));
    }

    public PageResponse<UserResponse> searchUsers(String keyword, PageRequest pageRequest) {
        PageResponse<User> page = userRepository.findPage(keyword, pageRequest);
        return new PageResponse<>(
                page.items().stream().map(this::userResponse).toList(),
                page.page(),
                page.size(),
                page.total());
    }

    public PageResponse<CodeRepositoryResponse> listRepositories(PageRequest pageRequest) {
        PageResponse<CodeRepository> page = configurationRepository.findRepositories(pageRequest);
        return new PageResponse<>(
                page.items().stream().map(this::repositoryResponse).toList(),
                page.page(),
                page.size(),
                page.total());
    }

    public List<RepositoryTypeOptionResponse> listRepositoryTypes() {
        return dictionaryRepository.findByDictKey(Dictionary.DICT_KEY_REPOSITORY_TYPE).stream()
                .sorted(java.util.Comparator.comparingInt(Dictionary::sortOrder))
                .map(dictionary -> new RepositoryTypeOptionResponse(dictionary.dictValue(), dictionary.dictLabel()))
                .toList();
    }

    public RepositoryDeploymentOptionsResponse repositoryDeploymentOptions(UserId currentUserId) {
        User user = existingUser(currentUserId);
        return new RepositoryDeploymentOptionsResponse(
                defaultRepositoryDeploymentMode,
                "ssh://" + user.unifiedAuthId() + "@",
                List.of(
                        new RepositoryDeploymentOptionResponse(CodeRepositoryDeploymentMode.EXTERNAL.value(), "外部部署"),
                        new RepositoryDeploymentOptionResponse(CodeRepositoryDeploymentMode.INTERNAL.value(), "内部部署")));
    }

    public CodeRepositoryResponse createRepository(
            String gitUrl,
            String name,
            String englishName,
            Boolean standard,
            String repositoryType) {
        return createRepository(gitUrl, name, englishName, standard, repositoryType, CodeRepositoryDeploymentMode.EXTERNAL.value());
    }

    public CodeRepositoryResponse createRepository(
            String gitUrl,
            String name,
            String englishName,
            Boolean standard,
            String repositoryType,
            String deploymentMode) {
        String normalizedDeploymentMode = normalizeRepositoryDeploymentMode(deploymentMode);
        String normalizedUrl = CodeRepositoryDeploymentMode.INTERNAL.value().equals(normalizedDeploymentMode)
                ? validateInternalGitUrl(gitUrl)
                : validateGitUrl(gitUrl);
        String normalizedName = requireText(name, "代码库名称不能为空", "name");
        String normalizedEnglishName = normalizeRepositoryEnglishName(englishName, normalizedUrl, normalizedDeploymentMode);
        String normalizedRepositoryType = normalizeRepositoryTypeForCreate(repositoryType, standard);
        configurationRepository.findRepositoryByGitUrl(normalizedUrl).ifPresent(repository -> {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "代码库地址已存在",
                    Map.of("repositoryId", repository.repositoryId().value()));
        });
        ensureRepositoryEnglishNameUnique(normalizedEnglishName, null);
        Instant now = Instant.now();
        CodeRepository repository = new CodeRepository(
                new CodeRepositoryId(RuntimeIdGenerator.repositoryId()),
                normalizedUrl,
                normalizedName,
                normalizedEnglishName,
                normalizedRepositoryType,
                normalizedDeploymentMode,
                CodeRepositoryType.fromValue(normalizedRepositoryType).standard(),
                now,
                now);
        return repositoryResponse(configurationRepository.saveRepository(repository));
    }

    public CodeRepositoryResponse updateRepository(String repositoryId, String name, String englishName, Boolean standard) {
        CodeRepository repository = existingRepository(new CodeRepositoryId(repositoryId));
        String normalizedEnglishName = normalizeRepositoryEnglishName(englishName);
        ensureRepositoryEnglishNameUnique(normalizedEnglishName, repository.repositoryId());
        String nextRepositoryType = standard == null
                ? repository.repositoryType()
                : CodeRepositoryType.fromStandard(Boolean.TRUE.equals(standard)).value();
        CodeRepository updated = repository.editMetadata(
                requireText(name, "代码库名称不能为空", "name"),
                normalizedEnglishName,
                nextRepositoryType,
                Instant.now());
        return repositoryResponse(configurationRepository.updateRepositoryMetadata(updated));
    }

    public List<CodeRepositoryResponse> listApplicationRepositories(String appId) {
        ApplicationId id = existingEnabledApp(appId).appId();
        return configurationRepository.findRepositoriesByApplication(id).stream()
                .map(this::repositoryResponse)
                .toList();
    }

    public CodeRepositoryResponse linkRepositoryToApplication(String appId, String repositoryId) {
        ApplicationId applicationId = existingEnabledApp(appId).appId();
        CodeRepository repository = existingRepository(new CodeRepositoryId(repositoryId));
        configurationRepository.linkRepository(applicationId, repository.repositoryId());
        return repositoryResponse(repository);
    }

    public void unlinkRepositoryFromApplication(String appId, String repositoryId) {
        configurationRepository.unlinkRepository(new ApplicationId(appId), new CodeRepositoryId(repositoryId));
    }

    public List<ApplicationResponse> listRepositoryApplications(String repositoryId) {
        CodeRepository repository = existingRepository(new CodeRepositoryId(repositoryId));
        return configurationRepository.findApplicationsByRepository(repository.repositoryId()).stream()
                .map(this::applicationResponse)
                .toList();
    }

    public ApplicationResponse linkApplicationToRepository(String repositoryId, String appId) {
        CodeRepository repository = existingRepository(new CodeRepositoryId(repositoryId));
        ApplicationDefinition application = existingEnabledApp(appId);
        configurationRepository.linkRepository(application.appId(), repository.repositoryId());
        return applicationResponse(application);
    }

    public void unlinkApplicationFromRepository(String repositoryId, String appId) {
        configurationRepository.unlinkRepository(new ApplicationId(appId), new CodeRepositoryId(repositoryId));
    }

    public List<String> listBranches(String repositoryId, UserId currentUserId) {
        CodeRepository repository = existingRepository(new CodeRepositoryId(repositoryId));
        return gitRemoteService.listBranches(effectiveGitUrl(repository, currentUserId), privateKeyFor(repository, currentUserId));
    }

    /**
     * 列出指定分支的目录结构。
     * 使用浅克隆缓存服务，避免 git archive --remote 超时问题。
     */
    public List<String> listDirectories(String repositoryId, String branch, UserId currentUserId) {
        CodeRepository repository = existingRepository(new CodeRepositoryId(repositoryId));
        String normalizedBranch = requireText(branch, "分支不能为空", "branch");
        String privateKey = privateKeyFor(repository, currentUserId);
        return gitCloneCacheService.listDirectories(effectiveGitUrl(repository, currentUserId), normalizedBranch, privateKey);
    }

    /**
     * 读取应用已关联版本库的远端目录/文件树。新接口只使用 git archive，不触发 clone/cache 落盘。
     * 测试工作库仅返回当前应用同名根目录及其完整子树。
     */
    public RepositoryTreeResponse listRepositoryTree(String appId, String repositoryId, String branch, UserId currentUserId) {
        ApplicationDefinition application = existingEnabledApp(appId);
        CodeRepository repository = existingRepository(new CodeRepositoryId(repositoryId));
        ensureRepositoryLinked(application.appId(), repository.repositoryId());
        String normalizedBranch = requireText(branch, "分支不能为空", "branch");
        String privateKey = privateKeyFor(repository, currentUserId);
        List<RemoteTreeNode> nodes = gitRemoteService.listTree(
                effectiveGitUrl(repository, currentUserId),
                normalizedBranch,
                privateKey);
        if (repository.standard()) {
            nodes = nodes.stream()
                    .filter(node -> GitRemoteService.NODE_TYPE_DIRECTORY.equals(node.type()))
                    .filter(node -> application.appName().equals(node.name()) && application.appName().equals(node.path()))
                    .toList();
        }
        return new RepositoryTreeResponse(nodes.stream().map(this::treeNodeResponse).toList());
    }

    public List<ApplicationWorkspaceResponse> listWorkspaces(String appId) {
        ApplicationId applicationId = existingEnabledApp(appId).appId();
        return configurationRepository.findWorkspaces(applicationId).stream()
                .map(this::workspaceResponse)
                .toList();
    }

    public ApplicationWorkspaceResponse createWorkspace(
            String appId,
            String repositoryId,
            String branch,
            String directoryPath,
            String workspaceName) {
        ApplicationId applicationId = existingEnabledApp(appId).appId();
        CodeRepository repository = existingRepository(new CodeRepositoryId(repositoryId));
        ensureRepositoryLinked(applicationId, repository.repositoryId());
        String normalizedPath = normalizeDirectoryPath(directoryPath);
        String normalizedBranch = requireText(branch, "分支不能为空", "branch");
        // 同一应用下 (repository, branch, directory) 组合必须唯一，已存在时直接返回已有工作空间
        Optional<ApplicationWorkspace> existing = configurationRepository.findWorkspaceByLocation(
                applicationId, repository.repositoryId(), normalizedBranch, normalizedPath);
        if (existing.isPresent()) {
            return workspaceResponse(existing.get());
        }
        String resolvedName = workspaceName == null || workspaceName.isBlank()
                ? defaultWorkspaceName(normalizedPath)
                : workspaceName.trim();
        ensureWorkspaceNameUnique(applicationId, resolvedName, null);
        Instant now = Instant.now();
        ApplicationWorkspace workspace = new ApplicationWorkspace(
                new ApplicationWorkspaceId(RuntimeIdGenerator.applicationWorkspaceId()),
                applicationId,
                repository.repositoryId(),
                normalizedBranch,
                normalizedPath,
                resolvedName,
                now,
                now);
        return workspaceResponse(configurationRepository.saveWorkspace(workspace));
    }

    public ApplicationWorkspaceResponse renameWorkspace(String appId, String workspaceId, String workspaceName) {
        ApplicationId applicationId = new ApplicationId(appId);
        ApplicationWorkspace workspace = configurationRepository.findWorkspace(new ApplicationWorkspaceId(workspaceId))
                .filter(found -> found.appId().equals(applicationId))
                .orElseThrow(() -> notFound("应用工作空间不存在", "workspaceId", workspaceId));
        String normalizedName = requireText(workspaceName, "工作空间名称不能为空", "workspaceName");
        ensureWorkspaceNameUnique(applicationId, normalizedName, workspace.workspaceId());
        ApplicationWorkspace updated = workspace.rename(normalizedName, Instant.now());
        return workspaceResponse(configurationRepository.updateWorkspace(updated));
    }

    @Transactional
    public void deleteWorkspace(String appId, String workspaceId) {
        ApplicationId applicationId = new ApplicationId(appId);
        ApplicationWorkspaceId applicationWorkspaceId = new ApplicationWorkspaceId(workspaceId);
        ApplicationWorkspace workspace = configurationRepository.findWorkspace(applicationWorkspaceId)
                .filter(found -> found.appId().equals(applicationId))
                .orElseThrow(() -> notFound("应用工作空间不存在", "workspaceId", workspaceId));
        // 先级联删除关联子表数据（同步记录 → 版本副本 → 个人工作空间 → 应用版本工作空间），
        // 严格按子→父依赖顺序，避免外键约束冲突
        managedWorkspaceRepository.deleteAllByApplicationWorkspaceId(applicationWorkspaceId);
        configurationRepository.deleteWorkspace(workspace.workspaceId());
    }

    public List<SshKeyResponse> listSshKeys(UserId userId) {
        return configurationRepository.findSshKeys(userId).stream()
                .map(this::sshKeyResponse)
                .toList();
    }

    public SshKeyResponse addSshKey(UserId userId, String name, String encryptedPrivateKey,
                                      String encryptedAesKey, String encryptionNonce, String fingerprint) {
        if (!configurationRepository.findSshKeys(userId).isEmpty()) {
            throw new PlatformException(ErrorCode.CONFLICT, "每个用户最多只能保存一把 SSH key");
        }
        // 解密并校验指纹，验证前端传来的密文未被篡改
        sshKeyEncryptionService.decryptAndVerify(encryptedPrivateKey, encryptedAesKey, encryptionNonce, fingerprint);

        UserSshKey sshKey = new UserSshKey(
                new SshKeyId(RuntimeIdGenerator.sshKeyId()),
                userId,
                requireText(name, "SSH key 名称不能为空", "name"),
                fingerprint,
                encryptedPrivateKey,
                encryptedAesKey,
                encryptionNonce,
                Instant.now());
        return sshKeyResponse(configurationRepository.saveSshKey(sshKey));
    }

    public void deleteSshKey(UserId userId, String sshKeyId) {
        configurationRepository.deleteSshKey(userId, new SshKeyId(sshKeyId));
    }

    private String privateKeyFor(CodeRepository repository, UserId currentUserId) {
        if (!repository.internalDeployment() && !requiresSshKey(repository.gitUrl())) {
            return null;
        }
        UserSshKey sshKey = configurationRepository.findSshKeys(currentUserId).stream()
                .findFirst()
                .orElseThrow(() -> new PlatformException(ErrorCode.FORBIDDEN, "当前用户未配置 SSH key"));

        if (sshKey.encryptedAesKey() == null || sshKey.encryptedAesKey().isBlank()) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR,
                    "SSH key 使用的旧版加密格式，请重新添加",
                    Map.of("sshKeyId", sshKey.sshKeyId().value()));
        }

        return sshKeyEncryptionService.decrypt(
                sshKey.encryptedPrivateKey(),
                sshKey.encryptedAesKey(),
                sshKey.encryptionNonce());
    }

    private void ensureRepositoryLinked(ApplicationId appId, CodeRepositoryId repositoryId) {
        boolean linked = configurationRepository.findRepositoriesByApplication(appId).stream()
                .anyMatch(repository -> repository.repositoryId().equals(repositoryId));
        if (!linked) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "代码库未关联当前应用",
                    Map.of("appId", appId.value(), "repositoryId", repositoryId.value()));
        }
    }

    private ApplicationDefinition existingEnabledApp(String appId) {
        ApplicationDefinition application = configurationRepository.findApplication(new ApplicationId(appId))
                .orElseThrow(() -> notFound("应用不存在", "appId", appId));
        if (!application.enabled()) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "应用未启用", Map.of("appId", appId));
        }
        return application;
    }

    private CodeRepository existingRepository(CodeRepositoryId repositoryId) {
        return configurationRepository.findRepository(repositoryId)
                .orElseThrow(() -> notFound("代码库不存在", "repositoryId", repositoryId.value()));
    }

    private User existingUser(UserId userId) {
        return userRepository.findByUserId(userId)
                .orElseThrow(() -> notFound("用户不存在", "userId", userId.value()));
    }

    private PlatformException notFound(String message, String key, String value) {
        return new PlatformException(ErrorCode.NOT_FOUND, message, Map.of(key, value));
    }

    private String validateGitUrl(String gitUrl) {
        String value = requireText(gitUrl, "代码库地址不能为空", "gitUrl");
        if (isScpLikeSshUrl(value)) {
            return value;
        }
        try {
            URI uri = URI.create(value);
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getRawUserInfo() == null) {
                return value;
            }
            if ("ssh".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null) {
                return value;
            }
        } catch (IllegalArgumentException ignored) {
            // 统一落到下面的校验错误，避免把 URI 解析异常原文返回前端。
        }
        throw new PlatformException(ErrorCode.VALIDATION_ERROR, "仅支持 SSH 或 HTTPS Git URL", Map.of("gitUrl", value));
    }

    private String validateInternalGitUrl(String gitUrl) {
        String value = requireText(gitUrl, "代码库地址不能为空", "gitUrl");
        if (value.contains("://") || value.contains("@") || WHITESPACE.matcher(value).find()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "内部版本库地址必须为 host[:port]/path，不包含协议、用户和空白字符", Map.of("gitUrl", value));
        }
        int slash = value.indexOf('/');
        if (slash <= 0 || slash == value.length() - 1) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "内部版本库地址必须包含仓库路径", Map.of("gitUrl", value));
        }
        String path = value.substring(slash + 1);
        for (String segment : path.split("/")) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "内部版本库路径无效", Map.of("gitUrl", value));
            }
        }
        return value;
    }

    private String normalizeRepositoryDeploymentMode(String deploymentMode) {
        try {
            return CodeRepositoryDeploymentMode.fromValue(deploymentMode).value();
        } catch (IllegalArgumentException exception) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "版本库部署模式无效",
                    Map.of("field", "deploymentMode", "deploymentMode", deploymentMode));
        }
    }

    private String normalizeRepositoryEnglishName(String englishName, String gitUrl, String deploymentMode) {
        if ((englishName == null || englishName.isBlank())
                && CodeRepositoryDeploymentMode.INTERNAL.value().equals(deploymentMode)) {
            return normalizeRepositoryEnglishName(deriveInternalRepositoryEnglishName(gitUrl));
        }
        return normalizeRepositoryEnglishName(englishName);
    }

    private String normalizeRepositoryEnglishName(String englishName) {
        String value = requireText(englishName, "版本库英文名称不能为空", "englishName");
        if (!REPOSITORY_ENGLISH_NAME_PATTERN.matcher(value).matches()) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "版本库英文名称只能使用字母、数字和连字符，长度 1 到 128，且不能以连字符开头或结尾",
                    Map.of("field", "englishName"));
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private String deriveInternalRepositoryEnglishName(String gitUrl) {
        int slash = gitUrl.indexOf('/');
        String path = slash >= 0 ? gitUrl.substring(slash + 1) : gitUrl;
        while (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.endsWith(".git")) {
            path = path.substring(0, path.length() - ".git".length());
        }
        return path.replace('/', '-').toLowerCase(Locale.ROOT);
    }

    private String normalizeRepositoryTypeForCreate(String repositoryType, Boolean legacyStandard) {
        String value = repositoryType == null || repositoryType.isBlank()
                ? CodeRepositoryType.fromStandard(Boolean.TRUE.equals(legacyStandard)).value()
                : repositoryType.trim();
        CodeRepositoryType parsedType;
        try {
            parsedType = CodeRepositoryType.fromValue(value);
        } catch (IllegalArgumentException exception) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "版本库类型无效",
                    Map.of("field", "repositoryType", "repositoryType", value));
        }
        // 类型必须存在于通用字典表，保证前后端选项来自同一个数据源。
        dictionaryRepository.findByDictKeyAndValue(Dictionary.DICT_KEY_REPOSITORY_TYPE, parsedType.value())
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.VALIDATION_ERROR,
                        "版本库类型无效",
                        Map.of("field", "repositoryType", "repositoryType", parsedType.value())));
        return parsedType.value();
    }

    private void ensureRepositoryEnglishNameUnique(String englishName, CodeRepositoryId currentRepositoryId) {
        configurationRepository.findRepositoryByEnglishName(englishName).ifPresent(existing -> {
            if (currentRepositoryId == null || !existing.repositoryId().equals(currentRepositoryId)) {
                throw new PlatformException(
                        ErrorCode.CONFLICT,
                        "版本库英文名称已存在",
                        Map.of("repositoryId", existing.repositoryId().value(), "englishName", englishName));
            }
        });
    }

    private static boolean requiresSshKey(String gitUrl) {
        return gitUrl.startsWith("ssh://") || isScpLikeSshUrl(gitUrl);
    }

    private String effectiveGitUrl(CodeRepository repository, UserId currentUserId) {
        if (!repository.internalDeployment()) {
            return repository.gitUrl();
        }
        return repository.effectiveGitUrl(existingUser(currentUserId).unifiedAuthId());
    }

    private static boolean isScpLikeSshUrl(String gitUrl) {
        return SCP_LIKE_SSH_URL.matcher(gitUrl).matches();
    }

    private String normalizeDirectoryPath(String directoryPath) {
        String value = requireText(directoryPath, "目录路径不能为空", "directoryPath")
                .replace('\\', '/')
                .trim();
        while (value.endsWith("/") && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.equals(".") || value.contains("../") || value.startsWith("../") || value.startsWith("/")) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "目录路径无效", Map.of("directoryPath", directoryPath));
        }
        return value;
    }

    private String defaultWorkspaceName(String directoryPath) {
        int index = directoryPath.lastIndexOf('/');
        return index >= 0 ? directoryPath.substring(index + 1) : directoryPath;
    }

    private void ensureWorkspaceNameUnique(ApplicationId appId, String workspaceName, ApplicationWorkspaceId currentWorkspaceId) {
        configurationRepository.findWorkspaceByName(appId, workspaceName).ifPresent(existing -> {
            if (currentWorkspaceId == null || !existing.workspaceId().equals(currentWorkspaceId)) {
                throw new PlatformException(
                        ErrorCode.CONFLICT,
                        "同一应用下工作空间别名不能重复",
                        Map.of("workspaceName", workspaceName, "workspaceId", existing.workspaceId().value()));
            }
        });
    }

    private String normalizePrivateKey(String privateKey) {
        String value = requireText(privateKey, "SSH 私钥不能为空", "privateKey")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
        if (!value.startsWith("-----BEGIN ") || !value.contains(" PRIVATE KEY-----")) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "SSH 私钥格式无效");
        }
        return value + "\n";
    }

    private String requireText(String value, String message, String field) {
        if (value == null || value.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, message, Map.of("field", field));
        }
        return value.trim();
    }

    private ApplicationResponse applicationResponse(ApplicationDefinition application) {
        return new ApplicationResponse(application.appId().value(), application.appName(), application.enabled());
    }

    private UserResponse userResponse(User user) {
        return new UserResponse(
                user.userId().value(),
                user.username(),
                user.unifiedAuthId(),
                user.organization(),
                user.rdDepartment(),
                user.department());
    }

    private ApplicationMemberResponse memberResponse(User user) {
        return new ApplicationMemberResponse(
                user.userId().value(),
                user.username(),
                user.unifiedAuthId(),
                user.organization(),
                user.rdDepartment(),
                user.department());
    }

    private CodeRepositoryResponse repositoryResponse(CodeRepository repository) {
        return new CodeRepositoryResponse(
                repository.repositoryId().value(),
                repository.gitUrl(),
                repository.name(),
                repository.englishName(),
                repository.deploymentMode(),
                repository.repositoryType(),
                repositoryTypeLabel(repository.repositoryType()),
                repository.standard(),
                repository.createdAt(),
                repository.updatedAt());
    }

    private String repositoryTypeLabel(String repositoryType) {
        return dictionaryRepository.findByDictKeyAndValue(Dictionary.DICT_KEY_REPOSITORY_TYPE, repositoryType)
                .map(Dictionary::dictLabel)
                .orElse(repositoryType);
    }

    private ApplicationWorkspaceResponse workspaceResponse(ApplicationWorkspace workspace) {
        return new ApplicationWorkspaceResponse(
                workspace.workspaceId().value(),
                workspace.appId().value(),
                workspace.repositoryId().value(),
                workspace.branch(),
                workspace.directoryPath(),
                workspace.workspaceName(),
                workspace.createdAt(),
                workspace.updatedAt());
    }

    private RepositoryTreeNodeResponse treeNodeResponse(RemoteTreeNode node) {
        return new RepositoryTreeNodeResponse(
                node.name(),
                node.path(),
                node.type(),
                node.children().stream().map(this::treeNodeResponse).toList());
    }

    private SshKeyResponse sshKeyResponse(UserSshKey sshKey) {
        return new SshKeyResponse(sshKey.sshKeyId().value(), sshKey.name(), sshKey.fingerprint(), sshKey.createdAt());
    }
}
