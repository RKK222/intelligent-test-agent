package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.AgentListLocationParameter;
import com.example.opencode.sdk.model.InvalidRequestErrorEncoded;
import com.example.opencode.sdk.model.PluginCheck400Response;
import com.example.opencode.sdk.model.ServiceUnavailableErrorEncoded;
import com.example.opencode.sdk.model.UnauthorizedErrorEncoded;
import com.example.opencode.sdk.model.WebsearchProviders200Response;
import com.example.opencode.sdk.model.WebsearchQuery200Response;
import com.example.opencode.sdk.model.WebsearchQueryRequest;

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
public class WebsearchApi {
    private ApiClient apiClient;

    public WebsearchApi() {
        this(new ApiClient());
    }

    public WebsearchApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * List web search providers
     * Return the registered web search providers.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param location The location parameter
     * @return WebsearchProviders200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec websearchProvidersRequestCreation(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
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

        ParameterizedTypeReference<WebsearchProviders200Response> localVarReturnType = new ParameterizedTypeReference<WebsearchProviders200Response>() {};
        return apiClient.invokeAPI("/api/websearch/provider", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List web search providers
     * Return the registered web search providers.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param location The location parameter
     * @return WebsearchProviders200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<WebsearchProviders200Response> websearchProviders(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<WebsearchProviders200Response> localVarReturnType = new ParameterizedTypeReference<WebsearchProviders200Response>() {};
        return websearchProvidersRequestCreation(location).bodyToMono(localVarReturnType);
    }

    /**
     * List web search providers
     * Return the registered web search providers.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param location The location parameter
     * @return ResponseEntity&lt;WebsearchProviders200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<WebsearchProviders200Response>> websearchProvidersWithHttpInfo(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<WebsearchProviders200Response> localVarReturnType = new ParameterizedTypeReference<WebsearchProviders200Response>() {};
        return websearchProvidersRequestCreation(location).toEntity(localVarReturnType);
    }

    /**
     * List web search providers
     * Return the registered web search providers.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec websearchProvidersWithResponseSpec(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return websearchProvidersRequestCreation(location);
    }

    public class WebsearchQueryRequest {
        private @jakarta.annotation.Nonnull WebsearchQueryRequest websearchQueryRequest;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;

        public WebsearchQueryRequest() {}

        public WebsearchQueryRequest(@jakarta.annotation.Nonnull WebsearchQueryRequest websearchQueryRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.websearchQueryRequest = websearchQueryRequest;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull WebsearchQueryRequest websearchQueryRequest() {
            return this.websearchQueryRequest;
        }
        public WebsearchQueryRequest websearchQueryRequest(@jakarta.annotation.Nonnull WebsearchQueryRequest websearchQueryRequest) {
            this.websearchQueryRequest = websearchQueryRequest;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public WebsearchQueryRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
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
            WebsearchQueryRequest request = (WebsearchQueryRequest) o;
            return Objects.equals(this.websearchQueryRequest, request.websearchQueryRequest()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(websearchQueryRequest, location);
        }
    }

    /**
     * Search the web
     * Run one web search through the selected provider. Specify a provider to override the configured default.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The websearchQuery request parameters as object
     * @return WebsearchQuery200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<WebsearchQuery200Response> websearchQuery(WebsearchQueryRequest requestParameters) throws WebClientResponseException {
        return this.websearchQuery(requestParameters.websearchQueryRequest(), requestParameters.location());
    }

    /**
     * Search the web
     * Run one web search through the selected provider. Specify a provider to override the configured default.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The websearchQuery request parameters as object
     * @return ResponseEntity&lt;WebsearchQuery200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<WebsearchQuery200Response>> websearchQueryWithHttpInfo(WebsearchQueryRequest requestParameters) throws WebClientResponseException {
        return this.websearchQueryWithHttpInfo(requestParameters.websearchQueryRequest(), requestParameters.location());
    }

    /**
     * Search the web
     * Run one web search through the selected provider. Specify a provider to override the configured default.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The websearchQuery request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec websearchQueryWithResponseSpec(WebsearchQueryRequest requestParameters) throws WebClientResponseException {
        return this.websearchQueryWithResponseSpec(requestParameters.websearchQueryRequest(), requestParameters.location());
    }


    /**
     * Search the web
     * Run one web search through the selected provider. Specify a provider to override the configured default.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param websearchQueryRequest The websearchQueryRequest parameter
     * @param location The location parameter
     * @return WebsearchQuery200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec websearchQueryRequestCreation(@jakarta.annotation.Nonnull WebsearchQueryRequest websearchQueryRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = websearchQueryRequest;
        // verify the required parameter 'websearchQueryRequest' is set
        if (websearchQueryRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'websearchQueryRequest' when calling websearchQuery", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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

        ParameterizedTypeReference<WebsearchQuery200Response> localVarReturnType = new ParameterizedTypeReference<WebsearchQuery200Response>() {};
        return apiClient.invokeAPI("/api/websearch", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Search the web
     * Run one web search through the selected provider. Specify a provider to override the configured default.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param websearchQueryRequest The websearchQueryRequest parameter
     * @param location The location parameter
     * @return WebsearchQuery200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<WebsearchQuery200Response> websearchQuery(@jakarta.annotation.Nonnull WebsearchQueryRequest websearchQueryRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<WebsearchQuery200Response> localVarReturnType = new ParameterizedTypeReference<WebsearchQuery200Response>() {};
        return websearchQueryRequestCreation(websearchQueryRequest, location).bodyToMono(localVarReturnType);
    }

    /**
     * Search the web
     * Run one web search through the selected provider. Specify a provider to override the configured default.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param websearchQueryRequest The websearchQueryRequest parameter
     * @param location The location parameter
     * @return ResponseEntity&lt;WebsearchQuery200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<WebsearchQuery200Response>> websearchQueryWithHttpInfo(@jakarta.annotation.Nonnull WebsearchQueryRequest websearchQueryRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<WebsearchQuery200Response> localVarReturnType = new ParameterizedTypeReference<WebsearchQuery200Response>() {};
        return websearchQueryRequestCreation(websearchQueryRequest, location).toEntity(localVarReturnType);
    }

    /**
     * Search the web
     * Run one web search through the selected provider. Specify a provider to override the configured default.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param websearchQueryRequest The websearchQueryRequest parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec websearchQueryWithResponseSpec(@jakarta.annotation.Nonnull WebsearchQueryRequest websearchQueryRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return websearchQueryRequestCreation(websearchQueryRequest, location);
    }
}
