package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.tcds.TcdsGateway;
import com.enterprise.testagent.domain.tcds.TcdsGateway.Application;
import com.enterprise.testagent.domain.tcds.TcdsGateway.Document;
import com.enterprise.testagent.domain.tcds.TcdsGateway.RequirementItem;
import com.enterprise.testagent.domain.tcds.TcdsGateway.RequirementSubItem;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * TCDS 需求导入应用服务：重新读取可信目录数据，将文档转换后通过现有工作区文件服务写入。
 */
@Service
public class RequirementImportApplicationService {

    public static final int MAX_SUB_ITEMS = 100;
    public static final int MAX_DOCUMENTS = 200;
    public static final long MAX_DOCUMENT_BYTES = 20L * 1024 * 1024;
    public static final long MAX_IMPORT_BYTES = 200L * 1024 * 1024;
    private static final int MAX_PATH_SEGMENT_CODE_POINTS = 120;

    private final TcdsGateway tcdsGateway;
    private final WorkspaceApplicationService workspaceService;

    public RequirementImportApplicationService(
            TcdsGateway tcdsGateway,
            WorkspaceApplicationService workspaceService) {
        this.tcdsGateway = Objects.requireNonNull(tcdsGateway, "tcdsGateway must not be null");
        this.workspaceService = Objects.requireNonNull(workspaceService, "workspaceService must not be null");
    }

    /** 返回 TCDS 按当前用户给出的应用目录，供页面作为输入建议。 */
    public List<ApplicationOption> listApplications(String unifiedAuthId) {
        return tcdsGateway.listApplications(required(unifiedAuthId, "unifiedAuthId")).stream()
                .map(application -> new ApplicationOption(application.appName(), application.appShortName()))
                .toList();
    }

    /** 返回不含 token 和文档 URL 的父子条目目录。 */
    public List<ItemOption> listItems(String unifiedAuthId, String appShortName, String editionId) {
        return listItemOptions(unifiedAuthId, appShortName, editionId, null);
    }

    /**
     * 通过已完成逐 RPC 鉴权的文件 WebSocket 返回目录及导入状态；状态只读取工作区相对目录。
     * 已导入条目仍可再次选择，重复导入继续使用既有覆盖语义。
     */
    public List<ItemOption> listWorkspaceItems(
            String unifiedAuthId,
            String workspaceId,
            String appShortName,
            String editionId) {
        return listItemOptions(
                unifiedAuthId,
                appShortName,
                editionId,
                new WorkspaceId(required(workspaceId, "workspaceId")));
    }

    private List<ItemOption> listItemOptions(
            String unifiedAuthId,
            String appShortName,
            String editionId,
            WorkspaceId workspaceId) {
        List<RequirementItem> items = tcdsGateway.listRequirementItems(
                        required(unifiedAuthId, "unifiedAuthId"),
                        required(appShortName, "appShortName"),
                        required(editionId, "editionId"));
        Map<String, FileStatusResponse> statuses = workspaceId == null
                ? Map.of()
                : workspaceService.fileStatuses(workspaceId, importStatusPaths(items));
        return items.stream()
                .map(item -> itemOption(item, workspaceId != null, statuses))
                .toList();
    }

    private Collection<String> importStatusPaths(List<RequirementItem> items) {
        Set<String> paths = new LinkedHashSet<>();
        for (RequirementItem item : items) {
            String root = parentRoot(item);
            paths.add(root);
            item.children().forEach(child -> paths.add(subItemRoot(root, child)));
        }
        return paths;
    }

    private ItemOption itemOption(
            RequirementItem item,
            boolean includeImportStatus,
            Map<String, FileStatusResponse> statuses) {
        String root = parentRoot(item);
        Boolean imported = includeImportStatus ? imported(statuses, root) : null;
        return new ItemOption(
                item.itemNo(),
                item.itemName(),
                item.children().stream()
                        .map(child -> new SubItemOption(
                                child.itemNo(),
                                child.itemName(),
                                includeImportStatus ? imported(statuses, subItemRoot(root, child)) : null))
                        .toList(),
                imported);
    }

    private String parentRoot(RequirementItem item) {
        return "spec/" + segment(item.itemNo() + "-" + item.itemName(), "父条目目录");
    }

    private String subItemRoot(String parentRoot, RequirementSubItem child) {
        return parentRoot + "/01-需求/" + segment(child.itemNo() + "-" + child.itemName(), "子条目目录");
    }

    private boolean imported(Map<String, FileStatusResponse> statuses, String path) {
        FileStatusResponse status = statuses.get(path);
        return status != null && status.exists();
    }

    /**
     * 导入只接收选择 ID；条目名称、文档元数据和下载地址全部由后端重新向 TCDS 查询。
     */
    public ImportResult importRequirements(String unifiedAuthId, ImportCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String identity = required(unifiedAuthId, "unifiedAuthId");
        String appShortName = required(command.appShortName(), "appShortName");
        String editionId = required(command.editionId(), "editionId");
        WorkspaceId workspaceId = new WorkspaceId(required(command.workspaceId(), "workspaceId"));
        List<String> selected = normalizedSelection(command.selectedSubItemNos());

        boolean authorizedApplication = tcdsGateway.listApplications(identity).stream()
                .map(Application::appShortName)
                .filter(Objects::nonNull)
                .anyMatch(appShortName::equals);
        if (!authorizedApplication) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "当前用户无权访问所选 TCDS 应用");
        }

        List<RequirementItem> items = tcdsGateway.listRequirementItems(identity, appShortName, editionId);
        Map<String, SelectedSubItem> available = indexItems(items);
        List<SelectedSubItem> selectedItems = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String subItemNo : selected) {
            SelectedSubItem item = available.get(subItemNo);
            if (item == null) missing.add(subItemNo);
            else selectedItems.add(item);
        }
        if (!missing.isEmpty()) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "部分 TCDS 子条目不存在或已无权访问",
                    Map.of("subItemNos", missing));
        }

        ImportPlan plan = buildPlan(identity, selectedItems);
        int createdDirectories = createDirectories(workspaceId, plan.directories());
        int importedFiles = 0;
        int overwrittenFiles = 0;
        long downloadedBytes = 0L;
        List<ImportFailure> failures = new ArrayList<>(plan.failures());

        for (PlannedDocument planned : plan.documents()) {
            try {
                TcdsGateway.DownloadedDocument downloaded = tcdsGateway.download(planned.document(), MAX_DOCUMENT_BYTES);
                downloadedBytes += downloaded.content().length;
                if (downloadedBytes > MAX_IMPORT_BYTES) {
                    throw new PlatformException(ErrorCode.PAYLOAD_TOO_LARGE, "TCDS 单次导入总量超过 200 MiB");
                }
                String markdown = RequirementDocumentConverter.toMarkdown(
                        planned.document().fileName(), downloaded.contentType(), downloaded.content());
                if (markdown.getBytes(StandardCharsets.UTF_8).length > MAX_DOCUMENT_BYTES) {
                    throw new PlatformException(ErrorCode.PAYLOAD_TOO_LARGE, "转换后的 Markdown 超过单文件大小限制");
                }
                boolean existed = workspaceService.fileStatus(workspaceId, planned.targetPath()).exists();
                workspaceService.writeFile(workspaceId, planned.targetPath(), markdown);
                if (existed) overwrittenFiles++;
                else importedFiles++;
            } catch (PlatformException exception) {
                failures.add(new ImportFailure(
                        planned.displayName(),
                        exception.errorCode().name(),
                        safeFailureMessage(exception)));
            } catch (RuntimeException exception) {
                failures.add(new ImportFailure(planned.displayName(), "IMPORT_FAILED", "文档导入失败"));
            }
        }

        String status = failures.isEmpty()
                ? "SUCCEEDED"
                : importedFiles + overwrittenFiles > 0 ? "PARTIAL" : "FAILED";
        return new ImportResult(
                status,
                createdDirectories,
                importedFiles,
                overwrittenFiles,
                failures.size(),
                List.copyOf(failures),
                List.copyOf(plan.workspaceRelativeDisplayPaths()));
    }

    private ImportPlan buildPlan(String unifiedAuthId, List<SelectedSubItem> selectedItems) {
        Set<String> directories = new LinkedHashSet<>();
        Set<String> workspaceRelativeDisplayPaths = new LinkedHashSet<>();
        Map<String, String> directoryOwners = new LinkedHashMap<>();
        List<PlannedDocument> documents = new ArrayList<>();
        List<ImportFailure> failures = new ArrayList<>();
        Map<String, String> targetOwners = new LinkedHashMap<>();

        for (SelectedSubItem selected : selectedItems) {
            String parent = segment(selected.parent().itemNo() + "-" + selected.parent().itemName(), "父条目目录");
            String child = segment(selected.child().itemNo() + "-" + selected.child().itemName(), "子条目目录");
            String root = "spec/" + parent;
            // 只把本批次父条目目录作为脱敏展示路径返回，前端据此有限展开，不推断 TCDS 名称。
            workspaceRelativeDisplayPaths.add(root);
            String requirementDirectory = root + "/01-需求/" + child + "/需求文档";
            String designDirectory = root + "/02-设计/" + child + "/开发文档";
            String source = selected.parent().itemNo() + ":" + selected.child().itemNo();
            addDirectory(directories, directoryOwners, requirementDirectory, source);
            addDirectory(directories, directoryOwners, designDirectory, source);
            addDirectory(directories, directoryOwners, root + "/03-编码/" + child + "/031-业务代码", source);
            addDirectory(directories, directoryOwners, root + "/03-编码/" + child + "/032-单元测试", source);
            addDirectory(directories, directoryOwners, root + "/04-测试/" + child + "/041-测试设计", source);
            addDirectory(directories, directoryOwners, root + "/04-测试/" + child + "/042-测试执行", source);

            List<Document> requirementDocuments = documentsByType(selected.child().documents(), "3");
            List<Document> designDocuments = documentsByType(selected.child().documents(), "1");
            if (designDocuments.isEmpty()) {
                try {
                    tcdsGateway.findFallbackDesignDocument(unifiedAuthId, selected.child().itemNo())
                            .ifPresent(document -> designDocuments.add(document));
                } catch (RuntimeException exception) {
                    failures.add(new ImportFailure(
                            selected.child().itemNo(),
                            "DESIGN_FALLBACK_FAILED",
                            "设计文档兜底查询失败"));
                }
            }
            addDocuments(documents, targetOwners, requirementDirectory, selected, requirementDocuments);
            addDocuments(documents, targetOwners, designDirectory, selected, designDocuments);
            if (documents.size() > MAX_DOCUMENTS) {
                throw new PlatformException(
                        ErrorCode.PAYLOAD_TOO_LARGE,
                        "单次最多导入 200 个 TCDS 文档",
                        Map.of("maxDocuments", MAX_DOCUMENTS));
            }
        }
        return new ImportPlan(directories, documents, failures, workspaceRelativeDisplayPaths);
    }

    private static void addDirectory(
            Set<String> directories,
            Map<String, String> owners,
            String target,
            String source) {
        String previous = owners.putIfAbsent(target.toLowerCase(Locale.ROOT), source);
        if (previous != null && !previous.equals(source)) {
            throw new PlatformException(
                    ErrorCode.PATH_COLLISION,
                    "TCDS 目录目标路径冲突",
                    Map.of("path", target));
        }
        directories.add(target);
    }

    private void addDocuments(
            List<PlannedDocument> planned,
            Map<String, String> targetOwners,
            String directory,
            SelectedSubItem selected,
            Collection<Document> documents) {
        for (Document document : documents) {
            String fileName = markdownFileName(document.fileName());
            String target = directory + "/" + fileName;
            String source = selected.child().itemNo() + ":" + document.fileName() + ":" + document.uri();
            String previous = targetOwners.putIfAbsent(target.toLowerCase(Locale.ROOT), source);
            if (previous != null && !previous.equals(source)) {
                throw new PlatformException(
                        ErrorCode.PATH_COLLISION,
                        "TCDS 文档目标路径冲突",
                        Map.of("path", target));
            }
            planned.add(new PlannedDocument(document, target, fileName));
        }
    }

    private int createDirectories(WorkspaceId workspaceId, Collection<String> directories) {
        int created = 0;
        for (String directory : directories) {
            boolean existed = workspaceService.fileStatus(workspaceId, directory).exists();
            workspaceService.createDirectory(workspaceId, directory);
            if (!existed) created++;
        }
        return created;
    }

    private static Map<String, SelectedSubItem> indexItems(List<RequirementItem> items) {
        Map<String, SelectedSubItem> result = new LinkedHashMap<>();
        for (RequirementItem parent : items) {
            for (RequirementSubItem child : parent.children()) {
                SelectedSubItem previous = result.putIfAbsent(child.itemNo(), new SelectedSubItem(parent, child));
                if (previous != null) {
                    throw new PlatformException(
                            ErrorCode.PATH_COLLISION,
                            "TCDS 子条目编号重复",
                            Map.of("subItemNo", child.itemNo()));
                }
            }
        }
        return result;
    }

    private static List<Document> documentsByType(List<Document> documents, String type) {
        return new ArrayList<>(documents.stream().filter(document -> type.equals(document.fileType())).toList());
    }

    private static List<String> normalizedSelection(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "请至少选择一个 TCDS 子条目");
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : raw) result.add(required(value, "selectedSubItemNo"));
        if (result.size() > MAX_SUB_ITEMS) {
            throw new PlatformException(
                    ErrorCode.PAYLOAD_TOO_LARGE,
                    "单次最多选择 100 个 TCDS 子条目",
                    Map.of("maxSubItems", MAX_SUB_ITEMS));
        }
        return List.copyOf(result);
    }

    private static String markdownFileName(String sourceName) {
        String safe = segment(sourceName, "文档名称");
        int dot = safe.lastIndexOf('.');
        String base = dot > 0 ? safe.substring(0, dot) : safe;
        return segment(base, "文档名称") + ".md";
    }

    /** 路径段采用确定性规范化；不允许路径分隔符、控制字符或 Windows 保留字符进入目标路径。 */
    private static String segment(String raw, String field) {
        String normalized = Normalizer.normalize(required(raw, field), Normalizer.Form.NFKC);
        StringBuilder safe = new StringBuilder();
        normalized.codePoints().forEach(codePoint -> {
            if (Character.isISOControl(codePoint) || Character.isWhitespace(codePoint)) return;
            if ("/\\<>:\"|?*".indexOf(codePoint) >= 0) return;
            if (safe.codePointCount(0, safe.length()) < MAX_PATH_SEGMENT_CODE_POINTS) {
                safe.appendCodePoint(codePoint);
            }
        });
        String result = safe.toString();
        if (result.isBlank() || ".".equals(result) || "..".equals(result)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, field + " 无法形成安全路径");
        }
        return result;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, field + " 不能为空");
        }
        return value.trim();
    }

    private static String safeFailureMessage(PlatformException exception) {
        return switch (exception.errorCode()) {
            case PAYLOAD_TOO_LARGE, VALIDATION_ERROR, EXTERNAL_API_UNAVAILABLE -> exception.getMessage();
            default -> "文档导入失败";
        };
    }

    public record ApplicationOption(String appName, String appShortName) {
    }

    public record ItemOption(String itemNo, String itemName, List<SubItemOption> children, Boolean imported) {
        public ItemOption(String itemNo, String itemName, List<SubItemOption> children) {
            this(itemNo, itemName, children, null);
        }

        public ItemOption {
            children = children == null ? List.of() : List.copyOf(children);
        }
    }

    public record SubItemOption(String itemNo, String itemName, Boolean imported) {
        public SubItemOption(String itemNo, String itemName) {
            this(itemNo, itemName, null);
        }
    }

    public record ImportCommand(
            String workspaceId,
            String appShortName,
            String editionId,
            List<String> selectedSubItemNos,
            String requestId) {
    }

    public record ImportFailure(String fileName, String code, String message) {
    }

    public record ImportResult(
            String status,
            int createdDirectories,
            int importedFiles,
            int overwrittenFiles,
            int failedFiles,
            List<ImportFailure> failures,
            List<String> workspaceRelativeDisplayPaths) {
        /** 滚动升级兼容旧测试与内部调用方；旧构造形态不返回可展开路径。 */
        public ImportResult(
                String status,
                int createdDirectories,
                int importedFiles,
                int overwrittenFiles,
                int failedFiles,
                List<ImportFailure> failures) {
            this(status, createdDirectories, importedFiles, overwrittenFiles, failedFiles, failures, List.of());
        }

        public ImportResult {
            failures = failures == null ? List.of() : List.copyOf(failures);
            workspaceRelativeDisplayPaths = workspaceRelativeDisplayPaths == null
                    ? List.of()
                    : List.copyOf(workspaceRelativeDisplayPaths);
        }
    }

    private record SelectedSubItem(RequirementItem parent, RequirementSubItem child) {
    }

    private record PlannedDocument(Document document, String targetPath, String displayName) {
    }

    private record ImportPlan(
            Set<String> directories,
            List<PlannedDocument> documents,
            List<ImportFailure> failures,
            Set<String> workspaceRelativeDisplayPaths) {
    }
}
