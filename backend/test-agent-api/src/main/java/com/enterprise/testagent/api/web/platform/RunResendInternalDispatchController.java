package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.opencode.runtime.run.RunResendDispatchService;
import com.enterprise.testagent.xxljob.XxlJobProperties;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** 目标 Java 的系统内部重发入口；仅精确路径豁免用户 token，并在此 fail-closed 校验 XXL token。 */
@RestController
public class RunResendInternalDispatchController {

    public static final String PATH =
            "/api/internal/platform/opencode-runtime/run-resends/internal-dispatch";

    private final RunResendDispatchService dispatchService;
    private final byte[] expectedToken;

    public RunResendInternalDispatchController(
            RunResendDispatchService dispatchService,
            XxlJobProperties properties) {
        this.dispatchService = Objects.requireNonNull(dispatchService);
        this.expectedToken = properties.getAccessToken().getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping(PATH)
    public Mono<ApiResponse<RunResendInternalDispatchDtos.Response>> dispatch(
            @RequestHeader(value = NightExecutionInternalDispatchController.ACCESS_TOKEN_HEADER, required = false)
                    String accessToken,
            @Valid @RequestBody RunResendInternalDispatchDtos.Request request,
            ServerWebExchange exchange) {
        requireToken(accessToken);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return dispatchService.dispatchBatch(
                        request.linuxServerId(), request.domainResendIds(), traceId)
                .map(result -> ApiResponse.ok(
                        RunResendInternalDispatchDtos.Response.from(result), traceId));
    }

    private void requireToken(String value) {
        byte[] provided = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        if (expectedToken.length == 0 || !MessageDigest.isEqual(expectedToken, provided)) {
            throw new PlatformException(ErrorCode.UNAUTHENTICATED, "XXL-JOB access token 无效");
        }
    }
}
