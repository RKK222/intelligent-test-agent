package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.memory.MemoryStatus;
import com.enterprise.testagent.memory.MemoryViews.MemoryEvidenceView;
import com.enterprise.testagent.memory.MemoryViews.MemoryUsageView;
import com.enterprise.testagent.memory.MemoryViews.MemoryView;
import com.enterprise.testagent.memory.MemoryViews.Page;
import com.enterprise.testagent.memory.MemoryViews.SkillProposalView;
import com.enterprise.testagent.memory.QaMemoryApplicationService;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/** 当前用户个人记忆、Application 团队记忆和 Skill 提案 HTTP 入口。 */
@RestController
@RequestMapping("/api/internal/platform/qa-memory/v1")
public class QaMemoryController {
    private final QaMemoryApplicationService service;

    public QaMemoryController(QaMemoryApplicationService service) {
        this.service = service;
    }

    @GetMapping("/availability")
    public ApiResponse<QaMemoryDtos.AvailabilityResponse> availability(ServerWebExchange exchange) {
        AuthPrincipal principal = principal(exchange);
        return ok(exchange, new QaMemoryDtos.AvailabilityResponse(service.availableFor(principal.userId())));
    }

    @GetMapping("/personal")
    public ApiResponse<Page<MemoryView>> listPersonal(
            @RequestParam(required = false) String applicationId,
            @RequestParam(required = false) MemoryStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int size,
            ServerWebExchange exchange) {
        return ok(exchange, service.listPersonal(principal(exchange).userId(), applicationId, status, page, size));
    }

    @PostMapping("/personal")
    public ApiResponse<MemoryView> createPersonal(
            @RequestBody QaMemoryDtos.CreatePersonalRequest request,
            ServerWebExchange exchange) {
        return ok(exchange, service.createPersonal(
                principal(exchange).userId(), request.scope(), request.applicationId(),
                request.content(), request.taskTypes()));
    }

    @GetMapping("/team")
    public ApiResponse<Page<MemoryView>> listTeam(
            @RequestParam(required = false) String applicationId,
            @RequestParam(required = false) MemoryStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int size,
            ServerWebExchange exchange) {
        return ok(exchange, service.listTeam(principal(exchange).userId(), applicationId, status, page, size));
    }

    @PostMapping("/team/proposals")
    public ApiResponse<MemoryView> createTeamProposal(
            @RequestBody QaMemoryDtos.CreateTeamRequest request,
            ServerWebExchange exchange) {
        return ok(exchange, service.createTeamCandidate(
                principal(exchange).userId(), request.applicationId(), request.content(), request.taskTypes()));
    }

    @PostMapping("/team")
    public ApiResponse<MemoryView> createTeamDirect(
            @RequestBody QaMemoryDtos.CreateTeamRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_APP_ADMIN);
        return ok(exchange, service.createTeamDirect(
                principal.userId(), request.applicationId(), request.content(), request.taskTypes()));
    }

    @PostMapping("/team/{memoryId}/reviews")
    public ApiResponse<MemoryView> reviewTeam(
            @PathVariable String memoryId,
            @RequestBody QaMemoryDtos.ReviewRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_APP_ADMIN);
        return ok(exchange, service.reviewTeam(
                principal.userId(), memoryId, request.decision(), request.comment(), request.expectedVersion()));
    }

    @GetMapping("/memories/{memoryId}")
    public ApiResponse<MemoryView> get(@PathVariable String memoryId, ServerWebExchange exchange) {
        return ok(exchange, service.get(principal(exchange).userId(), memoryId));
    }

    @PatchMapping("/memories/{memoryId}")
    public ApiResponse<MemoryView> update(
            @PathVariable String memoryId,
            @RequestBody QaMemoryDtos.UpdateMemoryRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = principal(exchange);
        return ok(exchange, service.update(
                principal.userId(), memoryId, request.content(), request.taskTypes(), request.expectedVersion(),
                AuthWebSupport.hasRole(principal, Dictionary.ROLE_APP_ADMIN)));
    }

    @PostMapping("/personal/{memoryId}/confirm")
    public ApiResponse<MemoryView> confirm(
            @PathVariable String memoryId,
            @RequestBody QaMemoryDtos.VersionRequest request,
            ServerWebExchange exchange) {
        return ok(exchange, service.confirmPersonal(
                principal(exchange).userId(), memoryId, request.expectedVersion()));
    }

    @PostMapping("/personal/{memoryId}/pause")
    public ApiResponse<MemoryView> pause(
            @PathVariable String memoryId,
            @RequestBody QaMemoryDtos.VersionRequest request,
            ServerWebExchange exchange) {
        return ok(exchange, service.pausePersonal(
                principal(exchange).userId(), memoryId, request.expectedVersion()));
    }

    @DeleteMapping("/memories/{memoryId}")
    public ApiResponse<MemoryView> archive(
            @PathVariable String memoryId,
            @RequestParam long expectedVersion,
            ServerWebExchange exchange) {
        AuthPrincipal principal = principal(exchange);
        return ok(exchange, service.archive(
                principal.userId(), memoryId, expectedVersion,
                AuthWebSupport.hasRole(principal, Dictionary.ROLE_APP_ADMIN)));
    }

    @GetMapping("/memories/{memoryId}/evidence")
    public ApiResponse<List<MemoryEvidenceView>> evidence(
            @PathVariable String memoryId, ServerWebExchange exchange) {
        return ok(exchange, service.evidence(principal(exchange).userId(), memoryId));
    }

    @PostMapping("/run-usage/query")
    public ApiResponse<List<MemoryUsageView>> usage(
            @RequestBody QaMemoryDtos.UsageQueryRequest request,
            ServerWebExchange exchange) {
        return ok(exchange, service.usage(principal(exchange).userId(), request.runIds()));
    }

    @PostMapping("/skill-proposals")
    public ApiResponse<SkillProposalView> createSkillProposal(
            @RequestBody QaMemoryDtos.CreateSkillProposalRequest request,
            ServerWebExchange exchange) {
        return ok(exchange, service.createSkillProposal(
                principal(exchange).userId(), request.memoryId(), request.applicationId(), request.title()));
    }

    @GetMapping("/skill-proposals")
    public ApiResponse<Page<SkillProposalView>> listSkillProposals(
            @RequestParam(required = false) String applicationId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int size,
            ServerWebExchange exchange) {
        return ok(exchange, service.listSkillProposals(
                principal(exchange).userId(), applicationId, page, size));
    }

    @PatchMapping("/skill-proposals/{proposalId}")
    public ApiResponse<SkillProposalView> updateSkillProposal(
            @PathVariable String proposalId,
            @RequestBody QaMemoryDtos.UpdateSkillProposalRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = principal(exchange);
        return ok(exchange, service.updateSkillProposal(
                principal.userId(), proposalId, request.title(), request.skillMdDraft(), request.expectedVersion(),
                AuthWebSupport.hasRole(principal, Dictionary.ROLE_APP_ADMIN)));
    }

    @PostMapping("/skill-proposals/{proposalId}/reviews")
    public ApiResponse<SkillProposalView> reviewSkillProposal(
            @PathVariable String proposalId,
            @RequestBody QaMemoryDtos.ReviewRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_APP_ADMIN);
        return ok(exchange, service.reviewSkillProposal(
                principal.userId(), proposalId, request.decision(), request.expectedVersion()));
    }

    @PostMapping("/skill-proposals/{proposalId}/published-asset")
    public ApiResponse<SkillProposalView> linkPublishedSkill(
            @PathVariable String proposalId,
            @RequestBody QaMemoryDtos.LinkPublishedSkillRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_APP_ADMIN);
        return ok(exchange, service.linkPublishedSkill(
                principal.userId(), proposalId, request.publishedAssetId(), request.expectedVersion()));
    }

    @DeleteMapping("/skill-proposals/{proposalId}")
    public ApiResponse<SkillProposalView> archiveSkillProposal(
            @PathVariable String proposalId,
            @RequestParam long expectedVersion,
            ServerWebExchange exchange) {
        AuthPrincipal principal = principal(exchange);
        return ok(exchange, service.archiveSkillProposal(
                principal.userId(), proposalId, expectedVersion,
                AuthWebSupport.hasRole(principal, Dictionary.ROLE_APP_ADMIN)));
    }

    private AuthPrincipal principal(ServerWebExchange exchange) {
        return AuthWebSupport.getAuthPrincipal(exchange);
    }

    private <T> ApiResponse<T> ok(ServerWebExchange exchange, T data) {
        return ApiResponse.ok(data, RuntimeApiSupport.traceId(exchange));
    }
}
