package com.enterprise.testagent.integration.codeknowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.user.UserId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TraceWeaveCodeKnowledgeServiceTest {

    private static final UserId USER_ID = new UserId("usr_pilot");
    private static final String ASSET_ID = "00000000-0000-0000-0000-000000000101";
    private static final String SCOPED_ASSET_ID = "00000000-0000-0000-0000-000000000103";

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<CapturedRequest> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger versionResolutions = new AtomicInteger();
    private HttpServer server;
    private String baseUrl;
    private boolean advanceVersionOnSecondResolution;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void searchAlwaysRestrictsTraceWeaveRequestToConfiguredRepositoryScope() {
        var result = service().execute(USER_ID, new TraceWeaveCodeKnowledgeService.CodeKnowledgeQuery(
                "search", List.of("repo_orders"), "submit", null,
                List.of("JAVA_METHOD"), List.of("ENDPOINT"), List.of("TRANSACTION"),
                25, null, null, null, null, null, null, null));

        assertThat(result.repositoryIds()).containsExactly("repo_orders");
        assertThat(result.data().path("graphEvidence").path("view").asText()).isEqualTo("DEV");
        CapturedRequest search = requests.stream()
                .filter(request -> request.uri().getPath().endsWith("/assets/search"))
                .findFirst().orElseThrow();
        assertThat(search.uri().getRawQuery())
                .contains("q=submit")
                .contains("applicationGroupId=group-orders")
                .contains("repositoryId=tw-orders")
                .contains("assetType=JAVA_METHOD")
                .doesNotContain("tw-payments");
    }

    @Test
    void definitionRejectsAssetOutsideSelectedScopeBeforeDefinitionRequest() {
        assertThatThrownBy(() -> service().execute(USER_ID,
                        new TraceWeaveCodeKnowledgeService.CodeKnowledgeQuery(
                                "definition", List.of("repo_orders"), null, ASSET_ID,
                                null, null, null, null, null, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(requests).noneMatch(request -> request.uri().getPath().endsWith("/definition"));
    }

    @Test
    void impactUsesBusinessTraversalAndMappedDependencyRepositories() throws Exception {
        var result = service().execute(USER_ID, new TraceWeaveCodeKnowledgeService.CodeKnowledgeQuery(
                "impact", List.of("repo_orders"), null, "00000000-0000-0000-0000-000000000102",
                null, null, null, null, false, null, 12, 900, 4000, 9000L, 30));

        CapturedRequest impact = requests.stream()
                .filter(request -> request.uri().getPath().endsWith("/analysis/call-chains"))
                .findFirst().orElseThrow();
        JsonNode body = mapper.readTree(impact.body());
        assertThat(body.path("direction").asText()).isEqualTo("UPSTREAM");
        assertThat(body.path("version").path("versionKey").asText()).isEqualTo("dev-42");
        assertThat(body.path("version").path("view").isNull()).isTrue();
        assertThat(body.path("filter").path("relationSources").toString())
                .contains("STATIC_ANALYSIS", "TOOL_IMPORT");
        assertThat(body.path("traversalOptions").path("mode").asText()).isEqualTo("BUSINESS");
        assertThat(body.path("traversalOptions").path("entryScope").asText()).isEqualTo("FILE_ELEMENTS");
        assertThat(body.path("traversalOptions").path("pathAssembly").asText()).isEqualTo("ENTRY_TO_TARGET");
        assertThat(body.path("traversalOptions").path("repositoryContext").toString())
                .contains("tw-orders", "tw-payments");
        assertThat(result.data().path("graphEvidence").path("versionKey").asText()).isEqualTo("dev-42");
        assertThat(result.data().path("detailUrl").asText())
                .contains("tab=impact", "versionKey=dev-42", "includeUncertain=false", "budget=");
        assertThat(URI.create(result.data().path("detailUrl").asText()).getRawQuery())
                .doesNotContain("view=");
        assertThat(result.data().path("queryRequest").path("version").path("versionKey").asText())
                .isEqualTo("dev-42");
    }

    @Test
    void definitionKeepsOnlyConfiguredRepositoryEvidenceForMergedAssets() {
        var result = service().execute(USER_ID, new TraceWeaveCodeKnowledgeService.CodeKnowledgeQuery(
                "definition", List.of("repo_orders"), null, SCOPED_ASSET_ID,
                null, null, null, null, null, null, null, null, null, null, null));

        assertThat(result.data().path("definitionScopeFilteredCount").asInt()).isEqualTo(1);
        assertThat(result.data().path("data").path("definitions").size()).isEqualTo(1);
        assertThat(result.data().path("data").path("definitions").get(0)
                .path("source").path("repositoryId").asText()).isEqualTo("tw-orders");
    }

    @Test
    void disabledOrNonPilotConfigurationFailsBeforeAnyNetworkCall() {
        CommonParameterValues values = configuredValues(false, List.of("usr_pilot"));
        var settings = new TraceWeaveCodeKnowledgeSettings(values, mapper);
        var service = new TraceWeaveCodeKnowledgeService(
                settings, mapper, HttpClient.newHttpClient(), Duration.ofSeconds(2), 1024 * 1024);

        assertThatThrownBy(() -> service.execute(USER_ID,
                        new TraceWeaveCodeKnowledgeService.CodeKnowledgeQuery(
                                "context", List.of(), null, null, null, null, null,
                                null, null, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(requests).isEmpty();
    }

    @Test
    void workbenchScopeHidesTraceWeaveInternalsAndTreatsDisabledAsUnavailable() {
        var available = new TraceWeaveCodeKnowledgeSettings(
                configuredValues(true, List.of(USER_ID.value())), mapper).selectionScope(USER_ID);

        assertThat(available.available()).isTrue();
        assertThat(available.defaultView()).isEqualTo("DEV");
        assertThat(available.repositoryIds()).containsExactly("repo_orders", "repo_payments");

        var disabled = new TraceWeaveCodeKnowledgeSettings(
                configuredValues(false, List.of(USER_ID.value())), mapper).selectionScope(USER_ID);
        assertThat(disabled.available()).isFalse();
        assertThat(disabled.reason()).isEqualTo("CODE_KNOWLEDGE_DISABLED");
        assertThat(disabled.repositoryIds()).isEmpty();
    }

    @Test
    void graphOperationsOtherThanContextRequireAnExplicitMimoRepositoryScope() {
        assertThatThrownBy(() -> service().execute(USER_ID,
                        new TraceWeaveCodeKnowledgeService.CodeKnowledgeQuery(
                                "search", List.of(), "submit", null, null, null, null,
                                null, null, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        assertThat(requests).isEmpty();
    }

    @Test
    void dynamicViewIsFixedForTheQueryAndReportsConcurrentVersionAdvance() {
        advanceVersionOnSecondResolution = true;

        var result = service().execute(USER_ID, new TraceWeaveCodeKnowledgeService.CodeKnowledgeQuery(
                "context", List.of("repo_orders"), null, null,
                null, null, null, null, null, null, null, null, null, null, null));

        assertThat(result.data().path("graphEvidence").path("versionKey").asText()).isEqualTo("dev-42");
        assertThat(result.data().path("knowledgeUpdating").asBoolean()).isTrue();
        assertThat(result.data().path("latestGraphEvidence").path("versionKey").asText()).isEqualTo("dev-43");
    }

    private TraceWeaveCodeKnowledgeService service() {
        var settings = new TraceWeaveCodeKnowledgeSettings(
                configuredValues(true, List.of(USER_ID.value())), mapper);
        return new TraceWeaveCodeKnowledgeService(
                settings, mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                Duration.ofSeconds(3), 1024 * 1024);
    }

    private CommonParameterValues configuredValues(boolean enabled, List<String> pilots) {
        CommonParameterValues values = mock(CommonParameterValues.class);
        when(values.resolvedValue(TraceWeaveCodeKnowledgeSettings.PARAM_BASE_URL))
                .thenReturn(Optional.of(baseUrl));
        when(values.resolvedValue(TraceWeaveCodeKnowledgeSettings.PARAM_WEB_BASE_URL))
                .thenReturn(Optional.of(baseUrl));
        String scope = """
                {"enabled":%s,"pilotUserIds":%s,"defaultView":"DEV","repositories":[
                  {"codeRepositoryId":"repo_orders","traceweaveApplicationGroupId":"group-orders",
                   "traceweaveRepositoryId":"tw-orders","dependencyRepositoryIds":["repo_payments"]},
                  {"codeRepositoryId":"repo_payments","traceweaveApplicationGroupId":"group-payments",
                   "traceweaveRepositoryId":"tw-payments","dependencyRepositoryIds":[]}
                ]}
                """.formatted(enabled, toJson(pilots));
        when(values.resolvedValue(TraceWeaveCodeKnowledgeSettings.PARAM_SCOPE))
                .thenReturn(Optional.of(scope));
        return values;
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        requests.add(new CapturedRequest(exchange.getRequestMethod(), exchange.getRequestURI(),
                new String(requestBody, StandardCharsets.UTF_8)));
        String path = exchange.getRequestURI().getPath();
        String response;
        int status = 200;
        if (path.endsWith("/graph-versions/resolve")) {
            boolean advanced = advanceVersionOnSecondResolution && versionResolutions.incrementAndGet() > 1;
            response = advanced
                    ? "{\"versionKey\":\"dev-43\",\"versionSeq\":43}"
                    : "{\"versionKey\":\"dev-42\",\"versionSeq\":42}";
        } else if (path.endsWith("/assets/search")) {
            response = "{\"items\":[],\"nextCursor\":null}";
        } else if (path.endsWith("/source-scan-baselines")) {
            response = "[]";
        } else if (path.endsWith("/source-scan-tasks")) {
            response = "[]";
        } else if (path.endsWith("/assets/" + ASSET_ID)) {
            response = "{\"assetId\":\"" + ASSET_ID
                    + "\",\"applicationGroupId\":\"group-outside\",\"repositoryId\":\"tw-outside\","
                    + "\"assetType\":\"JAVA_METHOD\"}";
        } else if (path.endsWith("/assets/00000000-0000-0000-0000-000000000102")) {
            response = "{\"assetId\":\"00000000-0000-0000-0000-000000000102\","
                    + "\"applicationGroupId\":\"group-orders\",\"repositoryId\":\"tw-orders\","
                    + "\"assetType\":\"SOURCE_FILE\"}";
        } else if (path.endsWith("/assets/" + SCOPED_ASSET_ID + "/definition")) {
            response = "{\"asset\":{\"assetId\":\"" + SCOPED_ASSET_ID + "\","
                    + "\"applicationGroupId\":\"group-orders\",\"repositoryId\":\"tw-orders\"},"
                    + "\"resolvedVersion\":{\"versionKey\":\"dev-42\",\"versionSeq\":42},"
                    + "\"definitions\":[{\"source\":{\"repositoryId\":\"tw-orders\"}},"
                    + "{\"source\":{\"repositoryId\":\"tw-outside\"}}],"
                    + "\"truncation\":{\"truncated\":false}}";
        } else if (path.endsWith("/assets/" + SCOPED_ASSET_ID)) {
            response = "{\"assetId\":\"" + SCOPED_ASSET_ID + "\","
                    + "\"applicationGroupId\":\"group-orders\",\"repositoryId\":\"tw-orders\","
                    + "\"assetType\":\"JAVA_METHOD\"}";
        } else if (path.endsWith("/analysis/call-chains")) {
            response = "{\"queryId\":\"00000000-0000-0000-0000-000000000201\","
                    + "\"resolvedVersion\":{\"versionKey\":\"dev-42\",\"versionSeq\":42},"
                    + "\"nodes\":[],\"relationRevisions\":[],\"truncation\":{\"truncated\":false}}";
        } else {
            status = 404;
            response = "{}";
        }
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private record CapturedRequest(String method, URI uri, String body) {
    }
}
