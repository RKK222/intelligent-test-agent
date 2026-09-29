package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.ForbiddenErrorEncoded;
import com.example.opencode.sdk.model.InvalidRequestErrorEncoded;
import com.example.opencode.sdk.model.PersistentPtyCreateInput;
import com.example.opencode.sdk.model.PersistentPtyUpdateInput;
import com.example.opencode.sdk.model.PluginCheck400Response;
import com.example.opencode.sdk.model.PtyNotFoundErrorEncoded;
import com.example.opencode.sdk.model.ServerExperimentalPersistentPtyConnectToken200Response;
import com.example.opencode.sdk.model.ServerExperimentalPersistentPtyCreate200Response;
import com.example.opencode.sdk.model.ServerExperimentalPersistentPtyGet200Response;
import com.example.opencode.sdk.model.ServerExperimentalPersistentPtyHandoff200Response;
import com.example.opencode.sdk.model.ServerExperimentalPersistentPtyList200Response;
import com.example.opencode.sdk.model.ServerExperimentalPersistentPtyRead200Response;
import com.example.opencode.sdk.model.ServerExperimentalPersistentPtySnapshot200Response;
import com.example.opencode.sdk.model.ServiceUnavailableErrorEncoded;
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
public class PersistentPtyApi {
    private ApiClient apiClient;

    public PersistentPtyApi() {
        this(new ApiClient());
    }

    public PersistentPtyApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public class PersistentPtyConnectRequest {
        private @jakarta.annotation.Nonnull String ptyID;
        private @jakarta.annotation.Nullable String cursor;
        private @jakarta.annotation.Nullable String role;
        private @jakarta.annotation.Nullable String attachmentId;
        private @jakarta.annotation.Nullable String takeover;
        private @jakarta.annotation.Nullable String inputProtocol;
        private @jakarta.annotation.Nullable String ticket;

        public PersistentPtyConnectRequest() {}

        public PersistentPtyConnectRequest(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String role, @jakarta.annotation.Nullable String attachmentId, @jakarta.annotation.Nullable String takeover, @jakarta.annotation.Nullable String inputProtocol, @jakarta.annotation.Nullable String ticket) {
            this.ptyID = ptyID;
            this.cursor = cursor;
            this.role = role;
            this.attachmentId = attachmentId;
            this.takeover = takeover;
            this.inputProtocol = inputProtocol;
            this.ticket = ticket;
        }

        public @jakarta.annotation.Nonnull String ptyID() {
            return this.ptyID;
        }
        public PersistentPtyConnectRequest ptyID(@jakarta.annotation.Nonnull String ptyID) {
            this.ptyID = ptyID;
            return this;
        }

        public @jakarta.annotation.Nullable String cursor() {
            return this.cursor;
        }
        public PersistentPtyConnectRequest cursor(@jakarta.annotation.Nullable String cursor) {
            this.cursor = cursor;
            return this;
        }

        public @jakarta.annotation.Nullable String role() {
            return this.role;
        }
        public PersistentPtyConnectRequest role(@jakarta.annotation.Nullable String role) {
            this.role = role;
            return this;
        }

        public @jakarta.annotation.Nullable String attachmentId() {
            return this.attachmentId;
        }
        public PersistentPtyConnectRequest attachmentId(@jakarta.annotation.Nullable String attachmentId) {
            this.attachmentId = attachmentId;
            return this;
        }

        public @jakarta.annotation.Nullable String takeover() {
            return this.takeover;
        }
        public PersistentPtyConnectRequest takeover(@jakarta.annotation.Nullable String takeover) {
            this.takeover = takeover;
            return this;
        }

        public @jakarta.annotation.Nullable String inputProtocol() {
            return this.inputProtocol;
        }
        public PersistentPtyConnectRequest inputProtocol(@jakarta.annotation.Nullable String inputProtocol) {
            this.inputProtocol = inputProtocol;
            return this;
        }

        public @jakarta.annotation.Nullable String ticket() {
            return this.ticket;
        }
        public PersistentPtyConnectRequest ticket(@jakarta.annotation.Nullable String ticket) {
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
            PersistentPtyConnectRequest request = (PersistentPtyConnectRequest) o;
            return Objects.equals(this.ptyID, request.ptyID()) &&
                Objects.equals(this.cursor, request.cursor()) &&
                Objects.equals(this.role, request.role()) &&
                Objects.equals(this.attachmentId, request.attachmentId()) &&
                Objects.equals(this.takeover, request.takeover()) &&
                Objects.equals(this.inputProtocol, request.inputProtocol()) &&
                Objects.equals(this.ticket, request.ticket());
        }

        @Override
        public int hashCode() {
            return Objects.hash(ptyID, cursor, role, attachmentId, takeover, inputProtocol, ticket);
        }
    }

    /**
     * Connect to a persistent PTY
     * Stream persistent PTY output through the OpenCode server.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The persistentPtyConnect request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> persistentPtyConnect(PersistentPtyConnectRequest requestParameters) throws WebClientResponseException {
        return this.persistentPtyConnect(requestParameters.ptyID(), requestParameters.cursor(), requestParameters.role(), requestParameters.attachmentId(), requestParameters.takeover(), requestParameters.inputProtocol(), requestParameters.ticket());
    }

    /**
     * Connect to a persistent PTY
     * Stream persistent PTY output through the OpenCode server.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The persistentPtyConnect request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> persistentPtyConnectWithHttpInfo(PersistentPtyConnectRequest requestParameters) throws WebClientResponseException {
        return this.persistentPtyConnectWithHttpInfo(requestParameters.ptyID(), requestParameters.cursor(), requestParameters.role(), requestParameters.attachmentId(), requestParameters.takeover(), requestParameters.inputProtocol(), requestParameters.ticket());
    }

    /**
     * Connect to a persistent PTY
     * Stream persistent PTY output through the OpenCode server.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The persistentPtyConnect request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec persistentPtyConnectWithResponseSpec(PersistentPtyConnectRequest requestParameters) throws WebClientResponseException {
        return this.persistentPtyConnectWithResponseSpec(requestParameters.ptyID(), requestParameters.cursor(), requestParameters.role(), requestParameters.attachmentId(), requestParameters.takeover(), requestParameters.inputProtocol(), requestParameters.ticket());
    }


    /**
     * Connect to a persistent PTY
     * Stream persistent PTY output through the OpenCode server.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param ptyID The ptyID parameter
     * @param cursor The cursor parameter
     * @param role The role parameter
     * @param attachmentId The attachmentId parameter
     * @param takeover The takeover parameter
     * @param inputProtocol The inputProtocol parameter
     * @param ticket The ticket parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec persistentPtyConnectRequestCreation(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String role, @jakarta.annotation.Nullable String attachmentId, @jakarta.annotation.Nullable String takeover, @jakarta.annotation.Nullable String inputProtocol, @jakarta.annotation.Nullable String ticket) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'ptyID' is set
        if (ptyID == null) {
            throw new WebClientResponseException("Missing the required parameter 'ptyID' when calling persistentPtyConnect", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("ptyID", ptyID);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "cursor", cursor));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "role", role));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "attachment_id", attachmentId));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "takeover", takeover));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "input_protocol", inputProtocol));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "ticket", ticket));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return apiClient.invokeAPI("/api/experimental/persistent-pty/{ptyID}/connect", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Connect to a persistent PTY
     * Stream persistent PTY output through the OpenCode server.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param ptyID The ptyID parameter
     * @param cursor The cursor parameter
     * @param role The role parameter
     * @param attachmentId The attachmentId parameter
     * @param takeover The takeover parameter
     * @param inputProtocol The inputProtocol parameter
     * @param ticket The ticket parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> persistentPtyConnect(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String role, @jakarta.annotation.Nullable String attachmentId, @jakarta.annotation.Nullable String takeover, @jakarta.annotation.Nullable String inputProtocol, @jakarta.annotation.Nullable String ticket) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return persistentPtyConnectRequestCreation(ptyID, cursor, role, attachmentId, takeover, inputProtocol, ticket).bodyToMono(localVarReturnType);
    }

    /**
     * Connect to a persistent PTY
     * Stream persistent PTY output through the OpenCode server.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param ptyID The ptyID parameter
     * @param cursor The cursor parameter
     * @param role The role parameter
     * @param attachmentId The attachmentId parameter
     * @param takeover The takeover parameter
     * @param inputProtocol The inputProtocol parameter
     * @param ticket The ticket parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> persistentPtyConnectWithHttpInfo(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String role, @jakarta.annotation.Nullable String attachmentId, @jakarta.annotation.Nullable String takeover, @jakarta.annotation.Nullable String inputProtocol, @jakarta.annotation.Nullable String ticket) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return persistentPtyConnectRequestCreation(ptyID, cursor, role, attachmentId, takeover, inputProtocol, ticket).toEntity(localVarReturnType);
    }

    /**
     * Connect to a persistent PTY
     * Stream persistent PTY output through the OpenCode server.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param ptyID The ptyID parameter
     * @param cursor The cursor parameter
     * @param role The role parameter
     * @param attachmentId The attachmentId parameter
     * @param takeover The takeover parameter
     * @param inputProtocol The inputProtocol parameter
     * @param ticket The ticket parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec persistentPtyConnectWithResponseSpec(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String role, @jakarta.annotation.Nullable String attachmentId, @jakarta.annotation.Nullable String takeover, @jakarta.annotation.Nullable String inputProtocol, @jakarta.annotation.Nullable String ticket) throws WebClientResponseException {
        return persistentPtyConnectRequestCreation(ptyID, cursor, role, attachmentId, takeover, inputProtocol, ticket);
    }

    public class ServerExperimentalPersistentPtyConnectTokenRequest {
        private @jakarta.annotation.Nonnull String ptyID;
        private @jakarta.annotation.Nullable String xOpencodeTicket;

        public ServerExperimentalPersistentPtyConnectTokenRequest() {}

        public ServerExperimentalPersistentPtyConnectTokenRequest(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String xOpencodeTicket) {
            this.ptyID = ptyID;
            this.xOpencodeTicket = xOpencodeTicket;
        }

        public @jakarta.annotation.Nonnull String ptyID() {
            return this.ptyID;
        }
        public ServerExperimentalPersistentPtyConnectTokenRequest ptyID(@jakarta.annotation.Nonnull String ptyID) {
            this.ptyID = ptyID;
            return this;
        }

        public @jakarta.annotation.Nullable String xOpencodeTicket() {
            return this.xOpencodeTicket;
        }
        public ServerExperimentalPersistentPtyConnectTokenRequest xOpencodeTicket(@jakarta.annotation.Nullable String xOpencodeTicket) {
            this.xOpencodeTicket = xOpencodeTicket;
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
            ServerExperimentalPersistentPtyConnectTokenRequest request = (ServerExperimentalPersistentPtyConnectTokenRequest) o;
            return Objects.equals(this.ptyID, request.ptyID()) &&
                Objects.equals(this.xOpencodeTicket, request.xOpencodeTicket());
        }

        @Override
        public int hashCode() {
            return Objects.hash(ptyID, xOpencodeTicket);
        }
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The serverExperimentalPersistentPtyConnectToken request parameters as object
     * @return ServerExperimentalPersistentPtyConnectToken200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ServerExperimentalPersistentPtyConnectToken200Response> serverExperimentalPersistentPtyConnectToken(ServerExperimentalPersistentPtyConnectTokenRequest requestParameters) throws WebClientResponseException {
        return this.serverExperimentalPersistentPtyConnectToken(requestParameters.ptyID(), requestParameters.xOpencodeTicket());
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The serverExperimentalPersistentPtyConnectToken request parameters as object
     * @return ResponseEntity&lt;ServerExperimentalPersistentPtyConnectToken200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ServerExperimentalPersistentPtyConnectToken200Response>> serverExperimentalPersistentPtyConnectTokenWithHttpInfo(ServerExperimentalPersistentPtyConnectTokenRequest requestParameters) throws WebClientResponseException {
        return this.serverExperimentalPersistentPtyConnectTokenWithHttpInfo(requestParameters.ptyID(), requestParameters.xOpencodeTicket());
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The serverExperimentalPersistentPtyConnectToken request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec serverExperimentalPersistentPtyConnectTokenWithResponseSpec(ServerExperimentalPersistentPtyConnectTokenRequest requestParameters) throws WebClientResponseException {
        return this.serverExperimentalPersistentPtyConnectTokenWithResponseSpec(requestParameters.ptyID(), requestParameters.xOpencodeTicket());
    }


    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param ptyID The ptyID parameter
     * @param xOpencodeTicket The xOpencodeTicket parameter
     * @return ServerExperimentalPersistentPtyConnectToken200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec serverExperimentalPersistentPtyConnectTokenRequestCreation(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String xOpencodeTicket) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'ptyID' is set
        if (ptyID == null) {
            throw new WebClientResponseException("Missing the required parameter 'ptyID' when calling serverExperimentalPersistentPtyConnectToken", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("ptyID", ptyID);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        if (xOpencodeTicket != null)
        headerParams.add("x-opencode-ticket", apiClient.parameterToString(xOpencodeTicket));
        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<ServerExperimentalPersistentPtyConnectToken200Response> localVarReturnType = new ParameterizedTypeReference<ServerExperimentalPersistentPtyConnectToken200Response>() {};
        return apiClient.invokeAPI("/api/experimental/persistent-pty/{ptyID}/connect-token", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param ptyID The ptyID parameter
     * @param xOpencodeTicket The xOpencodeTicket parameter
     * @return ServerExperimentalPersistentPtyConnectToken200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ServerExperimentalPersistentPtyConnectToken200Response> serverExperimentalPersistentPtyConnectToken(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String xOpencodeTicket) throws WebClientResponseException {
        ParameterizedTypeReference<ServerExperimentalPersistentPtyConnectToken200Response> localVarReturnType = new ParameterizedTypeReference<ServerExperimentalPersistentPtyConnectToken200Response>() {};
        return serverExperimentalPersistentPtyConnectTokenRequestCreation(ptyID, xOpencodeTicket).bodyToMono(localVarReturnType);
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param ptyID The ptyID parameter
     * @param xOpencodeTicket The xOpencodeTicket parameter
     * @return ResponseEntity&lt;ServerExperimentalPersistentPtyConnectToken200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ServerExperimentalPersistentPtyConnectToken200Response>> serverExperimentalPersistentPtyConnectTokenWithHttpInfo(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String xOpencodeTicket) throws WebClientResponseException {
        ParameterizedTypeReference<ServerExperimentalPersistentPtyConnectToken200Response> localVarReturnType = new ParameterizedTypeReference<ServerExperimentalPersistentPtyConnectToken200Response>() {};
        return serverExperimentalPersistentPtyConnectTokenRequestCreation(ptyID, xOpencodeTicket).toEntity(localVarReturnType);
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>403</b> - ForbiddenError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param ptyID The ptyID parameter
     * @param xOpencodeTicket The xOpencodeTicket parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec serverExperimentalPersistentPtyConnectTokenWithResponseSpec(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nullable String xOpencodeTicket) throws WebClientResponseException {
        return serverExperimentalPersistentPtyConnectTokenRequestCreation(ptyID, xOpencodeTicket);
    }

    public class ServerExperimentalPersistentPtyCreateRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull PersistentPtyCreateInput persistentPtyCreateInput;

        public ServerExperimentalPersistentPtyCreateRequest() {}

        public ServerExperimentalPersistentPtyCreateRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull PersistentPtyCreateInput persistentPtyCreateInput) {
            this.sessionID = sessionID;
            this.persistentPtyCreateInput = persistentPtyCreateInput;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public ServerExperimentalPersistentPtyCreateRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull PersistentPtyCreateInput persistentPtyCreateInput() {
            return this.persistentPtyCreateInput;
        }
        public ServerExperimentalPersistentPtyCreateRequest persistentPtyCreateInput(@jakarta.annotation.Nonnull PersistentPtyCreateInput persistentPtyCreateInput) {
            this.persistentPtyCreateInput = persistentPtyCreateInput;
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
            ServerExperimentalPersistentPtyCreateRequest request = (ServerExperimentalPersistentPtyCreateRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.persistentPtyCreateInput, request.persistentPtyCreateInput());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, persistentPtyCreateInput);
        }
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The serverExperimentalPersistentPtyCreate request parameters as object
     * @return ServerExperimentalPersistentPtyCreate200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ServerExperimentalPersistentPtyCreate200Response> serverExperimentalPersistentPtyCreate(ServerExperimentalPersistentPtyCreateRequest requestParameters) throws WebClientResponseException {
        return this.serverExperimentalPersistentPtyCreate(requestParameters.sessionID(), requestParameters.persistentPtyCreateInput());
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The serverExperimentalPersistentPtyCreate request parameters as object
     * @return ResponseEntity&lt;ServerExperimentalPersistentPtyCreate200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ServerExperimentalPersistentPtyCreate200Response>> serverExperimentalPersistentPtyCreateWithHttpInfo(ServerExperimentalPersistentPtyCreateRequest requestParameters) throws WebClientResponseException {
        return this.serverExperimentalPersistentPtyCreateWithHttpInfo(requestParameters.sessionID(), requestParameters.persistentPtyCreateInput());
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The serverExperimentalPersistentPtyCreate request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec serverExperimentalPersistentPtyCreateWithResponseSpec(ServerExperimentalPersistentPtyCreateRequest requestParameters) throws WebClientResponseException {
        return this.serverExperimentalPersistentPtyCreateWithResponseSpec(requestParameters.sessionID(), requestParameters.persistentPtyCreateInput());
    }


    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param sessionID The sessionID parameter
     * @param persistentPtyCreateInput The persistentPtyCreateInput parameter
     * @return ServerExperimentalPersistentPtyCreate200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec serverExperimentalPersistentPtyCreateRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull PersistentPtyCreateInput persistentPtyCreateInput) throws WebClientResponseException {
        Object postBody = persistentPtyCreateInput;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling serverExperimentalPersistentPtyCreate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'persistentPtyCreateInput' is set
        if (persistentPtyCreateInput == null) {
            throw new WebClientResponseException("Missing the required parameter 'persistentPtyCreateInput' when calling serverExperimentalPersistentPtyCreate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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

        ParameterizedTypeReference<ServerExperimentalPersistentPtyCreate200Response> localVarReturnType = new ParameterizedTypeReference<ServerExperimentalPersistentPtyCreate200Response>() {};
        return apiClient.invokeAPI("/api/experimental/session/{sessionID}/terminal", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param sessionID The sessionID parameter
     * @param persistentPtyCreateInput The persistentPtyCreateInput parameter
     * @return ServerExperimentalPersistentPtyCreate200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ServerExperimentalPersistentPtyCreate200Response> serverExperimentalPersistentPtyCreate(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull PersistentPtyCreateInput persistentPtyCreateInput) throws WebClientResponseException {
        ParameterizedTypeReference<ServerExperimentalPersistentPtyCreate200Response> localVarReturnType = new ParameterizedTypeReference<ServerExperimentalPersistentPtyCreate200Response>() {};
        return serverExperimentalPersistentPtyCreateRequestCreation(sessionID, persistentPtyCreateInput).bodyToMono(localVarReturnType);
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param sessionID The sessionID parameter
     * @param persistentPtyCreateInput The persistentPtyCreateInput parameter
     * @return ResponseEntity&lt;ServerExperimentalPersistentPtyCreate200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ServerExperimentalPersistentPtyCreate200Response>> serverExperimentalPersistentPtyCreateWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull PersistentPtyCreateInput persistentPtyCreateInput) throws WebClientResponseException {
        ParameterizedTypeReference<ServerExperimentalPersistentPtyCreate200Response> localVarReturnType = new ParameterizedTypeReference<ServerExperimentalPersistentPtyCreate200Response>() {};
        return serverExperimentalPersistentPtyCreateRequestCreation(sessionID, persistentPtyCreateInput).toEntity(localVarReturnType);
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param sessionID The sessionID parameter
     * @param persistentPtyCreateInput The persistentPtyCreateInput parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec serverExperimentalPersistentPtyCreateWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull PersistentPtyCreateInput persistentPtyCreateInput) throws WebClientResponseException {
        return serverExperimentalPersistentPtyCreateRequestCreation(sessionID, persistentPtyCreateInput);
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param ptyID The ptyID parameter
     * @return ServerExperimentalPersistentPtyGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec serverExperimentalPersistentPtyGetRequestCreation(@jakarta.annotation.Nonnull String ptyID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'ptyID' is set
        if (ptyID == null) {
            throw new WebClientResponseException("Missing the required parameter 'ptyID' when calling serverExperimentalPersistentPtyGet", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("ptyID", ptyID);

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

        ParameterizedTypeReference<ServerExperimentalPersistentPtyGet200Response> localVarReturnType = new ParameterizedTypeReference<ServerExperimentalPersistentPtyGet200Response>() {};
        return apiClient.invokeAPI("/api/experimental/persistent-pty/{ptyID}", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param ptyID The ptyID parameter
     * @return ServerExperimentalPersistentPtyGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ServerExperimentalPersistentPtyGet200Response> serverExperimentalPersistentPtyGet(@jakarta.annotation.Nonnull String ptyID) throws WebClientResponseException {
        ParameterizedTypeReference<ServerExperimentalPersistentPtyGet200Response> localVarReturnType = new ParameterizedTypeReference<ServerExperimentalPersistentPtyGet200Response>() {};
        return serverExperimentalPersistentPtyGetRequestCreation(ptyID).bodyToMono(localVarReturnType);
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param ptyID The ptyID parameter
     * @return ResponseEntity&lt;ServerExperimentalPersistentPtyGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ServerExperimentalPersistentPtyGet200Response>> serverExperimentalPersistentPtyGetWithHttpInfo(@jakarta.annotation.Nonnull String ptyID) throws WebClientResponseException {
        ParameterizedTypeReference<ServerExperimentalPersistentPtyGet200Response> localVarReturnType = new ParameterizedTypeReference<ServerExperimentalPersistentPtyGet200Response>() {};
        return serverExperimentalPersistentPtyGetRequestCreation(ptyID).toEntity(localVarReturnType);
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param ptyID The ptyID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec serverExperimentalPersistentPtyGetWithResponseSpec(@jakarta.annotation.Nonnull String ptyID) throws WebClientResponseException {
        return serverExperimentalPersistentPtyGetRequestCreation(ptyID);
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @return ServerExperimentalPersistentPtyHandoff200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec serverExperimentalPersistentPtyHandoffRequestCreation() throws WebClientResponseException {
        Object postBody = null;
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
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<ServerExperimentalPersistentPtyHandoff200Response> localVarReturnType = new ParameterizedTypeReference<ServerExperimentalPersistentPtyHandoff200Response>() {};
        return apiClient.invokeAPI("/api/experimental/persistent-pty/handoff", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @return ServerExperimentalPersistentPtyHandoff200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ServerExperimentalPersistentPtyHandoff200Response> serverExperimentalPersistentPtyHandoff() throws WebClientResponseException {
        ParameterizedTypeReference<ServerExperimentalPersistentPtyHandoff200Response> localVarReturnType = new ParameterizedTypeReference<ServerExperimentalPersistentPtyHandoff200Response>() {};
        return serverExperimentalPersistentPtyHandoffRequestCreation().bodyToMono(localVarReturnType);
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @return ResponseEntity&lt;ServerExperimentalPersistentPtyHandoff200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ServerExperimentalPersistentPtyHandoff200Response>> serverExperimentalPersistentPtyHandoffWithHttpInfo() throws WebClientResponseException {
        ParameterizedTypeReference<ServerExperimentalPersistentPtyHandoff200Response> localVarReturnType = new ParameterizedTypeReference<ServerExperimentalPersistentPtyHandoff200Response>() {};
        return serverExperimentalPersistentPtyHandoffRequestCreation().toEntity(localVarReturnType);
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec serverExperimentalPersistentPtyHandoffWithResponseSpec() throws WebClientResponseException {
        return serverExperimentalPersistentPtyHandoffRequestCreation();
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param sessionID The sessionID parameter
     * @return ServerExperimentalPersistentPtyList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec serverExperimentalPersistentPtyListRequestCreation(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling serverExperimentalPersistentPtyList", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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

        ParameterizedTypeReference<ServerExperimentalPersistentPtyList200Response> localVarReturnType = new ParameterizedTypeReference<ServerExperimentalPersistentPtyList200Response>() {};
        return apiClient.invokeAPI("/api/experimental/session/{sessionID}/terminal", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param sessionID The sessionID parameter
     * @return ServerExperimentalPersistentPtyList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ServerExperimentalPersistentPtyList200Response> serverExperimentalPersistentPtyList(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<ServerExperimentalPersistentPtyList200Response> localVarReturnType = new ParameterizedTypeReference<ServerExperimentalPersistentPtyList200Response>() {};
        return serverExperimentalPersistentPtyListRequestCreation(sessionID).bodyToMono(localVarReturnType);
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param sessionID The sessionID parameter
     * @return ResponseEntity&lt;ServerExperimentalPersistentPtyList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ServerExperimentalPersistentPtyList200Response>> serverExperimentalPersistentPtyListWithHttpInfo(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<ServerExperimentalPersistentPtyList200Response> localVarReturnType = new ParameterizedTypeReference<ServerExperimentalPersistentPtyList200Response>() {};
        return serverExperimentalPersistentPtyListRequestCreation(sessionID).toEntity(localVarReturnType);
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param sessionID The sessionID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec serverExperimentalPersistentPtyListWithResponseSpec(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        return serverExperimentalPersistentPtyListRequestCreation(sessionID);
    }

    public class ServerExperimentalPersistentPtyReadRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String lines;

        public ServerExperimentalPersistentPtyReadRequest() {}

        public ServerExperimentalPersistentPtyReadRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String lines) {
            this.sessionID = sessionID;
            this.lines = lines;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public ServerExperimentalPersistentPtyReadRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String lines() {
            return this.lines;
        }
        public ServerExperimentalPersistentPtyReadRequest lines(@jakarta.annotation.Nullable String lines) {
            this.lines = lines;
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
            ServerExperimentalPersistentPtyReadRequest request = (ServerExperimentalPersistentPtyReadRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.lines, request.lines());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, lines);
        }
    }

    /**
     * Read the session&#39;s most recently controlled terminal
     * Read the last physical rows without changing selection or taking control. Omitted lines uses the live terminal height; larger counts include retained history. Blank rows are preserved. Screen dimensions and cursor remain relative to the live screen. Returns null when no current terminal exists. Selection is server-local and resets on restart. Experimental: may change without compatibility guarantees.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The serverExperimentalPersistentPtyRead request parameters as object
     * @return ServerExperimentalPersistentPtyRead200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ServerExperimentalPersistentPtyRead200Response> serverExperimentalPersistentPtyRead(ServerExperimentalPersistentPtyReadRequest requestParameters) throws WebClientResponseException {
        return this.serverExperimentalPersistentPtyRead(requestParameters.sessionID(), requestParameters.lines());
    }

    /**
     * Read the session&#39;s most recently controlled terminal
     * Read the last physical rows without changing selection or taking control. Omitted lines uses the live terminal height; larger counts include retained history. Blank rows are preserved. Screen dimensions and cursor remain relative to the live screen. Returns null when no current terminal exists. Selection is server-local and resets on restart. Experimental: may change without compatibility guarantees.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The serverExperimentalPersistentPtyRead request parameters as object
     * @return ResponseEntity&lt;ServerExperimentalPersistentPtyRead200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ServerExperimentalPersistentPtyRead200Response>> serverExperimentalPersistentPtyReadWithHttpInfo(ServerExperimentalPersistentPtyReadRequest requestParameters) throws WebClientResponseException {
        return this.serverExperimentalPersistentPtyReadWithHttpInfo(requestParameters.sessionID(), requestParameters.lines());
    }

    /**
     * Read the session&#39;s most recently controlled terminal
     * Read the last physical rows without changing selection or taking control. Omitted lines uses the live terminal height; larger counts include retained history. Blank rows are preserved. Screen dimensions and cursor remain relative to the live screen. Returns null when no current terminal exists. Selection is server-local and resets on restart. Experimental: may change without compatibility guarantees.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The serverExperimentalPersistentPtyRead request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec serverExperimentalPersistentPtyReadWithResponseSpec(ServerExperimentalPersistentPtyReadRequest requestParameters) throws WebClientResponseException {
        return this.serverExperimentalPersistentPtyReadWithResponseSpec(requestParameters.sessionID(), requestParameters.lines());
    }


    /**
     * Read the session&#39;s most recently controlled terminal
     * Read the last physical rows without changing selection or taking control. Omitted lines uses the live terminal height; larger counts include retained history. Blank rows are preserved. Screen dimensions and cursor remain relative to the live screen. Returns null when no current terminal exists. Selection is server-local and resets on restart. Experimental: may change without compatibility guarantees.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param sessionID The sessionID parameter
     * @param lines The lines parameter
     * @return ServerExperimentalPersistentPtyRead200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec serverExperimentalPersistentPtyReadRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String lines) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling serverExperimentalPersistentPtyRead", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "lines", lines));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<ServerExperimentalPersistentPtyRead200Response> localVarReturnType = new ParameterizedTypeReference<ServerExperimentalPersistentPtyRead200Response>() {};
        return apiClient.invokeAPI("/api/experimental/session/{sessionID}/terminal/read", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Read the session&#39;s most recently controlled terminal
     * Read the last physical rows without changing selection or taking control. Omitted lines uses the live terminal height; larger counts include retained history. Blank rows are preserved. Screen dimensions and cursor remain relative to the live screen. Returns null when no current terminal exists. Selection is server-local and resets on restart. Experimental: may change without compatibility guarantees.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param sessionID The sessionID parameter
     * @param lines The lines parameter
     * @return ServerExperimentalPersistentPtyRead200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ServerExperimentalPersistentPtyRead200Response> serverExperimentalPersistentPtyRead(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String lines) throws WebClientResponseException {
        ParameterizedTypeReference<ServerExperimentalPersistentPtyRead200Response> localVarReturnType = new ParameterizedTypeReference<ServerExperimentalPersistentPtyRead200Response>() {};
        return serverExperimentalPersistentPtyReadRequestCreation(sessionID, lines).bodyToMono(localVarReturnType);
    }

    /**
     * Read the session&#39;s most recently controlled terminal
     * Read the last physical rows without changing selection or taking control. Omitted lines uses the live terminal height; larger counts include retained history. Blank rows are preserved. Screen dimensions and cursor remain relative to the live screen. Returns null when no current terminal exists. Selection is server-local and resets on restart. Experimental: may change without compatibility guarantees.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param sessionID The sessionID parameter
     * @param lines The lines parameter
     * @return ResponseEntity&lt;ServerExperimentalPersistentPtyRead200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ServerExperimentalPersistentPtyRead200Response>> serverExperimentalPersistentPtyReadWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String lines) throws WebClientResponseException {
        ParameterizedTypeReference<ServerExperimentalPersistentPtyRead200Response> localVarReturnType = new ParameterizedTypeReference<ServerExperimentalPersistentPtyRead200Response>() {};
        return serverExperimentalPersistentPtyReadRequestCreation(sessionID, lines).toEntity(localVarReturnType);
    }

    /**
     * Read the session&#39;s most recently controlled terminal
     * Read the last physical rows without changing selection or taking control. Omitted lines uses the live terminal height; larger counts include retained history. Blank rows are preserved. Screen dimensions and cursor remain relative to the live screen. Returns null when no current terminal exists. Selection is server-local and resets on restart. Experimental: may change without compatibility guarantees.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param sessionID The sessionID parameter
     * @param lines The lines parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec serverExperimentalPersistentPtyReadWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String lines) throws WebClientResponseException {
        return serverExperimentalPersistentPtyReadRequestCreation(sessionID, lines);
    }

    /**
     *
     *
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param ptyID The ptyID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec serverExperimentalPersistentPtyRemoveRequestCreation(@jakarta.annotation.Nonnull String ptyID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'ptyID' is set
        if (ptyID == null) {
            throw new WebClientResponseException("Missing the required parameter 'ptyID' when calling serverExperimentalPersistentPtyRemove", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("ptyID", ptyID);

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
        return apiClient.invokeAPI("/api/experimental/persistent-pty/{ptyID}", HttpMethod.DELETE, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     *
     *
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param ptyID The ptyID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> serverExperimentalPersistentPtyRemove(@jakarta.annotation.Nonnull String ptyID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return serverExperimentalPersistentPtyRemoveRequestCreation(ptyID).bodyToMono(localVarReturnType);
    }

    /**
     *
     *
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param ptyID The ptyID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> serverExperimentalPersistentPtyRemoveWithHttpInfo(@jakarta.annotation.Nonnull String ptyID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return serverExperimentalPersistentPtyRemoveRequestCreation(ptyID).toEntity(localVarReturnType);
    }

    /**
     *
     *
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param ptyID The ptyID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec serverExperimentalPersistentPtyRemoveWithResponseSpec(@jakarta.annotation.Nonnull String ptyID) throws WebClientResponseException {
        return serverExperimentalPersistentPtyRemoveRequestCreation(ptyID);
    }

    /**
     *
     *
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec serverExperimentalPersistentPtyShutdownRequestCreation() throws WebClientResponseException {
        Object postBody = null;
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
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/experimental/persistent-pty/shutdown", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     *
     *
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> serverExperimentalPersistentPtyShutdown() throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return serverExperimentalPersistentPtyShutdownRequestCreation().bodyToMono(localVarReturnType);
    }

    /**
     *
     *
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> serverExperimentalPersistentPtyShutdownWithHttpInfo() throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return serverExperimentalPersistentPtyShutdownRequestCreation().toEntity(localVarReturnType);
    }

    /**
     *
     *
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec serverExperimentalPersistentPtyShutdownWithResponseSpec() throws WebClientResponseException {
        return serverExperimentalPersistentPtyShutdownRequestCreation();
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param ptyID The ptyID parameter
     * @return ServerExperimentalPersistentPtySnapshot200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec serverExperimentalPersistentPtySnapshotRequestCreation(@jakarta.annotation.Nonnull String ptyID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'ptyID' is set
        if (ptyID == null) {
            throw new WebClientResponseException("Missing the required parameter 'ptyID' when calling serverExperimentalPersistentPtySnapshot", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("ptyID", ptyID);

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

        ParameterizedTypeReference<ServerExperimentalPersistentPtySnapshot200Response> localVarReturnType = new ParameterizedTypeReference<ServerExperimentalPersistentPtySnapshot200Response>() {};
        return apiClient.invokeAPI("/api/experimental/persistent-pty/{ptyID}/snapshot", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param ptyID The ptyID parameter
     * @return ServerExperimentalPersistentPtySnapshot200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ServerExperimentalPersistentPtySnapshot200Response> serverExperimentalPersistentPtySnapshot(@jakarta.annotation.Nonnull String ptyID) throws WebClientResponseException {
        ParameterizedTypeReference<ServerExperimentalPersistentPtySnapshot200Response> localVarReturnType = new ParameterizedTypeReference<ServerExperimentalPersistentPtySnapshot200Response>() {};
        return serverExperimentalPersistentPtySnapshotRequestCreation(ptyID).bodyToMono(localVarReturnType);
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param ptyID The ptyID parameter
     * @return ResponseEntity&lt;ServerExperimentalPersistentPtySnapshot200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ServerExperimentalPersistentPtySnapshot200Response>> serverExperimentalPersistentPtySnapshotWithHttpInfo(@jakarta.annotation.Nonnull String ptyID) throws WebClientResponseException {
        ParameterizedTypeReference<ServerExperimentalPersistentPtySnapshot200Response> localVarReturnType = new ParameterizedTypeReference<ServerExperimentalPersistentPtySnapshot200Response>() {};
        return serverExperimentalPersistentPtySnapshotRequestCreation(ptyID).toEntity(localVarReturnType);
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param ptyID The ptyID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec serverExperimentalPersistentPtySnapshotWithResponseSpec(@jakarta.annotation.Nonnull String ptyID) throws WebClientResponseException {
        return serverExperimentalPersistentPtySnapshotRequestCreation(ptyID);
    }

    public class ServerExperimentalPersistentPtyUpdateRequest {
        private @jakarta.annotation.Nonnull String ptyID;
        private @jakarta.annotation.Nonnull PersistentPtyUpdateInput persistentPtyUpdateInput;

        public ServerExperimentalPersistentPtyUpdateRequest() {}

        public ServerExperimentalPersistentPtyUpdateRequest(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nonnull PersistentPtyUpdateInput persistentPtyUpdateInput) {
            this.ptyID = ptyID;
            this.persistentPtyUpdateInput = persistentPtyUpdateInput;
        }

        public @jakarta.annotation.Nonnull String ptyID() {
            return this.ptyID;
        }
        public ServerExperimentalPersistentPtyUpdateRequest ptyID(@jakarta.annotation.Nonnull String ptyID) {
            this.ptyID = ptyID;
            return this;
        }

        public @jakarta.annotation.Nonnull PersistentPtyUpdateInput persistentPtyUpdateInput() {
            return this.persistentPtyUpdateInput;
        }
        public ServerExperimentalPersistentPtyUpdateRequest persistentPtyUpdateInput(@jakarta.annotation.Nonnull PersistentPtyUpdateInput persistentPtyUpdateInput) {
            this.persistentPtyUpdateInput = persistentPtyUpdateInput;
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
            ServerExperimentalPersistentPtyUpdateRequest request = (ServerExperimentalPersistentPtyUpdateRequest) o;
            return Objects.equals(this.ptyID, request.ptyID()) &&
                Objects.equals(this.persistentPtyUpdateInput, request.persistentPtyUpdateInput());
        }

        @Override
        public int hashCode() {
            return Objects.hash(ptyID, persistentPtyUpdateInput);
        }
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The serverExperimentalPersistentPtyUpdate request parameters as object
     * @return ServerExperimentalPersistentPtyGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ServerExperimentalPersistentPtyGet200Response> serverExperimentalPersistentPtyUpdate(ServerExperimentalPersistentPtyUpdateRequest requestParameters) throws WebClientResponseException {
        return this.serverExperimentalPersistentPtyUpdate(requestParameters.ptyID(), requestParameters.persistentPtyUpdateInput());
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The serverExperimentalPersistentPtyUpdate request parameters as object
     * @return ResponseEntity&lt;ServerExperimentalPersistentPtyGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ServerExperimentalPersistentPtyGet200Response>> serverExperimentalPersistentPtyUpdateWithHttpInfo(ServerExperimentalPersistentPtyUpdateRequest requestParameters) throws WebClientResponseException {
        return this.serverExperimentalPersistentPtyUpdateWithHttpInfo(requestParameters.ptyID(), requestParameters.persistentPtyUpdateInput());
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The serverExperimentalPersistentPtyUpdate request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec serverExperimentalPersistentPtyUpdateWithResponseSpec(ServerExperimentalPersistentPtyUpdateRequest requestParameters) throws WebClientResponseException {
        return this.serverExperimentalPersistentPtyUpdateWithResponseSpec(requestParameters.ptyID(), requestParameters.persistentPtyUpdateInput());
    }


    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param ptyID The ptyID parameter
     * @param persistentPtyUpdateInput The persistentPtyUpdateInput parameter
     * @return ServerExperimentalPersistentPtyGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec serverExperimentalPersistentPtyUpdateRequestCreation(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nonnull PersistentPtyUpdateInput persistentPtyUpdateInput) throws WebClientResponseException {
        Object postBody = persistentPtyUpdateInput;
        // verify the required parameter 'ptyID' is set
        if (ptyID == null) {
            throw new WebClientResponseException("Missing the required parameter 'ptyID' when calling serverExperimentalPersistentPtyUpdate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'persistentPtyUpdateInput' is set
        if (persistentPtyUpdateInput == null) {
            throw new WebClientResponseException("Missing the required parameter 'persistentPtyUpdateInput' when calling serverExperimentalPersistentPtyUpdate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("ptyID", ptyID);

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

        ParameterizedTypeReference<ServerExperimentalPersistentPtyGet200Response> localVarReturnType = new ParameterizedTypeReference<ServerExperimentalPersistentPtyGet200Response>() {};
        return apiClient.invokeAPI("/api/experimental/persistent-pty/{ptyID}", HttpMethod.PUT, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param ptyID The ptyID parameter
     * @param persistentPtyUpdateInput The persistentPtyUpdateInput parameter
     * @return ServerExperimentalPersistentPtyGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ServerExperimentalPersistentPtyGet200Response> serverExperimentalPersistentPtyUpdate(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nonnull PersistentPtyUpdateInput persistentPtyUpdateInput) throws WebClientResponseException {
        ParameterizedTypeReference<ServerExperimentalPersistentPtyGet200Response> localVarReturnType = new ParameterizedTypeReference<ServerExperimentalPersistentPtyGet200Response>() {};
        return serverExperimentalPersistentPtyUpdateRequestCreation(ptyID, persistentPtyUpdateInput).bodyToMono(localVarReturnType);
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param ptyID The ptyID parameter
     * @param persistentPtyUpdateInput The persistentPtyUpdateInput parameter
     * @return ResponseEntity&lt;ServerExperimentalPersistentPtyGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ServerExperimentalPersistentPtyGet200Response>> serverExperimentalPersistentPtyUpdateWithHttpInfo(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nonnull PersistentPtyUpdateInput persistentPtyUpdateInput) throws WebClientResponseException {
        ParameterizedTypeReference<ServerExperimentalPersistentPtyGet200Response> localVarReturnType = new ParameterizedTypeReference<ServerExperimentalPersistentPtyGet200Response>() {};
        return serverExperimentalPersistentPtyUpdateRequestCreation(ptyID, persistentPtyUpdateInput).toEntity(localVarReturnType);
    }

    /**
     *
     *
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - PtyNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param ptyID The ptyID parameter
     * @param persistentPtyUpdateInput The persistentPtyUpdateInput parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec serverExperimentalPersistentPtyUpdateWithResponseSpec(@jakarta.annotation.Nonnull String ptyID, @jakarta.annotation.Nonnull PersistentPtyUpdateInput persistentPtyUpdateInput) throws WebClientResponseException {
        return serverExperimentalPersistentPtyUpdateRequestCreation(ptyID, persistentPtyUpdateInput);
    }
}
