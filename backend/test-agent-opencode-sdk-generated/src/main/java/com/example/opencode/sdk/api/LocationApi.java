package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.InvalidRequestErrorEncoded;
import com.example.opencode.sdk.model.LocationGetLocationParameter;
import com.example.opencode.sdk.model.LocationPublicInfo;
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
public class LocationApi {
    private ApiClient apiClient;

    public LocationApi() {
        this(new ApiClient());
    }

    public LocationApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Get location
     * Resolve the requested location or the server default location.
     * <p><b>200</b> - Location.PublicInfo
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return LocationPublicInfo
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec locationGetRequestCreation(@jakarta.annotation.Nullable LocationGetLocationParameter location) throws WebClientResponseException {
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

        ParameterizedTypeReference<LocationPublicInfo> localVarReturnType = new ParameterizedTypeReference<LocationPublicInfo>() {};
        return apiClient.invokeAPI("/api/location", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get location
     * Resolve the requested location or the server default location.
     * <p><b>200</b> - Location.PublicInfo
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return LocationPublicInfo
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<LocationPublicInfo> locationGet(@jakarta.annotation.Nullable LocationGetLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<LocationPublicInfo> localVarReturnType = new ParameterizedTypeReference<LocationPublicInfo>() {};
        return locationGetRequestCreation(location).bodyToMono(localVarReturnType);
    }

    /**
     * Get location
     * Resolve the requested location or the server default location.
     * <p><b>200</b> - Location.PublicInfo
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseEntity&lt;LocationPublicInfo&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<LocationPublicInfo>> locationGetWithHttpInfo(@jakarta.annotation.Nullable LocationGetLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<LocationPublicInfo> localVarReturnType = new ParameterizedTypeReference<LocationPublicInfo>() {};
        return locationGetRequestCreation(location).toEntity(localVarReturnType);
    }

    /**
     * Get location
     * Resolve the requested location or the server default location.
     * <p><b>200</b> - Location.PublicInfo
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec locationGetWithResponseSpec(@jakarta.annotation.Nullable LocationGetLocationParameter location) throws WebClientResponseException {
        return locationGetRequestCreation(location);
    }

    /**
     * Reload configuration
     * Shut down and rebuild every loaded location. Pending permissions and forms are cancelled; running sessions continue with fresh services at the next step boundary. Emits location.shutdown for client recovery and responds once all replacement builds settle.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec locationReloadRequestCreation() throws WebClientResponseException {
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

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/location/reload", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Reload configuration
     * Shut down and rebuild every loaded location. Pending permissions and forms are cancelled; running sessions continue with fresh services at the next step boundary. Emits location.shutdown for client recovery and responds once all replacement builds settle.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> locationReload() throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return locationReloadRequestCreation().bodyToMono(localVarReturnType);
    }

    /**
     * Reload configuration
     * Shut down and rebuild every loaded location. Pending permissions and forms are cancelled; running sessions continue with fresh services at the next step boundary. Emits location.shutdown for client recovery and responds once all replacement builds settle.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> locationReloadWithHttpInfo() throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return locationReloadRequestCreation().toEntity(localVarReturnType);
    }

    /**
     * Reload configuration
     * Shut down and rebuild every loaded location. Pending permissions and forms are cancelled; running sessions continue with fresh services at the next step boundary. Emits location.shutdown for client recovery and responds once all replacement builds settle.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec locationReloadWithResponseSpec() throws WebClientResponseException {
        return locationReloadRequestCreation();
    }
}
