package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.InvalidRequestError;
import com.example.opencode.sdk.model.UnauthorizedError;
import com.example.opencode.sdk.model.V2AgentListLocationParameter;
import com.example.opencode.sdk.model.V2PermissionRequestList200Response;
import com.example.opencode.sdk.model.V2PermissionSavedList200Response;
import com.example.opencode.sdk.model.V2SessionGet404Response;
import com.example.opencode.sdk.model.V2SessionPermissionCreate200Response;
import com.example.opencode.sdk.model.V2SessionPermissionCreateRequest;
import com.example.opencode.sdk.model.V2SessionPermissionGet200Response;
import com.example.opencode.sdk.model.V2SessionPermissionGet404Response;
import com.example.opencode.sdk.model.V2SessionPermissionList200Response;
import com.example.opencode.sdk.model.V2SessionPermissionReplyRequest;

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
public class PermissionsApi {
    private ApiClient apiClient;

    public PermissionsApi() {
        this(new ApiClient());
    }

    public PermissionsApi(ApiClient apiClient) {
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
     * @return V2PermissionRequestList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2PermissionRequestListRequestCreation(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
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

        ParameterizedTypeReference<V2PermissionRequestList200Response> localVarReturnType = new ParameterizedTypeReference<V2PermissionRequestList200Response>() {};
        return apiClient.invokeAPI("/api/permission/request", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List pending permission requests
     * Retrieve pending permission requests for a location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return V2PermissionRequestList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2PermissionRequestList200Response> v2PermissionRequestList(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2PermissionRequestList200Response> localVarReturnType = new ParameterizedTypeReference<V2PermissionRequestList200Response>() {};
        return v2PermissionRequestListRequestCreation(location).bodyToMono(localVarReturnType);
    }

    /**
     * List pending permission requests
     * Retrieve pending permission requests for a location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseEntity&lt;V2PermissionRequestList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2PermissionRequestList200Response>> v2PermissionRequestListWithHttpInfo(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2PermissionRequestList200Response> localVarReturnType = new ParameterizedTypeReference<V2PermissionRequestList200Response>() {};
        return v2PermissionRequestListRequestCreation(location).toEntity(localVarReturnType);
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
    public ResponseSpec v2PermissionRequestListWithResponseSpec(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        return v2PermissionRequestListRequestCreation(location);
    }

    /**
     * List saved permissions
     * Retrieve saved permissions, optionally filtered by project.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param projectID The projectID parameter
     * @return V2PermissionSavedList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2PermissionSavedListRequestCreation(@jakarta.annotation.Nullable String projectID) throws WebClientResponseException {
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

        ParameterizedTypeReference<V2PermissionSavedList200Response> localVarReturnType = new ParameterizedTypeReference<V2PermissionSavedList200Response>() {};
        return apiClient.invokeAPI("/api/permission/saved", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List saved permissions
     * Retrieve saved permissions, optionally filtered by project.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param projectID The projectID parameter
     * @return V2PermissionSavedList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2PermissionSavedList200Response> v2PermissionSavedList(@jakarta.annotation.Nullable String projectID) throws WebClientResponseException {
        ParameterizedTypeReference<V2PermissionSavedList200Response> localVarReturnType = new ParameterizedTypeReference<V2PermissionSavedList200Response>() {};
        return v2PermissionSavedListRequestCreation(projectID).bodyToMono(localVarReturnType);
    }

    /**
     * List saved permissions
     * Retrieve saved permissions, optionally filtered by project.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param projectID The projectID parameter
     * @return ResponseEntity&lt;V2PermissionSavedList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2PermissionSavedList200Response>> v2PermissionSavedListWithHttpInfo(@jakarta.annotation.Nullable String projectID) throws WebClientResponseException {
        ParameterizedTypeReference<V2PermissionSavedList200Response> localVarReturnType = new ParameterizedTypeReference<V2PermissionSavedList200Response>() {};
        return v2PermissionSavedListRequestCreation(projectID).toEntity(localVarReturnType);
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
    public ResponseSpec v2PermissionSavedListWithResponseSpec(@jakarta.annotation.Nullable String projectID) throws WebClientResponseException {
        return v2PermissionSavedListRequestCreation(projectID);
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
    private ResponseSpec v2PermissionSavedRemoveRequestCreation(@jakarta.annotation.Nonnull String id) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'id' is set
        if (id == null) {
            throw new WebClientResponseException("Missing the required parameter 'id' when calling v2PermissionSavedRemove", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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
    public Mono<Void> v2PermissionSavedRemove(@jakarta.annotation.Nonnull String id) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2PermissionSavedRemoveRequestCreation(id).bodyToMono(localVarReturnType);
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
    public Mono<ResponseEntity<Void>> v2PermissionSavedRemoveWithHttpInfo(@jakarta.annotation.Nonnull String id) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2PermissionSavedRemoveRequestCreation(id).toEntity(localVarReturnType);
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
    public ResponseSpec v2PermissionSavedRemoveWithResponseSpec(@jakarta.annotation.Nonnull String id) throws WebClientResponseException {
        return v2PermissionSavedRemoveRequestCreation(id);
    }

    public class V2SessionPermissionCreateRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull V2SessionPermissionCreateRequest v2SessionPermissionCreateRequest;

        public V2SessionPermissionCreateRequest() {}

        public V2SessionPermissionCreateRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull V2SessionPermissionCreateRequest v2SessionPermissionCreateRequest) {
            this.sessionID = sessionID;
            this.v2SessionPermissionCreateRequest = v2SessionPermissionCreateRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public V2SessionPermissionCreateRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull V2SessionPermissionCreateRequest v2SessionPermissionCreateRequest() {
            return this.v2SessionPermissionCreateRequest;
        }
        public V2SessionPermissionCreateRequest v2SessionPermissionCreateRequest(@jakarta.annotation.Nonnull V2SessionPermissionCreateRequest v2SessionPermissionCreateRequest) {
            this.v2SessionPermissionCreateRequest = v2SessionPermissionCreateRequest;
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
            V2SessionPermissionCreateRequest request = (V2SessionPermissionCreateRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.v2SessionPermissionCreateRequest, request.v2SessionPermissionCreateRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, v2SessionPermissionCreateRequest);
        }
    }

    /**
     * Create permission request
     * Evaluate and, when approval is required, create a permission request for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The v2SessionPermissionCreate request parameters as object
     * @return V2SessionPermissionCreate200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2SessionPermissionCreate200Response> v2SessionPermissionCreate(V2SessionPermissionCreateRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionPermissionCreate(requestParameters.sessionID(), requestParameters.v2SessionPermissionCreateRequest());
    }

    /**
     * Create permission request
     * Evaluate and, when approval is required, create a permission request for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The v2SessionPermissionCreate request parameters as object
     * @return ResponseEntity&lt;V2SessionPermissionCreate200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2SessionPermissionCreate200Response>> v2SessionPermissionCreateWithHttpInfo(V2SessionPermissionCreateRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionPermissionCreateWithHttpInfo(requestParameters.sessionID(), requestParameters.v2SessionPermissionCreateRequest());
    }

    /**
     * Create permission request
     * Evaluate and, when approval is required, create a permission request for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The v2SessionPermissionCreate request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionPermissionCreateWithResponseSpec(V2SessionPermissionCreateRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionPermissionCreateWithResponseSpec(requestParameters.sessionID(), requestParameters.v2SessionPermissionCreateRequest());
    }


    /**
     * Create permission request
     * Evaluate and, when approval is required, create a permission request for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param v2SessionPermissionCreateRequest The v2SessionPermissionCreateRequest parameter
     * @return V2SessionPermissionCreate200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2SessionPermissionCreateRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull V2SessionPermissionCreateRequest v2SessionPermissionCreateRequest) throws WebClientResponseException {
        Object postBody = v2SessionPermissionCreateRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling v2SessionPermissionCreate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'v2SessionPermissionCreateRequest' is set
        if (v2SessionPermissionCreateRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'v2SessionPermissionCreateRequest' when calling v2SessionPermissionCreate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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

        ParameterizedTypeReference<V2SessionPermissionCreate200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionPermissionCreate200Response>() {};
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
     * @param v2SessionPermissionCreateRequest The v2SessionPermissionCreateRequest parameter
     * @return V2SessionPermissionCreate200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2SessionPermissionCreate200Response> v2SessionPermissionCreate(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull V2SessionPermissionCreateRequest v2SessionPermissionCreateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<V2SessionPermissionCreate200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionPermissionCreate200Response>() {};
        return v2SessionPermissionCreateRequestCreation(sessionID, v2SessionPermissionCreateRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Create permission request
     * Evaluate and, when approval is required, create a permission request for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param v2SessionPermissionCreateRequest The v2SessionPermissionCreateRequest parameter
     * @return ResponseEntity&lt;V2SessionPermissionCreate200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2SessionPermissionCreate200Response>> v2SessionPermissionCreateWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull V2SessionPermissionCreateRequest v2SessionPermissionCreateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<V2SessionPermissionCreate200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionPermissionCreate200Response>() {};
        return v2SessionPermissionCreateRequestCreation(sessionID, v2SessionPermissionCreateRequest).toEntity(localVarReturnType);
    }

    /**
     * Create permission request
     * Evaluate and, when approval is required, create a permission request for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param v2SessionPermissionCreateRequest The v2SessionPermissionCreateRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionPermissionCreateWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull V2SessionPermissionCreateRequest v2SessionPermissionCreateRequest) throws WebClientResponseException {
        return v2SessionPermissionCreateRequestCreation(sessionID, v2SessionPermissionCreateRequest);
    }

    public class V2SessionPermissionGetRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull String requestID;

        public V2SessionPermissionGetRequest() {}

        public V2SessionPermissionGetRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String requestID) {
            this.sessionID = sessionID;
            this.requestID = requestID;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public V2SessionPermissionGetRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull String requestID() {
            return this.requestID;
        }
        public V2SessionPermissionGetRequest requestID(@jakarta.annotation.Nonnull String requestID) {
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
            V2SessionPermissionGetRequest request = (V2SessionPermissionGetRequest) o;
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
     * @param requestParameters The v2SessionPermissionGet request parameters as object
     * @return V2SessionPermissionGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2SessionPermissionGet200Response> v2SessionPermissionGet(V2SessionPermissionGetRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionPermissionGet(requestParameters.sessionID(), requestParameters.requestID());
    }

    /**
     * Get permission request
     * Retrieve a pending permission request owned by a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | PermissionNotFoundError
     * @param requestParameters The v2SessionPermissionGet request parameters as object
     * @return ResponseEntity&lt;V2SessionPermissionGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2SessionPermissionGet200Response>> v2SessionPermissionGetWithHttpInfo(V2SessionPermissionGetRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionPermissionGetWithHttpInfo(requestParameters.sessionID(), requestParameters.requestID());
    }

    /**
     * Get permission request
     * Retrieve a pending permission request owned by a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | PermissionNotFoundError
     * @param requestParameters The v2SessionPermissionGet request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionPermissionGetWithResponseSpec(V2SessionPermissionGetRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionPermissionGetWithResponseSpec(requestParameters.sessionID(), requestParameters.requestID());
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
     * @return V2SessionPermissionGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2SessionPermissionGetRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String requestID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling v2SessionPermissionGet", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'requestID' is set
        if (requestID == null) {
            throw new WebClientResponseException("Missing the required parameter 'requestID' when calling v2SessionPermissionGet", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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

        ParameterizedTypeReference<V2SessionPermissionGet200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionPermissionGet200Response>() {};
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
     * @return V2SessionPermissionGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2SessionPermissionGet200Response> v2SessionPermissionGet(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String requestID) throws WebClientResponseException {
        ParameterizedTypeReference<V2SessionPermissionGet200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionPermissionGet200Response>() {};
        return v2SessionPermissionGetRequestCreation(sessionID, requestID).bodyToMono(localVarReturnType);
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
     * @return ResponseEntity&lt;V2SessionPermissionGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2SessionPermissionGet200Response>> v2SessionPermissionGetWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String requestID) throws WebClientResponseException {
        ParameterizedTypeReference<V2SessionPermissionGet200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionPermissionGet200Response>() {};
        return v2SessionPermissionGetRequestCreation(sessionID, requestID).toEntity(localVarReturnType);
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
    public ResponseSpec v2SessionPermissionGetWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String requestID) throws WebClientResponseException {
        return v2SessionPermissionGetRequestCreation(sessionID, requestID);
    }

    /**
     * List session permission requests
     * Retrieve pending permission requests owned by a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return V2SessionPermissionList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2SessionPermissionListRequestCreation(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling v2SessionPermissionList", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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

        ParameterizedTypeReference<V2SessionPermissionList200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionPermissionList200Response>() {};
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
     * @return V2SessionPermissionList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2SessionPermissionList200Response> v2SessionPermissionList(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<V2SessionPermissionList200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionPermissionList200Response>() {};
        return v2SessionPermissionListRequestCreation(sessionID).bodyToMono(localVarReturnType);
    }

    /**
     * List session permission requests
     * Retrieve pending permission requests owned by a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return ResponseEntity&lt;V2SessionPermissionList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2SessionPermissionList200Response>> v2SessionPermissionListWithHttpInfo(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<V2SessionPermissionList200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionPermissionList200Response>() {};
        return v2SessionPermissionListRequestCreation(sessionID).toEntity(localVarReturnType);
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
    public ResponseSpec v2SessionPermissionListWithResponseSpec(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        return v2SessionPermissionListRequestCreation(sessionID);
    }

    public class V2SessionPermissionReplyRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull String requestID;
        private @jakarta.annotation.Nonnull V2SessionPermissionReplyRequest v2SessionPermissionReplyRequest;

        public V2SessionPermissionReplyRequest() {}

        public V2SessionPermissionReplyRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nonnull V2SessionPermissionReplyRequest v2SessionPermissionReplyRequest) {
            this.sessionID = sessionID;
            this.requestID = requestID;
            this.v2SessionPermissionReplyRequest = v2SessionPermissionReplyRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public V2SessionPermissionReplyRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull String requestID() {
            return this.requestID;
        }
        public V2SessionPermissionReplyRequest requestID(@jakarta.annotation.Nonnull String requestID) {
            this.requestID = requestID;
            return this;
        }

        public @jakarta.annotation.Nonnull V2SessionPermissionReplyRequest v2SessionPermissionReplyRequest() {
            return this.v2SessionPermissionReplyRequest;
        }
        public V2SessionPermissionReplyRequest v2SessionPermissionReplyRequest(@jakarta.annotation.Nonnull V2SessionPermissionReplyRequest v2SessionPermissionReplyRequest) {
            this.v2SessionPermissionReplyRequest = v2SessionPermissionReplyRequest;
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
            V2SessionPermissionReplyRequest request = (V2SessionPermissionReplyRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.requestID, request.requestID()) &&
                Objects.equals(this.v2SessionPermissionReplyRequest, request.v2SessionPermissionReplyRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, requestID, v2SessionPermissionReplyRequest);
        }
    }

    /**
     * Reply to pending permission request
     * Respond to a pending permission request owned by a session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | PermissionNotFoundError
     * @param requestParameters The v2SessionPermissionReply request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2SessionPermissionReply(V2SessionPermissionReplyRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionPermissionReply(requestParameters.sessionID(), requestParameters.requestID(), requestParameters.v2SessionPermissionReplyRequest());
    }

    /**
     * Reply to pending permission request
     * Respond to a pending permission request owned by a session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | PermissionNotFoundError
     * @param requestParameters The v2SessionPermissionReply request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2SessionPermissionReplyWithHttpInfo(V2SessionPermissionReplyRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionPermissionReplyWithHttpInfo(requestParameters.sessionID(), requestParameters.requestID(), requestParameters.v2SessionPermissionReplyRequest());
    }

    /**
     * Reply to pending permission request
     * Respond to a pending permission request owned by a session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | PermissionNotFoundError
     * @param requestParameters The v2SessionPermissionReply request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionPermissionReplyWithResponseSpec(V2SessionPermissionReplyRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionPermissionReplyWithResponseSpec(requestParameters.sessionID(), requestParameters.requestID(), requestParameters.v2SessionPermissionReplyRequest());
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
     * @param v2SessionPermissionReplyRequest The v2SessionPermissionReplyRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2SessionPermissionReplyRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nonnull V2SessionPermissionReplyRequest v2SessionPermissionReplyRequest) throws WebClientResponseException {
        Object postBody = v2SessionPermissionReplyRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling v2SessionPermissionReply", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'requestID' is set
        if (requestID == null) {
            throw new WebClientResponseException("Missing the required parameter 'requestID' when calling v2SessionPermissionReply", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'v2SessionPermissionReplyRequest' is set
        if (v2SessionPermissionReplyRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'v2SessionPermissionReplyRequest' when calling v2SessionPermissionReply", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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
     * @param v2SessionPermissionReplyRequest The v2SessionPermissionReplyRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2SessionPermissionReply(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nonnull V2SessionPermissionReplyRequest v2SessionPermissionReplyRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2SessionPermissionReplyRequestCreation(sessionID, requestID, v2SessionPermissionReplyRequest).bodyToMono(localVarReturnType);
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
     * @param v2SessionPermissionReplyRequest The v2SessionPermissionReplyRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2SessionPermissionReplyWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nonnull V2SessionPermissionReplyRequest v2SessionPermissionReplyRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2SessionPermissionReplyRequestCreation(sessionID, requestID, v2SessionPermissionReplyRequest).toEntity(localVarReturnType);
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
     * @param v2SessionPermissionReplyRequest The v2SessionPermissionReplyRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionPermissionReplyWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nonnull V2SessionPermissionReplyRequest v2SessionPermissionReplyRequest) throws WebClientResponseException {
        return v2SessionPermissionReplyRequestCreation(sessionID, requestID, v2SessionPermissionReplyRequest);
    }
}
