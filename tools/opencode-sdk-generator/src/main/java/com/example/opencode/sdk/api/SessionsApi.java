package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import java.math.BigDecimal;
import com.example.opencode.sdk.model.ConflictError;
import com.example.opencode.sdk.model.InvalidRequestError;
import com.example.opencode.sdk.model.ServiceUnavailableError;
import com.example.opencode.sdk.model.SessionsResponse;
import com.example.opencode.sdk.model.UnauthorizedError;
import com.example.opencode.sdk.model.UnknownError1;
import com.example.opencode.sdk.model.V2SessionContext200Response;
import com.example.opencode.sdk.model.V2SessionCreate200Response;
import com.example.opencode.sdk.model.V2SessionCreateRequest;
import com.example.opencode.sdk.model.V2SessionGet200Response;
import com.example.opencode.sdk.model.V2SessionGet404Response;
import com.example.opencode.sdk.model.V2SessionList400Response;
import com.example.opencode.sdk.model.V2SessionPrompt200Response;
import com.example.opencode.sdk.model.V2SessionPromptRequest;

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

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", comments = "Generator version: 7.23.0")
public class SessionsApi {
    private ApiClient apiClient;

    public SessionsApi() {
        this(new ApiClient());
    }

    public SessionsApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Compact session
     * Compact a session conversation.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2SessionCompactRequestCreation(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling v2SessionCompact", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/compact", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Compact session
     * Compact a session conversation.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2SessionCompact(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2SessionCompactRequestCreation(sessionID).bodyToMono(localVarReturnType);
    }

    /**
     * Compact session
     * Compact a session conversation.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2SessionCompactWithHttpInfo(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2SessionCompactRequestCreation(sessionID).toEntity(localVarReturnType);
    }

    /**
     * Compact session
     * Compact a session conversation.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param sessionID The sessionID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionCompactWithResponseSpec(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        return v2SessionCompactRequestCreation(sessionID);
    }

    /**
     * Get session context
     * Retrieve the active context messages for a session (all messages after the last compaction).
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @return V2SessionContext200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2SessionContextRequestCreation(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling v2SessionContext", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<V2SessionContext200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionContext200Response>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/context", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get session context
     * Retrieve the active context messages for a session (all messages after the last compaction).
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @return V2SessionContext200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2SessionContext200Response> v2SessionContext(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<V2SessionContext200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionContext200Response>() {};
        return v2SessionContextRequestCreation(sessionID).bodyToMono(localVarReturnType);
    }

    /**
     * Get session context
     * Retrieve the active context messages for a session (all messages after the last compaction).
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @return ResponseEntity&lt;V2SessionContext200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2SessionContext200Response>> v2SessionContextWithHttpInfo(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<V2SessionContext200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionContext200Response>() {};
        return v2SessionContextRequestCreation(sessionID).toEntity(localVarReturnType);
    }

    /**
     * Get session context
     * Retrieve the active context messages for a session (all messages after the last compaction).
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionContextWithResponseSpec(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        return v2SessionContextRequestCreation(sessionID);
    }

    /**
     * Create session
     * Create a session at the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param v2SessionCreateRequest The v2SessionCreateRequest parameter
     * @return V2SessionCreate200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2SessionCreateRequestCreation(@jakarta.annotation.Nonnull V2SessionCreateRequest v2SessionCreateRequest) throws WebClientResponseException {
        Object postBody = v2SessionCreateRequest;
        // verify the required parameter 'v2SessionCreateRequest' is set
        if (v2SessionCreateRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'v2SessionCreateRequest' when calling v2SessionCreate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { 
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<V2SessionCreate200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionCreate200Response>() {};
        return apiClient.invokeAPI("/api/session", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Create session
     * Create a session at the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param v2SessionCreateRequest The v2SessionCreateRequest parameter
     * @return V2SessionCreate200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2SessionCreate200Response> v2SessionCreate(@jakarta.annotation.Nonnull V2SessionCreateRequest v2SessionCreateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<V2SessionCreate200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionCreate200Response>() {};
        return v2SessionCreateRequestCreation(v2SessionCreateRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Create session
     * Create a session at the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param v2SessionCreateRequest The v2SessionCreateRequest parameter
     * @return ResponseEntity&lt;V2SessionCreate200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2SessionCreate200Response>> v2SessionCreateWithHttpInfo(@jakarta.annotation.Nonnull V2SessionCreateRequest v2SessionCreateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<V2SessionCreate200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionCreate200Response>() {};
        return v2SessionCreateRequestCreation(v2SessionCreateRequest).toEntity(localVarReturnType);
    }

    /**
     * Create session
     * Create a session at the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param v2SessionCreateRequest The v2SessionCreateRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionCreateWithResponseSpec(@jakarta.annotation.Nonnull V2SessionCreateRequest v2SessionCreateRequest) throws WebClientResponseException {
        return v2SessionCreateRequestCreation(v2SessionCreateRequest);
    }

    /**
     * Get session
     * Retrieve a session by ID.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return V2SessionGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2SessionGetRequestCreation(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling v2SessionGet", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<V2SessionGet200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionGet200Response>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get session
     * Retrieve a session by ID.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return V2SessionGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2SessionGet200Response> v2SessionGet(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<V2SessionGet200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionGet200Response>() {};
        return v2SessionGetRequestCreation(sessionID).bodyToMono(localVarReturnType);
    }

    /**
     * Get session
     * Retrieve a session by ID.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return ResponseEntity&lt;V2SessionGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2SessionGet200Response>> v2SessionGetWithHttpInfo(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<V2SessionGet200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionGet200Response>() {};
        return v2SessionGetRequestCreation(sessionID).toEntity(localVarReturnType);
    }

    /**
     * Get session
     * Retrieve a session by ID.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionGetWithResponseSpec(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        return v2SessionGetRequestCreation(sessionID);
    }

    public class V2SessionListRequest {
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable BigDecimal limit;
        private @jakarta.annotation.Nullable String order;
        private @jakarta.annotation.Nullable String search;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String project;
        private @jakarta.annotation.Nullable String subpath;
        private @jakarta.annotation.Nullable String cursor;

        public V2SessionListRequest() {}

        public V2SessionListRequest(@jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable BigDecimal limit, @jakarta.annotation.Nullable String order, @jakarta.annotation.Nullable String search, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String project, @jakarta.annotation.Nullable String subpath, @jakarta.annotation.Nullable String cursor) {
            this.workspace = workspace;
            this.limit = limit;
            this.order = order;
            this.search = search;
            this.directory = directory;
            this.project = project;
            this.subpath = subpath;
            this.cursor = cursor;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public V2SessionListRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable BigDecimal limit() {
            return this.limit;
        }
        public V2SessionListRequest limit(@jakarta.annotation.Nullable BigDecimal limit) {
            this.limit = limit;
            return this;
        }

        public @jakarta.annotation.Nullable String order() {
            return this.order;
        }
        public V2SessionListRequest order(@jakarta.annotation.Nullable String order) {
            this.order = order;
            return this;
        }

        public @jakarta.annotation.Nullable String search() {
            return this.search;
        }
        public V2SessionListRequest search(@jakarta.annotation.Nullable String search) {
            this.search = search;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public V2SessionListRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String project() {
            return this.project;
        }
        public V2SessionListRequest project(@jakarta.annotation.Nullable String project) {
            this.project = project;
            return this;
        }

        public @jakarta.annotation.Nullable String subpath() {
            return this.subpath;
        }
        public V2SessionListRequest subpath(@jakarta.annotation.Nullable String subpath) {
            this.subpath = subpath;
            return this;
        }

        public @jakarta.annotation.Nullable String cursor() {
            return this.cursor;
        }
        public V2SessionListRequest cursor(@jakarta.annotation.Nullable String cursor) {
            this.cursor = cursor;
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
            V2SessionListRequest request = (V2SessionListRequest) o;
            return Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.limit, request.limit()) &&
                Objects.equals(this.order, request.order()) &&
                Objects.equals(this.search, request.search()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.project, request.project()) &&
                Objects.equals(this.subpath, request.subpath()) &&
                Objects.equals(this.cursor, request.cursor());
        }

        @Override
        public int hashCode() {
            return Objects.hash(workspace, limit, order, search, directory, project, subpath, cursor);
        }
    }

    /**
     * List sessions
     * Retrieve sessions in the requested order. Items keep that order across pages; use cursor.next or cursor.previous to move through the ordered list.
     * <p><b>200</b> - SessionsResponse
     * <p><b>400</b> - InvalidCursorError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2SessionList request parameters as object
     * @return SessionsResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionsResponse> v2SessionList(V2SessionListRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionList(requestParameters.workspace(), requestParameters.limit(), requestParameters.order(), requestParameters.search(), requestParameters.directory(), requestParameters.project(), requestParameters.subpath(), requestParameters.cursor());
    }

    /**
     * List sessions
     * Retrieve sessions in the requested order. Items keep that order across pages; use cursor.next or cursor.previous to move through the ordered list.
     * <p><b>200</b> - SessionsResponse
     * <p><b>400</b> - InvalidCursorError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2SessionList request parameters as object
     * @return ResponseEntity&lt;SessionsResponse&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionsResponse>> v2SessionListWithHttpInfo(V2SessionListRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionListWithHttpInfo(requestParameters.workspace(), requestParameters.limit(), requestParameters.order(), requestParameters.search(), requestParameters.directory(), requestParameters.project(), requestParameters.subpath(), requestParameters.cursor());
    }

    /**
     * List sessions
     * Retrieve sessions in the requested order. Items keep that order across pages; use cursor.next or cursor.previous to move through the ordered list.
     * <p><b>200</b> - SessionsResponse
     * <p><b>400</b> - InvalidCursorError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2SessionList request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionListWithResponseSpec(V2SessionListRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionListWithResponseSpec(requestParameters.workspace(), requestParameters.limit(), requestParameters.order(), requestParameters.search(), requestParameters.directory(), requestParameters.project(), requestParameters.subpath(), requestParameters.cursor());
    }


    /**
     * List sessions
     * Retrieve sessions in the requested order. Items keep that order across pages; use cursor.next or cursor.previous to move through the ordered list.
     * <p><b>200</b> - SessionsResponse
     * <p><b>400</b> - InvalidCursorError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param workspace The workspace parameter
     * @param limit The limit parameter
     * @param order The order parameter
     * @param search The search parameter
     * @param directory The directory parameter
     * @param project The project parameter
     * @param subpath The subpath parameter
     * @param cursor The cursor parameter
     * @return SessionsResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2SessionListRequestCreation(@jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable BigDecimal limit, @jakarta.annotation.Nullable String order, @jakarta.annotation.Nullable String search, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String project, @jakarta.annotation.Nullable String subpath, @jakarta.annotation.Nullable String cursor) throws WebClientResponseException {
        Object postBody = null;
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", workspace));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "limit", limit));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "order", order));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "search", search));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", directory));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "project", project));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "subpath", subpath));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "cursor", cursor));

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<SessionsResponse> localVarReturnType = new ParameterizedTypeReference<SessionsResponse>() {};
        return apiClient.invokeAPI("/api/session", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List sessions
     * Retrieve sessions in the requested order. Items keep that order across pages; use cursor.next or cursor.previous to move through the ordered list.
     * <p><b>200</b> - SessionsResponse
     * <p><b>400</b> - InvalidCursorError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param workspace The workspace parameter
     * @param limit The limit parameter
     * @param order The order parameter
     * @param search The search parameter
     * @param directory The directory parameter
     * @param project The project parameter
     * @param subpath The subpath parameter
     * @param cursor The cursor parameter
     * @return SessionsResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionsResponse> v2SessionList(@jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable BigDecimal limit, @jakarta.annotation.Nullable String order, @jakarta.annotation.Nullable String search, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String project, @jakarta.annotation.Nullable String subpath, @jakarta.annotation.Nullable String cursor) throws WebClientResponseException {
        ParameterizedTypeReference<SessionsResponse> localVarReturnType = new ParameterizedTypeReference<SessionsResponse>() {};
        return v2SessionListRequestCreation(workspace, limit, order, search, directory, project, subpath, cursor).bodyToMono(localVarReturnType);
    }

    /**
     * List sessions
     * Retrieve sessions in the requested order. Items keep that order across pages; use cursor.next or cursor.previous to move through the ordered list.
     * <p><b>200</b> - SessionsResponse
     * <p><b>400</b> - InvalidCursorError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param workspace The workspace parameter
     * @param limit The limit parameter
     * @param order The order parameter
     * @param search The search parameter
     * @param directory The directory parameter
     * @param project The project parameter
     * @param subpath The subpath parameter
     * @param cursor The cursor parameter
     * @return ResponseEntity&lt;SessionsResponse&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionsResponse>> v2SessionListWithHttpInfo(@jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable BigDecimal limit, @jakarta.annotation.Nullable String order, @jakarta.annotation.Nullable String search, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String project, @jakarta.annotation.Nullable String subpath, @jakarta.annotation.Nullable String cursor) throws WebClientResponseException {
        ParameterizedTypeReference<SessionsResponse> localVarReturnType = new ParameterizedTypeReference<SessionsResponse>() {};
        return v2SessionListRequestCreation(workspace, limit, order, search, directory, project, subpath, cursor).toEntity(localVarReturnType);
    }

    /**
     * List sessions
     * Retrieve sessions in the requested order. Items keep that order across pages; use cursor.next or cursor.previous to move through the ordered list.
     * <p><b>200</b> - SessionsResponse
     * <p><b>400</b> - InvalidCursorError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param workspace The workspace parameter
     * @param limit The limit parameter
     * @param order The order parameter
     * @param search The search parameter
     * @param directory The directory parameter
     * @param project The project parameter
     * @param subpath The subpath parameter
     * @param cursor The cursor parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionListWithResponseSpec(@jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable BigDecimal limit, @jakarta.annotation.Nullable String order, @jakarta.annotation.Nullable String search, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String project, @jakarta.annotation.Nullable String subpath, @jakarta.annotation.Nullable String cursor) throws WebClientResponseException {
        return v2SessionListRequestCreation(workspace, limit, order, search, directory, project, subpath, cursor);
    }

    public class V2SessionPromptRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull V2SessionPromptRequest v2SessionPromptRequest;

        public V2SessionPromptRequest() {}

        public V2SessionPromptRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull V2SessionPromptRequest v2SessionPromptRequest) {
            this.sessionID = sessionID;
            this.v2SessionPromptRequest = v2SessionPromptRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public V2SessionPromptRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull V2SessionPromptRequest v2SessionPromptRequest() {
            return this.v2SessionPromptRequest;
        }
        public V2SessionPromptRequest v2SessionPromptRequest(@jakarta.annotation.Nonnull V2SessionPromptRequest v2SessionPromptRequest) {
            this.v2SessionPromptRequest = v2SessionPromptRequest;
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
            V2SessionPromptRequest request = (V2SessionPromptRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.v2SessionPromptRequest, request.v2SessionPromptRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, v2SessionPromptRequest);
        }
    }

    /**
     * Send message
     * Durably admit one session input and schedule agent-loop execution unless resume is false.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param requestParameters The v2SessionPrompt request parameters as object
     * @return V2SessionPrompt200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2SessionPrompt200Response> v2SessionPrompt(V2SessionPromptRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionPrompt(requestParameters.sessionID(), requestParameters.v2SessionPromptRequest());
    }

    /**
     * Send message
     * Durably admit one session input and schedule agent-loop execution unless resume is false.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param requestParameters The v2SessionPrompt request parameters as object
     * @return ResponseEntity&lt;V2SessionPrompt200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2SessionPrompt200Response>> v2SessionPromptWithHttpInfo(V2SessionPromptRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionPromptWithHttpInfo(requestParameters.sessionID(), requestParameters.v2SessionPromptRequest());
    }

    /**
     * Send message
     * Durably admit one session input and schedule agent-loop execution unless resume is false.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param requestParameters The v2SessionPrompt request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionPromptWithResponseSpec(V2SessionPromptRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionPromptWithResponseSpec(requestParameters.sessionID(), requestParameters.v2SessionPromptRequest());
    }


    /**
     * Send message
     * Durably admit one session input and schedule agent-loop execution unless resume is false.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param sessionID The sessionID parameter
     * @param v2SessionPromptRequest The v2SessionPromptRequest parameter
     * @return V2SessionPrompt200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2SessionPromptRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull V2SessionPromptRequest v2SessionPromptRequest) throws WebClientResponseException {
        Object postBody = v2SessionPromptRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling v2SessionPrompt", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'v2SessionPromptRequest' is set
        if (v2SessionPromptRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'v2SessionPromptRequest' when calling v2SessionPrompt", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { 
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<V2SessionPrompt200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionPrompt200Response>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/prompt", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Send message
     * Durably admit one session input and schedule agent-loop execution unless resume is false.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param sessionID The sessionID parameter
     * @param v2SessionPromptRequest The v2SessionPromptRequest parameter
     * @return V2SessionPrompt200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2SessionPrompt200Response> v2SessionPrompt(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull V2SessionPromptRequest v2SessionPromptRequest) throws WebClientResponseException {
        ParameterizedTypeReference<V2SessionPrompt200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionPrompt200Response>() {};
        return v2SessionPromptRequestCreation(sessionID, v2SessionPromptRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Send message
     * Durably admit one session input and schedule agent-loop execution unless resume is false.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param sessionID The sessionID parameter
     * @param v2SessionPromptRequest The v2SessionPromptRequest parameter
     * @return ResponseEntity&lt;V2SessionPrompt200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2SessionPrompt200Response>> v2SessionPromptWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull V2SessionPromptRequest v2SessionPromptRequest) throws WebClientResponseException {
        ParameterizedTypeReference<V2SessionPrompt200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionPrompt200Response>() {};
        return v2SessionPromptRequestCreation(sessionID, v2SessionPromptRequest).toEntity(localVarReturnType);
    }

    /**
     * Send message
     * Durably admit one session input and schedule agent-loop execution unless resume is false.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param sessionID The sessionID parameter
     * @param v2SessionPromptRequest The v2SessionPromptRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionPromptWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull V2SessionPromptRequest v2SessionPromptRequest) throws WebClientResponseException {
        return v2SessionPromptRequestCreation(sessionID, v2SessionPromptRequest);
    }

    /**
     * Wait for session
     * Wait for a session agent loop to become idle.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2SessionWaitRequestCreation(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling v2SessionWait", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/wait", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Wait for session
     * Wait for a session agent loop to become idle.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2SessionWait(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2SessionWaitRequestCreation(sessionID).bodyToMono(localVarReturnType);
    }

    /**
     * Wait for session
     * Wait for a session agent loop to become idle.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2SessionWaitWithHttpInfo(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2SessionWaitRequestCreation(sessionID).toEntity(localVarReturnType);
    }

    /**
     * Wait for session
     * Wait for a session agent loop to become idle.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param sessionID The sessionID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionWaitWithResponseSpec(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        return v2SessionWaitRequestCreation(sessionID);
    }
}
