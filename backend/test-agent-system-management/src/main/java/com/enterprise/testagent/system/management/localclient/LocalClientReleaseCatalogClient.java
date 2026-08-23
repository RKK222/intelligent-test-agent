package com.enterprise.testagent.system.management.localclient;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.localclient.LocalClientDownloadTrust;
import com.enterprise.testagent.common.localclient.LocalClientReleaseVersion;
import com.enterprise.testagent.domain.localclient.LocalClientVersionModels;
import com.enterprise.testagent.domain.localclient.LocalClientVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 从不可信 catalog 发现版本，再逐个下载并验签不可变 release manifest。 */
@Service
public class LocalClientReleaseCatalogClient {

    private static final int MAX_CATALOG_BYTES = 1024 * 1024;
    private static final int MAX_MANIFEST_BYTES = 1024 * 1024;
    private static final int MAX_SIGNATURE_BYTES = 16 * 1024;
    private static final Set<String> REQUIRED_ARTIFACTS = Set.of("CLIENT_JAR", "JDK", "OPENCODE");
    private static final String PUBLIC_CAPABILITIES_ARTIFACT = "PUBLIC_CAPABILITIES";

    private final LocalClientVersionRepository repository;
    private final LocalClientReleaseCatalogProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public LocalClientReleaseCatalogClient(
            LocalClientVersionRepository repository,
            LocalClientReleaseCatalogProperties properties,
            ObjectMapper objectMapper) {
        this(repository, properties, objectMapper, Clock.systemUTC());
    }

    LocalClientReleaseCatalogClient(
            LocalClientVersionRepository repository,
            LocalClientReleaseCatalogProperties properties,
            ObjectMapper objectMapper,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public SyncResult sync() {
        LocalClientDownloadTrust trust = trust();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(validDuration(properties.getConnectTimeout(), "connectTimeout"))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        Catalog catalog = parse(fetch(
                httpClient,
                trust.resolve(required(properties.getCatalogPath(), "catalogPath", 255)),
                MAX_CATALOG_BYTES), Catalog.class, "客户端版本 catalog 无效");
        if (catalog.schemaVersion() != 1 || catalog.releases() == null) {
            throw invalid("客户端版本 catalog schema 不受支持");
        }
        Set<String> catalogVersions = new HashSet<>();
        List<LocalClientVersionModels.Release> candidates = new ArrayList<>();
        for (CatalogEntry entry : catalog.releases()) {
            String version = LocalClientReleaseVersion.parse(entry.version()).value();
            if (!catalogVersions.add(version)) {
                throw invalid("客户端版本 catalog 包含重复版本");
            }
            candidates.add(fetchRelease(httpClient, trust, entry, version));
        }

        int inserted = 0;
        int unchanged = 0;
        for (LocalClientVersionModels.Release candidate : candidates) {
            LocalClientVersionModels.Release existing = repository.findRelease(candidate.version()).orElse(null);
            if (existing == null) {
                repository.insertRelease(candidate);
                inserted++;
            } else if (existing.manifestSha256().equals(candidate.manifestSha256())) {
                unchanged++;
            } else {
                throw new PlatformException(ErrorCode.CONFLICT, "已同步客户端版本的签名清单发生覆盖");
            }
        }
        return new SyncResult(inserted, unchanged, candidates.size());
    }

    private LocalClientVersionModels.Release fetchRelease(
            HttpClient httpClient,
            LocalClientDownloadTrust trust,
            CatalogEntry entry,
            String expectedVersion) {
        String expectedManifestSha = digest(entry.manifestSha256(), "manifestSha256");
        byte[] manifestBytes = fetch(httpClient, trust.resolve(entry.manifestPath()), MAX_MANIFEST_BYTES);
        LocalClientDownloadTrust.requireSha256(manifestBytes, expectedManifestSha);
        byte[] manifestSignature = fetch(
                httpClient, trust.resolve(entry.manifestSignaturePath()), MAX_SIGNATURE_BYTES);
        trust.verifySignature(manifestBytes, manifestSignature);
        ReleaseManifest manifest = parse(manifestBytes, ReleaseManifest.class, "客户端发布清单无效");
        if (manifest.schemaVersion() != 2 || !expectedVersion.equals(manifest.version())) {
            throw invalid("客户端发布清单版本与 catalog 不一致");
        }
        if (!"1.18.4".equals(manifest.opencodeVersion())) {
            throw invalid("客户端发布清单 OpenCode 版本必须为 1.18.4");
        }
        if (manifest.artifacts() == null) {
            throw invalid("客户端发布清单缺少制品");
        }
        Map<String, ManifestArtifact> artifactsByKind;
        try {
            artifactsByKind = manifest.artifacts().stream().collect(Collectors.toUnmodifiableMap(
                    ManifestArtifact::kind, Function.identity()));
        } catch (IllegalStateException exception) {
            throw invalid("客户端发布清单包含重复制品");
        }
        if (!artifactsByKind.keySet().containsAll(REQUIRED_ARTIFACTS)
                || artifactsByKind.keySet().stream().anyMatch(kind ->
                        !REQUIRED_ARTIFACTS.contains(kind) && !PUBLIC_CAPABILITIES_ARTIFACT.equals(kind))) {
            throw invalid("客户端发布清单必须同时锁定 JAR、JDK 和 OpenCode");
        }
        List<LocalClientVersionModels.Artifact> artifacts = artifactsByKind.keySet().stream().sorted().map(kind -> {
            ManifestArtifact artifact = artifactsByKind.get(kind);
            String releasePrefix = "releases/" + expectedVersion + "/";
            if (artifact.size() < 1 || artifact.size() > 4L * 1024 * 1024 * 1024) {
                throw invalid("客户端发布制品大小无效");
            }
            if (artifact.path() == null
                    || !artifact.path().startsWith(releasePrefix)
                    || artifact.signaturePath() == null
                    || !artifact.signaturePath().startsWith(releasePrefix)) {
                throw invalid("客户端发布制品必须位于对应 release 发布前缀");
            }
            URI artifactUri = trust.resolve(artifact.path());
            byte[] signature = fetch(httpClient, trust.resolve(artifact.signaturePath()), MAX_SIGNATURE_BYTES);
            return new LocalClientVersionModels.Artifact(
                    kind,
                    artifactUri.toString(),
                    artifact.size(),
                    digest(artifact.sha256(), "artifactSha256"),
                    Base64.getEncoder().encodeToString(signature));
        }).toList();
        boolean compatible = "linux".equals(manifest.platform())
                && "arm64".equals(manifest.architecture())
                && manifest.launcherVersionMin() <= 1
                && manifest.launcherVersionMax() >= 1
                && "local-opencode-client.v1".equals(manifest.protocolVersion());
        return new LocalClientVersionModels.Release(
                expectedVersion,
                manifest.platform(),
                manifest.architecture(),
                manifest.launcherVersionMin(),
                manifest.launcherVersionMax(),
                manifest.protocolVersion(),
                trust.resolve(entry.manifestPath()).toString(),
                expectedManifestSha,
                Base64.getEncoder().encodeToString(manifestSignature),
                compatible,
                Objects.requireNonNull(manifest.publishedAt(), "publishedAt must not be null"),
                Instant.now(clock),
                artifacts);
    }

    private LocalClientDownloadTrust trust() {
        URI root = properties.getDownloadBaseUrl();
        String encodedPem = properties.getSigningPublicKeyBase64();
        if (root == null || encodedPem == null || encodedPem.isBlank()) {
            throw new PlatformException(ErrorCode.CONFLICT, "本地客户端版本同步未配置可信下载根或公钥");
        }
        try {
            String pem = new String(Base64.getDecoder().decode(encodedPem), StandardCharsets.US_ASCII);
            return new LocalClientDownloadTrust(root, LocalClientDownloadTrust.parsePublicKeyPem(pem));
        } catch (IllegalArgumentException exception) {
            throw new PlatformException(ErrorCode.CONFLICT, "本地客户端版本同步公钥配置无效");
        }
    }

    private byte[] fetch(HttpClient client, URI uri, int maxBytes) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(validDuration(properties.getRequestTimeout(), "requestTimeout"))
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream input = response.body()) {
                if (response.statusCode() != 200) {
                    throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "本地客户端版本下载返回非 200 状态");
                }
                long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                if (contentLength > maxBytes) {
                    throw invalid("本地客户端版本元数据超过大小上限");
                }
                byte[] content = input.readNBytes(maxBytes + 1);
                if (content.length > maxBytes) {
                    throw invalid("本地客户端版本元数据超过大小上限");
                }
                return content;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "本地客户端版本下载被中断");
        } catch (IOException exception) {
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "本地客户端版本下载失败");
        }
    }

    private <T> T parse(byte[] content, Class<T> type, String message) {
        try {
            return objectMapper.readValue(content, type);
        } catch (IOException exception) {
            throw invalid(message);
        }
    }

    private static Duration validDuration(Duration duration, String field) {
        if (duration == null || duration.isZero() || duration.isNegative() || duration.compareTo(Duration.ofMinutes(2)) > 0) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return duration;
    }

    private static String digest(String value, String field) {
        String normalized = required(value, field, 64).toLowerCase();
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw invalid(field + " 无效");
        }
        return normalized;
    }

    private static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw invalid(field + " 无效");
        }
        return value.trim();
    }

    private static PlatformException invalid(String message) {
        return new PlatformException(ErrorCode.VALIDATION_ERROR, message);
    }

    public record SyncResult(int synced, int unchanged, int discovered) {
    }

    public record Catalog(int schemaVersion, List<CatalogEntry> releases) {
    }

    public record CatalogEntry(
            String version,
            String manifestPath,
            String manifestSha256,
            String manifestSignaturePath) {
    }

    public record ReleaseManifest(
            int schemaVersion,
            String version,
            Instant publishedAt,
            String platform,
            String architecture,
            int launcherVersionMin,
            int launcherVersionMax,
            String protocolVersion,
            String opencodeVersion,
            List<ManifestArtifact> artifacts) {
    }

    public record ManifestArtifact(
            String kind,
            String path,
            long size,
            String sha256,
            String signaturePath) {
    }
}
