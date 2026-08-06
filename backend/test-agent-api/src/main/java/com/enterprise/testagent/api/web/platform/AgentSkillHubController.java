package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.workspace.AgentSkillHubApplicationService;
import com.enterprise.testagent.workspace.AgentSkillHubResponses;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.server.ServerWebExchange;

/** Agent & Skill Hub 元数据、显式发布和更新通知 HTTP 入口。 */
@RestController
@RequestMapping("/api/internal/platform/workspace-management/agent-skill-hub")
public class AgentSkillHubController {

    private final AgentSkillHubApplicationService service;

    public AgentSkillHubController(AgentSkillHubApplicationService service) {
        this.service = service;
    }

    @GetMapping("/assets")
    public ApiResponse<AgentSkillHubResponses.PageResponse<AgentSkillHubResponses.AssetResponse>> listAssets(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String subcategory,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean referencedOnly,
            @RequestParam(required = false) String targetWorkspaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int size,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        return ApiResponse.ok(service.listAssets(type, category, subcategory, keyword, referencedOnly, page, size,
                        targetWorkspaceId, principal.userId()),
                RuntimeApiSupport.traceId(exchange));
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

    /** 只有超级管理员可以把用户推送的 Skill 归入受控事项分类。 */
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
}
