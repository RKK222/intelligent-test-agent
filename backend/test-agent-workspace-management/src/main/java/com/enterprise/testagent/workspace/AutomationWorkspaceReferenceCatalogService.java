package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceGeneration;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceRepository;
import com.enterprise.testagent.domain.configuration.ApplicationDefinition;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import com.enterprise.testagent.domain.managedworkspace.AutomationWorkspaceReferenceCatalog;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspace;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 按主工作空间所属应用实时校验自动化代码库共享只读代次。 */
@Service
public class AutomationWorkspaceReferenceCatalogService implements AutomationWorkspaceReferenceCatalog {

    private static final Duration READ_LEASE_TTL = Duration.ofHours(12);
    private static final Duration READ_LEASE_REUSE_MARGIN = Duration.ofMinutes(5);
    private static final int MAX_CACHED_READ_LEASES = 10_000;

    private final ConfigurationManagementRepository configurationRepository;
    private final ManagedWorkspaceRepository managedWorkspaceRepository;
    private final ApplicationAutomationReferenceService automationReferenceService;
    private final ApplicationAutomationReferenceRepository automationRepository;
    /**
     * 文件树会频繁重建定位器。只在当前 Java 进程内短暂保留明文令牌，避免每次刷新都新增数据库租约；
     * 数据库仍只保存 SHA-256，进程重启后自然重新签发。
     */
    private final Map<ReadLeaseKey, CachedReadLease> readLeaseCache = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ReadLeaseKey, CachedReadLease> eldest) {
            return size() > MAX_CACHED_READ_LEASES;
        }
    };

    @Autowired
    public AutomationWorkspaceReferenceCatalogService(
            ConfigurationManagementRepository configurationRepository,
            ManagedWorkspaceRepository managedWorkspaceRepository,
            ApplicationAutomationReferenceService automationReferenceService,
            ApplicationAutomationReferenceRepository automationRepository) {
        this.configurationRepository = Objects.requireNonNull(configurationRepository);
        this.managedWorkspaceRepository = Objects.requireNonNull(managedWorkspaceRepository);
        this.automationReferenceService = Objects.requireNonNull(automationReferenceService);
        this.automationRepository = Objects.requireNonNull(automationRepository);
    }

    @Override
    public Reference resolveGeneration(
            UserId userId,
            WorkspaceId hostWorkspaceId,
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation) {
        return resolveGeneration(userId, hostWorkspaceId, appId, repositoryId, generation, null);
    }

    @Override
    public Reference resolveGeneration(
            UserId userId,
            WorkspaceId hostWorkspaceId,
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation,
            String readLeaseToken) {
        ApplicationId hostAppId = resolveHostApplication(hostWorkspaceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.FORBIDDEN, "当前工作空间不支持自动化引用"));
        if (!hostAppId.equals(appId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "自动化引用应用与当前工作空间不匹配");
        }
        requireApplicationMember(appId, userId);
        CodeRepository repository = configurationRepository.findRepositoriesByApplication(appId).stream()
                .filter(candidate -> candidate.repositoryId().equals(repositoryId))
                .filter(this::isAutomationRepository)
                .findFirst()
                .orElseThrow(() -> new PlatformException(ErrorCode.FORBIDDEN, "自动化代码库未关联当前应用"));
        boolean current = automationReferenceService.isActiveGeneration(appId, repositoryId, generation);
        if (!current) {
            requireReadLease(readLeaseToken, userId, hostWorkspaceId, appId, repositoryId, generation);
        }
        ApplicationAutomationReferenceGeneration configuration = automationReferenceService
                .requireGeneration(appId, repositoryId, generation);
        Path selectedDirectory = automationReferenceService.requireReadyLocalDirectory(
                appId, repositoryId, generation);
        return new Reference(
                appId,
                repositoryId,
                generation,
                repository.name(),
                repository.name(),
                configuration.branch(),
                configuration.targetCommitHash(),
                selectedDirectory.toString(),
                configuration.directoryPath(),
                automationReferenceService.logicalConfigurationPath(appId, repository, configuration),
                configuration.description(),
                current,
                current ? null : readLeaseToken);
    }

    @Override
    public synchronized String issueReadLease(UserId userId, WorkspaceId hostWorkspaceId, Reference reference) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(hostWorkspaceId, "hostWorkspaceId must not be null");
        Objects.requireNonNull(reference, "reference must not be null");
        if (!reference.current()) {
            throw new PlatformException(ErrorCode.CONFLICT, "只能为当前自动化引用创建只读标签租约");
        }
        Instant now = Instant.now();
        ReadLeaseKey cacheKey = new ReadLeaseKey(
                userId,
                hostWorkspaceId,
                reference.applicationId(),
                reference.repositoryId(),
                reference.generation());
        CachedReadLease cached = readLeaseCache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(now.plus(READ_LEASE_REUSE_MARGIN))) {
            return cached.token();
        }
        String token = "arl_" + UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = now.plus(READ_LEASE_TTL);
        if (!automationRepository.saveReadLease(
                sha256(token),
                userId,
                hostWorkspaceId,
                reference.applicationId(),
                reference.repositoryId(),
                reference.generation(),
                expiresAt,
                now)) {
            readLeaseCache.remove(cacheKey);
            throw new PlatformException(ErrorCode.CONFLICT, "自动化引用配置已切换，请刷新工作区");
        }
        readLeaseCache.put(cacheKey, new CachedReadLease(token, expiresAt));
        return token;
    }

    Optional<ApplicationId> resolveHostApplication(WorkspaceId workspaceId) {
        Optional<PersonalWorkspace> personal = managedWorkspaceRepository
                .findPersonalWorkspaceByRuntimeWorkspace(workspaceId);
        if (personal.isPresent()) {
            ApplicationWorkspaceVersion version = managedWorkspaceRepository
                    .findVersion(personal.get().versionId()).orElse(null);
            return isAutomationVersion(version) ? Optional.empty() : Optional.of(personal.get().appId());
        }
        Optional<ApplicationWorkspaceVersion> direct = managedWorkspaceRepository
                .findVersionByRuntimeWorkspace(workspaceId);
        if (direct.isPresent()) {
            return isAutomationVersion(direct.get()) ? Optional.empty() : Optional.of(direct.get().appId());
        }
        return managedWorkspaceRepository.findVersionReplicaByRuntimeWorkspace(workspaceId)
                .flatMap(replica -> managedWorkspaceRepository.findVersion(replica.versionId()))
                .filter(version -> !isAutomationVersion(version))
                .map(ApplicationWorkspaceVersion::appId);
    }

    private void requireApplicationMember(ApplicationId appId, UserId userId) {
        ApplicationDefinition application = configurationRepository.findApplication(appId)
                .filter(ApplicationDefinition::enabled)
                .orElseThrow(() -> new PlatformException(ErrorCode.FORBIDDEN, "应用不可用"));
        if (userId == null || !configurationRepository.isActiveMember(application.appId(), userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "无应用自动化代码库访问权限");
        }
    }

    private boolean isAutomationVersion(ApplicationWorkspaceVersion version) {
        return version != null
                && configurationRepository.findRepository(version.repositoryId())
                        .map(this::isAutomationRepository)
                        .orElse(false);
    }

    private boolean isAutomationRepository(CodeRepository repository) {
        return repository != null
                && CodeRepositoryType.AUTOMATION_CODE_REPOSITORY.value().equals(repository.repositoryType());
    }

    private void requireReadLease(
            String token,
            UserId userId,
            WorkspaceId workspaceId,
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation) {
        Instant now = Instant.now();
        String normalized = token == null ? "" : token.trim();
        if (normalized.isEmpty() || !automationRepository.renewReadLease(
                sha256(normalized),
                userId,
                workspaceId,
                appId,
                repositoryId,
                generation,
                now.plus(READ_LEASE_TTL),
                now)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "历史自动化引用标签已失效，请刷新工作区");
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record ReadLeaseKey(
            UserId userId,
            WorkspaceId workspaceId,
            ApplicationId applicationId,
            CodeRepositoryId repositoryId,
            long generation) {
    }

    private record CachedReadLease(String token, Instant expiresAt) {
    }
}
