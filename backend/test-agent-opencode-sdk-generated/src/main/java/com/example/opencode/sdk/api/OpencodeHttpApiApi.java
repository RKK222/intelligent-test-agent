package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.InvalidRequestError;
import com.example.opencode.sdk.model.LocationInfo;
import com.example.opencode.sdk.model.UnauthorizedError;
import com.example.opencode.sdk.model.V2AgentList200Response;
import com.example.opencode.sdk.model.V2AgentListLocationParameter;
import com.example.opencode.sdk.model.V2CredentialUpdateRequest;
import com.example.opencode.sdk.model.V2HealthGet200Response;
import com.example.opencode.sdk.model.V2LocationGetLocationParameter;

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
public class OpencodeHttpApiApi {
    private ApiClient apiClient;

    public OpencodeHttpApiApi() {
        this(new ApiClient());
    }

    public OpencodeHttpApiApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * List agents
     * Retrieve currently registered agents.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return V2AgentList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2AgentListRequestCreation(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
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

        ParameterizedTypeReference<V2AgentList200Response> localVarReturnType = new ParameterizedTypeReference<V2AgentList200Response>() {};
        return apiClient.invokeAPI("/api/agent", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List agents
     * Retrieve currently registered agents.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return V2AgentList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2AgentList200Response> v2AgentList(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2AgentList200Response> localVarReturnType = new ParameterizedTypeReference<V2AgentList200Response>() {};
        return v2AgentListRequestCreation(location).bodyToMono(localVarReturnType);
    }

    /**
     * List agents
     * Retrieve currently registered agents.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseEntity&lt;V2AgentList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2AgentList200Response>> v2AgentListWithHttpInfo(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2AgentList200Response> localVarReturnType = new ParameterizedTypeReference<V2AgentList200Response>() {};
        return v2AgentListRequestCreation(location).toEntity(localVarReturnType);
    }

    /**
     * List agents
     * Retrieve currently registered agents.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2AgentListWithResponseSpec(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        return v2AgentListRequestCreation(location);
    }

    public class V2CredentialRemoveRequest {
        private @jakarta.annotation.Nonnull String credentialID;
        private @jakarta.annotation.Nullable V2AgentListLocationParameter location;

        public V2CredentialRemoveRequest() {}

        public V2CredentialRemoveRequest(@jakarta.annotation.Nonnull String credentialID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) {
            this.credentialID = credentialID;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String credentialID() {
            return this.credentialID;
        }
        public V2CredentialRemoveRequest credentialID(@jakarta.annotation.Nonnull String credentialID) {
            this.credentialID = credentialID;
            return this;
        }

        public @jakarta.annotation.Nullable V2AgentListLocationParameter location() {
            return this.location;
        }
        public V2CredentialRemoveRequest location(@jakarta.annotation.Nullable V2AgentListLocationParameter location) {
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
            V2CredentialRemoveRequest request = (V2CredentialRemoveRequest) o;
            return Objects.equals(this.credentialID, request.credentialID()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(credentialID, location);
        }
    }

    /**
     * Remove credential
     * Remove a stored integration credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2CredentialRemove request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2CredentialRemove(V2CredentialRemoveRequest requestParameters) throws WebClientResponseException {
        return this.v2CredentialRemove(requestParameters.credentialID(), requestParameters.location());
    }

    /**
     * Remove credential
     * Remove a stored integration credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2CredentialRemove request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2CredentialRemoveWithHttpInfo(V2CredentialRemoveRequest requestParameters) throws WebClientResponseException {
        return this.v2CredentialRemoveWithHttpInfo(requestParameters.credentialID(), requestParameters.location());
    }

    /**
     * Remove credential
     * Remove a stored integration credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2CredentialRemove request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2CredentialRemoveWithResponseSpec(V2CredentialRemoveRequest requestParameters) throws WebClientResponseException {
        return this.v2CredentialRemoveWithResponseSpec(requestParameters.credentialID(), requestParameters.location());
    }


    /**
     * Remove credential
     * Remove a stored integration credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param credentialID The credentialID parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2CredentialRemoveRequestCreation(@jakarta.annotation.Nonnull String credentialID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'credentialID' is set
        if (credentialID == null) {
            throw new WebClientResponseException("Missing the required parameter 'credentialID' when calling v2CredentialRemove", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("credentialID", credentialID);

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
        return apiClient.invokeAPI("/api/credential/{credentialID}", HttpMethod.DELETE, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Remove credential
     * Remove a stored integration credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param credentialID The credentialID parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2CredentialRemove(@jakarta.annotation.Nonnull String credentialID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2CredentialRemoveRequestCreation(credentialID, location).bodyToMono(localVarReturnType);
    }

    /**
     * Remove credential
     * Remove a stored integration credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param credentialID The credentialID parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2CredentialRemoveWithHttpInfo(@jakarta.annotation.Nonnull String credentialID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2CredentialRemoveRequestCreation(credentialID, location).toEntity(localVarReturnType);
    }

    /**
     * Remove credential
     * Remove a stored integration credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param credentialID The credentialID parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2CredentialRemoveWithResponseSpec(@jakarta.annotation.Nonnull String credentialID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        return v2CredentialRemoveRequestCreation(credentialID, location);
    }

    public class V2CredentialUpdateRequest {
        private @jakarta.annotation.Nonnull String credentialID;
        private @jakarta.annotation.Nonnull V2CredentialUpdateRequest v2CredentialUpdateRequest;
        private @jakarta.annotation.Nullable V2AgentListLocationParameter location;

        public V2CredentialUpdateRequest() {}

        public V2CredentialUpdateRequest(@jakarta.annotation.Nonnull String credentialID, @jakarta.annotation.Nonnull V2CredentialUpdateRequest v2CredentialUpdateRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) {
            this.credentialID = credentialID;
            this.v2CredentialUpdateRequest = v2CredentialUpdateRequest;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String credentialID() {
            return this.credentialID;
        }
        public V2CredentialUpdateRequest credentialID(@jakarta.annotation.Nonnull String credentialID) {
            this.credentialID = credentialID;
            return this;
        }

        public @jakarta.annotation.Nonnull V2CredentialUpdateRequest v2CredentialUpdateRequest() {
            return this.v2CredentialUpdateRequest;
        }
        public V2CredentialUpdateRequest v2CredentialUpdateRequest(@jakarta.annotation.Nonnull V2CredentialUpdateRequest v2CredentialUpdateRequest) {
            this.v2CredentialUpdateRequest = v2CredentialUpdateRequest;
            return this;
        }

        public @jakarta.annotation.Nullable V2AgentListLocationParameter location() {
            return this.location;
        }
        public V2CredentialUpdateRequest location(@jakarta.annotation.Nullable V2AgentListLocationParameter location) {
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
            V2CredentialUpdateRequest request = (V2CredentialUpdateRequest) o;
            return Objects.equals(this.credentialID, request.credentialID()) &&
                Objects.equals(this.v2CredentialUpdateRequest, request.v2CredentialUpdateRequest()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(credentialID, v2CredentialUpdateRequest, location);
        }
    }

    /**
     * Update credential
     * Update a stored credential label.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2CredentialUpdate request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2CredentialUpdate(V2CredentialUpdateRequest requestParameters) throws WebClientResponseException {
        return this.v2CredentialUpdate(requestParameters.credentialID(), requestParameters.v2CredentialUpdateRequest(), requestParameters.location());
    }

    /**
     * Update credential
     * Update a stored credential label.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2CredentialUpdate request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2CredentialUpdateWithHttpInfo(V2CredentialUpdateRequest requestParameters) throws WebClientResponseException {
        return this.v2CredentialUpdateWithHttpInfo(requestParameters.credentialID(), requestParameters.v2CredentialUpdateRequest(), requestParameters.location());
    }

    /**
     * Update credential
     * Update a stored credential label.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2CredentialUpdate request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2CredentialUpdateWithResponseSpec(V2CredentialUpdateRequest requestParameters) throws WebClientResponseException {
        return this.v2CredentialUpdateWithResponseSpec(requestParameters.credentialID(), requestParameters.v2CredentialUpdateRequest(), requestParameters.location());
    }


    /**
     * Update credential
     * Update a stored credential label.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param credentialID The credentialID parameter
     * @param v2CredentialUpdateRequest The v2CredentialUpdateRequest parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2CredentialUpdateRequestCreation(@jakarta.annotation.Nonnull String credentialID, @jakarta.annotation.Nonnull V2CredentialUpdateRequest v2CredentialUpdateRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = v2CredentialUpdateRequest;
        // verify the required parameter 'credentialID' is set
        if (credentialID == null) {
            throw new WebClientResponseException("Missing the required parameter 'credentialID' when calling v2CredentialUpdate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'v2CredentialUpdateRequest' is set
        if (v2CredentialUpdateRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'v2CredentialUpdateRequest' when calling v2CredentialUpdate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("credentialID", credentialID);

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

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/credential/{credentialID}", HttpMethod.PATCH, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Update credential
     * Update a stored credential label.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param credentialID The credentialID parameter
     * @param v2CredentialUpdateRequest The v2CredentialUpdateRequest parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2CredentialUpdate(@jakarta.annotation.Nonnull String credentialID, @jakarta.annotation.Nonnull V2CredentialUpdateRequest v2CredentialUpdateRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2CredentialUpdateRequestCreation(credentialID, v2CredentialUpdateRequest, location).bodyToMono(localVarReturnType);
    }

    /**
     * Update credential
     * Update a stored credential label.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param credentialID The credentialID parameter
     * @param v2CredentialUpdateRequest The v2CredentialUpdateRequest parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2CredentialUpdateWithHttpInfo(@jakarta.annotation.Nonnull String credentialID, @jakarta.annotation.Nonnull V2CredentialUpdateRequest v2CredentialUpdateRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2CredentialUpdateRequestCreation(credentialID, v2CredentialUpdateRequest, location).toEntity(localVarReturnType);
    }

    /**
     * Update credential
     * Update a stored credential label.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param credentialID The credentialID parameter
     * @param v2CredentialUpdateRequest The v2CredentialUpdateRequest parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2CredentialUpdateWithResponseSpec(@jakarta.annotation.Nonnull String credentialID, @jakarta.annotation.Nonnull V2CredentialUpdateRequest v2CredentialUpdateRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        return v2CredentialUpdateRequestCreation(credentialID, v2CredentialUpdateRequest, location);
    }

    /**
     * Check server health
     * Check whether the API server is ready to accept requests.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return V2HealthGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2HealthGetRequestCreation() throws WebClientResponseException {
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

        ParameterizedTypeReference<V2HealthGet200Response> localVarReturnType = new ParameterizedTypeReference<V2HealthGet200Response>() {};
        return apiClient.invokeAPI("/api/health", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Check server health
     * Check whether the API server is ready to accept requests.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return V2HealthGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2HealthGet200Response> v2HealthGet() throws WebClientResponseException {
        ParameterizedTypeReference<V2HealthGet200Response> localVarReturnType = new ParameterizedTypeReference<V2HealthGet200Response>() {};
        return v2HealthGetRequestCreation().bodyToMono(localVarReturnType);
    }

    /**
     * Check server health
     * Check whether the API server is ready to accept requests.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return ResponseEntity&lt;V2HealthGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2HealthGet200Response>> v2HealthGetWithHttpInfo() throws WebClientResponseException {
        ParameterizedTypeReference<V2HealthGet200Response> localVarReturnType = new ParameterizedTypeReference<V2HealthGet200Response>() {};
        return v2HealthGetRequestCreation().toEntity(localVarReturnType);
    }

    /**
     * Check server health
     * Check whether the API server is ready to accept requests.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2HealthGetWithResponseSpec() throws WebClientResponseException {
        return v2HealthGetRequestCreation();
    }

    /**
     * Get location
     * Resolve the requested location or the server default location.
     * <p><b>200</b> - Location.Info
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return LocationInfo
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2LocationGetRequestCreation(@jakarta.annotation.Nullable V2LocationGetLocationParameter location) throws WebClientResponseException {
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

        ParameterizedTypeReference<LocationInfo> localVarReturnType = new ParameterizedTypeReference<LocationInfo>() {};
        return apiClient.invokeAPI("/api/location", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get location
     * Resolve the requested location or the server default location.
     * <p><b>200</b> - Location.Info
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return LocationInfo
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<LocationInfo> v2LocationGet(@jakarta.annotation.Nullable V2LocationGetLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<LocationInfo> localVarReturnType = new ParameterizedTypeReference<LocationInfo>() {};
        return v2LocationGetRequestCreation(location).bodyToMono(localVarReturnType);
    }

    /**
     * Get location
     * Resolve the requested location or the server default location.
     * <p><b>200</b> - Location.Info
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseEntity&lt;LocationInfo&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<LocationInfo>> v2LocationGetWithHttpInfo(@jakarta.annotation.Nullable V2LocationGetLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<LocationInfo> localVarReturnType = new ParameterizedTypeReference<LocationInfo>() {};
        return v2LocationGetRequestCreation(location).toEntity(localVarReturnType);
    }

    /**
     * Get location
     * Resolve the requested location or the server default location.
     * <p><b>200</b> - Location.Info
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2LocationGetWithResponseSpec(@jakarta.annotation.Nullable V2LocationGetLocationParameter location) throws WebClientResponseException {
        return v2LocationGetRequestCreation(location);
    }
}
