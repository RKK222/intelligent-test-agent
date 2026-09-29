package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.AgentListLocationParameter;
import com.example.opencode.sdk.model.ForbiddenErrorEncoded;
import com.example.opencode.sdk.model.InvalidRequestErrorEncoded;
import com.example.opencode.sdk.model.PtyConnectToken200Response;
import com.example.opencode.sdk.model.PtyCreate200Response;
import com.example.opencode.sdk.model.PtyCreateRequest;
import com.example.opencode.sdk.model.PtyGet200Response;
import com.example.opencode.sdk.model.PtyList200Response;
import com.example.opencode.sdk.model.PtyNotFoundErrorEncoded;
import com.example.opencode.sdk.model.PtyUpdateRequest;
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
        private @jakarta.annotation.Nullable String locationDirectory;
        private @jakarta.annotation.Nullable String cursor;
        private @jakarta.annotation.Nullable String ticket;

        public PtyConnectRequest() {}

        public PtyConnectRequest(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String locationDirectory, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String ticket) {
            this.ptyID = ptyID;
            this.locationDirectory = locationDirectory;
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

        public @jakarta.annotation.Nullable String locationDirectory() {
            return this.locationDirectory;
        }
        public PtyConnectRequest locationDirectory(@jakarta.annotation.Nullable String locationDirectory) {
            this.locationDirectory = locationDirectory;
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
                Objects.equals(this.locationDirectory, request.locationDirectory()) &&
                Objects.equals(this.cursor, request.cursor()) &&
                Objects.equals(this.ticket, request.ticket());
        }

        @Override
        public int hashCode() {
            return Objects.hash(ptyID, locationDirectory, cursor, ticket);
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
     * @param requestParameters The ptyConnect request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> ptyConnect(PtyConnectRequest requestParameters) throws WebClientResponseException {
        return this.ptyConnect(requestParameters.ptyID(), requestParameters.locationDirectory(), requestParameters.cursor(), requestParameters.ticket());
    }

    /**
     * Connect to PTY session
     * Establish a WebSocket connection streaming PTY output and accepting terminal input.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The ptyConnect request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> ptyConnectWithHttpInfo(PtyConnectRequest requestParameters) throws WebClientResponseException {
        return this.ptyConnectWithHttpInfo(requestParameters.ptyID(), requestParameters.locationDirectory(), requestParameters.cursor(), requestParameters.ticket());
    }

    /**
     * Connect to PTY session
     * Establish a WebSocket connection streaming PTY output and accepting terminal input.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The ptyConnect request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec ptyConnectWithResponseSpec(PtyConnectRequest requestParameters) throws WebClientResponseException {
        return this.ptyConnectWithResponseSpec(requestParameters.ptyID(), requestParameters.locationDirectory(), requestParameters.cursor(), requestParameters.ticket());
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
     * @param cursor The cursor parameter
     * @param ticket The ticket parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec ptyConnectRequestCreation(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String locationDirectory, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String ticket) throws WebClientResponseException {
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

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "location[directory]", locationDirectory));
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
     * @param cursor The cursor parameter
     * @param ticket The ticket parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> ptyConnect(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String locationDirectory, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String ticket) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return ptyConnectRequestCreation(ptyID, locationDirectory, cursor, ticket).bodyToMono(localVarReturnType);
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
     * @param cursor The cursor parameter
     * @param ticket The ticket parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> ptyConnectWithHttpInfo(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String locationDirectory, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String ticket) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return ptyConnectRequestCreation(ptyID, locationDirectory, cursor, ticket).toEntity(localVarReturnType);
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
     * @param cursor The cursor parameter
     * @param ticket The ticket parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec ptyConnectWithResponseSpec(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String locationDirectory, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String ticket) throws WebClientResponseException {
        return ptyConnectRequestCreation(ptyID, locationDirectory, cursor, ticket);
    }

    public class PtyConnectTokenRequest {
        private @jakarta.annotation.Nonnull String ptyID;
        private @jakarta.annotation.Nullable String xOpencodeTicket;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;

        public PtyConnectTokenRequest() {}

        public PtyConnectTokenRequest(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String xOpencodeTicket, @jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.ptyID = ptyID;
            this.xOpencodeTicket = xOpencodeTicket;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String ptyID() {
            return this.ptyID;
        }
        public PtyConnectTokenRequest ptyID(@jakarta.annotation.Nonnull String ptyID) {
            this.ptyID = ptyID;
            return this;
        }

        public @jakarta.annotation.Nullable String xOpencodeTicket() {
            return this.xOpencodeTicket;
        }
        public PtyConnectTokenRequest xOpencodeTicket(@jakarta.annotation.Nullable String xOpencodeTicket) {
            this.xOpencodeTicket = xOpencodeTicket;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public PtyConnectTokenRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
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
            PtyConnectTokenRequest request = (PtyConnectTokenRequest) o;
            return Objects.equals(this.ptyID, request.ptyID()) &&
                Objects.equals(this.xOpencodeTicket, request.xOpencodeTicket()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(ptyID, xOpencodeTicket, location);
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
     * @param requestParameters The ptyConnectToken request parameters as object
     * @return PtyConnectToken200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<PtyConnectToken200Response> ptyConnectToken(PtyConnectTokenRequest requestParameters) throws WebClientResponseException {
        return this.ptyConnectToken(requestParameters.ptyID(), requestParameters.xOpencodeTicket(), requestParameters.location());
    }

    /**
     * Create PTY WebSocket token
     * Create a short-lived single-use ticket for opening a PTY WebSocket connection.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The ptyConnectToken request parameters as object
     * @return ResponseEntity&lt;PtyConnectToken200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<PtyConnectToken200Response>> ptyConnectTokenWithHttpInfo(PtyConnectTokenRequest requestParameters) throws WebClientResponseException {
        return this.ptyConnectTokenWithHttpInfo(requestParameters.ptyID(), requestParameters.xOpencodeTicket(), requestParameters.location());
    }

    /**
     * Create PTY WebSocket token
     * Create a short-lived single-use ticket for opening a PTY WebSocket connection.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The ptyConnectToken request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec ptyConnectTokenWithResponseSpec(PtyConnectTokenRequest requestParameters) throws WebClientResponseException {
        return this.ptyConnectTokenWithResponseSpec(requestParameters.ptyID(), requestParameters.xOpencodeTicket(), requestParameters.location());
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
     * @param xOpencodeTicket The xOpencodeTicket parameter
     * @param location The location parameter
     * @return PtyConnectToken200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec ptyConnectTokenRequestCreation(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String xOpencodeTicket, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
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

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));

        if (xOpencodeTicket != null)
        headerParams.add("x-opencode-ticket", apiClient.parameterToString(xOpencodeTicket));
        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<PtyConnectToken200Response> localVarReturnType = new ParameterizedTypeReference<PtyConnectToken200Response>() {};
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
     * @param xOpencodeTicket The xOpencodeTicket parameter
     * @param location The location parameter
     * @return PtyConnectToken200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<PtyConnectToken200Response> ptyConnectToken(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String xOpencodeTicket, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<PtyConnectToken200Response> localVarReturnType = new ParameterizedTypeReference<PtyConnectToken200Response>() {};
        return ptyConnectTokenRequestCreation(ptyID, xOpencodeTicket, location).bodyToMono(localVarReturnType);
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
     * @param xOpencodeTicket The xOpencodeTicket parameter
     * @param location The location parameter
     * @return ResponseEntity&lt;PtyConnectToken200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<PtyConnectToken200Response>> ptyConnectTokenWithHttpInfo(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String xOpencodeTicket, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<PtyConnectToken200Response> localVarReturnType = new ParameterizedTypeReference<PtyConnectToken200Response>() {};
        return ptyConnectTokenRequestCreation(ptyID, xOpencodeTicket, location).toEntity(localVarReturnType);
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
     * @param xOpencodeTicket The xOpencodeTicket parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec ptyConnectTokenWithResponseSpec(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String xOpencodeTicket, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return ptyConnectTokenRequestCreation(ptyID, xOpencodeTicket, location);
    }

    public class PtyCreateRequest {
        private @jakarta.annotation.Nonnull PtyCreateRequest ptyCreateRequest;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;

        public PtyCreateRequest() {}

        public PtyCreateRequest(@jakarta.annotation.Nonnull PtyCreateRequest ptyCreateRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.ptyCreateRequest = ptyCreateRequest;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull PtyCreateRequest ptyCreateRequest() {
            return this.ptyCreateRequest;
        }
        public PtyCreateRequest ptyCreateRequest(@jakarta.annotation.Nonnull PtyCreateRequest ptyCreateRequest) {
            this.ptyCreateRequest = ptyCreateRequest;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public PtyCreateRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
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
            PtyCreateRequest request = (PtyCreateRequest) o;
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
     * @param requestParameters The ptyCreate request parameters as object
     * @return PtyCreate200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<PtyCreate200Response> ptyCreate(PtyCreateRequest requestParameters) throws WebClientResponseException {
        return this.ptyCreate(requestParameters.ptyCreateRequest(), requestParameters.location());
    }

    /**
     * Create PTY session
     * Create a pseudo-terminal session for a location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The ptyCreate request parameters as object
     * @return ResponseEntity&lt;PtyCreate200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<PtyCreate200Response>> ptyCreateWithHttpInfo(PtyCreateRequest requestParameters) throws WebClientResponseException {
        return this.ptyCreateWithHttpInfo(requestParameters.ptyCreateRequest(), requestParameters.location());
    }

    /**
     * Create PTY session
     * Create a pseudo-terminal session for a location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The ptyCreate request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec ptyCreateWithResponseSpec(PtyCreateRequest requestParameters) throws WebClientResponseException {
        return this.ptyCreateWithResponseSpec(requestParameters.ptyCreateRequest(), requestParameters.location());
    }


    /**
     * Create PTY session
     * Create a pseudo-terminal session for a location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param ptyCreateRequest The ptyCreateRequest parameter
     * @param location The location parameter
     * @return PtyCreate200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec ptyCreateRequestCreation(@jakarta.annotation.Nonnull PtyCreateRequest ptyCreateRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = ptyCreateRequest;
        // verify the required parameter 'ptyCreateRequest' is set
        if (ptyCreateRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'ptyCreateRequest' when calling ptyCreate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
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
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<PtyCreate200Response> localVarReturnType = new ParameterizedTypeReference<PtyCreate200Response>() {};
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
     * @return PtyCreate200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<PtyCreate200Response> ptyCreate(@jakarta.annotation.Nonnull PtyCreateRequest ptyCreateRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<PtyCreate200Response> localVarReturnType = new ParameterizedTypeReference<PtyCreate200Response>() {};
        return ptyCreateRequestCreation(ptyCreateRequest, location).bodyToMono(localVarReturnType);
    }

    /**
     * Create PTY session
     * Create a pseudo-terminal session for a location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param ptyCreateRequest The ptyCreateRequest parameter
     * @param location The location parameter
     * @return ResponseEntity&lt;PtyCreate200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<PtyCreate200Response>> ptyCreateWithHttpInfo(@jakarta.annotation.Nonnull PtyCreateRequest ptyCreateRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<PtyCreate200Response> localVarReturnType = new ParameterizedTypeReference<PtyCreate200Response>() {};
        return ptyCreateRequestCreation(ptyCreateRequest, location).toEntity(localVarReturnType);
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
    public ResponseSpec ptyCreateWithResponseSpec(@jakarta.annotation.Nonnull PtyCreateRequest ptyCreateRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return ptyCreateRequestCreation(ptyCreateRequest, location);
    }

    public class PtyGetRequest {
        private @jakarta.annotation.Nonnull String ptyID;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;

        public PtyGetRequest() {}

        public PtyGetRequest(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.ptyID = ptyID;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String ptyID() {
            return this.ptyID;
        }
        public PtyGetRequest ptyID(@jakarta.annotation.Nonnull String ptyID) {
            this.ptyID = ptyID;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public PtyGetRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
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
            PtyGetRequest request = (PtyGetRequest) o;
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
     * @param requestParameters The ptyGet request parameters as object
     * @return PtyGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<PtyGet200Response> ptyGet(PtyGetRequest requestParameters) throws WebClientResponseException {
        return this.ptyGet(requestParameters.ptyID(), requestParameters.location());
    }

    /**
     * Get PTY session
     * Get one PTY session, including its exit code once exited.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The ptyGet request parameters as object
     * @return ResponseEntity&lt;PtyGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<PtyGet200Response>> ptyGetWithHttpInfo(PtyGetRequest requestParameters) throws WebClientResponseException {
        return this.ptyGetWithHttpInfo(requestParameters.ptyID(), requestParameters.location());
    }

    /**
     * Get PTY session
     * Get one PTY session, including its exit code once exited.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The ptyGet request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec ptyGetWithResponseSpec(PtyGetRequest requestParameters) throws WebClientResponseException {
        return this.ptyGetWithResponseSpec(requestParameters.ptyID(), requestParameters.location());
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
     * @return PtyGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec ptyGetRequestCreation(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
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

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<PtyGet200Response> localVarReturnType = new ParameterizedTypeReference<PtyGet200Response>() {};
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
     * @return PtyGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<PtyGet200Response> ptyGet(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<PtyGet200Response> localVarReturnType = new ParameterizedTypeReference<PtyGet200Response>() {};
        return ptyGetRequestCreation(ptyID, location).bodyToMono(localVarReturnType);
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
     * @return ResponseEntity&lt;PtyGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<PtyGet200Response>> ptyGetWithHttpInfo(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<PtyGet200Response> localVarReturnType = new ParameterizedTypeReference<PtyGet200Response>() {};
        return ptyGetRequestCreation(ptyID, location).toEntity(localVarReturnType);
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
    public ResponseSpec ptyGetWithResponseSpec(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return ptyGetRequestCreation(ptyID, location);
    }

    /**
     * List PTY sessions
     * List PTY sessions for a location, including exited sessions retained until removal.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return PtyList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec ptyListRequestCreation(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
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

        ParameterizedTypeReference<PtyList200Response> localVarReturnType = new ParameterizedTypeReference<PtyList200Response>() {};
        return apiClient.invokeAPI("/api/pty", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List PTY sessions
     * List PTY sessions for a location, including exited sessions retained until removal.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return PtyList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<PtyList200Response> ptyList(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<PtyList200Response> localVarReturnType = new ParameterizedTypeReference<PtyList200Response>() {};
        return ptyListRequestCreation(location).bodyToMono(localVarReturnType);
    }

    /**
     * List PTY sessions
     * List PTY sessions for a location, including exited sessions retained until removal.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseEntity&lt;PtyList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<PtyList200Response>> ptyListWithHttpInfo(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<PtyList200Response> localVarReturnType = new ParameterizedTypeReference<PtyList200Response>() {};
        return ptyListRequestCreation(location).toEntity(localVarReturnType);
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
    public ResponseSpec ptyListWithResponseSpec(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return ptyListRequestCreation(location);
    }

    public class PtyRemoveRequest {
        private @jakarta.annotation.Nonnull String ptyID;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;

        public PtyRemoveRequest() {}

        public PtyRemoveRequest(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.ptyID = ptyID;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String ptyID() {
            return this.ptyID;
        }
        public PtyRemoveRequest ptyID(@jakarta.annotation.Nonnull String ptyID) {
            this.ptyID = ptyID;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public PtyRemoveRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
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
            PtyRemoveRequest request = (PtyRemoveRequest) o;
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
     * @param requestParameters The ptyRemove request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> ptyRemove(PtyRemoveRequest requestParameters) throws WebClientResponseException {
        return this.ptyRemove(requestParameters.ptyID(), requestParameters.location());
    }

    /**
     * Remove PTY session
     * Terminate and remove one PTY session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The ptyRemove request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> ptyRemoveWithHttpInfo(PtyRemoveRequest requestParameters) throws WebClientResponseException {
        return this.ptyRemoveWithHttpInfo(requestParameters.ptyID(), requestParameters.location());
    }

    /**
     * Remove PTY session
     * Terminate and remove one PTY session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The ptyRemove request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec ptyRemoveWithResponseSpec(PtyRemoveRequest requestParameters) throws WebClientResponseException {
        return this.ptyRemoveWithResponseSpec(requestParameters.ptyID(), requestParameters.location());
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
    private ResponseSpec ptyRemoveRequestCreation(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
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

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));

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
    public Mono<Void> ptyRemove(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return ptyRemoveRequestCreation(ptyID, location).bodyToMono(localVarReturnType);
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
    public Mono<ResponseEntity<Void>> ptyRemoveWithHttpInfo(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return ptyRemoveRequestCreation(ptyID, location).toEntity(localVarReturnType);
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
    public ResponseSpec ptyRemoveWithResponseSpec(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return ptyRemoveRequestCreation(ptyID, location);
    }

    public class PtyUpdateRequest {
        private @jakarta.annotation.Nonnull String ptyID;
        private @jakarta.annotation.Nonnull PtyUpdateRequest ptyUpdateRequest;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;

        public PtyUpdateRequest() {}

        public PtyUpdateRequest(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nonnull PtyUpdateRequest ptyUpdateRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.ptyID = ptyID;
            this.ptyUpdateRequest = ptyUpdateRequest;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String ptyID() {
            return this.ptyID;
        }
        public PtyUpdateRequest ptyID(@jakarta.annotation.Nonnull String ptyID) {
            this.ptyID = ptyID;
            return this;
        }

        public @jakarta.annotation.Nonnull PtyUpdateRequest ptyUpdateRequest() {
            return this.ptyUpdateRequest;
        }
        public PtyUpdateRequest ptyUpdateRequest(@jakarta.annotation.Nonnull PtyUpdateRequest ptyUpdateRequest) {
            this.ptyUpdateRequest = ptyUpdateRequest;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public PtyUpdateRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
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
            PtyUpdateRequest request = (PtyUpdateRequest) o;
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
     * @param requestParameters The ptyUpdate request parameters as object
     * @return PtyGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<PtyGet200Response> ptyUpdate(PtyUpdateRequest requestParameters) throws WebClientResponseException {
        return this.ptyUpdate(requestParameters.ptyID(), requestParameters.ptyUpdateRequest(), requestParameters.location());
    }

    /**
     * Update PTY session
     * Update the title or viewport size of one PTY session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The ptyUpdate request parameters as object
     * @return ResponseEntity&lt;PtyGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<PtyGet200Response>> ptyUpdateWithHttpInfo(PtyUpdateRequest requestParameters) throws WebClientResponseException {
        return this.ptyUpdateWithHttpInfo(requestParameters.ptyID(), requestParameters.ptyUpdateRequest(), requestParameters.location());
    }

    /**
     * Update PTY session
     * Update the title or viewport size of one PTY session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * @param requestParameters The ptyUpdate request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec ptyUpdateWithResponseSpec(PtyUpdateRequest requestParameters) throws WebClientResponseException {
        return this.ptyUpdateWithResponseSpec(requestParameters.ptyID(), requestParameters.ptyUpdateRequest(), requestParameters.location());
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
     * @return PtyGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec ptyUpdateRequestCreation(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nonnull PtyUpdateRequest ptyUpdateRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = ptyUpdateRequest;
        // verify the required parameter 'ptyID' is set
        if (ptyID == null) {
            throw new WebClientResponseException("Missing the required parameter 'ptyID' when calling ptyUpdate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'ptyUpdateRequest' is set
        if (ptyUpdateRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'ptyUpdateRequest' when calling ptyUpdate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("ptyID", ptyID);

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

        ParameterizedTypeReference<PtyGet200Response> localVarReturnType = new ParameterizedTypeReference<PtyGet200Response>() {};
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
     * @return PtyGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<PtyGet200Response> ptyUpdate(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nonnull PtyUpdateRequest ptyUpdateRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<PtyGet200Response> localVarReturnType = new ParameterizedTypeReference<PtyGet200Response>() {};
        return ptyUpdateRequestCreation(ptyID, ptyUpdateRequest, location).bodyToMono(localVarReturnType);
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
     * @return ResponseEntity&lt;PtyGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<PtyGet200Response>> ptyUpdateWithHttpInfo(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nonnull PtyUpdateRequest ptyUpdateRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<PtyGet200Response> localVarReturnType = new ParameterizedTypeReference<PtyGet200Response>() {};
        return ptyUpdateRequestCreation(ptyID, ptyUpdateRequest, location).toEntity(localVarReturnType);
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
    public ResponseSpec ptyUpdateWithResponseSpec(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nonnull PtyUpdateRequest ptyUpdateRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return ptyUpdateRequestCreation(ptyID, ptyUpdateRequest, location);
    }
}
