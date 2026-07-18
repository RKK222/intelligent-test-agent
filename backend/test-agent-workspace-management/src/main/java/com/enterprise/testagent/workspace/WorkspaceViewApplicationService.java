package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionReplica;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspace;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 工作区文件与受管引用资产的只读组合视图。
 *
 * <p>每次 list/read 都从 Workspace 的 JSONC 和数据库当前状态重建挂载；定位器只包含逻辑路径和引用别名，
 * 物理引用根、repositoryId 与服务器副本均由后端重新解析。
 */
@Service
public class WorkspaceViewApplicationService {

    private static final int MAX_ENTRIES = 1000;
    private static final String CONFIG_PATH = ".opencode/opencode.jsonc";
    private static final String REFERENCE_PREFIX = "{env:OPENCODE_REFERENCES_DIR}/";

    private final WorkspaceApplicationService workspaceService;
    private final WorkspaceFileService fileService;
    private final ManagedWorkspaceRepository managedWorkspaceRepository;
    private final ReferenceRepositoryApplicationService referenceService;
    private final ObjectMapper jsoncMapper;

    public WorkspaceViewApplicationService(
            WorkspaceApplicationService workspaceService,
            WorkspaceFileService fileService,
            ManagedWorkspaceRepository managedWorkspaceRepository,
            ReferenceRepositoryApplicationService referenceService) {
        this.workspaceService = Objects.requireNonNull(workspaceService, "workspaceService must not be null");
        this.fileService = Objects.requireNonNull(fileService, "fileService must not be null");
        this.managedWorkspaceRepository = Objects.requireNonNull(
                managedWorkspaceRepository,
                "managedWorkspaceRepository must not be null");
        this.referenceService = Objects.requireNonNull(referenceService, "referenceService must not be null");
        JsonFactory factory = JsonFactory.builder()
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
                .build();
        this.jsoncMapper = new ObjectMapper(factory);
    }

    /** 列出定位器指向的一层逻辑目录，并在组合归并完成后应用 1000 项上限。 */
    public WorkspaceViewListResponse list(WorkspaceId workspaceId, WorkspaceViewLocator locator) {
        Workspace workspace = workspaceService.getWorkspace(requireWorkspaceId(workspaceId));
        WorkspaceViewLocator normalized = normalizeLocator(locator, true);
        MountSnapshot snapshot = rebuildMounts(workspaceId, workspace);
        List<WorkspaceViewWarning> warnings = new ArrayList<>(snapshot.warnings());
        List<Candidate> candidates = switch (normalized.kind()) {
            case WORKSPACE -> workspaceCandidates(workspace, normalized.path(), false);
            case REFERENCE -> referenceCandidates(snapshot, normalized, false, warnings);
            case COMPOSITE -> compositeCandidates(workspace, normalized.path(), snapshot, warnings);
        };
        List<WorkspaceViewEntry> entries = groupAndSort(candidates);
        boolean truncated = entries.size() > MAX_ENTRIES;
        if (truncated) {
            entries = entries.subList(0, MAX_ENTRIES);
        }
        return new WorkspaceViewListResponse(entries, warnings, truncated || snapshot.sourceTruncated());
    }

    /** 读取 WORKSPACE 或 REFERENCE 文件；COMPOSITE 只表示目录投影，不能作为文件读取。 */
    public WorkspaceViewReadResponse read(WorkspaceId workspaceId, WorkspaceViewLocator locator) {
        Workspace workspace = workspaceService.getWorkspace(requireWorkspaceId(workspaceId));
        WorkspaceViewLocator normalized = normalizeLocator(locator, false);
        if (normalized.kind() == WorkspaceViewLocatorKind.COMPOSITE) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "组合定位器不能作为文件读取");
        }
        if (normalized.kind() == WorkspaceViewLocatorKind.WORKSPACE) {
            FileContentResponse content = fileService.readContent(workspace.rootPath(), normalized.path());
            return new WorkspaceViewReadResponse(
                    content.path(),
                    content.content(),
                    content.size(),
                    false,
                    WorkspaceViewSource.WORKSPACE,
                    null,
                    normalized);
        }
        MountSnapshot snapshot = rebuildMounts(workspaceId, workspace);
        Mount mount = requireMount(snapshot, normalized.referenceAlias());
        ReferenceRepositoryApplicationService.ViewContent content = referenceService.readView(
                mount.appId().value(),
                mount.repositoryEnglishName(),
                mount.folder(),
                normalized.path());
        String logicalPath = join(mount.merge() ? mount.folder() : mount.alias(), content.path());
        return new WorkspaceViewReadResponse(
                logicalPath,
                content.content(),
                content.size(),
                true,
                WorkspaceViewSource.REFERENCE,
                mount.alias(),
                normalized);
    }

    private List<Candidate> compositeCandidates(
            Workspace workspace,
            String path,
            MountSnapshot snapshot,
            List<WorkspaceViewWarning> warnings) {
        List<Candidate> result = new ArrayList<>();
        try {
            result.addAll(workspaceCandidates(workspace, path, true));
        } catch (PlatformException exception) {
            if (exception.errorCode() != ErrorCode.NOT_FOUND || !snapshot.coversCompositePath(path)) {
                throw exception;
            }
        }
        for (ValidatedMount validated : snapshot.mounts()) {
            Mount mount = validated.mount();
            if (!mount.merge()) {
                if (path.isEmpty()) {
                    result.add(Candidate.referenceDirectory(
                            mount.alias(),
                            mount.alias(),
                            "",
                            false,
                            true,
                            mount.alias()));
                }
                continue;
            }
            if (path.isEmpty()) {
                result.add(Candidate.referenceDirectory(
                        mount.folder(),
                        mount.folder(),
                        "",
                        true,
                        false,
                        mount.alias()));
                continue;
            }
            if (!path.equals(mount.folder()) && !path.startsWith(mount.folder() + "/")) {
                continue;
            }
            String referencePath = path.equals(mount.folder()) ? "" : path.substring(mount.folder().length() + 1);
            ReferenceRepositoryApplicationService.ViewListing listing;
            try {
                listing = referenceService.listView(
                        mount.appId().value(),
                        mount.repositoryEnglishName(),
                        mount.folder(),
                        referencePath,
                        MAX_ENTRIES + 1);
            } catch (PlatformException exception) {
                if (exception.errorCode() == ErrorCode.NOT_FOUND) {
                    // 某个引用没有贡献当前组合目录时直接跳过，不能拖垮工作区和其他引用来源。
                    continue;
                }
                addReferenceWarning(warnings, mount.alias(), exception.errorCode());
                continue;
            } catch (RuntimeException exception) {
                addReferenceWarning(warnings, mount.alias(), ErrorCode.INTERNAL_ERROR);
                continue;
            }
            for (ReferenceRepositoryApplicationService.ViewEntry entry : listing.entries()) {
                result.add(Candidate.referenceEntry(
                        join(path, entry.name()),
                        join(referencePath, entry.name()),
                        entry,
                        true,
                        mount.alias()));
            }
        }
        return result;
    }

    private List<Candidate> workspaceCandidates(Workspace workspace, String path, boolean composite) {
        return fileService.listDirectory(workspace.rootPath(), path, MAX_ENTRIES + 1).stream()
                .filter(entry -> !(path.isEmpty() && ".opencode".equals(entry.name())))
                .map(entry -> Candidate.workspace(entry, composite))
                .toList();
    }

    private List<Candidate> referenceCandidates(
            MountSnapshot snapshot,
            WorkspaceViewLocator locator,
            boolean merged,
            List<WorkspaceViewWarning> warnings) {
        Optional<Mount> currentMount = snapshot.findMount(locator.referenceAlias());
        if (currentMount.isEmpty()) {
            if (warnings.stream().anyMatch(warning -> Objects.equals(warning.alias(), locator.referenceAlias()))) {
                return List.of();
            }
            throw new PlatformException(ErrorCode.FORBIDDEN, "引用定位器与当前工作区配置不匹配");
        }
        Mount mount = currentMount.get();
        ReferenceRepositoryApplicationService.ViewListing listing;
        try {
            listing = referenceService.listView(
                    mount.appId().value(),
                    mount.repositoryEnglishName(),
                    mount.folder(),
                    locator.path(),
                    MAX_ENTRIES + 1);
        } catch (PlatformException exception) {
            addReferenceWarning(warnings, mount.alias(), exception.errorCode());
            return List.of();
        } catch (RuntimeException exception) {
            addReferenceWarning(warnings, mount.alias(), ErrorCode.INTERNAL_ERROR);
            return List.of();
        }
        String logicalRoot = join(mount.merge() ? mount.folder() : mount.alias(), locator.path());
        return listing.entries().stream()
                .map(entry -> Candidate.referenceEntry(
                        join(logicalRoot, entry.name()),
                        join(locator.path(), entry.name()),
                        entry,
                        merged || mount.merge(),
                        mount.alias()))
                .toList();
    }

    private List<WorkspaceViewEntry> groupAndSort(List<Candidate> candidates) {
        Map<String, List<Candidate>> groups = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            String key = candidate.directory() && !candidate.isolated()
                    ? "directory\u0000" + candidate.name()
                    : "entry\u0000" + candidate.identity();
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(candidate);
        }
        List<WorkspaceViewEntry> entries = new ArrayList<>();
        for (List<Candidate> group : groups.values()) {
            if (group.getFirst().directory() && !group.getFirst().isolated()) {
                String name = group.getFirst().name();
                boolean typeConflict = candidates.stream()
                        .filter(candidate -> candidate.name().equals(name))
                        .map(Candidate::directory)
                        .distinct()
                        .count() > 1;
                entries.add(directoryEntry(group, typeConflict));
            } else {
                boolean conflict = candidates.stream().filter(candidate -> candidate.name().equals(group.getFirst().name())).count() > 1;
                Candidate candidate = group.getFirst();
                entries.add(candidate.toEntry(candidate.referenceAlias() != null && conflict));
            }
        }
        Map<String, Boolean> directoryNameGroups = new LinkedHashMap<>();
        entries.forEach(entry -> directoryNameGroups.merge(entry.name(), entry.directory(), Boolean::logicalOr));
        entries.sort(Comparator
                // 同名并列项作为一个排序组：组内工作区优先，组间仍保持目录组在文件组之前。
                .comparing((WorkspaceViewEntry entry) -> !directoryNameGroups.getOrDefault(entry.name(), false))
                .thenComparing(WorkspaceViewEntry::name)
                .thenComparingInt(entry -> sourcePriority(entry.source()))
                .thenComparing(entry -> !entry.directory())
                .thenComparing(entry -> entry.locator().referenceAlias(), Comparator.nullsFirst(String::compareTo)));
        return entries;
    }

    private int sourcePriority(WorkspaceViewSource source) {
        return source == WorkspaceViewSource.REFERENCE ? 1 : 0;
    }

    private WorkspaceViewEntry directoryEntry(List<Candidate> group, boolean typeConflict) {
        Candidate workspace = group.stream().filter(candidate -> candidate.referenceAlias() == null).findFirst().orElse(null);
        List<String> aliases = group.stream()
                .map(Candidate::referenceAlias)
                .filter(Objects::nonNull)
                .sorted()
                .distinct()
                .toList();
        WorkspaceViewSource source = workspace != null && !aliases.isEmpty()
                ? WorkspaceViewSource.MIXED
                : workspace != null ? WorkspaceViewSource.WORKSPACE : WorkspaceViewSource.REFERENCE;
        boolean merged = group.stream().anyMatch(Candidate::merged);
        WorkspaceViewLocator locator;
        if (merged || group.size() > 1) {
            locator = new WorkspaceViewLocator(WorkspaceViewLocatorKind.COMPOSITE, group.getFirst().logicalPath(), null);
        } else {
            locator = group.getFirst().locator();
        }
        Candidate metadata = workspace == null ? group.getFirst() : workspace;
        String stableSource = workspace == null
                ? locator.kind().name().toLowerCase(Locale.ROOT)
                : WorkspaceViewLocatorKind.WORKSPACE.name().toLowerCase(Locale.ROOT);
        String stablePath = workspace == null ? locator.path() : workspace.workspacePath();
        return new WorkspaceViewEntry(
                stableId(
                        stableSource,
                        workspace == null && locator.kind() == WorkspaceViewLocatorKind.REFERENCE
                                ? locator.referenceAlias()
                                : null,
                        stablePath),
                group.getFirst().logicalPath(),
                group.getFirst().name(),
                true,
                0L,
                metadata.lastModifiedAt(),
                locator,
                source,
                merged,
                workspace == null && typeConflict,
                workspace == null,
                workspace == null ? null : workspace.workspacePath(),
                aliases);
    }

    private MountSnapshot rebuildMounts(WorkspaceId workspaceId, Workspace workspace) {
        Optional<ApplicationId> appId = managedApplicationId(workspaceId);
        if (appId.isEmpty()) {
            return MountSnapshot.empty();
        }
        FileContentResponse config;
        try {
            config = fileService.readContent(workspace.rootPath(), CONFIG_PATH);
        } catch (PlatformException exception) {
            if (exception.errorCode() == ErrorCode.NOT_FOUND) {
                return MountSnapshot.empty();
            }
            if (exception.errorCode() != ErrorCode.VALIDATION_ERROR) {
                throw exception;
            }
            return new MountSnapshot(
                    List.of(),
                    List.of(new WorkspaceViewWarning(
                            null,
                            "CONFIG_UNAVAILABLE",
                            "工作区引用配置当前不可读取")),
                    false);
        }
        List<WorkspaceViewWarning> warnings = new ArrayList<>();
        List<Mount> parsed = parseMounts(appId.get(), config.content(), warnings);
        List<ValidatedMount> valid = new ArrayList<>();
        for (Mount mount : parsed) {
            try {
                referenceService.listView(
                        mount.appId().value(),
                        mount.repositoryEnglishName(),
                        mount.folder(),
                        "",
                        1);
                valid.add(new ValidatedMount(mount));
            } catch (PlatformException exception) {
                warnings.add(new WorkspaceViewWarning(
                        mount.alias(),
                        exception.errorCode().name(),
                        "引用 " + mount.alias() + " 当前不可用"));
            } catch (RuntimeException exception) {
                warnings.add(new WorkspaceViewWarning(mount.alias(), ErrorCode.INTERNAL_ERROR.name(), "引用当前不可用"));
            }
        }
        return new MountSnapshot(valid, warnings, false);
    }

    private List<Mount> parseMounts(
            ApplicationId appId,
            String content,
            List<WorkspaceViewWarning> warnings) {
        JsonNode root;
        try {
            root = jsoncMapper.readTree(content);
        } catch (Exception exception) {
            warnings.add(new WorkspaceViewWarning(null, "INVALID_JSONC", "工作区引用配置无法解析"));
            return List.of();
        }
        if (root == null || !root.isObject()) {
            warnings.add(new WorkspaceViewWarning(null, "INVALID_JSONC", "工作区引用配置根节点无效"));
            return List.of();
        }
        JsonNode references = root.get("references");
        if (references == null || references.isNull()) {
            return List.of();
        }
        if (!references.isObject()) {
            warnings.add(new WorkspaceViewWarning(null, "INVALID_REFERENCES", "工作区 references 配置无效"));
            return List.of();
        }
        List<Mount> mounts = new ArrayList<>();
        references.fields().forEachRemaining(field -> {
            String alias = field.getKey();
            JsonNode value = field.getValue();
            Mount mount = parseMount(appId, alias, value, warnings);
            if (mount != null) {
                mounts.add(mount);
            }
        });
        mounts.sort(Comparator.comparing(Mount::alias));
        return mounts;
    }

    private Mount parseMount(
            ApplicationId appId,
            String alias,
            JsonNode value,
            List<WorkspaceViewWarning> warnings) {
        if (!value.isObject()) {
            // OpenCode 原生支持字符串形式的 Git 引用；平台视图只消费自己的本地挂载对象。
            return null;
        }
        JsonNode pathNode = value.get("path");
        boolean platformPath = pathNode != null
                && pathNode.isTextual()
                && pathNode.textValue().startsWith(REFERENCE_PREFIX);
        boolean platformMarked = platformPath || value.has("merge") || value.has("sdd-folder-name");
        if (value.has("repository")) {
            if (!platformMarked) {
                return null;
            }
            warnings.add(warning(alias, "INVALID_REFERENCE", "Git 引用对象不能作为平台本地引用挂载"));
            return null;
        }
        if (!platformMarked) {
            return null;
        }
        JsonNode mergeNode = value.get("merge");
        JsonNode folderNode = value.get("sdd-folder-name");
        if (pathNode == null || !pathNode.isTextual()
                || mergeNode == null || !mergeNode.isBoolean()
                || folderNode == null || !folderNode.isTextual()) {
            warnings.add(warning(alias, "INVALID_REFERENCE", "引用缺少平台托管字段"));
            return null;
        }
        String path = pathNode.textValue();
        if (!path.startsWith(REFERENCE_PREFIX)) {
            warnings.add(warning(alias, "INVALID_REFERENCE_PATH", "引用路径不是平台托管路径"));
            return null;
        }
        String suffix = path.substring(REFERENCE_PREFIX.length());
        String[] segments = suffix.split("/", -1);
        if (segments.length != 2 || !safeSegment(segments[0]) || !safeSegment(segments[1])) {
            warnings.add(warning(alias, "INVALID_REFERENCE_PATH", "引用路径结构无效"));
            return null;
        }
        String repositoryEnglishName = segments[0];
        String folder = segments[1];
        if (!alias.equals(folder + "-" + repositoryEnglishName)
                || !folder.equals(folderNode.textValue())) {
            warnings.add(warning(alias, "INVALID_REFERENCE_IDENTITY", "引用别名或规格目录不匹配"));
            return null;
        }
        return new Mount(appId, alias, repositoryEnglishName, folder, mergeNode.booleanValue());
    }

    private WorkspaceViewWarning warning(String alias, String code, String message) {
        return new WorkspaceViewWarning(alias, code, message);
    }

    private void addReferenceWarning(
            List<WorkspaceViewWarning> warnings,
            String alias,
            ErrorCode errorCode) {
        boolean exists = warnings.stream().anyMatch(warning -> Objects.equals(warning.alias(), alias)
                && warning.code().equals(errorCode.name()));
        if (!exists) {
            warnings.add(new WorkspaceViewWarning(alias, errorCode.name(), "引用 " + alias + " 当前不可用"));
        }
    }

    private Optional<ApplicationId> managedApplicationId(WorkspaceId workspaceId) {
        Optional<PersonalWorkspace> personal = managedWorkspaceRepository
                .findPersonalWorkspaceByRuntimeWorkspace(workspaceId);
        if (personal.isPresent()) {
            return Optional.of(personal.get().appId());
        }
        Optional<ApplicationWorkspaceVersion> version = managedWorkspaceRepository
                .findVersionByRuntimeWorkspace(workspaceId);
        if (version.isPresent()) {
            return Optional.of(version.get().appId());
        }
        Optional<ApplicationWorkspaceVersionReplica> replica = managedWorkspaceRepository
                .findVersionReplicaByRuntimeWorkspace(workspaceId);
        return replica.flatMap(value -> managedWorkspaceRepository.findVersion(value.versionId()))
                .map(ApplicationWorkspaceVersion::appId);
    }

    private Mount requireMount(MountSnapshot snapshot, String alias) {
        return snapshot.mounts().stream()
                .map(ValidatedMount::mount)
                .filter(mount -> mount.alias().equals(alias))
                .findFirst()
                .orElseThrow(() -> new PlatformException(ErrorCode.FORBIDDEN, "引用定位器与当前工作区配置不匹配"));
    }

    private WorkspaceViewLocator normalizeLocator(WorkspaceViewLocator locator, boolean listing) {
        if (locator == null || locator.kind() == null) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "工作区视图 locator 无效");
        }
        String path = normalizePath(locator.path());
        String alias = normalizeOptional(locator.referenceAlias());
        if (locator.kind() == WorkspaceViewLocatorKind.REFERENCE) {
            if (alias == null) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "引用定位器缺少 referenceAlias");
            }
        } else if (alias != null) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "非引用定位器不能携带 referenceAlias");
        }
        if (locator.kind() == WorkspaceViewLocatorKind.COMPOSITE && path.isEmpty() && !listing) {
            return WorkspaceViewLocator.root();
        }
        return new WorkspaceViewLocator(locator.kind(), path, alias);
    }

    private String normalizePath(String path) {
        // 文件系统允许名称带首尾空格；定位器来自 list 响应时必须逐字保留，不能做展示层式 trim。
        String value = path == null ? "" : path;
        if (value.isEmpty()) {
            return "";
        }
        if (value.startsWith("/") || value.contains("\\")) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "工作区视图路径必须是安全相对路径");
        }
        List<String> segments = new ArrayList<>();
        for (String segment : value.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)
                    || ".git".equalsIgnoreCase(segment)) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "工作区视图路径包含受保护目录");
            }
            segments.add(segment);
        }
        return String.join("/", segments);
    }

    private boolean safeSegment(String value) {
        return value != null
                && !value.isBlank()
                && !value.equals(".")
                && !value.equals("..")
                && !value.equalsIgnoreCase(".git")
                && !value.contains("\\");
    }

    private WorkspaceId requireWorkspaceId(WorkspaceId workspaceId) {
        return Objects.requireNonNull(workspaceId, "workspaceId must not be null");
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String join(String left, String right) {
        if (left == null || left.isEmpty()) {
            return right == null ? "" : right;
        }
        if (right == null || right.isEmpty()) {
            return left;
        }
        return left + "/" + right;
    }

    private static String stableId(String source, String alias, String path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((source + "\u0000" + alias + "\u0000" + path)
                    .getBytes(StandardCharsets.UTF_8));
            return "view_" + HexFormat.of().formatHex(bytes, 0, 16);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record Mount(
            ApplicationId appId,
            String alias,
            String repositoryEnglishName,
            String folder,
            boolean merge) {
    }

    private record ValidatedMount(Mount mount) {
    }

    private record MountSnapshot(
            List<ValidatedMount> mounts,
            List<WorkspaceViewWarning> warnings,
            boolean sourceTruncated) {

        private MountSnapshot {
            mounts = List.copyOf(mounts);
            warnings = List.copyOf(warnings);
        }

        private static MountSnapshot empty() {
            return new MountSnapshot(List.of(), List.of(), false);
        }

        private boolean coversCompositePath(String path) {
            return mounts.stream()
                    .map(ValidatedMount::mount)
                    .filter(Mount::merge)
                    .anyMatch(mount -> path.equals(mount.folder()) || path.startsWith(mount.folder() + "/"));
        }

        private Optional<Mount> findMount(String alias) {
            return mounts.stream()
                    .map(ValidatedMount::mount)
                    .filter(mount -> mount.alias().equals(alias))
                    .findFirst();
        }
    }

    private record Candidate(
            String identity,
            String logicalPath,
            String name,
            boolean directory,
            long size,
            Instant lastModifiedAt,
            WorkspaceViewLocator locator,
            WorkspaceViewSource source,
            boolean merged,
            boolean readonly,
            String workspacePath,
            String referenceAlias,
            boolean isolated) {

        private static Candidate workspace(FileTreeEntryResponse entry, boolean composite) {
            return new Candidate(
                    "workspace:" + entry.path(),
                    entry.path().replace('\\', '/'),
                    entry.name(),
                    entry.directory(),
                    entry.size(),
                    entry.lastModifiedAt(),
                    new WorkspaceViewLocator(WorkspaceViewLocatorKind.WORKSPACE, entry.path().replace('\\', '/'), null),
                    WorkspaceViewSource.WORKSPACE,
                    false,
                    false,
                    entry.path().replace('\\', '/'),
                    null,
                    false);
        }

        private static Candidate referenceDirectory(
                String logicalPath,
                String name,
                String referencePath,
                boolean merged,
                boolean isolated,
                String alias) {
            WorkspaceViewLocator locator = merged
                    ? new WorkspaceViewLocator(WorkspaceViewLocatorKind.COMPOSITE, logicalPath, null)
                    : new WorkspaceViewLocator(WorkspaceViewLocatorKind.REFERENCE, referencePath, alias);
            return new Candidate(
                    "reference:" + alias + ":" + referencePath,
                    logicalPath,
                    name,
                    true,
                    0L,
                    null,
                    locator,
                    WorkspaceViewSource.REFERENCE,
                    merged,
                    true,
                    null,
                    alias,
                    isolated);
        }

        private static Candidate referenceEntry(
                String logicalPath,
                String referencePath,
                ReferenceRepositoryApplicationService.ViewEntry entry,
                boolean merged,
                String alias) {
            WorkspaceViewLocator locator = entry.directory() && merged
                    ? new WorkspaceViewLocator(WorkspaceViewLocatorKind.COMPOSITE, logicalPath, null)
                    : new WorkspaceViewLocator(WorkspaceViewLocatorKind.REFERENCE, referencePath, alias);
            return new Candidate(
                    "reference:" + alias + ":" + referencePath,
                    logicalPath,
                    entry.name(),
                    entry.directory(),
                    entry.size(),
                    entry.lastModifiedAt(),
                    locator,
                    WorkspaceViewSource.REFERENCE,
                    merged,
                    true,
                    null,
                    alias,
                    false);
        }

        private WorkspaceViewEntry toEntry(boolean collision) {
            return new WorkspaceViewEntry(
                    stableId(
                            locator.kind().name().toLowerCase(Locale.ROOT),
                            locator.referenceAlias(),
                            locator.path()),
                    logicalPath,
                    name,
                    directory,
                    size,
                    lastModifiedAt,
                    locator,
                    source,
                    merged,
                    collision,
                    readonly,
                    workspacePath,
                    referenceAlias == null ? List.of() : List.of(referenceAlias));
        }
    }
}
