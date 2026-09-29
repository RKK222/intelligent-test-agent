package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.AgentListLocationParameter;
import com.example.opencode.sdk.model.InvalidRequestErrorEncoded;
import com.example.opencode.sdk.model.ProviderGet200Response;
import com.example.opencode.sdk.model.ProviderList200Response;
import com.example.opencode.sdk.model.ProviderNotFoundErrorEncoded;
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
public class ProviderApi {
    private ApiClient apiClient;

    public ProviderApi() {
        this(new ApiClient());
    }

    public ProviderApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public class ProviderGetRequest {
        private @jakarta.annotation.Nullable String providerID;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;

        public ProviderGetRequest() {}

        public ProviderGetRequest(@jakarta.annotation.Nullable String providerID, @jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.providerID = providerID;
            this.location = location;
        }

        public @jakarta.annotation.Nullable String providerID() {
            return this.providerID;
        }
        public ProviderGetRequest providerID(@jakarta.annotation.Nullable String providerID) {
            this.providerID = providerID;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public ProviderGetRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
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
            ProviderGetRequest request = (ProviderGetRequest) o;
            return Objects.equals(this.providerID, request.providerID()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(providerID, location);
        }
    }

    /**
     * Get provider
     * Retrieve a single AI provider so clients can inspect its availability and endpoint settings.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProviderNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The providerGet request parameters as object
     * @return ProviderGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ProviderGet200Response> providerGet(ProviderGetRequest requestParameters) throws WebClientResponseException {
        return this.providerGet(requestParameters.providerID(), requestParameters.location());
    }

    /**
     * Get provider
     * Retrieve a single AI provider so clients can inspect its availability and endpoint settings.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProviderNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The providerGet request parameters as object
     * @return ResponseEntity&lt;ProviderGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ProviderGet200Response>> providerGetWithHttpInfo(ProviderGetRequest requestParameters) throws WebClientResponseException {
        return this.providerGetWithHttpInfo(requestParameters.providerID(), requestParameters.location());
    }

    /**
     * Get provider
     * Retrieve a single AI provider so clients can inspect its availability and endpoint settings.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProviderNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The providerGet request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec providerGetWithResponseSpec(ProviderGetRequest requestParameters) throws WebClientResponseException {
        return this.providerGetWithResponseSpec(requestParameters.providerID(), requestParameters.location());
    }


    /**
     * Get provider
     * Retrieve a single AI provider so clients can inspect its availability and endpoint settings.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProviderNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param providerID The providerID parameter
     * @param location The location parameter
     * @return ProviderGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec providerGetRequestCreation(@jakarta.annotation.Nullable String providerID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'providerID' is set
        if (providerID == null) {
            throw new WebClientResponseException("Missing the required parameter 'providerID' when calling providerGet", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("providerID", providerID);

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

        ParameterizedTypeReference<ProviderGet200Response> localVarReturnType = new ParameterizedTypeReference<ProviderGet200Response>() {};
        return apiClient.invokeAPI("/api/provider/{providerID}", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get provider
     * Retrieve a single AI provider so clients can inspect its availability and endpoint settings.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProviderNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param providerID The providerID parameter
     * @param location The location parameter
     * @return ProviderGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ProviderGet200Response> providerGet(@jakarta.annotation.Nullable String providerID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<ProviderGet200Response> localVarReturnType = new ParameterizedTypeReference<ProviderGet200Response>() {};
        return providerGetRequestCreation(providerID, location).bodyToMono(localVarReturnType);
    }

    /**
     * Get provider
     * Retrieve a single AI provider so clients can inspect its availability and endpoint settings.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProviderNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param providerID The providerID parameter
     * @param location The location parameter
     * @return ResponseEntity&lt;ProviderGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ProviderGet200Response>> providerGetWithHttpInfo(@jakarta.annotation.Nullable String providerID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<ProviderGet200Response> localVarReturnType = new ParameterizedTypeReference<ProviderGet200Response>() {};
        return providerGetRequestCreation(providerID, location).toEntity(localVarReturnType);
    }

    /**
     * Get provider
     * Retrieve a single AI provider so clients can inspect its availability and endpoint settings.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProviderNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param providerID The providerID parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec providerGetWithResponseSpec(@jakarta.annotation.Nullable String providerID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return providerGetRequestCreation(providerID, location);
    }

    /**
     * List providers
     * Retrieve active AI providers so clients can show provider availability and configuration.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param location The location parameter
     * @return ProviderList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec providerListRequestCreation(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
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

        ParameterizedTypeReference<ProviderList200Response> localVarReturnType = new ParameterizedTypeReference<ProviderList200Response>() {};
        return apiClient.invokeAPI("/api/provider", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List providers
     * Retrieve active AI providers so clients can show provider availability and configuration.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param location The location parameter
     * @return ProviderList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ProviderList200Response> providerList(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<ProviderList200Response> localVarReturnType = new ParameterizedTypeReference<ProviderList200Response>() {};
        return providerListRequestCreation(location).bodyToMono(localVarReturnType);
    }

    /**
     * List providers
     * Retrieve active AI providers so clients can show provider availability and configuration.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param location The location parameter
     * @return ResponseEntity&lt;ProviderList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ProviderList200Response>> providerListWithHttpInfo(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<ProviderList200Response> localVarReturnType = new ParameterizedTypeReference<ProviderList200Response>() {};
        return providerListRequestCreation(location).toEntity(localVarReturnType);
    }

    /**
     * List providers
     * Retrieve active AI providers so clients can show provider availability and configuration.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec providerListWithResponseSpec(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return providerListRequestCreation(location);
    }
}
