package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.opencode.runtime.night.NightExecutionDispatchService;
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

/** 目标 Java 的系统内部入口；普通用户鉴权仅对此精确路径豁免，token 在这里常量时间校验。 */
@RestController
public class NightExecutionInternalDispatchController {

    public static final String PATH =
            "/api/internal/platform/opencode-runtime/night-execution/internal-dispatch";
    public static final String ACCESS_TOKEN_HEADER = "XXL-JOB-ACCESS-TOKEN";

    private final NightExecutionDispatchService dispatchService;
    private final byte[] expectedToken;

    public NightExecutionInternalDispatchController(
            NightExecutionDispatchService dispatchService,
            XxlJobProperties properties) {
        this.dispatchService = Objects.requireNonNull(dispatchService);
        this.expectedToken = properties.getAccessToken().getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping(PATH)
    public Mono<ApiResponse<NightExecutionInternalDispatchDtos.Response>> dispatch(
            @RequestHeader(value = ACCESS_TOKEN_HEADER, required = false) String accessToken,
            @Valid @RequestBody NightExecutionInternalDispatchDtos.Request request,
            ServerWebExchange exchange) {
        requireToken(accessToken);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return dispatchService.dispatchBatch(request.linuxServerId(), request.domainTaskIds(), traceId)
                .map(result -> ApiResponse.ok(
                        NightExecutionInternalDispatchDtos.Response.from(result), traceId));
    }

    private void requireToken(String value) {
        byte[] provided = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        // 配置缺失时必须 fail-closed，不能让“空期望值 + 缺失 header”通过比较。
        if (expectedToken.length == 0 || !MessageDigest.isEqual(expectedToken, provided)) {
            throw new PlatformException(ErrorCode.UNAUTHENTICATED, "XXL-JOB access token 无效");
        }
    }
}
