package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import java.math.BigDecimal;
import com.example.opencode.sdk.model.SessionMessagesResponse;
import com.example.opencode.sdk.model.UnauthorizedError;
import com.example.opencode.sdk.model.UnknownError1;
import com.example.opencode.sdk.model.V2SessionGet404Response;
import com.example.opencode.sdk.model.V2SessionMessages400Response;

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
public class MessagesApi {
    private ApiClient apiClient;

    public MessagesApi() {
        this(new ApiClient());
    }

    public MessagesApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public class V2SessionMessagesRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable BigDecimal limit;
        private @jakarta.annotation.Nullable String order;
        private @jakarta.annotation.Nullable String cursor;

        public V2SessionMessagesRequest() {}

        public V2SessionMessagesRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable BigDecimal limit, @jakarta.annotation.Nullable String order, @jakarta.annotation.Nullable String cursor) {
            this.sessionID = sessionID;
            this.limit = limit;
            this.order = order;
            this.cursor = cursor;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public V2SessionMessagesRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable BigDecimal limit() {
            return this.limit;
        }
        public V2SessionMessagesRequest limit(@jakarta.annotation.Nullable BigDecimal limit) {
            this.limit = limit;
            return this;
        }

        public @jakarta.annotation.Nullable String order() {
            return this.order;
        }
        public V2SessionMessagesRequest order(@jakarta.annotation.Nullable String order) {
            this.order = order;
            return this;
        }

        public @jakarta.annotation.Nullable String cursor() {
            return this.cursor;
        }
        public V2SessionMessagesRequest cursor(@jakarta.annotation.Nullable String cursor) {
            this.cursor = cursor;
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
            V2SessionMessagesRequest request = (V2SessionMessagesRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.limit, request.limit()) &&
                Objects.equals(this.order, request.order()) &&
                Objects.equals(this.cursor, request.cursor());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, limit, order, cursor);
        }
    }

    /**
     * Get session messages
     * Retrieve projected messages for a session. Items keep the requested order across pages; use cursor.next or cursor.previous to move through the ordered timeline.
     * <p><b>200</b> - SessionMessagesResponse
     * <p><b>400</b> - InvalidCursorError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param requestParameters The v2SessionMessages request parameters as object
     * @return SessionMessagesResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionMessagesResponse> v2SessionMessages(V2SessionMessagesRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionMessages(requestParameters.sessionID(), requestParameters.limit(), requestParameters.order(), requestParameters.cursor());
    }

    /**
     * Get session messages
     * Retrieve projected messages for a session. Items keep the requested order across pages; use cursor.next or cursor.previous to move through the ordered timeline.
     * <p><b>200</b> - SessionMessagesResponse
     * <p><b>400</b> - InvalidCursorError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param requestParameters The v2SessionMessages request parameters as object
     * @return ResponseEntity&lt;SessionMessagesResponse&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionMessagesResponse>> v2SessionMessagesWithHttpInfo(V2SessionMessagesRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionMessagesWithHttpInfo(requestParameters.sessionID(), requestParameters.limit(), requestParameters.order(), requestParameters.cursor());
    }

    /**
     * Get session messages
     * Retrieve projected messages for a session. Items keep the requested order across pages; use cursor.next or cursor.previous to move through the ordered timeline.
     * <p><b>200</b> - SessionMessagesResponse
     * <p><b>400</b> - InvalidCursorError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param requestParameters The v2SessionMessages request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionMessagesWithResponseSpec(V2SessionMessagesRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionMessagesWithResponseSpec(requestParameters.sessionID(), requestParameters.limit(), requestParameters.order(), requestParameters.cursor());
    }


    /**
     * Get session messages
     * Retrieve projected messages for a session. Items keep the requested order across pages; use cursor.next or cursor.previous to move through the ordered timeline.
     * <p><b>200</b> - SessionMessagesResponse
     * <p><b>400</b> - InvalidCursorError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @param limit The limit parameter
     * @param order The order parameter
     * @param cursor The cursor parameter
     * @return SessionMessagesResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2SessionMessagesRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable BigDecimal limit, @jakarta.annotation.Nullable String order, @jakarta.annotation.Nullable String cursor) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling v2SessionMessages", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "limit", limit));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "order", order));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "cursor", cursor));

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<SessionMessagesResponse> localVarReturnType = new ParameterizedTypeReference<SessionMessagesResponse>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/message", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get session messages
     * Retrieve projected messages for a session. Items keep the requested order across pages; use cursor.next or cursor.previous to move through the ordered timeline.
     * <p><b>200</b> - SessionMessagesResponse
     * <p><b>400</b> - InvalidCursorError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @param limit The limit parameter
     * @param order The order parameter
     * @param cursor The cursor parameter
     * @return SessionMessagesResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionMessagesResponse> v2SessionMessages(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable BigDecimal limit, @jakarta.annotation.Nullable String order, @jakarta.annotation.Nullable String cursor) throws WebClientResponseException {
        ParameterizedTypeReference<SessionMessagesResponse> localVarReturnType = new ParameterizedTypeReference<SessionMessagesResponse>() {};
        return v2SessionMessagesRequestCreation(sessionID, limit, order, cursor).bodyToMono(localVarReturnType);
    }

    /**
     * Get session messages
     * Retrieve projected messages for a session. Items keep the requested order across pages; use cursor.next or cursor.previous to move through the ordered timeline.
     * <p><b>200</b> - SessionMessagesResponse
     * <p><b>400</b> - InvalidCursorError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @param limit The limit parameter
     * @param order The order parameter
     * @param cursor The cursor parameter
     * @return ResponseEntity&lt;SessionMessagesResponse&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionMessagesResponse>> v2SessionMessagesWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable BigDecimal limit, @jakarta.annotation.Nullable String order, @jakarta.annotation.Nullable String cursor) throws WebClientResponseException {
        ParameterizedTypeReference<SessionMessagesResponse> localVarReturnType = new ParameterizedTypeReference<SessionMessagesResponse>() {};
        return v2SessionMessagesRequestCreation(sessionID, limit, order, cursor).toEntity(localVarReturnType);
    }

    /**
     * Get session messages
     * Retrieve projected messages for a session. Items keep the requested order across pages; use cursor.next or cursor.previous to move through the ordered timeline.
     * <p><b>200</b> - SessionMessagesResponse
     * <p><b>400</b> - InvalidCursorError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @param limit The limit parameter
     * @param order The order parameter
     * @param cursor The cursor parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionMessagesWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable BigDecimal limit, @jakarta.annotation.Nullable String order, @jakarta.annotation.Nullable String cursor) throws WebClientResponseException {
        return v2SessionMessagesRequestCreation(sessionID, limit, order, cursor);
    }
}
