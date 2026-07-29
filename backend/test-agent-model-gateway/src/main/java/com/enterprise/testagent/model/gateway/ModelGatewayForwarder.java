package com.enterprise.testagent.model.gateway;

import org.springframework.http.codec.multipart.Part;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** API 层依赖的中立模型网关转发边界，避免 Controller 直接耦合供应商实现。 */
public interface ModelGatewayForwarder {

    PreparedModelGatewayRequest prepare(ServerWebExchange exchange, byte[] requestBody);

    Mono<Void> forward(
            ServerWebExchange exchange,
            PreparedModelGatewayRequest request,
            ModelGatewayCaller caller,
            String traceId);

    PreparedModelGatewayMultipartRequest prepareMultipart(
            ServerWebExchange exchange,
            MultiValueMap<String, Part> parts);

    Mono<Void> forwardMultipart(
            ServerWebExchange exchange,
            PreparedModelGatewayMultipartRequest request,
            ModelGatewayCaller caller,
            String traceId);
}
