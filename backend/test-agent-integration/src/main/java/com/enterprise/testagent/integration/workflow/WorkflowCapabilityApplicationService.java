package com.enterprise.testagent.integration.workflow;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.GitRemoteService;
import com.enterprise.testagent.common.git.SshKeyEncryptionService;
import com.enterprise.testagent.domain.auth.TokenSessionMarkerStore;
import com.enterprise.testagent.domain.configuration.ApplicationDefinition;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.configuration.UserSshKey;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.dictionary.DictionaryRepository;
import com.enterprise.testagent.domain.dictionary.UserRoleRepository;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.workflowcapability.CheckoutTicketPayload;
import com.enterprise.testagent.domain.workflowcapability.WorkflowCapabilityStore;
import com.enterprise.testagent.domain.workflowcapability.WorkflowModelGrantPayload;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Java仅提供平台主数据、SSH票据和模型委托，不创建或查询任何工作流会话、任务、报告、事件。
 */
@Service
public class WorkflowCapabilityApplicationService {

    private static final String MODEL_SCOPE = "workflow-model-gateway";
    private static final List<String> REGISTERED_ANALYZERS =
            List.of("agentscope-intent", "agentscope-report-qa", "agentscope-synthesis", "codex", "opencode");
    private static final List<String> SYSTEM_ANALYZERS =
            List.of("agentscope-intent", "agentscope-report-qa", "agentscope-synthesis");
    private final UserRepository users;
    private final UserRoleRepository userRoles;
    private final DictionaryRepository dictionaries;
    private final ConfigurationManagementRepository configuration;
    private final SshKeyEncryptionService sshKeys;
    private final TokenSessionMarkerStore markers;
    private final WorkflowCapabilityStore store;
    private final WorkflowCapabilityProperties properties;
    private final RunnerPublicKeyEncryptionService runnerEncryption;
    private final GitRemoteService git;
    private final Clock clock;
    private final WorkflowCapabilityTokenFactory tokens;

    @Autowired
    public WorkflowCapabilityApplicationService(
            UserRepository users,
            UserRoleRepository userRoles,
            DictionaryRepository dictionaries,
            ConfigurationManagementRepository configuration,
            SshKeyEncryptionService sshKeys,
            TokenSessionMarkerStore markers,
            WorkflowCapabilityStore store,
            WorkflowCapabilityProperties properties,
            RunnerPublicKeyEncryptionService runnerEncryption) {
        this(
                users,
                userRoles,
                dictionaries,
                configuration,
                sshKeys,
                markers,
                store,
                properties,
                runnerEncryption,
                new GitRemoteService(),
                Clock.systemUTC(),
                new SecureWorkflowCapabilityTokenFactory());
    }

    WorkflowCapabilityApplicationService(
            UserRepository users,
            UserRoleRepository userRoles,
            DictionaryRepository dictionaries,
            ConfigurationManagementRepository configuration,
            SshKeyEncryptionService sshKeys,
            TokenSessionMarkerStore markers,
            WorkflowCapabilityStore store,
            WorkflowCapabilityProperties properties,
            RunnerPublicKeyEncryptionService runnerEncryption,
            GitRemoteService git,
            Clock clock,
            WorkflowCapabilityTokenFactory tokens) {
        this.users = Objects.requireNonNull(users);
        this.userRoles = Objects.requireNonNull(userRoles);
        this.dictionaries = Objects.requireNonNull(dictionaries);
        this.configuration = Objects.requireNonNull(configuration);
        this.sshKeys = Objects.requireNonNull(sshKeys);
        this.markers = Objects.requireNonNull(markers);
        this.store = Objects.requireNonNull(store);
        this.properties = Objects.requireNonNull(properties);
        this.runnerEncryption = Objects.requireNonNull(runnerEncryption);
        this.git = Objects.requireNonNull(git);
        this.clock = Objects.requireNonNull(clock);
        this.tokens = Objects.requireNonNull(tokens);
    }

    public List<ApplicationRepositoryGroup> listRepositories(WorkflowCapabilityCaller caller) {
        User user = requireCaller(caller);
        return configuration.findApplicationsByMember(user.userId()).stream()
                .filter(ApplicationDefinition::enabled)
                .map(application -> new ApplicationRepositoryGroup(
                        application.appId().value(),
                        application.appName(),
                        configuration.findRepositoriesByApplication(application.appId()).stream()
                                .filter(this::isApplicationCodeRepository)
                                .map(this::repositoryView)
                                .toList()))
                .filter(group -> !group.repositories().isEmpty())
                .toList();
    }

    public List<AuthorizedRepository> authorizeRepositories(
            WorkflowCapabilityCaller caller,
            List<String> repositoryIds) {
        requireCaller(caller);
        List<String> requested = repositoryIds == null ? List.of() : repositoryIds;
        LinkedHashSet<String> distinct = new LinkedHashSet<>(requested);
        if (distinct.isEmpty() || distinct.size() != requested.size()) {
            throw validation("代码库列表不能为空且不能重复");
        }
        return distinct.stream()
                .map(value -> requireAccessibleRepository(caller.userId(), new CodeRepositoryId(value)))
                .map(repository -> new AuthorizedRepository(
                        repository.repositoryId().value(),
                        repository.name(),
                        repository.englishName()))
                .toList();
    }

    public List<BranchView> listBranches(WorkflowCapabilityCaller caller, String repositoryId) {
        User user = requireCaller(caller);
        CodeRepository repository = requireAccessibleRepository(caller.userId(), new CodeRepositoryId(repositoryId));
        String privateKey = privateKeyFor(repository, caller.userId());
        String remoteUrl = repository.effectiveGitUrl(user.unifiedAuthId());
        String defaultBranch = git.resolveDefaultBranch(remoteUrl, privateKey).orElse(null);
        return git.listBranches(remoteUrl, privateKey).stream()
                .map(branch -> new BranchView(branch, branch.equals(defaultBranch)))
                .toList();
    }

    public CheckoutTicketIssue issueCheckoutTicket(
            WorkflowCapabilityCaller caller,
            String repositoryId,
            String taskId,
            String runId,
            String runnerId,
            String runnerPublicKey,
            String targetBranch,
            String baselineBranch) {
        User user = requireCaller(caller);
        CodeRepository repository = requireAccessibleRepository(caller.userId(), new CodeRepositoryId(repositoryId));
        validateOpaqueId(taskId, "taskId");
        validateOpaqueId(runId, "runId");
        requireRunner(runnerId, runnerPublicKey);
        String privateKey = privateKeyFor(repository, caller.userId());
        String remoteUrl = repository.effectiveGitUrl(user.unifiedAuthId());
        List<String> branches = git.listBranches(remoteUrl, privateKey);
        String target = normalize(targetBranch);
        if (target == null || !branches.contains(target)) {
            throw validation("目标分支不存在");
        }
        String baseline = normalize(baselineBranch);
        if (baseline == null) {
            baseline = git.resolveDefaultBranch(remoteUrl, privateKey)
                    .orElseThrow(() -> new PlatformException(
                            ErrorCode.VALIDATION_ERROR,
                            "默认分支无法自动解析，请选择基线分支",
                            Map.of("reason", "BASELINE_REQUIRED", "branches", branches)));
        }
        if (!branches.contains(baseline)) {
            throw validation("基线分支不存在");
        }
        String ticket = tokens.token("wfcheckout_");
        Instant expiresAt = clock.instant().plus(properties.getCheckoutTicketTtl());
        store.saveCheckoutTicket(
                sha256(ticket),
                new CheckoutTicketPayload(
                        caller.userId().value(),
                        caller.sessionDigest(),
                        repositoryId,
                        taskId,
                        runId,
                        runnerId,
                        runnerPublicKey,
                        target,
                        baseline,
                        expiresAt),
                properties.getCheckoutTicketTtl());
        return new CheckoutTicketIssue(ticket, expiresAt);
    }

    public CheckoutMaterial consumeCheckoutTicket(
            String ticket,
            String taskId,
            String runId,
            String runnerId) {
        if (ticket == null || !ticket.startsWith("wfcheckout_")) {
            throw unauthenticated("checkout ticket无效");
        }
        CheckoutTicketPayload payload = store.consumeCheckoutTicket(sha256(ticket))
                .orElseThrow(() -> unauthenticated("checkout ticket无效或已使用"));
        if (!payload.expiresAt().isAfter(clock.instant())
                || !payload.taskId().equals(taskId)
                || !payload.runId().equals(runId)
                || !payload.runnerId().equals(runnerId)
                || !runnerId.equals(properties.getRunnerId())
                || !markers.isActiveForUser(payload.sessionDigest(), new UserId(payload.userId()))) {
            throw unauthenticated("checkout ticket范围无效");
        }
        User user = requireActiveUser(new UserId(payload.userId()));
        CodeRepository repository = requireAccessibleRepository(user.userId(), new CodeRepositoryId(payload.repositoryId()));
        String privateKey = privateKeyFor(repository, user.userId());
        String encrypted = privateKey == null
                ? null
                : runnerEncryption.encrypt(privateKey, payload.runnerPublicKey());
        return new CheckoutMaterial(
                repository.repositoryId().value(),
                repository.effectiveGitUrl(user.unifiedAuthId()),
                payload.baselineBranch(),
                payload.targetBranch(),
                encrypted,
                authorizedSubmodules(user, repository.repositoryId(), payload.runnerPublicKey()));
    }

    /**
     * Runner只拿到当前用户在启用应用内仍可访问的平台仓库；GitWorkspace随后按.gitmodules URL精确匹配，
     * 未匹配项不会拉取。凭据仍以Runner固定公钥封装，Python工作流服务不可见。
     */
    private List<AuthorizedSubmoduleMaterial> authorizedSubmodules(
            User user,
            CodeRepositoryId rootRepositoryId,
            String runnerPublicKey) {
        Map<String, CodeRepository> distinct = new LinkedHashMap<>();
        configuration.findApplicationsByMember(user.userId()).stream()
                .filter(ApplicationDefinition::enabled)
                .flatMap(application -> configuration.findRepositoriesByApplication(application.appId()).stream())
                .filter(this::isApplicationCodeRepository)
                .filter(repository -> !repository.repositoryId().equals(rootRepositoryId))
                .forEach(repository -> distinct.putIfAbsent(repository.repositoryId().value(), repository));
        return distinct.values().stream()
                .map(repository -> requireAccessibleRepository(user.userId(), repository.repositoryId()))
                .map(repository -> {
                    String privateKey = privateKeyFor(repository, user.userId());
                    return new AuthorizedSubmoduleMaterial(
                            repository.repositoryId().value(),
                            repository.effectiveGitUrl(user.unifiedAuthId()),
                            privateKey == null ? null : runnerEncryption.encrypt(privateKey, runnerPublicKey));
                })
                .toList();
    }

    public ModelGrantIssue issueModelGrant(
            WorkflowCapabilityCaller caller,
            String taskId,
            String runId,
            List<String> analyzerIds) {
        User user = requireCaller(caller);
        validateOpaqueId(taskId, "taskId");
        validateOpaqueId(runId, "runId");
        List<String> analyzers = validateAnalyzers(analyzerIds);
        String grantId = tokens.token("wfgrantid_");
        String grant = tokens.token("wfg_");
        Instant expiresAt = clock.instant().plus(properties.getModelGrantTtl());
        boolean saved = store.saveModelGrant(
                grantId,
                sha256(grant),
                new WorkflowModelGrantPayload(
                        grantId,
                        user.userId().value(),
                        user.unifiedAuthId(),
                        caller.sessionDigest(),
                        WorkflowCapabilityHmacAuthenticator.CLIENT_ID,
                        taskId,
                        runId,
                        analyzers,
                        expiresAt),
                properties.getModelGrantTtl());
        if (!saved) {
            throw unauthenticated("workflow运行已取消，不能签发模型委托");
        }
        return new ModelGrantIssue(grantId, grant, properties.getModelGatewayUrl(), expiresAt);
    }

    public ModelGrantRefresh refreshModelGrant(
            WorkflowCapabilityCaller caller,
            String grantId,
            String taskId,
            String runId) {
        WorkflowModelGrantPayload existing = requireOwnedGrant(caller, grantId, taskId, runId);
        Instant expiresAt = clock.instant().plus(properties.getModelGrantTtl());
        WorkflowModelGrantPayload refreshed = new WorkflowModelGrantPayload(
                existing.grantId(),
                existing.userId(),
                existing.unifiedAuthId(),
                existing.sessionDigest(),
                existing.clientId(),
                existing.taskId(),
                existing.runId(),
                existing.analyzerIds(),
                expiresAt);
        if (!store.refreshModelGrant(grantId, refreshed, properties.getModelGrantTtl())) {
            throw unauthenticated("workflow模型委托已失效");
        }
        // 不返回也不更换原始grant；运行中的Codex/OpenCode继续使用同一Bearer。
        return new ModelGrantRefresh(grantId, properties.getModelGatewayUrl(), expiresAt);
    }

    public void revokeModelGrant(
            WorkflowCapabilityCaller caller,
            String grantId,
            String taskId,
            String runId) {
        requireOwnedGrant(caller, grantId, taskId, runId);
        store.revokeModelGrant(grantId);
    }

    /** 取消run时撤销其全部短期grant；逐项校验归属，禁止凭猜测ID跨用户撤销。 */
    public void revokeModelGrants(
            WorkflowCapabilityCaller caller,
            String taskId,
            String runId) {
        requireCaller(caller);
        validateOpaqueId(taskId, "taskId");
        validateOpaqueId(runId, "runId");
        List<WorkflowModelGrantPayload> grants = store.findModelGrants(taskId, runId);
        boolean scopeMismatch = grants.stream().anyMatch(payload ->
                !payload.userId().equals(caller.userId().value())
                        || !payload.sessionDigest().equals(caller.sessionDigest())
                        || !payload.taskId().equals(taskId)
                        || !payload.runId().equals(runId));
        if (scopeMismatch) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "workflow模型委托范围不匹配");
        }
        store.revokeModelGrants(taskId, runId, properties.getModelGrantTtl());
    }

    public WorkflowModelIdentity authenticateModelGrant(String grant) {
        if (grant == null || !grant.startsWith("wfg_")) {
            throw unauthenticated("workflow模型委托无效");
        }
        WorkflowModelGrantPayload payload = store.findModelGrant(sha256(grant))
                .orElseThrow(() -> unauthenticated("workflow模型委托无效"));
        if (!payload.expiresAt().isAfter(clock.instant())
                || !WorkflowCapabilityHmacAuthenticator.CLIENT_ID.equals(payload.clientId())
                || !markers.isActiveForUser(payload.sessionDigest(), new UserId(payload.userId()))) {
            throw unauthenticated("workflow模型委托已失效");
        }
        User user = requireActiveUser(new UserId(payload.userId()));
        if (!Objects.equals(payload.unifiedAuthId(), user.unifiedAuthId())) {
            throw unauthenticated("workflow模型委托已失效");
        }
        return new WorkflowModelIdentity(user.userId().value(), user.unifiedAuthId(), MODEL_SCOPE);
    }

    public boolean verifySuperAdmin(WorkflowCapabilityCaller caller) {
        User user = requireCaller(caller);
        return userRoles.findByUserId(user.userId()).stream()
                .map(role -> dictionaries.findByDictId(role.dictId()))
                .flatMap(java.util.Optional::stream)
                .anyMatch(dictionary -> Dictionary.DICT_KEY_ROLE.equals(dictionary.dictKey())
                        && Dictionary.ROLE_SUPER_ADMIN.equals(dictionary.dictValue()));
    }

    private WorkflowModelGrantPayload requireOwnedGrant(
            WorkflowCapabilityCaller caller,
            String grantId,
            String taskId,
            String runId) {
        User currentUser = requireCaller(caller);
        WorkflowModelGrantPayload payload = store.findModelGrantById(grantId)
                .orElseThrow(() -> unauthenticated("workflow模型委托无效"));
        if (!payload.userId().equals(caller.userId().value())
                || !payload.sessionDigest().equals(caller.sessionDigest())
                || !payload.unifiedAuthId().equals(currentUser.unifiedAuthId())
                || !payload.taskId().equals(taskId)
                || !payload.runId().equals(runId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "workflow模型委托范围不匹配");
        }
        return payload;
    }

    private User requireCaller(WorkflowCapabilityCaller caller) {
        if (!markers.isActiveForUser(caller.sessionDigest(), caller.userId())) {
            throw unauthenticated("平台会话已失效");
        }
        return requireActiveUser(caller.userId());
    }

    private User requireActiveUser(UserId userId) {
        return users.findByUserId(userId)
                .filter(User::canLogin)
                .orElseThrow(() -> unauthenticated("平台用户不可用"));
    }

    private CodeRepository requireAccessibleRepository(UserId userId, CodeRepositoryId repositoryId) {
        CodeRepository repository = configuration.findRepository(repositoryId)
                .filter(this::isApplicationCodeRepository)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "代码库不存在"));
        boolean accessible = configuration.findApplicationsByRepository(repositoryId).stream()
                .filter(ApplicationDefinition::enabled)
                .anyMatch(application -> configuration.isActiveMember(application.appId(), userId));
        if (!accessible) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "无权访问代码库");
        }
        return repository;
    }

    private boolean isApplicationCodeRepository(CodeRepository repository) {
        return CodeRepositoryType.APPLICATION_CODE_REPOSITORY.value().equals(repository.repositoryType());
    }

    private RepositoryView repositoryView(CodeRepository repository) {
        return new RepositoryView(repository.repositoryId().value(), repository.name(), repository.englishName());
    }

    private String privateKeyFor(CodeRepository repository, UserId userId) {
        if (!repository.internalDeployment() && !requiresSshKey(repository.gitUrl())) {
            return null;
        }
        UserSshKey sshKey = configuration.findSshKeys(userId).stream()
                .findFirst()
                .orElseThrow(() -> new PlatformException(ErrorCode.FORBIDDEN, "当前用户未配置SSH key"));
        if (sshKey.encryptedAesKey() == null || sshKey.encryptedAesKey().isBlank()) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "SSH key使用旧版加密格式，请重新添加");
        }
        return sshKeys.decrypt(
                sshKey.encryptedPrivateKey(),
                sshKey.encryptedAesKey(),
                sshKey.encryptionNonce());
    }

    private void requireRunner(String runnerId, String runnerPublicKey) {
        if (!Objects.equals(properties.getRunnerId(), runnerId)
                || !runnerEncryption.samePublicKey(
                        properties.configuredRunnerPublicKey(), runnerPublicKey)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "Runner身份或公钥不受信任");
        }
    }

    private List<String> validateAnalyzers(List<String> values) {
        List<String> requested = values == null ? List.of() : values;
        LinkedHashSet<String> distinct = new LinkedHashSet<>(requested);
        long systemCount = distinct.stream().filter(SYSTEM_ANALYZERS::contains).count();
        long codeAnalyzerCount = distinct.size() - systemCount;
        if (distinct.isEmpty()
                || distinct.size() != requested.size()
                || !REGISTERED_ANALYZERS.containsAll(distinct)
                || systemCount > 1
                || codeAnalyzerCount > 3) {
            throw validation("模型委托最多包含3个代码智能体和1个已注册系统智能体，且ID不得重复");
        }
        return List.copyOf(distinct);
    }

    private static boolean requiresSshKey(String gitUrl) {
        if (gitUrl == null || gitUrl.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(gitUrl);
            String scheme = uri.getScheme();
            return scheme == null || "ssh".equalsIgnoreCase(scheme) || gitUrl.contains("@") && gitUrl.contains(":");
        } catch (IllegalArgumentException exception) {
            return gitUrl.contains("@") && gitUrl.contains(":");
        }
    }

    private static void validateOpaqueId(String value, String field) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{8,128}")) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, field + "格式无效");
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static PlatformException validation(String message) {
        return new PlatformException(ErrorCode.VALIDATION_ERROR, message);
    }

    private static PlatformException unauthenticated(String message) {
        return new PlatformException(ErrorCode.UNAUTHENTICATED, message);
    }

    public record RepositoryView(String id, String name, String englishName) {
    }

    public record ApplicationRepositoryGroup(
            String applicationId,
            String applicationName,
            List<RepositoryView> repositories) {
    }

    public record AuthorizedRepository(String repositoryId, String name, String englishName) {
    }

    public record BranchView(String name, @JsonProperty("default") boolean defaultValue) {
    }

    public record CheckoutTicketIssue(String ticketId, Instant expiresAt) {
    }

    public record CheckoutMaterial(
            String repositoryId,
            String remoteUrl,
            String defaultBranch,
            String targetBranch,
            String encryptedPrivateKey,
            List<AuthorizedSubmoduleMaterial> authorizedSubmodules) {
        public CheckoutMaterial {
            authorizedSubmodules = List.copyOf(authorizedSubmodules);
        }
    }

    public record AuthorizedSubmoduleMaterial(
            String repositoryId,
            String remoteUrl,
            String encryptedPrivateKey) {
    }

    public record ModelGrantIssue(
            String grantId,
            String grant,
            String gatewayUrl,
            Instant expiresAt) {
    }

    public record ModelGrantRefresh(
            String grantId,
            String gatewayUrl,
            Instant expiresAt) {
    }
}
