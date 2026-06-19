package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.Event;

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

    public class EventSubscribeRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public EventSubscribeRequest() {}

        public EventSubscribeRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public EventSubscribeRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public EventSubscribeRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            EventSubscribeRequest request = (EventSubscribeRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * Subscribe to events
     * Get events
     * <p><b>200</b> - Event stream
     * @param requestParameters The eventSubscribe request parameters as object
     * @return Event
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Event> eventSubscribe(EventSubscribeRequest requestParameters) throws WebClientResponseException {
        return this.eventSubscribe(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Subscribe to events
     * Get events
     * <p><b>200</b> - Event stream
     * @param requestParameters The eventSubscribe request parameters as object
     * @return ResponseEntity&lt;Event&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Event>> eventSubscribeWithHttpInfo(EventSubscribeRequest requestParameters) throws WebClientResponseException {
        return this.eventSubscribeWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Subscribe to events
     * Get events
     * <p><b>200</b> - Event stream
     * @param requestParameters The eventSubscribe request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec eventSubscribeWithResponseSpec(EventSubscribeRequest requestParameters) throws WebClientResponseException {
        return this.eventSubscribeWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Subscribe to events
     * Get events
     * <p><b>200</b> - Event stream
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Event
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec eventSubscribeRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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
            "text/event-stream"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Event> localVarReturnType = new ParameterizedTypeReference<Event>() {};
        return apiClient.invokeAPI("/event", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Subscribe to events
     * Get events
     * <p><b>200</b> - Event stream
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Event
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Event> eventSubscribe(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Event> localVarReturnType = new ParameterizedTypeReference<Event>() {};
        return eventSubscribeRequestCreation(directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Subscribe to events
     * Get events
     * <p><b>200</b> - Event stream
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Event&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Event>> eventSubscribeWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Event> localVarReturnType = new ParameterizedTypeReference<Event>() {};
        return eventSubscribeRequestCreation(directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Subscribe to events
     * Get events
     * <p><b>200</b> - Event stream
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec eventSubscribeWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return eventSubscribeRequestCreation(directory, workspace);
    }
}
