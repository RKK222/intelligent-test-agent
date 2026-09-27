package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.team.TeamReviewModels.Scope;
import com.enterprise.testagent.domain.team.TeamScopeMode;
import com.enterprise.testagent.opencode.runtime.process.TeamReviewToolTokenService;
import com.enterprise.testagent.xxljob.XxlJobProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 这里只签发审阅范围和一次性文件票据，绝不通过 HTTP 返回文件目录或正文。 */
@RestController
public class TeamReviewController {
    private static final String BASE = "/api/internal/platform/workspace-management/team";
    private final TeamReviewProtocolService protocol;
    private final TeamReviewToolTokenService tools;
    private final byte[] internalToken;
    public TeamReviewController(TeamReviewProtocolService protocol, TeamReviewToolTokenService tools, XxlJobProperties properties) {
        this.protocol = protocol; this.tools = tools;
        this.internalToken = properties.getAccessToken().getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping(BASE + "/review-scopes")
    public Mono<ApiResponse<ReviewContext>> create(@RequestBody ScopeRequest request, ServerWebExchange exchange) {
        var principal = AuthWebSupport.getAuthPrincipal(exchange);
        return Mono.fromCallable(() -> {
            Scope scope = protocol.create(principal, request.scopeMode(), request.ownerUserId(), request.versionId(), request.selectedUserId());
            return ApiResponse.ok(new ReviewContext(scope.id(), scope.versionId(), scope.selectedUserId(), scope.sources(), scope.expiresAt()),
                    RuntimeApiSupport.traceId(exchange));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping(BASE + "/review-file-ticket")
    public Mono<ApiResponse<TeamReviewProtocolService.Connection>> ticket(@RequestBody TicketRequest request, ServerWebExchange exchange) {
        var principal = AuthWebSupport.getAuthPrincipal(exchange);
        return Mono.fromCallable(() -> ApiResponse.ok(protocol.connection(request.scopeId(), principal.userId().value(), null,
                RuntimeApiSupport.traceId(exchange)), RuntimeApiSupport.traceId(exchange))).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping(TeamReviewToolTokenService.TICKET_PATH)
    public Mono<ApiResponse<TeamReviewProtocolService.Connection>> toolTicket(@RequestBody TicketRequest request, ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return Mono.fromCallable(() -> {
            var actor = tools.authenticate(header);
            if (request.sessionId() == null || request.sessionId().isBlank())
                throw new PlatformException(ErrorCode.FORBIDDEN, "模型读取必须绑定当前会话和 Run");
            return ApiResponse.ok(protocol.connection(request.scopeId(), actor.value(), request.sessionId(),
                    RuntimeApiSupport.traceId(exchange)), RuntimeApiSupport.traceId(exchange));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping(TeamReviewProtocolService.INTERNAL_TICKET_PATH)
    public Mono<ApiResponse<TeamReviewProtocolService.Connection>> internalTicket(
            @RequestBody TeamReviewProtocolService.InternalTicketRequest request, ServerWebExchange exchange) {
        byte[] supplied = java.util.Objects.toString(exchange.getRequest().getHeaders().getFirst("XXL-JOB-ACCESS-TOKEN"), "")
                .getBytes(StandardCharsets.UTF_8);
        if (internalToken.length == 0 || !MessageDigest.isEqual(internalToken, supplied))
            throw new PlatformException(ErrorCode.UNAUTHENTICATED, "内部审阅 ticket 未授权");
        return Mono.fromCallable(() -> ApiResponse.ok(protocol.sourceTicket(request.scopeId(), request.personalWorkspaceId(),
                RuntimeApiSupport.traceId(exchange)), RuntimeApiSupport.traceId(exchange))).subscribeOn(Schedulers.boundedElastic());
    }

    public record ScopeRequest(TeamScopeMode scopeMode, String ownerUserId, String versionId, String selectedUserId) { }
    public record ReviewContext(String id, String versionId, String selectedUserId,
            java.util.List<com.enterprise.testagent.domain.team.TeamReviewModels.Source> sources, java.time.Instant expiresAt) { }
    public record TicketRequest(String scopeId, String sessionId) { }
}
