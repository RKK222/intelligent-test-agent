package com.enterprise.testagent.localclient;

import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.enterprise.testagent.workspace.WorkspaceFileService;
import com.enterprise.testagent.workspace.WorkspaceFileUpload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/** 本地文件 RPC 适配器；正常操作只接收 workspaceId、root digest 和相对路径。 */
final class LocalClientFileRpcHandler {

    private static final int MAX_ACTIVE_UPLOADS = 64;

    private final LocalWorkspaceRegistry workspaceRegistry;
    private final WorkspaceFileService fileService;
    private final LocalGitAccessChecker gitAccessChecker;
    private final ObjectMapper objectMapper;
    private final Map<String, ActiveUpload> uploads = new ConcurrentHashMap<>();
    private final Semaphore uploadSlots = new Semaphore(MAX_ACTIVE_UPLOADS);

    LocalClientFileRpcHandler(LocalWorkspaceRegistry workspaceRegistry, ObjectMapper objectMapper) {
        this(workspaceRegistry, objectMapper, new LocalGitAccessChecker());
    }

    LocalClientFileRpcHandler(
            LocalWorkspaceRegistry workspaceRegistry,
            ObjectMapper objectMapper,
            LocalGitAccessChecker gitAccessChecker) {
        this.workspaceRegistry = workspaceRegistry;
        this.objectMapper = objectMapper;
        this.fileService = new WorkspaceFileService();
        this.gitAccessChecker = gitAccessChecker;
    }

    JsonNode handle(LocalClientPayloads.FileRequest request) {
        JsonNode params = request.parameters() == null ? objectMapper.createObjectNode() : request.parameters();
        Object result = switch (request.operation()) {
            case "directory.list" -> workspaceRegistry.listAbsolute(
                    requiredOneOf(params, "absolutePath", "path"), integer(params, "limit", 1000));
            case "workspace.validateRoot" -> workspaceRegistry.validate(requiredText(params, "absolutePath"));
            case "workspace.registerRoot" -> workspaceRegistry.register(
                    requiredWorkspaceId(request), requiredText(params, "absolutePath"));
            case "workspace.unregister" -> {
                workspaceRegistry.unregister(requiredWorkspaceId(request));
                yield null;
            }
            case "workspace.list" -> fileService.listDirectory(root(request), text(params, "path"));
            case "workspace.search" -> fileService.searchFiles(root(request), text(params, "query"));
            case "workspace.read" -> fileService.readContent(root(request), requiredText(params, "path"));
            case "workspace.read.chunk" -> fileService.readContentChunk(
                    root(request),
                    requiredText(params, "path"),
                    nonNegativeLong(params, "offset"),
                    optionalLong(params, "expectedSize"),
                    optionalLong(params, "expectedLastModifiedMillis"));
            case "workspace.read.binary.chunk" -> fileService.readBinaryChunk(
                    root(request),
                    requiredText(params, "path"),
                    nonNegativeLong(params, "offset"),
                    optionalLong(params, "expectedSize"),
                    optionalLong(params, "expectedLastModifiedMillis"));
            case "workspace.write" -> {
                fileService.writeContent(root(request), requiredText(params, "path"), text(params, "content"));
                yield null;
            }
            case "workspace.upload" -> {
                fileService.uploadFile(root(request), requiredText(params, "path"), requiredText(params, "contentBase64"));
                yield null;
            }
            case "workspace.upload.begin" -> beginUpload(request, params);
            case "workspace.upload.chunk" -> appendUpload(request, params);
            case "workspace.upload.complete" -> completeUpload(request, params);
            case "workspace.upload.abort" -> {
                abortUpload(requiredText(params, "uploadId"));
                yield null;
            }
            case "workspace.copy" -> {
                fileService.copyFile(
                        root(request), requiredText(params, "sourcePath"), requiredText(params, "targetPath"));
                yield null;
            }
            case "workspace.move" -> {
                fileService.moveFile(
                        root(request), requiredText(params, "sourcePath"), requiredText(params, "targetPath"));
                yield null;
            }
            case "workspace.rename" -> {
                fileService.renameFile(root(request), requiredText(params, "path"), requiredText(params, "name"));
                yield null;
            }
            case "workspace.status" -> fileService.status(root(request), requiredText(params, "path"));
            case "workspace.delete" -> {
                fileService.deleteFile(root(request), requiredText(params, "path"));
                yield null;
            }
            case "workspace.mkdir" -> {
                fileService.createDirectory(root(request), requiredText(params, "path"));
                yield null;
            }
            case "workspace.git-access.check" -> gitAccessChecker.check(root(request));
            default -> throw new IllegalArgumentException("unsupported local file operation: " + request.operation());
        };
        return objectMapper.valueToTree(result);
    }

    void abortAll() {
        uploads.forEach((id, active) -> {
            if (uploads.remove(id, active)) {
                active.upload().abort();
                uploadSlots.release();
            }
        });
    }

    private UploadStarted beginUpload(LocalClientPayloads.FileRequest request, JsonNode params) {
        if (!uploadSlots.tryAcquire()) {
            throw new IllegalStateException("too many active local uploads");
        }
        String root = root(request);
        WorkspaceFileUpload upload = null;
        try {
            upload = fileService.beginUpload(
                    root, requiredText(params, "path"), nonNegativeLongOneOf(params, "expectedBytes", "size"));
            String uploadId = "lup_" + UUID.randomUUID().toString().replace("-", "");
            ActiveUpload active = new ActiveUpload(
                    requiredWorkspaceId(request), request.rootDigest(), upload);
            if (uploads.putIfAbsent(uploadId, active) != null) {
                throw new IllegalStateException("local upload id collision");
            }
            return new UploadStarted(uploadId, upload.chunkBytes(), upload.expectedBytes());
        } catch (RuntimeException exception) {
            if (upload != null) {
                upload.abort();
            }
            uploadSlots.release();
            throw exception;
        }
    }

    private UploadProgress appendUpload(LocalClientPayloads.FileRequest request, JsonNode params) {
        ActiveUpload active = requireUpload(request, requiredText(params, "uploadId"));
        active.upload().append(nonNegativeLong(params, "index"), requiredText(params, "contentBase64"));
        return new UploadProgress(active.upload().uploadedBytes(), active.upload().expectedBytes());
    }

    private UploadProgress completeUpload(LocalClientPayloads.FileRequest request, JsonNode params) {
        String uploadId = requiredText(params, "uploadId");
        ActiveUpload active = requireUpload(request, uploadId);
        try {
            long size = active.upload().complete();
            return new UploadProgress(size, size);
        } finally {
            if (uploads.remove(uploadId, active)) {
                uploadSlots.release();
            }
        }
    }

    private void abortUpload(String uploadId) {
        ActiveUpload active = uploads.remove(uploadId);
        if (active != null) {
            active.upload().abort();
            uploadSlots.release();
        }
    }

    private ActiveUpload requireUpload(LocalClientPayloads.FileRequest request, String uploadId) {
        ActiveUpload active = uploads.get(uploadId);
        if (active == null
                || !active.workspaceId().equals(requiredWorkspaceId(request))
                || !active.rootDigest().equals(request.rootDigest())) {
            throw new IllegalArgumentException("upload session is invalid");
        }
        workspaceRegistry.requireRoot(active.workspaceId(), active.rootDigest());
        return active;
    }

    private String root(LocalClientPayloads.FileRequest request) {
        return workspaceRegistry.requireRoot(requiredWorkspaceId(request), request.rootDigest());
    }

    private static String requiredWorkspaceId(LocalClientPayloads.FileRequest request) {
        if (request.workspaceId() == null || request.workspaceId().isBlank()) {
            throw new IllegalArgumentException("workspaceId is required");
        }
        return request.workspaceId();
    }

    private static String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static String requiredOneOf(JsonNode node, String first, String second) {
        String value = text(node, first);
        if (value == null || value.isBlank()) {
            value = text(node, second);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(first + " is required");
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static long nonNegativeLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.canConvertToLong() || value.asLong() < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value.asLong();
    }

    private static long nonNegativeLongOneOf(JsonNode node, String first, String second) {
        JsonNode value = node.path(first);
        if (value.isMissingNode() || value.isNull()) {
            value = node.path(second);
        }
        if (!value.canConvertToLong() || value.asLong() < 0) {
            throw new IllegalArgumentException(first + " must not be negative");
        }
        return value.asLong();
    }

    private static Long optionalLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.canConvertToLong() || value.asLong() < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value.asLong();
    }

    private static int integer(JsonNode node, String field, int fallback) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? fallback : value.asInt();
    }

    private record ActiveUpload(String workspaceId, String rootDigest, WorkspaceFileUpload upload) {
    }

    private record UploadStarted(String uploadId, int chunkBytes, long expectedBytes) {
    }

    private record UploadProgress(long uploadedBytes, long expectedBytes) {
    }
}
