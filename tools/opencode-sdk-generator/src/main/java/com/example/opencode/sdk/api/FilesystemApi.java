package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.AgentListLocationParameter;
import com.example.opencode.sdk.model.ExperimentalFsWrite200Response;
import java.io.File;
import com.example.opencode.sdk.model.FileNotFoundErrorEncoded;
import com.example.opencode.sdk.model.FsList200Response;
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

    public class ExperimentalFsWriteRequest {
        private @jakarta.annotation.Nonnull String path;
        private @jakarta.annotation.Nonnull File body;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;

        public ExperimentalFsWriteRequest() {}

        public ExperimentalFsWriteRequest(@jakarta.annotation.Nonnull String path, @jakarta.annotation.Nonnull File body, @jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.path = path;
            this.body = body;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String path() {
            return this.path;
        }
        public ExperimentalFsWriteRequest path(@jakarta.annotation.Nonnull String path) {
            this.path = path;
            return this;
        }

        public @jakarta.annotation.Nonnull File body() {
            return this.body;
        }
        public ExperimentalFsWriteRequest body(@jakarta.annotation.Nonnull File body) {
            this.body = body;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public ExperimentalFsWriteRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.location = location;
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
            ExperimentalFsWriteRequest request = (ExperimentalFsWriteRequest) o;
            return Objects.equals(this.path, request.path()) &&
                Objects.equals(this.body, request.body()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, body, location);
        }
    }

    /**
     * Write file
     * Write the raw request body to an absolute path or a path relative to the requested location, creating parent directories, and return the resolved absolute path. Unlike read, the target is not confined to the location. Experimental: may change without compatibility guarantees.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The experimentalFsWrite request parameters as object
     * @return ExperimentalFsWrite200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ExperimentalFsWrite200Response> experimentalFsWrite(ExperimentalFsWriteRequest requestParameters) throws WebClientResponseException {
        return this.experimentalFsWrite(requestParameters.path(), requestParameters.body(), requestParameters.location());
    }

    /**
     * Write file
     * Write the raw request body to an absolute path or a path relative to the requested location, creating parent directories, and return the resolved absolute path. Unlike read, the target is not confined to the location. Experimental: may change without compatibility guarantees.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The experimentalFsWrite request parameters as object
     * @return ResponseEntity&lt;ExperimentalFsWrite200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ExperimentalFsWrite200Response>> experimentalFsWriteWithHttpInfo(ExperimentalFsWriteRequest requestParameters) throws WebClientResponseException {
        return this.experimentalFsWriteWithHttpInfo(requestParameters.path(), requestParameters.body(), requestParameters.location());
    }

    /**
     * Write file
     * Write the raw request body to an absolute path or a path relative to the requested location, creating parent directories, and return the resolved absolute path. Unlike read, the target is not confined to the location. Experimental: may change without compatibility guarantees.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The experimentalFsWrite request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalFsWriteWithResponseSpec(ExperimentalFsWriteRequest requestParameters) throws WebClientResponseException {
        return this.experimentalFsWriteWithResponseSpec(requestParameters.path(), requestParameters.body(), requestParameters.location());
    }


    /**
     * Write file
     * Write the raw request body to an absolute path or a path relative to the requested location, creating parent directories, and return the resolved absolute path. Unlike read, the target is not confined to the location. Experimental: may change without compatibility guarantees.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param path The path parameter
     * @param body The body parameter
     * @param location The location parameter
     * @return ExperimentalFsWrite200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalFsWriteRequestCreation(@jakarta.annotation.Nonnull String path, @jakarta.annotation.Nonnull File body, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = body;
        // verify the required parameter 'path' is set
        if (path == null) {
            throw new WebClientResponseException("Missing the required parameter 'path' when calling experimentalFsWrite", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'body' is set
        if (body == null) {
            throw new WebClientResponseException("Missing the required parameter 'body' when calling experimentalFsWrite", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "path", path));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = {
            "application/octet-stream"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<ExperimentalFsWrite200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalFsWrite200Response>() {};
        return apiClient.invokeAPI("/api/experimental/fs/write", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Write file
     * Write the raw request body to an absolute path or a path relative to the requested location, creating parent directories, and return the resolved absolute path. Unlike read, the target is not confined to the location. Experimental: may change without compatibility guarantees.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param path The path parameter
     * @param body The body parameter
     * @param location The location parameter
     * @return ExperimentalFsWrite200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ExperimentalFsWrite200Response> experimentalFsWrite(@jakarta.annotation.Nonnull String path, @jakarta.annotation.Nonnull File body, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<ExperimentalFsWrite200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalFsWrite200Response>() {};
        return experimentalFsWriteRequestCreation(path, body, location).bodyToMono(localVarReturnType);
    }

    /**
     * Write file
     * Write the raw request body to an absolute path or a path relative to the requested location, creating parent directories, and return the resolved absolute path. Unlike read, the target is not confined to the location. Experimental: may change without compatibility guarantees.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param path The path parameter
     * @param body The body parameter
     * @param location The location parameter
     * @return ResponseEntity&lt;ExperimentalFsWrite200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ExperimentalFsWrite200Response>> experimentalFsWriteWithHttpInfo(@jakarta.annotation.Nonnull String path, @jakarta.annotation.Nonnull File body, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<ExperimentalFsWrite200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalFsWrite200Response>() {};
        return experimentalFsWriteRequestCreation(path, body, location).toEntity(localVarReturnType);
    }

    /**
     * Write file
     * Write the raw request body to an absolute path or a path relative to the requested location, creating parent directories, and return the resolved absolute path. Unlike read, the target is not confined to the location. Experimental: may change without compatibility guarantees.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param path The path parameter
     * @param body The body parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalFsWriteWithResponseSpec(@jakarta.annotation.Nonnull String path, @jakarta.annotation.Nonnull File body, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return experimentalFsWriteRequestCreation(path, body, location);
    }

    public class FsFindRequest {
        private @jakarta.annotation.Nullable String query;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;
        private @jakarta.annotation.Nullable String type;
        private @jakarta.annotation.Nullable String limit;

        public FsFindRequest() {}

        public FsFindRequest(@jakarta.annotation.Nullable String query, @jakarta.annotation.Nullable AgentListLocationParameter location, @jakarta.annotation.Nullable String type, @jakarta.annotation.Nullable String limit) {
            this.query = query;
            this.location = location;
            this.type = type;
            this.limit = limit;
        }

        public @jakarta.annotation.Nullable String query() {
            return this.query;
        }
        public FsFindRequest query(@jakarta.annotation.Nullable String query) {
            this.query = query;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public FsFindRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.location = location;
            return this;
        }

        public @jakarta.annotation.Nullable String type() {
            return this.type;
        }
        public FsFindRequest type(@jakarta.annotation.Nullable String type) {
            this.type = type;
            return this;
        }

        public @jakarta.annotation.Nullable String limit() {
            return this.limit;
        }
        public FsFindRequest limit(@jakarta.annotation.Nullable String limit) {
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
            FsFindRequest request = (FsFindRequest) o;
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
     * @param requestParameters The fsFind request parameters as object
     * @return FsList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<FsList200Response> fsFind(FsFindRequest requestParameters) throws WebClientResponseException {
        return this.fsFind(requestParameters.query(), requestParameters.location(), requestParameters.type(), requestParameters.limit());
    }

    /**
     * Find files
     * Find recursively ranked filesystem entries relative to the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The fsFind request parameters as object
     * @return ResponseEntity&lt;FsList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<FsList200Response>> fsFindWithHttpInfo(FsFindRequest requestParameters) throws WebClientResponseException {
        return this.fsFindWithHttpInfo(requestParameters.query(), requestParameters.location(), requestParameters.type(), requestParameters.limit());
    }

    /**
     * Find files
     * Find recursively ranked filesystem entries relative to the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The fsFind request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec fsFindWithResponseSpec(FsFindRequest requestParameters) throws WebClientResponseException {
        return this.fsFindWithResponseSpec(requestParameters.query(), requestParameters.location(), requestParameters.type(), requestParameters.limit());
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
     * @return FsList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec fsFindRequestCreation(@jakarta.annotation.Nullable String query, @jakarta.annotation.Nullable AgentListLocationParameter location, @jakarta.annotation.Nullable String type, @jakarta.annotation.Nullable String limit) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'query' is set
        if (query == null) {
            throw new WebClientResponseException("Missing the required parameter 'query' when calling fsFind", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));
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

        ParameterizedTypeReference<FsList200Response> localVarReturnType = new ParameterizedTypeReference<FsList200Response>() {};
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
     * @return FsList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<FsList200Response> fsFind(@jakarta.annotation.Nullable String query, @jakarta.annotation.Nullable AgentListLocationParameter location, @jakarta.annotation.Nullable String type, @jakarta.annotation.Nullable String limit) throws WebClientResponseException {
        ParameterizedTypeReference<FsList200Response> localVarReturnType = new ParameterizedTypeReference<FsList200Response>() {};
        return fsFindRequestCreation(query, location, type, limit).bodyToMono(localVarReturnType);
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
     * @return ResponseEntity&lt;FsList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<FsList200Response>> fsFindWithHttpInfo(@jakarta.annotation.Nullable String query, @jakarta.annotation.Nullable AgentListLocationParameter location, @jakarta.annotation.Nullable String type, @jakarta.annotation.Nullable String limit) throws WebClientResponseException {
        ParameterizedTypeReference<FsList200Response> localVarReturnType = new ParameterizedTypeReference<FsList200Response>() {};
        return fsFindRequestCreation(query, location, type, limit).toEntity(localVarReturnType);
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
    public ResponseSpec fsFindWithResponseSpec(@jakarta.annotation.Nullable String query, @jakarta.annotation.Nullable AgentListLocationParameter location, @jakarta.annotation.Nullable String type, @jakarta.annotation.Nullable String limit) throws WebClientResponseException {
        return fsFindRequestCreation(query, location, type, limit);
    }

    public class FsListRequest {
        private @jakarta.annotation.Nullable AgentListLocationParameter location;
        private @jakarta.annotation.Nullable String path;

        public FsListRequest() {}

        public FsListRequest(@jakarta.annotation.Nullable AgentListLocationParameter location, @jakarta.annotation.Nullable String path) {
            this.location = location;
            this.path = path;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public FsListRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.location = location;
            return this;
        }

        public @jakarta.annotation.Nullable String path() {
            return this.path;
        }
        public FsListRequest path(@jakarta.annotation.Nullable String path) {
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
            FsListRequest request = (FsListRequest) o;
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
     * List direct children using an absolute path or a path relative to the requested location, including parents and siblings outside its directory. Entry paths remain relative to the requested location; listing does not switch locations.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The fsList request parameters as object
     * @return FsList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<FsList200Response> fsList(FsListRequest requestParameters) throws WebClientResponseException {
        return this.fsList(requestParameters.location(), requestParameters.path());
    }

    /**
     * List directory
     * List direct children using an absolute path or a path relative to the requested location, including parents and siblings outside its directory. Entry paths remain relative to the requested location; listing does not switch locations.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The fsList request parameters as object
     * @return ResponseEntity&lt;FsList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<FsList200Response>> fsListWithHttpInfo(FsListRequest requestParameters) throws WebClientResponseException {
        return this.fsListWithHttpInfo(requestParameters.location(), requestParameters.path());
    }

    /**
     * List directory
     * List direct children using an absolute path or a path relative to the requested location, including parents and siblings outside its directory. Entry paths remain relative to the requested location; listing does not switch locations.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The fsList request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec fsListWithResponseSpec(FsListRequest requestParameters) throws WebClientResponseException {
        return this.fsListWithResponseSpec(requestParameters.location(), requestParameters.path());
    }


    /**
     * List directory
     * List direct children using an absolute path or a path relative to the requested location, including parents and siblings outside its directory. Entry paths remain relative to the requested location; listing does not switch locations.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @param path The path parameter
     * @return FsList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec fsListRequestCreation(@jakarta.annotation.Nullable AgentListLocationParameter location, @jakarta.annotation.Nullable String path) throws WebClientResponseException {
        Object postBody = null;
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "path", path));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<FsList200Response> localVarReturnType = new ParameterizedTypeReference<FsList200Response>() {};
        return apiClient.invokeAPI("/api/fs/list", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List directory
     * List direct children using an absolute path or a path relative to the requested location, including parents and siblings outside its directory. Entry paths remain relative to the requested location; listing does not switch locations.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @param path The path parameter
     * @return FsList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<FsList200Response> fsList(@jakarta.annotation.Nullable AgentListLocationParameter location, @jakarta.annotation.Nullable String path) throws WebClientResponseException {
        ParameterizedTypeReference<FsList200Response> localVarReturnType = new ParameterizedTypeReference<FsList200Response>() {};
        return fsListRequestCreation(location, path).bodyToMono(localVarReturnType);
    }

    /**
     * List directory
     * List direct children using an absolute path or a path relative to the requested location, including parents and siblings outside its directory. Entry paths remain relative to the requested location; listing does not switch locations.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @param path The path parameter
     * @return ResponseEntity&lt;FsList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<FsList200Response>> fsListWithHttpInfo(@jakarta.annotation.Nullable AgentListLocationParameter location, @jakarta.annotation.Nullable String path) throws WebClientResponseException {
        ParameterizedTypeReference<FsList200Response> localVarReturnType = new ParameterizedTypeReference<FsList200Response>() {};
        return fsListRequestCreation(location, path).toEntity(localVarReturnType);
    }

    /**
     * List directory
     * List direct children using an absolute path or a path relative to the requested location, including parents and siblings outside its directory. Entry paths remain relative to the requested location; listing does not switch locations.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @param path The path parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec fsListWithResponseSpec(@jakarta.annotation.Nullable AgentListLocationParameter location, @jakarta.annotation.Nullable String path) throws WebClientResponseException {
        return fsListRequestCreation(location, path);
    }

    /**
     * Read file
     * Serve one file relative to the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - FileNotFoundError
     * @param location The location parameter
     * @return File
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec fsReadRequestCreation(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = null;
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));

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
     * <p><b>404</b> - FileNotFoundError
     * @param location The location parameter
     * @return File
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<File> fsRead(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<File> localVarReturnType = new ParameterizedTypeReference<File>() {};
        return fsReadRequestCreation(location).bodyToMono(localVarReturnType);
    }

    /**
     * Read file
     * Serve one file relative to the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - FileNotFoundError
     * @param location The location parameter
     * @return ResponseEntity&lt;File&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<File>> fsReadWithHttpInfo(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<File> localVarReturnType = new ParameterizedTypeReference<File>() {};
        return fsReadRequestCreation(location).toEntity(localVarReturnType);
    }

    /**
     * Read file
     * Serve one file relative to the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - FileNotFoundError
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec fsReadWithResponseSpec(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return fsReadRequestCreation(location);
    }
}
