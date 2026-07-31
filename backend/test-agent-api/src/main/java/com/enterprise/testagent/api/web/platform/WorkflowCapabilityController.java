package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.integration.workflow.WorkflowCapabilityApplicationService;
import com.enterprise.testagent.integration.workflow.WorkflowCapabilityCaller;
import com.enterprise.testagent.integration.workflow.WorkflowCapabilityHmacAuthenticator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Python只可调用的白名单平台能力入口；本Controller不承载工作流业务对象。 */
@RestController
public class WorkflowCapabilityController {

    public static final String BASE_PATH = "/api/internal/workflow-capabilities/v1";
    private static final String CLIENT_HEADER = "X-Workflow-Client-Id";
    private static final String USER_HEADER = "X-Workflow-User-Id";
    private static final String SESSION_HEADER = "X-Workflow-Session-Digest";
    private static final String RUNNER_HEADER = "X-Workflow-Runner-Id";
    private static final String TIMESTAMP_HEADER = "X-Workflow-Timestamp";
    private static final String NONCE_HEADER = "X-Workflow-Nonce";
    private static final String BODY_DIGEST_HEADER = "X-Workflow-Body-SHA256";
    private static final String SIGNATURE_HEADER = "X-Workflow-Signature";
    private final WorkflowCapabilityApplicationService service;
    private final WorkflowCapabilityHmacAuthenticator authenticator;
    private final ObjectMapper objectMapper;

    public WorkflowCapabilityController(
            WorkflowCapabilityApplicationService service,
            WorkflowCapabilityHmacAuthenticator authenticator,
            ObjectMapper objectMapper) {
        this.service = Objects.requireNonNull(service);
        this.authenticator = Objects.requireNonNull(authenticator);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @GetMapping(BASE_PATH + "/repositories")
    public Mono<ApiResponse<Object>> repositories(
            @RequestHeader(value = CLIENT_HEADER, required = false) String clientId,
            @RequestHeader(value = USER_HEADER, required = false) String userId,
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionDigest,
            @RequestHeader(value = TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = NONCE_HEADER, required = false) String nonce,
            @RequestHeader(value = BODY_DIGEST_HEADER, required = false) String bodyDigest,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            ServerWebExchange exchange) {
        String path = BASE_PATH + "/repositories";
        return execute(exchange, () -> service.listRepositories(authenticateWorkflow(
                "GET", path, new byte[0], clientId, userId, sessionDigest,
                timestamp, nonce, bodyDigest, signature)));
    }

    @GetMapping(BASE_PATH + "/repositories/{repositoryId}/branches")
    public Mono<ApiResponse<Object>> branches(
            @PathVariable String repositoryId,
            @RequestHeader(value = CLIENT_HEADER, required = false) String clientId,
            @RequestHeader(value = USER_HEADER, required = false) String userId,
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionDigest,
            @RequestHeader(value = TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = NONCE_HEADER, required = false) String nonce,
            @RequestHeader(value = BODY_DIGEST_HEADER, required = false) String bodyDigest,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            ServerWebExchange exchange) {
        String path = BASE_PATH + "/repositories/" + repositoryId + "/branches";
        return execute(exchange, () -> service.listBranches(authenticateWorkflow(
                "GET", path, new byte[0], clientId, userId, sessionDigest,
                timestamp, nonce, bodyDigest, signature), repositoryId));
    }

    @PostMapping(path = BASE_PATH + "/repositories/authorize", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<Object>> authorize(
            @RequestBody byte[] body,
            @RequestHeader(value = CLIENT_HEADER, required = false) String clientId,
            @RequestHeader(value = USER_HEADER, required = false) String userId,
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionDigest,
            @RequestHeader(value = TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = NONCE_HEADER, required = false) String nonce,
            @RequestHeader(value = BODY_DIGEST_HEADER, required = false) String bodyDigest,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            ServerWebExchange exchange) {
        String path = BASE_PATH + "/repositories/authorize";
        return execute(exchange, () -> {
            WorkflowCapabilityCaller caller = authenticateWorkflow(
                    "POST", path, body, clientId, userId, sessionDigest,
                    timestamp, nonce, bodyDigest, signature);
            RepositoryAuthorizationRequest request = read(body, RepositoryAuthorizationRequest.class);
            return service.authorizeRepositories(caller, request.repositoryIds());
        });
    }

    @PostMapping(path = BASE_PATH + "/checkout-tickets", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<Object>> issueCheckoutTicket(
            @RequestBody byte[] body,
            @RequestHeader(value = CLIENT_HEADER, required = false) String clientId,
            @RequestHeader(value = USER_HEADER, required = false) String userId,
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionDigest,
            @RequestHeader(value = TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = NONCE_HEADER, required = false) String nonce,
            @RequestHeader(value = BODY_DIGEST_HEADER, required = false) String bodyDigest,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            ServerWebExchange exchange) {
        String path = BASE_PATH + "/checkout-tickets";
        return execute(exchange, () -> {
            WorkflowCapabilityCaller caller = authenticateWorkflow(
                    "POST", path, body, clientId, userId, sessionDigest,
                    timestamp, nonce, bodyDigest, signature);
            CheckoutTicketRequest request = read(body, CheckoutTicketRequest.class);
            return service.issueCheckoutTicket(
                    caller,
                    request.repositoryId(),
                    request.taskId(),
                    request.runId(),
                    request.runnerId(),
                    request.runnerPublicKey(),
                    request.targetBranch(),
                    request.baselineBranch());
        });
    }

    @PostMapping(path = BASE_PATH + "/checkout-tickets/{ticketId}/consume", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<Object>> consumeCheckoutTicket(
            @PathVariable String ticketId,
            @RequestBody byte[] body,
            @RequestHeader(value = RUNNER_HEADER, required = false) String runnerId,
            @RequestHeader(value = TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = NONCE_HEADER, required = false) String nonce,
            @RequestHeader(value = BODY_DIGEST_HEADER, required = false) String bodyDigest,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            ServerWebExchange exchange) {
        String path = BASE_PATH + "/checkout-tickets/" + ticketId + "/consume";
        return execute(exchange, () -> {
            authenticator.authenticateRunner(
                    "POST", path, body, runnerId, timestamp, nonce, bodyDigest, signature);
            CheckoutConsumeRequest request = read(body, CheckoutConsumeRequest.class);
            return service.consumeCheckoutTicket(ticketId, request.taskId(), request.runId(), request.runnerId());
        });
    }

    @PostMapping(path = BASE_PATH + "/model-grants", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<Object>> issueModelGrant(
            @RequestBody byte[] body,
            @RequestHeader(value = CLIENT_HEADER, required = false) String clientId,
            @RequestHeader(value = USER_HEADER, required = false) String userId,
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionDigest,
            @RequestHeader(value = TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = NONCE_HEADER, required = false) String nonce,
            @RequestHeader(value = BODY_DIGEST_HEADER, required = false) String bodyDigest,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            ServerWebExchange exchange) {
        String path = BASE_PATH + "/model-grants";
        return execute(exchange, () -> {
            WorkflowCapabilityCaller caller = authenticateWorkflow(
                    "POST", path, body, clientId, userId, sessionDigest,
                    timestamp, nonce, bodyDigest, signature);
            ModelGrantRequest request = read(body, ModelGrantRequest.class);
            return service.issueModelGrant(caller, request.taskId(), request.runId(), request.analyzerIds());
        });
    }

    @PostMapping(path = BASE_PATH + "/model-grants/{grantId}/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<Object>> refreshModelGrant(
            @PathVariable String grantId,
            @RequestBody byte[] body,
            @RequestHeader(value = CLIENT_HEADER, required = false) String clientId,
            @RequestHeader(value = USER_HEADER, required = false) String userId,
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionDigest,
            @RequestHeader(value = TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = NONCE_HEADER, required = false) String nonce,
            @RequestHeader(value = BODY_DIGEST_HEADER, required = false) String bodyDigest,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            ServerWebExchange exchange) {
        String path = BASE_PATH + "/model-grants/" + grantId + "/refresh";
        return execute(exchange, () -> {
            WorkflowCapabilityCaller caller = authenticateWorkflow(
                    "POST", path, body, clientId, userId, sessionDigest,
                    timestamp, nonce, bodyDigest, signature);
            GrantScopeRequest request = read(body, GrantScopeRequest.class);
            return service.refreshModelGrant(caller, grantId, request.taskId(), request.runId());
        });
    }

    @PostMapping(path = BASE_PATH + "/model-grants/{grantId}/revoke", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<Object>> revokeModelGrant(
            @PathVariable String grantId,
            @RequestBody byte[] body,
            @RequestHeader(value = CLIENT_HEADER, required = false) String clientId,
            @RequestHeader(value = USER_HEADER, required = false) String userId,
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionDigest,
            @RequestHeader(value = TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = NONCE_HEADER, required = false) String nonce,
            @RequestHeader(value = BODY_DIGEST_HEADER, required = false) String bodyDigest,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            ServerWebExchange exchange) {
        String path = BASE_PATH + "/model-grants/" + grantId + "/revoke";
        return execute(exchange, () -> {
            WorkflowCapabilityCaller caller = authenticateWorkflow(
                    "POST", path, body, clientId, userId, sessionDigest,
                    timestamp, nonce, bodyDigest, signature);
            GrantScopeRequest request = read(body, GrantScopeRequest.class);
            service.revokeModelGrant(caller, grantId, request.taskId(), request.runId());
            return Map.of("revoked", true);
        });
    }

    @PostMapping(path = BASE_PATH + "/model-grants/revoke-run", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<Object>> revokeRunModelGrants(
            @RequestBody byte[] body,
            @RequestHeader(value = CLIENT_HEADER, required = false) String clientId,
            @RequestHeader(value = USER_HEADER, required = false) String userId,
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionDigest,
            @RequestHeader(value = TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = NONCE_HEADER, required = false) String nonce,
            @RequestHeader(value = BODY_DIGEST_HEADER, required = false) String bodyDigest,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            ServerWebExchange exchange) {
        String path = BASE_PATH + "/model-grants/revoke-run";
        return execute(exchange, () -> {
            WorkflowCapabilityCaller caller = authenticateWorkflow(
                    "POST", path, body, clientId, userId, sessionDigest,
                    timestamp, nonce, bodyDigest, signature);
            GrantScopeRequest request = read(body, GrantScopeRequest.class);
            service.revokeModelGrants(caller, request.taskId(), request.runId());
            return Map.of("revoked", true);
        });
    }

    @PostMapping(path = BASE_PATH + "/permissions/super-admin/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<Object>> verifySuperAdmin(
            @RequestBody byte[] body,
            @RequestHeader(value = CLIENT_HEADER, required = false) String clientId,
            @RequestHeader(value = USER_HEADER, required = false) String userId,
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionDigest,
            @RequestHeader(value = TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = NONCE_HEADER, required = false) String nonce,
            @RequestHeader(value = BODY_DIGEST_HEADER, required = false) String bodyDigest,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            ServerWebExchange exchange) {
        String path = BASE_PATH + "/permissions/super-admin/verify";
        return execute(exchange, () -> Map.of("allowed", service.verifySuperAdmin(authenticateWorkflow(
                "POST", path, body, clientId, userId, sessionDigest,
                timestamp, nonce, bodyDigest, signature))));
    }

    private WorkflowCapabilityCaller authenticateWorkflow(
            String method,
            String path,
            byte[] body,
            String clientId,
            String userId,
            String sessionDigest,
            String timestamp,
            String nonce,
            String bodyDigest,
            String signature) {
        return authenticator.authenticateWorkflow(
                method,
                path,
                body,
                clientId,
                userId,
                sessionDigest,
                timestamp,
                nonce,
                bodyDigest,
                signature);
    }

    private Mono<ApiResponse<Object>> execute(ServerWebExchange exchange, ThrowingSupplier supplier) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> ApiResponse.ok(supplier.get(), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private <T> T read(byte[] body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (IOException exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "workflow能力请求体无效");
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        Object get();
    }

    private record RepositoryAuthorizationRequest(List<String> repositoryIds) {
    }

    private record CheckoutTicketRequest(
            String repositoryId,
            String taskId,
            String runId,
            String runnerId,
            String runnerPublicKey,
            String targetBranch,
            String baselineBranch) {
    }

    private record CheckoutConsumeRequest(String taskId, String runId, String runnerId) {
    }

    private record ModelGrantRequest(String taskId, String runId, List<String> analyzerIds) {
    }

    private record GrantScopeRequest(String taskId, String runId) {
    }
}
