package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.InvalidRequestError;
import com.example.opencode.sdk.model.ServiceUnavailableError;
import com.example.opencode.sdk.model.UnauthorizedError;
import com.example.opencode.sdk.model.V2AgentListLocationParameter;
import com.example.opencode.sdk.model.V2ModelList200Response;

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
public class ModelsApi {
    private ApiClient apiClient;

    public ModelsApi() {
        this(new ApiClient());
    }

    public ModelsApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * List models
     * Retrieve available models ordered by release date.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param location The location parameter
     * @return V2ModelList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2ModelListRequestCreation(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
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

        ParameterizedTypeReference<V2ModelList200Response> localVarReturnType = new ParameterizedTypeReference<V2ModelList200Response>() {};
        return apiClient.invokeAPI("/api/model", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List models
     * Retrieve available models ordered by release date.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param location The location parameter
     * @return V2ModelList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2ModelList200Response> v2ModelList(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2ModelList200Response> localVarReturnType = new ParameterizedTypeReference<V2ModelList200Response>() {};
        return v2ModelListRequestCreation(location).bodyToMono(localVarReturnType);
    }

    /**
     * List models
     * Retrieve available models ordered by release date.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param location The location parameter
     * @return ResponseEntity&lt;V2ModelList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2ModelList200Response>> v2ModelListWithHttpInfo(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2ModelList200Response> localVarReturnType = new ParameterizedTypeReference<V2ModelList200Response>() {};
        return v2ModelListRequestCreation(location).toEntity(localVarReturnType);
    }

    /**
     * List models
     * Retrieve available models ordered by release date.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2ModelListWithResponseSpec(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        return v2ModelListRequestCreation(location);
    }
}
