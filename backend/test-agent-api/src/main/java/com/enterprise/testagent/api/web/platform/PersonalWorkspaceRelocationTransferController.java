package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
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

/** 目标 Java 的内部搬迁 ticket 入口；文件内容只能在随后的一次性 WebSocket 上传。 */
@RestController
public class PersonalWorkspaceRelocationTransferController {

    public static final String TICKET_PATH =
            "/api/internal/platform/workspace-management/personal-workspace-relocations/transfer-tickets";
    public static final String WEB_SOCKET_PATH =
            "/api/internal/platform/workspace-management/personal-workspace-relocations/transfer/ws";
    public static final String INTERNAL_ORIGIN = "https://test-agent.internal";
    private static final String ACCESS_TOKEN_HEADER = "XXL-JOB-ACCESS-TOKEN";

    private final PersonalWorkspaceRelocationTransferTicketService ticketService;
    private final byte[] expectedToken;

    public PersonalWorkspaceRelocationTransferController(
            PersonalWorkspaceRelocationTransferTicketService ticketService,
            XxlJobProperties properties) {
        this.ticketService = Objects.requireNonNull(ticketService);
        this.expectedToken = properties.getAccessToken().getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping(TICKET_PATH)
    public ApiResponse<PersonalWorkspaceRelocationTransferDtos.TicketResponse> issue(
            @RequestHeader(value = ACCESS_TOKEN_HEADER, required = false) String accessToken,
            @Valid @RequestBody PersonalWorkspaceRelocationTransferDtos.TicketRequest request,
            ServerWebExchange exchange) {
        requireToken(accessToken);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(ticketService.issue(request, traceId), traceId);
    }

    private void requireToken(String value) {
        byte[] provided = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        if (expectedToken.length == 0 || !MessageDigest.isEqual(expectedToken, provided)) {
            throw new PlatformException(ErrorCode.UNAUTHENTICATED, "XXL-JOB access token 无效");
        }
    }
}
