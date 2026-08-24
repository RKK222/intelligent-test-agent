package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillHubUploadFile;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillHubUploadProgress;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillHubUploadRequest;
import com.enterprise.testagent.workspace.AgentSkillHubApplicationService;
import com.enterprise.testagent.workspace.AgentSkillHubResponses;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Agent & Skill Hub 元数据、显式发布和更新通知 HTTP 入口。 */
@RestController
@RequestMapping("/api/internal/platform/workspace-management/agent-skill-hub")
public class AgentSkillHubController {

    private static final int MAX_SKILL_PACKAGE_BYTES = 20 * 1024 * 1024;
    private static final int MAX_PICTURE_BYTES = 5 * 1024 * 1024;
    private static final Set<String> EXTERNAL_UPLOAD_FIELDS = Set.of(
            "source", "phase", "file", "safetyReportPic", "directoryStructurePic", "runningEffectPic");

    private final AgentSkillHubApplicationService service;

    public AgentSkillHubController(AgentSkillHubApplicationService service) {
        this.service = service;
    }

    @GetMapping("/assets")
    public ApiResponse<AgentSkillHubResponses.PageResponse<AgentSkillHubResponses.AssetResponse>> listAssets(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String subcategory,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean referencedOnly,
            @RequestParam(required = false) String targetWorkspaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int size,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        return ApiResponse.ok(service.listAssets(type, category, subcategory, source, keyword, referencedOnly, page, size,
                        targetWorkspaceId, principal.userId()),
                RuntimeApiSupport.traceId(exchange));
    }

    /** 正文仅在用户显式预览时从外部 SkillHub 下载并安全物化。 */
    @PostMapping("/assets/{assetId}/materialize")
    public ApiResponse<AgentSkillHubResponses.AssetDetailResponse> materialize(
            @PathVariable String assetId,
            @RequestParam(required = false) String targetWorkspaceId,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        return ApiResponse.ok(service.materializeExternalAsset(assetId, targetWorkspaceId, principal.userId()),
                RuntimeApiSupport.traceId(exchange));
    }

    /** 运维补偿入口；正常情况由带 Redis 锁的定时对账刷新目录。 */
    @PostMapping("/external/sync")
    public ApiResponse<AgentSkillHubResponses.ExternalSyncResponse> syncExternalCatalog(ServerWebExchange exchange) {
        AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        return ApiResponse.ok(service.syncExternalSkillHubCatalog(), RuntimeApiSupport.traceId(exchange));
    }

    /** 六个字段及返回结构均直接对齐企业 SkillHub /upload 文档。 */
    @PostMapping(value = "/external/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ApiResponse<String>> uploadExternalSkill(ServerWebExchange exchange) {
        AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return exchange.getMultipartData()
                .flatMap(parts -> uploadRequest(parts)
                        .flatMap(request -> Mono.fromCallable(() -> service.uploadExternalSkillHub(request))
                                .subscribeOn(Schedulers.boundedElastic())))
                .map(taskId -> ApiResponse.ok(taskId, traceId));
    }

    /** data 直接返回上游 result 的 progress 和 message，不附加平台自造字段。 */
    @GetMapping("/external/upload/progress")
    public Mono<ApiResponse<SkillHubUploadProgress>> externalUploadProgress(
            @RequestParam String taskId,
            ServerWebExchange exchange) {
        AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> service.externalSkillHubUploadProgress(taskId))
                .subscribeOn(Schedulers.boundedElastic())
                .map(progress -> ApiResponse.ok(progress, traceId));
    }

    @GetMapping("/assets/{assetId}")
    public ApiResponse<AgentSkillHubResponses.AssetDetailResponse> getAsset(
            @PathVariable String assetId,
            @RequestParam(required = false) String revisionId,
            @RequestParam(required = false) String targetWorkspaceId,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        return ApiResponse.ok(service.getAsset(assetId, revisionId, targetWorkspaceId, principal.userId()),
                RuntimeApiSupport.traceId(exchange));
    }

    @PostMapping("/assets/{assetId}/publish")
    public ApiResponse<AgentSkillHubResponses.PublishResponse> publish(
            @PathVariable String assetId,
            @RequestBody(required = false) PublishRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_APP_ADMIN);
        List<String> dependencies = request == null || request.dependencyAssetIds() == null
                ? List.of() : request.dependencyAssetIds();
        return ApiResponse.ok(service.publish(assetId, dependencies, principal.userId()), RuntimeApiSupport.traceId(exchange));
    }

    /** 只有超级管理员可以把 Hub Skill（含公共 Git 内容）归入受控事项分类。 */
    @PutMapping("/assets/{assetId}/classification")
    public ApiResponse<AgentSkillHubResponses.ClassificationResponse> classifySkill(
            @PathVariable String assetId,
            @RequestBody ClassificationRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        return ApiResponse.ok(service.classifySkill(
                        assetId, request.category(), request.subcategory(), principal.userId()),
                RuntimeApiSupport.traceId(exchange));
    }

    @GetMapping("/updates/count")
    public ApiResponse<UpdateCountResponse> countUpdates(
            @RequestParam(required = false) String targetWorkspaceId,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        return ApiResponse.ok(new UpdateCountResponse(service.countUpdates(targetWorkspaceId, principal.userId())),
                RuntimeApiSupport.traceId(exchange));
    }

    @GetMapping("/references/updates")
    public ApiResponse<AgentSkillHubResponses.PageResponse<AgentSkillHubResponses.UpdateResponse>> listUpdates(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) String targetWorkspaceId,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        return ApiResponse.ok(service.listUpdates(page, size, targetWorkspaceId, principal.userId()),
                RuntimeApiSupport.traceId(exchange));
    }

    record PublishRequest(List<String> dependencyAssetIds) {
    }

    record ClassificationRequest(String category, String subcategory) {
    }

    record UpdateCountResponse(long count) {
    }

    private Mono<SkillHubUploadRequest> uploadRequest(MultiValueMap<String, Part> parts) {
        if (!parts.keySet().equals(EXTERNAL_UPLOAD_FIELDS)) {
            return Mono.error(new PlatformException(
                    ErrorCode.VALIDATION_ERROR, "SkillHub 上传必须且只能包含接口文档规定的六个字段"));
        }
        String source = formField(parts, "source").value();
        String phase = formField(parts, "phase").value();
        return Mono.zip(
                readFile(filePart(parts, "file"), "file", MAX_SKILL_PACKAGE_BYTES),
                readFile(filePart(parts, "safetyReportPic"), "safetyReportPic", MAX_PICTURE_BYTES),
                readFile(filePart(parts, "directoryStructurePic"), "directoryStructurePic", MAX_PICTURE_BYTES),
                readFile(filePart(parts, "runningEffectPic"), "runningEffectPic", MAX_PICTURE_BYTES))
                .map(files -> new SkillHubUploadRequest(
                        source, phase, files.getT1(), files.getT2(), files.getT3(), files.getT4()));
    }

    private FormFieldPart formField(MultiValueMap<String, Part> parts, String name) {
        Part part = singlePart(parts, name);
        if (part instanceof FormFieldPart formField) return formField;
        throw new PlatformException(ErrorCode.VALIDATION_ERROR, name + " 必须是文本字段");
    }

    private FilePart filePart(MultiValueMap<String, Part> parts, String name) {
        Part part = singlePart(parts, name);
        if (part instanceof FilePart file) return file;
        throw new PlatformException(ErrorCode.VALIDATION_ERROR, name + " 必须是文件字段");
    }

    private Part singlePart(MultiValueMap<String, Part> parts, String name) {
        List<Part> values = parts.get(name);
        if (values == null || values.size() != 1) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, name + " 必须且只能出现一次");
        }
        return values.get(0);
    }

    private Mono<SkillHubUploadFile> readFile(FilePart file, String field, int maxBytes) {
        return DataBufferUtils.join(file.content(), maxBytes)
                .map(buffer -> {
                    byte[] content = new byte[buffer.readableByteCount()];
                    try {
                        buffer.read(content);
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                    MediaType contentType = file.headers().getContentType();
                    return new SkillHubUploadFile(
                            file.filename(),
                            contentType == null
                                    ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                                    : contentType.getType() + "/" + contentType.getSubtype(),
                            content);
                })
                .defaultIfEmpty(new SkillHubUploadFile(
                        file.filename(), MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[0]))
                .onErrorMap(DataBufferLimitException.class, ignored -> new PlatformException(
                        ErrorCode.PAYLOAD_TOO_LARGE,
                        field + " 超过接口文档建议上限",
                        Map.of("maxBytes", maxBytes)));
    }
}
