package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.AuthSet400Response;
import com.example.opencode.sdk.model.BadRequestError;
import com.example.opencode.sdk.model.MCPStatus;
import com.example.opencode.sdk.model.McpAddRequest;
import com.example.opencode.sdk.model.McpAuthCallbackRequest;
import com.example.opencode.sdk.model.McpAuthRemove200Response;
import com.example.opencode.sdk.model.McpAuthStart200Response;
import com.example.opencode.sdk.model.McpAuthStart400Response;
import com.example.opencode.sdk.model.McpServerNotFoundError;

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

    public class McpAddRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable McpAddRequest mcpAddRequest;

        public McpAddRequest() {}

        public McpAddRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable McpAddRequest mcpAddRequest) {
            this.directory = directory;
            this.workspace = workspace;
            this.mcpAddRequest = mcpAddRequest;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public McpAddRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public McpAddRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable McpAddRequest mcpAddRequest() {
            return this.mcpAddRequest;
        }
        public McpAddRequest mcpAddRequest(@jakarta.annotation.Nullable McpAddRequest mcpAddRequest) {
            this.mcpAddRequest = mcpAddRequest;
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
            McpAddRequest request = (McpAddRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.mcpAddRequest, request.mcpAddRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace, mcpAddRequest);
        }
    }

    /**
     * Add MCP server
     * Dynamically add a new Model Context Protocol (MCP) server to the system.
     * <p><b>200</b> - MCP server added successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The mcpAdd request parameters as object
     * @return Map&lt;String, MCPStatus&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Map<String, MCPStatus>> mcpAdd(McpAddRequest requestParameters) throws WebClientResponseException {
        return this.mcpAdd(requestParameters.directory(), requestParameters.workspace(), requestParameters.mcpAddRequest());
    }

    /**
     * Add MCP server
     * Dynamically add a new Model Context Protocol (MCP) server to the system.
     * <p><b>200</b> - MCP server added successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The mcpAdd request parameters as object
     * @return ResponseEntity&lt;Map&lt;String, MCPStatus&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Map<String, MCPStatus>>> mcpAddWithHttpInfo(McpAddRequest requestParameters) throws WebClientResponseException {
        return this.mcpAddWithHttpInfo(requestParameters.directory(), requestParameters.workspace(), requestParameters.mcpAddRequest());
    }

    /**
     * Add MCP server
     * Dynamically add a new Model Context Protocol (MCP) server to the system.
     * <p><b>200</b> - MCP server added successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The mcpAdd request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec mcpAddWithResponseSpec(McpAddRequest requestParameters) throws WebClientResponseException {
        return this.mcpAddWithResponseSpec(requestParameters.directory(), requestParameters.workspace(), requestParameters.mcpAddRequest());
    }


    /**
     * Add MCP server
     * Dynamically add a new Model Context Protocol (MCP) server to the system.
     * <p><b>200</b> - MCP server added successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param mcpAddRequest The mcpAddRequest parameter
     * @return Map&lt;String, MCPStatus&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec mcpAddRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable McpAddRequest mcpAddRequest) throws WebClientResponseException {
        Object postBody = mcpAddRequest;
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

        ParameterizedTypeReference<Map<String, MCPStatus>> localVarReturnType = new ParameterizedTypeReference<Map<String, MCPStatus>>() {};
        return apiClient.invokeAPI("/mcp", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Add MCP server
     * Dynamically add a new Model Context Protocol (MCP) server to the system.
     * <p><b>200</b> - MCP server added successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param mcpAddRequest The mcpAddRequest parameter
     * @return Map&lt;String, MCPStatus&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Map<String, MCPStatus>> mcpAdd(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable McpAddRequest mcpAddRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Map<String, MCPStatus>> localVarReturnType = new ParameterizedTypeReference<Map<String, MCPStatus>>() {};
        return mcpAddRequestCreation(directory, workspace, mcpAddRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Add MCP server
     * Dynamically add a new Model Context Protocol (MCP) server to the system.
     * <p><b>200</b> - MCP server added successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param mcpAddRequest The mcpAddRequest parameter
     * @return ResponseEntity&lt;Map&lt;String, MCPStatus&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Map<String, MCPStatus>>> mcpAddWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable McpAddRequest mcpAddRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Map<String, MCPStatus>> localVarReturnType = new ParameterizedTypeReference<Map<String, MCPStatus>>() {};
        return mcpAddRequestCreation(directory, workspace, mcpAddRequest).toEntity(localVarReturnType);
    }

    /**
     * Add MCP server
     * Dynamically add a new Model Context Protocol (MCP) server to the system.
     * <p><b>200</b> - MCP server added successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param mcpAddRequest The mcpAddRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec mcpAddWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable McpAddRequest mcpAddRequest) throws WebClientResponseException {
        return mcpAddRequestCreation(directory, workspace, mcpAddRequest);
    }

    public class McpAuthAuthenticateRequest {
        private @jakarta.annotation.Nonnull String name;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public McpAuthAuthenticateRequest() {}

        public McpAuthAuthenticateRequest(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.name = name;
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nonnull String name() {
            return this.name;
        }
        public McpAuthAuthenticateRequest name(@jakarta.annotation.Nonnull String name) {
            this.name = name;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public McpAuthAuthenticateRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public McpAuthAuthenticateRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            McpAuthAuthenticateRequest request = (McpAuthAuthenticateRequest) o;
            return Objects.equals(this.name, request.name()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, directory, workspace);
        }
    }

    /**
     * Authenticate MCP OAuth
     * Start OAuth flow and wait for callback (opens browser).
     * <p><b>200</b> - OAuth authentication completed
     * <p><b>400</b> - McpUnsupportedOAuthError | InvalidRequestError
     * <p><b>404</b> - McpServerNotFoundError
     * @param requestParameters The mcpAuthAuthenticate request parameters as object
     * @return MCPStatus
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<MCPStatus> mcpAuthAuthenticate(McpAuthAuthenticateRequest requestParameters) throws WebClientResponseException {
        return this.mcpAuthAuthenticate(requestParameters.name(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Authenticate MCP OAuth
     * Start OAuth flow and wait for callback (opens browser).
     * <p><b>200</b> - OAuth authentication completed
     * <p><b>400</b> - McpUnsupportedOAuthError | InvalidRequestError
     * <p><b>404</b> - McpServerNotFoundError
     * @param requestParameters The mcpAuthAuthenticate request parameters as object
     * @return ResponseEntity&lt;MCPStatus&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<MCPStatus>> mcpAuthAuthenticateWithHttpInfo(McpAuthAuthenticateRequest requestParameters) throws WebClientResponseException {
        return this.mcpAuthAuthenticateWithHttpInfo(requestParameters.name(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Authenticate MCP OAuth
     * Start OAuth flow and wait for callback (opens browser).
     * <p><b>200</b> - OAuth authentication completed
     * <p><b>400</b> - McpUnsupportedOAuthError | InvalidRequestError
     * <p><b>404</b> - McpServerNotFoundError
     * @param requestParameters The mcpAuthAuthenticate request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec mcpAuthAuthenticateWithResponseSpec(McpAuthAuthenticateRequest requestParameters) throws WebClientResponseException {
        return this.mcpAuthAuthenticateWithResponseSpec(requestParameters.name(), requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Authenticate MCP OAuth
     * Start OAuth flow and wait for callback (opens browser).
     * <p><b>200</b> - OAuth authentication completed
     * <p><b>400</b> - McpUnsupportedOAuthError | InvalidRequestError
     * <p><b>404</b> - McpServerNotFoundError
     * @param name The name parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return MCPStatus
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec mcpAuthAuthenticateRequestCreation(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'name' is set
        if (name == null) {
            throw new WebClientResponseException("Missing the required parameter 'name' when calling mcpAuthAuthenticate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("name", name);

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

        ParameterizedTypeReference<MCPStatus> localVarReturnType = new ParameterizedTypeReference<MCPStatus>() {};
        return apiClient.invokeAPI("/mcp/{name}/auth/authenticate", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Authenticate MCP OAuth
     * Start OAuth flow and wait for callback (opens browser).
     * <p><b>200</b> - OAuth authentication completed
     * <p><b>400</b> - McpUnsupportedOAuthError | InvalidRequestError
     * <p><b>404</b> - McpServerNotFoundError
     * @param name The name parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return MCPStatus
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<MCPStatus> mcpAuthAuthenticate(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<MCPStatus> localVarReturnType = new ParameterizedTypeReference<MCPStatus>() {};
        return mcpAuthAuthenticateRequestCreation(name, directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Authenticate MCP OAuth
     * Start OAuth flow and wait for callback (opens browser).
     * <p><b>200</b> - OAuth authentication completed
     * <p><b>400</b> - McpUnsupportedOAuthError | InvalidRequestError
     * <p><b>404</b> - McpServerNotFoundError
     * @param name The name parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;MCPStatus&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<MCPStatus>> mcpAuthAuthenticateWithHttpInfo(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<MCPStatus> localVarReturnType = new ParameterizedTypeReference<MCPStatus>() {};
        return mcpAuthAuthenticateRequestCreation(name, directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Authenticate MCP OAuth
     * Start OAuth flow and wait for callback (opens browser).
     * <p><b>200</b> - OAuth authentication completed
     * <p><b>400</b> - McpUnsupportedOAuthError | InvalidRequestError
     * <p><b>404</b> - McpServerNotFoundError
     * @param name The name parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec mcpAuthAuthenticateWithResponseSpec(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return mcpAuthAuthenticateRequestCreation(name, directory, workspace);
    }

    public class McpAuthCallbackRequest {
        private @jakarta.annotation.Nonnull String name;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable McpAuthCallbackRequest mcpAuthCallbackRequest;

        public McpAuthCallbackRequest() {}

        public McpAuthCallbackRequest(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable McpAuthCallbackRequest mcpAuthCallbackRequest) {
            this.name = name;
            this.directory = directory;
            this.workspace = workspace;
            this.mcpAuthCallbackRequest = mcpAuthCallbackRequest;
        }

        public @jakarta.annotation.Nonnull String name() {
            return this.name;
        }
        public McpAuthCallbackRequest name(@jakarta.annotation.Nonnull String name) {
            this.name = name;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public McpAuthCallbackRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public McpAuthCallbackRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable McpAuthCallbackRequest mcpAuthCallbackRequest() {
            return this.mcpAuthCallbackRequest;
        }
        public McpAuthCallbackRequest mcpAuthCallbackRequest(@jakarta.annotation.Nullable McpAuthCallbackRequest mcpAuthCallbackRequest) {
            this.mcpAuthCallbackRequest = mcpAuthCallbackRequest;
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
            McpAuthCallbackRequest request = (McpAuthCallbackRequest) o;
            return Objects.equals(this.name, request.name()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.mcpAuthCallbackRequest, request.mcpAuthCallbackRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, directory, workspace, mcpAuthCallbackRequest);
        }
    }

    /**
     * Complete MCP OAuth
     * Complete OAuth authentication for a Model Context Protocol (MCP) server using the authorization code.
     * <p><b>200</b> - OAuth authentication completed
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - McpServerNotFoundError
     * @param requestParameters The mcpAuthCallback request parameters as object
     * @return MCPStatus
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<MCPStatus> mcpAuthCallback(McpAuthCallbackRequest requestParameters) throws WebClientResponseException {
        return this.mcpAuthCallback(requestParameters.name(), requestParameters.directory(), requestParameters.workspace(), requestParameters.mcpAuthCallbackRequest());
    }

    /**
     * Complete MCP OAuth
     * Complete OAuth authentication for a Model Context Protocol (MCP) server using the authorization code.
     * <p><b>200</b> - OAuth authentication completed
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - McpServerNotFoundError
     * @param requestParameters The mcpAuthCallback request parameters as object
     * @return ResponseEntity&lt;MCPStatus&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<MCPStatus>> mcpAuthCallbackWithHttpInfo(McpAuthCallbackRequest requestParameters) throws WebClientResponseException {
        return this.mcpAuthCallbackWithHttpInfo(requestParameters.name(), requestParameters.directory(), requestParameters.workspace(), requestParameters.mcpAuthCallbackRequest());
    }

    /**
     * Complete MCP OAuth
     * Complete OAuth authentication for a Model Context Protocol (MCP) server using the authorization code.
     * <p><b>200</b> - OAuth authentication completed
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - McpServerNotFoundError
     * @param requestParameters The mcpAuthCallback request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec mcpAuthCallbackWithResponseSpec(McpAuthCallbackRequest requestParameters) throws WebClientResponseException {
        return this.mcpAuthCallbackWithResponseSpec(requestParameters.name(), requestParameters.directory(), requestParameters.workspace(), requestParameters.mcpAuthCallbackRequest());
    }


    /**
     * Complete MCP OAuth
     * Complete OAuth authentication for a Model Context Protocol (MCP) server using the authorization code.
     * <p><b>200</b> - OAuth authentication completed
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - McpServerNotFoundError
     * @param name The name parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param mcpAuthCallbackRequest The mcpAuthCallbackRequest parameter
     * @return MCPStatus
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec mcpAuthCallbackRequestCreation(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable McpAuthCallbackRequest mcpAuthCallbackRequest) throws WebClientResponseException {
        Object postBody = mcpAuthCallbackRequest;
        // verify the required parameter 'name' is set
        if (name == null) {
            throw new WebClientResponseException("Missing the required parameter 'name' when calling mcpAuthCallback", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("name", name);

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

        ParameterizedTypeReference<MCPStatus> localVarReturnType = new ParameterizedTypeReference<MCPStatus>() {};
        return apiClient.invokeAPI("/mcp/{name}/auth/callback", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Complete MCP OAuth
     * Complete OAuth authentication for a Model Context Protocol (MCP) server using the authorization code.
     * <p><b>200</b> - OAuth authentication completed
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - McpServerNotFoundError
     * @param name The name parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param mcpAuthCallbackRequest The mcpAuthCallbackRequest parameter
     * @return MCPStatus
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<MCPStatus> mcpAuthCallback(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable McpAuthCallbackRequest mcpAuthCallbackRequest) throws WebClientResponseException {
        ParameterizedTypeReference<MCPStatus> localVarReturnType = new ParameterizedTypeReference<MCPStatus>() {};
        return mcpAuthCallbackRequestCreation(name, directory, workspace, mcpAuthCallbackRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Complete MCP OAuth
     * Complete OAuth authentication for a Model Context Protocol (MCP) server using the authorization code.
     * <p><b>200</b> - OAuth authentication completed
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - McpServerNotFoundError
     * @param name The name parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param mcpAuthCallbackRequest The mcpAuthCallbackRequest parameter
     * @return ResponseEntity&lt;MCPStatus&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<MCPStatus>> mcpAuthCallbackWithHttpInfo(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable McpAuthCallbackRequest mcpAuthCallbackRequest) throws WebClientResponseException {
        ParameterizedTypeReference<MCPStatus> localVarReturnType = new ParameterizedTypeReference<MCPStatus>() {};
        return mcpAuthCallbackRequestCreation(name, directory, workspace, mcpAuthCallbackRequest).toEntity(localVarReturnType);
    }

    /**
     * Complete MCP OAuth
     * Complete OAuth authentication for a Model Context Protocol (MCP) server using the authorization code.
     * <p><b>200</b> - OAuth authentication completed
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - McpServerNotFoundError
     * @param name The name parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param mcpAuthCallbackRequest The mcpAuthCallbackRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec mcpAuthCallbackWithResponseSpec(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable McpAuthCallbackRequest mcpAuthCallbackRequest) throws WebClientResponseException {
        return mcpAuthCallbackRequestCreation(name, directory, workspace, mcpAuthCallbackRequest);
    }

    public class McpAuthRemoveRequest {
        private @jakarta.annotation.Nonnull String name;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public McpAuthRemoveRequest() {}

        public McpAuthRemoveRequest(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.name = name;
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nonnull String name() {
            return this.name;
        }
        public McpAuthRemoveRequest name(@jakarta.annotation.Nonnull String name) {
            this.name = name;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public McpAuthRemoveRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public McpAuthRemoveRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            McpAuthRemoveRequest request = (McpAuthRemoveRequest) o;
            return Objects.equals(this.name, request.name()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, directory, workspace);
        }
    }

    /**
     * Remove MCP OAuth
     * Remove OAuth credentials for an MCP server.
     * <p><b>200</b> - OAuth credentials removed
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - McpServerNotFoundError
     * @param requestParameters The mcpAuthRemove request parameters as object
     * @return McpAuthRemove200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<McpAuthRemove200Response> mcpAuthRemove(McpAuthRemoveRequest requestParameters) throws WebClientResponseException {
        return this.mcpAuthRemove(requestParameters.name(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Remove MCP OAuth
     * Remove OAuth credentials for an MCP server.
     * <p><b>200</b> - OAuth credentials removed
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - McpServerNotFoundError
     * @param requestParameters The mcpAuthRemove request parameters as object
     * @return ResponseEntity&lt;McpAuthRemove200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<McpAuthRemove200Response>> mcpAuthRemoveWithHttpInfo(McpAuthRemoveRequest requestParameters) throws WebClientResponseException {
        return this.mcpAuthRemoveWithHttpInfo(requestParameters.name(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Remove MCP OAuth
     * Remove OAuth credentials for an MCP server.
     * <p><b>200</b> - OAuth credentials removed
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - McpServerNotFoundError
     * @param requestParameters The mcpAuthRemove request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec mcpAuthRemoveWithResponseSpec(McpAuthRemoveRequest requestParameters) throws WebClientResponseException {
        return this.mcpAuthRemoveWithResponseSpec(requestParameters.name(), requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Remove MCP OAuth
     * Remove OAuth credentials for an MCP server.
     * <p><b>200</b> - OAuth credentials removed
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - McpServerNotFoundError
     * @param name The name parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return McpAuthRemove200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec mcpAuthRemoveRequestCreation(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'name' is set
        if (name == null) {
            throw new WebClientResponseException("Missing the required parameter 'name' when calling mcpAuthRemove", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("name", name);

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

        ParameterizedTypeReference<McpAuthRemove200Response> localVarReturnType = new ParameterizedTypeReference<McpAuthRemove200Response>() {};
        return apiClient.invokeAPI("/mcp/{name}/auth", HttpMethod.DELETE, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Remove MCP OAuth
     * Remove OAuth credentials for an MCP server.
     * <p><b>200</b> - OAuth credentials removed
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - McpServerNotFoundError
     * @param name The name parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return McpAuthRemove200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<McpAuthRemove200Response> mcpAuthRemove(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<McpAuthRemove200Response> localVarReturnType = new ParameterizedTypeReference<McpAuthRemove200Response>() {};
        return mcpAuthRemoveRequestCreation(name, directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Remove MCP OAuth
     * Remove OAuth credentials for an MCP server.
     * <p><b>200</b> - OAuth credentials removed
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - McpServerNotFoundError
     * @param name The name parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;McpAuthRemove200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<McpAuthRemove200Response>> mcpAuthRemoveWithHttpInfo(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<McpAuthRemove200Response> localVarReturnType = new ParameterizedTypeReference<McpAuthRemove200Response>() {};
        return mcpAuthRemoveRequestCreation(name, directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Remove MCP OAuth
     * Remove OAuth credentials for an MCP server.
     * <p><b>200</b> - OAuth credentials removed
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - McpServerNotFoundError
     * @param name The name parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec mcpAuthRemoveWithResponseSpec(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return mcpAuthRemoveRequestCreation(name, directory, workspace);
    }

    public class McpAuthStartRequest {
        private @jakarta.annotation.Nonnull String name;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public McpAuthStartRequest() {}

        public McpAuthStartRequest(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.name = name;
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nonnull String name() {
            return this.name;
        }
        public McpAuthStartRequest name(@jakarta.annotation.Nonnull String name) {
            this.name = name;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public McpAuthStartRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public McpAuthStartRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            McpAuthStartRequest request = (McpAuthStartRequest) o;
            return Objects.equals(this.name, request.name()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, directory, workspace);
        }
    }

    /**
     * Start MCP OAuth
     * Start OAuth authentication flow for a Model Context Protocol (MCP) server.
     * <p><b>200</b> - OAuth flow started
     * <p><b>400</b> - McpUnsupportedOAuthError | InvalidRequestError
     * <p><b>404</b> - McpServerNotFoundError
     * @param requestParameters The mcpAuthStart request parameters as object
     * @return McpAuthStart200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<McpAuthStart200Response> mcpAuthStart(McpAuthStartRequest requestParameters) throws WebClientResponseException {
        return this.mcpAuthStart(requestParameters.name(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Start MCP OAuth
     * Start OAuth authentication flow for a Model Context Protocol (MCP) server.
     * <p><b>200</b> - OAuth flow started
     * <p><b>400</b> - McpUnsupportedOAuthError | InvalidRequestError
     * <p><b>404</b> - McpServerNotFoundError
     * @param requestParameters The mcpAuthStart request parameters as object
     * @return ResponseEntity&lt;McpAuthStart200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<McpAuthStart200Response>> mcpAuthStartWithHttpInfo(McpAuthStartRequest requestParameters) throws WebClientResponseException {
        return this.mcpAuthStartWithHttpInfo(requestParameters.name(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Start MCP OAuth
     * Start OAuth authentication flow for a Model Context Protocol (MCP) server.
     * <p><b>200</b> - OAuth flow started
     * <p><b>400</b> - McpUnsupportedOAuthError | InvalidRequestError
     * <p><b>404</b> - McpServerNotFoundError
     * @param requestParameters The mcpAuthStart request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec mcpAuthStartWithResponseSpec(McpAuthStartRequest requestParameters) throws WebClientResponseException {
        return this.mcpAuthStartWithResponseSpec(requestParameters.name(), requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Start MCP OAuth
     * Start OAuth authentication flow for a Model Context Protocol (MCP) server.
     * <p><b>200</b> - OAuth flow started
     * <p><b>400</b> - McpUnsupportedOAuthError | InvalidRequestError
     * <p><b>404</b> - McpServerNotFoundError
     * @param name The name parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return McpAuthStart200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec mcpAuthStartRequestCreation(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'name' is set
        if (name == null) {
            throw new WebClientResponseException("Missing the required parameter 'name' when calling mcpAuthStart", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("name", name);

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

        ParameterizedTypeReference<McpAuthStart200Response> localVarReturnType = new ParameterizedTypeReference<McpAuthStart200Response>() {};
        return apiClient.invokeAPI("/mcp/{name}/auth", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Start MCP OAuth
     * Start OAuth authentication flow for a Model Context Protocol (MCP) server.
     * <p><b>200</b> - OAuth flow started
     * <p><b>400</b> - McpUnsupportedOAuthError | InvalidRequestError
     * <p><b>404</b> - McpServerNotFoundError
     * @param name The name parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return McpAuthStart200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<McpAuthStart200Response> mcpAuthStart(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<McpAuthStart200Response> localVarReturnType = new ParameterizedTypeReference<McpAuthStart200Response>() {};
        return mcpAuthStartRequestCreation(name, directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Start MCP OAuth
     * Start OAuth authentication flow for a Model Context Protocol (MCP) server.
     * <p><b>200</b> - OAuth flow started
     * <p><b>400</b> - McpUnsupportedOAuthError | InvalidRequestError
     * <p><b>404</b> - McpServerNotFoundError
     * @param name The name parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;McpAuthStart200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<McpAuthStart200Response>> mcpAuthStartWithHttpInfo(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<McpAuthStart200Response> localVarReturnType = new ParameterizedTypeReference<McpAuthStart200Response>() {};
        return mcpAuthStartRequestCreation(name, directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Start MCP OAuth
     * Start OAuth authentication flow for a Model Context Protocol (MCP) server.
     * <p><b>200</b> - OAuth flow started
     * <p><b>400</b> - McpUnsupportedOAuthError | InvalidRequestError
     * <p><b>404</b> - McpServerNotFoundError
     * @param name The name parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec mcpAuthStartWithResponseSpec(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return mcpAuthStartRequestCreation(name, directory, workspace);
    }

    public class McpConnectRequest {
        private @jakarta.annotation.Nonnull String name;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public McpConnectRequest() {}

        public McpConnectRequest(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.name = name;
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nonnull String name() {
            return this.name;
        }
        public McpConnectRequest name(@jakarta.annotation.Nonnull String name) {
            this.name = name;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public McpConnectRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public McpConnectRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            McpConnectRequest request = (McpConnectRequest) o;
            return Objects.equals(this.name, request.name()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, directory, workspace);
        }
    }

    /**
     *
     * Connect an MCP server.
     * <p><b>200</b> - MCP server connected successfully
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - McpServerNotFoundError
     * @param requestParameters The mcpConnect request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> mcpConnect(McpConnectRequest requestParameters) throws WebClientResponseException {
        return this.mcpConnect(requestParameters.name(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     *
     * Connect an MCP server.
     * <p><b>200</b> - MCP server connected successfully
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - McpServerNotFoundError
     * @param requestParameters The mcpConnect request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> mcpConnectWithHttpInfo(McpConnectRequest requestParameters) throws WebClientResponseException {
        return this.mcpConnectWithHttpInfo(requestParameters.name(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     *
     * Connect an MCP server.
     * <p><b>200</b> - MCP server connected successfully
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - McpServerNotFoundError
     * @param requestParameters The mcpConnect request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec mcpConnectWithResponseSpec(McpConnectRequest requestParameters) throws WebClientResponseException {
        return this.mcpConnectWithResponseSpec(requestParameters.name(), requestParameters.directory(), requestParameters.workspace());
    }


    /**
     *
     * Connect an MCP server.
     * <p><b>200</b> - MCP server connected successfully
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - McpServerNotFoundError
     * @param name The name parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec mcpConnectRequestCreation(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'name' is set
        if (name == null) {
            throw new WebClientResponseException("Missing the required parameter 'name' when calling mcpConnect", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("name", name);

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
        return apiClient.invokeAPI("/mcp/{name}/connect", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     *
     * Connect an MCP server.
     * <p><b>200</b> - MCP server connected successfully
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - McpServerNotFoundError
     * @param name The name parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> mcpConnect(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return mcpConnectRequestCreation(name, directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     *
     * Connect an MCP server.
     * <p><b>200</b> - MCP server connected successfully
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - McpServerNotFoundError
     * @param name The name parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> mcpConnectWithHttpInfo(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return mcpConnectRequestCreation(name, directory, workspace).toEntity(localVarReturnType);
    }

    /**
     *
     * Connect an MCP server.
     * <p><b>200</b> - MCP server connected successfully
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - McpServerNotFoundError
     * @param name The name parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec mcpConnectWithResponseSpec(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return mcpConnectRequestCreation(name, directory, workspace);
    }

    public class McpDisconnectRequest {
        private @jakarta.annotation.Nonnull String name;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public McpDisconnectRequest() {}

        public McpDisconnectRequest(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.name = name;
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nonnull String name() {
            return this.name;
        }
        public McpDisconnectRequest name(@jakarta.annotation.Nonnull String name) {
            this.name = name;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public McpDisconnectRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public McpDisconnectRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            McpDisconnectRequest request = (McpDisconnectRequest) o;
            return Objects.equals(this.name, request.name()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, directory, workspace);
        }
    }

    /**
     *
     * Disconnect an MCP server.
     * <p><b>200</b> - MCP server disconnected successfully
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - McpServerNotFoundError
     * @param requestParameters The mcpDisconnect request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> mcpDisconnect(McpDisconnectRequest requestParameters) throws WebClientResponseException {
        return this.mcpDisconnect(requestParameters.name(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     *
     * Disconnect an MCP server.
     * <p><b>200</b> - MCP server disconnected successfully
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - McpServerNotFoundError
     * @param requestParameters The mcpDisconnect request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> mcpDisconnectWithHttpInfo(McpDisconnectRequest requestParameters) throws WebClientResponseException {
        return this.mcpDisconnectWithHttpInfo(requestParameters.name(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     *
     * Disconnect an MCP server.
     * <p><b>200</b> - MCP server disconnected successfully
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - McpServerNotFoundError
     * @param requestParameters The mcpDisconnect request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec mcpDisconnectWithResponseSpec(McpDisconnectRequest requestParameters) throws WebClientResponseException {
        return this.mcpDisconnectWithResponseSpec(requestParameters.name(), requestParameters.directory(), requestParameters.workspace());
    }


    /**
     *
     * Disconnect an MCP server.
     * <p><b>200</b> - MCP server disconnected successfully
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - McpServerNotFoundError
     * @param name The name parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec mcpDisconnectRequestCreation(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'name' is set
        if (name == null) {
            throw new WebClientResponseException("Missing the required parameter 'name' when calling mcpDisconnect", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("name", name);

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
        return apiClient.invokeAPI("/mcp/{name}/disconnect", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     *
     * Disconnect an MCP server.
     * <p><b>200</b> - MCP server disconnected successfully
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - McpServerNotFoundError
     * @param name The name parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> mcpDisconnect(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return mcpDisconnectRequestCreation(name, directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     *
     * Disconnect an MCP server.
     * <p><b>200</b> - MCP server disconnected successfully
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - McpServerNotFoundError
     * @param name The name parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> mcpDisconnectWithHttpInfo(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return mcpDisconnectRequestCreation(name, directory, workspace).toEntity(localVarReturnType);
    }

    /**
     *
     * Disconnect an MCP server.
     * <p><b>200</b> - MCP server disconnected successfully
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - McpServerNotFoundError
     * @param name The name parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec mcpDisconnectWithResponseSpec(@jakarta.annotation.Nonnull String name, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return mcpDisconnectRequestCreation(name, directory, workspace);
    }

    public class McpStatusRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public McpStatusRequest() {}

        public McpStatusRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public McpStatusRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public McpStatusRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            McpStatusRequest request = (McpStatusRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * Get MCP status
     * Get the status of all Model Context Protocol (MCP) servers.
     * <p><b>200</b> - MCP server status
     * <p><b>400</b> - Bad request
     * @param requestParameters The mcpStatus request parameters as object
     * @return Map&lt;String, MCPStatus&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Map<String, MCPStatus>> mcpStatus(McpStatusRequest requestParameters) throws WebClientResponseException {
        return this.mcpStatus(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get MCP status
     * Get the status of all Model Context Protocol (MCP) servers.
     * <p><b>200</b> - MCP server status
     * <p><b>400</b> - Bad request
     * @param requestParameters The mcpStatus request parameters as object
     * @return ResponseEntity&lt;Map&lt;String, MCPStatus&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Map<String, MCPStatus>>> mcpStatusWithHttpInfo(McpStatusRequest requestParameters) throws WebClientResponseException {
        return this.mcpStatusWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get MCP status
     * Get the status of all Model Context Protocol (MCP) servers.
     * <p><b>200</b> - MCP server status
     * <p><b>400</b> - Bad request
     * @param requestParameters The mcpStatus request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec mcpStatusWithResponseSpec(McpStatusRequest requestParameters) throws WebClientResponseException {
        return this.mcpStatusWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Get MCP status
     * Get the status of all Model Context Protocol (MCP) servers.
     * <p><b>200</b> - MCP server status
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Map&lt;String, MCPStatus&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec mcpStatusRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<Map<String, MCPStatus>> localVarReturnType = new ParameterizedTypeReference<Map<String, MCPStatus>>() {};
        return apiClient.invokeAPI("/mcp", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get MCP status
     * Get the status of all Model Context Protocol (MCP) servers.
     * <p><b>200</b> - MCP server status
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Map&lt;String, MCPStatus&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Map<String, MCPStatus>> mcpStatus(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Map<String, MCPStatus>> localVarReturnType = new ParameterizedTypeReference<Map<String, MCPStatus>>() {};
        return mcpStatusRequestCreation(directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Get MCP status
     * Get the status of all Model Context Protocol (MCP) servers.
     * <p><b>200</b> - MCP server status
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Map&lt;String, MCPStatus&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Map<String, MCPStatus>>> mcpStatusWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Map<String, MCPStatus>> localVarReturnType = new ParameterizedTypeReference<Map<String, MCPStatus>>() {};
        return mcpStatusRequestCreation(directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Get MCP status
     * Get the status of all Model Context Protocol (MCP) servers.
     * <p><b>200</b> - MCP server status
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec mcpStatusWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return mcpStatusRequestCreation(directory, workspace);
    }
}
