package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.AuthSet400Response;
import com.example.opencode.sdk.model.BadRequestError;
import com.example.opencode.sdk.model.SyncHistoryList200ResponseInner;
import com.example.opencode.sdk.model.SyncReplay200Response;
import com.example.opencode.sdk.model.SyncReplayRequest;
import com.example.opencode.sdk.model.SyncSteal200Response;
import com.example.opencode.sdk.model.SyncStealRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", comments = "Generator version: 7.24.0")
public class SyncApi {
    private ApiClient apiClient;

    public SyncApi() {
        this(new ApiClient());
    }

    public SyncApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public class SyncHistoryListRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable Map<String, Integer> requestBody;

        public SyncHistoryListRequest() {}

        public SyncHistoryListRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Map<String, Integer> requestBody) {
            this.directory = directory;
            this.workspace = workspace;
            this.requestBody = requestBody;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public SyncHistoryListRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public SyncHistoryListRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable Map<String, Integer> requestBody() {
            return this.requestBody;
        }
        public SyncHistoryListRequest requestBody(@jakarta.annotation.Nullable Map<String, Integer> requestBody) {
            this.requestBody = requestBody;
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SyncHistoryListRequest request = (SyncHistoryListRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.requestBody, request.requestBody());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace, requestBody);
        }
    }

    /**
     * List sync events
     * List sync events for all aggregates. Keys are aggregate IDs the client already knows about, values are the last known sequence ID. Events with seq &gt; value are returned for those aggregates. Aggregates not listed in the input get their full history.
     * <p><b>200</b> - Sync events
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The syncHistoryList request parameters as object
     * @return List&lt;SyncHistoryList200ResponseInner&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<SyncHistoryList200ResponseInner> syncHistoryList(SyncHistoryListRequest requestParameters) throws WebClientResponseException {
        return this.syncHistoryList(requestParameters.directory(), requestParameters.workspace(), requestParameters.requestBody());
    }

    /**
     * List sync events
     * List sync events for all aggregates. Keys are aggregate IDs the client already knows about, values are the last known sequence ID. Events with seq &gt; value are returned for those aggregates. Aggregates not listed in the input get their full history.
     * <p><b>200</b> - Sync events
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The syncHistoryList request parameters as object
     * @return ResponseEntity&lt;List&lt;SyncHistoryList200ResponseInner&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<SyncHistoryList200ResponseInner>>> syncHistoryListWithHttpInfo(SyncHistoryListRequest requestParameters) throws WebClientResponseException {
        return this.syncHistoryListWithHttpInfo(requestParameters.directory(), requestParameters.workspace(), requestParameters.requestBody());
    }

    /**
     * List sync events
     * List sync events for all aggregates. Keys are aggregate IDs the client already knows about, values are the last known sequence ID. Events with seq &gt; value are returned for those aggregates. Aggregates not listed in the input get their full history.
     * <p><b>200</b> - Sync events
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The syncHistoryList request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec syncHistoryListWithResponseSpec(SyncHistoryListRequest requestParameters) throws WebClientResponseException {
        return this.syncHistoryListWithResponseSpec(requestParameters.directory(), requestParameters.workspace(), requestParameters.requestBody());
    }


    /**
     * List sync events
     * List sync events for all aggregates. Keys are aggregate IDs the client already knows about, values are the last known sequence ID. Events with seq &gt; value are returned for those aggregates. Aggregates not listed in the input get their full history.
     * <p><b>200</b> - Sync events
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param requestBody The requestBody parameter
     * @return List&lt;SyncHistoryList200ResponseInner&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec syncHistoryListRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Map<String, Integer> requestBody) throws WebClientResponseException {
        Object postBody = requestBody;
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", directory));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", workspace));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<SyncHistoryList200ResponseInner> localVarReturnType = new ParameterizedTypeReference<SyncHistoryList200ResponseInner>() {};
        return apiClient.invokeAPI("/sync/history", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List sync events
     * List sync events for all aggregates. Keys are aggregate IDs the client already knows about, values are the last known sequence ID. Events with seq &gt; value are returned for those aggregates. Aggregates not listed in the input get their full history.
     * <p><b>200</b> - Sync events
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param requestBody The requestBody parameter
     * @return List&lt;SyncHistoryList200ResponseInner&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<SyncHistoryList200ResponseInner> syncHistoryList(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Map<String, Integer> requestBody) throws WebClientResponseException {
        ParameterizedTypeReference<SyncHistoryList200ResponseInner> localVarReturnType = new ParameterizedTypeReference<SyncHistoryList200ResponseInner>() {};
        return syncHistoryListRequestCreation(directory, workspace, requestBody).bodyToFlux(localVarReturnType);
    }

    /**
     * List sync events
     * List sync events for all aggregates. Keys are aggregate IDs the client already knows about, values are the last known sequence ID. Events with seq &gt; value are returned for those aggregates. Aggregates not listed in the input get their full history.
     * <p><b>200</b> - Sync events
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param requestBody The requestBody parameter
     * @return ResponseEntity&lt;List&lt;SyncHistoryList200ResponseInner&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<SyncHistoryList200ResponseInner>>> syncHistoryListWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Map<String, Integer> requestBody) throws WebClientResponseException {
        ParameterizedTypeReference<SyncHistoryList200ResponseInner> localVarReturnType = new ParameterizedTypeReference<SyncHistoryList200ResponseInner>() {};
        return syncHistoryListRequestCreation(directory, workspace, requestBody).toEntityList(localVarReturnType);
    }

    /**
     * List sync events
     * List sync events for all aggregates. Keys are aggregate IDs the client already knows about, values are the last known sequence ID. Events with seq &gt; value are returned for those aggregates. Aggregates not listed in the input get their full history.
     * <p><b>200</b> - Sync events
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param requestBody The requestBody parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec syncHistoryListWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Map<String, Integer> requestBody) throws WebClientResponseException {
        return syncHistoryListRequestCreation(directory, workspace, requestBody);
    }

    public class SyncReplayRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable SyncReplayRequest syncReplayRequest;

        public SyncReplayRequest() {}

        public SyncReplayRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SyncReplayRequest syncReplayRequest) {
            this.directory = directory;
            this.workspace = workspace;
            this.syncReplayRequest = syncReplayRequest;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public SyncReplayRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public SyncReplayRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable SyncReplayRequest syncReplayRequest() {
            return this.syncReplayRequest;
        }
        public SyncReplayRequest syncReplayRequest(@jakarta.annotation.Nullable SyncReplayRequest syncReplayRequest) {
            this.syncReplayRequest = syncReplayRequest;
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SyncReplayRequest request = (SyncReplayRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.syncReplayRequest, request.syncReplayRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace, syncReplayRequest);
        }
    }

    /**
     * Replay sync events
     * Validate and replay a complete sync event history.
     * <p><b>200</b> - Replayed sync events
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The syncReplay request parameters as object
     * @return SyncReplay200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SyncReplay200Response> syncReplay(SyncReplayRequest requestParameters) throws WebClientResponseException {
        return this.syncReplay(requestParameters.directory(), requestParameters.workspace(), requestParameters.syncReplayRequest());
    }

    /**
     * Replay sync events
     * Validate and replay a complete sync event history.
     * <p><b>200</b> - Replayed sync events
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The syncReplay request parameters as object
     * @return ResponseEntity&lt;SyncReplay200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SyncReplay200Response>> syncReplayWithHttpInfo(SyncReplayRequest requestParameters) throws WebClientResponseException {
        return this.syncReplayWithHttpInfo(requestParameters.directory(), requestParameters.workspace(), requestParameters.syncReplayRequest());
    }

    /**
     * Replay sync events
     * Validate and replay a complete sync event history.
     * <p><b>200</b> - Replayed sync events
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The syncReplay request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec syncReplayWithResponseSpec(SyncReplayRequest requestParameters) throws WebClientResponseException {
        return this.syncReplayWithResponseSpec(requestParameters.directory(), requestParameters.workspace(), requestParameters.syncReplayRequest());
    }


    /**
     * Replay sync events
     * Validate and replay a complete sync event history.
     * <p><b>200</b> - Replayed sync events
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param syncReplayRequest The syncReplayRequest parameter
     * @return SyncReplay200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec syncReplayRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SyncReplayRequest syncReplayRequest) throws WebClientResponseException {
        Object postBody = syncReplayRequest;
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", directory));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", workspace));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<SyncReplay200Response> localVarReturnType = new ParameterizedTypeReference<SyncReplay200Response>() {};
        return apiClient.invokeAPI("/sync/replay", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Replay sync events
     * Validate and replay a complete sync event history.
     * <p><b>200</b> - Replayed sync events
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param syncReplayRequest The syncReplayRequest parameter
     * @return SyncReplay200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SyncReplay200Response> syncReplay(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SyncReplayRequest syncReplayRequest) throws WebClientResponseException {
        ParameterizedTypeReference<SyncReplay200Response> localVarReturnType = new ParameterizedTypeReference<SyncReplay200Response>() {};
        return syncReplayRequestCreation(directory, workspace, syncReplayRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Replay sync events
     * Validate and replay a complete sync event history.
     * <p><b>200</b> - Replayed sync events
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param syncReplayRequest The syncReplayRequest parameter
     * @return ResponseEntity&lt;SyncReplay200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SyncReplay200Response>> syncReplayWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SyncReplayRequest syncReplayRequest) throws WebClientResponseException {
        ParameterizedTypeReference<SyncReplay200Response> localVarReturnType = new ParameterizedTypeReference<SyncReplay200Response>() {};
        return syncReplayRequestCreation(directory, workspace, syncReplayRequest).toEntity(localVarReturnType);
    }

    /**
     * Replay sync events
     * Validate and replay a complete sync event history.
     * <p><b>200</b> - Replayed sync events
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param syncReplayRequest The syncReplayRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec syncReplayWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SyncReplayRequest syncReplayRequest) throws WebClientResponseException {
        return syncReplayRequestCreation(directory, workspace, syncReplayRequest);
    }

    public class SyncStartRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public SyncStartRequest() {}

        public SyncStartRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public SyncStartRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public SyncStartRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SyncStartRequest request = (SyncStartRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * Start workspace sync
     * Start sync loops for workspaces in the current project that have active sessions.
     * <p><b>200</b> - Workspace sync started
     * <p><b>400</b> - Bad request
     * @param requestParameters The syncStart request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> syncStart(SyncStartRequest requestParameters) throws WebClientResponseException {
        return this.syncStart(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Start workspace sync
     * Start sync loops for workspaces in the current project that have active sessions.
     * <p><b>200</b> - Workspace sync started
     * <p><b>400</b> - Bad request
     * @param requestParameters The syncStart request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> syncStartWithHttpInfo(SyncStartRequest requestParameters) throws WebClientResponseException {
        return this.syncStartWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Start workspace sync
     * Start sync loops for workspaces in the current project that have active sessions.
     * <p><b>200</b> - Workspace sync started
     * <p><b>400</b> - Bad request
     * @param requestParameters The syncStart request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec syncStartWithResponseSpec(SyncStartRequest requestParameters) throws WebClientResponseException {
        return this.syncStartWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Start workspace sync
     * Start sync loops for workspaces in the current project that have active sessions.
     * <p><b>200</b> - Workspace sync started
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec syncStartRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", directory));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", workspace));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return apiClient.invokeAPI("/sync/start", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Start workspace sync
     * Start sync loops for workspaces in the current project that have active sessions.
     * <p><b>200</b> - Workspace sync started
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> syncStart(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return syncStartRequestCreation(directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Start workspace sync
     * Start sync loops for workspaces in the current project that have active sessions.
     * <p><b>200</b> - Workspace sync started
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> syncStartWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return syncStartRequestCreation(directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Start workspace sync
     * Start sync loops for workspaces in the current project that have active sessions.
     * <p><b>200</b> - Workspace sync started
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec syncStartWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return syncStartRequestCreation(directory, workspace);
    }

    public class SyncStealRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable SyncStealRequest syncStealRequest;

        public SyncStealRequest() {}

        public SyncStealRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SyncStealRequest syncStealRequest) {
            this.directory = directory;
            this.workspace = workspace;
            this.syncStealRequest = syncStealRequest;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public SyncStealRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public SyncStealRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable SyncStealRequest syncStealRequest() {
            return this.syncStealRequest;
        }
        public SyncStealRequest syncStealRequest(@jakarta.annotation.Nullable SyncStealRequest syncStealRequest) {
            this.syncStealRequest = syncStealRequest;
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SyncStealRequest request = (SyncStealRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.syncStealRequest, request.syncStealRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace, syncStealRequest);
        }
    }

    /**
     * Steal session into workspace
     * Update a session to belong to the current workspace through the sync event system.
     * <p><b>200</b> - Session stolen into workspace
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The syncSteal request parameters as object
     * @return SyncSteal200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SyncSteal200Response> syncSteal(SyncStealRequest requestParameters) throws WebClientResponseException {
        return this.syncSteal(requestParameters.directory(), requestParameters.workspace(), requestParameters.syncStealRequest());
    }

    /**
     * Steal session into workspace
     * Update a session to belong to the current workspace through the sync event system.
     * <p><b>200</b> - Session stolen into workspace
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The syncSteal request parameters as object
     * @return ResponseEntity&lt;SyncSteal200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SyncSteal200Response>> syncStealWithHttpInfo(SyncStealRequest requestParameters) throws WebClientResponseException {
        return this.syncStealWithHttpInfo(requestParameters.directory(), requestParameters.workspace(), requestParameters.syncStealRequest());
    }

    /**
     * Steal session into workspace
     * Update a session to belong to the current workspace through the sync event system.
     * <p><b>200</b> - Session stolen into workspace
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The syncSteal request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec syncStealWithResponseSpec(SyncStealRequest requestParameters) throws WebClientResponseException {
        return this.syncStealWithResponseSpec(requestParameters.directory(), requestParameters.workspace(), requestParameters.syncStealRequest());
    }


    /**
     * Steal session into workspace
     * Update a session to belong to the current workspace through the sync event system.
     * <p><b>200</b> - Session stolen into workspace
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param syncStealRequest The syncStealRequest parameter
     * @return SyncSteal200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec syncStealRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SyncStealRequest syncStealRequest) throws WebClientResponseException {
        Object postBody = syncStealRequest;
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", directory));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", workspace));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<SyncSteal200Response> localVarReturnType = new ParameterizedTypeReference<SyncSteal200Response>() {};
        return apiClient.invokeAPI("/sync/steal", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Steal session into workspace
     * Update a session to belong to the current workspace through the sync event system.
     * <p><b>200</b> - Session stolen into workspace
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param syncStealRequest The syncStealRequest parameter
     * @return SyncSteal200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SyncSteal200Response> syncSteal(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SyncStealRequest syncStealRequest) throws WebClientResponseException {
        ParameterizedTypeReference<SyncSteal200Response> localVarReturnType = new ParameterizedTypeReference<SyncSteal200Response>() {};
        return syncStealRequestCreation(directory, workspace, syncStealRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Steal session into workspace
     * Update a session to belong to the current workspace through the sync event system.
     * <p><b>200</b> - Session stolen into workspace
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param syncStealRequest The syncStealRequest parameter
     * @return ResponseEntity&lt;SyncSteal200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SyncSteal200Response>> syncStealWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SyncStealRequest syncStealRequest) throws WebClientResponseException {
        ParameterizedTypeReference<SyncSteal200Response> localVarReturnType = new ParameterizedTypeReference<SyncSteal200Response>() {};
        return syncStealRequestCreation(directory, workspace, syncStealRequest).toEntity(localVarReturnType);
    }

    /**
     * Steal session into workspace
     * Update a session to belong to the current workspace through the sync event system.
     * <p><b>200</b> - Session stolen into workspace
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param syncStealRequest The syncStealRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec syncStealWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SyncStealRequest syncStealRequest) throws WebClientResponseException {
        return syncStealRequestCreation(directory, workspace, syncStealRequest);
    }
}
