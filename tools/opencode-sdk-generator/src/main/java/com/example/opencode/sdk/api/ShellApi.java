package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.AgentListLocationParameter;
import com.example.opencode.sdk.model.InvalidRequestErrorEncoded;
import com.example.opencode.sdk.model.ShellCreate200Response;
import com.example.opencode.sdk.model.ShellCreateRequest;
import com.example.opencode.sdk.model.ShellGet200Response;
import com.example.opencode.sdk.model.ShellList200Response;
import com.example.opencode.sdk.model.ShellNotFoundErrorEncoded;
import com.example.opencode.sdk.model.ShellOutput200Response;
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
public class ShellApi {
    private ApiClient apiClient;

    public ShellApi() {
        this(new ApiClient());
    }

    public ShellApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public class ShellCreateRequest {
        private @jakarta.annotation.Nonnull ShellCreateRequest shellCreateRequest;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;

        public ShellCreateRequest() {}

        public ShellCreateRequest(@jakarta.annotation.Nonnull ShellCreateRequest shellCreateRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.shellCreateRequest = shellCreateRequest;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull ShellCreateRequest shellCreateRequest() {
            return this.shellCreateRequest;
        }
        public ShellCreateRequest shellCreateRequest(@jakarta.annotation.Nonnull ShellCreateRequest shellCreateRequest) {
            this.shellCreateRequest = shellCreateRequest;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public ShellCreateRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
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
            ShellCreateRequest request = (ShellCreateRequest) o;
            return Objects.equals(this.shellCreateRequest, request.shellCreateRequest()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(shellCreateRequest, location);
        }
    }

    /**
     * Run shell command
     * Spawn one non-interactive shell command for a location. Combined stdout/stderr is captured to a file pageable via output.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The shellCreate request parameters as object
     * @return ShellCreate200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ShellCreate200Response> shellCreate(ShellCreateRequest requestParameters) throws WebClientResponseException {
        return this.shellCreate(requestParameters.shellCreateRequest(), requestParameters.location());
    }

    /**
     * Run shell command
     * Spawn one non-interactive shell command for a location. Combined stdout/stderr is captured to a file pageable via output.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The shellCreate request parameters as object
     * @return ResponseEntity&lt;ShellCreate200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ShellCreate200Response>> shellCreateWithHttpInfo(ShellCreateRequest requestParameters) throws WebClientResponseException {
        return this.shellCreateWithHttpInfo(requestParameters.shellCreateRequest(), requestParameters.location());
    }

    /**
     * Run shell command
     * Spawn one non-interactive shell command for a location. Combined stdout/stderr is captured to a file pageable via output.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The shellCreate request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec shellCreateWithResponseSpec(ShellCreateRequest requestParameters) throws WebClientResponseException {
        return this.shellCreateWithResponseSpec(requestParameters.shellCreateRequest(), requestParameters.location());
    }


    /**
     * Run shell command
     * Spawn one non-interactive shell command for a location. Combined stdout/stderr is captured to a file pageable via output.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param shellCreateRequest The shellCreateRequest parameter
     * @param location The location parameter
     * @return ShellCreate200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec shellCreateRequestCreation(@jakarta.annotation.Nonnull ShellCreateRequest shellCreateRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = shellCreateRequest;
        // verify the required parameter 'shellCreateRequest' is set
        if (shellCreateRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'shellCreateRequest' when calling shellCreate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
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
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<ShellCreate200Response> localVarReturnType = new ParameterizedTypeReference<ShellCreate200Response>() {};
        return apiClient.invokeAPI("/api/shell", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Run shell command
     * Spawn one non-interactive shell command for a location. Combined stdout/stderr is captured to a file pageable via output.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param shellCreateRequest The shellCreateRequest parameter
     * @param location The location parameter
     * @return ShellCreate200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ShellCreate200Response> shellCreate(@jakarta.annotation.Nonnull ShellCreateRequest shellCreateRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<ShellCreate200Response> localVarReturnType = new ParameterizedTypeReference<ShellCreate200Response>() {};
        return shellCreateRequestCreation(shellCreateRequest, location).bodyToMono(localVarReturnType);
    }

    /**
     * Run shell command
     * Spawn one non-interactive shell command for a location. Combined stdout/stderr is captured to a file pageable via output.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param shellCreateRequest The shellCreateRequest parameter
     * @param location The location parameter
     * @return ResponseEntity&lt;ShellCreate200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ShellCreate200Response>> shellCreateWithHttpInfo(@jakarta.annotation.Nonnull ShellCreateRequest shellCreateRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<ShellCreate200Response> localVarReturnType = new ParameterizedTypeReference<ShellCreate200Response>() {};
        return shellCreateRequestCreation(shellCreateRequest, location).toEntity(localVarReturnType);
    }

    /**
     * Run shell command
     * Spawn one non-interactive shell command for a location. Combined stdout/stderr is captured to a file pageable via output.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param shellCreateRequest The shellCreateRequest parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec shellCreateWithResponseSpec(@jakarta.annotation.Nonnull ShellCreateRequest shellCreateRequest, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return shellCreateRequestCreation(shellCreateRequest, location);
    }

    public class ShellGetRequest {
        private @jakarta.annotation.Nonnull String id;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;

        public ShellGetRequest() {}

        public ShellGetRequest(@jakarta.annotation.Nonnull String id, @jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.id = id;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String id() {
            return this.id;
        }
        public ShellGetRequest id(@jakarta.annotation.Nonnull String id) {
            this.id = id;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public ShellGetRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
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
            ShellGetRequest request = (ShellGetRequest) o;
            return Objects.equals(this.id, request.id()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, location);
        }
    }

    /**
     * Get shell command
     * Get one shell command, including its status and exit code once exited.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ShellNotFoundError
     * @param requestParameters The shellGet request parameters as object
     * @return ShellGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ShellGet200Response> shellGet(ShellGetRequest requestParameters) throws WebClientResponseException {
        return this.shellGet(requestParameters.id(), requestParameters.location());
    }

    /**
     * Get shell command
     * Get one shell command, including its status and exit code once exited.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ShellNotFoundError
     * @param requestParameters The shellGet request parameters as object
     * @return ResponseEntity&lt;ShellGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ShellGet200Response>> shellGetWithHttpInfo(ShellGetRequest requestParameters) throws WebClientResponseException {
        return this.shellGetWithHttpInfo(requestParameters.id(), requestParameters.location());
    }

    /**
     * Get shell command
     * Get one shell command, including its status and exit code once exited.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ShellNotFoundError
     * @param requestParameters The shellGet request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec shellGetWithResponseSpec(ShellGetRequest requestParameters) throws WebClientResponseException {
        return this.shellGetWithResponseSpec(requestParameters.id(), requestParameters.location());
    }


    /**
     * Get shell command
     * Get one shell command, including its status and exit code once exited.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ShellNotFoundError
     * @param id The id parameter
     * @param location The location parameter
     * @return ShellGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec shellGetRequestCreation(@jakarta.annotation.Nonnull String id, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'id' is set
        if (id == null) {
            throw new WebClientResponseException("Missing the required parameter 'id' when calling shellGet", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("id", id);

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

        ParameterizedTypeReference<ShellGet200Response> localVarReturnType = new ParameterizedTypeReference<ShellGet200Response>() {};
        return apiClient.invokeAPI("/api/shell/{id}", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get shell command
     * Get one shell command, including its status and exit code once exited.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ShellNotFoundError
     * @param id The id parameter
     * @param location The location parameter
     * @return ShellGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ShellGet200Response> shellGet(@jakarta.annotation.Nonnull String id, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<ShellGet200Response> localVarReturnType = new ParameterizedTypeReference<ShellGet200Response>() {};
        return shellGetRequestCreation(id, location).bodyToMono(localVarReturnType);
    }

    /**
     * Get shell command
     * Get one shell command, including its status and exit code once exited.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ShellNotFoundError
     * @param id The id parameter
     * @param location The location parameter
     * @return ResponseEntity&lt;ShellGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ShellGet200Response>> shellGetWithHttpInfo(@jakarta.annotation.Nonnull String id, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<ShellGet200Response> localVarReturnType = new ParameterizedTypeReference<ShellGet200Response>() {};
        return shellGetRequestCreation(id, location).toEntity(localVarReturnType);
    }

    /**
     * Get shell command
     * Get one shell command, including its status and exit code once exited.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ShellNotFoundError
     * @param id The id parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec shellGetWithResponseSpec(@jakarta.annotation.Nonnull String id, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return shellGetRequestCreation(id, location);
    }

    /**
     * List running shell commands
     * List currently running shell commands for a location. Exited commands are not included.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ShellList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec shellListRequestCreation(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
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

        ParameterizedTypeReference<ShellList200Response> localVarReturnType = new ParameterizedTypeReference<ShellList200Response>() {};
        return apiClient.invokeAPI("/api/shell", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List running shell commands
     * List currently running shell commands for a location. Exited commands are not included.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ShellList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ShellList200Response> shellList(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<ShellList200Response> localVarReturnType = new ParameterizedTypeReference<ShellList200Response>() {};
        return shellListRequestCreation(location).bodyToMono(localVarReturnType);
    }

    /**
     * List running shell commands
     * List currently running shell commands for a location. Exited commands are not included.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseEntity&lt;ShellList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ShellList200Response>> shellListWithHttpInfo(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<ShellList200Response> localVarReturnType = new ParameterizedTypeReference<ShellList200Response>() {};
        return shellListRequestCreation(location).toEntity(localVarReturnType);
    }

    /**
     * List running shell commands
     * List currently running shell commands for a location. Exited commands are not included.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec shellListWithResponseSpec(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return shellListRequestCreation(location);
    }

    public class ShellOutputRequest {
        private @jakarta.annotation.Nonnull String id;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;
        private @jakarta.annotation.Nullable String cursor;
        private @jakarta.annotation.Nullable String limit;

        public ShellOutputRequest() {}

        public ShellOutputRequest(@jakarta.annotation.Nonnull String id, @jakarta.annotation.Nullable AgentListLocationParameter location, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String limit) {
            this.id = id;
            this.location = location;
            this.cursor = cursor;
            this.limit = limit;
        }

        public @jakarta.annotation.Nonnull String id() {
            return this.id;
        }
        public ShellOutputRequest id(@jakarta.annotation.Nonnull String id) {
            this.id = id;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public ShellOutputRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.location = location;
            return this;
        }

        public @jakarta.annotation.Nullable String cursor() {
            return this.cursor;
        }
        public ShellOutputRequest cursor(@jakarta.annotation.Nullable String cursor) {
            this.cursor = cursor;
            return this;
        }

        public @jakarta.annotation.Nullable String limit() {
            return this.limit;
        }
        public ShellOutputRequest limit(@jakarta.annotation.Nullable String limit) {
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
            ShellOutputRequest request = (ShellOutputRequest) o;
            return Objects.equals(this.id, request.id()) &&
                Objects.equals(this.location, request.location()) &&
                Objects.equals(this.cursor, request.cursor()) &&
                Objects.equals(this.limit, request.limit());
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, location, cursor, limit);
        }
    }

    /**
     * Read shell output
     * Page through captured combined output by absolute byte cursor.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ShellNotFoundError
     * @param requestParameters The shellOutput request parameters as object
     * @return ShellOutput200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ShellOutput200Response> shellOutput(ShellOutputRequest requestParameters) throws WebClientResponseException {
        return this.shellOutput(requestParameters.id(), requestParameters.location(), requestParameters.cursor(), requestParameters.limit());
    }

    /**
     * Read shell output
     * Page through captured combined output by absolute byte cursor.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ShellNotFoundError
     * @param requestParameters The shellOutput request parameters as object
     * @return ResponseEntity&lt;ShellOutput200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ShellOutput200Response>> shellOutputWithHttpInfo(ShellOutputRequest requestParameters) throws WebClientResponseException {
        return this.shellOutputWithHttpInfo(requestParameters.id(), requestParameters.location(), requestParameters.cursor(), requestParameters.limit());
    }

    /**
     * Read shell output
     * Page through captured combined output by absolute byte cursor.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ShellNotFoundError
     * @param requestParameters The shellOutput request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec shellOutputWithResponseSpec(ShellOutputRequest requestParameters) throws WebClientResponseException {
        return this.shellOutputWithResponseSpec(requestParameters.id(), requestParameters.location(), requestParameters.cursor(), requestParameters.limit());
    }


    /**
     * Read shell output
     * Page through captured combined output by absolute byte cursor.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ShellNotFoundError
     * @param id The id parameter
     * @param location The location parameter
     * @param cursor The cursor parameter
     * @param limit The limit parameter
     * @return ShellOutput200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec shellOutputRequestCreation(@jakarta.annotation.Nonnull String id, @jakarta.annotation.Nullable AgentListLocationParameter location, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String limit) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'id' is set
        if (id == null) {
            throw new WebClientResponseException("Missing the required parameter 'id' when calling shellOutput", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("id", id);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "cursor", cursor));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "limit", limit));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<ShellOutput200Response> localVarReturnType = new ParameterizedTypeReference<ShellOutput200Response>() {};
        return apiClient.invokeAPI("/api/shell/{id}/output", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Read shell output
     * Page through captured combined output by absolute byte cursor.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ShellNotFoundError
     * @param id The id parameter
     * @param location The location parameter
     * @param cursor The cursor parameter
     * @param limit The limit parameter
     * @return ShellOutput200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ShellOutput200Response> shellOutput(@jakarta.annotation.Nonnull String id, @jakarta.annotation.Nullable AgentListLocationParameter location, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String limit) throws WebClientResponseException {
        ParameterizedTypeReference<ShellOutput200Response> localVarReturnType = new ParameterizedTypeReference<ShellOutput200Response>() {};
        return shellOutputRequestCreation(id, location, cursor, limit).bodyToMono(localVarReturnType);
    }

    /**
     * Read shell output
     * Page through captured combined output by absolute byte cursor.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ShellNotFoundError
     * @param id The id parameter
     * @param location The location parameter
     * @param cursor The cursor parameter
     * @param limit The limit parameter
     * @return ResponseEntity&lt;ShellOutput200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ShellOutput200Response>> shellOutputWithHttpInfo(@jakarta.annotation.Nonnull String id, @jakarta.annotation.Nullable AgentListLocationParameter location, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String limit) throws WebClientResponseException {
        ParameterizedTypeReference<ShellOutput200Response> localVarReturnType = new ParameterizedTypeReference<ShellOutput200Response>() {};
        return shellOutputRequestCreation(id, location, cursor, limit).toEntity(localVarReturnType);
    }

    /**
     * Read shell output
     * Page through captured combined output by absolute byte cursor.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ShellNotFoundError
     * @param id The id parameter
     * @param location The location parameter
     * @param cursor The cursor parameter
     * @param limit The limit parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec shellOutputWithResponseSpec(@jakarta.annotation.Nonnull String id, @jakarta.annotation.Nullable AgentListLocationParameter location, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String limit) throws WebClientResponseException {
        return shellOutputRequestCreation(id, location, cursor, limit);
    }

    public class ShellRemoveRequest {
        private @jakarta.annotation.Nonnull String id;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;

        public ShellRemoveRequest() {}

        public ShellRemoveRequest(@jakarta.annotation.Nonnull String id, @jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.id = id;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String id() {
            return this.id;
        }
        public ShellRemoveRequest id(@jakarta.annotation.Nonnull String id) {
            this.id = id;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public ShellRemoveRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
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
            ShellRemoveRequest request = (ShellRemoveRequest) o;
            return Objects.equals(this.id, request.id()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, location);
        }
    }

    /**
     * Remove shell command
     * Terminate and remove one shell command and its retained output.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The shellRemove request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> shellRemove(ShellRemoveRequest requestParameters) throws WebClientResponseException {
        return this.shellRemove(requestParameters.id(), requestParameters.location());
    }

    /**
     * Remove shell command
     * Terminate and remove one shell command and its retained output.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The shellRemove request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> shellRemoveWithHttpInfo(ShellRemoveRequest requestParameters) throws WebClientResponseException {
        return this.shellRemoveWithHttpInfo(requestParameters.id(), requestParameters.location());
    }

    /**
     * Remove shell command
     * Terminate and remove one shell command and its retained output.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The shellRemove request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec shellRemoveWithResponseSpec(ShellRemoveRequest requestParameters) throws WebClientResponseException {
        return this.shellRemoveWithResponseSpec(requestParameters.id(), requestParameters.location());
    }


    /**
     * Remove shell command
     * Terminate and remove one shell command and its retained output.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param id The id parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec shellRemoveRequestCreation(@jakarta.annotation.Nonnull String id, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'id' is set
        if (id == null) {
            throw new WebClientResponseException("Missing the required parameter 'id' when calling shellRemove", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("id", id);

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

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/shell/{id}", HttpMethod.DELETE, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Remove shell command
     * Terminate and remove one shell command and its retained output.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param id The id parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> shellRemove(@jakarta.annotation.Nonnull String id, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return shellRemoveRequestCreation(id, location).bodyToMono(localVarReturnType);
    }

    /**
     * Remove shell command
     * Terminate and remove one shell command and its retained output.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param id The id parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> shellRemoveWithHttpInfo(@jakarta.annotation.Nonnull String id, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return shellRemoveRequestCreation(id, location).toEntity(localVarReturnType);
    }

    /**
     * Remove shell command
     * Terminate and remove one shell command and its retained output.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param id The id parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec shellRemoveWithResponseSpec(@jakarta.annotation.Nonnull String id, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return shellRemoveRequestCreation(id, location);
    }
}
