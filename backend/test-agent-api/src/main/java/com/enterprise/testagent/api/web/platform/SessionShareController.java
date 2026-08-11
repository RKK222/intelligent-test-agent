package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.sessionshare.SessionShareId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.run.RunApplicationService;
import com.enterprise.testagent.opencode.runtime.session.SessionMessageRealtimeHub;
import com.enterprise.testagent.opencode.runtime.share.DelegatedOperationContext;
import com.enterprise.testagent.opencode.runtime.share.SessionCollaborationShareService;
import com.enterprise.testagent.opencode.runtime.share.SessionShareMemberCommand;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 平台会话协作分享 HTTP 入口。 */
@RestController
public class SessionShareController {

    public static final String SHARE_HEADER = "X-Test-Agent-Session-Share";
    private static final String BASE = "/api/internal/platform/opencode-runtime";

    private final SessionCollaborationShareService service;
    private final RunApplicationService runService;
    private final SessionMessageRealtimeHub sessionMessageRealtimeHub;

    public SessionShareController(SessionCollaborationShareService service) {
        this(service, null, null);
    }

    public SessionShareController(
            SessionCollaborationShareService service,
            RunApplicationService runService) {
        this(service, runService, null);
    }

    @Autowired
    public SessionShareController(
            SessionCollaborationShareService service,
            RunApplicationService runService,
            SessionMessageRealtimeHub sessionMessageRealtimeHub) {
        this.service = service;
        this.runService = runService;
        this.sessionMessageRealtimeHub = sessionMessageRealtimeHub;
    }

    /** 分页搜索可被分享的有效平台用户，仅返回安全最小资料。 */
    @GetMapping(BASE + "/session-share-candidates")
    public ApiResponse<PageResponse<SessionShareDtos.SessionShareCandidateResponse>> candidates(
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        UserId actor = AuthWebSupport.getAuthPrincipal(exchange).userId();
        return ApiResponse.ok(SessionShareDtos.candidatePage(service.findCandidates(
                actor, query, RuntimeApiSupport.pageRequest(page, size))), traceId);
    }

    /** 查询所属人的当前分享设置。 */
    @GetMapping(BASE + "/sessions/{sessionId}/collaboration-share")
    public ApiResponse<SessionShareDtos.SessionShareResponse> get(
            @PathVariable String sessionId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        UserId actor = AuthWebSupport.getAuthPrincipal(exchange).userId();
        return ApiResponse.ok(SessionShareDtos.SessionShareResponse.from(
                service.get(actor, new SessionId(sessionId))), traceId);
    }

    /** 首次创建、重新启用或全量更新唯一分享链接。 */
    @PutMapping(BASE + "/sessions/{sessionId}/collaboration-share")
    public ApiResponse<SessionShareDtos.SessionShareResponse> put(
            @PathVariable String sessionId,
            @Valid @RequestBody SessionShareDtos.PutSessionShareRequest request,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        UserId actor = AuthWebSupport.getAuthPrincipal(exchange).userId();
        List<SessionShareMemberCommand> members = request.members().stream()
                .map(member -> new SessionShareMemberCommand(new UserId(member.userId()), member.canChat()))
                .toList();
        return ApiResponse.ok(SessionShareDtos.SessionShareResponse.from(service.put(
                actor, new SessionId(sessionId), request.expectedVersion(), request.expiresAt(),
                members, traceId)), traceId);
    }

    /** 取消分享但永久保留链接和成员历史。 */
    @DeleteMapping(BASE + "/sessions/{sessionId}/collaboration-share")
    public ApiResponse<SessionShareDtos.SessionShareResponse> revoke(
            @PathVariable String sessionId,
            @RequestParam long expectedVersion,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        UserId actor = AuthWebSupport.getAuthPrincipal(exchange).userId();
        return ApiResponse.ok(SessionShareDtos.SessionShareResponse.from(service.revoke(
                actor, new SessionId(sessionId), expectedVersion, traceId)), traceId);
    }

    /** 查询“分享给我”列表；不校验当前用户的工作区成员关系。 */
    @GetMapping(BASE + "/session-shares")
    public ApiResponse<PageResponse<SessionShareDtos.SharedSessionListResponse>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        UserId actor = AuthWebSupport.getAuthPrincipal(exchange).userId();
        return ApiResponse.ok(SessionShareDtos.sharedSessionPage(service.listSharedWith(
                actor, RuntimeApiSupport.pageRequest(page, size))), traceId);
    }

    /** 解析分享头为显式代操作上下文；不会改写 exchange 中的真实 AuthPrincipal。 */
    @GetMapping(BASE + "/session-shares/access")
    public ApiResponse<SessionShareDtos.SessionShareAccessResponse> access(
            @RequestHeader(SHARE_HEADER) String shareId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        DelegatedOperationContext context = service.requireAccess(
                principal.userId(), new SessionShareId(shareId), false, traceId);
        return ApiResponse.ok(SessionShareDtos.SessionShareAccessResponse.from(
                context, service.participantDirectory(context)), traceId);
    }

    /** 分享工作台专用状态流：推送活跃 Run、权限变更，并在授权失效后发末帧再断流。 */
    @GetMapping(value = BASE + "/session-shares/runtime-state/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<SessionShareDtos.SessionShareRuntimeStateResponse>> runtimeStateEvents(
            @RequestHeader(SHARE_HEADER) String shareId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        UserId actor = AuthWebSupport.getAuthPrincipal(exchange).userId();
        SessionShareId requestedShare = new SessionShareId(shareId);
        DelegatedOperationContext initial = service.requireAccess(
                actor, requestedShare, false, traceId);

        Flux<RuntimeStateTrigger> refreshTriggers = sessionMessageRealtimeHub == null
                ? Flux.interval(Duration.ofSeconds(1)).map(ignored -> new RuntimeStateTrigger(null))
                : Flux.merge(
                        Flux.interval(Duration.ofSeconds(1)).map(ignored -> new RuntimeStateTrigger(null)),
                        sessionMessageRealtimeHub.events(initial.sessionId()).map(RuntimeStateTrigger::new));
        Flux<RuntimeStateContext> contexts = Flux.concat(
                Mono.just(new RuntimeStateContext(initial, null)),
                refreshTriggers.concatMap(trigger -> Mono.fromCallable(() -> new RuntimeStateContext(
                                service.refreshAccess(actor, requestedShare, traceId), trigger.messageChange()))
                        .subscribeOn(Schedulers.boundedElastic())));
        Flux<ServerSentEvent<SessionShareDtos.SessionShareRuntimeStateResponse>> states = contexts
                .concatMap(context -> Mono.fromCallable(() -> runtimeState(
                                context.context(), context.messageChange()))
                        .subscribeOn(Schedulers.boundedElastic()))
                .distinctUntilChanged(this::runtimeKey)
                .index()
                .map(tuple -> ServerSentEvent.builder(tuple.getT2())
                        .event(tuple.getT1() == 0 ? "session-share.snapshot" : "session-share.updated")
                        .id(tuple.getT2().generatedAt().toString())
                        .build())
                .onErrorResume(PlatformException.class, failure -> Flux.just(ServerSentEvent
                        .builder(SessionShareDtos.SessionShareRuntimeStateResponse.invalid(
                                initial, invalidReason(failure), Instant.now()))
                        .event("session-share.invalidated")
                        .build()));
        return states.publish(shared -> shared.mergeWith(
                Flux.interval(Duration.ofSeconds(25))
                        .map(ignored -> ServerSentEvent
                                .<SessionShareDtos.SessionShareRuntimeStateResponse>builder()
                                .comment("heartbeat")
                                .build())
                        .takeUntilOther(shared.ignoreElements())));
    }

    private SessionShareDtos.SessionShareRuntimeStateResponse runtimeState(
            DelegatedOperationContext context,
            SessionMessageRealtimeHub.SessionMessageChange messageChange) {
        RuntimeDtos.RunResponse activeRun = runService == null
                ? null
                : runService.findActiveRun(context.sessionId())
                        .map(run -> RuntimeDtos.RunResponse.from(
                                run, RuntimeDtos.memoizedUsernameLookup(service::findUsername)))
                        .orElse(null);
        return SessionShareDtos.SessionShareRuntimeStateResponse.active(
                context, activeRun, service.sessionUpdatedAt(context), messageChange, Instant.now());
    }

    private RuntimeStateKey runtimeKey(SessionShareDtos.SessionShareRuntimeStateResponse state) {
        RuntimeDtos.RunResponse run = state.activeRun();
        return new RuntimeStateKey(
                state.version(), state.canChat(), state.expiresAt(), state.sessionUpdatedAt(),
                run == null ? null : run.runId(), run == null ? null : run.status(),
                run == null ? null : run.updatedAt(),
                state.messageChange() == null ? null : state.messageChange().sourceRunId(),
                state.messageChange() == null ? null : state.messageChange().replacementRunId(),
                state.messageChange() == null ? null : state.messageChange().changeType(),
                state.messageChange() == null ? null : state.messageChange().revision());
    }

    private String invalidReason(PlatformException failure) {
        Map<String, Object> details = failure.details();
        Object reason = details.get("reason");
        return reason == null ? failure.errorCode().name() : String.valueOf(reason);
    }

    private record RuntimeStateKey(
            long version,
            boolean canChat,
            Instant expiresAt,
            Instant sessionUpdatedAt,
            String runId,
            String runStatus,
            Instant runUpdatedAt,
            String sourceRunId,
            String replacementRunId,
            String messageChangeType,
            Instant messageRevision) { }

    private record RuntimeStateTrigger(
            SessionMessageRealtimeHub.SessionMessageChange messageChange) { }

    private record RuntimeStateContext(
            DelegatedOperationContext context,
            SessionMessageRealtimeHub.SessionMessageChange messageChange) { }
}
