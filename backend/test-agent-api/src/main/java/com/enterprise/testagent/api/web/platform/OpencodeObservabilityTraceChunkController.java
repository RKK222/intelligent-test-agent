package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.opencode.runtime.observability.OpencodeObservabilityModels;
import com.enterprise.testagent.opencode.runtime.observability.TraceArchiveService;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import java.util.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 已冻结归档 owner 节点的内部 Trace 分片入口，只接受 Java 控制面 Bearer。 */
@RestController
public class OpencodeObservabilityTraceChunkController {

    private static final String PATH = "/api/internal/agent/opencode-observability/v1/traces/{traceId}/chunks/{sequence}";

    private final ManagerControlSettings managerSettings;
    private final UserRepository userRepository;
    private final TraceArchiveService archiveService;

    public OpencodeObservabilityTraceChunkController(
            ManagerControlSettings managerSettings,
            UserRepository userRepository,
            TraceArchiveService archiveService) {
        this.managerSettings = managerSettings;
        this.userRepository = userRepository;
        this.archiveService = archiveService;
    }

    @PutMapping(PATH)
    public Mono<ApiResponse<OpencodeObservabilityModels.TraceAck>> upload(
            @PathVariable String traceId,
            @PathVariable long sequence,
            @RequestBody OpencodeObservabilityModels.InternalTraceChunk request,
            ServerWebExchange exchange) {
        if (!managerSettings.tokenMatches(
                exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))) {
            throw new PlatformException(ErrorCode.UNAUTHENTICATED, "Trace 内部转发凭据无效");
        }
        if (!traceId.equals(request.traceId()) || sequence != request.firstSequence()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Trace 内部转发路径坐标无效");
        }
        byte[] body;
        try {
            body = Base64.getDecoder().decode(request.dataBase64());
        } catch (IllegalArgumentException exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Trace 内部转发正文无效");
        }
        var user = userRepository.findByUserId(new com.enterprise.testagent.domain.user.UserId(request.userId()))
                .filter(com.enterprise.testagent.domain.user.User::canLogin)
                .orElseThrow(() -> new PlatformException(ErrorCode.UNAUTHENTICATED, "Trace 用户已停用"));
        var identity = new OpencodeObservabilityModels.IngestionIdentity(
                user, null, request.clientInstanceId(), request.generation());
        String requestTraceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> ApiResponse.ok(
                        archiveService.ingestLocalChunk(
                                identity,
                                request.runtime(),
                                request.coverageStartAt(),
                                request.droppedCount(),
                                request.complete(),
                                request.traceId(),
                                request.firstSequence(),
                                request.lastSequence(),
                                request.sha256(),
                                body),
                        requestTraceId))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
