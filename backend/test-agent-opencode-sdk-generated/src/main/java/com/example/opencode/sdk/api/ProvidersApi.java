package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.InvalidRequestError;
import com.example.opencode.sdk.model.ProviderNotFoundError;
import com.example.opencode.sdk.model.ServiceUnavailableError;
import com.example.opencode.sdk.model.UnauthorizedError;
import com.example.opencode.sdk.model.V2AgentListLocationParameter;
import com.example.opencode.sdk.model.V2ProviderGet200Response;
import com.example.opencode.sdk.model.V2ProviderList200Response;

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
public class ProvidersApi {
    private ApiClient apiClient;

    public ProvidersApi() {
        this(new ApiClient());
    }

    public ProvidersApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public class V2ProviderGetRequest {
        private @jakarta.annotation.Nonnull String providerID;
        private @jakarta.annotation.Nullable V2AgentListLocationParameter location;

        public V2ProviderGetRequest() {}

        public V2ProviderGetRequest(@jakarta.annotation.Nonnull String providerID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) {
            this.providerID = providerID;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String providerID() {
            return this.providerID;
        }
        public V2ProviderGetRequest providerID(@jakarta.annotation.Nonnull String providerID) {
            this.providerID = providerID;
            return this;
        }

        public @jakarta.annotation.Nullable V2AgentListLocationParameter location() {
            return this.location;
        }
        public V2ProviderGetRequest location(@jakarta.annotation.Nullable V2AgentListLocationParameter location) {
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
            V2ProviderGetRequest request = (V2ProviderGetRequest) o;
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
     * @param requestParameters The v2ProviderGet request parameters as object
     * @return V2ProviderGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2ProviderGet200Response> v2ProviderGet(V2ProviderGetRequest requestParameters) throws WebClientResponseException {
        return this.v2ProviderGet(requestParameters.providerID(), requestParameters.location());
    }

    /**
     * Get provider
     * Retrieve a single AI provider so clients can inspect its availability and endpoint settings.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProviderNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The v2ProviderGet request parameters as object
     * @return ResponseEntity&lt;V2ProviderGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2ProviderGet200Response>> v2ProviderGetWithHttpInfo(V2ProviderGetRequest requestParameters) throws WebClientResponseException {
        return this.v2ProviderGetWithHttpInfo(requestParameters.providerID(), requestParameters.location());
    }

    /**
     * Get provider
     * Retrieve a single AI provider so clients can inspect its availability and endpoint settings.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProviderNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The v2ProviderGet request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2ProviderGetWithResponseSpec(V2ProviderGetRequest requestParameters) throws WebClientResponseException {
        return this.v2ProviderGetWithResponseSpec(requestParameters.providerID(), requestParameters.location());
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
     * @return V2ProviderGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2ProviderGetRequestCreation(@jakarta.annotation.Nonnull String providerID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'providerID' is set
        if (providerID == null) {
            throw new WebClientResponseException("Missing the required parameter 'providerID' when calling v2ProviderGet", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("providerID", providerID);

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

        ParameterizedTypeReference<V2ProviderGet200Response> localVarReturnType = new ParameterizedTypeReference<V2ProviderGet200Response>() {};
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
     * @return V2ProviderGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2ProviderGet200Response> v2ProviderGet(@jakarta.annotation.Nonnull String providerID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2ProviderGet200Response> localVarReturnType = new ParameterizedTypeReference<V2ProviderGet200Response>() {};
        return v2ProviderGetRequestCreation(providerID, location).bodyToMono(localVarReturnType);
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
     * @return ResponseEntity&lt;V2ProviderGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2ProviderGet200Response>> v2ProviderGetWithHttpInfo(@jakarta.annotation.Nonnull String providerID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2ProviderGet200Response> localVarReturnType = new ParameterizedTypeReference<V2ProviderGet200Response>() {};
        return v2ProviderGetRequestCreation(providerID, location).toEntity(localVarReturnType);
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
    public ResponseSpec v2ProviderGetWithResponseSpec(@jakarta.annotation.Nonnull String providerID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        return v2ProviderGetRequestCreation(providerID, location);
    }

    /**
     * List providers
     * Retrieve active AI providers so clients can show provider availability and configuration.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param location The location parameter
     * @return V2ProviderList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2ProviderListRequestCreation(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
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

        ParameterizedTypeReference<V2ProviderList200Response> localVarReturnType = new ParameterizedTypeReference<V2ProviderList200Response>() {};
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
     * @return V2ProviderList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2ProviderList200Response> v2ProviderList(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2ProviderList200Response> localVarReturnType = new ParameterizedTypeReference<V2ProviderList200Response>() {};
        return v2ProviderListRequestCreation(location).bodyToMono(localVarReturnType);
    }

    /**
     * List providers
     * Retrieve active AI providers so clients can show provider availability and configuration.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param location The location parameter
     * @return ResponseEntity&lt;V2ProviderList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2ProviderList200Response>> v2ProviderListWithHttpInfo(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2ProviderList200Response> localVarReturnType = new ParameterizedTypeReference<V2ProviderList200Response>() {};
        return v2ProviderListRequestCreation(location).toEntity(localVarReturnType);
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
    public ResponseSpec v2ProviderListWithResponseSpec(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        return v2ProviderListRequestCreation(location);
    }
}
