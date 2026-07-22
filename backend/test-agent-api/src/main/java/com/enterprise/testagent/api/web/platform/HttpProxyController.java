package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

@RestController
public class HttpProxyController {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpProxyController.class);

    private final HttpProxyService proxyService;

    public HttpProxyController(HttpProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @PostMapping("/api/proxy/call")
    public ResponseEntity<ApiResponse<HttpProxyService.ProxyResponse>> proxyCall(
            @RequestBody HttpProxyService.ProxyRequest request,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        LOGGER.info("HTTP proxy call requested by user={}, uri={}, method={}, traceId={}",
                principal.username(), request.uri(), request.method(), traceId);

        HttpProxyService.ProxyResponse response = proxyService.execute(request);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }
}
