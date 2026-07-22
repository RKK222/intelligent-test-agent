package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.InvalidRequestError;
import com.example.opencode.sdk.model.UnauthorizedError;
import com.example.opencode.sdk.model.V2AgentListLocationParameter;
import com.example.opencode.sdk.model.V2IntegrationAttemptCompleteRequest;
import com.example.opencode.sdk.model.V2IntegrationAttemptStatus200Response;
import com.example.opencode.sdk.model.V2IntegrationConnectKey400Response;
import com.example.opencode.sdk.model.V2IntegrationConnectKeyRequest;
import com.example.opencode.sdk.model.V2IntegrationConnectOauth200Response;
import com.example.opencode.sdk.model.V2IntegrationConnectOauthRequest;
import com.example.opencode.sdk.model.V2IntegrationGet200Response;
import com.example.opencode.sdk.model.V2IntegrationList200Response;

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
public class IntegrationsApi {
    private ApiClient apiClient;

    public IntegrationsApi() {
        this(new ApiClient());
    }

    public IntegrationsApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public class V2IntegrationAttemptCancelRequest {
        private @jakarta.annotation.Nonnull String attemptID;
        private @jakarta.annotation.Nullable V2AgentListLocationParameter location;

        public V2IntegrationAttemptCancelRequest() {}

        public V2IntegrationAttemptCancelRequest(@jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) {
            this.attemptID = attemptID;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String attemptID() {
            return this.attemptID;
        }
        public V2IntegrationAttemptCancelRequest attemptID(@jakarta.annotation.Nonnull String attemptID) {
            this.attemptID = attemptID;
            return this;
        }

        public @jakarta.annotation.Nullable V2AgentListLocationParameter location() {
            return this.location;
        }
        public V2IntegrationAttemptCancelRequest location(@jakarta.annotation.Nullable V2AgentListLocationParameter location) {
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
            V2IntegrationAttemptCancelRequest request = (V2IntegrationAttemptCancelRequest) o;
            return Objects.equals(this.attemptID, request.attemptID()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(attemptID, location);
        }
    }

    /**
     * Cancel OAuth connection
     * Cancel an OAuth attempt and release its resources.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2IntegrationAttemptCancel request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2IntegrationAttemptCancel(V2IntegrationAttemptCancelRequest requestParameters) throws WebClientResponseException {
        return this.v2IntegrationAttemptCancel(requestParameters.attemptID(), requestParameters.location());
    }

    /**
     * Cancel OAuth connection
     * Cancel an OAuth attempt and release its resources.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2IntegrationAttemptCancel request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2IntegrationAttemptCancelWithHttpInfo(V2IntegrationAttemptCancelRequest requestParameters) throws WebClientResponseException {
        return this.v2IntegrationAttemptCancelWithHttpInfo(requestParameters.attemptID(), requestParameters.location());
    }

    /**
     * Cancel OAuth connection
     * Cancel an OAuth attempt and release its resources.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2IntegrationAttemptCancel request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2IntegrationAttemptCancelWithResponseSpec(V2IntegrationAttemptCancelRequest requestParameters) throws WebClientResponseException {
        return this.v2IntegrationAttemptCancelWithResponseSpec(requestParameters.attemptID(), requestParameters.location());
    }


    /**
     * Cancel OAuth connection
     * Cancel an OAuth attempt and release its resources.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param attemptID The attemptID parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2IntegrationAttemptCancelRequestCreation(@jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'attemptID' is set
        if (attemptID == null) {
            throw new WebClientResponseException("Missing the required parameter 'attemptID' when calling v2IntegrationAttemptCancel", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("attemptID", attemptID);

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
        return apiClient.invokeAPI("/api/integration/attempt/{attemptID}", HttpMethod.DELETE, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Cancel OAuth connection
     * Cancel an OAuth attempt and release its resources.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param attemptID The attemptID parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2IntegrationAttemptCancel(@jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2IntegrationAttemptCancelRequestCreation(attemptID, location).bodyToMono(localVarReturnType);
    }

    /**
     * Cancel OAuth connection
     * Cancel an OAuth attempt and release its resources.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param attemptID The attemptID parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2IntegrationAttemptCancelWithHttpInfo(@jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2IntegrationAttemptCancelRequestCreation(attemptID, location).toEntity(localVarReturnType);
    }

    /**
     * Cancel OAuth connection
     * Cancel an OAuth attempt and release its resources.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param attemptID The attemptID parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2IntegrationAttemptCancelWithResponseSpec(@jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        return v2IntegrationAttemptCancelRequestCreation(attemptID, location);
    }

    public class V2IntegrationAttemptCompleteRequest {
        private @jakarta.annotation.Nonnull String attemptID;
        private @jakarta.annotation.Nonnull V2IntegrationAttemptCompleteRequest v2IntegrationAttemptCompleteRequest;
        private @jakarta.annotation.Nullable V2AgentListLocationParameter location;

        public V2IntegrationAttemptCompleteRequest() {}

        public V2IntegrationAttemptCompleteRequest(@jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nonnull V2IntegrationAttemptCompleteRequest v2IntegrationAttemptCompleteRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) {
            this.attemptID = attemptID;
            this.v2IntegrationAttemptCompleteRequest = v2IntegrationAttemptCompleteRequest;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String attemptID() {
            return this.attemptID;
        }
        public V2IntegrationAttemptCompleteRequest attemptID(@jakarta.annotation.Nonnull String attemptID) {
            this.attemptID = attemptID;
            return this;
        }

        public @jakarta.annotation.Nonnull V2IntegrationAttemptCompleteRequest v2IntegrationAttemptCompleteRequest() {
            return this.v2IntegrationAttemptCompleteRequest;
        }
        public V2IntegrationAttemptCompleteRequest v2IntegrationAttemptCompleteRequest(@jakarta.annotation.Nonnull V2IntegrationAttemptCompleteRequest v2IntegrationAttemptCompleteRequest) {
            this.v2IntegrationAttemptCompleteRequest = v2IntegrationAttemptCompleteRequest;
            return this;
        }

        public @jakarta.annotation.Nullable V2AgentListLocationParameter location() {
            return this.location;
        }
        public V2IntegrationAttemptCompleteRequest location(@jakarta.annotation.Nullable V2AgentListLocationParameter location) {
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
            V2IntegrationAttemptCompleteRequest request = (V2IntegrationAttemptCompleteRequest) o;
            return Objects.equals(this.attemptID, request.attemptID()) &&
                Objects.equals(this.v2IntegrationAttemptCompleteRequest, request.v2IntegrationAttemptCompleteRequest()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(attemptID, v2IntegrationAttemptCompleteRequest, location);
        }
    }

    /**
     * Complete OAuth connection
     * Complete a code-based OAuth attempt and store the resulting credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2IntegrationAttemptComplete request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2IntegrationAttemptComplete(V2IntegrationAttemptCompleteRequest requestParameters) throws WebClientResponseException {
        return this.v2IntegrationAttemptComplete(requestParameters.attemptID(), requestParameters.v2IntegrationAttemptCompleteRequest(), requestParameters.location());
    }

    /**
     * Complete OAuth connection
     * Complete a code-based OAuth attempt and store the resulting credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2IntegrationAttemptComplete request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2IntegrationAttemptCompleteWithHttpInfo(V2IntegrationAttemptCompleteRequest requestParameters) throws WebClientResponseException {
        return this.v2IntegrationAttemptCompleteWithHttpInfo(requestParameters.attemptID(), requestParameters.v2IntegrationAttemptCompleteRequest(), requestParameters.location());
    }

    /**
     * Complete OAuth connection
     * Complete a code-based OAuth attempt and store the resulting credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2IntegrationAttemptComplete request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2IntegrationAttemptCompleteWithResponseSpec(V2IntegrationAttemptCompleteRequest requestParameters) throws WebClientResponseException {
        return this.v2IntegrationAttemptCompleteWithResponseSpec(requestParameters.attemptID(), requestParameters.v2IntegrationAttemptCompleteRequest(), requestParameters.location());
    }


    /**
     * Complete OAuth connection
     * Complete a code-based OAuth attempt and store the resulting credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param attemptID The attemptID parameter
     * @param v2IntegrationAttemptCompleteRequest The v2IntegrationAttemptCompleteRequest parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2IntegrationAttemptCompleteRequestCreation(@jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nonnull V2IntegrationAttemptCompleteRequest v2IntegrationAttemptCompleteRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = v2IntegrationAttemptCompleteRequest;
        // verify the required parameter 'attemptID' is set
        if (attemptID == null) {
            throw new WebClientResponseException("Missing the required parameter 'attemptID' when calling v2IntegrationAttemptComplete", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'v2IntegrationAttemptCompleteRequest' is set
        if (v2IntegrationAttemptCompleteRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'v2IntegrationAttemptCompleteRequest' when calling v2IntegrationAttemptComplete", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("attemptID", attemptID);

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
        return apiClient.invokeAPI("/api/integration/attempt/{attemptID}/complete", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Complete OAuth connection
     * Complete a code-based OAuth attempt and store the resulting credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param attemptID The attemptID parameter
     * @param v2IntegrationAttemptCompleteRequest The v2IntegrationAttemptCompleteRequest parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2IntegrationAttemptComplete(@jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nonnull V2IntegrationAttemptCompleteRequest v2IntegrationAttemptCompleteRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2IntegrationAttemptCompleteRequestCreation(attemptID, v2IntegrationAttemptCompleteRequest, location).bodyToMono(localVarReturnType);
    }

    /**
     * Complete OAuth connection
     * Complete a code-based OAuth attempt and store the resulting credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param attemptID The attemptID parameter
     * @param v2IntegrationAttemptCompleteRequest The v2IntegrationAttemptCompleteRequest parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2IntegrationAttemptCompleteWithHttpInfo(@jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nonnull V2IntegrationAttemptCompleteRequest v2IntegrationAttemptCompleteRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2IntegrationAttemptCompleteRequestCreation(attemptID, v2IntegrationAttemptCompleteRequest, location).toEntity(localVarReturnType);
    }

    /**
     * Complete OAuth connection
     * Complete a code-based OAuth attempt and store the resulting credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param attemptID The attemptID parameter
     * @param v2IntegrationAttemptCompleteRequest The v2IntegrationAttemptCompleteRequest parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2IntegrationAttemptCompleteWithResponseSpec(@jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nonnull V2IntegrationAttemptCompleteRequest v2IntegrationAttemptCompleteRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        return v2IntegrationAttemptCompleteRequestCreation(attemptID, v2IntegrationAttemptCompleteRequest, location);
    }

    public class V2IntegrationAttemptStatusRequest {
        private @jakarta.annotation.Nonnull String attemptID;
        private @jakarta.annotation.Nullable V2AgentListLocationParameter location;

        public V2IntegrationAttemptStatusRequest() {}

        public V2IntegrationAttemptStatusRequest(@jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) {
            this.attemptID = attemptID;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String attemptID() {
            return this.attemptID;
        }
        public V2IntegrationAttemptStatusRequest attemptID(@jakarta.annotation.Nonnull String attemptID) {
            this.attemptID = attemptID;
            return this;
        }

        public @jakarta.annotation.Nullable V2AgentListLocationParameter location() {
            return this.location;
        }
        public V2IntegrationAttemptStatusRequest location(@jakarta.annotation.Nullable V2AgentListLocationParameter location) {
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
            V2IntegrationAttemptStatusRequest request = (V2IntegrationAttemptStatusRequest) o;
            return Objects.equals(this.attemptID, request.attemptID()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(attemptID, location);
        }
    }

    /**
     * Get OAuth attempt status
     * Poll the current status of an OAuth attempt.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2IntegrationAttemptStatus request parameters as object
     * @return V2IntegrationAttemptStatus200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2IntegrationAttemptStatus200Response> v2IntegrationAttemptStatus(V2IntegrationAttemptStatusRequest requestParameters) throws WebClientResponseException {
        return this.v2IntegrationAttemptStatus(requestParameters.attemptID(), requestParameters.location());
    }

    /**
     * Get OAuth attempt status
     * Poll the current status of an OAuth attempt.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2IntegrationAttemptStatus request parameters as object
     * @return ResponseEntity&lt;V2IntegrationAttemptStatus200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2IntegrationAttemptStatus200Response>> v2IntegrationAttemptStatusWithHttpInfo(V2IntegrationAttemptStatusRequest requestParameters) throws WebClientResponseException {
        return this.v2IntegrationAttemptStatusWithHttpInfo(requestParameters.attemptID(), requestParameters.location());
    }

    /**
     * Get OAuth attempt status
     * Poll the current status of an OAuth attempt.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2IntegrationAttemptStatus request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2IntegrationAttemptStatusWithResponseSpec(V2IntegrationAttemptStatusRequest requestParameters) throws WebClientResponseException {
        return this.v2IntegrationAttemptStatusWithResponseSpec(requestParameters.attemptID(), requestParameters.location());
    }


    /**
     * Get OAuth attempt status
     * Poll the current status of an OAuth attempt.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param attemptID The attemptID parameter
     * @param location The location parameter
     * @return V2IntegrationAttemptStatus200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2IntegrationAttemptStatusRequestCreation(@jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'attemptID' is set
        if (attemptID == null) {
            throw new WebClientResponseException("Missing the required parameter 'attemptID' when calling v2IntegrationAttemptStatus", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("attemptID", attemptID);

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

        ParameterizedTypeReference<V2IntegrationAttemptStatus200Response> localVarReturnType = new ParameterizedTypeReference<V2IntegrationAttemptStatus200Response>() {};
        return apiClient.invokeAPI("/api/integration/attempt/{attemptID}", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get OAuth attempt status
     * Poll the current status of an OAuth attempt.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param attemptID The attemptID parameter
     * @param location The location parameter
     * @return V2IntegrationAttemptStatus200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2IntegrationAttemptStatus200Response> v2IntegrationAttemptStatus(@jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2IntegrationAttemptStatus200Response> localVarReturnType = new ParameterizedTypeReference<V2IntegrationAttemptStatus200Response>() {};
        return v2IntegrationAttemptStatusRequestCreation(attemptID, location).bodyToMono(localVarReturnType);
    }

    /**
     * Get OAuth attempt status
     * Poll the current status of an OAuth attempt.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param attemptID The attemptID parameter
     * @param location The location parameter
     * @return ResponseEntity&lt;V2IntegrationAttemptStatus200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2IntegrationAttemptStatus200Response>> v2IntegrationAttemptStatusWithHttpInfo(@jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2IntegrationAttemptStatus200Response> localVarReturnType = new ParameterizedTypeReference<V2IntegrationAttemptStatus200Response>() {};
        return v2IntegrationAttemptStatusRequestCreation(attemptID, location).toEntity(localVarReturnType);
    }

    /**
     * Get OAuth attempt status
     * Poll the current status of an OAuth attempt.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param attemptID The attemptID parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2IntegrationAttemptStatusWithResponseSpec(@jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        return v2IntegrationAttemptStatusRequestCreation(attemptID, location);
    }

    public class V2IntegrationConnectKeyRequest {
        private @jakarta.annotation.Nonnull String integrationID;
        private @jakarta.annotation.Nonnull V2IntegrationConnectKeyRequest v2IntegrationConnectKeyRequest;
        private @jakarta.annotation.Nullable V2AgentListLocationParameter location;

        public V2IntegrationConnectKeyRequest() {}

        public V2IntegrationConnectKeyRequest(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull V2IntegrationConnectKeyRequest v2IntegrationConnectKeyRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) {
            this.integrationID = integrationID;
            this.v2IntegrationConnectKeyRequest = v2IntegrationConnectKeyRequest;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String integrationID() {
            return this.integrationID;
        }
        public V2IntegrationConnectKeyRequest integrationID(@jakarta.annotation.Nonnull String integrationID) {
            this.integrationID = integrationID;
            return this;
        }

        public @jakarta.annotation.Nonnull V2IntegrationConnectKeyRequest v2IntegrationConnectKeyRequest() {
            return this.v2IntegrationConnectKeyRequest;
        }
        public V2IntegrationConnectKeyRequest v2IntegrationConnectKeyRequest(@jakarta.annotation.Nonnull V2IntegrationConnectKeyRequest v2IntegrationConnectKeyRequest) {
            this.v2IntegrationConnectKeyRequest = v2IntegrationConnectKeyRequest;
            return this;
        }

        public @jakarta.annotation.Nullable V2AgentListLocationParameter location() {
            return this.location;
        }
        public V2IntegrationConnectKeyRequest location(@jakarta.annotation.Nullable V2AgentListLocationParameter location) {
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
            V2IntegrationConnectKeyRequest request = (V2IntegrationConnectKeyRequest) o;
            return Objects.equals(this.integrationID, request.integrationID()) &&
                Objects.equals(this.v2IntegrationConnectKeyRequest, request.v2IntegrationConnectKeyRequest()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(integrationID, v2IntegrationConnectKeyRequest, location);
        }
    }

    /**
     * Connect with key
     * Run a key authentication method and store the resulting credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2IntegrationConnectKey request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2IntegrationConnectKey(V2IntegrationConnectKeyRequest requestParameters) throws WebClientResponseException {
        return this.v2IntegrationConnectKey(requestParameters.integrationID(), requestParameters.v2IntegrationConnectKeyRequest(), requestParameters.location());
    }

    /**
     * Connect with key
     * Run a key authentication method and store the resulting credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2IntegrationConnectKey request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2IntegrationConnectKeyWithHttpInfo(V2IntegrationConnectKeyRequest requestParameters) throws WebClientResponseException {
        return this.v2IntegrationConnectKeyWithHttpInfo(requestParameters.integrationID(), requestParameters.v2IntegrationConnectKeyRequest(), requestParameters.location());
    }

    /**
     * Connect with key
     * Run a key authentication method and store the resulting credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2IntegrationConnectKey request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2IntegrationConnectKeyWithResponseSpec(V2IntegrationConnectKeyRequest requestParameters) throws WebClientResponseException {
        return this.v2IntegrationConnectKeyWithResponseSpec(requestParameters.integrationID(), requestParameters.v2IntegrationConnectKeyRequest(), requestParameters.location());
    }


    /**
     * Connect with key
     * Run a key authentication method and store the resulting credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param integrationID The integrationID parameter
     * @param v2IntegrationConnectKeyRequest The v2IntegrationConnectKeyRequest parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2IntegrationConnectKeyRequestCreation(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull V2IntegrationConnectKeyRequest v2IntegrationConnectKeyRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = v2IntegrationConnectKeyRequest;
        // verify the required parameter 'integrationID' is set
        if (integrationID == null) {
            throw new WebClientResponseException("Missing the required parameter 'integrationID' when calling v2IntegrationConnectKey", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'v2IntegrationConnectKeyRequest' is set
        if (v2IntegrationConnectKeyRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'v2IntegrationConnectKeyRequest' when calling v2IntegrationConnectKey", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("integrationID", integrationID);

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
        return apiClient.invokeAPI("/api/integration/{integrationID}/connect/key", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Connect with key
     * Run a key authentication method and store the resulting credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param integrationID The integrationID parameter
     * @param v2IntegrationConnectKeyRequest The v2IntegrationConnectKeyRequest parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2IntegrationConnectKey(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull V2IntegrationConnectKeyRequest v2IntegrationConnectKeyRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2IntegrationConnectKeyRequestCreation(integrationID, v2IntegrationConnectKeyRequest, location).bodyToMono(localVarReturnType);
    }

    /**
     * Connect with key
     * Run a key authentication method and store the resulting credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param integrationID The integrationID parameter
     * @param v2IntegrationConnectKeyRequest The v2IntegrationConnectKeyRequest parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2IntegrationConnectKeyWithHttpInfo(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull V2IntegrationConnectKeyRequest v2IntegrationConnectKeyRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2IntegrationConnectKeyRequestCreation(integrationID, v2IntegrationConnectKeyRequest, location).toEntity(localVarReturnType);
    }

    /**
     * Connect with key
     * Run a key authentication method and store the resulting credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param integrationID The integrationID parameter
     * @param v2IntegrationConnectKeyRequest The v2IntegrationConnectKeyRequest parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2IntegrationConnectKeyWithResponseSpec(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull V2IntegrationConnectKeyRequest v2IntegrationConnectKeyRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        return v2IntegrationConnectKeyRequestCreation(integrationID, v2IntegrationConnectKeyRequest, location);
    }

    public class V2IntegrationConnectOauthRequest {
        private @jakarta.annotation.Nonnull String integrationID;
        private @jakarta.annotation.Nonnull V2IntegrationConnectOauthRequest v2IntegrationConnectOauthRequest;
        private @jakarta.annotation.Nullable V2AgentListLocationParameter location;

        public V2IntegrationConnectOauthRequest() {}

        public V2IntegrationConnectOauthRequest(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull V2IntegrationConnectOauthRequest v2IntegrationConnectOauthRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) {
            this.integrationID = integrationID;
            this.v2IntegrationConnectOauthRequest = v2IntegrationConnectOauthRequest;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String integrationID() {
            return this.integrationID;
        }
        public V2IntegrationConnectOauthRequest integrationID(@jakarta.annotation.Nonnull String integrationID) {
            this.integrationID = integrationID;
            return this;
        }

        public @jakarta.annotation.Nonnull V2IntegrationConnectOauthRequest v2IntegrationConnectOauthRequest() {
            return this.v2IntegrationConnectOauthRequest;
        }
        public V2IntegrationConnectOauthRequest v2IntegrationConnectOauthRequest(@jakarta.annotation.Nonnull V2IntegrationConnectOauthRequest v2IntegrationConnectOauthRequest) {
            this.v2IntegrationConnectOauthRequest = v2IntegrationConnectOauthRequest;
            return this;
        }

        public @jakarta.annotation.Nullable V2AgentListLocationParameter location() {
            return this.location;
        }
        public V2IntegrationConnectOauthRequest location(@jakarta.annotation.Nullable V2AgentListLocationParameter location) {
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
            V2IntegrationConnectOauthRequest request = (V2IntegrationConnectOauthRequest) o;
            return Objects.equals(this.integrationID, request.integrationID()) &&
                Objects.equals(this.v2IntegrationConnectOauthRequest, request.v2IntegrationConnectOauthRequest()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(integrationID, v2IntegrationConnectOauthRequest, location);
        }
    }

    /**
     * Begin OAuth connection
     * Start an OAuth attempt and return the authorization details.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2IntegrationConnectOauth request parameters as object
     * @return V2IntegrationConnectOauth200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2IntegrationConnectOauth200Response> v2IntegrationConnectOauth(V2IntegrationConnectOauthRequest requestParameters) throws WebClientResponseException {
        return this.v2IntegrationConnectOauth(requestParameters.integrationID(), requestParameters.v2IntegrationConnectOauthRequest(), requestParameters.location());
    }

    /**
     * Begin OAuth connection
     * Start an OAuth attempt and return the authorization details.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2IntegrationConnectOauth request parameters as object
     * @return ResponseEntity&lt;V2IntegrationConnectOauth200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2IntegrationConnectOauth200Response>> v2IntegrationConnectOauthWithHttpInfo(V2IntegrationConnectOauthRequest requestParameters) throws WebClientResponseException {
        return this.v2IntegrationConnectOauthWithHttpInfo(requestParameters.integrationID(), requestParameters.v2IntegrationConnectOauthRequest(), requestParameters.location());
    }

    /**
     * Begin OAuth connection
     * Start an OAuth attempt and return the authorization details.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2IntegrationConnectOauth request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2IntegrationConnectOauthWithResponseSpec(V2IntegrationConnectOauthRequest requestParameters) throws WebClientResponseException {
        return this.v2IntegrationConnectOauthWithResponseSpec(requestParameters.integrationID(), requestParameters.v2IntegrationConnectOauthRequest(), requestParameters.location());
    }


    /**
     * Begin OAuth connection
     * Start an OAuth attempt and return the authorization details.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param integrationID The integrationID parameter
     * @param v2IntegrationConnectOauthRequest The v2IntegrationConnectOauthRequest parameter
     * @param location The location parameter
     * @return V2IntegrationConnectOauth200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2IntegrationConnectOauthRequestCreation(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull V2IntegrationConnectOauthRequest v2IntegrationConnectOauthRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = v2IntegrationConnectOauthRequest;
        // verify the required parameter 'integrationID' is set
        if (integrationID == null) {
            throw new WebClientResponseException("Missing the required parameter 'integrationID' when calling v2IntegrationConnectOauth", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'v2IntegrationConnectOauthRequest' is set
        if (v2IntegrationConnectOauthRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'v2IntegrationConnectOauthRequest' when calling v2IntegrationConnectOauth", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("integrationID", integrationID);

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

        ParameterizedTypeReference<V2IntegrationConnectOauth200Response> localVarReturnType = new ParameterizedTypeReference<V2IntegrationConnectOauth200Response>() {};
        return apiClient.invokeAPI("/api/integration/{integrationID}/connect/oauth", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Begin OAuth connection
     * Start an OAuth attempt and return the authorization details.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param integrationID The integrationID parameter
     * @param v2IntegrationConnectOauthRequest The v2IntegrationConnectOauthRequest parameter
     * @param location The location parameter
     * @return V2IntegrationConnectOauth200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2IntegrationConnectOauth200Response> v2IntegrationConnectOauth(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull V2IntegrationConnectOauthRequest v2IntegrationConnectOauthRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2IntegrationConnectOauth200Response> localVarReturnType = new ParameterizedTypeReference<V2IntegrationConnectOauth200Response>() {};
        return v2IntegrationConnectOauthRequestCreation(integrationID, v2IntegrationConnectOauthRequest, location).bodyToMono(localVarReturnType);
    }

    /**
     * Begin OAuth connection
     * Start an OAuth attempt and return the authorization details.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param integrationID The integrationID parameter
     * @param v2IntegrationConnectOauthRequest The v2IntegrationConnectOauthRequest parameter
     * @param location The location parameter
     * @return ResponseEntity&lt;V2IntegrationConnectOauth200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2IntegrationConnectOauth200Response>> v2IntegrationConnectOauthWithHttpInfo(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull V2IntegrationConnectOauthRequest v2IntegrationConnectOauthRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2IntegrationConnectOauth200Response> localVarReturnType = new ParameterizedTypeReference<V2IntegrationConnectOauth200Response>() {};
        return v2IntegrationConnectOauthRequestCreation(integrationID, v2IntegrationConnectOauthRequest, location).toEntity(localVarReturnType);
    }

    /**
     * Begin OAuth connection
     * Start an OAuth attempt and return the authorization details.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param integrationID The integrationID parameter
     * @param v2IntegrationConnectOauthRequest The v2IntegrationConnectOauthRequest parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2IntegrationConnectOauthWithResponseSpec(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull V2IntegrationConnectOauthRequest v2IntegrationConnectOauthRequest, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        return v2IntegrationConnectOauthRequestCreation(integrationID, v2IntegrationConnectOauthRequest, location);
    }

    public class V2IntegrationGetRequest {
        private @jakarta.annotation.Nonnull String integrationID;
        private @jakarta.annotation.Nullable V2AgentListLocationParameter location;

        public V2IntegrationGetRequest() {}

        public V2IntegrationGetRequest(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) {
            this.integrationID = integrationID;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String integrationID() {
            return this.integrationID;
        }
        public V2IntegrationGetRequest integrationID(@jakarta.annotation.Nonnull String integrationID) {
            this.integrationID = integrationID;
            return this;
        }

        public @jakarta.annotation.Nullable V2AgentListLocationParameter location() {
            return this.location;
        }
        public V2IntegrationGetRequest location(@jakarta.annotation.Nullable V2AgentListLocationParameter location) {
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
            V2IntegrationGetRequest request = (V2IntegrationGetRequest) o;
            return Objects.equals(this.integrationID, request.integrationID()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(integrationID, location);
        }
    }

    /**
     * Get integration
     * Retrieve one integration and its authentication methods.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2IntegrationGet request parameters as object
     * @return V2IntegrationGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2IntegrationGet200Response> v2IntegrationGet(V2IntegrationGetRequest requestParameters) throws WebClientResponseException {
        return this.v2IntegrationGet(requestParameters.integrationID(), requestParameters.location());
    }

    /**
     * Get integration
     * Retrieve one integration and its authentication methods.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2IntegrationGet request parameters as object
     * @return ResponseEntity&lt;V2IntegrationGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2IntegrationGet200Response>> v2IntegrationGetWithHttpInfo(V2IntegrationGetRequest requestParameters) throws WebClientResponseException {
        return this.v2IntegrationGetWithHttpInfo(requestParameters.integrationID(), requestParameters.location());
    }

    /**
     * Get integration
     * Retrieve one integration and its authentication methods.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2IntegrationGet request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2IntegrationGetWithResponseSpec(V2IntegrationGetRequest requestParameters) throws WebClientResponseException {
        return this.v2IntegrationGetWithResponseSpec(requestParameters.integrationID(), requestParameters.location());
    }


    /**
     * Get integration
     * Retrieve one integration and its authentication methods.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param integrationID The integrationID parameter
     * @param location The location parameter
     * @return V2IntegrationGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2IntegrationGetRequestCreation(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'integrationID' is set
        if (integrationID == null) {
            throw new WebClientResponseException("Missing the required parameter 'integrationID' when calling v2IntegrationGet", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("integrationID", integrationID);

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

        ParameterizedTypeReference<V2IntegrationGet200Response> localVarReturnType = new ParameterizedTypeReference<V2IntegrationGet200Response>() {};
        return apiClient.invokeAPI("/api/integration/{integrationID}", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get integration
     * Retrieve one integration and its authentication methods.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param integrationID The integrationID parameter
     * @param location The location parameter
     * @return V2IntegrationGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2IntegrationGet200Response> v2IntegrationGet(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2IntegrationGet200Response> localVarReturnType = new ParameterizedTypeReference<V2IntegrationGet200Response>() {};
        return v2IntegrationGetRequestCreation(integrationID, location).bodyToMono(localVarReturnType);
    }

    /**
     * Get integration
     * Retrieve one integration and its authentication methods.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param integrationID The integrationID parameter
     * @param location The location parameter
     * @return ResponseEntity&lt;V2IntegrationGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2IntegrationGet200Response>> v2IntegrationGetWithHttpInfo(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2IntegrationGet200Response> localVarReturnType = new ParameterizedTypeReference<V2IntegrationGet200Response>() {};
        return v2IntegrationGetRequestCreation(integrationID, location).toEntity(localVarReturnType);
    }

    /**
     * Get integration
     * Retrieve one integration and its authentication methods.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param integrationID The integrationID parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2IntegrationGetWithResponseSpec(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        return v2IntegrationGetRequestCreation(integrationID, location);
    }

    /**
     * List integrations
     * Retrieve available integrations and their authentication methods.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return V2IntegrationList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2IntegrationListRequestCreation(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
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

        ParameterizedTypeReference<V2IntegrationList200Response> localVarReturnType = new ParameterizedTypeReference<V2IntegrationList200Response>() {};
        return apiClient.invokeAPI("/api/integration", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List integrations
     * Retrieve available integrations and their authentication methods.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return V2IntegrationList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2IntegrationList200Response> v2IntegrationList(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2IntegrationList200Response> localVarReturnType = new ParameterizedTypeReference<V2IntegrationList200Response>() {};
        return v2IntegrationListRequestCreation(location).bodyToMono(localVarReturnType);
    }

    /**
     * List integrations
     * Retrieve available integrations and their authentication methods.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseEntity&lt;V2IntegrationList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2IntegrationList200Response>> v2IntegrationListWithHttpInfo(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2IntegrationList200Response> localVarReturnType = new ParameterizedTypeReference<V2IntegrationList200Response>() {};
        return v2IntegrationListRequestCreation(location).toEntity(localVarReturnType);
    }

    /**
     * List integrations
     * Retrieve available integrations and their authentication methods.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2IntegrationListWithResponseSpec(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        return v2IntegrationListRequestCreation(location);
    }
}
