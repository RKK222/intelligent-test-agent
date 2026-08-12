package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.localclient.LocalClientModelGrant;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.opencode.runtime.internalmodel.InternalModelProxyRuntimeSettings;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientModelGrantService;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** 本地 loopback 模型中继入口；短期 grant 换成后台内部密钥后复用既有模型代理实现。 */
@RestController
public class LocalClientModelProxyController {

    public static final String BASE_PATH =
            "/api/internal/platform/local-opencode-client/model-proxy/v1";

    private final LocalClientModelGrantService grantService;
    private final UserRepository userRepository;
    private final InternalModelProxyRuntimeSettings internalSettings;
    private final InternalModelProxyController internalController;
    private final LocalClientControlSecuritySettings securitySettings;

    public LocalClientModelProxyController(
            LocalClientModelGrantService grantService,
            UserRepository userRepository,
            InternalModelProxyRuntimeSettings internalSettings,
            InternalModelProxyController internalController,
            LocalClientControlSecuritySettings securitySettings) {
        this.grantService = Objects.requireNonNull(grantService);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.internalSettings = Objects.requireNonNull(internalSettings);
        this.internalController = Objects.requireNonNull(internalController);
        this.securitySettings = Objects.requireNonNull(securitySettings);
    }

    @RequestMapping(BASE_PATH + "/**")
    public Mono<Void> proxy(ServerWebExchange exchange) {
        securitySettings.requireSecure(
                exchange.getRequest().getURI(),
                exchange.getRequest().getHeaders(),
                exchange.getRequest().getRemoteAddress());
        LocalClientModelGrant grant = grantService.authenticate(AuthWebSupport.extractBearerToken(exchange));
        User user = userRepository.findByUserId(grant.userId())
                .filter(User::canLogin)
                .orElseThrow(() -> new PlatformException(ErrorCode.UNAUTHENTICATED, "本地客户端用户已失效"));
        String fullPath = exchange.getRequest().getURI().getRawPath();
        if (fullPath == null || !fullPath.startsWith(BASE_PATH)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "本地客户端模型路径无效");
        }
        String suffix = fullPath.substring(BASE_PATH.length());
        String internalPath = InternalModelProxyRuntimeSettings.PROXY_PATH + suffix;
        ServerHttpRequest request = exchange.getRequest().mutate()
                .path(internalPath)
                .headers(headers -> {
                    headers.setBearerAuth(internalSettings.requireApiKey());
                    headers.set(InternalModelProxyForwardingService.UCID_HEADER, user.unifiedAuthId());
                    headers.remove(HttpHeaders.COOKIE);
                })
                .build();
        return internalController.proxy(exchange.mutate().request(request).build());
    }
}
