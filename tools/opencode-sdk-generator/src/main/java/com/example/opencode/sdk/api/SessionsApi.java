package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import java.math.BigDecimal;
import com.example.opencode.sdk.model.ConflictError;
import com.example.opencode.sdk.model.InvalidRequestError;
import com.example.opencode.sdk.model.ServiceUnavailableError;
import com.example.opencode.sdk.model.SessionHistory;
import com.example.opencode.sdk.model.SessionsResponse;
import com.example.opencode.sdk.model.UnauthorizedError;
import com.example.opencode.sdk.model.UnknownError1;
import com.example.opencode.sdk.model.V2SessionActive200Response;
import com.example.opencode.sdk.model.V2SessionContext200Response;
import com.example.opencode.sdk.model.V2SessionCreate200Response;
import com.example.opencode.sdk.model.V2SessionCreateRequest;
import com.example.opencode.sdk.model.V2SessionEvents200Response;
import com.example.opencode.sdk.model.V2SessionGet200Response;
import com.example.opencode.sdk.model.V2SessionGet404Response;
import com.example.opencode.sdk.model.V2SessionList400Response;
import com.example.opencode.sdk.model.V2SessionMessage200Response;
import com.example.opencode.sdk.model.V2SessionPrompt200Response;
import com.example.opencode.sdk.model.V2SessionPromptRequest;
import com.example.opencode.sdk.model.V2SessionRevertStage200Response;
import com.example.opencode.sdk.model.V2SessionRevertStage404Response;
import com.example.opencode.sdk.model.V2SessionRevertStageRequest;
import com.example.opencode.sdk.model.V2SessionSwitchAgentRequest;
import com.example.opencode.sdk.model.V2SessionSwitchModelRequest;

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
     * List active sessions
     * Retrieve foreground Session drains currently owned by this OpenCode process. Sessions absent from the result are inactive.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return V2SessionActive200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2SessionActiveRequestCreation() throws WebClientResponseException {
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

        ParameterizedTypeReference<V2SessionActive200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionActive200Response>() {};
        return apiClient.invokeAPI("/api/session/active", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List active sessions
     * Retrieve foreground Session drains currently owned by this OpenCode process. Sessions absent from the result are inactive.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return V2SessionActive200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2SessionActive200Response> v2SessionActive() throws WebClientResponseException {
        ParameterizedTypeReference<V2SessionActive200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionActive200Response>() {};
        return v2SessionActiveRequestCreation().bodyToMono(localVarReturnType);
    }

    /**
     * List active sessions
     * Retrieve foreground Session drains currently owned by this OpenCode process. Sessions absent from the result are inactive.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return ResponseEntity&lt;V2SessionActive200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2SessionActive200Response>> v2SessionActiveWithHttpInfo() throws WebClientResponseException {
        ParameterizedTypeReference<V2SessionActive200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionActive200Response>() {};
        return v2SessionActiveRequestCreation().toEntity(localVarReturnType);
    }

    /**
     * List active sessions
     * Retrieve foreground Session drains currently owned by this OpenCode process. Sessions absent from the result are inactive.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionActiveWithResponseSpec() throws WebClientResponseException {
        return v2SessionActiveRequestCreation();
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

    public class V2SessionEventsRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String after;

        public V2SessionEventsRequest() {}

        public V2SessionEventsRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String after) {
            this.sessionID = sessionID;
            this.after = after;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public V2SessionEventsRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String after() {
            return this.after;
        }
        public V2SessionEventsRequest after(@jakarta.annotation.Nullable String after) {
            this.after = after;
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
            V2SessionEventsRequest request = (V2SessionEventsRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.after, request.after());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, after);
        }
    }

    /**
     * Subscribe to session events
     * Replay durable events after an aggregate sequence, then continue with new durable events.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The v2SessionEvents request parameters as object
     * @return V2SessionEvents200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2SessionEvents200Response> v2SessionEvents(V2SessionEventsRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionEvents(requestParameters.sessionID(), requestParameters.after());
    }

    /**
     * Subscribe to session events
     * Replay durable events after an aggregate sequence, then continue with new durable events.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The v2SessionEvents request parameters as object
     * @return ResponseEntity&lt;V2SessionEvents200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2SessionEvents200Response>> v2SessionEventsWithHttpInfo(V2SessionEventsRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionEventsWithHttpInfo(requestParameters.sessionID(), requestParameters.after());
    }

    /**
     * Subscribe to session events
     * Replay durable events after an aggregate sequence, then continue with new durable events.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The v2SessionEvents request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionEventsWithResponseSpec(V2SessionEventsRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionEventsWithResponseSpec(requestParameters.sessionID(), requestParameters.after());
    }


    /**
     * Subscribe to session events
     * Replay durable events after an aggregate sequence, then continue with new durable events.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param after The after parameter
     * @return V2SessionEvents200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2SessionEventsRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String after) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling v2SessionEvents", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "after", after));

        final String[] localVarAccepts = {
            "text/event-stream", "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<V2SessionEvents200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionEvents200Response>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/event", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Subscribe to session events
     * Replay durable events after an aggregate sequence, then continue with new durable events.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param after The after parameter
     * @return V2SessionEvents200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2SessionEvents200Response> v2SessionEvents(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String after) throws WebClientResponseException {
        ParameterizedTypeReference<V2SessionEvents200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionEvents200Response>() {};
        return v2SessionEventsRequestCreation(sessionID, after).bodyToMono(localVarReturnType);
    }

    /**
     * Subscribe to session events
     * Replay durable events after an aggregate sequence, then continue with new durable events.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param after The after parameter
     * @return ResponseEntity&lt;V2SessionEvents200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2SessionEvents200Response>> v2SessionEventsWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String after) throws WebClientResponseException {
        ParameterizedTypeReference<V2SessionEvents200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionEvents200Response>() {};
        return v2SessionEventsRequestCreation(sessionID, after).toEntity(localVarReturnType);
    }

    /**
     * Subscribe to session events
     * Replay durable events after an aggregate sequence, then continue with new durable events.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param after The after parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionEventsWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String after) throws WebClientResponseException {
        return v2SessionEventsRequestCreation(sessionID, after);
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

    public class V2SessionHistoryRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String limit;
        private @jakarta.annotation.Nullable String after;

        public V2SessionHistoryRequest() {}

        public V2SessionHistoryRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String limit, @jakarta.annotation.Nullable String after) {
            this.sessionID = sessionID;
            this.limit = limit;
            this.after = after;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public V2SessionHistoryRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String limit() {
            return this.limit;
        }
        public V2SessionHistoryRequest limit(@jakarta.annotation.Nullable String limit) {
            this.limit = limit;
            return this;
        }

        public @jakarta.annotation.Nullable String after() {
            return this.after;
        }
        public V2SessionHistoryRequest after(@jakarta.annotation.Nullable String after) {
            this.after = after;
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
            V2SessionHistoryRequest request = (V2SessionHistoryRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.limit, request.limit()) &&
                Objects.equals(this.after, request.after());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, limit, after);
        }
    }

    /**
     * Get session history
     * Read one finite page of public durable Session events after an exclusive aggregate sequence. Newly committed events may appear on later pages.
     * <p><b>200</b> - SessionHistory
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The v2SessionHistory request parameters as object
     * @return SessionHistory
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionHistory> v2SessionHistory(V2SessionHistoryRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionHistory(requestParameters.sessionID(), requestParameters.limit(), requestParameters.after());
    }

    /**
     * Get session history
     * Read one finite page of public durable Session events after an exclusive aggregate sequence. Newly committed events may appear on later pages.
     * <p><b>200</b> - SessionHistory
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The v2SessionHistory request parameters as object
     * @return ResponseEntity&lt;SessionHistory&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionHistory>> v2SessionHistoryWithHttpInfo(V2SessionHistoryRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionHistoryWithHttpInfo(requestParameters.sessionID(), requestParameters.limit(), requestParameters.after());
    }

    /**
     * Get session history
     * Read one finite page of public durable Session events after an exclusive aggregate sequence. Newly committed events may appear on later pages.
     * <p><b>200</b> - SessionHistory
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The v2SessionHistory request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionHistoryWithResponseSpec(V2SessionHistoryRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionHistoryWithResponseSpec(requestParameters.sessionID(), requestParameters.limit(), requestParameters.after());
    }


    /**
     * Get session history
     * Read one finite page of public durable Session events after an exclusive aggregate sequence. Newly committed events may appear on later pages.
     * <p><b>200</b> - SessionHistory
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param limit The limit parameter
     * @param after The after parameter
     * @return SessionHistory
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2SessionHistoryRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String limit, @jakarta.annotation.Nullable String after) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling v2SessionHistory", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "limit", limit));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "after", after));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<SessionHistory> localVarReturnType = new ParameterizedTypeReference<SessionHistory>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/history", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get session history
     * Read one finite page of public durable Session events after an exclusive aggregate sequence. Newly committed events may appear on later pages.
     * <p><b>200</b> - SessionHistory
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param limit The limit parameter
     * @param after The after parameter
     * @return SessionHistory
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionHistory> v2SessionHistory(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String limit, @jakarta.annotation.Nullable String after) throws WebClientResponseException {
        ParameterizedTypeReference<SessionHistory> localVarReturnType = new ParameterizedTypeReference<SessionHistory>() {};
        return v2SessionHistoryRequestCreation(sessionID, limit, after).bodyToMono(localVarReturnType);
    }

    /**
     * Get session history
     * Read one finite page of public durable Session events after an exclusive aggregate sequence. Newly committed events may appear on later pages.
     * <p><b>200</b> - SessionHistory
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param limit The limit parameter
     * @param after The after parameter
     * @return ResponseEntity&lt;SessionHistory&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionHistory>> v2SessionHistoryWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String limit, @jakarta.annotation.Nullable String after) throws WebClientResponseException {
        ParameterizedTypeReference<SessionHistory> localVarReturnType = new ParameterizedTypeReference<SessionHistory>() {};
        return v2SessionHistoryRequestCreation(sessionID, limit, after).toEntity(localVarReturnType);
    }

    /**
     * Get session history
     * Read one finite page of public durable Session events after an exclusive aggregate sequence. Newly committed events may appear on later pages.
     * <p><b>200</b> - SessionHistory
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param limit The limit parameter
     * @param after The after parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionHistoryWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String limit, @jakarta.annotation.Nullable String after) throws WebClientResponseException {
        return v2SessionHistoryRequestCreation(sessionID, limit, after);
    }

    /**
     * Interrupt session execution
     * Interrupt active execution owned by this OpenCode process. Idle interruption is a no-op.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2SessionInterruptRequestCreation(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling v2SessionInterrupt", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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
        return apiClient.invokeAPI("/api/session/{sessionID}/interrupt", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Interrupt session execution
     * Interrupt active execution owned by this OpenCode process. Idle interruption is a no-op.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2SessionInterrupt(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2SessionInterruptRequestCreation(sessionID).bodyToMono(localVarReturnType);
    }

    /**
     * Interrupt session execution
     * Interrupt active execution owned by this OpenCode process. Idle interruption is a no-op.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2SessionInterruptWithHttpInfo(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2SessionInterruptRequestCreation(sessionID).toEntity(localVarReturnType);
    }

    /**
     * Interrupt session execution
     * Interrupt active execution owned by this OpenCode process. Idle interruption is a no-op.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionInterruptWithResponseSpec(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        return v2SessionInterruptRequestCreation(sessionID);
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

    public class V2SessionMessageRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull String messageID;

        public V2SessionMessageRequest() {}

        public V2SessionMessageRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String messageID) {
            this.sessionID = sessionID;
            this.messageID = messageID;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public V2SessionMessageRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull String messageID() {
            return this.messageID;
        }
        public V2SessionMessageRequest messageID(@jakarta.annotation.Nonnull String messageID) {
            this.messageID = messageID;
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
            V2SessionMessageRequest request = (V2SessionMessageRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.messageID, request.messageID());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, messageID);
        }
    }

    /**
     * Get session message
     * Retrieve one projected message owned by the Session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | MessageNotFoundError
     * @param requestParameters The v2SessionMessage request parameters as object
     * @return V2SessionMessage200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2SessionMessage200Response> v2SessionMessage(V2SessionMessageRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionMessage(requestParameters.sessionID(), requestParameters.messageID());
    }

    /**
     * Get session message
     * Retrieve one projected message owned by the Session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | MessageNotFoundError
     * @param requestParameters The v2SessionMessage request parameters as object
     * @return ResponseEntity&lt;V2SessionMessage200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2SessionMessage200Response>> v2SessionMessageWithHttpInfo(V2SessionMessageRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionMessageWithHttpInfo(requestParameters.sessionID(), requestParameters.messageID());
    }

    /**
     * Get session message
     * Retrieve one projected message owned by the Session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | MessageNotFoundError
     * @param requestParameters The v2SessionMessage request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionMessageWithResponseSpec(V2SessionMessageRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionMessageWithResponseSpec(requestParameters.sessionID(), requestParameters.messageID());
    }


    /**
     * Get session message
     * Retrieve one projected message owned by the Session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | MessageNotFoundError
     * @param sessionID The sessionID parameter
     * @param messageID The messageID parameter
     * @return V2SessionMessage200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2SessionMessageRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String messageID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling v2SessionMessage", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'messageID' is set
        if (messageID == null) {
            throw new WebClientResponseException("Missing the required parameter 'messageID' when calling v2SessionMessage", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);
        pathParams.put("messageID", messageID);

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

        ParameterizedTypeReference<V2SessionMessage200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionMessage200Response>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/message/{messageID}", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get session message
     * Retrieve one projected message owned by the Session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | MessageNotFoundError
     * @param sessionID The sessionID parameter
     * @param messageID The messageID parameter
     * @return V2SessionMessage200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2SessionMessage200Response> v2SessionMessage(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String messageID) throws WebClientResponseException {
        ParameterizedTypeReference<V2SessionMessage200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionMessage200Response>() {};
        return v2SessionMessageRequestCreation(sessionID, messageID).bodyToMono(localVarReturnType);
    }

    /**
     * Get session message
     * Retrieve one projected message owned by the Session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | MessageNotFoundError
     * @param sessionID The sessionID parameter
     * @param messageID The messageID parameter
     * @return ResponseEntity&lt;V2SessionMessage200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2SessionMessage200Response>> v2SessionMessageWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String messageID) throws WebClientResponseException {
        ParameterizedTypeReference<V2SessionMessage200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionMessage200Response>() {};
        return v2SessionMessageRequestCreation(sessionID, messageID).toEntity(localVarReturnType);
    }

    /**
     * Get session message
     * Retrieve one projected message owned by the Session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | MessageNotFoundError
     * @param sessionID The sessionID parameter
     * @param messageID The messageID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionMessageWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String messageID) throws WebClientResponseException {
        return v2SessionMessageRequestCreation(sessionID, messageID);
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
     * Clear staged revert
     *
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2SessionRevertClearRequestCreation(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling v2SessionRevertClear", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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
        return apiClient.invokeAPI("/api/session/{sessionID}/revert/clear", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Clear staged revert
     *
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2SessionRevertClear(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2SessionRevertClearRequestCreation(sessionID).bodyToMono(localVarReturnType);
    }

    /**
     * Clear staged revert
     *
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2SessionRevertClearWithHttpInfo(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2SessionRevertClearRequestCreation(sessionID).toEntity(localVarReturnType);
    }

    /**
     * Clear staged revert
     *
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionRevertClearWithResponseSpec(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        return v2SessionRevertClearRequestCreation(sessionID);
    }

    /**
     * Commit staged revert
     *
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2SessionRevertCommitRequestCreation(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling v2SessionRevertCommit", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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
        return apiClient.invokeAPI("/api/session/{sessionID}/revert/commit", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Commit staged revert
     *
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2SessionRevertCommit(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2SessionRevertCommitRequestCreation(sessionID).bodyToMono(localVarReturnType);
    }

    /**
     * Commit staged revert
     *
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2SessionRevertCommitWithHttpInfo(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2SessionRevertCommitRequestCreation(sessionID).toEntity(localVarReturnType);
    }

    /**
     * Commit staged revert
     *
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionRevertCommitWithResponseSpec(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        return v2SessionRevertCommitRequestCreation(sessionID);
    }

    public class V2SessionRevertStageRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull V2SessionRevertStageRequest v2SessionRevertStageRequest;

        public V2SessionRevertStageRequest() {}

        public V2SessionRevertStageRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull V2SessionRevertStageRequest v2SessionRevertStageRequest) {
            this.sessionID = sessionID;
            this.v2SessionRevertStageRequest = v2SessionRevertStageRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public V2SessionRevertStageRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull V2SessionRevertStageRequest v2SessionRevertStageRequest() {
            return this.v2SessionRevertStageRequest;
        }
        public V2SessionRevertStageRequest v2SessionRevertStageRequest(@jakarta.annotation.Nonnull V2SessionRevertStageRequest v2SessionRevertStageRequest) {
            this.v2SessionRevertStageRequest = v2SessionRevertStageRequest;
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
            V2SessionRevertStageRequest request = (V2SessionRevertStageRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.v2SessionRevertStageRequest, request.v2SessionRevertStageRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, v2SessionRevertStageRequest);
        }
    }

    /**
     * Stage session revert
     * Stage or move a reversible session boundary and optionally apply its file changes.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - MessageNotFoundError | SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param requestParameters The v2SessionRevertStage request parameters as object
     * @return V2SessionRevertStage200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2SessionRevertStage200Response> v2SessionRevertStage(V2SessionRevertStageRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionRevertStage(requestParameters.sessionID(), requestParameters.v2SessionRevertStageRequest());
    }

    /**
     * Stage session revert
     * Stage or move a reversible session boundary and optionally apply its file changes.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - MessageNotFoundError | SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param requestParameters The v2SessionRevertStage request parameters as object
     * @return ResponseEntity&lt;V2SessionRevertStage200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2SessionRevertStage200Response>> v2SessionRevertStageWithHttpInfo(V2SessionRevertStageRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionRevertStageWithHttpInfo(requestParameters.sessionID(), requestParameters.v2SessionRevertStageRequest());
    }

    /**
     * Stage session revert
     * Stage or move a reversible session boundary and optionally apply its file changes.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - MessageNotFoundError | SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param requestParameters The v2SessionRevertStage request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionRevertStageWithResponseSpec(V2SessionRevertStageRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionRevertStageWithResponseSpec(requestParameters.sessionID(), requestParameters.v2SessionRevertStageRequest());
    }


    /**
     * Stage session revert
     * Stage or move a reversible session boundary and optionally apply its file changes.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - MessageNotFoundError | SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @param v2SessionRevertStageRequest The v2SessionRevertStageRequest parameter
     * @return V2SessionRevertStage200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2SessionRevertStageRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull V2SessionRevertStageRequest v2SessionRevertStageRequest) throws WebClientResponseException {
        Object postBody = v2SessionRevertStageRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling v2SessionRevertStage", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'v2SessionRevertStageRequest' is set
        if (v2SessionRevertStageRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'v2SessionRevertStageRequest' when calling v2SessionRevertStage", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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

        ParameterizedTypeReference<V2SessionRevertStage200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionRevertStage200Response>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/revert/stage", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Stage session revert
     * Stage or move a reversible session boundary and optionally apply its file changes.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - MessageNotFoundError | SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @param v2SessionRevertStageRequest The v2SessionRevertStageRequest parameter
     * @return V2SessionRevertStage200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2SessionRevertStage200Response> v2SessionRevertStage(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull V2SessionRevertStageRequest v2SessionRevertStageRequest) throws WebClientResponseException {
        ParameterizedTypeReference<V2SessionRevertStage200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionRevertStage200Response>() {};
        return v2SessionRevertStageRequestCreation(sessionID, v2SessionRevertStageRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Stage session revert
     * Stage or move a reversible session boundary and optionally apply its file changes.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - MessageNotFoundError | SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @param v2SessionRevertStageRequest The v2SessionRevertStageRequest parameter
     * @return ResponseEntity&lt;V2SessionRevertStage200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2SessionRevertStage200Response>> v2SessionRevertStageWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull V2SessionRevertStageRequest v2SessionRevertStageRequest) throws WebClientResponseException {
        ParameterizedTypeReference<V2SessionRevertStage200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionRevertStage200Response>() {};
        return v2SessionRevertStageRequestCreation(sessionID, v2SessionRevertStageRequest).toEntity(localVarReturnType);
    }

    /**
     * Stage session revert
     * Stage or move a reversible session boundary and optionally apply its file changes.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - MessageNotFoundError | SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @param v2SessionRevertStageRequest The v2SessionRevertStageRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionRevertStageWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull V2SessionRevertStageRequest v2SessionRevertStageRequest) throws WebClientResponseException {
        return v2SessionRevertStageRequestCreation(sessionID, v2SessionRevertStageRequest);
    }

    public class V2SessionSwitchAgentRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull V2SessionSwitchAgentRequest v2SessionSwitchAgentRequest;

        public V2SessionSwitchAgentRequest() {}

        public V2SessionSwitchAgentRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull V2SessionSwitchAgentRequest v2SessionSwitchAgentRequest) {
            this.sessionID = sessionID;
            this.v2SessionSwitchAgentRequest = v2SessionSwitchAgentRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public V2SessionSwitchAgentRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull V2SessionSwitchAgentRequest v2SessionSwitchAgentRequest() {
            return this.v2SessionSwitchAgentRequest;
        }
        public V2SessionSwitchAgentRequest v2SessionSwitchAgentRequest(@jakarta.annotation.Nonnull V2SessionSwitchAgentRequest v2SessionSwitchAgentRequest) {
            this.v2SessionSwitchAgentRequest = v2SessionSwitchAgentRequest;
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
            V2SessionSwitchAgentRequest request = (V2SessionSwitchAgentRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.v2SessionSwitchAgentRequest, request.v2SessionSwitchAgentRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, v2SessionSwitchAgentRequest);
        }
    }

    /**
     * Switch session agent
     * Switch the agent used by subsequent provider turns.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The v2SessionSwitchAgent request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2SessionSwitchAgent(V2SessionSwitchAgentRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionSwitchAgent(requestParameters.sessionID(), requestParameters.v2SessionSwitchAgentRequest());
    }

    /**
     * Switch session agent
     * Switch the agent used by subsequent provider turns.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The v2SessionSwitchAgent request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2SessionSwitchAgentWithHttpInfo(V2SessionSwitchAgentRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionSwitchAgentWithHttpInfo(requestParameters.sessionID(), requestParameters.v2SessionSwitchAgentRequest());
    }

    /**
     * Switch session agent
     * Switch the agent used by subsequent provider turns.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The v2SessionSwitchAgent request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionSwitchAgentWithResponseSpec(V2SessionSwitchAgentRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionSwitchAgentWithResponseSpec(requestParameters.sessionID(), requestParameters.v2SessionSwitchAgentRequest());
    }


    /**
     * Switch session agent
     * Switch the agent used by subsequent provider turns.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param v2SessionSwitchAgentRequest The v2SessionSwitchAgentRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2SessionSwitchAgentRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull V2SessionSwitchAgentRequest v2SessionSwitchAgentRequest) throws WebClientResponseException {
        Object postBody = v2SessionSwitchAgentRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling v2SessionSwitchAgent", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'v2SessionSwitchAgentRequest' is set
        if (v2SessionSwitchAgentRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'v2SessionSwitchAgentRequest' when calling v2SessionSwitchAgent", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/agent", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Switch session agent
     * Switch the agent used by subsequent provider turns.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param v2SessionSwitchAgentRequest The v2SessionSwitchAgentRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2SessionSwitchAgent(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull V2SessionSwitchAgentRequest v2SessionSwitchAgentRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2SessionSwitchAgentRequestCreation(sessionID, v2SessionSwitchAgentRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Switch session agent
     * Switch the agent used by subsequent provider turns.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param v2SessionSwitchAgentRequest The v2SessionSwitchAgentRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2SessionSwitchAgentWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull V2SessionSwitchAgentRequest v2SessionSwitchAgentRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2SessionSwitchAgentRequestCreation(sessionID, v2SessionSwitchAgentRequest).toEntity(localVarReturnType);
    }

    /**
     * Switch session agent
     * Switch the agent used by subsequent provider turns.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param v2SessionSwitchAgentRequest The v2SessionSwitchAgentRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionSwitchAgentWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull V2SessionSwitchAgentRequest v2SessionSwitchAgentRequest) throws WebClientResponseException {
        return v2SessionSwitchAgentRequestCreation(sessionID, v2SessionSwitchAgentRequest);
    }

    public class V2SessionSwitchModelRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull V2SessionSwitchModelRequest v2SessionSwitchModelRequest;

        public V2SessionSwitchModelRequest() {}

        public V2SessionSwitchModelRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull V2SessionSwitchModelRequest v2SessionSwitchModelRequest) {
            this.sessionID = sessionID;
            this.v2SessionSwitchModelRequest = v2SessionSwitchModelRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public V2SessionSwitchModelRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull V2SessionSwitchModelRequest v2SessionSwitchModelRequest() {
            return this.v2SessionSwitchModelRequest;
        }
        public V2SessionSwitchModelRequest v2SessionSwitchModelRequest(@jakarta.annotation.Nonnull V2SessionSwitchModelRequest v2SessionSwitchModelRequest) {
            this.v2SessionSwitchModelRequest = v2SessionSwitchModelRequest;
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
            V2SessionSwitchModelRequest request = (V2SessionSwitchModelRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.v2SessionSwitchModelRequest, request.v2SessionSwitchModelRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, v2SessionSwitchModelRequest);
        }
    }

    /**
     * Switch session model
     * Switch the model used by subsequent provider turns.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The v2SessionSwitchModel request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2SessionSwitchModel(V2SessionSwitchModelRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionSwitchModel(requestParameters.sessionID(), requestParameters.v2SessionSwitchModelRequest());
    }

    /**
     * Switch session model
     * Switch the model used by subsequent provider turns.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The v2SessionSwitchModel request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2SessionSwitchModelWithHttpInfo(V2SessionSwitchModelRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionSwitchModelWithHttpInfo(requestParameters.sessionID(), requestParameters.v2SessionSwitchModelRequest());
    }

    /**
     * Switch session model
     * Switch the model used by subsequent provider turns.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The v2SessionSwitchModel request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionSwitchModelWithResponseSpec(V2SessionSwitchModelRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionSwitchModelWithResponseSpec(requestParameters.sessionID(), requestParameters.v2SessionSwitchModelRequest());
    }


    /**
     * Switch session model
     * Switch the model used by subsequent provider turns.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param v2SessionSwitchModelRequest The v2SessionSwitchModelRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2SessionSwitchModelRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull V2SessionSwitchModelRequest v2SessionSwitchModelRequest) throws WebClientResponseException {
        Object postBody = v2SessionSwitchModelRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling v2SessionSwitchModel", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'v2SessionSwitchModelRequest' is set
        if (v2SessionSwitchModelRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'v2SessionSwitchModelRequest' when calling v2SessionSwitchModel", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/model", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Switch session model
     * Switch the model used by subsequent provider turns.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param v2SessionSwitchModelRequest The v2SessionSwitchModelRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2SessionSwitchModel(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull V2SessionSwitchModelRequest v2SessionSwitchModelRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2SessionSwitchModelRequestCreation(sessionID, v2SessionSwitchModelRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Switch session model
     * Switch the model used by subsequent provider turns.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param v2SessionSwitchModelRequest The v2SessionSwitchModelRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2SessionSwitchModelWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull V2SessionSwitchModelRequest v2SessionSwitchModelRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2SessionSwitchModelRequestCreation(sessionID, v2SessionSwitchModelRequest).toEntity(localVarReturnType);
    }

    /**
     * Switch session model
     * Switch the model used by subsequent provider turns.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param v2SessionSwitchModelRequest The v2SessionSwitchModelRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionSwitchModelWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull V2SessionSwitchModelRequest v2SessionSwitchModelRequest) throws WebClientResponseException {
        return v2SessionSwitchModelRequestCreation(sessionID, v2SessionSwitchModelRequest);
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
