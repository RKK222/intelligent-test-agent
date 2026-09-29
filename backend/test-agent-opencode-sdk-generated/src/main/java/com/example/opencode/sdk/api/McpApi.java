package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.AgentListLocationParameter;
import com.example.opencode.sdk.model.ExperimentalMcpAddRequest;
import com.example.opencode.sdk.model.InvalidRequestErrorEncoded;
import com.example.opencode.sdk.model.McpList200Response;
import com.example.opencode.sdk.model.McpResourceCatalog200Response;
import com.example.opencode.sdk.model.McpServerNotFoundErrorEncoded;
import com.example.opencode.sdk.model.UnauthorizedErrorEncoded;

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
public class McpApi {
    private ApiClient apiClient;

    public McpApi() {
        this(new ApiClient());
    }

    public McpApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public class ExperimentalMcpAddRequest {
        private @jakarta.annotation.Nullable String server;
        private @jakarta.annotation.Nonnull ExperimentalMcpAddRequest experimentalMcpAddRequest;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;

        public ExperimentalMcpAddRequest() {}

        public ExperimentalMcpAddRequest(@jakarta.annotation.Nullable String server, @jakarta.annotation.Nonnull ExperimentalMcpAddRequest experimentalMcpAddRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.server = server;
            this.experimentalMcpAddRequest = experimentalMcpAddRequest;
            this.location = location;
        }

        public @jakarta.annotation.Nullable String server() {
            return this.server;
        }
        public ExperimentalMcpAddRequest server(@jakarta.annotation.Nullable String server) {
            this.server = server;
            return this;
        }

        public @jakarta.annotation.Nonnull ExperimentalMcpAddRequest experimentalMcpAddRequest() {
            return this.experimentalMcpAddRequest;
        }
        public ExperimentalMcpAddRequest experimentalMcpAddRequest(@jakarta.annotation.Nonnull ExperimentalMcpAddRequest experimentalMcpAddRequest) {
            this.experimentalMcpAddRequest = experimentalMcpAddRequest;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public ExperimentalMcpAddRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.location = location;
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
            ExperimentalMcpAddRequest request = (ExperimentalMcpAddRequest) o;
            return Objects.equals(this.server, request.server()) &&
                Objects.equals(this.experimentalMcpAddRequest, request.experimentalMcpAddRequest()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(server, experimentalMcpAddRequest, location);
        }
    }

    /**
     * Add MCP server
     * Add an MCP server at runtime or replace an existing one, connecting it immediately.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The experimentalMcpAdd request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> experimentalMcpAdd(ExperimentalMcpAddRequest requestParameters) throws WebClientResponseException {
        return this.experimentalMcpAdd(requestParameters.server(), requestParameters.experimentalMcpAddRequest(), requestParameters.location());
    }

    /**
     * Add MCP server
     * Add an MCP server at runtime or replace an existing one, connecting it immediately.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The experimentalMcpAdd request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> experimentalMcpAddWithHttpInfo(ExperimentalMcpAddRequest requestParameters) throws WebClientResponseException {
        return this.experimentalMcpAddWithHttpInfo(requestParameters.server(), requestParameters.experimentalMcpAddRequest(), requestParameters.location());
    }

    /**
     * Add MCP server
     * Add an MCP server at runtime or replace an existing one, connecting it immediately.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The experimentalMcpAdd request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalMcpAddWithResponseSpec(ExperimentalMcpAddRequest requestParameters) throws WebClientResponseException {
        return this.experimentalMcpAddWithResponseSpec(requestParameters.server(), requestParameters.experimentalMcpAddRequest(), requestParameters.location());
    }


    /**
     * Add MCP server
     * Add an MCP server at runtime or replace an existing one, connecting it immediately.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param server The server parameter
     * @param experimentalMcpAddRequest The experimentalMcpAddRequest parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalMcpAddRequestCreation(@jakarta.annotation.Nullable String server, @jakarta.annotation.Nonnull ExperimentalMcpAddRequest experimentalMcpAddRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = experimentalMcpAddRequest;
        // verify the required parameter 'server' is set
        if (server == null) {
            throw new WebClientResponseException("Missing the required parameter 'server' when calling experimentalMcpAdd", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'experimentalMcpAddRequest' is set
        if (experimentalMcpAddRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'experimentalMcpAddRequest' when calling experimentalMcpAdd", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("server", server);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/experimental/mcp/{server}", HttpMethod.PUT, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Add MCP server
     * Add an MCP server at runtime or replace an existing one, connecting it immediately.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param server The server parameter
     * @param experimentalMcpAddRequest The experimentalMcpAddRequest parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> experimentalMcpAdd(@jakarta.annotation.Nullable String server, @jakarta.annotation.Nonnull ExperimentalMcpAddRequest experimentalMcpAddRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return experimentalMcpAddRequestCreation(server, experimentalMcpAddRequest, location).bodyToMono(localVarReturnType);
    }

    /**
     * Add MCP server
     * Add an MCP server at runtime or replace an existing one, connecting it immediately.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param server The server parameter
     * @param experimentalMcpAddRequest The experimentalMcpAddRequest parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> experimentalMcpAddWithHttpInfo(@jakarta.annotation.Nullable String server, @jakarta.annotation.Nonnull ExperimentalMcpAddRequest experimentalMcpAddRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return experimentalMcpAddRequestCreation(server, experimentalMcpAddRequest, location).toEntity(localVarReturnType);
    }

    /**
     * Add MCP server
     * Add an MCP server at runtime or replace an existing one, connecting it immediately.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param server The server parameter
     * @param experimentalMcpAddRequest The experimentalMcpAddRequest parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalMcpAddWithResponseSpec(@jakarta.annotation.Nullable String server, @jakarta.annotation.Nonnull ExperimentalMcpAddRequest experimentalMcpAddRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return experimentalMcpAddRequestCreation(server, experimentalMcpAddRequest, location);
    }

    public class ExperimentalMcpConnectRequest {
        private @jakarta.annotation.Nonnull String server;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;

        public ExperimentalMcpConnectRequest() {}

        public ExperimentalMcpConnectRequest(@jakarta.annotation.Nonnull String server, @jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.server = server;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String server() {
            return this.server;
        }
        public ExperimentalMcpConnectRequest server(@jakarta.annotation.Nonnull String server) {
            this.server = server;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public ExperimentalMcpConnectRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.location = location;
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
            ExperimentalMcpConnectRequest request = (ExperimentalMcpConnectRequest) o;
            return Objects.equals(this.server, request.server()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(server, location);
        }
    }

    /**
     * Connect MCP server
     * Connect an MCP server at runtime, overriding a disabled configuration until restart.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - McpServerNotFoundError
     * @param requestParameters The experimentalMcpConnect request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> experimentalMcpConnect(ExperimentalMcpConnectRequest requestParameters) throws WebClientResponseException {
        return this.experimentalMcpConnect(requestParameters.server(), requestParameters.location());
    }

    /**
     * Connect MCP server
     * Connect an MCP server at runtime, overriding a disabled configuration until restart.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - McpServerNotFoundError
     * @param requestParameters The experimentalMcpConnect request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> experimentalMcpConnectWithHttpInfo(ExperimentalMcpConnectRequest requestParameters) throws WebClientResponseException {
        return this.experimentalMcpConnectWithHttpInfo(requestParameters.server(), requestParameters.location());
    }

    /**
     * Connect MCP server
     * Connect an MCP server at runtime, overriding a disabled configuration until restart.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - McpServerNotFoundError
     * @param requestParameters The experimentalMcpConnect request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalMcpConnectWithResponseSpec(ExperimentalMcpConnectRequest requestParameters) throws WebClientResponseException {
        return this.experimentalMcpConnectWithResponseSpec(requestParameters.server(), requestParameters.location());
    }


    /**
     * Connect MCP server
     * Connect an MCP server at runtime, overriding a disabled configuration until restart.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - McpServerNotFoundError
     * @param server The server parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalMcpConnectRequestCreation(@jakarta.annotation.Nonnull String server, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'server' is set
        if (server == null) {
            throw new WebClientResponseException("Missing the required parameter 'server' when calling experimentalMcpConnect", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("server", server);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/experimental/mcp/{server}/connect", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Connect MCP server
     * Connect an MCP server at runtime, overriding a disabled configuration until restart.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - McpServerNotFoundError
     * @param server The server parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> experimentalMcpConnect(@jakarta.annotation.Nonnull String server, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return experimentalMcpConnectRequestCreation(server, location).bodyToMono(localVarReturnType);
    }

    /**
     * Connect MCP server
     * Connect an MCP server at runtime, overriding a disabled configuration until restart.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - McpServerNotFoundError
     * @param server The server parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> experimentalMcpConnectWithHttpInfo(@jakarta.annotation.Nonnull String server, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return experimentalMcpConnectRequestCreation(server, location).toEntity(localVarReturnType);
    }

    /**
     * Connect MCP server
     * Connect an MCP server at runtime, overriding a disabled configuration until restart.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - McpServerNotFoundError
     * @param server The server parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalMcpConnectWithResponseSpec(@jakarta.annotation.Nonnull String server, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return experimentalMcpConnectRequestCreation(server, location);
    }

    public class ExperimentalMcpDisconnectRequest {
        private @jakarta.annotation.Nonnull String server;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;

        public ExperimentalMcpDisconnectRequest() {}

        public ExperimentalMcpDisconnectRequest(@jakarta.annotation.Nonnull String server, @jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.server = server;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String server() {
            return this.server;
        }
        public ExperimentalMcpDisconnectRequest server(@jakarta.annotation.Nonnull String server) {
            this.server = server;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public ExperimentalMcpDisconnectRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.location = location;
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
            ExperimentalMcpDisconnectRequest request = (ExperimentalMcpDisconnectRequest) o;
            return Objects.equals(this.server, request.server()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(server, location);
        }
    }

    /**
     * Disconnect MCP server
     * Disconnect an MCP server at runtime, removing its tools until reconnected.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - McpServerNotFoundError
     * @param requestParameters The experimentalMcpDisconnect request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> experimentalMcpDisconnect(ExperimentalMcpDisconnectRequest requestParameters) throws WebClientResponseException {
        return this.experimentalMcpDisconnect(requestParameters.server(), requestParameters.location());
    }

    /**
     * Disconnect MCP server
     * Disconnect an MCP server at runtime, removing its tools until reconnected.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - McpServerNotFoundError
     * @param requestParameters The experimentalMcpDisconnect request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> experimentalMcpDisconnectWithHttpInfo(ExperimentalMcpDisconnectRequest requestParameters) throws WebClientResponseException {
        return this.experimentalMcpDisconnectWithHttpInfo(requestParameters.server(), requestParameters.location());
    }

    /**
     * Disconnect MCP server
     * Disconnect an MCP server at runtime, removing its tools until reconnected.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - McpServerNotFoundError
     * @param requestParameters The experimentalMcpDisconnect request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalMcpDisconnectWithResponseSpec(ExperimentalMcpDisconnectRequest requestParameters) throws WebClientResponseException {
        return this.experimentalMcpDisconnectWithResponseSpec(requestParameters.server(), requestParameters.location());
    }


    /**
     * Disconnect MCP server
     * Disconnect an MCP server at runtime, removing its tools until reconnected.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - McpServerNotFoundError
     * @param server The server parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalMcpDisconnectRequestCreation(@jakarta.annotation.Nonnull String server, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'server' is set
        if (server == null) {
            throw new WebClientResponseException("Missing the required parameter 'server' when calling experimentalMcpDisconnect", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("server", server);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/experimental/mcp/{server}/disconnect", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Disconnect MCP server
     * Disconnect an MCP server at runtime, removing its tools until reconnected.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - McpServerNotFoundError
     * @param server The server parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> experimentalMcpDisconnect(@jakarta.annotation.Nonnull String server, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return experimentalMcpDisconnectRequestCreation(server, location).bodyToMono(localVarReturnType);
    }

    /**
     * Disconnect MCP server
     * Disconnect an MCP server at runtime, removing its tools until reconnected.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - McpServerNotFoundError
     * @param server The server parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> experimentalMcpDisconnectWithHttpInfo(@jakarta.annotation.Nonnull String server, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return experimentalMcpDisconnectRequestCreation(server, location).toEntity(localVarReturnType);
    }

    /**
     * Disconnect MCP server
     * Disconnect an MCP server at runtime, removing its tools until reconnected.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - McpServerNotFoundError
     * @param server The server parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalMcpDisconnectWithResponseSpec(@jakarta.annotation.Nonnull String server, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return experimentalMcpDisconnectRequestCreation(server, location);
    }

    public class ExperimentalMcpRemoveRequest {
        private @jakarta.annotation.Nonnull String server;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;

        public ExperimentalMcpRemoveRequest() {}

        public ExperimentalMcpRemoveRequest(@jakarta.annotation.Nonnull String server, @jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.server = server;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String server() {
            return this.server;
        }
        public ExperimentalMcpRemoveRequest server(@jakarta.annotation.Nonnull String server) {
            this.server = server;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public ExperimentalMcpRemoveRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.location = location;
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
            ExperimentalMcpRemoveRequest request = (ExperimentalMcpRemoveRequest) o;
            return Objects.equals(this.server, request.server()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(server, location);
        }
    }

    /**
     * Remove MCP server
     * Stop an MCP server and remove it from the runtime set until restart.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - McpServerNotFoundError
     * @param requestParameters The experimentalMcpRemove request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> experimentalMcpRemove(ExperimentalMcpRemoveRequest requestParameters) throws WebClientResponseException {
        return this.experimentalMcpRemove(requestParameters.server(), requestParameters.location());
    }

    /**
     * Remove MCP server
     * Stop an MCP server and remove it from the runtime set until restart.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - McpServerNotFoundError
     * @param requestParameters The experimentalMcpRemove request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> experimentalMcpRemoveWithHttpInfo(ExperimentalMcpRemoveRequest requestParameters) throws WebClientResponseException {
        return this.experimentalMcpRemoveWithHttpInfo(requestParameters.server(), requestParameters.location());
    }

    /**
     * Remove MCP server
     * Stop an MCP server and remove it from the runtime set until restart.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - McpServerNotFoundError
     * @param requestParameters The experimentalMcpRemove request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalMcpRemoveWithResponseSpec(ExperimentalMcpRemoveRequest requestParameters) throws WebClientResponseException {
        return this.experimentalMcpRemoveWithResponseSpec(requestParameters.server(), requestParameters.location());
    }


    /**
     * Remove MCP server
     * Stop an MCP server and remove it from the runtime set until restart.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - McpServerNotFoundError
     * @param server The server parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalMcpRemoveRequestCreation(@jakarta.annotation.Nonnull String server, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'server' is set
        if (server == null) {
            throw new WebClientResponseException("Missing the required parameter 'server' when calling experimentalMcpRemove", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("server", server);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/experimental/mcp/{server}", HttpMethod.DELETE, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Remove MCP server
     * Stop an MCP server and remove it from the runtime set until restart.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - McpServerNotFoundError
     * @param server The server parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> experimentalMcpRemove(@jakarta.annotation.Nonnull String server, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return experimentalMcpRemoveRequestCreation(server, location).bodyToMono(localVarReturnType);
    }

    /**
     * Remove MCP server
     * Stop an MCP server and remove it from the runtime set until restart.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - McpServerNotFoundError
     * @param server The server parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> experimentalMcpRemoveWithHttpInfo(@jakarta.annotation.Nonnull String server, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return experimentalMcpRemoveRequestCreation(server, location).toEntity(localVarReturnType);
    }

    /**
     * Remove MCP server
     * Stop an MCP server and remove it from the runtime set until restart.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - McpServerNotFoundError
     * @param server The server parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalMcpRemoveWithResponseSpec(@jakarta.annotation.Nonnull String server, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return experimentalMcpRemoveRequestCreation(server, location);
    }

    /**
     * List MCP servers
     * Retrieve configured MCP servers and their connection status.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return McpList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec mcpListRequestCreation(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = null;
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<McpList200Response> localVarReturnType = new ParameterizedTypeReference<McpList200Response>() {};
        return apiClient.invokeAPI("/api/mcp", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List MCP servers
     * Retrieve configured MCP servers and their connection status.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return McpList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<McpList200Response> mcpList(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<McpList200Response> localVarReturnType = new ParameterizedTypeReference<McpList200Response>() {};
        return mcpListRequestCreation(location).bodyToMono(localVarReturnType);
    }

    /**
     * List MCP servers
     * Retrieve configured MCP servers and their connection status.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseEntity&lt;McpList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<McpList200Response>> mcpListWithHttpInfo(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<McpList200Response> localVarReturnType = new ParameterizedTypeReference<McpList200Response>() {};
        return mcpListRequestCreation(location).toEntity(localVarReturnType);
    }

    /**
     * List MCP servers
     * Retrieve configured MCP servers and their connection status.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec mcpListWithResponseSpec(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return mcpListRequestCreation(location);
    }

    /**
     * List MCP resources
     * Retrieve resources and resource templates from connected MCP servers.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return McpResourceCatalog200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec mcpResourceCatalogRequestCreation(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = null;
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<McpResourceCatalog200Response> localVarReturnType = new ParameterizedTypeReference<McpResourceCatalog200Response>() {};
        return apiClient.invokeAPI("/api/mcp/resource", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List MCP resources
     * Retrieve resources and resource templates from connected MCP servers.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return McpResourceCatalog200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<McpResourceCatalog200Response> mcpResourceCatalog(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<McpResourceCatalog200Response> localVarReturnType = new ParameterizedTypeReference<McpResourceCatalog200Response>() {};
        return mcpResourceCatalogRequestCreation(location).bodyToMono(localVarReturnType);
    }

    /**
     * List MCP resources
     * Retrieve resources and resource templates from connected MCP servers.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseEntity&lt;McpResourceCatalog200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<McpResourceCatalog200Response>> mcpResourceCatalogWithHttpInfo(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<McpResourceCatalog200Response> localVarReturnType = new ParameterizedTypeReference<McpResourceCatalog200Response>() {};
        return mcpResourceCatalogRequestCreation(location).toEntity(localVarReturnType);
    }

    /**
     * List MCP resources
     * Retrieve resources and resource templates from connected MCP servers.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec mcpResourceCatalogWithResponseSpec(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return mcpResourceCatalogRequestCreation(location);
    }
}
