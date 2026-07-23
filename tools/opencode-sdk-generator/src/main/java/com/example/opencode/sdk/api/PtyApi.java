package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.AuthSet400Response;
import com.example.opencode.sdk.model.BadRequestError;
import com.example.opencode.sdk.model.EffectHttpApiErrorForbidden;
import com.example.opencode.sdk.model.ForbiddenError;
import com.example.opencode.sdk.model.InvalidRequestError;
import com.example.opencode.sdk.model.NotFoundError;
import com.example.opencode.sdk.model.Pty;
import com.example.opencode.sdk.model.PtyCreateRequest;
import com.example.opencode.sdk.model.PtyForbiddenError;
import com.example.opencode.sdk.model.PtyNotFoundError;
import com.example.opencode.sdk.model.PtyShells200ResponseInner;
import com.example.opencode.sdk.model.PtyTicketConnectToken;
import com.example.opencode.sdk.model.PtyUpdateRequest;
import com.example.opencode.sdk.model.UnauthorizedError;
import com.example.opencode.sdk.model.V2AgentListLocationParameter;
import com.example.opencode.sdk.model.V2PtyConnectToken200Response;
import com.example.opencode.sdk.model.V2PtyCreate200Response;
import com.example.opencode.sdk.model.V2PtyGet200Response;
import com.example.opencode.sdk.model.V2PtyList200Response;

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
public class PtyApi {
    private ApiClient apiClient;

    public PtyApi() {
        this(new ApiClient());
    }

    public PtyApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public class PtyConnectRequest {
        private @jakarta.annotation.Nonnull String ptyID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable String cursor;
        private @jakarta.annotation.Nullable String ticket;

        public PtyConnectRequest() {}

        public PtyConnectRequest(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String ticket) {
            this.ptyID = ptyID;
            this.directory = directory;
            this.workspace = workspace;
            this.cursor = cursor;
            this.ticket = ticket;
        }

        public @jakarta.annotation.Nonnull String ptyID() {
            return this.ptyID;
        }
        public PtyConnectRequest ptyID(@jakarta.annotation.Nonnull String ptyID) {
            this.ptyID = ptyID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public PtyConnectRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public PtyConnectRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable String cursor() {
            return this.cursor;
        }
        public PtyConnectRequest cursor(@jakarta.annotation.Nullable String cursor) {
            this.cursor = cursor;
            return this;
        }

        public @jakarta.annotation.Nullable String ticket() {
            return this.ticket;
        }
        public PtyConnectRequest ticket(@jakarta.annotation.Nullable String ticket) {
            this.ticket = ticket;
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
            PtyConnectRequest request = (PtyConnectRequest) o;
            return Objects.equals(this.ptyID, request.ptyID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.cursor, request.cursor()) &&
                Objects.equals(this.ticket, request.ticket());
        }

        @Override
        public int hashCode() {
            return Objects.hash(ptyID, directory, workspace, cursor, ticket);
        }
    }

    /**
     * Connect to PTY session
     * Establish a WebSocket connection to interact with a pseudo-terminal (PTY) session in real-time.
     * <p><b>200</b> - Connected session
     * <p><b>403</b> - Forbidden
     * <p><b>404</b> - Not found
     * @param requestParameters The ptyConnect request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> ptyConnect(PtyConnectRequest requestParameters) throws WebClientResponseException {
        return this.ptyConnect(requestParameters.ptyID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.cursor(), requestParameters.ticket());
    }

    /**
     * Connect to PTY session
     * Establish a WebSocket connection to interact with a pseudo-terminal (PTY) session in real-time.
     * <p><b>200</b> - Connected session
     * <p><b>403</b> - Forbidden
     * <p><b>404</b> - Not found
     * @param requestParameters The ptyConnect request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> ptyConnectWithHttpInfo(PtyConnectRequest requestParameters) throws WebClientResponseException {
        return this.ptyConnectWithHttpInfo(requestParameters.ptyID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.cursor(), requestParameters.ticket());
    }

    /**
     * Connect to PTY session
     * Establish a WebSocket connection to interact with a pseudo-terminal (PTY) session in real-time.
     * <p><b>200</b> - Connected session
     * <p><b>403</b> - Forbidden
     * <p><b>404</b> - Not found
     * @param requestParameters The ptyConnect request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec ptyConnectWithResponseSpec(PtyConnectRequest requestParameters) throws WebClientResponseException {
        return this.ptyConnectWithResponseSpec(requestParameters.ptyID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.cursor(), requestParameters.ticket());
    }


    /**
     * Connect to PTY session
     * Establish a WebSocket connection to interact with a pseudo-terminal (PTY) session in real-time.
     * <p><b>200</b> - Connected session
     * <p><b>403</b> - Forbidden
     * <p><b>404</b> - Not found
     * @param ptyID The ptyID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param cursor The cursor parameter
     * @param ticket The ticket parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec ptyConnectRequestCreation(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String ticket) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'ptyID' is set
        if (ptyID == null) {
            throw new WebClientResponseException("Missing the required parameter 'ptyID' when calling ptyConnect", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("ptyID", ptyID);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", directory));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", workspace));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "cursor", cursor));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "ticket", ticket));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return apiClient.invokeAPI("/pty/{ptyID}/connect", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Connect to PTY session
     * Establish a WebSocket connection to interact with a pseudo-terminal (PTY) session in real-time.
     * <p><b>200</b> - Connected session
     * <p><b>403</b> - Forbidden
     * <p><b>404</b> - Not found
     * @param ptyID The ptyID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param cursor The cursor parameter
     * @param ticket The ticket parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> ptyConnect(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String ticket) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return ptyConnectRequestCreation(ptyID, directory, workspace, cursor, ticket).bodyToMono(localVarReturnType);
    }

    /**
     * Connect to PTY session
     * Establish a WebSocket connection to interact with a pseudo-terminal (PTY) session in real-time.
     * <p><b>200</b> - Connected session
     * <p><b>403</b> - Forbidden
     * <p><b>404</b> - Not found
     * @param ptyID The ptyID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param cursor The cursor parameter
     * @param ticket The ticket parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> ptyConnectWithHttpInfo(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String ticket) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return ptyConnectRequestCreation(ptyID, directory, workspace, cursor, ticket).toEntity(localVarReturnType);
    }

    /**
     * Connect to PTY session
     * Establish a WebSocket connection to interact with a pseudo-terminal (PTY) session in real-time.
     * <p><b>200</b> - Connected session
     * <p><b>403</b> - Forbidden
     * <p><b>404</b> - Not found
     * @param ptyID The ptyID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param cursor The cursor parameter
     * @param ticket The ticket parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec ptyConnectWithResponseSpec(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String ticket) throws WebClientResponseException {
        return ptyConnectRequestCreation(ptyID, directory, workspace, cursor, ticket);
    }

    public class PtyConnectTokenRequest {
        private @jakarta.annotation.Nonnull String ptyID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public PtyConnectTokenRequest() {}

        public PtyConnectTokenRequest(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.ptyID = ptyID;
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nonnull String ptyID() {
            return this.ptyID;
        }
        public PtyConnectTokenRequest ptyID(@jakarta.annotation.Nonnull String ptyID) {
            this.ptyID = ptyID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public PtyConnectTokenRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public PtyConnectTokenRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            PtyConnectTokenRequest request = (PtyConnectTokenRequest) o;
            return Objects.equals(this.ptyID, request.ptyID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(ptyID, directory, workspace);
        }
    }

    /**
     * Create PTY WebSocket token
     * Create a short-lived ticket for opening a PTY WebSocket connection.
     * <p><b>200</b> - WebSocket connect token
     * <p><b>400</b> - Bad request
     * <p><b>403</b> - PtyForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The ptyConnectToken request parameters as object
     * @return PtyTicketConnectToken
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<PtyTicketConnectToken> ptyConnectToken(PtyConnectTokenRequest requestParameters) throws WebClientResponseException {
        return this.ptyConnectToken(requestParameters.ptyID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Create PTY WebSocket token
     * Create a short-lived ticket for opening a PTY WebSocket connection.
     * <p><b>200</b> - WebSocket connect token
     * <p><b>400</b> - Bad request
     * <p><b>403</b> - PtyForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The ptyConnectToken request parameters as object
     * @return ResponseEntity&lt;PtyTicketConnectToken&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<PtyTicketConnectToken>> ptyConnectTokenWithHttpInfo(PtyConnectTokenRequest requestParameters) throws WebClientResponseException {
        return this.ptyConnectTokenWithHttpInfo(requestParameters.ptyID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Create PTY WebSocket token
     * Create a short-lived ticket for opening a PTY WebSocket connection.
     * <p><b>200</b> - WebSocket connect token
     * <p><b>400</b> - Bad request
     * <p><b>403</b> - PtyForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The ptyConnectToken request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec ptyConnectTokenWithResponseSpec(PtyConnectTokenRequest requestParameters) throws WebClientResponseException {
        return this.ptyConnectTokenWithResponseSpec(requestParameters.ptyID(), requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Create PTY WebSocket token
     * Create a short-lived ticket for opening a PTY WebSocket connection.
     * <p><b>200</b> - WebSocket connect token
     * <p><b>400</b> - Bad request
     * <p><b>403</b> - PtyForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return PtyTicketConnectToken
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec ptyConnectTokenRequestCreation(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'ptyID' is set
        if (ptyID == null) {
            throw new WebClientResponseException("Missing the required parameter 'ptyID' when calling ptyConnectToken", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("ptyID", ptyID);

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

        ParameterizedTypeReference<PtyTicketConnectToken> localVarReturnType = new ParameterizedTypeReference<PtyTicketConnectToken>() {};
        return apiClient.invokeAPI("/pty/{ptyID}/connect-token", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Create PTY WebSocket token
     * Create a short-lived ticket for opening a PTY WebSocket connection.
     * <p><b>200</b> - WebSocket connect token
     * <p><b>400</b> - Bad request
     * <p><b>403</b> - PtyForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return PtyTicketConnectToken
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<PtyTicketConnectToken> ptyConnectToken(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<PtyTicketConnectToken> localVarReturnType = new ParameterizedTypeReference<PtyTicketConnectToken>() {};
        return ptyConnectTokenRequestCreation(ptyID, directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Create PTY WebSocket token
     * Create a short-lived ticket for opening a PTY WebSocket connection.
     * <p><b>200</b> - WebSocket connect token
     * <p><b>400</b> - Bad request
     * <p><b>403</b> - PtyForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;PtyTicketConnectToken&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<PtyTicketConnectToken>> ptyConnectTokenWithHttpInfo(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<PtyTicketConnectToken> localVarReturnType = new ParameterizedTypeReference<PtyTicketConnectToken>() {};
        return ptyConnectTokenRequestCreation(ptyID, directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Create PTY WebSocket token
     * Create a short-lived ticket for opening a PTY WebSocket connection.
     * <p><b>200</b> - WebSocket connect token
     * <p><b>400</b> - Bad request
     * <p><b>403</b> - PtyForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec ptyConnectTokenWithResponseSpec(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return ptyConnectTokenRequestCreation(ptyID, directory, workspace);
    }

    public class PtyCreateRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable PtyCreateRequest ptyCreateRequest;

        public PtyCreateRequest() {}

        public PtyCreateRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable PtyCreateRequest ptyCreateRequest) {
            this.directory = directory;
            this.workspace = workspace;
            this.ptyCreateRequest = ptyCreateRequest;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public PtyCreateRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public PtyCreateRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable PtyCreateRequest ptyCreateRequest() {
            return this.ptyCreateRequest;
        }
        public PtyCreateRequest ptyCreateRequest(@jakarta.annotation.Nullable PtyCreateRequest ptyCreateRequest) {
            this.ptyCreateRequest = ptyCreateRequest;
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
            PtyCreateRequest request = (PtyCreateRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.ptyCreateRequest, request.ptyCreateRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace, ptyCreateRequest);
        }
    }

    /**
     * Create PTY session
     * Create a new pseudo-terminal (PTY) session for running shell commands and processes.
     * <p><b>200</b> - Created session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The ptyCreate request parameters as object
     * @return Pty
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Pty> ptyCreate(PtyCreateRequest requestParameters) throws WebClientResponseException {
        return this.ptyCreate(requestParameters.directory(), requestParameters.workspace(), requestParameters.ptyCreateRequest());
    }

    /**
     * Create PTY session
     * Create a new pseudo-terminal (PTY) session for running shell commands and processes.
     * <p><b>200</b> - Created session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The ptyCreate request parameters as object
     * @return ResponseEntity&lt;Pty&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Pty>> ptyCreateWithHttpInfo(PtyCreateRequest requestParameters) throws WebClientResponseException {
        return this.ptyCreateWithHttpInfo(requestParameters.directory(), requestParameters.workspace(), requestParameters.ptyCreateRequest());
    }

    /**
     * Create PTY session
     * Create a new pseudo-terminal (PTY) session for running shell commands and processes.
     * <p><b>200</b> - Created session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The ptyCreate request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec ptyCreateWithResponseSpec(PtyCreateRequest requestParameters) throws WebClientResponseException {
        return this.ptyCreateWithResponseSpec(requestParameters.directory(), requestParameters.workspace(), requestParameters.ptyCreateRequest());
    }


    /**
     * Create PTY session
     * Create a new pseudo-terminal (PTY) session for running shell commands and processes.
     * <p><b>200</b> - Created session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param ptyCreateRequest The ptyCreateRequest parameter
     * @return Pty
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec ptyCreateRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable PtyCreateRequest ptyCreateRequest) throws WebClientResponseException {
        Object postBody = ptyCreateRequest;
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

        ParameterizedTypeReference<Pty> localVarReturnType = new ParameterizedTypeReference<Pty>() {};
        return apiClient.invokeAPI("/pty", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Create PTY session
     * Create a new pseudo-terminal (PTY) session for running shell commands and processes.
     * <p><b>200</b> - Created session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param ptyCreateRequest The ptyCreateRequest parameter
     * @return Pty
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Pty> ptyCreate(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable PtyCreateRequest ptyCreateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Pty> localVarReturnType = new ParameterizedTypeReference<Pty>() {};
        return ptyCreateRequestCreation(directory, workspace, ptyCreateRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Create PTY session
     * Create a new pseudo-terminal (PTY) session for running shell commands and processes.
     * <p><b>200</b> - Created session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param ptyCreateRequest The ptyCreateRequest parameter
     * @return ResponseEntity&lt;Pty&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Pty>> ptyCreateWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable PtyCreateRequest ptyCreateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Pty> localVarReturnType = new ParameterizedTypeReference<Pty>() {};
        return ptyCreateRequestCreation(directory, workspace, ptyCreateRequest).toEntity(localVarReturnType);
    }

    /**
     * Create PTY session
     * Create a new pseudo-terminal (PTY) session for running shell commands and processes.
     * <p><b>200</b> - Created session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param ptyCreateRequest The ptyCreateRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec ptyCreateWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable PtyCreateRequest ptyCreateRequest) throws WebClientResponseException {
        return ptyCreateRequestCreation(directory, workspace, ptyCreateRequest);
    }

    public class PtyGetRequest {
        private @jakarta.annotation.Nonnull String ptyID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public PtyGetRequest() {}

        public PtyGetRequest(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.ptyID = ptyID;
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nonnull String ptyID() {
            return this.ptyID;
        }
        public PtyGetRequest ptyID(@jakarta.annotation.Nonnull String ptyID) {
            this.ptyID = ptyID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public PtyGetRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public PtyGetRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            PtyGetRequest request = (PtyGetRequest) o;
            return Objects.equals(this.ptyID, request.ptyID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(ptyID, directory, workspace);
        }
    }

    /**
     * Get PTY session
     * Retrieve detailed information about a specific pseudo-terminal (PTY) session.
     * <p><b>200</b> - Session info
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The ptyGet request parameters as object
     * @return Pty
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Pty> ptyGet(PtyGetRequest requestParameters) throws WebClientResponseException {
        return this.ptyGet(requestParameters.ptyID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get PTY session
     * Retrieve detailed information about a specific pseudo-terminal (PTY) session.
     * <p><b>200</b> - Session info
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The ptyGet request parameters as object
     * @return ResponseEntity&lt;Pty&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Pty>> ptyGetWithHttpInfo(PtyGetRequest requestParameters) throws WebClientResponseException {
        return this.ptyGetWithHttpInfo(requestParameters.ptyID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get PTY session
     * Retrieve detailed information about a specific pseudo-terminal (PTY) session.
     * <p><b>200</b> - Session info
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The ptyGet request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec ptyGetWithResponseSpec(PtyGetRequest requestParameters) throws WebClientResponseException {
        return this.ptyGetWithResponseSpec(requestParameters.ptyID(), requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Get PTY session
     * Retrieve detailed information about a specific pseudo-terminal (PTY) session.
     * <p><b>200</b> - Session info
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Pty
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec ptyGetRequestCreation(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'ptyID' is set
        if (ptyID == null) {
            throw new WebClientResponseException("Missing the required parameter 'ptyID' when calling ptyGet", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("ptyID", ptyID);

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

        ParameterizedTypeReference<Pty> localVarReturnType = new ParameterizedTypeReference<Pty>() {};
        return apiClient.invokeAPI("/pty/{ptyID}", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get PTY session
     * Retrieve detailed information about a specific pseudo-terminal (PTY) session.
     * <p><b>200</b> - Session info
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Pty
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Pty> ptyGet(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Pty> localVarReturnType = new ParameterizedTypeReference<Pty>() {};
        return ptyGetRequestCreation(ptyID, directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Get PTY session
     * Retrieve detailed information about a specific pseudo-terminal (PTY) session.
     * <p><b>200</b> - Session info
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Pty&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Pty>> ptyGetWithHttpInfo(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Pty> localVarReturnType = new ParameterizedTypeReference<Pty>() {};
        return ptyGetRequestCreation(ptyID, directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Get PTY session
     * Retrieve detailed information about a specific pseudo-terminal (PTY) session.
     * <p><b>200</b> - Session info
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec ptyGetWithResponseSpec(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return ptyGetRequestCreation(ptyID, directory, workspace);
    }

    public class PtyListRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public PtyListRequest() {}

        public PtyListRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public PtyListRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public PtyListRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            PtyListRequest request = (PtyListRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * List PTY sessions
     * Get a list of all active pseudo-terminal (PTY) sessions managed by OpenCode.
     * <p><b>200</b> - List of sessions
     * <p><b>400</b> - Bad request
     * @param requestParameters The ptyList request parameters as object
     * @return List&lt;Pty&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<Pty> ptyList(PtyListRequest requestParameters) throws WebClientResponseException {
        return this.ptyList(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List PTY sessions
     * Get a list of all active pseudo-terminal (PTY) sessions managed by OpenCode.
     * <p><b>200</b> - List of sessions
     * <p><b>400</b> - Bad request
     * @param requestParameters The ptyList request parameters as object
     * @return ResponseEntity&lt;List&lt;Pty&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<Pty>>> ptyListWithHttpInfo(PtyListRequest requestParameters) throws WebClientResponseException {
        return this.ptyListWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List PTY sessions
     * Get a list of all active pseudo-terminal (PTY) sessions managed by OpenCode.
     * <p><b>200</b> - List of sessions
     * <p><b>400</b> - Bad request
     * @param requestParameters The ptyList request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec ptyListWithResponseSpec(PtyListRequest requestParameters) throws WebClientResponseException {
        return this.ptyListWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * List PTY sessions
     * Get a list of all active pseudo-terminal (PTY) sessions managed by OpenCode.
     * <p><b>200</b> - List of sessions
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;Pty&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec ptyListRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<Pty> localVarReturnType = new ParameterizedTypeReference<Pty>() {};
        return apiClient.invokeAPI("/pty", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List PTY sessions
     * Get a list of all active pseudo-terminal (PTY) sessions managed by OpenCode.
     * <p><b>200</b> - List of sessions
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;Pty&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<Pty> ptyList(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Pty> localVarReturnType = new ParameterizedTypeReference<Pty>() {};
        return ptyListRequestCreation(directory, workspace).bodyToFlux(localVarReturnType);
    }

    /**
     * List PTY sessions
     * Get a list of all active pseudo-terminal (PTY) sessions managed by OpenCode.
     * <p><b>200</b> - List of sessions
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;List&lt;Pty&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<Pty>>> ptyListWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Pty> localVarReturnType = new ParameterizedTypeReference<Pty>() {};
        return ptyListRequestCreation(directory, workspace).toEntityList(localVarReturnType);
    }

    /**
     * List PTY sessions
     * Get a list of all active pseudo-terminal (PTY) sessions managed by OpenCode.
     * <p><b>200</b> - List of sessions
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec ptyListWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return ptyListRequestCreation(directory, workspace);
    }

    public class PtyRemoveRequest {
        private @jakarta.annotation.Nonnull String ptyID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public PtyRemoveRequest() {}

        public PtyRemoveRequest(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.ptyID = ptyID;
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nonnull String ptyID() {
            return this.ptyID;
        }
        public PtyRemoveRequest ptyID(@jakarta.annotation.Nonnull String ptyID) {
            this.ptyID = ptyID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public PtyRemoveRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public PtyRemoveRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            PtyRemoveRequest request = (PtyRemoveRequest) o;
            return Objects.equals(this.ptyID, request.ptyID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(ptyID, directory, workspace);
        }
    }

    /**
     * Remove PTY session
     * Remove and terminate a specific pseudo-terminal (PTY) session.
     * <p><b>200</b> - Session removed
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The ptyRemove request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> ptyRemove(PtyRemoveRequest requestParameters) throws WebClientResponseException {
        return this.ptyRemove(requestParameters.ptyID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Remove PTY session
     * Remove and terminate a specific pseudo-terminal (PTY) session.
     * <p><b>200</b> - Session removed
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The ptyRemove request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> ptyRemoveWithHttpInfo(PtyRemoveRequest requestParameters) throws WebClientResponseException {
        return this.ptyRemoveWithHttpInfo(requestParameters.ptyID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Remove PTY session
     * Remove and terminate a specific pseudo-terminal (PTY) session.
     * <p><b>200</b> - Session removed
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The ptyRemove request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec ptyRemoveWithResponseSpec(PtyRemoveRequest requestParameters) throws WebClientResponseException {
        return this.ptyRemoveWithResponseSpec(requestParameters.ptyID(), requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Remove PTY session
     * Remove and terminate a specific pseudo-terminal (PTY) session.
     * <p><b>200</b> - Session removed
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec ptyRemoveRequestCreation(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'ptyID' is set
        if (ptyID == null) {
            throw new WebClientResponseException("Missing the required parameter 'ptyID' when calling ptyRemove", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("ptyID", ptyID);

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
        return apiClient.invokeAPI("/pty/{ptyID}", HttpMethod.DELETE, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Remove PTY session
     * Remove and terminate a specific pseudo-terminal (PTY) session.
     * <p><b>200</b> - Session removed
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> ptyRemove(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return ptyRemoveRequestCreation(ptyID, directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Remove PTY session
     * Remove and terminate a specific pseudo-terminal (PTY) session.
     * <p><b>200</b> - Session removed
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> ptyRemoveWithHttpInfo(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return ptyRemoveRequestCreation(ptyID, directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Remove PTY session
     * Remove and terminate a specific pseudo-terminal (PTY) session.
     * <p><b>200</b> - Session removed
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec ptyRemoveWithResponseSpec(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return ptyRemoveRequestCreation(ptyID, directory, workspace);
    }

    public class PtyShellsRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public PtyShellsRequest() {}

        public PtyShellsRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public PtyShellsRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public PtyShellsRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            PtyShellsRequest request = (PtyShellsRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * List available shells
     * Get a list of available shells on the system.
     * <p><b>200</b> - List of shells
     * <p><b>400</b> - Bad request
     * @param requestParameters The ptyShells request parameters as object
     * @return List&lt;PtyShells200ResponseInner&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<PtyShells200ResponseInner> ptyShells(PtyShellsRequest requestParameters) throws WebClientResponseException {
        return this.ptyShells(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List available shells
     * Get a list of available shells on the system.
     * <p><b>200</b> - List of shells
     * <p><b>400</b> - Bad request
     * @param requestParameters The ptyShells request parameters as object
     * @return ResponseEntity&lt;List&lt;PtyShells200ResponseInner&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<PtyShells200ResponseInner>>> ptyShellsWithHttpInfo(PtyShellsRequest requestParameters) throws WebClientResponseException {
        return this.ptyShellsWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List available shells
     * Get a list of available shells on the system.
     * <p><b>200</b> - List of shells
     * <p><b>400</b> - Bad request
     * @param requestParameters The ptyShells request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec ptyShellsWithResponseSpec(PtyShellsRequest requestParameters) throws WebClientResponseException {
        return this.ptyShellsWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * List available shells
     * Get a list of available shells on the system.
     * <p><b>200</b> - List of shells
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;PtyShells200ResponseInner&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec ptyShellsRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<PtyShells200ResponseInner> localVarReturnType = new ParameterizedTypeReference<PtyShells200ResponseInner>() {};
        return apiClient.invokeAPI("/pty/shells", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List available shells
     * Get a list of available shells on the system.
     * <p><b>200</b> - List of shells
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;PtyShells200ResponseInner&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<PtyShells200ResponseInner> ptyShells(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<PtyShells200ResponseInner> localVarReturnType = new ParameterizedTypeReference<PtyShells200ResponseInner>() {};
        return ptyShellsRequestCreation(directory, workspace).bodyToFlux(localVarReturnType);
    }

    /**
     * List available shells
     * Get a list of available shells on the system.
     * <p><b>200</b> - List of shells
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;List&lt;PtyShells200ResponseInner&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<PtyShells200ResponseInner>>> ptyShellsWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<PtyShells200ResponseInner> localVarReturnType = new ParameterizedTypeReference<PtyShells200ResponseInner>() {};
        return ptyShellsRequestCreation(directory, workspace).toEntityList(localVarReturnType);
    }

    /**
     * List available shells
     * Get a list of available shells on the system.
     * <p><b>200</b> - List of shells
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec ptyShellsWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return ptyShellsRequestCreation(directory, workspace);
    }

    public class PtyUpdateRequest {
        private @jakarta.annotation.Nonnull String ptyID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable PtyUpdateRequest ptyUpdateRequest;

        public PtyUpdateRequest() {}

        public PtyUpdateRequest(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable PtyUpdateRequest ptyUpdateRequest) {
            this.ptyID = ptyID;
            this.directory = directory;
            this.workspace = workspace;
            this.ptyUpdateRequest = ptyUpdateRequest;
        }

        public @jakarta.annotation.Nonnull String ptyID() {
            return this.ptyID;
        }
        public PtyUpdateRequest ptyID(@jakarta.annotation.Nonnull String ptyID) {
            this.ptyID = ptyID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public PtyUpdateRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public PtyUpdateRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable PtyUpdateRequest ptyUpdateRequest() {
            return this.ptyUpdateRequest;
        }
        public PtyUpdateRequest ptyUpdateRequest(@jakarta.annotation.Nullable PtyUpdateRequest ptyUpdateRequest) {
            this.ptyUpdateRequest = ptyUpdateRequest;
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
            PtyUpdateRequest request = (PtyUpdateRequest) o;
            return Objects.equals(this.ptyID, request.ptyID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.ptyUpdateRequest, request.ptyUpdateRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(ptyID, directory, workspace, ptyUpdateRequest);
        }
    }

    /**
     * Update PTY session
     * Update properties of an existing pseudo-terminal (PTY) session.
     * <p><b>200</b> - Updated session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The ptyUpdate request parameters as object
     * @return Pty
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Pty> ptyUpdate(PtyUpdateRequest requestParameters) throws WebClientResponseException {
        return this.ptyUpdate(requestParameters.ptyID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.ptyUpdateRequest());
    }

    /**
     * Update PTY session
     * Update properties of an existing pseudo-terminal (PTY) session.
     * <p><b>200</b> - Updated session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The ptyUpdate request parameters as object
     * @return ResponseEntity&lt;Pty&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Pty>> ptyUpdateWithHttpInfo(PtyUpdateRequest requestParameters) throws WebClientResponseException {
        return this.ptyUpdateWithHttpInfo(requestParameters.ptyID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.ptyUpdateRequest());
    }

    /**
     * Update PTY session
     * Update properties of an existing pseudo-terminal (PTY) session.
     * <p><b>200</b> - Updated session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The ptyUpdate request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec ptyUpdateWithResponseSpec(PtyUpdateRequest requestParameters) throws WebClientResponseException {
        return this.ptyUpdateWithResponseSpec(requestParameters.ptyID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.ptyUpdateRequest());
    }


    /**
     * Update PTY session
     * Update properties of an existing pseudo-terminal (PTY) session.
     * <p><b>200</b> - Updated session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param ptyUpdateRequest The ptyUpdateRequest parameter
     * @return Pty
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec ptyUpdateRequestCreation(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable PtyUpdateRequest ptyUpdateRequest) throws WebClientResponseException {
        Object postBody = ptyUpdateRequest;
        // verify the required parameter 'ptyID' is set
        if (ptyID == null) {
            throw new WebClientResponseException("Missing the required parameter 'ptyID' when calling ptyUpdate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("ptyID", ptyID);

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

        ParameterizedTypeReference<Pty> localVarReturnType = new ParameterizedTypeReference<Pty>() {};
        return apiClient.invokeAPI("/pty/{ptyID}", HttpMethod.PUT, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Update PTY session
     * Update properties of an existing pseudo-terminal (PTY) session.
     * <p><b>200</b> - Updated session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param ptyUpdateRequest The ptyUpdateRequest parameter
     * @return Pty
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Pty> ptyUpdate(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable PtyUpdateRequest ptyUpdateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Pty> localVarReturnType = new ParameterizedTypeReference<Pty>() {};
        return ptyUpdateRequestCreation(ptyID, directory, workspace, ptyUpdateRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Update PTY session
     * Update properties of an existing pseudo-terminal (PTY) session.
     * <p><b>200</b> - Updated session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param ptyUpdateRequest The ptyUpdateRequest parameter
     * @return ResponseEntity&lt;Pty&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Pty>> ptyUpdateWithHttpInfo(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable PtyUpdateRequest ptyUpdateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Pty> localVarReturnType = new ParameterizedTypeReference<Pty>() {};
        return ptyUpdateRequestCreation(ptyID, directory, workspace, ptyUpdateRequest).toEntity(localVarReturnType);
    }

    /**
     * Update PTY session
     * Update properties of an existing pseudo-terminal (PTY) session.
     * <p><b>200</b> - Updated session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param ptyUpdateRequest The ptyUpdateRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec ptyUpdateWithResponseSpec(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable PtyUpdateRequest ptyUpdateRequest) throws WebClientResponseException {
        return ptyUpdateRequestCreation(ptyID, directory, workspace, ptyUpdateRequest);
    }

    public class V2PtyConnectRequest {
        private @jakarta.annotation.Nonnull String ptyID;
        private @jakarta.annotation.Nullable String locationDirectory;
        private @jakarta.annotation.Nullable String locationWorkspace;
        private @jakarta.annotation.Nullable String cursor;
        private @jakarta.annotation.Nullable String ticket;

        public V2PtyConnectRequest() {}

        public V2PtyConnectRequest(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String locationDirectory, @jakarta.annotation.Nullable String locationWorkspace, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String ticket) {
            this.ptyID = ptyID;
            this.locationDirectory = locationDirectory;
            this.locationWorkspace = locationWorkspace;
            this.cursor = cursor;
            this.ticket = ticket;
        }

        public @jakarta.annotation.Nonnull String ptyID() {
            return this.ptyID;
        }
        public V2PtyConnectRequest ptyID(@jakarta.annotation.Nonnull String ptyID) {
            this.ptyID = ptyID;
            return this;
        }

        public @jakarta.annotation.Nullable String locationDirectory() {
            return this.locationDirectory;
        }
        public V2PtyConnectRequest locationDirectory(@jakarta.annotation.Nullable String locationDirectory) {
            this.locationDirectory = locationDirectory;
            return this;
        }

        public @jakarta.annotation.Nullable String locationWorkspace() {
            return this.locationWorkspace;
        }
        public V2PtyConnectRequest locationWorkspace(@jakarta.annotation.Nullable String locationWorkspace) {
            this.locationWorkspace = locationWorkspace;
            return this;
        }

        public @jakarta.annotation.Nullable String cursor() {
            return this.cursor;
        }
        public V2PtyConnectRequest cursor(@jakarta.annotation.Nullable String cursor) {
            this.cursor = cursor;
            return this;
        }

        public @jakarta.annotation.Nullable String ticket() {
            return this.ticket;
        }
        public V2PtyConnectRequest ticket(@jakarta.annotation.Nullable String ticket) {
            this.ticket = ticket;
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
            V2PtyConnectRequest request = (V2PtyConnectRequest) o;
            return Objects.equals(this.ptyID, request.ptyID()) &&
                Objects.equals(this.locationDirectory, request.locationDirectory()) &&
                Objects.equals(this.locationWorkspace, request.locationWorkspace()) &&
                Objects.equals(this.cursor, request.cursor()) &&
                Objects.equals(this.ticket, request.ticket());
        }

        @Override
        public int hashCode() {
            return Objects.hash(ptyID, locationDirectory, locationWorkspace, cursor, ticket);
        }
    }

    /**
     * Connect to PTY session
     * Establish a WebSocket connection streaming PTY output and accepting terminal input.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The v2PtyConnect request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> v2PtyConnect(V2PtyConnectRequest requestParameters) throws WebClientResponseException {
        return this.v2PtyConnect(requestParameters.ptyID(), requestParameters.locationDirectory(), requestParameters.locationWorkspace(), requestParameters.cursor(), requestParameters.ticket());
    }

    /**
     * Connect to PTY session
     * Establish a WebSocket connection streaming PTY output and accepting terminal input.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The v2PtyConnect request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> v2PtyConnectWithHttpInfo(V2PtyConnectRequest requestParameters) throws WebClientResponseException {
        return this.v2PtyConnectWithHttpInfo(requestParameters.ptyID(), requestParameters.locationDirectory(), requestParameters.locationWorkspace(), requestParameters.cursor(), requestParameters.ticket());
    }

    /**
     * Connect to PTY session
     * Establish a WebSocket connection streaming PTY output and accepting terminal input.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The v2PtyConnect request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2PtyConnectWithResponseSpec(V2PtyConnectRequest requestParameters) throws WebClientResponseException {
        return this.v2PtyConnectWithResponseSpec(requestParameters.ptyID(), requestParameters.locationDirectory(), requestParameters.locationWorkspace(), requestParameters.cursor(), requestParameters.ticket());
    }


    /**
     * Connect to PTY session
     * Establish a WebSocket connection streaming PTY output and accepting terminal input.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param locationDirectory The locationDirectory parameter
     * @param locationWorkspace The locationWorkspace parameter
     * @param cursor The cursor parameter
     * @param ticket The ticket parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2PtyConnectRequestCreation(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String locationDirectory, @jakarta.annotation.Nullable String locationWorkspace, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String ticket) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'ptyID' is set
        if (ptyID == null) {
            throw new WebClientResponseException("Missing the required parameter 'ptyID' when calling v2PtyConnect", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("ptyID", ptyID);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "location[directory]", locationDirectory));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "location[workspace]", locationWorkspace));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "cursor", cursor));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "ticket", ticket));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return apiClient.invokeAPI("/api/pty/{ptyID}/connect", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Connect to PTY session
     * Establish a WebSocket connection streaming PTY output and accepting terminal input.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param locationDirectory The locationDirectory parameter
     * @param locationWorkspace The locationWorkspace parameter
     * @param cursor The cursor parameter
     * @param ticket The ticket parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> v2PtyConnect(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String locationDirectory, @jakarta.annotation.Nullable String locationWorkspace, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String ticket) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return v2PtyConnectRequestCreation(ptyID, locationDirectory, locationWorkspace, cursor, ticket).bodyToMono(localVarReturnType);
    }

    /**
     * Connect to PTY session
     * Establish a WebSocket connection streaming PTY output and accepting terminal input.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param locationDirectory The locationDirectory parameter
     * @param locationWorkspace The locationWorkspace parameter
     * @param cursor The cursor parameter
     * @param ticket The ticket parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> v2PtyConnectWithHttpInfo(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String locationDirectory, @jakarta.annotation.Nullable String locationWorkspace, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String ticket) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return v2PtyConnectRequestCreation(ptyID, locationDirectory, locationWorkspace, cursor, ticket).toEntity(localVarReturnType);
    }

    /**
     * Connect to PTY session
     * Establish a WebSocket connection streaming PTY output and accepting terminal input.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param locationDirectory The locationDirectory parameter
     * @param locationWorkspace The locationWorkspace parameter
     * @param cursor The cursor parameter
     * @param ticket The ticket parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2PtyConnectWithResponseSpec(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String locationDirectory, @jakarta.annotation.Nullable String locationWorkspace, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String ticket) throws WebClientResponseException {
        return v2PtyConnectRequestCreation(ptyID, locationDirectory, locationWorkspace, cursor, ticket);
    }

    public class V2PtyConnectTokenRequest {
        private @jakarta.annotation.Nonnull String ptyID;
        private @jakarta.annotation.Nullable V2AgentListLocationParameter location;

        public V2PtyConnectTokenRequest() {}

        public V2PtyConnectTokenRequest(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) {
            this.ptyID = ptyID;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String ptyID() {
            return this.ptyID;
        }
        public V2PtyConnectTokenRequest ptyID(@jakarta.annotation.Nonnull String ptyID) {
            this.ptyID = ptyID;
            return this;
        }

        public @jakarta.annotation.Nullable V2AgentListLocationParameter location() {
            return this.location;
        }
        public V2PtyConnectTokenRequest location(@jakarta.annotation.Nullable V2AgentListLocationParameter location) {
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
            V2PtyConnectTokenRequest request = (V2PtyConnectTokenRequest) o;
            return Objects.equals(this.ptyID, request.ptyID()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(ptyID, location);
        }
    }

    /**
     * Create PTY WebSocket token
     * Create a short-lived single-use ticket for opening a PTY WebSocket connection.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The v2PtyConnectToken request parameters as object
     * @return V2PtyConnectToken200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2PtyConnectToken200Response> v2PtyConnectToken(V2PtyConnectTokenRequest requestParameters) throws WebClientResponseException {
        return this.v2PtyConnectToken(requestParameters.ptyID(), requestParameters.location());
    }

    /**
     * Create PTY WebSocket token
     * Create a short-lived single-use ticket for opening a PTY WebSocket connection.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The v2PtyConnectToken request parameters as object
     * @return ResponseEntity&lt;V2PtyConnectToken200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2PtyConnectToken200Response>> v2PtyConnectTokenWithHttpInfo(V2PtyConnectTokenRequest requestParameters) throws WebClientResponseException {
        return this.v2PtyConnectTokenWithHttpInfo(requestParameters.ptyID(), requestParameters.location());
    }

    /**
     * Create PTY WebSocket token
     * Create a short-lived single-use ticket for opening a PTY WebSocket connection.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The v2PtyConnectToken request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2PtyConnectTokenWithResponseSpec(V2PtyConnectTokenRequest requestParameters) throws WebClientResponseException {
        return this.v2PtyConnectTokenWithResponseSpec(requestParameters.ptyID(), requestParameters.location());
    }


    /**
     * Create PTY WebSocket token
     * Create a short-lived single-use ticket for opening a PTY WebSocket connection.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param location The location parameter
     * @return V2PtyConnectToken200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2PtyConnectTokenRequestCreation(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'ptyID' is set
        if (ptyID == null) {
            throw new WebClientResponseException("Missing the required parameter 'ptyID' when calling v2PtyConnectToken", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("ptyID", ptyID);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", location.getWorkspace()));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<V2PtyConnectToken200Response> localVarReturnType = new ParameterizedTypeReference<V2PtyConnectToken200Response>() {};
        return apiClient.invokeAPI("/api/pty/{ptyID}/connect-token", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Create PTY WebSocket token
     * Create a short-lived single-use ticket for opening a PTY WebSocket connection.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param location The location parameter
     * @return V2PtyConnectToken200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2PtyConnectToken200Response> v2PtyConnectToken(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2PtyConnectToken200Response> localVarReturnType = new ParameterizedTypeReference<V2PtyConnectToken200Response>() {};
        return v2PtyConnectTokenRequestCreation(ptyID, location).bodyToMono(localVarReturnType);
    }

    /**
     * Create PTY WebSocket token
     * Create a short-lived single-use ticket for opening a PTY WebSocket connection.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param location The location parameter
     * @return ResponseEntity&lt;V2PtyConnectToken200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2PtyConnectToken200Response>> v2PtyConnectTokenWithHttpInfo(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2PtyConnectToken200Response> localVarReturnType = new ParameterizedTypeReference<V2PtyConnectToken200Response>() {};
        return v2PtyConnectTokenRequestCreation(ptyID, location).toEntity(localVarReturnType);
    }

    /**
     * Create PTY WebSocket token
     * Create a short-lived single-use ticket for opening a PTY WebSocket connection.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2PtyConnectTokenWithResponseSpec(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        return v2PtyConnectTokenRequestCreation(ptyID, location);
    }

    public class V2PtyCreateRequest {
        private @jakarta.annotation.Nonnull PtyCreateRequest ptyCreateRequest;
        private @jakarta.annotation.Nullable V2AgentListLocationParameter location;

        public V2PtyCreateRequest() {}

        public V2PtyCreateRequest(@jakarta.annotation.Nonnull PtyCreateRequest ptyCreateRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) {
            this.ptyCreateRequest = ptyCreateRequest;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull PtyCreateRequest ptyCreateRequest() {
            return this.ptyCreateRequest;
        }
        public V2PtyCreateRequest ptyCreateRequest(@jakarta.annotation.Nonnull PtyCreateRequest ptyCreateRequest) {
            this.ptyCreateRequest = ptyCreateRequest;
            return this;
        }

        public @jakarta.annotation.Nullable V2AgentListLocationParameter location() {
            return this.location;
        }
        public V2PtyCreateRequest location(@jakarta.annotation.Nullable V2AgentListLocationParameter location) {
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
            V2PtyCreateRequest request = (V2PtyCreateRequest) o;
            return Objects.equals(this.ptyCreateRequest, request.ptyCreateRequest()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(ptyCreateRequest, location);
        }
    }

    /**
     * Create PTY session
     * Create a pseudo-terminal session for a location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2PtyCreate request parameters as object
     * @return V2PtyCreate200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2PtyCreate200Response> v2PtyCreate(V2PtyCreateRequest requestParameters) throws WebClientResponseException {
        return this.v2PtyCreate(requestParameters.ptyCreateRequest(), requestParameters.location());
    }

    /**
     * Create PTY session
     * Create a pseudo-terminal session for a location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2PtyCreate request parameters as object
     * @return ResponseEntity&lt;V2PtyCreate200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2PtyCreate200Response>> v2PtyCreateWithHttpInfo(V2PtyCreateRequest requestParameters) throws WebClientResponseException {
        return this.v2PtyCreateWithHttpInfo(requestParameters.ptyCreateRequest(), requestParameters.location());
    }

    /**
     * Create PTY session
     * Create a pseudo-terminal session for a location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2PtyCreate request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2PtyCreateWithResponseSpec(V2PtyCreateRequest requestParameters) throws WebClientResponseException {
        return this.v2PtyCreateWithResponseSpec(requestParameters.ptyCreateRequest(), requestParameters.location());
    }


    /**
     * Create PTY session
     * Create a pseudo-terminal session for a location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param ptyCreateRequest The ptyCreateRequest parameter
     * @param location The location parameter
     * @return V2PtyCreate200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2PtyCreateRequestCreation(@jakarta.annotation.Nonnull PtyCreateRequest ptyCreateRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = ptyCreateRequest;
        // verify the required parameter 'ptyCreateRequest' is set
        if (ptyCreateRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'ptyCreateRequest' when calling v2PtyCreate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", location.getWorkspace()));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<V2PtyCreate200Response> localVarReturnType = new ParameterizedTypeReference<V2PtyCreate200Response>() {};
        return apiClient.invokeAPI("/api/pty", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Create PTY session
     * Create a pseudo-terminal session for a location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param ptyCreateRequest The ptyCreateRequest parameter
     * @param location The location parameter
     * @return V2PtyCreate200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2PtyCreate200Response> v2PtyCreate(@jakarta.annotation.Nonnull PtyCreateRequest ptyCreateRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2PtyCreate200Response> localVarReturnType = new ParameterizedTypeReference<V2PtyCreate200Response>() {};
        return v2PtyCreateRequestCreation(ptyCreateRequest, location).bodyToMono(localVarReturnType);
    }

    /**
     * Create PTY session
     * Create a pseudo-terminal session for a location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param ptyCreateRequest The ptyCreateRequest parameter
     * @param location The location parameter
     * @return ResponseEntity&lt;V2PtyCreate200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2PtyCreate200Response>> v2PtyCreateWithHttpInfo(@jakarta.annotation.Nonnull PtyCreateRequest ptyCreateRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2PtyCreate200Response> localVarReturnType = new ParameterizedTypeReference<V2PtyCreate200Response>() {};
        return v2PtyCreateRequestCreation(ptyCreateRequest, location).toEntity(localVarReturnType);
    }

    /**
     * Create PTY session
     * Create a pseudo-terminal session for a location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param ptyCreateRequest The ptyCreateRequest parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2PtyCreateWithResponseSpec(@jakarta.annotation.Nonnull PtyCreateRequest ptyCreateRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        return v2PtyCreateRequestCreation(ptyCreateRequest, location);
    }

    public class V2PtyGetRequest {
        private @jakarta.annotation.Nonnull String ptyID;
        private @jakarta.annotation.Nullable V2AgentListLocationParameter location;

        public V2PtyGetRequest() {}

        public V2PtyGetRequest(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) {
            this.ptyID = ptyID;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String ptyID() {
            return this.ptyID;
        }
        public V2PtyGetRequest ptyID(@jakarta.annotation.Nonnull String ptyID) {
            this.ptyID = ptyID;
            return this;
        }

        public @jakarta.annotation.Nullable V2AgentListLocationParameter location() {
            return this.location;
        }
        public V2PtyGetRequest location(@jakarta.annotation.Nullable V2AgentListLocationParameter location) {
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
            V2PtyGetRequest request = (V2PtyGetRequest) o;
            return Objects.equals(this.ptyID, request.ptyID()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(ptyID, location);
        }
    }

    /**
     * Get PTY session
     * Get one PTY session, including its exit code once exited.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The v2PtyGet request parameters as object
     * @return V2PtyGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2PtyGet200Response> v2PtyGet(V2PtyGetRequest requestParameters) throws WebClientResponseException {
        return this.v2PtyGet(requestParameters.ptyID(), requestParameters.location());
    }

    /**
     * Get PTY session
     * Get one PTY session, including its exit code once exited.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The v2PtyGet request parameters as object
     * @return ResponseEntity&lt;V2PtyGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2PtyGet200Response>> v2PtyGetWithHttpInfo(V2PtyGetRequest requestParameters) throws WebClientResponseException {
        return this.v2PtyGetWithHttpInfo(requestParameters.ptyID(), requestParameters.location());
    }

    /**
     * Get PTY session
     * Get one PTY session, including its exit code once exited.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The v2PtyGet request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2PtyGetWithResponseSpec(V2PtyGetRequest requestParameters) throws WebClientResponseException {
        return this.v2PtyGetWithResponseSpec(requestParameters.ptyID(), requestParameters.location());
    }


    /**
     * Get PTY session
     * Get one PTY session, including its exit code once exited.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param location The location parameter
     * @return V2PtyGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2PtyGetRequestCreation(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'ptyID' is set
        if (ptyID == null) {
            throw new WebClientResponseException("Missing the required parameter 'ptyID' when calling v2PtyGet", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("ptyID", ptyID);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", location.getWorkspace()));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<V2PtyGet200Response> localVarReturnType = new ParameterizedTypeReference<V2PtyGet200Response>() {};
        return apiClient.invokeAPI("/api/pty/{ptyID}", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get PTY session
     * Get one PTY session, including its exit code once exited.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param location The location parameter
     * @return V2PtyGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2PtyGet200Response> v2PtyGet(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2PtyGet200Response> localVarReturnType = new ParameterizedTypeReference<V2PtyGet200Response>() {};
        return v2PtyGetRequestCreation(ptyID, location).bodyToMono(localVarReturnType);
    }

    /**
     * Get PTY session
     * Get one PTY session, including its exit code once exited.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param location The location parameter
     * @return ResponseEntity&lt;V2PtyGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2PtyGet200Response>> v2PtyGetWithHttpInfo(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2PtyGet200Response> localVarReturnType = new ParameterizedTypeReference<V2PtyGet200Response>() {};
        return v2PtyGetRequestCreation(ptyID, location).toEntity(localVarReturnType);
    }

    /**
     * Get PTY session
     * Get one PTY session, including its exit code once exited.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2PtyGetWithResponseSpec(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        return v2PtyGetRequestCreation(ptyID, location);
    }

    /**
     * List PTY sessions
     * List PTY sessions for a location, including exited sessions retained until removal.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return V2PtyList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2PtyListRequestCreation(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = null;
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", location.getWorkspace()));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<V2PtyList200Response> localVarReturnType = new ParameterizedTypeReference<V2PtyList200Response>() {};
        return apiClient.invokeAPI("/api/pty", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List PTY sessions
     * List PTY sessions for a location, including exited sessions retained until removal.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return V2PtyList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2PtyList200Response> v2PtyList(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2PtyList200Response> localVarReturnType = new ParameterizedTypeReference<V2PtyList200Response>() {};
        return v2PtyListRequestCreation(location).bodyToMono(localVarReturnType);
    }

    /**
     * List PTY sessions
     * List PTY sessions for a location, including exited sessions retained until removal.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseEntity&lt;V2PtyList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2PtyList200Response>> v2PtyListWithHttpInfo(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2PtyList200Response> localVarReturnType = new ParameterizedTypeReference<V2PtyList200Response>() {};
        return v2PtyListRequestCreation(location).toEntity(localVarReturnType);
    }

    /**
     * List PTY sessions
     * List PTY sessions for a location, including exited sessions retained until removal.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2PtyListWithResponseSpec(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        return v2PtyListRequestCreation(location);
    }

    public class V2PtyRemoveRequest {
        private @jakarta.annotation.Nonnull String ptyID;
        private @jakarta.annotation.Nullable V2AgentListLocationParameter location;

        public V2PtyRemoveRequest() {}

        public V2PtyRemoveRequest(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) {
            this.ptyID = ptyID;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String ptyID() {
            return this.ptyID;
        }
        public V2PtyRemoveRequest ptyID(@jakarta.annotation.Nonnull String ptyID) {
            this.ptyID = ptyID;
            return this;
        }

        public @jakarta.annotation.Nullable V2AgentListLocationParameter location() {
            return this.location;
        }
        public V2PtyRemoveRequest location(@jakarta.annotation.Nullable V2AgentListLocationParameter location) {
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
            V2PtyRemoveRequest request = (V2PtyRemoveRequest) o;
            return Objects.equals(this.ptyID, request.ptyID()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(ptyID, location);
        }
    }

    /**
     * Remove PTY session
     * Terminate and remove one PTY session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The v2PtyRemove request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2PtyRemove(V2PtyRemoveRequest requestParameters) throws WebClientResponseException {
        return this.v2PtyRemove(requestParameters.ptyID(), requestParameters.location());
    }

    /**
     * Remove PTY session
     * Terminate and remove one PTY session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The v2PtyRemove request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2PtyRemoveWithHttpInfo(V2PtyRemoveRequest requestParameters) throws WebClientResponseException {
        return this.v2PtyRemoveWithHttpInfo(requestParameters.ptyID(), requestParameters.location());
    }

    /**
     * Remove PTY session
     * Terminate and remove one PTY session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The v2PtyRemove request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2PtyRemoveWithResponseSpec(V2PtyRemoveRequest requestParameters) throws WebClientResponseException {
        return this.v2PtyRemoveWithResponseSpec(requestParameters.ptyID(), requestParameters.location());
    }


    /**
     * Remove PTY session
     * Terminate and remove one PTY session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2PtyRemoveRequestCreation(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'ptyID' is set
        if (ptyID == null) {
            throw new WebClientResponseException("Missing the required parameter 'ptyID' when calling v2PtyRemove", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("ptyID", ptyID);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", location.getWorkspace()));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/pty/{ptyID}", HttpMethod.DELETE, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Remove PTY session
     * Terminate and remove one PTY session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2PtyRemove(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2PtyRemoveRequestCreation(ptyID, location).bodyToMono(localVarReturnType);
    }

    /**
     * Remove PTY session
     * Terminate and remove one PTY session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2PtyRemoveWithHttpInfo(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2PtyRemoveRequestCreation(ptyID, location).toEntity(localVarReturnType);
    }

    /**
     * Remove PTY session
     * Terminate and remove one PTY session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2PtyRemoveWithResponseSpec(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        return v2PtyRemoveRequestCreation(ptyID, location);
    }

    public class V2PtyUpdateRequest {
        private @jakarta.annotation.Nonnull String ptyID;
        private @jakarta.annotation.Nonnull PtyUpdateRequest ptyUpdateRequest;
        private @jakarta.annotation.Nullable V2AgentListLocationParameter location;

        public V2PtyUpdateRequest() {}

        public V2PtyUpdateRequest(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nonnull PtyUpdateRequest ptyUpdateRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) {
            this.ptyID = ptyID;
            this.ptyUpdateRequest = ptyUpdateRequest;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String ptyID() {
            return this.ptyID;
        }
        public V2PtyUpdateRequest ptyID(@jakarta.annotation.Nonnull String ptyID) {
            this.ptyID = ptyID;
            return this;
        }

        public @jakarta.annotation.Nonnull PtyUpdateRequest ptyUpdateRequest() {
            return this.ptyUpdateRequest;
        }
        public V2PtyUpdateRequest ptyUpdateRequest(@jakarta.annotation.Nonnull PtyUpdateRequest ptyUpdateRequest) {
            this.ptyUpdateRequest = ptyUpdateRequest;
            return this;
        }

        public @jakarta.annotation.Nullable V2AgentListLocationParameter location() {
            return this.location;
        }
        public V2PtyUpdateRequest location(@jakarta.annotation.Nullable V2AgentListLocationParameter location) {
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
            V2PtyUpdateRequest request = (V2PtyUpdateRequest) o;
            return Objects.equals(this.ptyID, request.ptyID()) &&
                Objects.equals(this.ptyUpdateRequest, request.ptyUpdateRequest()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(ptyID, ptyUpdateRequest, location);
        }
    }

    /**
     * Update PTY session
     * Update the title or viewport size of one PTY session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The v2PtyUpdate request parameters as object
     * @return V2PtyGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2PtyGet200Response> v2PtyUpdate(V2PtyUpdateRequest requestParameters) throws WebClientResponseException {
        return this.v2PtyUpdate(requestParameters.ptyID(), requestParameters.ptyUpdateRequest(), requestParameters.location());
    }

    /**
     * Update PTY session
     * Update the title or viewport size of one PTY session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The v2PtyUpdate request parameters as object
     * @return ResponseEntity&lt;V2PtyGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2PtyGet200Response>> v2PtyUpdateWithHttpInfo(V2PtyUpdateRequest requestParameters) throws WebClientResponseException {
        return this.v2PtyUpdateWithHttpInfo(requestParameters.ptyID(), requestParameters.ptyUpdateRequest(), requestParameters.location());
    }

    /**
     * Update PTY session
     * Update the title or viewport size of one PTY session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The v2PtyUpdate request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2PtyUpdateWithResponseSpec(V2PtyUpdateRequest requestParameters) throws WebClientResponseException {
        return this.v2PtyUpdateWithResponseSpec(requestParameters.ptyID(), requestParameters.ptyUpdateRequest(), requestParameters.location());
    }


    /**
     * Update PTY session
     * Update the title or viewport size of one PTY session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param ptyUpdateRequest The ptyUpdateRequest parameter
     * @param location The location parameter
     * @return V2PtyGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2PtyUpdateRequestCreation(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nonnull PtyUpdateRequest ptyUpdateRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = ptyUpdateRequest;
        // verify the required parameter 'ptyID' is set
        if (ptyID == null) {
            throw new WebClientResponseException("Missing the required parameter 'ptyID' when calling v2PtyUpdate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'ptyUpdateRequest' is set
        if (ptyUpdateRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'ptyUpdateRequest' when calling v2PtyUpdate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("ptyID", ptyID);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", location.getWorkspace()));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<V2PtyGet200Response> localVarReturnType = new ParameterizedTypeReference<V2PtyGet200Response>() {};
        return apiClient.invokeAPI("/api/pty/{ptyID}", HttpMethod.PUT, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Update PTY session
     * Update the title or viewport size of one PTY session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param ptyUpdateRequest The ptyUpdateRequest parameter
     * @param location The location parameter
     * @return V2PtyGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2PtyGet200Response> v2PtyUpdate(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nonnull PtyUpdateRequest ptyUpdateRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2PtyGet200Response> localVarReturnType = new ParameterizedTypeReference<V2PtyGet200Response>() {};
        return v2PtyUpdateRequestCreation(ptyID, ptyUpdateRequest, location).bodyToMono(localVarReturnType);
    }

    /**
     * Update PTY session
     * Update the title or viewport size of one PTY session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param ptyUpdateRequest The ptyUpdateRequest parameter
     * @param location The location parameter
     * @return ResponseEntity&lt;V2PtyGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2PtyGet200Response>> v2PtyUpdateWithHttpInfo(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nonnull PtyUpdateRequest ptyUpdateRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2PtyGet200Response> localVarReturnType = new ParameterizedTypeReference<V2PtyGet200Response>() {};
        return v2PtyUpdateRequestCreation(ptyID, ptyUpdateRequest, location).toEntity(localVarReturnType);
    }

    /**
     * Update PTY session
     * Update the title or viewport size of one PTY session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * @param ptyID The ptyID parameter
     * @param ptyUpdateRequest The ptyUpdateRequest parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2PtyUpdateWithResponseSpec(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nonnull PtyUpdateRequest ptyUpdateRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        return v2PtyUpdateRequestCreation(ptyID, ptyUpdateRequest, location);
    }
}
