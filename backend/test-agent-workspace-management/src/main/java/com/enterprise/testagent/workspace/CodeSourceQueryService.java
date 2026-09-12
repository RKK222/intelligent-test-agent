package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.appsource.AppSourceRepository;
import com.enterprise.testagent.domain.appsource.AppSourceReplicaStatus;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshot;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshotStatus;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ConversationWorkspaceAccessAuthorizer;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 面向 Agent 的 APP_SOURCE 原始源码只读查询服务。
 *
 * <p>每次请求都重新校验用户、当前 generation、本机 READY 副本和数据库权威索引；实际读取的是
 * generation 专属知识基线，不读取用户可编辑的 Workspace 目录。</p>
 */
@Service
public class CodeSourceQueryService {

    private static final int MAX_LIST_LIMIT = 200;
    private static final int MAX_READ_LINES = 400;
    private static final int MAX_SEARCH_LIMIT = 100;
    private static final int MAX_EXCERPT_CHARS = 240;
    private static final int MAX_RELATIVE_PATH_CHARS = 4096;
    private static final int MAX_SEARCH_QUERY_CHARS = 512;
    private static final Set<String> SEARCHABLE_EXTENSIONS = Set.of(
            "java", "kt", "kts", "jsp", "jspx", "js", "jsx", "ts", "tsx", "vue",
            "xml", "sql", "properties", "yml", "yaml", "json", "md", "py", "go",
            "c", "cc", "cpp", "h", "hpp", "cs", "php", "rb", "sh", "gradle");
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of(
            ".git", "node_modules", "target", "build", ".gradle", ".idea", "dist", "__pycache__");

    private final AppSourceRepository appSources;
    private final ConfigurationManagementRepository configuration;
    private final WorkspaceRepository workspaces;
    private final ConversationWorkspaceAccessAuthorizer authorizer;
    private final ManagedWorkspacePathResolver paths;
    private final WorkspaceFileService files;
    private final AppSourceIndexManager indexes;
    private final WorkspaceServerIdentity serverIdentity;
    private final Clock clock;
    private final int maxSearchDepth;
    private final long maxSearchFileBytes;
    private final long searchTimeoutMillis;
    private final int maxVisitedFiles;

    @Autowired
    public CodeSourceQueryService(
            AppSourceRepository appSources,
            ConfigurationManagementRepository configuration,
            WorkspaceRepository workspaces,
            ConversationWorkspaceAccessAuthorizer authorizer,
            ManagedWorkspacePathResolver paths,
            WorkspaceFileService files,
            AppSourceIndexManager indexes,
            WorkspaceServerIdentity serverIdentity,
            Clock clock,
            @Value("${test-agent.code-knowledge.source.max-search-depth:20}") int maxSearchDepth,
            @Value("${test-agent.code-knowledge.source.max-search-file-bytes:2097152}") long maxSearchFileBytes,
            @Value("${test-agent.code-knowledge.source.search-timeout-millis:5000}") long searchTimeoutMillis,
            @Value("${test-agent.code-knowledge.source.max-visited-files:2000}") int maxVisitedFiles) {
        this.appSources = Objects.requireNonNull(appSources);
        this.configuration = Objects.requireNonNull(configuration);
        this.workspaces = Objects.requireNonNull(workspaces);
        this.authorizer = Objects.requireNonNull(authorizer);
        this.paths = Objects.requireNonNull(paths);
        this.files = Objects.requireNonNull(files);
        this.indexes = Objects.requireNonNull(indexes);
        this.serverIdentity = Objects.requireNonNull(serverIdentity);
        this.clock = Objects.requireNonNull(clock);
        this.maxSearchDepth = positiveOrZero(maxSearchDepth, "maxSearchDepth");
        this.maxSearchFileBytes = positive(maxSearchFileBytes, "maxSearchFileBytes");
        this.searchTimeoutMillis = positive(searchTimeoutMillis, "searchTimeoutMillis");
        this.maxVisitedFiles = Math.toIntExact(positive(maxVisitedFiles, "maxVisitedFiles"));
    }

    /** 返回可供对话展示的版本库与固定提交上下文。 */
    public SourceContext context(UserId userId, String repositoryId) {
        ResolvedSource source = resolveAuthorized(userId, repositoryId);
        try {
            requireUsableBaseline(source);
            return new SourceContext(
                    source.repository().name(), source.repository().englishName(), evidence(source), true, null);
        } catch (PlatformException exception) {
            Object reason = exception.details().get("reason");
            if (!Set.of("SOURCE_BASELINE_UNAVAILABLE", "SOURCE_BASELINE_INDEX_INVALID").contains(reason)) {
                throw exception;
            }
            return new SourceContext(
                    source.repository().name(),
                    source.repository().englishName(),
                    evidence(source, reason.toString()),
                    false,
                    "OPEN_APP_SOURCE_PREPARATION");
        }
    }

    /** 列出知识基线中的一层目录，结果和截断状态均有界。 */
    public SourceListResult list(UserId userId, String repositoryId, String path, Integer requestedLimit) {
        ResolvedSource source = resolve(userId, repositoryId);
        int limit = bounded(requestedLimit, 50, 1, MAX_LIST_LIMIT, "limit");
        Path directory = resolveSearchRoot(source.baseline(), path);
        BasicFileAttributes before = readAttributes(directory);
        List<FileTreeEntryResponse> loaded = files.listDirectory(
                source.baseline().toString(), path, limit + 1);
        requireUnchanged(directory, before);
        requireCurrent(userId, repositoryId, source);
        requireUnchanged(directory, before);
        boolean truncated = loaded.size() > limit;
        return new SourceListResult(
                evidence(source), normalizePath(path), List.copyOf(loaded.subList(0, Math.min(limit, loaded.size()))), truncated);
    }

    /** 按 1 起始行号读取 UTF-8 文本，并返回完整文件 SHA-256 和固定提交证据。 */
    public SourceReadResult read(
            UserId userId,
            String repositoryId,
            String path,
            Integer requestedStartLine,
            Integer requestedEndLine) {
        ResolvedSource source = resolve(userId, repositoryId);
        Path target = resolveReadableFile(source.baseline(), path);
        BasicFileAttributes before = readAttributes(target);
        FileContentResponse loaded = files.readContent(source.baseline().toString(), path);
        requireUnchanged(target, before);
        String[] lines = loaded.content().split("\\R", -1);
        int totalLines = lines.length;
        int startLine = requestedStartLine == null ? 1 : requestedStartLine;
        int endLine = requestedEndLine == null
                ? Math.min(totalLines, startLine + MAX_READ_LINES - 1)
                : requestedEndLine;
        if (startLine < 1 || endLine < startLine || endLine > totalLines
                || endLine - startLine + 1 > MAX_READ_LINES) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "源码读取行范围无效",
                    Map.of("maxLines", MAX_READ_LINES, "totalLines", totalLines));
        }
        String content = String.join("\n", java.util.Arrays.copyOfRange(lines, startLine - 1, endLine));
        requireCurrent(userId, repositoryId, source);
        requireUnchanged(target, before);
        return new SourceReadResult(
                evidence(source), loaded.path(), startLine, endLine, totalLines,
                sha256(loaded.content().getBytes(StandardCharsets.UTF_8)), content);
    }

    /** 在固定基线中执行有深度、文件数、单文件大小、时间和结果数量上限的正文检索。 */
    public SourceSearchResult search(
            UserId userId,
            String repositoryId,
            String path,
            String query,
            Integer requestedLimit) {
        ResolvedSource source = resolve(userId, repositoryId);
        String normalizedQuery = requireText(query, "源码搜索关键字不能为空", MAX_SEARCH_QUERY_CHARS);
        int limit = bounded(requestedLimit, 20, 1, MAX_SEARCH_LIMIT, "limit");
        Path searchRoot = resolveSearchRoot(source.baseline(), path);
        SearchCollector collector = new SearchCollector(
                source.baseline(), normalizedQuery.toLowerCase(Locale.ROOT), limit,
                System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(searchTimeoutMillis));
        try {
            Files.walkFileTree(searchRoot, Set.of(), maxSearchDepth, collector);
        } catch (IOException exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "搜索应用源码失败", Map.of(), exception);
        }
        collector.requireVisitedPathsUnchanged();
        requireCurrent(userId, repositoryId, source);
        collector.requireVisitedPathsUnchanged();
        return new SourceSearchResult(
                evidence(source), normalizePath(path), normalizedQuery, List.copyOf(collector.matches),
                collector.truncationReason != null, collector.truncationReason);
    }

    private ResolvedSource resolve(UserId userId, String repositoryId) {
        ResolvedSource source = resolveAuthorized(userId, repositoryId);
        requireUsableBaseline(source);
        return source;
    }

    private ResolvedSource resolveAuthorized(UserId userId, String repositoryId) {
        Objects.requireNonNull(userId, "userId must not be null");
        CodeRepositoryId id;
        try {
            id = new CodeRepositoryId(repositoryId);
        } catch (RuntimeException exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "版本库 ID 无效");
        }
        CodeRepository repository = configuration.findRepository(id)
                .filter(value -> CodeRepositoryType.APPLICATION_CODE_REPOSITORY.value()
                        .equals(value.repositoryType()))
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "应用源码版本库不存在"));
        AppSourceSnapshot snapshot = appSources.findActiveSnapshot(id)
                .filter(value -> value.status() == AppSourceSnapshotStatus.ACTIVE)
                .filter(value -> value.expiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "应用源码快照未就绪"));
        boolean currentGeneration = appSources.findSlot(id)
                .map(slot -> Objects.equals(slot.activeGeneration(), snapshot.generation()))
                .orElse(false);
        if (!currentGeneration) {
            throw new PlatformException(ErrorCode.CONFLICT, "应用源码快照不是当前 generation");
        }
        LinuxServerId serverId = new LinuxServerId(serverIdentity.linuxServerId());
        var replica = appSources.findReplica(id, snapshot.generation(), serverId)
                .filter(value -> value.status() == AppSourceReplicaStatus.READY)
                .filter(value -> value.runtimeWorkspaceId() != null)
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "当前服务器应用源码副本未就绪"));
        Workspace workspace = workspaces.findById(replica.runtimeWorkspaceId())
                .filter(value -> value.status() == WorkspaceStatus.ACTIVE)
                .filter(value -> serverId.value().equals(value.linuxServerId()))
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "应用源码 Workspace 不可用"));
        var kind = authorizer.requireClassifiedFileAccess(userId, workspace.workspaceId(), false);
        if (kind != ConversationWorkspaceAccessAuthorizer.FileWorkspaceKind.APP_SOURCE) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "目标不是应用源码工作区");
        }
        Path editable = AppSourcePathGuard.requireSafe(paths.resolve(workspace.rootPath()));
        Path configured = AppSourcePathGuard.requireSafe(
                paths.resolve(paths.appSourceValue(snapshot.repositoryEnglishName())));
        if (!editable.equals(configured)) {
            throw new PlatformException(ErrorCode.CONFLICT, "应用源码 Workspace 路径不一致");
        }
        Path baseline = AppSourcePathGuard.requireSafe(
                AppSourceKnowledgeBaseline.root(editable, snapshot.generation()));
        return new ResolvedSource(repository, snapshot, workspace, baseline);
    }

    private void requireUsableBaseline(ResolvedSource source) {
        Path baseline = source.baseline();
        if (!Files.isDirectory(baseline, LinkOption.NOFOLLOW_LINKS)) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "应用源码知识基线不可用，请重新准备源码",
                    Map.of("reason", "SOURCE_BASELINE_UNAVAILABLE"));
        }
        indexes.verifyAuthoritativeIndex(baseline, source.snapshot());
    }

    /** 查询结束前重做完整授权和版本定位，撤权、过期或 generation 切换时不返回旧结果。 */
    private void requireCurrent(UserId userId, String repositoryId, ResolvedSource expected) {
        ResolvedSource actual = resolve(userId, repositoryId);
        if (actual.snapshot().generation() != expected.snapshot().generation()
                || !actual.snapshot().targetCommit().equals(expected.snapshot().targetCommit())
                || !actual.workspace().workspaceId().equals(expected.workspace().workspaceId())
                || !actual.baseline().equals(expected.baseline())) {
            throw sourceChanged("SOURCE_CONTEXT_CHANGED");
        }
    }

    private Path resolveReadableFile(Path root, String requestedPath) {
        String normalized = normalizePath(requestedPath);
        if (normalized.isEmpty()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "源码文件路径不能为空");
        }
        Path resolved = root.resolve(normalized).normalize();
        if (!resolved.startsWith(root)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "源码文件路径超出版本库");
        }
        AppSourcePathGuard.requireSafe(root, resolved);
        if (!Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS)) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "源码文件不存在");
        }
        return resolved;
    }

    private BasicFileAttributes readAttributes(Path path) {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException exception) {
            throw sourceChanged("SOURCE_CHANGED_DURING_READ");
        }
    }

    private void requireUnchanged(Path path, BasicFileAttributes before) {
        BasicFileAttributes after = readAttributes(path);
        if (before.isRegularFile() != after.isRegularFile()
                || before.isDirectory() != after.isDirectory()
                || before.size() != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !Objects.equals(before.fileKey(), after.fileKey())) {
            throw sourceChanged("SOURCE_CHANGED_DURING_READ");
        }
    }

    private PlatformException sourceChanged(String reason) {
        return new PlatformException(
                ErrorCode.CONFLICT,
                "应用源码知识基线在读取期间发生变化，请重试",
                Map.of("reason", reason));
    }

    private Path resolveSearchRoot(Path root, String requestedPath) {
        String normalized = normalizePath(requestedPath);
        Path resolved = root.resolve(normalized).normalize();
        if (!resolved.startsWith(root)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "源码搜索路径超出版本库");
        }
        if (!normalized.isEmpty()) {
            AppSourcePathGuard.requireSafe(root, resolved);
        }
        if (!Files.isDirectory(resolved, LinkOption.NOFOLLOW_LINKS)) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "源码搜索目录不存在");
        }
        return resolved;
    }

    private SourceEvidence evidence(ResolvedSource source) {
        return evidence(source, "IMMUTABLE_BASELINE");
    }

    private SourceEvidence evidence(ResolvedSource source, String sourceState) {
        AppSourceSnapshot snapshot = source.snapshot();
        return new SourceEvidence(
                snapshot.repositoryId().value(), source.repository().englishName(), snapshot.branch(),
                snapshot.targetCommit(), snapshot.generation(), snapshot.expiresAt(),
                snapshot.selectedPaths().stream()
                        .map(selected -> new SourceSelection(selected.path(), selected.pathType().name()))
                        .toList(),
                sourceState);
    }

    private static int bounded(Integer value, int defaultValue, int min, int max, String field) {
        int actual = value == null ? defaultValue : value;
        if (actual < min || actual > max) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR, field + " 超出允许范围", Map.of("min", min, "max", max));
        }
        return actual;
    }

    private static long positive(long value, String field) {
        if (value < 1L) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static int positiveOrZero(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }

    private static String requireText(String value, String message, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, message);
        }
        return value.trim();
    }

    private static String normalizePath(String value) {
        if (value == null || value.isBlank() || ".".equals(value.trim())) {
            return "";
        }
        String normalized = value.trim().replace('\\', '/');
        if (normalized.length() > MAX_RELATIVE_PATH_CHARS) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "源码路径无效");
        }
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:/.*")) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "源码路径必须是相对路径");
        }
        try {
            if (Path.of(normalized).isAbsolute()) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "源码路径必须是相对路径");
            }
        } catch (PlatformException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "源码路径无效");
        }
        for (String segment : normalized.split("/", -1)) {
            if ("..".equals(segment)) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "源码路径不允许目录穿越");
            }
            if (segment.isEmpty() || ".".equals(segment)) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "源码路径无效");
            }
        }
        return normalized;
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private final class SearchCollector extends SimpleFileVisitor<Path> {
        private final Path baseline;
        private final String query;
        private final int limit;
        private final long deadlineNanos;
        private final List<SourceSearchMatch> matches = new ArrayList<>();
        private final Map<Path, BasicFileAttributes> visitedPaths = new java.util.HashMap<>();
        private int visitedFiles;
        private String truncationReason;

        private SearchCollector(Path baseline, String query, int limit, long deadlineNanos) {
            this.baseline = baseline;
            this.query = query;
            this.limit = limit;
            this.deadlineNanos = deadlineNanos;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
            if (expired()) {
                truncationReason = "TIMEOUT";
                return FileVisitResult.TERMINATE;
            }
            if (!directory.equals(baseline)
                    && SKIPPED_DIRECTORIES.contains(directory.getFileName().toString())) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            visitedPaths.put(directory, readAttributes(directory));
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path directory, IOException exception) {
            if (exception != null) {
                throw sourceChanged("SOURCE_CHANGED_DURING_READ");
            }
            BasicFileAttributes before = visitedPaths.get(directory);
            if (before != null) {
                requireUnchanged(directory, before);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
            if (expired()) {
                truncationReason = "TIMEOUT";
                return FileVisitResult.TERMINATE;
            }
            // walkFileTree 将达到 maxDepth 的目录交给 visitFile；显式标记截断，
            // 避免 Agent 把未遍历的深层目录误判为“没有命中”。
            if (attributes.isDirectory()) {
                visitedPaths.put(file, readAttributes(file));
                if (truncationReason == null) {
                    truncationReason = "DEPTH_LIMIT";
                }
                return FileVisitResult.CONTINUE;
            }
            if (++visitedFiles > maxVisitedFiles) {
                truncationReason = "FILE_LIMIT";
                return FileVisitResult.TERMINATE;
            }
            if (!attributes.isRegularFile()
                    || attributes.size() > maxSearchFileBytes
                    || AppSourceApplicationService.INDEX_FILE_NAME.equals(file.getFileName().toString())
                    || !SEARCHABLE_EXTENSIONS.contains(extension(file.getFileName().toString()))) {
                return FileVisitResult.CONTINUE;
            }
            try {
                BasicFileAttributes before = readAttributes(file);
                visitedPaths.put(file, before);
                String[] lines = Files.readString(file, StandardCharsets.UTF_8).split("\\R", -1);
                requireUnchanged(file, before);
                for (int index = 0; index < lines.length; index++) {
                    if (!lines[index].toLowerCase(Locale.ROOT).contains(query)) {
                        continue;
                    }
                    if (matches.size() >= limit) {
                        truncationReason = "RESULT_LIMIT";
                        return FileVisitResult.TERMINATE;
                    }
                    String excerpt = lines[index].strip();
                    if (excerpt.length() > MAX_EXCERPT_CHARS) {
                        excerpt = excerpt.substring(0, MAX_EXCERPT_CHARS);
                    }
                    matches.add(new SourceSearchMatch(
                            baseline.relativize(file).toString().replace('\\', '/'), index + 1, excerpt));
                }
            } catch (MalformedInputException ignored) {
                // 非 UTF-8 文本不作为源码命中；文件身份仍在查询结束前复核。
            } catch (IOException exception) {
                throw sourceChanged("SOURCE_CHANGED_DURING_READ");
            }
            return FileVisitResult.CONTINUE;
        }

        private void requireVisitedPathsUnchanged() {
            visitedPaths.forEach(CodeSourceQueryService.this::requireUnchanged);
        }

        private boolean expired() {
            return System.nanoTime() >= deadlineNanos;
        }
    }

    private record ResolvedSource(
            CodeRepository repository,
            AppSourceSnapshot snapshot,
            Workspace workspace,
            Path baseline) {
    }

    public record SourceEvidence(
            String repositoryId,
            String repositoryEnglishName,
            String branch,
            String commit,
            long generation,
            Instant expiresAt,
            List<SourceSelection> selectedPaths,
            String sourceState) {
        public SourceEvidence {
            selectedPaths = selectedPaths == null ? List.of() : List.copyOf(selectedPaths);
        }
    }

    public record SourceSelection(String path, String type) {
    }

    public record SourceContext(
            String repositoryName,
            String repositoryEnglishName,
            SourceEvidence evidence,
            boolean available,
            String preparationAction) {
    }

    public record SourceListResult(
            SourceEvidence evidence, String path, List<FileTreeEntryResponse> entries, boolean truncated) {
    }

    public record SourceReadResult(
            SourceEvidence evidence,
            String path,
            int startLine,
            int endLine,
            int totalLines,
            String sha256,
            String content) {
    }

    public record SourceSearchMatch(String path, int line, String excerpt) {
    }

    public record SourceSearchResult(
            SourceEvidence evidence,
            String path,
            String query,
            List<SourceSearchMatch> matches,
            boolean truncated,
            String truncationReason) {
    }
}
