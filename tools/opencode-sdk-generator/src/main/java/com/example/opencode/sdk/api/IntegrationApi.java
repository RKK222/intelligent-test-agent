package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.AgentListLocationParameter;
import com.example.opencode.sdk.model.ExperimentalIntegrationWellknownAddRequest;
import com.example.opencode.sdk.model.IntegrationCommandConnect200Response;
import com.example.opencode.sdk.model.IntegrationCommandConnect404Response;
import com.example.opencode.sdk.model.IntegrationCommandConnectRequest;
import com.example.opencode.sdk.model.IntegrationCommandStatus200Response;
import com.example.opencode.sdk.model.IntegrationConnectKeyRequest;
import com.example.opencode.sdk.model.IntegrationGet200Response;
import com.example.opencode.sdk.model.IntegrationList200Response;
import com.example.opencode.sdk.model.IntegrationNotFoundErrorEncoded;
import com.example.opencode.sdk.model.IntegrationOauthCompleteRequest;
import com.example.opencode.sdk.model.IntegrationOauthConnect200Response;
import com.example.opencode.sdk.model.IntegrationOauthConnectRequest;
import com.example.opencode.sdk.model.IntegrationOauthStatus200Response;
import com.example.opencode.sdk.model.IntegrationOauthStatus404Response;
import com.example.opencode.sdk.model.InvalidRequestErrorEncoded;
import com.example.opencode.sdk.model.PluginCheck400Response;
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
public class IntegrationApi {
    private ApiClient apiClient;

    public IntegrationApi() {
        this(new ApiClient());
    }

    public IntegrationApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public class ExperimentalIntegrationWellknownAddRequest {
        private @jakarta.annotation.Nonnull ExperimentalIntegrationWellknownAddRequest experimentalIntegrationWellknownAddRequest;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;

        public ExperimentalIntegrationWellknownAddRequest() {}

        public ExperimentalIntegrationWellknownAddRequest(@jakarta.annotation.Nonnull ExperimentalIntegrationWellknownAddRequest experimentalIntegrationWellknownAddRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.experimentalIntegrationWellknownAddRequest = experimentalIntegrationWellknownAddRequest;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull ExperimentalIntegrationWellknownAddRequest experimentalIntegrationWellknownAddRequest() {
            return this.experimentalIntegrationWellknownAddRequest;
        }
        public ExperimentalIntegrationWellknownAddRequest experimentalIntegrationWellknownAddRequest(@jakarta.annotation.Nonnull ExperimentalIntegrationWellknownAddRequest experimentalIntegrationWellknownAddRequest) {
            this.experimentalIntegrationWellknownAddRequest = experimentalIntegrationWellknownAddRequest;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public ExperimentalIntegrationWellknownAddRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
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
            ExperimentalIntegrationWellknownAddRequest request = (ExperimentalIntegrationWellknownAddRequest) o;
            return Objects.equals(this.experimentalIntegrationWellknownAddRequest, request.experimentalIntegrationWellknownAddRequest()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(experimentalIntegrationWellknownAddRequest, location);
        }
    }

    /**
     * Add wellknown integration
     * Discover and persist an experimental wellknown integration source.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The experimentalIntegrationWellknownAdd request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> experimentalIntegrationWellknownAdd(ExperimentalIntegrationWellknownAddRequest requestParameters) throws WebClientResponseException {
        return this.experimentalIntegrationWellknownAdd(requestParameters.experimentalIntegrationWellknownAddRequest(), requestParameters.location());
    }

    /**
     * Add wellknown integration
     * Discover and persist an experimental wellknown integration source.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The experimentalIntegrationWellknownAdd request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> experimentalIntegrationWellknownAddWithHttpInfo(ExperimentalIntegrationWellknownAddRequest requestParameters) throws WebClientResponseException {
        return this.experimentalIntegrationWellknownAddWithHttpInfo(requestParameters.experimentalIntegrationWellknownAddRequest(), requestParameters.location());
    }

    /**
     * Add wellknown integration
     * Discover and persist an experimental wellknown integration source.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The experimentalIntegrationWellknownAdd request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalIntegrationWellknownAddWithResponseSpec(ExperimentalIntegrationWellknownAddRequest requestParameters) throws WebClientResponseException {
        return this.experimentalIntegrationWellknownAddWithResponseSpec(requestParameters.experimentalIntegrationWellknownAddRequest(), requestParameters.location());
    }


    /**
     * Add wellknown integration
     * Discover and persist an experimental wellknown integration source.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param experimentalIntegrationWellknownAddRequest The experimentalIntegrationWellknownAddRequest parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalIntegrationWellknownAddRequestCreation(@jakarta.annotation.Nonnull ExperimentalIntegrationWellknownAddRequest experimentalIntegrationWellknownAddRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = experimentalIntegrationWellknownAddRequest;
        // verify the required parameter 'experimentalIntegrationWellknownAddRequest' is set
        if (experimentalIntegrationWellknownAddRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'experimentalIntegrationWellknownAddRequest' when calling experimentalIntegrationWellknownAdd", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/experimental/integration/wellknown", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Add wellknown integration
     * Discover and persist an experimental wellknown integration source.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param experimentalIntegrationWellknownAddRequest The experimentalIntegrationWellknownAddRequest parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> experimentalIntegrationWellknownAdd(@jakarta.annotation.Nonnull ExperimentalIntegrationWellknownAddRequest experimentalIntegrationWellknownAddRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return experimentalIntegrationWellknownAddRequestCreation(experimentalIntegrationWellknownAddRequest, location).bodyToMono(localVarReturnType);
    }

    /**
     * Add wellknown integration
     * Discover and persist an experimental wellknown integration source.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param experimentalIntegrationWellknownAddRequest The experimentalIntegrationWellknownAddRequest parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> experimentalIntegrationWellknownAddWithHttpInfo(@jakarta.annotation.Nonnull ExperimentalIntegrationWellknownAddRequest experimentalIntegrationWellknownAddRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return experimentalIntegrationWellknownAddRequestCreation(experimentalIntegrationWellknownAddRequest, location).toEntity(localVarReturnType);
    }

    /**
     * Add wellknown integration
     * Discover and persist an experimental wellknown integration source.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param experimentalIntegrationWellknownAddRequest The experimentalIntegrationWellknownAddRequest parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalIntegrationWellknownAddWithResponseSpec(@jakarta.annotation.Nonnull ExperimentalIntegrationWellknownAddRequest experimentalIntegrationWellknownAddRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return experimentalIntegrationWellknownAddRequestCreation(experimentalIntegrationWellknownAddRequest, location);
    }

    public class IntegrationCommandCancelRequest {
        private @jakarta.annotation.Nonnull String integrationID;
        private @jakarta.annotation.Nonnull String attemptID;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;

        public IntegrationCommandCancelRequest() {}

        public IntegrationCommandCancelRequest(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.integrationID = integrationID;
            this.attemptID = attemptID;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String integrationID() {
            return this.integrationID;
        }
        public IntegrationCommandCancelRequest integrationID(@jakarta.annotation.Nonnull String integrationID) {
            this.integrationID = integrationID;
            return this;
        }

        public @jakarta.annotation.Nonnull String attemptID() {
            return this.attemptID;
        }
        public IntegrationCommandCancelRequest attemptID(@jakarta.annotation.Nonnull String attemptID) {
            this.attemptID = attemptID;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public IntegrationCommandCancelRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
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
            IntegrationCommandCancelRequest request = (IntegrationCommandCancelRequest) o;
            return Objects.equals(this.integrationID, request.integrationID()) &&
                Objects.equals(this.attemptID, request.attemptID()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(integrationID, attemptID, location);
        }
    }

    /**
     * Cancel command connection
     * Cancel a command authentication attempt and terminate its process.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The integrationCommandCancel request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> integrationCommandCancel(IntegrationCommandCancelRequest requestParameters) throws WebClientResponseException {
        return this.integrationCommandCancel(requestParameters.integrationID(), requestParameters.attemptID(), requestParameters.location());
    }

    /**
     * Cancel command connection
     * Cancel a command authentication attempt and terminate its process.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The integrationCommandCancel request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> integrationCommandCancelWithHttpInfo(IntegrationCommandCancelRequest requestParameters) throws WebClientResponseException {
        return this.integrationCommandCancelWithHttpInfo(requestParameters.integrationID(), requestParameters.attemptID(), requestParameters.location());
    }

    /**
     * Cancel command connection
     * Cancel a command authentication attempt and terminate its process.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The integrationCommandCancel request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec integrationCommandCancelWithResponseSpec(IntegrationCommandCancelRequest requestParameters) throws WebClientResponseException {
        return this.integrationCommandCancelWithResponseSpec(requestParameters.integrationID(), requestParameters.attemptID(), requestParameters.location());
    }


    /**
     * Cancel command connection
     * Cancel a command authentication attempt and terminate its process.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param integrationID The integrationID parameter
     * @param attemptID The attemptID parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec integrationCommandCancelRequestCreation(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'integrationID' is set
        if (integrationID == null) {
            throw new WebClientResponseException("Missing the required parameter 'integrationID' when calling integrationCommandCancel", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'attemptID' is set
        if (attemptID == null) {
            throw new WebClientResponseException("Missing the required parameter 'attemptID' when calling integrationCommandCancel", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("integrationID", integrationID);
        pathParams.put("attemptID", attemptID);

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
        return apiClient.invokeAPI("/api/integration/{integrationID}/connect/command/{attemptID}", HttpMethod.DELETE, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Cancel command connection
     * Cancel a command authentication attempt and terminate its process.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param integrationID The integrationID parameter
     * @param attemptID The attemptID parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> integrationCommandCancel(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return integrationCommandCancelRequestCreation(integrationID, attemptID, location).bodyToMono(localVarReturnType);
    }

    /**
     * Cancel command connection
     * Cancel a command authentication attempt and terminate its process.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param integrationID The integrationID parameter
     * @param attemptID The attemptID parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> integrationCommandCancelWithHttpInfo(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return integrationCommandCancelRequestCreation(integrationID, attemptID, location).toEntity(localVarReturnType);
    }

    /**
     * Cancel command connection
     * Cancel a command authentication attempt and terminate its process.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param integrationID The integrationID parameter
     * @param attemptID The attemptID parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec integrationCommandCancelWithResponseSpec(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return integrationCommandCancelRequestCreation(integrationID, attemptID, location);
    }

    public class IntegrationCommandConnectRequest {
        private @jakarta.annotation.Nonnull String integrationID;
        private @jakarta.annotation.Nonnull IntegrationCommandConnectRequest integrationCommandConnectRequest;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;

        public IntegrationCommandConnectRequest() {}

        public IntegrationCommandConnectRequest(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull IntegrationCommandConnectRequest integrationCommandConnectRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.integrationID = integrationID;
            this.integrationCommandConnectRequest = integrationCommandConnectRequest;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String integrationID() {
            return this.integrationID;
        }
        public IntegrationCommandConnectRequest integrationID(@jakarta.annotation.Nonnull String integrationID) {
            this.integrationID = integrationID;
            return this;
        }

        public @jakarta.annotation.Nonnull IntegrationCommandConnectRequest integrationCommandConnectRequest() {
            return this.integrationCommandConnectRequest;
        }
        public IntegrationCommandConnectRequest integrationCommandConnectRequest(@jakarta.annotation.Nonnull IntegrationCommandConnectRequest integrationCommandConnectRequest) {
            this.integrationCommandConnectRequest = integrationCommandConnectRequest;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public IntegrationCommandConnectRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
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
            IntegrationCommandConnectRequest request = (IntegrationCommandConnectRequest) o;
            return Objects.equals(this.integrationID, request.integrationID()) &&
                Objects.equals(this.integrationCommandConnectRequest, request.integrationCommandConnectRequest()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(integrationID, integrationCommandConnectRequest, location);
        }
    }

    /**
     * Begin command connection
     * Start a command authentication attempt.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError | IntegrationMethodNotFoundError
     * @param requestParameters The integrationCommandConnect request parameters as object
     * @return IntegrationCommandConnect200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<IntegrationCommandConnect200Response> integrationCommandConnect(IntegrationCommandConnectRequest requestParameters) throws WebClientResponseException {
        return this.integrationCommandConnect(requestParameters.integrationID(), requestParameters.integrationCommandConnectRequest(), requestParameters.location());
    }

    /**
     * Begin command connection
     * Start a command authentication attempt.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError | IntegrationMethodNotFoundError
     * @param requestParameters The integrationCommandConnect request parameters as object
     * @return ResponseEntity&lt;IntegrationCommandConnect200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<IntegrationCommandConnect200Response>> integrationCommandConnectWithHttpInfo(IntegrationCommandConnectRequest requestParameters) throws WebClientResponseException {
        return this.integrationCommandConnectWithHttpInfo(requestParameters.integrationID(), requestParameters.integrationCommandConnectRequest(), requestParameters.location());
    }

    /**
     * Begin command connection
     * Start a command authentication attempt.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError | IntegrationMethodNotFoundError
     * @param requestParameters The integrationCommandConnect request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec integrationCommandConnectWithResponseSpec(IntegrationCommandConnectRequest requestParameters) throws WebClientResponseException {
        return this.integrationCommandConnectWithResponseSpec(requestParameters.integrationID(), requestParameters.integrationCommandConnectRequest(), requestParameters.location());
    }


    /**
     * Begin command connection
     * Start a command authentication attempt.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError | IntegrationMethodNotFoundError
     * @param integrationID The integrationID parameter
     * @param integrationCommandConnectRequest The integrationCommandConnectRequest parameter
     * @param location The location parameter
     * @return IntegrationCommandConnect200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec integrationCommandConnectRequestCreation(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull IntegrationCommandConnectRequest integrationCommandConnectRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = integrationCommandConnectRequest;
        // verify the required parameter 'integrationID' is set
        if (integrationID == null) {
            throw new WebClientResponseException("Missing the required parameter 'integrationID' when calling integrationCommandConnect", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'integrationCommandConnectRequest' is set
        if (integrationCommandConnectRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'integrationCommandConnectRequest' when calling integrationCommandConnect", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("integrationID", integrationID);

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

        ParameterizedTypeReference<IntegrationCommandConnect200Response> localVarReturnType = new ParameterizedTypeReference<IntegrationCommandConnect200Response>() {};
        return apiClient.invokeAPI("/api/integration/{integrationID}/connect/command", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Begin command connection
     * Start a command authentication attempt.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError | IntegrationMethodNotFoundError
     * @param integrationID The integrationID parameter
     * @param integrationCommandConnectRequest The integrationCommandConnectRequest parameter
     * @param location The location parameter
     * @return IntegrationCommandConnect200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<IntegrationCommandConnect200Response> integrationCommandConnect(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull IntegrationCommandConnectRequest integrationCommandConnectRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<IntegrationCommandConnect200Response> localVarReturnType = new ParameterizedTypeReference<IntegrationCommandConnect200Response>() {};
        return integrationCommandConnectRequestCreation(integrationID, integrationCommandConnectRequest, location).bodyToMono(localVarReturnType);
    }

    /**
     * Begin command connection
     * Start a command authentication attempt.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError | IntegrationMethodNotFoundError
     * @param integrationID The integrationID parameter
     * @param integrationCommandConnectRequest The integrationCommandConnectRequest parameter
     * @param location The location parameter
     * @return ResponseEntity&lt;IntegrationCommandConnect200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<IntegrationCommandConnect200Response>> integrationCommandConnectWithHttpInfo(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull IntegrationCommandConnectRequest integrationCommandConnectRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<IntegrationCommandConnect200Response> localVarReturnType = new ParameterizedTypeReference<IntegrationCommandConnect200Response>() {};
        return integrationCommandConnectRequestCreation(integrationID, integrationCommandConnectRequest, location).toEntity(localVarReturnType);
    }

    /**
     * Begin command connection
     * Start a command authentication attempt.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError | IntegrationMethodNotFoundError
     * @param integrationID The integrationID parameter
     * @param integrationCommandConnectRequest The integrationCommandConnectRequest parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec integrationCommandConnectWithResponseSpec(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull IntegrationCommandConnectRequest integrationCommandConnectRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return integrationCommandConnectRequestCreation(integrationID, integrationCommandConnectRequest, location);
    }

    public class IntegrationCommandStatusRequest {
        private @jakarta.annotation.Nonnull String integrationID;
        private @jakarta.annotation.Nonnull String attemptID;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;

        public IntegrationCommandStatusRequest() {}

        public IntegrationCommandStatusRequest(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.integrationID = integrationID;
            this.attemptID = attemptID;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String integrationID() {
            return this.integrationID;
        }
        public IntegrationCommandStatusRequest integrationID(@jakarta.annotation.Nonnull String integrationID) {
            this.integrationID = integrationID;
            return this;
        }

        public @jakarta.annotation.Nonnull String attemptID() {
            return this.attemptID;
        }
        public IntegrationCommandStatusRequest attemptID(@jakarta.annotation.Nonnull String attemptID) {
            this.attemptID = attemptID;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public IntegrationCommandStatusRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
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
            IntegrationCommandStatusRequest request = (IntegrationCommandStatusRequest) o;
            return Objects.equals(this.integrationID, request.integrationID()) &&
                Objects.equals(this.attemptID, request.attemptID()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(integrationID, attemptID, location);
        }
    }

    /**
     * Get command attempt status
     * Poll the current status and output of a command authentication attempt.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError | IntegrationAttemptNotFoundError
     * @param requestParameters The integrationCommandStatus request parameters as object
     * @return IntegrationCommandStatus200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<IntegrationCommandStatus200Response> integrationCommandStatus(IntegrationCommandStatusRequest requestParameters) throws WebClientResponseException {
        return this.integrationCommandStatus(requestParameters.integrationID(), requestParameters.attemptID(), requestParameters.location());
    }

    /**
     * Get command attempt status
     * Poll the current status and output of a command authentication attempt.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError | IntegrationAttemptNotFoundError
     * @param requestParameters The integrationCommandStatus request parameters as object
     * @return ResponseEntity&lt;IntegrationCommandStatus200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<IntegrationCommandStatus200Response>> integrationCommandStatusWithHttpInfo(IntegrationCommandStatusRequest requestParameters) throws WebClientResponseException {
        return this.integrationCommandStatusWithHttpInfo(requestParameters.integrationID(), requestParameters.attemptID(), requestParameters.location());
    }

    /**
     * Get command attempt status
     * Poll the current status and output of a command authentication attempt.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError | IntegrationAttemptNotFoundError
     * @param requestParameters The integrationCommandStatus request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec integrationCommandStatusWithResponseSpec(IntegrationCommandStatusRequest requestParameters) throws WebClientResponseException {
        return this.integrationCommandStatusWithResponseSpec(requestParameters.integrationID(), requestParameters.attemptID(), requestParameters.location());
    }


    /**
     * Get command attempt status
     * Poll the current status and output of a command authentication attempt.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError | IntegrationAttemptNotFoundError
     * @param integrationID The integrationID parameter
     * @param attemptID The attemptID parameter
     * @param location The location parameter
     * @return IntegrationCommandStatus200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec integrationCommandStatusRequestCreation(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'integrationID' is set
        if (integrationID == null) {
            throw new WebClientResponseException("Missing the required parameter 'integrationID' when calling integrationCommandStatus", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'attemptID' is set
        if (attemptID == null) {
            throw new WebClientResponseException("Missing the required parameter 'attemptID' when calling integrationCommandStatus", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("integrationID", integrationID);
        pathParams.put("attemptID", attemptID);

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

        ParameterizedTypeReference<IntegrationCommandStatus200Response> localVarReturnType = new ParameterizedTypeReference<IntegrationCommandStatus200Response>() {};
        return apiClient.invokeAPI("/api/integration/{integrationID}/connect/command/{attemptID}", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get command attempt status
     * Poll the current status and output of a command authentication attempt.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError | IntegrationAttemptNotFoundError
     * @param integrationID The integrationID parameter
     * @param attemptID The attemptID parameter
     * @param location The location parameter
     * @return IntegrationCommandStatus200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<IntegrationCommandStatus200Response> integrationCommandStatus(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<IntegrationCommandStatus200Response> localVarReturnType = new ParameterizedTypeReference<IntegrationCommandStatus200Response>() {};
        return integrationCommandStatusRequestCreation(integrationID, attemptID, location).bodyToMono(localVarReturnType);
    }

    /**
     * Get command attempt status
     * Poll the current status and output of a command authentication attempt.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError | IntegrationAttemptNotFoundError
     * @param integrationID The integrationID parameter
     * @param attemptID The attemptID parameter
     * @param location The location parameter
     * @return ResponseEntity&lt;IntegrationCommandStatus200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<IntegrationCommandStatus200Response>> integrationCommandStatusWithHttpInfo(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<IntegrationCommandStatus200Response> localVarReturnType = new ParameterizedTypeReference<IntegrationCommandStatus200Response>() {};
        return integrationCommandStatusRequestCreation(integrationID, attemptID, location).toEntity(localVarReturnType);
    }

    /**
     * Get command attempt status
     * Poll the current status and output of a command authentication attempt.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError | IntegrationAttemptNotFoundError
     * @param integrationID The integrationID parameter
     * @param attemptID The attemptID parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec integrationCommandStatusWithResponseSpec(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return integrationCommandStatusRequestCreation(integrationID, attemptID, location);
    }

    public class IntegrationConnectKeyRequest {
        private @jakarta.annotation.Nonnull String integrationID;
        private @jakarta.annotation.Nonnull IntegrationConnectKeyRequest integrationConnectKeyRequest;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;

        public IntegrationConnectKeyRequest() {}

        public IntegrationConnectKeyRequest(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull IntegrationConnectKeyRequest integrationConnectKeyRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.integrationID = integrationID;
            this.integrationConnectKeyRequest = integrationConnectKeyRequest;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String integrationID() {
            return this.integrationID;
        }
        public IntegrationConnectKeyRequest integrationID(@jakarta.annotation.Nonnull String integrationID) {
            this.integrationID = integrationID;
            return this;
        }

        public @jakarta.annotation.Nonnull IntegrationConnectKeyRequest integrationConnectKeyRequest() {
            return this.integrationConnectKeyRequest;
        }
        public IntegrationConnectKeyRequest integrationConnectKeyRequest(@jakarta.annotation.Nonnull IntegrationConnectKeyRequest integrationConnectKeyRequest) {
            this.integrationConnectKeyRequest = integrationConnectKeyRequest;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public IntegrationConnectKeyRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
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
            IntegrationConnectKeyRequest request = (IntegrationConnectKeyRequest) o;
            return Objects.equals(this.integrationID, request.integrationID()) &&
                Objects.equals(this.integrationConnectKeyRequest, request.integrationConnectKeyRequest()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(integrationID, integrationConnectKeyRequest, location);
        }
    }

    /**
     * Connect with key
     * Run a key authentication method and store the resulting credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError
     * @param requestParameters The integrationConnectKey request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> integrationConnectKey(IntegrationConnectKeyRequest requestParameters) throws WebClientResponseException {
        return this.integrationConnectKey(requestParameters.integrationID(), requestParameters.integrationConnectKeyRequest(), requestParameters.location());
    }

    /**
     * Connect with key
     * Run a key authentication method and store the resulting credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError
     * @param requestParameters The integrationConnectKey request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> integrationConnectKeyWithHttpInfo(IntegrationConnectKeyRequest requestParameters) throws WebClientResponseException {
        return this.integrationConnectKeyWithHttpInfo(requestParameters.integrationID(), requestParameters.integrationConnectKeyRequest(), requestParameters.location());
    }

    /**
     * Connect with key
     * Run a key authentication method and store the resulting credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError
     * @param requestParameters The integrationConnectKey request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec integrationConnectKeyWithResponseSpec(IntegrationConnectKeyRequest requestParameters) throws WebClientResponseException {
        return this.integrationConnectKeyWithResponseSpec(requestParameters.integrationID(), requestParameters.integrationConnectKeyRequest(), requestParameters.location());
    }


    /**
     * Connect with key
     * Run a key authentication method and store the resulting credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError
     * @param integrationID The integrationID parameter
     * @param integrationConnectKeyRequest The integrationConnectKeyRequest parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec integrationConnectKeyRequestCreation(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull IntegrationConnectKeyRequest integrationConnectKeyRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = integrationConnectKeyRequest;
        // verify the required parameter 'integrationID' is set
        if (integrationID == null) {
            throw new WebClientResponseException("Missing the required parameter 'integrationID' when calling integrationConnectKey", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'integrationConnectKeyRequest' is set
        if (integrationConnectKeyRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'integrationConnectKeyRequest' when calling integrationConnectKey", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("integrationID", integrationID);

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
        return apiClient.invokeAPI("/api/integration/{integrationID}/connect/key", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Connect with key
     * Run a key authentication method and store the resulting credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError
     * @param integrationID The integrationID parameter
     * @param integrationConnectKeyRequest The integrationConnectKeyRequest parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> integrationConnectKey(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull IntegrationConnectKeyRequest integrationConnectKeyRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return integrationConnectKeyRequestCreation(integrationID, integrationConnectKeyRequest, location).bodyToMono(localVarReturnType);
    }

    /**
     * Connect with key
     * Run a key authentication method and store the resulting credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError
     * @param integrationID The integrationID parameter
     * @param integrationConnectKeyRequest The integrationConnectKeyRequest parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> integrationConnectKeyWithHttpInfo(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull IntegrationConnectKeyRequest integrationConnectKeyRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return integrationConnectKeyRequestCreation(integrationID, integrationConnectKeyRequest, location).toEntity(localVarReturnType);
    }

    /**
     * Connect with key
     * Run a key authentication method and store the resulting credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError
     * @param integrationID The integrationID parameter
     * @param integrationConnectKeyRequest The integrationConnectKeyRequest parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec integrationConnectKeyWithResponseSpec(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull IntegrationConnectKeyRequest integrationConnectKeyRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return integrationConnectKeyRequestCreation(integrationID, integrationConnectKeyRequest, location);
    }

    public class IntegrationGetRequest {
        private @jakarta.annotation.Nullable String integrationID;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;

        public IntegrationGetRequest() {}

        public IntegrationGetRequest(@jakarta.annotation.Nullable String integrationID, @jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.integrationID = integrationID;
            this.location = location;
        }

        public @jakarta.annotation.Nullable String integrationID() {
            return this.integrationID;
        }
        public IntegrationGetRequest integrationID(@jakarta.annotation.Nullable String integrationID) {
            this.integrationID = integrationID;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public IntegrationGetRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
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
            IntegrationGetRequest request = (IntegrationGetRequest) o;
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
     * <p><b>404</b> - IntegrationNotFoundError
     * @param requestParameters The integrationGet request parameters as object
     * @return IntegrationGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<IntegrationGet200Response> integrationGet(IntegrationGetRequest requestParameters) throws WebClientResponseException {
        return this.integrationGet(requestParameters.integrationID(), requestParameters.location());
    }

    /**
     * Get integration
     * Retrieve one integration and its authentication methods.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError
     * @param requestParameters The integrationGet request parameters as object
     * @return ResponseEntity&lt;IntegrationGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<IntegrationGet200Response>> integrationGetWithHttpInfo(IntegrationGetRequest requestParameters) throws WebClientResponseException {
        return this.integrationGetWithHttpInfo(requestParameters.integrationID(), requestParameters.location());
    }

    /**
     * Get integration
     * Retrieve one integration and its authentication methods.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError
     * @param requestParameters The integrationGet request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec integrationGetWithResponseSpec(IntegrationGetRequest requestParameters) throws WebClientResponseException {
        return this.integrationGetWithResponseSpec(requestParameters.integrationID(), requestParameters.location());
    }


    /**
     * Get integration
     * Retrieve one integration and its authentication methods.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError
     * @param integrationID The integrationID parameter
     * @param location The location parameter
     * @return IntegrationGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec integrationGetRequestCreation(@jakarta.annotation.Nullable String integrationID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'integrationID' is set
        if (integrationID == null) {
            throw new WebClientResponseException("Missing the required parameter 'integrationID' when calling integrationGet", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("integrationID", integrationID);

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

        ParameterizedTypeReference<IntegrationGet200Response> localVarReturnType = new ParameterizedTypeReference<IntegrationGet200Response>() {};
        return apiClient.invokeAPI("/api/integration/{integrationID}", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get integration
     * Retrieve one integration and its authentication methods.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError
     * @param integrationID The integrationID parameter
     * @param location The location parameter
     * @return IntegrationGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<IntegrationGet200Response> integrationGet(@jakarta.annotation.Nullable String integrationID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<IntegrationGet200Response> localVarReturnType = new ParameterizedTypeReference<IntegrationGet200Response>() {};
        return integrationGetRequestCreation(integrationID, location).bodyToMono(localVarReturnType);
    }

    /**
     * Get integration
     * Retrieve one integration and its authentication methods.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError
     * @param integrationID The integrationID parameter
     * @param location The location parameter
     * @return ResponseEntity&lt;IntegrationGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<IntegrationGet200Response>> integrationGetWithHttpInfo(@jakarta.annotation.Nullable String integrationID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<IntegrationGet200Response> localVarReturnType = new ParameterizedTypeReference<IntegrationGet200Response>() {};
        return integrationGetRequestCreation(integrationID, location).toEntity(localVarReturnType);
    }

    /**
     * Get integration
     * Retrieve one integration and its authentication methods.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError
     * @param integrationID The integrationID parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec integrationGetWithResponseSpec(@jakarta.annotation.Nullable String integrationID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return integrationGetRequestCreation(integrationID, location);
    }

    /**
     * List integrations
     * Retrieve available integrations and their authentication methods.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return IntegrationList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec integrationListRequestCreation(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
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

        ParameterizedTypeReference<IntegrationList200Response> localVarReturnType = new ParameterizedTypeReference<IntegrationList200Response>() {};
        return apiClient.invokeAPI("/api/integration", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List integrations
     * Retrieve available integrations and their authentication methods.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return IntegrationList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<IntegrationList200Response> integrationList(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<IntegrationList200Response> localVarReturnType = new ParameterizedTypeReference<IntegrationList200Response>() {};
        return integrationListRequestCreation(location).bodyToMono(localVarReturnType);
    }

    /**
     * List integrations
     * Retrieve available integrations and their authentication methods.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseEntity&lt;IntegrationList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<IntegrationList200Response>> integrationListWithHttpInfo(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<IntegrationList200Response> localVarReturnType = new ParameterizedTypeReference<IntegrationList200Response>() {};
        return integrationListRequestCreation(location).toEntity(localVarReturnType);
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
    public ResponseSpec integrationListWithResponseSpec(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return integrationListRequestCreation(location);
    }

    public class IntegrationOauthCancelRequest {
        private @jakarta.annotation.Nonnull String integrationID;
        private @jakarta.annotation.Nonnull String attemptID;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;

        public IntegrationOauthCancelRequest() {}

        public IntegrationOauthCancelRequest(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.integrationID = integrationID;
            this.attemptID = attemptID;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String integrationID() {
            return this.integrationID;
        }
        public IntegrationOauthCancelRequest integrationID(@jakarta.annotation.Nonnull String integrationID) {
            this.integrationID = integrationID;
            return this;
        }

        public @jakarta.annotation.Nonnull String attemptID() {
            return this.attemptID;
        }
        public IntegrationOauthCancelRequest attemptID(@jakarta.annotation.Nonnull String attemptID) {
            this.attemptID = attemptID;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public IntegrationOauthCancelRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
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
            IntegrationOauthCancelRequest request = (IntegrationOauthCancelRequest) o;
            return Objects.equals(this.integrationID, request.integrationID()) &&
                Objects.equals(this.attemptID, request.attemptID()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(integrationID, attemptID, location);
        }
    }

    /**
     * Cancel OAuth connection
     * Cancel an OAuth attempt and release its resources.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The integrationOauthCancel request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> integrationOauthCancel(IntegrationOauthCancelRequest requestParameters) throws WebClientResponseException {
        return this.integrationOauthCancel(requestParameters.integrationID(), requestParameters.attemptID(), requestParameters.location());
    }

    /**
     * Cancel OAuth connection
     * Cancel an OAuth attempt and release its resources.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The integrationOauthCancel request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> integrationOauthCancelWithHttpInfo(IntegrationOauthCancelRequest requestParameters) throws WebClientResponseException {
        return this.integrationOauthCancelWithHttpInfo(requestParameters.integrationID(), requestParameters.attemptID(), requestParameters.location());
    }

    /**
     * Cancel OAuth connection
     * Cancel an OAuth attempt and release its resources.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The integrationOauthCancel request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec integrationOauthCancelWithResponseSpec(IntegrationOauthCancelRequest requestParameters) throws WebClientResponseException {
        return this.integrationOauthCancelWithResponseSpec(requestParameters.integrationID(), requestParameters.attemptID(), requestParameters.location());
    }


    /**
     * Cancel OAuth connection
     * Cancel an OAuth attempt and release its resources.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param integrationID The integrationID parameter
     * @param attemptID The attemptID parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec integrationOauthCancelRequestCreation(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'integrationID' is set
        if (integrationID == null) {
            throw new WebClientResponseException("Missing the required parameter 'integrationID' when calling integrationOauthCancel", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'attemptID' is set
        if (attemptID == null) {
            throw new WebClientResponseException("Missing the required parameter 'attemptID' when calling integrationOauthCancel", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("integrationID", integrationID);
        pathParams.put("attemptID", attemptID);

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
        return apiClient.invokeAPI("/api/integration/{integrationID}/connect/oauth/{attemptID}", HttpMethod.DELETE, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Cancel OAuth connection
     * Cancel an OAuth attempt and release its resources.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param integrationID The integrationID parameter
     * @param attemptID The attemptID parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> integrationOauthCancel(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return integrationOauthCancelRequestCreation(integrationID, attemptID, location).bodyToMono(localVarReturnType);
    }

    /**
     * Cancel OAuth connection
     * Cancel an OAuth attempt and release its resources.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param integrationID The integrationID parameter
     * @param attemptID The attemptID parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> integrationOauthCancelWithHttpInfo(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return integrationOauthCancelRequestCreation(integrationID, attemptID, location).toEntity(localVarReturnType);
    }

    /**
     * Cancel OAuth connection
     * Cancel an OAuth attempt and release its resources.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param integrationID The integrationID parameter
     * @param attemptID The attemptID parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec integrationOauthCancelWithResponseSpec(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return integrationOauthCancelRequestCreation(integrationID, attemptID, location);
    }

    public class IntegrationOauthCompleteRequest {
        private @jakarta.annotation.Nonnull String integrationID;
        private @jakarta.annotation.Nonnull String attemptID;
        private @jakarta.annotation.Nonnull IntegrationOauthCompleteRequest integrationOauthCompleteRequest;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;

        public IntegrationOauthCompleteRequest() {}

        public IntegrationOauthCompleteRequest(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nonnull IntegrationOauthCompleteRequest integrationOauthCompleteRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.integrationID = integrationID;
            this.attemptID = attemptID;
            this.integrationOauthCompleteRequest = integrationOauthCompleteRequest;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String integrationID() {
            return this.integrationID;
        }
        public IntegrationOauthCompleteRequest integrationID(@jakarta.annotation.Nonnull String integrationID) {
            this.integrationID = integrationID;
            return this;
        }

        public @jakarta.annotation.Nonnull String attemptID() {
            return this.attemptID;
        }
        public IntegrationOauthCompleteRequest attemptID(@jakarta.annotation.Nonnull String attemptID) {
            this.attemptID = attemptID;
            return this;
        }

        public @jakarta.annotation.Nonnull IntegrationOauthCompleteRequest integrationOauthCompleteRequest() {
            return this.integrationOauthCompleteRequest;
        }
        public IntegrationOauthCompleteRequest integrationOauthCompleteRequest(@jakarta.annotation.Nonnull IntegrationOauthCompleteRequest integrationOauthCompleteRequest) {
            this.integrationOauthCompleteRequest = integrationOauthCompleteRequest;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public IntegrationOauthCompleteRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
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
            IntegrationOauthCompleteRequest request = (IntegrationOauthCompleteRequest) o;
            return Objects.equals(this.integrationID, request.integrationID()) &&
                Objects.equals(this.attemptID, request.attemptID()) &&
                Objects.equals(this.integrationOauthCompleteRequest, request.integrationOauthCompleteRequest()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(integrationID, attemptID, integrationOauthCompleteRequest, location);
        }
    }

    /**
     * Complete OAuth connection
     * Complete a code-based OAuth attempt and store the resulting credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError | IntegrationAttemptNotFoundError
     * @param requestParameters The integrationOauthComplete request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> integrationOauthComplete(IntegrationOauthCompleteRequest requestParameters) throws WebClientResponseException {
        return this.integrationOauthComplete(requestParameters.integrationID(), requestParameters.attemptID(), requestParameters.integrationOauthCompleteRequest(), requestParameters.location());
    }

    /**
     * Complete OAuth connection
     * Complete a code-based OAuth attempt and store the resulting credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError | IntegrationAttemptNotFoundError
     * @param requestParameters The integrationOauthComplete request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> integrationOauthCompleteWithHttpInfo(IntegrationOauthCompleteRequest requestParameters) throws WebClientResponseException {
        return this.integrationOauthCompleteWithHttpInfo(requestParameters.integrationID(), requestParameters.attemptID(), requestParameters.integrationOauthCompleteRequest(), requestParameters.location());
    }

    /**
     * Complete OAuth connection
     * Complete a code-based OAuth attempt and store the resulting credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError | IntegrationAttemptNotFoundError
     * @param requestParameters The integrationOauthComplete request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec integrationOauthCompleteWithResponseSpec(IntegrationOauthCompleteRequest requestParameters) throws WebClientResponseException {
        return this.integrationOauthCompleteWithResponseSpec(requestParameters.integrationID(), requestParameters.attemptID(), requestParameters.integrationOauthCompleteRequest(), requestParameters.location());
    }


    /**
     * Complete OAuth connection
     * Complete a code-based OAuth attempt and store the resulting credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError | IntegrationAttemptNotFoundError
     * @param integrationID The integrationID parameter
     * @param attemptID The attemptID parameter
     * @param integrationOauthCompleteRequest The integrationOauthCompleteRequest parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec integrationOauthCompleteRequestCreation(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nonnull IntegrationOauthCompleteRequest integrationOauthCompleteRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = integrationOauthCompleteRequest;
        // verify the required parameter 'integrationID' is set
        if (integrationID == null) {
            throw new WebClientResponseException("Missing the required parameter 'integrationID' when calling integrationOauthComplete", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'attemptID' is set
        if (attemptID == null) {
            throw new WebClientResponseException("Missing the required parameter 'attemptID' when calling integrationOauthComplete", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'integrationOauthCompleteRequest' is set
        if (integrationOauthCompleteRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'integrationOauthCompleteRequest' when calling integrationOauthComplete", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("integrationID", integrationID);
        pathParams.put("attemptID", attemptID);

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
        return apiClient.invokeAPI("/api/integration/{integrationID}/connect/oauth/{attemptID}/complete", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Complete OAuth connection
     * Complete a code-based OAuth attempt and store the resulting credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError | IntegrationAttemptNotFoundError
     * @param integrationID The integrationID parameter
     * @param attemptID The attemptID parameter
     * @param integrationOauthCompleteRequest The integrationOauthCompleteRequest parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> integrationOauthComplete(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nonnull IntegrationOauthCompleteRequest integrationOauthCompleteRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return integrationOauthCompleteRequestCreation(integrationID, attemptID, integrationOauthCompleteRequest, location).bodyToMono(localVarReturnType);
    }

    /**
     * Complete OAuth connection
     * Complete a code-based OAuth attempt and store the resulting credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError | IntegrationAttemptNotFoundError
     * @param integrationID The integrationID parameter
     * @param attemptID The attemptID parameter
     * @param integrationOauthCompleteRequest The integrationOauthCompleteRequest parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> integrationOauthCompleteWithHttpInfo(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nonnull IntegrationOauthCompleteRequest integrationOauthCompleteRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return integrationOauthCompleteRequestCreation(integrationID, attemptID, integrationOauthCompleteRequest, location).toEntity(localVarReturnType);
    }

    /**
     * Complete OAuth connection
     * Complete a code-based OAuth attempt and store the resulting credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError | IntegrationAttemptNotFoundError
     * @param integrationID The integrationID parameter
     * @param attemptID The attemptID parameter
     * @param integrationOauthCompleteRequest The integrationOauthCompleteRequest parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec integrationOauthCompleteWithResponseSpec(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull String attemptID, @jakarta.annotation.Nonnull IntegrationOauthCompleteRequest integrationOauthCompleteRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return integrationOauthCompleteRequestCreation(integrationID, attemptID, integrationOauthCompleteRequest, location);
    }

    public class IntegrationOauthConnectRequest {
        private @jakarta.annotation.Nonnull String integrationID;
        private @jakarta.annotation.Nonnull IntegrationOauthConnectRequest integrationOauthConnectRequest;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;

        public IntegrationOauthConnectRequest() {}

        public IntegrationOauthConnectRequest(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull IntegrationOauthConnectRequest integrationOauthConnectRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.integrationID = integrationID;
            this.integrationOauthConnectRequest = integrationOauthConnectRequest;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String integrationID() {
            return this.integrationID;
        }
        public IntegrationOauthConnectRequest integrationID(@jakarta.annotation.Nonnull String integrationID) {
            this.integrationID = integrationID;
            return this;
        }

        public @jakarta.annotation.Nonnull IntegrationOauthConnectRequest integrationOauthConnectRequest() {
            return this.integrationOauthConnectRequest;
        }
        public IntegrationOauthConnectRequest integrationOauthConnectRequest(@jakarta.annotation.Nonnull IntegrationOauthConnectRequest integrationOauthConnectRequest) {
            this.integrationOauthConnectRequest = integrationOauthConnectRequest;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public IntegrationOauthConnectRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
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
            IntegrationOauthConnectRequest request = (IntegrationOauthConnectRequest) o;
            return Objects.equals(this.integrationID, request.integrationID()) &&
                Objects.equals(this.integrationOauthConnectRequest, request.integrationOauthConnectRequest()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(integrationID, integrationOauthConnectRequest, location);
        }
    }

    /**
     * Begin OAuth connection
     * Start an OAuth attempt and return the authorization details.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The integrationOauthConnect request parameters as object
     * @return IntegrationOauthConnect200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<IntegrationOauthConnect200Response> integrationOauthConnect(IntegrationOauthConnectRequest requestParameters) throws WebClientResponseException {
        return this.integrationOauthConnect(requestParameters.integrationID(), requestParameters.integrationOauthConnectRequest(), requestParameters.location());
    }

    /**
     * Begin OAuth connection
     * Start an OAuth attempt and return the authorization details.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The integrationOauthConnect request parameters as object
     * @return ResponseEntity&lt;IntegrationOauthConnect200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<IntegrationOauthConnect200Response>> integrationOauthConnectWithHttpInfo(IntegrationOauthConnectRequest requestParameters) throws WebClientResponseException {
        return this.integrationOauthConnectWithHttpInfo(requestParameters.integrationID(), requestParameters.integrationOauthConnectRequest(), requestParameters.location());
    }

    /**
     * Begin OAuth connection
     * Start an OAuth attempt and return the authorization details.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The integrationOauthConnect request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec integrationOauthConnectWithResponseSpec(IntegrationOauthConnectRequest requestParameters) throws WebClientResponseException {
        return this.integrationOauthConnectWithResponseSpec(requestParameters.integrationID(), requestParameters.integrationOauthConnectRequest(), requestParameters.location());
    }


    /**
     * Begin OAuth connection
     * Start an OAuth attempt and return the authorization details.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param integrationID The integrationID parameter
     * @param integrationOauthConnectRequest The integrationOauthConnectRequest parameter
     * @param location The location parameter
     * @return IntegrationOauthConnect200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec integrationOauthConnectRequestCreation(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull IntegrationOauthConnectRequest integrationOauthConnectRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = integrationOauthConnectRequest;
        // verify the required parameter 'integrationID' is set
        if (integrationID == null) {
            throw new WebClientResponseException("Missing the required parameter 'integrationID' when calling integrationOauthConnect", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'integrationOauthConnectRequest' is set
        if (integrationOauthConnectRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'integrationOauthConnectRequest' when calling integrationOauthConnect", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("integrationID", integrationID);

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

        ParameterizedTypeReference<IntegrationOauthConnect200Response> localVarReturnType = new ParameterizedTypeReference<IntegrationOauthConnect200Response>() {};
        return apiClient.invokeAPI("/api/integration/{integrationID}/connect/oauth", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Begin OAuth connection
     * Start an OAuth attempt and return the authorization details.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param integrationID The integrationID parameter
     * @param integrationOauthConnectRequest The integrationOauthConnectRequest parameter
     * @param location The location parameter
     * @return IntegrationOauthConnect200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<IntegrationOauthConnect200Response> integrationOauthConnect(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull IntegrationOauthConnectRequest integrationOauthConnectRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<IntegrationOauthConnect200Response> localVarReturnType = new ParameterizedTypeReference<IntegrationOauthConnect200Response>() {};
        return integrationOauthConnectRequestCreation(integrationID, integrationOauthConnectRequest, location).bodyToMono(localVarReturnType);
    }

    /**
     * Begin OAuth connection
     * Start an OAuth attempt and return the authorization details.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param integrationID The integrationID parameter
     * @param integrationOauthConnectRequest The integrationOauthConnectRequest parameter
     * @param location The location parameter
     * @return ResponseEntity&lt;IntegrationOauthConnect200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<IntegrationOauthConnect200Response>> integrationOauthConnectWithHttpInfo(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull IntegrationOauthConnectRequest integrationOauthConnectRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<IntegrationOauthConnect200Response> localVarReturnType = new ParameterizedTypeReference<IntegrationOauthConnect200Response>() {};
        return integrationOauthConnectRequestCreation(integrationID, integrationOauthConnectRequest, location).toEntity(localVarReturnType);
    }

    /**
     * Begin OAuth connection
     * Start an OAuth attempt and return the authorization details.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param integrationID The integrationID parameter
     * @param integrationOauthConnectRequest The integrationOauthConnectRequest parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec integrationOauthConnectWithResponseSpec(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nonnull IntegrationOauthConnectRequest integrationOauthConnectRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return integrationOauthConnectRequestCreation(integrationID, integrationOauthConnectRequest, location);
    }

    public class IntegrationOauthStatusRequest {
        private @jakarta.annotation.Nonnull String integrationID;
        private @jakarta.annotation.Nullable String attemptID;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;

        public IntegrationOauthStatusRequest() {}

        public IntegrationOauthStatusRequest(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nullable String attemptID, @jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.integrationID = integrationID;
            this.attemptID = attemptID;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String integrationID() {
            return this.integrationID;
        }
        public IntegrationOauthStatusRequest integrationID(@jakarta.annotation.Nonnull String integrationID) {
            this.integrationID = integrationID;
            return this;
        }

        public @jakarta.annotation.Nullable String attemptID() {
            return this.attemptID;
        }
        public IntegrationOauthStatusRequest attemptID(@jakarta.annotation.Nullable String attemptID) {
            this.attemptID = attemptID;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public IntegrationOauthStatusRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
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
            IntegrationOauthStatusRequest request = (IntegrationOauthStatusRequest) o;
            return Objects.equals(this.integrationID, request.integrationID()) &&
                Objects.equals(this.attemptID, request.attemptID()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(integrationID, attemptID, location);
        }
    }

    /**
     * Get OAuth attempt status
     * Poll the current status of an OAuth attempt.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError | IntegrationAttemptNotFoundError
     * @param requestParameters The integrationOauthStatus request parameters as object
     * @return IntegrationOauthStatus200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<IntegrationOauthStatus200Response> integrationOauthStatus(IntegrationOauthStatusRequest requestParameters) throws WebClientResponseException {
        return this.integrationOauthStatus(requestParameters.integrationID(), requestParameters.attemptID(), requestParameters.location());
    }

    /**
     * Get OAuth attempt status
     * Poll the current status of an OAuth attempt.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError | IntegrationAttemptNotFoundError
     * @param requestParameters The integrationOauthStatus request parameters as object
     * @return ResponseEntity&lt;IntegrationOauthStatus200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<IntegrationOauthStatus200Response>> integrationOauthStatusWithHttpInfo(IntegrationOauthStatusRequest requestParameters) throws WebClientResponseException {
        return this.integrationOauthStatusWithHttpInfo(requestParameters.integrationID(), requestParameters.attemptID(), requestParameters.location());
    }

    /**
     * Get OAuth attempt status
     * Poll the current status of an OAuth attempt.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError | IntegrationAttemptNotFoundError
     * @param requestParameters The integrationOauthStatus request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec integrationOauthStatusWithResponseSpec(IntegrationOauthStatusRequest requestParameters) throws WebClientResponseException {
        return this.integrationOauthStatusWithResponseSpec(requestParameters.integrationID(), requestParameters.attemptID(), requestParameters.location());
    }


    /**
     * Get OAuth attempt status
     * Poll the current status of an OAuth attempt.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError | IntegrationAttemptNotFoundError
     * @param integrationID The integrationID parameter
     * @param attemptID The attemptID parameter
     * @param location The location parameter
     * @return IntegrationOauthStatus200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec integrationOauthStatusRequestCreation(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nullable String attemptID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'integrationID' is set
        if (integrationID == null) {
            throw new WebClientResponseException("Missing the required parameter 'integrationID' when calling integrationOauthStatus", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'attemptID' is set
        if (attemptID == null) {
            throw new WebClientResponseException("Missing the required parameter 'attemptID' when calling integrationOauthStatus", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("integrationID", integrationID);
        pathParams.put("attemptID", attemptID);

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

        ParameterizedTypeReference<IntegrationOauthStatus200Response> localVarReturnType = new ParameterizedTypeReference<IntegrationOauthStatus200Response>() {};
        return apiClient.invokeAPI("/api/integration/{integrationID}/connect/oauth/{attemptID}", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get OAuth attempt status
     * Poll the current status of an OAuth attempt.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError | IntegrationAttemptNotFoundError
     * @param integrationID The integrationID parameter
     * @param attemptID The attemptID parameter
     * @param location The location parameter
     * @return IntegrationOauthStatus200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<IntegrationOauthStatus200Response> integrationOauthStatus(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nullable String attemptID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<IntegrationOauthStatus200Response> localVarReturnType = new ParameterizedTypeReference<IntegrationOauthStatus200Response>() {};
        return integrationOauthStatusRequestCreation(integrationID, attemptID, location).bodyToMono(localVarReturnType);
    }

    /**
     * Get OAuth attempt status
     * Poll the current status of an OAuth attempt.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError | IntegrationAttemptNotFoundError
     * @param integrationID The integrationID parameter
     * @param attemptID The attemptID parameter
     * @param location The location parameter
     * @return ResponseEntity&lt;IntegrationOauthStatus200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<IntegrationOauthStatus200Response>> integrationOauthStatusWithHttpInfo(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nullable String attemptID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<IntegrationOauthStatus200Response> localVarReturnType = new ParameterizedTypeReference<IntegrationOauthStatus200Response>() {};
        return integrationOauthStatusRequestCreation(integrationID, attemptID, location).toEntity(localVarReturnType);
    }

    /**
     * Get OAuth attempt status
     * Poll the current status of an OAuth attempt.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - IntegrationNotFoundError | IntegrationAttemptNotFoundError
     * @param integrationID The integrationID parameter
     * @param attemptID The attemptID parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec integrationOauthStatusWithResponseSpec(@jakarta.annotation.Nonnull String integrationID, @jakarta.annotation.Nullable String attemptID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return integrationOauthStatusRequestCreation(integrationID, attemptID, location);
    }
}
