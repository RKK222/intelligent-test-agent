package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.EventSubscribe200Response;
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
public class EventApi {
    private ApiClient apiClient;

    public EventApi() {
        this(new ApiClient());
    }

    public EventApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Subscribe to events
     * Subscribe to native events and plugin RPC events across all server locations. Volatile by contract: a slow consumer overflows and fails the stream, and events during disconnection are missed.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return EventSubscribe200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec eventSubscribeRequestCreation() throws WebClientResponseException {
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

        ParameterizedTypeReference<EventSubscribe200Response> localVarReturnType = new ParameterizedTypeReference<EventSubscribe200Response>() {};
        return apiClient.invokeAPI("/api/event", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Subscribe to events
     * Subscribe to native events and plugin RPC events across all server locations. Volatile by contract: a slow consumer overflows and fails the stream, and events during disconnection are missed.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return EventSubscribe200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<EventSubscribe200Response> eventSubscribe() throws WebClientResponseException {
        ParameterizedTypeReference<EventSubscribe200Response> localVarReturnType = new ParameterizedTypeReference<EventSubscribe200Response>() {};
        return eventSubscribeRequestCreation().bodyToMono(localVarReturnType);
    }

    /**
     * Subscribe to events
     * Subscribe to native events and plugin RPC events across all server locations. Volatile by contract: a slow consumer overflows and fails the stream, and events during disconnection are missed.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return ResponseEntity&lt;EventSubscribe200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<EventSubscribe200Response>> eventSubscribeWithHttpInfo() throws WebClientResponseException {
        ParameterizedTypeReference<EventSubscribe200Response> localVarReturnType = new ParameterizedTypeReference<EventSubscribe200Response>() {};
        return eventSubscribeRequestCreation().toEntity(localVarReturnType);
    }

    /**
     * Subscribe to events
     * Subscribe to native events and plugin RPC events across all server locations. Volatile by contract: a slow consumer overflows and fails the stream, and events during disconnection are missed.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec eventSubscribeWithResponseSpec() throws WebClientResponseException {
        return eventSubscribeRequestCreation();
    }
}
