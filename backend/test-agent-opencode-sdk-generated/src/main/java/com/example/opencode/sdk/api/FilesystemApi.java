package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import java.io.File;
import com.example.opencode.sdk.model.InvalidRequestError;
import com.example.opencode.sdk.model.UnauthorizedError;
import com.example.opencode.sdk.model.V2AgentListLocationParameter;
import com.example.opencode.sdk.model.V2FsList200Response;

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
public class FilesystemApi {
    private ApiClient apiClient;

    public FilesystemApi() {
        this(new ApiClient());
    }

    public FilesystemApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public class V2FsFindRequest {
        private @jakarta.annotation.Nonnull String query;
        private @jakarta.annotation.Nullable V2AgentListLocationParameter location;
        private @jakarta.annotation.Nullable String type;
        private @jakarta.annotation.Nullable String limit;

        public V2FsFindRequest() {}

        public V2FsFindRequest(@jakarta.annotation.Nonnull String query, @jakarta.annotation.Nullable V2AgentListLocationParameter location, @jakarta.annotation.Nullable String type, @jakarta.annotation.Nullable String limit) {
            this.query = query;
            this.location = location;
            this.type = type;
            this.limit = limit;
        }

        public @jakarta.annotation.Nonnull String query() {
            return this.query;
        }
        public V2FsFindRequest query(@jakarta.annotation.Nonnull String query) {
            this.query = query;
            return this;
        }

        public @jakarta.annotation.Nullable V2AgentListLocationParameter location() {
            return this.location;
        }
        public V2FsFindRequest location(@jakarta.annotation.Nullable V2AgentListLocationParameter location) {
            this.location = location;
            return this;
        }

        public @jakarta.annotation.Nullable String type() {
            return this.type;
        }
        public V2FsFindRequest type(@jakarta.annotation.Nullable String type) {
            this.type = type;
            return this;
        }

        public @jakarta.annotation.Nullable String limit() {
            return this.limit;
        }
        public V2FsFindRequest limit(@jakarta.annotation.Nullable String limit) {
            this.limit = limit;
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
            V2FsFindRequest request = (V2FsFindRequest) o;
            return Objects.equals(this.query, request.query()) &&
                Objects.equals(this.location, request.location()) &&
                Objects.equals(this.type, request.type()) &&
                Objects.equals(this.limit, request.limit());
        }

        @Override
        public int hashCode() {
            return Objects.hash(query, location, type, limit);
        }
    }

    /**
     * Find files
     * Find recursively ranked filesystem entries relative to the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2FsFind request parameters as object
     * @return V2FsList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2FsList200Response> v2FsFind(V2FsFindRequest requestParameters) throws WebClientResponseException {
        return this.v2FsFind(requestParameters.query(), requestParameters.location(), requestParameters.type(), requestParameters.limit());
    }

    /**
     * Find files
     * Find recursively ranked filesystem entries relative to the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2FsFind request parameters as object
     * @return ResponseEntity&lt;V2FsList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2FsList200Response>> v2FsFindWithHttpInfo(V2FsFindRequest requestParameters) throws WebClientResponseException {
        return this.v2FsFindWithHttpInfo(requestParameters.query(), requestParameters.location(), requestParameters.type(), requestParameters.limit());
    }

    /**
     * Find files
     * Find recursively ranked filesystem entries relative to the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2FsFind request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2FsFindWithResponseSpec(V2FsFindRequest requestParameters) throws WebClientResponseException {
        return this.v2FsFindWithResponseSpec(requestParameters.query(), requestParameters.location(), requestParameters.type(), requestParameters.limit());
    }


    /**
     * Find files
     * Find recursively ranked filesystem entries relative to the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param query The query parameter
     * @param location The location parameter
     * @param type The type parameter
     * @param limit The limit parameter
     * @return V2FsList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2FsFindRequestCreation(@jakarta.annotation.Nonnull String query, @jakarta.annotation.Nullable V2AgentListLocationParameter location, @jakarta.annotation.Nullable String type, @jakarta.annotation.Nullable String limit) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'query' is set
        if (query == null) {
            throw new WebClientResponseException("Missing the required parameter 'query' when calling v2FsFind", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", location.getWorkspace()));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "query", query));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "type", type));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "limit", limit));

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<V2FsList200Response> localVarReturnType = new ParameterizedTypeReference<V2FsList200Response>() {};
        return apiClient.invokeAPI("/api/fs/find", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Find files
     * Find recursively ranked filesystem entries relative to the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param query The query parameter
     * @param location The location parameter
     * @param type The type parameter
     * @param limit The limit parameter
     * @return V2FsList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2FsList200Response> v2FsFind(@jakarta.annotation.Nonnull String query, @jakarta.annotation.Nullable V2AgentListLocationParameter location, @jakarta.annotation.Nullable String type, @jakarta.annotation.Nullable String limit) throws WebClientResponseException {
        ParameterizedTypeReference<V2FsList200Response> localVarReturnType = new ParameterizedTypeReference<V2FsList200Response>() {};
        return v2FsFindRequestCreation(query, location, type, limit).bodyToMono(localVarReturnType);
    }

    /**
     * Find files
     * Find recursively ranked filesystem entries relative to the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param query The query parameter
     * @param location The location parameter
     * @param type The type parameter
     * @param limit The limit parameter
     * @return ResponseEntity&lt;V2FsList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2FsList200Response>> v2FsFindWithHttpInfo(@jakarta.annotation.Nonnull String query, @jakarta.annotation.Nullable V2AgentListLocationParameter location, @jakarta.annotation.Nullable String type, @jakarta.annotation.Nullable String limit) throws WebClientResponseException {
        ParameterizedTypeReference<V2FsList200Response> localVarReturnType = new ParameterizedTypeReference<V2FsList200Response>() {};
        return v2FsFindRequestCreation(query, location, type, limit).toEntity(localVarReturnType);
    }

    /**
     * Find files
     * Find recursively ranked filesystem entries relative to the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param query The query parameter
     * @param location The location parameter
     * @param type The type parameter
     * @param limit The limit parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2FsFindWithResponseSpec(@jakarta.annotation.Nonnull String query, @jakarta.annotation.Nullable V2AgentListLocationParameter location, @jakarta.annotation.Nullable String type, @jakarta.annotation.Nullable String limit) throws WebClientResponseException {
        return v2FsFindRequestCreation(query, location, type, limit);
    }

    public class V2FsListRequest {
        private @jakarta.annotation.Nullable V2AgentListLocationParameter location;
        private @jakarta.annotation.Nullable String path;

        public V2FsListRequest() {}

        public V2FsListRequest(@jakarta.annotation.Nullable V2AgentListLocationParameter location, @jakarta.annotation.Nullable String path) {
            this.location = location;
            this.path = path;
        }

        public @jakarta.annotation.Nullable V2AgentListLocationParameter location() {
            return this.location;
        }
        public V2FsListRequest location(@jakarta.annotation.Nullable V2AgentListLocationParameter location) {
            this.location = location;
            return this;
        }

        public @jakarta.annotation.Nullable String path() {
            return this.path;
        }
        public V2FsListRequest path(@jakarta.annotation.Nullable String path) {
            this.path = path;
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
            V2FsListRequest request = (V2FsListRequest) o;
            return Objects.equals(this.location, request.location()) &&
                Objects.equals(this.path, request.path());
        }

        @Override
        public int hashCode() {
            return Objects.hash(location, path);
        }
    }

    /**
     * List directory
     * List direct children of one directory relative to the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2FsList request parameters as object
     * @return V2FsList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2FsList200Response> v2FsList(V2FsListRequest requestParameters) throws WebClientResponseException {
        return this.v2FsList(requestParameters.location(), requestParameters.path());
    }

    /**
     * List directory
     * List direct children of one directory relative to the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2FsList request parameters as object
     * @return ResponseEntity&lt;V2FsList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2FsList200Response>> v2FsListWithHttpInfo(V2FsListRequest requestParameters) throws WebClientResponseException {
        return this.v2FsListWithHttpInfo(requestParameters.location(), requestParameters.path());
    }

    /**
     * List directory
     * List direct children of one directory relative to the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The v2FsList request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2FsListWithResponseSpec(V2FsListRequest requestParameters) throws WebClientResponseException {
        return this.v2FsListWithResponseSpec(requestParameters.location(), requestParameters.path());
    }


    /**
     * List directory
     * List direct children of one directory relative to the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @param path The path parameter
     * @return V2FsList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2FsListRequestCreation(@jakarta.annotation.Nullable V2AgentListLocationParameter location, @jakarta.annotation.Nullable String path) throws WebClientResponseException {
        Object postBody = null;
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", location.getWorkspace()));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "path", path));

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<V2FsList200Response> localVarReturnType = new ParameterizedTypeReference<V2FsList200Response>() {};
        return apiClient.invokeAPI("/api/fs/list", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List directory
     * List direct children of one directory relative to the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @param path The path parameter
     * @return V2FsList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2FsList200Response> v2FsList(@jakarta.annotation.Nullable V2AgentListLocationParameter location, @jakarta.annotation.Nullable String path) throws WebClientResponseException {
        ParameterizedTypeReference<V2FsList200Response> localVarReturnType = new ParameterizedTypeReference<V2FsList200Response>() {};
        return v2FsListRequestCreation(location, path).bodyToMono(localVarReturnType);
    }

    /**
     * List directory
     * List direct children of one directory relative to the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @param path The path parameter
     * @return ResponseEntity&lt;V2FsList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2FsList200Response>> v2FsListWithHttpInfo(@jakarta.annotation.Nullable V2AgentListLocationParameter location, @jakarta.annotation.Nullable String path) throws WebClientResponseException {
        ParameterizedTypeReference<V2FsList200Response> localVarReturnType = new ParameterizedTypeReference<V2FsList200Response>() {};
        return v2FsListRequestCreation(location, path).toEntity(localVarReturnType);
    }

    /**
     * List directory
     * List direct children of one directory relative to the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @param path The path parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2FsListWithResponseSpec(@jakarta.annotation.Nullable V2AgentListLocationParameter location, @jakarta.annotation.Nullable String path) throws WebClientResponseException {
        return v2FsListRequestCreation(location, path);
    }

    /**
     * Read file
     * Serve one file relative to the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return File
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2FsReadRequestCreation(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
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
            "application/octet-stream", "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<File> localVarReturnType = new ParameterizedTypeReference<File>() {};
        return apiClient.invokeAPI("/api/fs/read/*", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Read file
     * Serve one file relative to the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return File
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<File> v2FsRead(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<File> localVarReturnType = new ParameterizedTypeReference<File>() {};
        return v2FsReadRequestCreation(location).bodyToMono(localVarReturnType);
    }

    /**
     * Read file
     * Serve one file relative to the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseEntity&lt;File&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<File>> v2FsReadWithHttpInfo(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<File> localVarReturnType = new ParameterizedTypeReference<File>() {};
        return v2FsReadRequestCreation(location).toEntity(localVarReturnType);
    }

    /**
     * Read file
     * Serve one file relative to the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2FsReadWithResponseSpec(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        return v2FsReadRequestCreation(location);
    }
}
