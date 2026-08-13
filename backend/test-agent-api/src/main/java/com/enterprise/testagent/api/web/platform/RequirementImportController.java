package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.workspace.RequirementImportApplicationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 同源需求导入目录 API；Controller 只提取登录身份并委托应用服务。 */
@RestController
public class RequirementImportController {

    private final RequirementImportApplicationService importService;

    public RequirementImportController(RequirementImportApplicationService importService) {
        this.importService = importService;
    }

    /** 查询当前统一认证用户有权访问的全部 TCDS 应用。 */
    @GetMapping("/api/v1/requirement-import/applications")
    public Mono<ApiResponse<List<RequirementImportApplicationService.ApplicationOption>>> applications(
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> ApiResponse.ok(
                        importService.listApplications(principal.unifiedAuthId()),
                        traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 查询指定应用和版本的父子条目，响应中不包含 token 或文档地址。 */
    @GetMapping("/api/v1/requirement-import/sub-items")
    public Mono<ApiResponse<List<RequirementImportApplicationService.ItemOption>>> subItems(
            @RequestParam String appShortName,
            @RequestParam String editionId,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> ApiResponse.ok(
                        importService.listItems(principal.unifiedAuthId(), appShortName, editionId),
                        traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
