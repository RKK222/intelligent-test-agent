package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.integration.codeknowledge.TraceWeaveCodeKnowledgeService;
import com.enterprise.testagent.opencode.runtime.process.CodeKnowledgeToolTokenService;
import com.enterprise.testagent.workspace.CodeSourceQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** OpenCode 公共只读 Tool 的代码知识与固定源码基线入口。 */
@RestController
public class CodeKnowledgeToolController {

    private final CodeKnowledgeToolTokenService tokens;
    private final TraceWeaveCodeKnowledgeService graph;
    private final CodeSourceQueryService source;
    private final ObjectMapper mapper;

    public CodeKnowledgeToolController(
            CodeKnowledgeToolTokenService tokens,
            TraceWeaveCodeKnowledgeService graph,
            CodeSourceQueryService source,
            ObjectMapper mapper) {
        this.tokens = Objects.requireNonNull(tokens);
        this.graph = Objects.requireNonNull(graph);
        this.source = Objects.requireNonNull(source);
        this.mapper = Objects.requireNonNull(mapper);
    }

    /** 图谱操作在弹性线程执行；context 同步返回各 Mimo 源码基线的实时可用状态。 */
    @PostMapping(CodeKnowledgeToolTokenService.CODE_KNOWLEDGE_ENDPOINT_PATH)
    public Mono<ApiResponse<Object>> executeGraph(
            @RequestBody CodeKnowledgeToolRequest request,
            ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> {
                    var principal = tokens.authenticate(authorization);
                    var result = graph.execute(principal.userId(), request.toQuery());
                    JsonNode data = result.data();
                    if ("context".equals(normalizeOperation(request.operation())) && data.isObject()) {
                        ObjectNode enriched = ((ObjectNode) data).deepCopy();
                        enriched.set("sourceContexts", sourceContexts(principal.userId(), result.repositoryIds()));
                        data = enriched;
                    }
                    // Spring Boot 4 的 HTTP codec 使用 Jackson 3；Jackson 2 JsonNode 必须先转换为普通对象，
                    // 否则会按 JavaBean 暴露 isArray/isObject 等类型标志，而不是输出真实图谱字段。
                    return ApiResponse.ok(mapper.convertValue(data, Object.class), traceId);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 源码入口只接受 list/search/read，并由服务端从 repositoryId 解析受控基线路径。 */
    @PostMapping(CodeKnowledgeToolTokenService.CODE_SOURCE_ENDPOINT_PATH)
    public Mono<ApiResponse<Object>> executeSource(
            @RequestBody CodeSourceToolRequest request,
            ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> {
                    var principal = tokens.authenticate(authorization);
                    Object result = switch (normalizeOperation(request.operation())) {
                        case "list" -> source.list(
                                principal.userId(), request.repositoryId(), request.path(), request.limit());
                        case "search" -> source.search(
                                principal.userId(), request.repositoryId(), request.path(), request.query(), request.limit());
                        case "read" -> source.read(
                                principal.userId(), request.repositoryId(), request.path(),
                                request.startLine(), request.endLine());
                        default -> throw new com.enterprise.testagent.common.error.PlatformException(
                                com.enterprise.testagent.common.error.ErrorCode.VALIDATION_ERROR,
                                "不支持的源码查询操作");
                    };
                    return ApiResponse.ok(result, traceId);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ArrayNode sourceContexts(
            com.enterprise.testagent.domain.user.UserId userId,
            List<String> repositoryIds) {
        ArrayNode contexts = mapper.createArrayNode();
        for (String repositoryId : repositoryIds) {
            try {
                contexts.add(mapper.valueToTree(source.context(userId, repositoryId)));
            } catch (PlatformException exception) {
                ObjectNode unavailable = contexts.addObject();
                unavailable.put("repositoryId", repositoryId);
                unavailable.put("available", false);
                unavailable.put("sourceState", "UNAVAILABLE");
                unavailable.put("code", exception.errorCode().name());
                if (exception.errorCode() == com.enterprise.testagent.common.error.ErrorCode.CONFLICT) {
                    unavailable.put("preparationAction", "OPEN_APP_SOURCE_PREPARATION");
                }
                Object reason = exception.details().get("reason");
                if (reason != null) {
                    unavailable.put("reason", reason.toString());
                }
            }
        }
        return contexts;
    }

    private String normalizeOperation(String operation) {
        return operation == null ? "" : operation.trim().toLowerCase(Locale.ROOT);
    }

    /** Tool 可传查询与安全预算，但不能传 TraceWeave 内部地址、应用组或图仓库 ID。 */
    public record CodeKnowledgeToolRequest(
            String sessionId,
            String operation,
            List<String> repositoryIds,
            String query,
            String assetId,
            List<String> assetTypes,
            List<String> semanticKinds,
            List<String> businessKinds,
            Integer limit,
            Boolean includeUncertain,
            String versionKey,
            Integer maxDepth,
            Integer maxNodes,
            Integer maxEdges,
            Long timeoutMillis,
            Integer maxPaths) {

        TraceWeaveCodeKnowledgeService.CodeKnowledgeQuery toQuery() {
            return new TraceWeaveCodeKnowledgeService.CodeKnowledgeQuery(
                    operation, repositoryIds, query, assetId, assetTypes, semanticKinds, businessKinds,
                    limit, includeUncertain, versionKey, maxDepth, maxNodes, maxEdges, timeoutMillis, maxPaths);
        }
    }

    /** Tool 不能提供物理根目录或 Workspace ID，只能选择已配置的 Mimo 版本库。 */
    public record CodeSourceToolRequest(
            String sessionId,
            String operation,
            String repositoryId,
            String path,
            String query,
            Integer startLine,
            Integer endLine,
            Integer limit) {
    }
}
