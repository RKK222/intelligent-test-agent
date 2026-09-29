package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.AgentListLocationParameter;
import com.example.opencode.sdk.model.InvalidRequestErrorEncoded;
import com.example.opencode.sdk.model.PermissionRequestList200Response;
import com.example.opencode.sdk.model.PermissionSavedList200Response;
import com.example.opencode.sdk.model.SessionNotFoundErrorEncoded;
import com.example.opencode.sdk.model.SessionPermissionCreate200Response;
import com.example.opencode.sdk.model.SessionPermissionCreateRequest;
import com.example.opencode.sdk.model.SessionPermissionGet200Response;
import com.example.opencode.sdk.model.SessionPermissionGet404Response;
import com.example.opencode.sdk.model.SessionPermissionList200Response;
import com.example.opencode.sdk.model.SessionPermissionReplyRequest;
import com.example.opencode.sdk.model.SessionUpdate404Response;
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
public class PermissionApi {
    private ApiClient apiClient;

    public PermissionApi() {
        this(new ApiClient());
    }

    public PermissionApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * List pending permission requests
     * Retrieve pending permission requests for a location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return PermissionRequestList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec permissionRequestListRequestCreation(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
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

        ParameterizedTypeReference<PermissionRequestList200Response> localVarReturnType = new ParameterizedTypeReference<PermissionRequestList200Response>() {};
        return apiClient.invokeAPI("/api/permission/request", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List pending permission requests
     * Retrieve pending permission requests for a location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return PermissionRequestList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<PermissionRequestList200Response> permissionRequestList(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<PermissionRequestList200Response> localVarReturnType = new ParameterizedTypeReference<PermissionRequestList200Response>() {};
        return permissionRequestListRequestCreation(location).bodyToMono(localVarReturnType);
    }

    /**
     * List pending permission requests
     * Retrieve pending permission requests for a location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseEntity&lt;PermissionRequestList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<PermissionRequestList200Response>> permissionRequestListWithHttpInfo(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<PermissionRequestList200Response> localVarReturnType = new ParameterizedTypeReference<PermissionRequestList200Response>() {};
        return permissionRequestListRequestCreation(location).toEntity(localVarReturnType);
    }

    /**
     * List pending permission requests
     * Retrieve pending permission requests for a location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec permissionRequestListWithResponseSpec(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return permissionRequestListRequestCreation(location);
    }

    /**
     * List saved permissions
     * Retrieve saved permissions, optionally filtered by project.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param projectID The projectID parameter
     * @return PermissionSavedList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec permissionSavedListRequestCreation(@jakarta.annotation.Nullable String projectID) throws WebClientResponseException {
        Object postBody = null;
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "projectID", projectID));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<PermissionSavedList200Response> localVarReturnType = new ParameterizedTypeReference<PermissionSavedList200Response>() {};
        return apiClient.invokeAPI("/api/permission/saved", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List saved permissions
     * Retrieve saved permissions, optionally filtered by project.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param projectID The projectID parameter
     * @return PermissionSavedList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<PermissionSavedList200Response> permissionSavedList(@jakarta.annotation.Nullable String projectID) throws WebClientResponseException {
        ParameterizedTypeReference<PermissionSavedList200Response> localVarReturnType = new ParameterizedTypeReference<PermissionSavedList200Response>() {};
        return permissionSavedListRequestCreation(projectID).bodyToMono(localVarReturnType);
    }

    /**
     * List saved permissions
     * Retrieve saved permissions, optionally filtered by project.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param projectID The projectID parameter
     * @return ResponseEntity&lt;PermissionSavedList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<PermissionSavedList200Response>> permissionSavedListWithHttpInfo(@jakarta.annotation.Nullable String projectID) throws WebClientResponseException {
        ParameterizedTypeReference<PermissionSavedList200Response> localVarReturnType = new ParameterizedTypeReference<PermissionSavedList200Response>() {};
        return permissionSavedListRequestCreation(projectID).toEntity(localVarReturnType);
    }

    /**
     * List saved permissions
     * Retrieve saved permissions, optionally filtered by project.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param projectID The projectID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec permissionSavedListWithResponseSpec(@jakarta.annotation.Nullable String projectID) throws WebClientResponseException {
        return permissionSavedListRequestCreation(projectID);
    }

    /**
     * Remove saved permission
     * Remove a saved permission by ID.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param id The id parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec permissionSavedRemoveRequestCreation(@jakarta.annotation.Nullable String id) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'id' is set
        if (id == null) {
            throw new WebClientResponseException("Missing the required parameter 'id' when calling permissionSavedRemove", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("id", id);

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
        return apiClient.invokeAPI("/api/permission/saved/{id}", HttpMethod.DELETE, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Remove saved permission
     * Remove a saved permission by ID.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param id The id parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> permissionSavedRemove(@jakarta.annotation.Nullable String id) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return permissionSavedRemoveRequestCreation(id).bodyToMono(localVarReturnType);
    }

    /**
     * Remove saved permission
     * Remove a saved permission by ID.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param id The id parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> permissionSavedRemoveWithHttpInfo(@jakarta.annotation.Nullable String id) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return permissionSavedRemoveRequestCreation(id).toEntity(localVarReturnType);
    }

    /**
     * Remove saved permission
     * Remove a saved permission by ID.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param id The id parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec permissionSavedRemoveWithResponseSpec(@jakarta.annotation.Nullable String id) throws WebClientResponseException {
        return permissionSavedRemoveRequestCreation(id);
    }

    public class SessionPermissionCreateRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull SessionPermissionCreateRequest sessionPermissionCreateRequest;

        public SessionPermissionCreateRequest() {}

        public SessionPermissionCreateRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionPermissionCreateRequest sessionPermissionCreateRequest) {
            this.sessionID = sessionID;
            this.sessionPermissionCreateRequest = sessionPermissionCreateRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionPermissionCreateRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull SessionPermissionCreateRequest sessionPermissionCreateRequest() {
            return this.sessionPermissionCreateRequest;
        }
        public SessionPermissionCreateRequest sessionPermissionCreateRequest(@jakarta.annotation.Nonnull SessionPermissionCreateRequest sessionPermissionCreateRequest) {
            this.sessionPermissionCreateRequest = sessionPermissionCreateRequest;
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
            SessionPermissionCreateRequest request = (SessionPermissionCreateRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.sessionPermissionCreateRequest, request.sessionPermissionCreateRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, sessionPermissionCreateRequest);
        }
    }

    /**
     * Create permission request
     * Evaluate and, when approval is required, create a permission request for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionPermissionCreate request parameters as object
     * @return SessionPermissionCreate200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionPermissionCreate200Response> sessionPermissionCreate(SessionPermissionCreateRequest requestParameters) throws WebClientResponseException {
        return this.sessionPermissionCreate(requestParameters.sessionID(), requestParameters.sessionPermissionCreateRequest());
    }

    /**
     * Create permission request
     * Evaluate and, when approval is required, create a permission request for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionPermissionCreate request parameters as object
     * @return ResponseEntity&lt;SessionPermissionCreate200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionPermissionCreate200Response>> sessionPermissionCreateWithHttpInfo(SessionPermissionCreateRequest requestParameters) throws WebClientResponseException {
        return this.sessionPermissionCreateWithHttpInfo(requestParameters.sessionID(), requestParameters.sessionPermissionCreateRequest());
    }

    /**
     * Create permission request
     * Evaluate and, when approval is required, create a permission request for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionPermissionCreate request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionPermissionCreateWithResponseSpec(SessionPermissionCreateRequest requestParameters) throws WebClientResponseException {
        return this.sessionPermissionCreateWithResponseSpec(requestParameters.sessionID(), requestParameters.sessionPermissionCreateRequest());
    }


    /**
     * Create permission request
     * Evaluate and, when approval is required, create a permission request for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionPermissionCreateRequest The sessionPermissionCreateRequest parameter
     * @return SessionPermissionCreate200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionPermissionCreateRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionPermissionCreateRequest sessionPermissionCreateRequest) throws WebClientResponseException {
        Object postBody = sessionPermissionCreateRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionPermissionCreate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'sessionPermissionCreateRequest' is set
        if (sessionPermissionCreateRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionPermissionCreateRequest' when calling sessionPermissionCreate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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

        ParameterizedTypeReference<SessionPermissionCreate200Response> localVarReturnType = new ParameterizedTypeReference<SessionPermissionCreate200Response>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/permission", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Create permission request
     * Evaluate and, when approval is required, create a permission request for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionPermissionCreateRequest The sessionPermissionCreateRequest parameter
     * @return SessionPermissionCreate200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionPermissionCreate200Response> sessionPermissionCreate(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionPermissionCreateRequest sessionPermissionCreateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<SessionPermissionCreate200Response> localVarReturnType = new ParameterizedTypeReference<SessionPermissionCreate200Response>() {};
        return sessionPermissionCreateRequestCreation(sessionID, sessionPermissionCreateRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Create permission request
     * Evaluate and, when approval is required, create a permission request for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionPermissionCreateRequest The sessionPermissionCreateRequest parameter
     * @return ResponseEntity&lt;SessionPermissionCreate200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionPermissionCreate200Response>> sessionPermissionCreateWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionPermissionCreateRequest sessionPermissionCreateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<SessionPermissionCreate200Response> localVarReturnType = new ParameterizedTypeReference<SessionPermissionCreate200Response>() {};
        return sessionPermissionCreateRequestCreation(sessionID, sessionPermissionCreateRequest).toEntity(localVarReturnType);
    }

    /**
     * Create permission request
     * Evaluate and, when approval is required, create a permission request for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionPermissionCreateRequest The sessionPermissionCreateRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionPermissionCreateWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionPermissionCreateRequest sessionPermissionCreateRequest) throws WebClientResponseException {
        return sessionPermissionCreateRequestCreation(sessionID, sessionPermissionCreateRequest);
    }

    public class SessionPermissionGetRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String requestID;

        public SessionPermissionGetRequest() {}

        public SessionPermissionGetRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String requestID) {
            this.sessionID = sessionID;
            this.requestID = requestID;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionPermissionGetRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String requestID() {
            return this.requestID;
        }
        public SessionPermissionGetRequest requestID(@jakarta.annotation.Nullable String requestID) {
            this.requestID = requestID;
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
            SessionPermissionGetRequest request = (SessionPermissionGetRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.requestID, request.requestID());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, requestID);
        }
    }

    /**
     * Get permission request
     * Retrieve a pending permission request owned by a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | PermissionNotFoundError
     * @param requestParameters The sessionPermissionGet request parameters as object
     * @return SessionPermissionGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionPermissionGet200Response> sessionPermissionGet(SessionPermissionGetRequest requestParameters) throws WebClientResponseException {
        return this.sessionPermissionGet(requestParameters.sessionID(), requestParameters.requestID());
    }

    /**
     * Get permission request
     * Retrieve a pending permission request owned by a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | PermissionNotFoundError
     * @param requestParameters The sessionPermissionGet request parameters as object
     * @return ResponseEntity&lt;SessionPermissionGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionPermissionGet200Response>> sessionPermissionGetWithHttpInfo(SessionPermissionGetRequest requestParameters) throws WebClientResponseException {
        return this.sessionPermissionGetWithHttpInfo(requestParameters.sessionID(), requestParameters.requestID());
    }

    /**
     * Get permission request
     * Retrieve a pending permission request owned by a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | PermissionNotFoundError
     * @param requestParameters The sessionPermissionGet request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionPermissionGetWithResponseSpec(SessionPermissionGetRequest requestParameters) throws WebClientResponseException {
        return this.sessionPermissionGetWithResponseSpec(requestParameters.sessionID(), requestParameters.requestID());
    }


    /**
     * Get permission request
     * Retrieve a pending permission request owned by a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | PermissionNotFoundError
     * @param sessionID The sessionID parameter
     * @param requestID The requestID parameter
     * @return SessionPermissionGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionPermissionGetRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String requestID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionPermissionGet", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'requestID' is set
        if (requestID == null) {
            throw new WebClientResponseException("Missing the required parameter 'requestID' when calling sessionPermissionGet", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);
        pathParams.put("requestID", requestID);

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

        ParameterizedTypeReference<SessionPermissionGet200Response> localVarReturnType = new ParameterizedTypeReference<SessionPermissionGet200Response>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/permission/{requestID}", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get permission request
     * Retrieve a pending permission request owned by a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | PermissionNotFoundError
     * @param sessionID The sessionID parameter
     * @param requestID The requestID parameter
     * @return SessionPermissionGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionPermissionGet200Response> sessionPermissionGet(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String requestID) throws WebClientResponseException {
        ParameterizedTypeReference<SessionPermissionGet200Response> localVarReturnType = new ParameterizedTypeReference<SessionPermissionGet200Response>() {};
        return sessionPermissionGetRequestCreation(sessionID, requestID).bodyToMono(localVarReturnType);
    }

    /**
     * Get permission request
     * Retrieve a pending permission request owned by a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | PermissionNotFoundError
     * @param sessionID The sessionID parameter
     * @param requestID The requestID parameter
     * @return ResponseEntity&lt;SessionPermissionGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionPermissionGet200Response>> sessionPermissionGetWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String requestID) throws WebClientResponseException {
        ParameterizedTypeReference<SessionPermissionGet200Response> localVarReturnType = new ParameterizedTypeReference<SessionPermissionGet200Response>() {};
        return sessionPermissionGetRequestCreation(sessionID, requestID).toEntity(localVarReturnType);
    }

    /**
     * Get permission request
     * Retrieve a pending permission request owned by a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | PermissionNotFoundError
     * @param sessionID The sessionID parameter
     * @param requestID The requestID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionPermissionGetWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String requestID) throws WebClientResponseException {
        return sessionPermissionGetRequestCreation(sessionID, requestID);
    }

    /**
     * List session permission requests
     * Retrieve pending permission requests owned by a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return SessionPermissionList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionPermissionListRequestCreation(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionPermissionList", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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

        ParameterizedTypeReference<SessionPermissionList200Response> localVarReturnType = new ParameterizedTypeReference<SessionPermissionList200Response>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/permission", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List session permission requests
     * Retrieve pending permission requests owned by a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return SessionPermissionList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionPermissionList200Response> sessionPermissionList(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<SessionPermissionList200Response> localVarReturnType = new ParameterizedTypeReference<SessionPermissionList200Response>() {};
        return sessionPermissionListRequestCreation(sessionID).bodyToMono(localVarReturnType);
    }

    /**
     * List session permission requests
     * Retrieve pending permission requests owned by a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return ResponseEntity&lt;SessionPermissionList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionPermissionList200Response>> sessionPermissionListWithHttpInfo(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<SessionPermissionList200Response> localVarReturnType = new ParameterizedTypeReference<SessionPermissionList200Response>() {};
        return sessionPermissionListRequestCreation(sessionID).toEntity(localVarReturnType);
    }

    /**
     * List session permission requests
     * Retrieve pending permission requests owned by a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionPermissionListWithResponseSpec(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        return sessionPermissionListRequestCreation(sessionID);
    }

    public class SessionPermissionReplyRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull String requestID;
        private @jakarta.annotation.Nonnull SessionPermissionReplyRequest sessionPermissionReplyRequest;

        public SessionPermissionReplyRequest() {}

        public SessionPermissionReplyRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nonnull SessionPermissionReplyRequest sessionPermissionReplyRequest) {
            this.sessionID = sessionID;
            this.requestID = requestID;
            this.sessionPermissionReplyRequest = sessionPermissionReplyRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionPermissionReplyRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull String requestID() {
            return this.requestID;
        }
        public SessionPermissionReplyRequest requestID(@jakarta.annotation.Nonnull String requestID) {
            this.requestID = requestID;
            return this;
        }

        public @jakarta.annotation.Nonnull SessionPermissionReplyRequest sessionPermissionReplyRequest() {
            return this.sessionPermissionReplyRequest;
        }
        public SessionPermissionReplyRequest sessionPermissionReplyRequest(@jakarta.annotation.Nonnull SessionPermissionReplyRequest sessionPermissionReplyRequest) {
            this.sessionPermissionReplyRequest = sessionPermissionReplyRequest;
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
            SessionPermissionReplyRequest request = (SessionPermissionReplyRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.requestID, request.requestID()) &&
                Objects.equals(this.sessionPermissionReplyRequest, request.sessionPermissionReplyRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, requestID, sessionPermissionReplyRequest);
        }
    }

    /**
     * Reply to pending permission request
     * Respond to a pending permission request owned by a session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | PermissionNotFoundError
     * @param requestParameters The sessionPermissionReply request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionPermissionReply(SessionPermissionReplyRequest requestParameters) throws WebClientResponseException {
        return this.sessionPermissionReply(requestParameters.sessionID(), requestParameters.requestID(), requestParameters.sessionPermissionReplyRequest());
    }

    /**
     * Reply to pending permission request
     * Respond to a pending permission request owned by a session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | PermissionNotFoundError
     * @param requestParameters The sessionPermissionReply request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionPermissionReplyWithHttpInfo(SessionPermissionReplyRequest requestParameters) throws WebClientResponseException {
        return this.sessionPermissionReplyWithHttpInfo(requestParameters.sessionID(), requestParameters.requestID(), requestParameters.sessionPermissionReplyRequest());
    }

    /**
     * Reply to pending permission request
     * Respond to a pending permission request owned by a session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | PermissionNotFoundError
     * @param requestParameters The sessionPermissionReply request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionPermissionReplyWithResponseSpec(SessionPermissionReplyRequest requestParameters) throws WebClientResponseException {
        return this.sessionPermissionReplyWithResponseSpec(requestParameters.sessionID(), requestParameters.requestID(), requestParameters.sessionPermissionReplyRequest());
    }


    /**
     * Reply to pending permission request
     * Respond to a pending permission request owned by a session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | PermissionNotFoundError
     * @param sessionID The sessionID parameter
     * @param requestID The requestID parameter
     * @param sessionPermissionReplyRequest The sessionPermissionReplyRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionPermissionReplyRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nonnull SessionPermissionReplyRequest sessionPermissionReplyRequest) throws WebClientResponseException {
        Object postBody = sessionPermissionReplyRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionPermissionReply", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'requestID' is set
        if (requestID == null) {
            throw new WebClientResponseException("Missing the required parameter 'requestID' when calling sessionPermissionReply", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'sessionPermissionReplyRequest' is set
        if (sessionPermissionReplyRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionPermissionReplyRequest' when calling sessionPermissionReply", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);
        pathParams.put("requestID", requestID);

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
        return apiClient.invokeAPI("/api/session/{sessionID}/permission/{requestID}/reply", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Reply to pending permission request
     * Respond to a pending permission request owned by a session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | PermissionNotFoundError
     * @param sessionID The sessionID parameter
     * @param requestID The requestID parameter
     * @param sessionPermissionReplyRequest The sessionPermissionReplyRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionPermissionReply(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nonnull SessionPermissionReplyRequest sessionPermissionReplyRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionPermissionReplyRequestCreation(sessionID, requestID, sessionPermissionReplyRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Reply to pending permission request
     * Respond to a pending permission request owned by a session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | PermissionNotFoundError
     * @param sessionID The sessionID parameter
     * @param requestID The requestID parameter
     * @param sessionPermissionReplyRequest The sessionPermissionReplyRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionPermissionReplyWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nonnull SessionPermissionReplyRequest sessionPermissionReplyRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionPermissionReplyRequestCreation(sessionID, requestID, sessionPermissionReplyRequest).toEntity(localVarReturnType);
    }

    /**
     * Reply to pending permission request
     * Respond to a pending permission request owned by a session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | PermissionNotFoundError
     * @param sessionID The sessionID parameter
     * @param requestID The requestID parameter
     * @param sessionPermissionReplyRequest The sessionPermissionReplyRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionPermissionReplyWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nonnull SessionPermissionReplyRequest sessionPermissionReplyRequest) throws WebClientResponseException {
        return sessionPermissionReplyRequestCreation(sessionID, requestID, sessionPermissionReplyRequest);
    }
}
