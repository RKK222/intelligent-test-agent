package com.enterprise.testagent.opencode.client;

import com.enterprise.testagent.domain.node.ExecutionNode;
import org.springframework.web.reactive.function.client.WebClient;

/** generated SDK 的可插拔 WebClient 传输；服务器目标仍使用默认 HTTP，本地目标由隧道实现。 */
public interface OpencodeWebClientTransport {

    boolean supports(ExecutionNode node);

    WebClient create(ExecutionNode node, String traceId, int maxInMemorySize);
}
