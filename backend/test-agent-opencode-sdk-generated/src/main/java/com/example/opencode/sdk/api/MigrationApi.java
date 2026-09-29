package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.ExperimentalMigrationV1Status200Response;
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
public class MigrationApi {
    private ApiClient apiClient;

    public MigrationApi() {
        this(new ApiClient());
    }

    public MigrationApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Get V1 migration status
     * Return the progress of the V1 to V2 session history migration.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return ExperimentalMigrationV1Status200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalMigrationV1StatusRequestCreation() throws WebClientResponseException {
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

        ParameterizedTypeReference<ExperimentalMigrationV1Status200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalMigrationV1Status200Response>() {};
        return apiClient.invokeAPI("/api/experimental/migration/v1", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get V1 migration status
     * Return the progress of the V1 to V2 session history migration.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return ExperimentalMigrationV1Status200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ExperimentalMigrationV1Status200Response> experimentalMigrationV1Status() throws WebClientResponseException {
        ParameterizedTypeReference<ExperimentalMigrationV1Status200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalMigrationV1Status200Response>() {};
        return experimentalMigrationV1StatusRequestCreation().bodyToMono(localVarReturnType);
    }

    /**
     * Get V1 migration status
     * Return the progress of the V1 to V2 session history migration.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return ResponseEntity&lt;ExperimentalMigrationV1Status200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ExperimentalMigrationV1Status200Response>> experimentalMigrationV1StatusWithHttpInfo() throws WebClientResponseException {
        ParameterizedTypeReference<ExperimentalMigrationV1Status200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalMigrationV1Status200Response>() {};
        return experimentalMigrationV1StatusRequestCreation().toEntity(localVarReturnType);
    }

    /**
     * Get V1 migration status
     * Return the progress of the V1 to V2 session history migration.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalMigrationV1StatusWithResponseSpec() throws WebClientResponseException {
        return experimentalMigrationV1StatusRequestCreation();
    }
}
