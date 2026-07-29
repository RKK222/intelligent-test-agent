package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.integration.lobehub.LobehubHmacAuthenticator;
import com.enterprise.testagent.integration.lobehub.LobehubSsoRedeemResult;
import com.enterprise.testagent.integration.lobehub.LobehubSsoService;
import com.enterprise.testagent.integration.lobehub.LobehubSsoTicketIssue;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 平台用户与 LobeHub 服务之间的一次性票据交接入口。 */
@RestController
public class LobehubSsoController {

    public static final String TICKETS_PATH = "/api/internal/platform/lobehub-sso/tickets";
    public static final String REDEEM_PATH = TICKETS_PATH + "/redeem";
    public static final String REVOKE_PATH = "/api/internal/platform/lobehub-sso/grants/revoke";
    public static final String TIMESTAMP_HEADER = "X-LobeHub-Timestamp";
    public static final String NONCE_HEADER = "X-LobeHub-Nonce";
    public static final String SIGNATURE_HEADER = "X-LobeHub-Signature";

    private final LobehubSsoService ssoService;
    private final LobehubHmacAuthenticator hmacAuthenticator;
    private final ObjectMapper objectMapper;

    public LobehubSsoController(
            LobehubSsoService ssoService,
            LobehubHmacAuthenticator hmacAuthenticator,
            ObjectMapper objectMapper) {
        this.ssoService = Objects.requireNonNull(ssoService, "ssoService must not be null");
        this.hmacAuthenticator = Objects.requireNonNull(hmacAuthenticator, "hmacAuthenticator must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /** 仅已登录平台用户可签发 60 秒隐藏表单票据。 */
    @PostMapping(TICKETS_PATH)
    public Mono<ApiResponse<LobehubSsoTicketIssue>> issue(ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> ApiResponse.ok(ssoService.issue(principal), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 仅通过 HMAC 服务身份的 LobeHub 后端可原子兑换票据。 */
    @PostMapping(path = REDEEM_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<LobehubSsoRedeemResult>> redeem(
            @RequestBody byte[] body,
            @RequestHeader(value = TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = NONCE_HEADER, required = false) String nonce,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> {
                    hmacAuthenticator.authenticate(
                            "POST", REDEEM_PATH, body, timestamp, nonce, signature);
                    TicketRequest request = read(body, TicketRequest.class);
                    return ApiResponse.ok(ssoService.redeem(request.ticket()), traceId);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** LobeHub 显式退出、设备撤销或管理操作时撤销服务端委托。 */
    @PostMapping(path = REVOKE_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<Map<String, Boolean>>> revoke(
            @RequestBody byte[] body,
            @RequestHeader(value = TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = NONCE_HEADER, required = false) String nonce,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> {
                    hmacAuthenticator.authenticate(
                            "POST", REVOKE_PATH, body, timestamp, nonce, signature);
                    GrantRequest request = read(body, GrantRequest.class);
                    ssoService.revokeModelGrant(request.modelGrant());
                    return ApiResponse.ok(Map.of("revoked", true), traceId);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private <T> T read(byte[] body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (IOException exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "LobeHub SSO 请求体无效");
        }
    }

    private record TicketRequest(String ticket) {
    }

    private record GrantRequest(String modelGrant) {
    }
}
