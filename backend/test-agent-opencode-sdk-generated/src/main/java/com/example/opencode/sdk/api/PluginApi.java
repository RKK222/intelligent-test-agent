package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.AgentListLocationParameter;
import com.example.opencode.sdk.model.InvalidRequestErrorEncoded;
import com.example.opencode.sdk.model.PluginCheck200Response;
import com.example.opencode.sdk.model.PluginCheck400Response;
import com.example.opencode.sdk.model.PluginCheckRequest;
import com.example.opencode.sdk.model.PluginList200Response;
import com.example.opencode.sdk.model.PluginUpdateRequest;
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
public class PluginApi {
    private ApiClient apiClient;

    public PluginApi() {
        this(new ApiClient());
    }

    public PluginApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public class PluginCheckRequest {
        private @jakarta.annotation.Nonnull PluginCheckRequest pluginCheckRequest;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;

        public PluginCheckRequest() {}

        public PluginCheckRequest(@jakarta.annotation.Nonnull PluginCheckRequest pluginCheckRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.pluginCheckRequest = pluginCheckRequest;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull PluginCheckRequest pluginCheckRequest() {
            return this.pluginCheckRequest;
        }
        public PluginCheckRequest pluginCheckRequest(@jakarta.annotation.Nonnull PluginCheckRequest pluginCheckRequest) {
            this.pluginCheckRequest = pluginCheckRequest;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public PluginCheckRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
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
            PluginCheckRequest request = (PluginCheckRequest) o;
            return Objects.equals(this.pluginCheckRequest, request.pluginCheckRequest()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(pluginCheckRequest, location);
        }
    }

    /**
     * Check plugin updates
     * Check one or all package plugins for available updates.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The pluginCheck request parameters as object
     * @return PluginCheck200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<PluginCheck200Response> pluginCheck(PluginCheckRequest requestParameters) throws WebClientResponseException {
        return this.pluginCheck(requestParameters.pluginCheckRequest(), requestParameters.location());
    }

    /**
     * Check plugin updates
     * Check one or all package plugins for available updates.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The pluginCheck request parameters as object
     * @return ResponseEntity&lt;PluginCheck200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<PluginCheck200Response>> pluginCheckWithHttpInfo(PluginCheckRequest requestParameters) throws WebClientResponseException {
        return this.pluginCheckWithHttpInfo(requestParameters.pluginCheckRequest(), requestParameters.location());
    }

    /**
     * Check plugin updates
     * Check one or all package plugins for available updates.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The pluginCheck request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec pluginCheckWithResponseSpec(PluginCheckRequest requestParameters) throws WebClientResponseException {
        return this.pluginCheckWithResponseSpec(requestParameters.pluginCheckRequest(), requestParameters.location());
    }


    /**
     * Check plugin updates
     * Check one or all package plugins for available updates.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param pluginCheckRequest The pluginCheckRequest parameter
     * @param location The location parameter
     * @return PluginCheck200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec pluginCheckRequestCreation(@jakarta.annotation.Nonnull PluginCheckRequest pluginCheckRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = pluginCheckRequest;
        // verify the required parameter 'pluginCheckRequest' is set
        if (pluginCheckRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'pluginCheckRequest' when calling pluginCheck", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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

        ParameterizedTypeReference<PluginCheck200Response> localVarReturnType = new ParameterizedTypeReference<PluginCheck200Response>() {};
        return apiClient.invokeAPI("/api/plugin/check", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Check plugin updates
     * Check one or all package plugins for available updates.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param pluginCheckRequest The pluginCheckRequest parameter
     * @param location The location parameter
     * @return PluginCheck200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<PluginCheck200Response> pluginCheck(@jakarta.annotation.Nonnull PluginCheckRequest pluginCheckRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<PluginCheck200Response> localVarReturnType = new ParameterizedTypeReference<PluginCheck200Response>() {};
        return pluginCheckRequestCreation(pluginCheckRequest, location).bodyToMono(localVarReturnType);
    }

    /**
     * Check plugin updates
     * Check one or all package plugins for available updates.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param pluginCheckRequest The pluginCheckRequest parameter
     * @param location The location parameter
     * @return ResponseEntity&lt;PluginCheck200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<PluginCheck200Response>> pluginCheckWithHttpInfo(@jakarta.annotation.Nonnull PluginCheckRequest pluginCheckRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<PluginCheck200Response> localVarReturnType = new ParameterizedTypeReference<PluginCheck200Response>() {};
        return pluginCheckRequestCreation(pluginCheckRequest, location).toEntity(localVarReturnType);
    }

    /**
     * Check plugin updates
     * Check one or all package plugins for available updates.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param pluginCheckRequest The pluginCheckRequest parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec pluginCheckWithResponseSpec(@jakarta.annotation.Nonnull PluginCheckRequest pluginCheckRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return pluginCheckRequestCreation(pluginCheckRequest, location);
    }

    /**
     * List plugins
     * Retrieve enabled server plugins and their current status.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return PluginList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec pluginListRequestCreation(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
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

        ParameterizedTypeReference<PluginList200Response> localVarReturnType = new ParameterizedTypeReference<PluginList200Response>() {};
        return apiClient.invokeAPI("/api/plugin", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List plugins
     * Retrieve enabled server plugins and their current status.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return PluginList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<PluginList200Response> pluginList(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<PluginList200Response> localVarReturnType = new ParameterizedTypeReference<PluginList200Response>() {};
        return pluginListRequestCreation(location).bodyToMono(localVarReturnType);
    }

    /**
     * List plugins
     * Retrieve enabled server plugins and their current status.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseEntity&lt;PluginList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<PluginList200Response>> pluginListWithHttpInfo(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<PluginList200Response> localVarReturnType = new ParameterizedTypeReference<PluginList200Response>() {};
        return pluginListRequestCreation(location).toEntity(localVarReturnType);
    }

    /**
     * List plugins
     * Retrieve enabled server plugins and their current status.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec pluginListWithResponseSpec(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return pluginListRequestCreation(location);
    }

    public class PluginUpdateRequest {
        private @jakarta.annotation.Nonnull PluginUpdateRequest pluginUpdateRequest;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;

        public PluginUpdateRequest() {}

        public PluginUpdateRequest(@jakarta.annotation.Nonnull PluginUpdateRequest pluginUpdateRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.pluginUpdateRequest = pluginUpdateRequest;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull PluginUpdateRequest pluginUpdateRequest() {
            return this.pluginUpdateRequest;
        }
        public PluginUpdateRequest pluginUpdateRequest(@jakarta.annotation.Nonnull PluginUpdateRequest pluginUpdateRequest) {
            this.pluginUpdateRequest = pluginUpdateRequest;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public PluginUpdateRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
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
            PluginUpdateRequest request = (PluginUpdateRequest) o;
            return Objects.equals(this.pluginUpdateRequest, request.pluginUpdateRequest()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(pluginUpdateRequest, location);
        }
    }

    /**
     * Update plugins
     * Update package plugins concurrently and notify active locations to reload them. Responds once every update has finished; fails when any update fails.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The pluginUpdate request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> pluginUpdate(PluginUpdateRequest requestParameters) throws WebClientResponseException {
        return this.pluginUpdate(requestParameters.pluginUpdateRequest(), requestParameters.location());
    }

    /**
     * Update plugins
     * Update package plugins concurrently and notify active locations to reload them. Responds once every update has finished; fails when any update fails.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The pluginUpdate request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> pluginUpdateWithHttpInfo(PluginUpdateRequest requestParameters) throws WebClientResponseException {
        return this.pluginUpdateWithHttpInfo(requestParameters.pluginUpdateRequest(), requestParameters.location());
    }

    /**
     * Update plugins
     * Update package plugins concurrently and notify active locations to reload them. Responds once every update has finished; fails when any update fails.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The pluginUpdate request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec pluginUpdateWithResponseSpec(PluginUpdateRequest requestParameters) throws WebClientResponseException {
        return this.pluginUpdateWithResponseSpec(requestParameters.pluginUpdateRequest(), requestParameters.location());
    }


    /**
     * Update plugins
     * Update package plugins concurrently and notify active locations to reload them. Responds once every update has finished; fails when any update fails.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param pluginUpdateRequest The pluginUpdateRequest parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec pluginUpdateRequestCreation(@jakarta.annotation.Nonnull PluginUpdateRequest pluginUpdateRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = pluginUpdateRequest;
        // verify the required parameter 'pluginUpdateRequest' is set
        if (pluginUpdateRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'pluginUpdateRequest' when calling pluginUpdate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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
        return apiClient.invokeAPI("/api/plugin/update", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Update plugins
     * Update package plugins concurrently and notify active locations to reload them. Responds once every update has finished; fails when any update fails.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param pluginUpdateRequest The pluginUpdateRequest parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> pluginUpdate(@jakarta.annotation.Nonnull PluginUpdateRequest pluginUpdateRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return pluginUpdateRequestCreation(pluginUpdateRequest, location).bodyToMono(localVarReturnType);
    }

    /**
     * Update plugins
     * Update package plugins concurrently and notify active locations to reload them. Responds once every update has finished; fails when any update fails.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param pluginUpdateRequest The pluginUpdateRequest parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> pluginUpdateWithHttpInfo(@jakarta.annotation.Nonnull PluginUpdateRequest pluginUpdateRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return pluginUpdateRequestCreation(pluginUpdateRequest, location).toEntity(localVarReturnType);
    }

    /**
     * Update plugins
     * Update package plugins concurrently and notify active locations to reload them. Responds once every update has finished; fails when any update fails.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param pluginUpdateRequest The pluginUpdateRequest parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec pluginUpdateWithResponseSpec(@jakarta.annotation.Nonnull PluginUpdateRequest pluginUpdateRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return pluginUpdateRequestCreation(pluginUpdateRequest, location);
    }
}
