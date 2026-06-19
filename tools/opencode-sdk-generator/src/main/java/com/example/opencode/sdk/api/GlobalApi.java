package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.AuthSet400Response;
import com.example.opencode.sdk.model.BadRequestError;
import com.example.opencode.sdk.model.Config;
import com.example.opencode.sdk.model.GlobalEvent;
import com.example.opencode.sdk.model.GlobalHealth200Response;
import com.example.opencode.sdk.model.GlobalUpgrade200Response;
import com.example.opencode.sdk.model.GlobalUpgradeRequest;

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
public class GlobalApi {
    private ApiClient apiClient;

    public GlobalApi() {
        this(new ApiClient());
    }

    public GlobalApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Get global configuration
     * Retrieve the current global OpenCode configuration settings and preferences.
     * <p><b>200</b> - Get global config info
     * <p><b>400</b> - Bad request
     * @return Config
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec globalConfigGetRequestCreation() throws WebClientResponseException {
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

        ParameterizedTypeReference<Config> localVarReturnType = new ParameterizedTypeReference<Config>() {};
        return apiClient.invokeAPI("/global/config", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get global configuration
     * Retrieve the current global OpenCode configuration settings and preferences.
     * <p><b>200</b> - Get global config info
     * <p><b>400</b> - Bad request
     * @return Config
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Config> globalConfigGet() throws WebClientResponseException {
        ParameterizedTypeReference<Config> localVarReturnType = new ParameterizedTypeReference<Config>() {};
        return globalConfigGetRequestCreation().bodyToMono(localVarReturnType);
    }

    /**
     * Get global configuration
     * Retrieve the current global OpenCode configuration settings and preferences.
     * <p><b>200</b> - Get global config info
     * <p><b>400</b> - Bad request
     * @return ResponseEntity&lt;Config&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Config>> globalConfigGetWithHttpInfo() throws WebClientResponseException {
        ParameterizedTypeReference<Config> localVarReturnType = new ParameterizedTypeReference<Config>() {};
        return globalConfigGetRequestCreation().toEntity(localVarReturnType);
    }

    /**
     * Get global configuration
     * Retrieve the current global OpenCode configuration settings and preferences.
     * <p><b>200</b> - Get global config info
     * <p><b>400</b> - Bad request
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec globalConfigGetWithResponseSpec() throws WebClientResponseException {
        return globalConfigGetRequestCreation();
    }

    /**
     * Update global configuration
     * Update global OpenCode configuration settings and preferences.
     * <p><b>200</b> - Successfully updated global config
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param config The config parameter
     * @return Config
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec globalConfigUpdateRequestCreation(@jakarta.annotation.Nullable Config config) throws WebClientResponseException {
        Object postBody = config;
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
        final String[] localVarContentTypes = { 
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Config> localVarReturnType = new ParameterizedTypeReference<Config>() {};
        return apiClient.invokeAPI("/global/config", HttpMethod.PATCH, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Update global configuration
     * Update global OpenCode configuration settings and preferences.
     * <p><b>200</b> - Successfully updated global config
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param config The config parameter
     * @return Config
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Config> globalConfigUpdate(@jakarta.annotation.Nullable Config config) throws WebClientResponseException {
        ParameterizedTypeReference<Config> localVarReturnType = new ParameterizedTypeReference<Config>() {};
        return globalConfigUpdateRequestCreation(config).bodyToMono(localVarReturnType);
    }

    /**
     * Update global configuration
     * Update global OpenCode configuration settings and preferences.
     * <p><b>200</b> - Successfully updated global config
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param config The config parameter
     * @return ResponseEntity&lt;Config&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Config>> globalConfigUpdateWithHttpInfo(@jakarta.annotation.Nullable Config config) throws WebClientResponseException {
        ParameterizedTypeReference<Config> localVarReturnType = new ParameterizedTypeReference<Config>() {};
        return globalConfigUpdateRequestCreation(config).toEntity(localVarReturnType);
    }

    /**
     * Update global configuration
     * Update global OpenCode configuration settings and preferences.
     * <p><b>200</b> - Successfully updated global config
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param config The config parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec globalConfigUpdateWithResponseSpec(@jakarta.annotation.Nullable Config config) throws WebClientResponseException {
        return globalConfigUpdateRequestCreation(config);
    }

    /**
     * Dispose instance
     * Clean up and dispose all OpenCode instances, releasing all resources.
     * <p><b>200</b> - Global disposed
     * <p><b>400</b> - Bad request
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec globalDisposeRequestCreation() throws WebClientResponseException {
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

        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return apiClient.invokeAPI("/global/dispose", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Dispose instance
     * Clean up and dispose all OpenCode instances, releasing all resources.
     * <p><b>200</b> - Global disposed
     * <p><b>400</b> - Bad request
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> globalDispose() throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return globalDisposeRequestCreation().bodyToMono(localVarReturnType);
    }

    /**
     * Dispose instance
     * Clean up and dispose all OpenCode instances, releasing all resources.
     * <p><b>200</b> - Global disposed
     * <p><b>400</b> - Bad request
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> globalDisposeWithHttpInfo() throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return globalDisposeRequestCreation().toEntity(localVarReturnType);
    }

    /**
     * Dispose instance
     * Clean up and dispose all OpenCode instances, releasing all resources.
     * <p><b>200</b> - Global disposed
     * <p><b>400</b> - Bad request
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec globalDisposeWithResponseSpec() throws WebClientResponseException {
        return globalDisposeRequestCreation();
    }

    /**
     * Get global events
     * Subscribe to global events from the OpenCode system using server-sent events.
     * <p><b>200</b> - Event stream
     * <p><b>400</b> - Bad request
     * @return GlobalEvent
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec globalEventRequestCreation() throws WebClientResponseException {
        Object postBody = null;
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = { 
            "text/event-stream", "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<GlobalEvent> localVarReturnType = new ParameterizedTypeReference<GlobalEvent>() {};
        return apiClient.invokeAPI("/global/event", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get global events
     * Subscribe to global events from the OpenCode system using server-sent events.
     * <p><b>200</b> - Event stream
     * <p><b>400</b> - Bad request
     * @return GlobalEvent
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<GlobalEvent> globalEvent() throws WebClientResponseException {
        ParameterizedTypeReference<GlobalEvent> localVarReturnType = new ParameterizedTypeReference<GlobalEvent>() {};
        return globalEventRequestCreation().bodyToMono(localVarReturnType);
    }

    /**
     * Get global events
     * Subscribe to global events from the OpenCode system using server-sent events.
     * <p><b>200</b> - Event stream
     * <p><b>400</b> - Bad request
     * @return ResponseEntity&lt;GlobalEvent&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<GlobalEvent>> globalEventWithHttpInfo() throws WebClientResponseException {
        ParameterizedTypeReference<GlobalEvent> localVarReturnType = new ParameterizedTypeReference<GlobalEvent>() {};
        return globalEventRequestCreation().toEntity(localVarReturnType);
    }

    /**
     * Get global events
     * Subscribe to global events from the OpenCode system using server-sent events.
     * <p><b>200</b> - Event stream
     * <p><b>400</b> - Bad request
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec globalEventWithResponseSpec() throws WebClientResponseException {
        return globalEventRequestCreation();
    }

    /**
     * Get health
     * Get health information about the OpenCode server.
     * <p><b>200</b> - Health information
     * <p><b>400</b> - Bad request
     * @return GlobalHealth200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec globalHealthRequestCreation() throws WebClientResponseException {
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

        ParameterizedTypeReference<GlobalHealth200Response> localVarReturnType = new ParameterizedTypeReference<GlobalHealth200Response>() {};
        return apiClient.invokeAPI("/global/health", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get health
     * Get health information about the OpenCode server.
     * <p><b>200</b> - Health information
     * <p><b>400</b> - Bad request
     * @return GlobalHealth200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<GlobalHealth200Response> globalHealth() throws WebClientResponseException {
        ParameterizedTypeReference<GlobalHealth200Response> localVarReturnType = new ParameterizedTypeReference<GlobalHealth200Response>() {};
        return globalHealthRequestCreation().bodyToMono(localVarReturnType);
    }

    /**
     * Get health
     * Get health information about the OpenCode server.
     * <p><b>200</b> - Health information
     * <p><b>400</b> - Bad request
     * @return ResponseEntity&lt;GlobalHealth200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<GlobalHealth200Response>> globalHealthWithHttpInfo() throws WebClientResponseException {
        ParameterizedTypeReference<GlobalHealth200Response> localVarReturnType = new ParameterizedTypeReference<GlobalHealth200Response>() {};
        return globalHealthRequestCreation().toEntity(localVarReturnType);
    }

    /**
     * Get health
     * Get health information about the OpenCode server.
     * <p><b>200</b> - Health information
     * <p><b>400</b> - Bad request
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec globalHealthWithResponseSpec() throws WebClientResponseException {
        return globalHealthRequestCreation();
    }

    /**
     * Upgrade opencode
     * Upgrade opencode to the specified version or latest if not specified.
     * <p><b>200</b> - Upgrade result
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param globalUpgradeRequest The globalUpgradeRequest parameter
     * @return GlobalUpgrade200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec globalUpgradeRequestCreation(@jakarta.annotation.Nullable GlobalUpgradeRequest globalUpgradeRequest) throws WebClientResponseException {
        Object postBody = globalUpgradeRequest;
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
        final String[] localVarContentTypes = { 
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<GlobalUpgrade200Response> localVarReturnType = new ParameterizedTypeReference<GlobalUpgrade200Response>() {};
        return apiClient.invokeAPI("/global/upgrade", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Upgrade opencode
     * Upgrade opencode to the specified version or latest if not specified.
     * <p><b>200</b> - Upgrade result
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param globalUpgradeRequest The globalUpgradeRequest parameter
     * @return GlobalUpgrade200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<GlobalUpgrade200Response> globalUpgrade(@jakarta.annotation.Nullable GlobalUpgradeRequest globalUpgradeRequest) throws WebClientResponseException {
        ParameterizedTypeReference<GlobalUpgrade200Response> localVarReturnType = new ParameterizedTypeReference<GlobalUpgrade200Response>() {};
        return globalUpgradeRequestCreation(globalUpgradeRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Upgrade opencode
     * Upgrade opencode to the specified version or latest if not specified.
     * <p><b>200</b> - Upgrade result
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param globalUpgradeRequest The globalUpgradeRequest parameter
     * @return ResponseEntity&lt;GlobalUpgrade200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<GlobalUpgrade200Response>> globalUpgradeWithHttpInfo(@jakarta.annotation.Nullable GlobalUpgradeRequest globalUpgradeRequest) throws WebClientResponseException {
        ParameterizedTypeReference<GlobalUpgrade200Response> localVarReturnType = new ParameterizedTypeReference<GlobalUpgrade200Response>() {};
        return globalUpgradeRequestCreation(globalUpgradeRequest).toEntity(localVarReturnType);
    }

    /**
     * Upgrade opencode
     * Upgrade opencode to the specified version or latest if not specified.
     * <p><b>200</b> - Upgrade result
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param globalUpgradeRequest The globalUpgradeRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec globalUpgradeWithResponseSpec(@jakarta.annotation.Nullable GlobalUpgradeRequest globalUpgradeRequest) throws WebClientResponseException {
        return globalUpgradeRequestCreation(globalUpgradeRequest);
    }
}
