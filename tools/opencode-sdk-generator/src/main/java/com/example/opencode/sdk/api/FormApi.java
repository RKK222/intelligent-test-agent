package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.AgentListLocationParameter;
import com.example.opencode.sdk.model.FormList200Response;
import com.example.opencode.sdk.model.InvalidRequestErrorEncoded;
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
public class FormApi {
    private ApiClient apiClient;

    public FormApi() {
        this(new ApiClient());
    }

    public FormApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * List pending forms
     * Retrieve pending forms for a location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return FormList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec formListRequestCreation(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
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

        ParameterizedTypeReference<FormList200Response> localVarReturnType = new ParameterizedTypeReference<FormList200Response>() {};
        return apiClient.invokeAPI("/api/form", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List pending forms
     * Retrieve pending forms for a location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return FormList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<FormList200Response> formList(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<FormList200Response> localVarReturnType = new ParameterizedTypeReference<FormList200Response>() {};
        return formListRequestCreation(location).bodyToMono(localVarReturnType);
    }

    /**
     * List pending forms
     * Retrieve pending forms for a location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseEntity&lt;FormList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<FormList200Response>> formListWithHttpInfo(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<FormList200Response> localVarReturnType = new ParameterizedTypeReference<FormList200Response>() {};
        return formListRequestCreation(location).toEntity(localVarReturnType);
    }

    /**
     * List pending forms
     * Retrieve pending forms for a location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec formListWithResponseSpec(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return formListRequestCreation(location);
    }
}
