package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.AuthSet400Response;
import com.example.opencode.sdk.model.BadRequestError;
import com.example.opencode.sdk.model.Config;
import com.example.opencode.sdk.model.ConfigProviders200Response;

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
public class ConfigApi {
    private ApiClient apiClient;

    public ConfigApi() {
        this(new ApiClient());
    }

    public ConfigApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public class ConfigGetRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public ConfigGetRequest() {}

        public ConfigGetRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public ConfigGetRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public ConfigGetRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
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
            ConfigGetRequest request = (ConfigGetRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * Get configuration
     * Retrieve the current OpenCode configuration settings and preferences.
     * <p><b>200</b> - Get config info
     * <p><b>400</b> - Bad request
     * @param requestParameters The configGet request parameters as object
     * @return Config
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Config> configGet(ConfigGetRequest requestParameters) throws WebClientResponseException {
        return this.configGet(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get configuration
     * Retrieve the current OpenCode configuration settings and preferences.
     * <p><b>200</b> - Get config info
     * <p><b>400</b> - Bad request
     * @param requestParameters The configGet request parameters as object
     * @return ResponseEntity&lt;Config&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Config>> configGetWithHttpInfo(ConfigGetRequest requestParameters) throws WebClientResponseException {
        return this.configGetWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get configuration
     * Retrieve the current OpenCode configuration settings and preferences.
     * <p><b>200</b> - Get config info
     * <p><b>400</b> - Bad request
     * @param requestParameters The configGet request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec configGetWithResponseSpec(ConfigGetRequest requestParameters) throws WebClientResponseException {
        return this.configGetWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Get configuration
     * Retrieve the current OpenCode configuration settings and preferences.
     * <p><b>200</b> - Get config info
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Config
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec configGetRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", directory));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", workspace));

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Config> localVarReturnType = new ParameterizedTypeReference<Config>() {};
        return apiClient.invokeAPI("/config", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get configuration
     * Retrieve the current OpenCode configuration settings and preferences.
     * <p><b>200</b> - Get config info
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Config
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Config> configGet(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Config> localVarReturnType = new ParameterizedTypeReference<Config>() {};
        return configGetRequestCreation(directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Get configuration
     * Retrieve the current OpenCode configuration settings and preferences.
     * <p><b>200</b> - Get config info
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Config&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Config>> configGetWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Config> localVarReturnType = new ParameterizedTypeReference<Config>() {};
        return configGetRequestCreation(directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Get configuration
     * Retrieve the current OpenCode configuration settings and preferences.
     * <p><b>200</b> - Get config info
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec configGetWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return configGetRequestCreation(directory, workspace);
    }

    public class ConfigProvidersRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public ConfigProvidersRequest() {}

        public ConfigProvidersRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public ConfigProvidersRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public ConfigProvidersRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
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
            ConfigProvidersRequest request = (ConfigProvidersRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * List config providers
     * Get a list of all configured AI providers and their default models.
     * <p><b>200</b> - List of providers
     * <p><b>400</b> - Bad request
     * @param requestParameters The configProviders request parameters as object
     * @return ConfigProviders200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ConfigProviders200Response> configProviders(ConfigProvidersRequest requestParameters) throws WebClientResponseException {
        return this.configProviders(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List config providers
     * Get a list of all configured AI providers and their default models.
     * <p><b>200</b> - List of providers
     * <p><b>400</b> - Bad request
     * @param requestParameters The configProviders request parameters as object
     * @return ResponseEntity&lt;ConfigProviders200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ConfigProviders200Response>> configProvidersWithHttpInfo(ConfigProvidersRequest requestParameters) throws WebClientResponseException {
        return this.configProvidersWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List config providers
     * Get a list of all configured AI providers and their default models.
     * <p><b>200</b> - List of providers
     * <p><b>400</b> - Bad request
     * @param requestParameters The configProviders request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec configProvidersWithResponseSpec(ConfigProvidersRequest requestParameters) throws WebClientResponseException {
        return this.configProvidersWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * List config providers
     * Get a list of all configured AI providers and their default models.
     * <p><b>200</b> - List of providers
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ConfigProviders200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec configProvidersRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", directory));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", workspace));

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<ConfigProviders200Response> localVarReturnType = new ParameterizedTypeReference<ConfigProviders200Response>() {};
        return apiClient.invokeAPI("/config/providers", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List config providers
     * Get a list of all configured AI providers and their default models.
     * <p><b>200</b> - List of providers
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ConfigProviders200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ConfigProviders200Response> configProviders(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<ConfigProviders200Response> localVarReturnType = new ParameterizedTypeReference<ConfigProviders200Response>() {};
        return configProvidersRequestCreation(directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * List config providers
     * Get a list of all configured AI providers and their default models.
     * <p><b>200</b> - List of providers
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;ConfigProviders200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ConfigProviders200Response>> configProvidersWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<ConfigProviders200Response> localVarReturnType = new ParameterizedTypeReference<ConfigProviders200Response>() {};
        return configProvidersRequestCreation(directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * List config providers
     * Get a list of all configured AI providers and their default models.
     * <p><b>200</b> - List of providers
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec configProvidersWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return configProvidersRequestCreation(directory, workspace);
    }

    public class ConfigUpdateRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable Config config;

        public ConfigUpdateRequest() {}

        public ConfigUpdateRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Config config) {
            this.directory = directory;
            this.workspace = workspace;
            this.config = config;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public ConfigUpdateRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public ConfigUpdateRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable Config config() {
            return this.config;
        }
        public ConfigUpdateRequest config(@jakarta.annotation.Nullable Config config) {
            this.config = config;
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
            ConfigUpdateRequest request = (ConfigUpdateRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.config, request.config());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace, config);
        }
    }

    /**
     * Update configuration
     * Update OpenCode configuration settings and preferences.
     * <p><b>200</b> - Successfully updated config
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The configUpdate request parameters as object
     * @return Config
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Config> configUpdate(ConfigUpdateRequest requestParameters) throws WebClientResponseException {
        return this.configUpdate(requestParameters.directory(), requestParameters.workspace(), requestParameters.config());
    }

    /**
     * Update configuration
     * Update OpenCode configuration settings and preferences.
     * <p><b>200</b> - Successfully updated config
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The configUpdate request parameters as object
     * @return ResponseEntity&lt;Config&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Config>> configUpdateWithHttpInfo(ConfigUpdateRequest requestParameters) throws WebClientResponseException {
        return this.configUpdateWithHttpInfo(requestParameters.directory(), requestParameters.workspace(), requestParameters.config());
    }

    /**
     * Update configuration
     * Update OpenCode configuration settings and preferences.
     * <p><b>200</b> - Successfully updated config
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The configUpdate request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec configUpdateWithResponseSpec(ConfigUpdateRequest requestParameters) throws WebClientResponseException {
        return this.configUpdateWithResponseSpec(requestParameters.directory(), requestParameters.workspace(), requestParameters.config());
    }


    /**
     * Update configuration
     * Update OpenCode configuration settings and preferences.
     * <p><b>200</b> - Successfully updated config
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param config The config parameter
     * @return Config
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec configUpdateRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Config config) throws WebClientResponseException {
        Object postBody = config;
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", directory));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", workspace));

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { 
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Config> localVarReturnType = new ParameterizedTypeReference<Config>() {};
        return apiClient.invokeAPI("/config", HttpMethod.PATCH, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Update configuration
     * Update OpenCode configuration settings and preferences.
     * <p><b>200</b> - Successfully updated config
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param config The config parameter
     * @return Config
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Config> configUpdate(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Config config) throws WebClientResponseException {
        ParameterizedTypeReference<Config> localVarReturnType = new ParameterizedTypeReference<Config>() {};
        return configUpdateRequestCreation(directory, workspace, config).bodyToMono(localVarReturnType);
    }

    /**
     * Update configuration
     * Update OpenCode configuration settings and preferences.
     * <p><b>200</b> - Successfully updated config
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param config The config parameter
     * @return ResponseEntity&lt;Config&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Config>> configUpdateWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Config config) throws WebClientResponseException {
        ParameterizedTypeReference<Config> localVarReturnType = new ParameterizedTypeReference<Config>() {};
        return configUpdateRequestCreation(directory, workspace, config).toEntity(localVarReturnType);
    }

    /**
     * Update configuration
     * Update OpenCode configuration settings and preferences.
     * <p><b>200</b> - Successfully updated config
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param config The config parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec configUpdateWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Config config) throws WebClientResponseException {
        return configUpdateRequestCreation(directory, workspace, config);
    }
}
