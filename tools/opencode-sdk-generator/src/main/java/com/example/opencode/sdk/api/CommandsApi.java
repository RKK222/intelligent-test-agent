package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.InvalidRequestError;
import com.example.opencode.sdk.model.UnauthorizedError;
import com.example.opencode.sdk.model.V2AgentListLocationParameter;
import com.example.opencode.sdk.model.V2CommandList200Response;

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
public class CommandsApi {
    private ApiClient apiClient;

    public CommandsApi() {
        this(new ApiClient());
    }

    public CommandsApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * List commands
     * Retrieve currently registered commands.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return V2CommandList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2CommandListRequestCreation(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
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

        ParameterizedTypeReference<V2CommandList200Response> localVarReturnType = new ParameterizedTypeReference<V2CommandList200Response>() {};
        return apiClient.invokeAPI("/api/command", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List commands
     * Retrieve currently registered commands.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return V2CommandList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2CommandList200Response> v2CommandList(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2CommandList200Response> localVarReturnType = new ParameterizedTypeReference<V2CommandList200Response>() {};
        return v2CommandListRequestCreation(location).bodyToMono(localVarReturnType);
    }

    /**
     * List commands
     * Retrieve currently registered commands.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseEntity&lt;V2CommandList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2CommandList200Response>> v2CommandListWithHttpInfo(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2CommandList200Response> localVarReturnType = new ParameterizedTypeReference<V2CommandList200Response>() {};
        return v2CommandListRequestCreation(location).toEntity(localVarReturnType);
    }

    /**
     * List commands
     * Retrieve currently registered commands.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2CommandListWithResponseSpec(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        return v2CommandListRequestCreation(location);
    }
}
