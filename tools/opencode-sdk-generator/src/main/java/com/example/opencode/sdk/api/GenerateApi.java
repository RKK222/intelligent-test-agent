package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.ExperimentalGenerateTextRequest;
import com.example.opencode.sdk.model.GenerateTextResponse;
import com.example.opencode.sdk.model.PluginCheck400Response;
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
public class GenerateApi {
    private ApiClient apiClient;

    public GenerateApi() {
        this(new ApiClient());
    }

    public GenerateApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Generate text
     * Run one stateless model generation using the server&#39;s base configuration and return the assistant text. Uses the base configuration&#39;s default model when none is specified.
     * <p><b>200</b> - GenerateTextResponse
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param experimentalGenerateTextRequest The experimentalGenerateTextRequest parameter
     * @return GenerateTextResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalGenerateTextRequestCreation(@jakarta.annotation.Nonnull ExperimentalGenerateTextRequest experimentalGenerateTextRequest) throws WebClientResponseException {
        Object postBody = experimentalGenerateTextRequest;
        // verify the required parameter 'experimentalGenerateTextRequest' is set
        if (experimentalGenerateTextRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'experimentalGenerateTextRequest' when calling experimentalGenerateText", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
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

        ParameterizedTypeReference<GenerateTextResponse> localVarReturnType = new ParameterizedTypeReference<GenerateTextResponse>() {};
        return apiClient.invokeAPI("/api/experimental/generate", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Generate text
     * Run one stateless model generation using the server&#39;s base configuration and return the assistant text. Uses the base configuration&#39;s default model when none is specified.
     * <p><b>200</b> - GenerateTextResponse
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param experimentalGenerateTextRequest The experimentalGenerateTextRequest parameter
     * @return GenerateTextResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<GenerateTextResponse> experimentalGenerateText(@jakarta.annotation.Nonnull ExperimentalGenerateTextRequest experimentalGenerateTextRequest) throws WebClientResponseException {
        ParameterizedTypeReference<GenerateTextResponse> localVarReturnType = new ParameterizedTypeReference<GenerateTextResponse>() {};
        return experimentalGenerateTextRequestCreation(experimentalGenerateTextRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Generate text
     * Run one stateless model generation using the server&#39;s base configuration and return the assistant text. Uses the base configuration&#39;s default model when none is specified.
     * <p><b>200</b> - GenerateTextResponse
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param experimentalGenerateTextRequest The experimentalGenerateTextRequest parameter
     * @return ResponseEntity&lt;GenerateTextResponse&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<GenerateTextResponse>> experimentalGenerateTextWithHttpInfo(@jakarta.annotation.Nonnull ExperimentalGenerateTextRequest experimentalGenerateTextRequest) throws WebClientResponseException {
        ParameterizedTypeReference<GenerateTextResponse> localVarReturnType = new ParameterizedTypeReference<GenerateTextResponse>() {};
        return experimentalGenerateTextRequestCreation(experimentalGenerateTextRequest).toEntity(localVarReturnType);
    }

    /**
     * Generate text
     * Run one stateless model generation using the server&#39;s base configuration and return the assistant text. Uses the base configuration&#39;s default model when none is specified.
     * <p><b>200</b> - GenerateTextResponse
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param experimentalGenerateTextRequest The experimentalGenerateTextRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalGenerateTextWithResponseSpec(@jakarta.annotation.Nonnull ExperimentalGenerateTextRequest experimentalGenerateTextRequest) throws WebClientResponseException {
        return experimentalGenerateTextRequestCreation(experimentalGenerateTextRequest);
    }
}
