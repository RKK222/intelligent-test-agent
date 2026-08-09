package com.enterprise.testagent.api.web.common;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.externalapi.ExternalApiPrincipal;
import java.util.Optional;
import org.springframework.web.server.ServerWebExchange;

/** 外部 API 专用认证 attribute 与精确命名空间判断。 */
public final class ExternalApiWebSupport {

    public static final String EXTERNAL_ROOT = "/api/external/v1";
    public static final String EXTERNAL_PREFIX = EXTERNAL_ROOT + "/";
    public static final String PRINCIPAL_ATTR = "test-agent.external-api.principal";

    private ExternalApiWebSupport() {
    }

    public static boolean isExternalPath(String path) {
        return EXTERNAL_ROOT.equals(path) || path.startsWith(EXTERNAL_PREFIX);
    }

    public static ExternalApiPrincipal getPrincipal(ServerWebExchange exchange) {
        return getOptionalPrincipal(exchange)
                .orElseThrow(() -> new PlatformException(ErrorCode.UNAUTHENTICATED, "未认证"));
    }

    public static Optional<ExternalApiPrincipal> getOptionalPrincipal(ServerWebExchange exchange) {
        Object value = exchange.getAttribute(PRINCIPAL_ATTR);
        return value instanceof ExternalApiPrincipal principal ? Optional.of(principal) : Optional.empty();
    }
}
