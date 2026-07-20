package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.xxljob.XxlJobSsoTicketIssue;
import com.enterprise.testagent.xxljob.XxlJobSsoTicketService;
import java.util.Objects;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 超级管理员进入同源 XXL Admin iframe 前签发 60 秒一次性表单票据。 */
@RestController
@RequestMapping("/api/internal/platform/xxl-job")
public class XxlJobSsoTicketController {

    private final XxlJobSsoTicketService ticketService;

    public XxlJobSsoTicketController(XxlJobSsoTicketService ticketService) {
        this.ticketService = Objects.requireNonNull(ticketService, "ticketService must not be null");
    }

    @PostMapping("/sso-tickets")
    public Mono<ApiResponse<XxlJobSsoTicketIssue>> issue(ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> ApiResponse.ok(ticketService.issue(principal), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
