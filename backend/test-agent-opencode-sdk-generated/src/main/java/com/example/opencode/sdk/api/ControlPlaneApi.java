package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.ExperimentalControlPlaneMoveSession400Response;
import com.example.opencode.sdk.model.ExperimentalControlPlaneMoveSessionRequest;

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
public class ControlPlaneApi {
    private ApiClient apiClient;

    public ControlPlaneApi() {
        this(new ApiClient());
    }

    public ControlPlaneApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Move session
     * Move a session to another project directory, optionally transferring local changes.
     * <p><b>204</b> - Session moved
     * <p><b>400</b> - MoveSessionError | InvalidRequestError
     * @param experimentalControlPlaneMoveSessionRequest The experimentalControlPlaneMoveSessionRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalControlPlaneMoveSessionRequestCreation(@jakarta.annotation.Nullable ExperimentalControlPlaneMoveSessionRequest experimentalControlPlaneMoveSessionRequest) throws WebClientResponseException {
        Object postBody = experimentalControlPlaneMoveSessionRequest;
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

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/experimental/control-plane/move-session", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Move session
     * Move a session to another project directory, optionally transferring local changes.
     * <p><b>204</b> - Session moved
     * <p><b>400</b> - MoveSessionError | InvalidRequestError
     * @param experimentalControlPlaneMoveSessionRequest The experimentalControlPlaneMoveSessionRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> experimentalControlPlaneMoveSession(@jakarta.annotation.Nullable ExperimentalControlPlaneMoveSessionRequest experimentalControlPlaneMoveSessionRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return experimentalControlPlaneMoveSessionRequestCreation(experimentalControlPlaneMoveSessionRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Move session
     * Move a session to another project directory, optionally transferring local changes.
     * <p><b>204</b> - Session moved
     * <p><b>400</b> - MoveSessionError | InvalidRequestError
     * @param experimentalControlPlaneMoveSessionRequest The experimentalControlPlaneMoveSessionRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> experimentalControlPlaneMoveSessionWithHttpInfo(@jakarta.annotation.Nullable ExperimentalControlPlaneMoveSessionRequest experimentalControlPlaneMoveSessionRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return experimentalControlPlaneMoveSessionRequestCreation(experimentalControlPlaneMoveSessionRequest).toEntity(localVarReturnType);
    }

    /**
     * Move session
     * Move a session to another project directory, optionally transferring local changes.
     * <p><b>204</b> - Session moved
     * <p><b>400</b> - MoveSessionError | InvalidRequestError
     * @param experimentalControlPlaneMoveSessionRequest The experimentalControlPlaneMoveSessionRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalControlPlaneMoveSessionWithResponseSpec(@jakarta.annotation.Nullable ExperimentalControlPlaneMoveSessionRequest experimentalControlPlaneMoveSessionRequest) throws WebClientResponseException {
        return experimentalControlPlaneMoveSessionRequestCreation(experimentalControlPlaneMoveSessionRequest);
    }
}
