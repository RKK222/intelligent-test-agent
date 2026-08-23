package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.git.GitWorkspaceService;
import com.enterprise.testagent.domain.localclient.LocalClientPublicCapabilityModels;
import com.enterprise.testagent.domain.localclient.LocalClientPublicCapabilityRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 公共配置发布后的客户端完整能力包生成与 SERVER_ONLY 降级入口。 */
@Service
public class PublicClientCapabilityPackageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicClientCapabilityPackageService.class);
    public static final String PROTOCOL_CAPABILITY = "PUBLIC_CAPABILITY_SYNC_V1";

    private final LocalClientPublicCapabilityRepository repository;
    private final GitWorkspaceService gitWorkspaceService;
    private final ObjectMapper objectMapper;
    private final PublicClientCapabilityPackageBuilder builder;
    private final Path portableLock;
    private final Path portableNodeModules;
    private final Clock clock;

    @Autowired
    public PublicClientCapabilityPackageService(
            LocalClientPublicCapabilityRepository repository,
            ObjectMapper objectMapper,
            @Value("${test-agent.local-client.public-capabilities.portable-lock:../deploy/internal/opencode-node-runtime.package-lock.json}")
            String portableLock,
            @Value("${test-agent.local-client.public-capabilities.node-modules:../.testagent/agent-opencode/.config/opencode/node_modules}")
            String portableNodeModules) {
        this(repository, new GitWorkspaceService(), objectMapper, Path.of(portableLock), Path.of(portableNodeModules),
                Clock.systemUTC());
    }

    PublicClientCapabilityPackageService(
            LocalClientPublicCapabilityRepository repository,
            GitWorkspaceService gitWorkspaceService,
            ObjectMapper objectMapper,
            Path portableLock,
            Path portableNodeModules,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.gitWorkspaceService = Objects.requireNonNull(gitWorkspaceService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.builder = new PublicClientCapabilityPackageBuilder(objectMapper);
        this.portableLock = resolveRuntimePath(portableLock);
        this.portableNodeModules = resolvePortableNodeModules(portableNodeModules);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * 能力包失败不得回滚已经推送的服务器公共配置；兼容性错误会持久化 SERVER_ONLY。
     * 仓储本身故障只记录日志，等待后续对同 commit 的管理补偿，不能伪造 AVAILABLE。
     */
    @Transactional
    public LocalClientPublicCapabilityModels.Release generateForPublishedCommit(
            Path sharedGitRoot,
            String sourceCommit,
            String traceId) {
        LocalClientPublicCapabilityModels.Release existing = repository
                .findReleaseBySourceCommit(sourceCommit.toLowerCase())
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        Instant now = Instant.now(clock);
        LocalClientPublicCapabilityModels.Release release;
        try {
            String head = gitWorkspaceService.headCommit(sharedGitRoot);
            if (!sourceCommit.equalsIgnoreCase(head)) {
                throw PublicClientCapabilityPackageBuilder.incompatible(
                        "PUBLIC_COMMIT_NOT_CHECKED_OUT", "公共运行副本未切换到待打包 commit");
            }
            LocalClientPublicCapabilityModels.Release previous = repository.findLatestAvailableRelease().orElse(null);
            PublicClientCapabilityPackageBuilder.BuildResult result = builder.build(
                    sharedGitRoot.resolve("opencode"), portableLock, portableNodeModules,
                    sourceCommit.toLowerCase(), previous, now);
            release = new LocalClientPublicCapabilityModels.Release(
                    result.sourceCommit(), result.bundleDigest(), result.artifactSha256(),
                    LocalClientPublicCapabilityModels.Compatibility.AVAILABLE, null,
                    result.manifestJson(), result.changeSummaryJson(), result.counts(), result.requiresRestart(),
                    result.artifact(), result.artifact().length, result.uncompressedSize(), result.fileCount(), now);
        } catch (PublicClientCapabilityPackageBuilder.CompatibilityException exception) {
            release = serverOnly(sourceCommit, exception.errorCode(), exception.getMessage(), now);
        } catch (RuntimeException exception) {
            release = serverOnly(sourceCommit, "PACKAGE_BUILD_FAILED", "能力包生成失败", now);
            LOGGER.warn("event=public_capability_package_build_failed sourceCommit={} traceId={}",
                    sourceCommit, traceId, exception);
        }
        repository.insertRelease(release);
        if (release.compatibility() == LocalClientPublicCapabilityModels.Compatibility.AVAILABLE) {
            repository.markUpdateAvailableForCapableInstances(
                    release.sourceCommit(), release.bundleDigest(), now, PROTOCOL_CAPABILITY);
        }
        LOGGER.info(
                "event=public_capability_package_persisted sourceCommit={} compatibility={} bundleDigest={} errorCode={} traceId={}",
                sourceCommit, release.compatibility(), release.bundleDigest(), release.errorCode(), traceId);
        return release;
    }

    @Transactional(readOnly = true)
    public LocalClientPublicCapabilityModels.Release latestRelease() {
        return repository.findLatestRelease().orElse(null);
    }

    @Transactional(readOnly = true)
    public LocalClientPublicCapabilityModels.Release releaseForCommit(String sourceCommit) {
        if (sourceCommit == null || sourceCommit.isBlank()) {
            return null;
        }
        return repository.findReleaseBySourceCommit(sourceCommit.toLowerCase()).orElse(null);
    }

    /** API 下载入口只允许取得可安装制品，避免 Controller 越过应用服务直接访问仓储。 */
    @Transactional(readOnly = true)
    public LocalClientPublicCapabilityModels.Release availableReleaseForDigest(String bundleDigest) {
        if (bundleDigest == null || bundleDigest.isBlank()) {
            return null;
        }
        return repository.findReleaseByDigest(bundleDigest.toLowerCase())
                .filter(release -> release.compatibility()
                        == LocalClientPublicCapabilityModels.Compatibility.AVAILABLE)
                .orElse(null);
    }

    private LocalClientPublicCapabilityModels.Release serverOnly(
            String sourceCommit,
            String errorCode,
            String message,
            Instant now) {
        try {
            ObjectNode manifest = objectMapper.createObjectNode();
            manifest.put("schemaVersion", PublicClientCapabilityPackageBuilder.SCHEMA_VERSION);
            manifest.put("sourceCommit", sourceCommit.toLowerCase());
            manifest.put("protocolCapability", PROTOCOL_CAPABILITY);
            manifest.put("clientCompatibility", "SERVER_ONLY");
            manifest.put("errorCode", errorCode);
            ObjectNode summary = objectMapper.createObjectNode();
            summary.put("message", safeMessage(message));
            String digest = sha256((sourceCommit.toLowerCase() + ":" + errorCode).getBytes(StandardCharsets.UTF_8));
            manifest.put("bundleDigest", digest);
            return new LocalClientPublicCapabilityModels.Release(
                    sourceCommit.toLowerCase(), digest, null,
                    LocalClientPublicCapabilityModels.Compatibility.SERVER_ONLY, errorCode,
                    objectMapper.writeValueAsString(manifest), objectMapper.writeValueAsString(summary),
                    new LocalClientPublicCapabilityModels.Counts(0, 0, 0), false, null, 0, 0, 0, now);
        } catch (Exception exception) {
            throw new IllegalStateException("server-only capability manifest could not be serialized", exception);
        }
    }

    private static String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "客户端能力包不可用";
        }
        return message.length() > 512 ? message.substring(0, 512) : message;
    }

    /** 本地开发可能从仓库根或 backend 子目录启动；生产应始终配置显式绝对路径。 */
    private static Path resolveRuntimePath(Path configured) {
        Path direct = configured.toAbsolutePath().normalize();
        if (java.nio.file.Files.exists(direct)) {
            return direct;
        }
        String value = configured.toString();
        if (value.startsWith("../")) {
            Path fromRoot = Path.of(value.substring(3)).toAbsolutePath().normalize();
            if (java.nio.file.Files.exists(fromRoot)) {
                return fromRoot;
            }
        }
        return direct;
    }

    /** 企业 systemd 从安装根启动，纯依赖位于同批次 programs；本地开发仍优先使用配置路径。 */
    private static Path resolvePortableNodeModules(Path configured) {
        Path resolved = resolveRuntimePath(configured);
        if (java.nio.file.Files.exists(resolved)) {
            return resolved;
        }
        Path installedPrograms = Path.of("programs/opencode/node_modules").toAbsolutePath().normalize();
        return java.nio.file.Files.exists(installedPrograms) ? installedPrograms : resolved;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
