package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.AuthSet400Response;
import com.example.opencode.sdk.model.BadRequestError;
import com.example.opencode.sdk.model.ExperimentalWorkspaceAdapterList200ResponseInner;
import com.example.opencode.sdk.model.ExperimentalWorkspaceCreate400Response;
import com.example.opencode.sdk.model.ExperimentalWorkspaceCreateRequest;
import com.example.opencode.sdk.model.ExperimentalWorkspaceStatus200ResponseInner;
import com.example.opencode.sdk.model.ExperimentalWorkspaceWarp400Response;
import com.example.opencode.sdk.model.ExperimentalWorkspaceWarpRequest;
import com.example.opencode.sdk.model.NotFoundError;
import com.example.opencode.sdk.model.Workspace;

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
public class WorkspaceApi {
    private ApiClient apiClient;

    public WorkspaceApi() {
        this(new ApiClient());
    }

    public WorkspaceApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public class ExperimentalWorkspaceAdapterListRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public ExperimentalWorkspaceAdapterListRequest() {}

        public ExperimentalWorkspaceAdapterListRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public ExperimentalWorkspaceAdapterListRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public ExperimentalWorkspaceAdapterListRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            ExperimentalWorkspaceAdapterListRequest request = (ExperimentalWorkspaceAdapterListRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * List workspace adapters
     * List all available workspace adapters for the current project.
     * <p><b>200</b> - Workspace adapters
     * <p><b>400</b> - Bad request
     * @param requestParameters The experimentalWorkspaceAdapterList request parameters as object
     * @return List&lt;ExperimentalWorkspaceAdapterList200ResponseInner&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<ExperimentalWorkspaceAdapterList200ResponseInner> experimentalWorkspaceAdapterList(ExperimentalWorkspaceAdapterListRequest requestParameters) throws WebClientResponseException {
        return this.experimentalWorkspaceAdapterList(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List workspace adapters
     * List all available workspace adapters for the current project.
     * <p><b>200</b> - Workspace adapters
     * <p><b>400</b> - Bad request
     * @param requestParameters The experimentalWorkspaceAdapterList request parameters as object
     * @return ResponseEntity&lt;List&lt;ExperimentalWorkspaceAdapterList200ResponseInner&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<ExperimentalWorkspaceAdapterList200ResponseInner>>> experimentalWorkspaceAdapterListWithHttpInfo(ExperimentalWorkspaceAdapterListRequest requestParameters) throws WebClientResponseException {
        return this.experimentalWorkspaceAdapterListWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List workspace adapters
     * List all available workspace adapters for the current project.
     * <p><b>200</b> - Workspace adapters
     * <p><b>400</b> - Bad request
     * @param requestParameters The experimentalWorkspaceAdapterList request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalWorkspaceAdapterListWithResponseSpec(ExperimentalWorkspaceAdapterListRequest requestParameters) throws WebClientResponseException {
        return this.experimentalWorkspaceAdapterListWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * List workspace adapters
     * List all available workspace adapters for the current project.
     * <p><b>200</b> - Workspace adapters
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;ExperimentalWorkspaceAdapterList200ResponseInner&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalWorkspaceAdapterListRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<ExperimentalWorkspaceAdapterList200ResponseInner> localVarReturnType = new ParameterizedTypeReference<ExperimentalWorkspaceAdapterList200ResponseInner>() {};
        return apiClient.invokeAPI("/experimental/workspace/adapter", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List workspace adapters
     * List all available workspace adapters for the current project.
     * <p><b>200</b> - Workspace adapters
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;ExperimentalWorkspaceAdapterList200ResponseInner&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<ExperimentalWorkspaceAdapterList200ResponseInner> experimentalWorkspaceAdapterList(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<ExperimentalWorkspaceAdapterList200ResponseInner> localVarReturnType = new ParameterizedTypeReference<ExperimentalWorkspaceAdapterList200ResponseInner>() {};
        return experimentalWorkspaceAdapterListRequestCreation(directory, workspace).bodyToFlux(localVarReturnType);
    }

    /**
     * List workspace adapters
     * List all available workspace adapters for the current project.
     * <p><b>200</b> - Workspace adapters
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;List&lt;ExperimentalWorkspaceAdapterList200ResponseInner&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<ExperimentalWorkspaceAdapterList200ResponseInner>>> experimentalWorkspaceAdapterListWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<ExperimentalWorkspaceAdapterList200ResponseInner> localVarReturnType = new ParameterizedTypeReference<ExperimentalWorkspaceAdapterList200ResponseInner>() {};
        return experimentalWorkspaceAdapterListRequestCreation(directory, workspace).toEntityList(localVarReturnType);
    }

    /**
     * List workspace adapters
     * List all available workspace adapters for the current project.
     * <p><b>200</b> - Workspace adapters
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalWorkspaceAdapterListWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return experimentalWorkspaceAdapterListRequestCreation(directory, workspace);
    }

    public class ExperimentalWorkspaceCreateRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable ExperimentalWorkspaceCreateRequest experimentalWorkspaceCreateRequest;

        public ExperimentalWorkspaceCreateRequest() {}

        public ExperimentalWorkspaceCreateRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ExperimentalWorkspaceCreateRequest experimentalWorkspaceCreateRequest) {
            this.directory = directory;
            this.workspace = workspace;
            this.experimentalWorkspaceCreateRequest = experimentalWorkspaceCreateRequest;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public ExperimentalWorkspaceCreateRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public ExperimentalWorkspaceCreateRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable ExperimentalWorkspaceCreateRequest experimentalWorkspaceCreateRequest() {
            return this.experimentalWorkspaceCreateRequest;
        }
        public ExperimentalWorkspaceCreateRequest experimentalWorkspaceCreateRequest(@jakarta.annotation.Nullable ExperimentalWorkspaceCreateRequest experimentalWorkspaceCreateRequest) {
            this.experimentalWorkspaceCreateRequest = experimentalWorkspaceCreateRequest;
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
            ExperimentalWorkspaceCreateRequest request = (ExperimentalWorkspaceCreateRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.experimentalWorkspaceCreateRequest, request.experimentalWorkspaceCreateRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace, experimentalWorkspaceCreateRequest);
        }
    }

    /**
     * Create workspace
     * Create a workspace for the current project.
     * <p><b>200</b> - Workspace created
     * <p><b>400</b> - WorkspaceCreateError | BadRequest | InvalidRequestError
     * @param requestParameters The experimentalWorkspaceCreate request parameters as object
     * @return Workspace
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Workspace> experimentalWorkspaceCreate(ExperimentalWorkspaceCreateRequest requestParameters) throws WebClientResponseException {
        return this.experimentalWorkspaceCreate(requestParameters.directory(), requestParameters.workspace(), requestParameters.experimentalWorkspaceCreateRequest());
    }

    /**
     * Create workspace
     * Create a workspace for the current project.
     * <p><b>200</b> - Workspace created
     * <p><b>400</b> - WorkspaceCreateError | BadRequest | InvalidRequestError
     * @param requestParameters The experimentalWorkspaceCreate request parameters as object
     * @return ResponseEntity&lt;Workspace&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Workspace>> experimentalWorkspaceCreateWithHttpInfo(ExperimentalWorkspaceCreateRequest requestParameters) throws WebClientResponseException {
        return this.experimentalWorkspaceCreateWithHttpInfo(requestParameters.directory(), requestParameters.workspace(), requestParameters.experimentalWorkspaceCreateRequest());
    }

    /**
     * Create workspace
     * Create a workspace for the current project.
     * <p><b>200</b> - Workspace created
     * <p><b>400</b> - WorkspaceCreateError | BadRequest | InvalidRequestError
     * @param requestParameters The experimentalWorkspaceCreate request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalWorkspaceCreateWithResponseSpec(ExperimentalWorkspaceCreateRequest requestParameters) throws WebClientResponseException {
        return this.experimentalWorkspaceCreateWithResponseSpec(requestParameters.directory(), requestParameters.workspace(), requestParameters.experimentalWorkspaceCreateRequest());
    }


    /**
     * Create workspace
     * Create a workspace for the current project.
     * <p><b>200</b> - Workspace created
     * <p><b>400</b> - WorkspaceCreateError | BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param experimentalWorkspaceCreateRequest The experimentalWorkspaceCreateRequest parameter
     * @return Workspace
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalWorkspaceCreateRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ExperimentalWorkspaceCreateRequest experimentalWorkspaceCreateRequest) throws WebClientResponseException {
        Object postBody = experimentalWorkspaceCreateRequest;
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", directory));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", workspace));

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { 
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Workspace> localVarReturnType = new ParameterizedTypeReference<Workspace>() {};
        return apiClient.invokeAPI("/experimental/workspace", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Create workspace
     * Create a workspace for the current project.
     * <p><b>200</b> - Workspace created
     * <p><b>400</b> - WorkspaceCreateError | BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param experimentalWorkspaceCreateRequest The experimentalWorkspaceCreateRequest parameter
     * @return Workspace
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Workspace> experimentalWorkspaceCreate(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ExperimentalWorkspaceCreateRequest experimentalWorkspaceCreateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Workspace> localVarReturnType = new ParameterizedTypeReference<Workspace>() {};
        return experimentalWorkspaceCreateRequestCreation(directory, workspace, experimentalWorkspaceCreateRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Create workspace
     * Create a workspace for the current project.
     * <p><b>200</b> - Workspace created
     * <p><b>400</b> - WorkspaceCreateError | BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param experimentalWorkspaceCreateRequest The experimentalWorkspaceCreateRequest parameter
     * @return ResponseEntity&lt;Workspace&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Workspace>> experimentalWorkspaceCreateWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ExperimentalWorkspaceCreateRequest experimentalWorkspaceCreateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Workspace> localVarReturnType = new ParameterizedTypeReference<Workspace>() {};
        return experimentalWorkspaceCreateRequestCreation(directory, workspace, experimentalWorkspaceCreateRequest).toEntity(localVarReturnType);
    }

    /**
     * Create workspace
     * Create a workspace for the current project.
     * <p><b>200</b> - Workspace created
     * <p><b>400</b> - WorkspaceCreateError | BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param experimentalWorkspaceCreateRequest The experimentalWorkspaceCreateRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalWorkspaceCreateWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ExperimentalWorkspaceCreateRequest experimentalWorkspaceCreateRequest) throws WebClientResponseException {
        return experimentalWorkspaceCreateRequestCreation(directory, workspace, experimentalWorkspaceCreateRequest);
    }

    public class ExperimentalWorkspaceListRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public ExperimentalWorkspaceListRequest() {}

        public ExperimentalWorkspaceListRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public ExperimentalWorkspaceListRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public ExperimentalWorkspaceListRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            ExperimentalWorkspaceListRequest request = (ExperimentalWorkspaceListRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * List workspaces
     * List all workspaces.
     * <p><b>200</b> - Workspaces
     * <p><b>400</b> - Bad request
     * @param requestParameters The experimentalWorkspaceList request parameters as object
     * @return List&lt;Workspace&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<Workspace> experimentalWorkspaceList(ExperimentalWorkspaceListRequest requestParameters) throws WebClientResponseException {
        return this.experimentalWorkspaceList(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List workspaces
     * List all workspaces.
     * <p><b>200</b> - Workspaces
     * <p><b>400</b> - Bad request
     * @param requestParameters The experimentalWorkspaceList request parameters as object
     * @return ResponseEntity&lt;List&lt;Workspace&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<Workspace>>> experimentalWorkspaceListWithHttpInfo(ExperimentalWorkspaceListRequest requestParameters) throws WebClientResponseException {
        return this.experimentalWorkspaceListWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List workspaces
     * List all workspaces.
     * <p><b>200</b> - Workspaces
     * <p><b>400</b> - Bad request
     * @param requestParameters The experimentalWorkspaceList request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalWorkspaceListWithResponseSpec(ExperimentalWorkspaceListRequest requestParameters) throws WebClientResponseException {
        return this.experimentalWorkspaceListWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * List workspaces
     * List all workspaces.
     * <p><b>200</b> - Workspaces
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;Workspace&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalWorkspaceListRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Workspace> localVarReturnType = new ParameterizedTypeReference<Workspace>() {};
        return apiClient.invokeAPI("/experimental/workspace", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List workspaces
     * List all workspaces.
     * <p><b>200</b> - Workspaces
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;Workspace&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<Workspace> experimentalWorkspaceList(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Workspace> localVarReturnType = new ParameterizedTypeReference<Workspace>() {};
        return experimentalWorkspaceListRequestCreation(directory, workspace).bodyToFlux(localVarReturnType);
    }

    /**
     * List workspaces
     * List all workspaces.
     * <p><b>200</b> - Workspaces
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;List&lt;Workspace&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<Workspace>>> experimentalWorkspaceListWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Workspace> localVarReturnType = new ParameterizedTypeReference<Workspace>() {};
        return experimentalWorkspaceListRequestCreation(directory, workspace).toEntityList(localVarReturnType);
    }

    /**
     * List workspaces
     * List all workspaces.
     * <p><b>200</b> - Workspaces
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalWorkspaceListWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return experimentalWorkspaceListRequestCreation(directory, workspace);
    }

    public class ExperimentalWorkspaceRemoveRequest {
        private @jakarta.annotation.Nullable String id;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public ExperimentalWorkspaceRemoveRequest() {}

        public ExperimentalWorkspaceRemoveRequest(@jakarta.annotation.Nullable String id, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.id = id;
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String id() {
            return this.id;
        }
        public ExperimentalWorkspaceRemoveRequest id(@jakarta.annotation.Nullable String id) {
            this.id = id;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public ExperimentalWorkspaceRemoveRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public ExperimentalWorkspaceRemoveRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            ExperimentalWorkspaceRemoveRequest request = (ExperimentalWorkspaceRemoveRequest) o;
            return Objects.equals(this.id, request.id()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, directory, workspace);
        }
    }

    /**
     * Remove workspace
     * Remove an existing workspace.
     * <p><b>200</b> - Workspace removed
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The experimentalWorkspaceRemove request parameters as object
     * @return Workspace
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Workspace> experimentalWorkspaceRemove(ExperimentalWorkspaceRemoveRequest requestParameters) throws WebClientResponseException {
        return this.experimentalWorkspaceRemove(requestParameters.id(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Remove workspace
     * Remove an existing workspace.
     * <p><b>200</b> - Workspace removed
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The experimentalWorkspaceRemove request parameters as object
     * @return ResponseEntity&lt;Workspace&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Workspace>> experimentalWorkspaceRemoveWithHttpInfo(ExperimentalWorkspaceRemoveRequest requestParameters) throws WebClientResponseException {
        return this.experimentalWorkspaceRemoveWithHttpInfo(requestParameters.id(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Remove workspace
     * Remove an existing workspace.
     * <p><b>200</b> - Workspace removed
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The experimentalWorkspaceRemove request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalWorkspaceRemoveWithResponseSpec(ExperimentalWorkspaceRemoveRequest requestParameters) throws WebClientResponseException {
        return this.experimentalWorkspaceRemoveWithResponseSpec(requestParameters.id(), requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Remove workspace
     * Remove an existing workspace.
     * <p><b>200</b> - Workspace removed
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param id The id parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Workspace
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalWorkspaceRemoveRequestCreation(@jakarta.annotation.Nullable String id, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'id' is set
        if (id == null) {
            throw new WebClientResponseException("Missing the required parameter 'id' when calling experimentalWorkspaceRemove", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("id", id);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", directory));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", workspace));

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Workspace> localVarReturnType = new ParameterizedTypeReference<Workspace>() {};
        return apiClient.invokeAPI("/experimental/workspace/{id}", HttpMethod.DELETE, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Remove workspace
     * Remove an existing workspace.
     * <p><b>200</b> - Workspace removed
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param id The id parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Workspace
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Workspace> experimentalWorkspaceRemove(@jakarta.annotation.Nullable String id, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Workspace> localVarReturnType = new ParameterizedTypeReference<Workspace>() {};
        return experimentalWorkspaceRemoveRequestCreation(id, directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Remove workspace
     * Remove an existing workspace.
     * <p><b>200</b> - Workspace removed
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param id The id parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Workspace&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Workspace>> experimentalWorkspaceRemoveWithHttpInfo(@jakarta.annotation.Nullable String id, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Workspace> localVarReturnType = new ParameterizedTypeReference<Workspace>() {};
        return experimentalWorkspaceRemoveRequestCreation(id, directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Remove workspace
     * Remove an existing workspace.
     * <p><b>200</b> - Workspace removed
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param id The id parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalWorkspaceRemoveWithResponseSpec(@jakarta.annotation.Nullable String id, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return experimentalWorkspaceRemoveRequestCreation(id, directory, workspace);
    }

    public class ExperimentalWorkspaceStatusRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public ExperimentalWorkspaceStatusRequest() {}

        public ExperimentalWorkspaceStatusRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public ExperimentalWorkspaceStatusRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public ExperimentalWorkspaceStatusRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            ExperimentalWorkspaceStatusRequest request = (ExperimentalWorkspaceStatusRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * Workspace status
     * Get connection status for workspaces in the current project.
     * <p><b>200</b> - Workspace status
     * <p><b>400</b> - Bad request
     * @param requestParameters The experimentalWorkspaceStatus request parameters as object
     * @return List&lt;ExperimentalWorkspaceStatus200ResponseInner&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<ExperimentalWorkspaceStatus200ResponseInner> experimentalWorkspaceStatus(ExperimentalWorkspaceStatusRequest requestParameters) throws WebClientResponseException {
        return this.experimentalWorkspaceStatus(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Workspace status
     * Get connection status for workspaces in the current project.
     * <p><b>200</b> - Workspace status
     * <p><b>400</b> - Bad request
     * @param requestParameters The experimentalWorkspaceStatus request parameters as object
     * @return ResponseEntity&lt;List&lt;ExperimentalWorkspaceStatus200ResponseInner&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<ExperimentalWorkspaceStatus200ResponseInner>>> experimentalWorkspaceStatusWithHttpInfo(ExperimentalWorkspaceStatusRequest requestParameters) throws WebClientResponseException {
        return this.experimentalWorkspaceStatusWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Workspace status
     * Get connection status for workspaces in the current project.
     * <p><b>200</b> - Workspace status
     * <p><b>400</b> - Bad request
     * @param requestParameters The experimentalWorkspaceStatus request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalWorkspaceStatusWithResponseSpec(ExperimentalWorkspaceStatusRequest requestParameters) throws WebClientResponseException {
        return this.experimentalWorkspaceStatusWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Workspace status
     * Get connection status for workspaces in the current project.
     * <p><b>200</b> - Workspace status
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;ExperimentalWorkspaceStatus200ResponseInner&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalWorkspaceStatusRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<ExperimentalWorkspaceStatus200ResponseInner> localVarReturnType = new ParameterizedTypeReference<ExperimentalWorkspaceStatus200ResponseInner>() {};
        return apiClient.invokeAPI("/experimental/workspace/status", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Workspace status
     * Get connection status for workspaces in the current project.
     * <p><b>200</b> - Workspace status
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;ExperimentalWorkspaceStatus200ResponseInner&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<ExperimentalWorkspaceStatus200ResponseInner> experimentalWorkspaceStatus(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<ExperimentalWorkspaceStatus200ResponseInner> localVarReturnType = new ParameterizedTypeReference<ExperimentalWorkspaceStatus200ResponseInner>() {};
        return experimentalWorkspaceStatusRequestCreation(directory, workspace).bodyToFlux(localVarReturnType);
    }

    /**
     * Workspace status
     * Get connection status for workspaces in the current project.
     * <p><b>200</b> - Workspace status
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;List&lt;ExperimentalWorkspaceStatus200ResponseInner&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<ExperimentalWorkspaceStatus200ResponseInner>>> experimentalWorkspaceStatusWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<ExperimentalWorkspaceStatus200ResponseInner> localVarReturnType = new ParameterizedTypeReference<ExperimentalWorkspaceStatus200ResponseInner>() {};
        return experimentalWorkspaceStatusRequestCreation(directory, workspace).toEntityList(localVarReturnType);
    }

    /**
     * Workspace status
     * Get connection status for workspaces in the current project.
     * <p><b>200</b> - Workspace status
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalWorkspaceStatusWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return experimentalWorkspaceStatusRequestCreation(directory, workspace);
    }

    public class ExperimentalWorkspaceSyncListRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public ExperimentalWorkspaceSyncListRequest() {}

        public ExperimentalWorkspaceSyncListRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public ExperimentalWorkspaceSyncListRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public ExperimentalWorkspaceSyncListRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            ExperimentalWorkspaceSyncListRequest request = (ExperimentalWorkspaceSyncListRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * Sync workspace list
     * Register missing workspaces returned by workspace adapters.
     * <p><b>204</b> - Workspace list synced
     * <p><b>400</b> - Bad request
     * @param requestParameters The experimentalWorkspaceSyncList request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> experimentalWorkspaceSyncList(ExperimentalWorkspaceSyncListRequest requestParameters) throws WebClientResponseException {
        return this.experimentalWorkspaceSyncList(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Sync workspace list
     * Register missing workspaces returned by workspace adapters.
     * <p><b>204</b> - Workspace list synced
     * <p><b>400</b> - Bad request
     * @param requestParameters The experimentalWorkspaceSyncList request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> experimentalWorkspaceSyncListWithHttpInfo(ExperimentalWorkspaceSyncListRequest requestParameters) throws WebClientResponseException {
        return this.experimentalWorkspaceSyncListWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Sync workspace list
     * Register missing workspaces returned by workspace adapters.
     * <p><b>204</b> - Workspace list synced
     * <p><b>400</b> - Bad request
     * @param requestParameters The experimentalWorkspaceSyncList request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalWorkspaceSyncListWithResponseSpec(ExperimentalWorkspaceSyncListRequest requestParameters) throws WebClientResponseException {
        return this.experimentalWorkspaceSyncListWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Sync workspace list
     * Register missing workspaces returned by workspace adapters.
     * <p><b>204</b> - Workspace list synced
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalWorkspaceSyncListRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/experimental/workspace/sync-list", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Sync workspace list
     * Register missing workspaces returned by workspace adapters.
     * <p><b>204</b> - Workspace list synced
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> experimentalWorkspaceSyncList(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return experimentalWorkspaceSyncListRequestCreation(directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Sync workspace list
     * Register missing workspaces returned by workspace adapters.
     * <p><b>204</b> - Workspace list synced
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> experimentalWorkspaceSyncListWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return experimentalWorkspaceSyncListRequestCreation(directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Sync workspace list
     * Register missing workspaces returned by workspace adapters.
     * <p><b>204</b> - Workspace list synced
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalWorkspaceSyncListWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return experimentalWorkspaceSyncListRequestCreation(directory, workspace);
    }

    public class ExperimentalWorkspaceWarpRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable ExperimentalWorkspaceWarpRequest experimentalWorkspaceWarpRequest;

        public ExperimentalWorkspaceWarpRequest() {}

        public ExperimentalWorkspaceWarpRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ExperimentalWorkspaceWarpRequest experimentalWorkspaceWarpRequest) {
            this.directory = directory;
            this.workspace = workspace;
            this.experimentalWorkspaceWarpRequest = experimentalWorkspaceWarpRequest;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public ExperimentalWorkspaceWarpRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public ExperimentalWorkspaceWarpRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable ExperimentalWorkspaceWarpRequest experimentalWorkspaceWarpRequest() {
            return this.experimentalWorkspaceWarpRequest;
        }
        public ExperimentalWorkspaceWarpRequest experimentalWorkspaceWarpRequest(@jakarta.annotation.Nullable ExperimentalWorkspaceWarpRequest experimentalWorkspaceWarpRequest) {
            this.experimentalWorkspaceWarpRequest = experimentalWorkspaceWarpRequest;
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
            ExperimentalWorkspaceWarpRequest request = (ExperimentalWorkspaceWarpRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.experimentalWorkspaceWarpRequest, request.experimentalWorkspaceWarpRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace, experimentalWorkspaceWarpRequest);
        }
    }

    /**
     * Warp session into workspace
     * Move a session&#39;s sync history into the target workspace, or detach it to the local project.
     * <p><b>204</b> - Session warped
     * <p><b>400</b> - WorkspaceWarpError | VcsApplyError | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The experimentalWorkspaceWarp request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> experimentalWorkspaceWarp(ExperimentalWorkspaceWarpRequest requestParameters) throws WebClientResponseException {
        return this.experimentalWorkspaceWarp(requestParameters.directory(), requestParameters.workspace(), requestParameters.experimentalWorkspaceWarpRequest());
    }

    /**
     * Warp session into workspace
     * Move a session&#39;s sync history into the target workspace, or detach it to the local project.
     * <p><b>204</b> - Session warped
     * <p><b>400</b> - WorkspaceWarpError | VcsApplyError | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The experimentalWorkspaceWarp request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> experimentalWorkspaceWarpWithHttpInfo(ExperimentalWorkspaceWarpRequest requestParameters) throws WebClientResponseException {
        return this.experimentalWorkspaceWarpWithHttpInfo(requestParameters.directory(), requestParameters.workspace(), requestParameters.experimentalWorkspaceWarpRequest());
    }

    /**
     * Warp session into workspace
     * Move a session&#39;s sync history into the target workspace, or detach it to the local project.
     * <p><b>204</b> - Session warped
     * <p><b>400</b> - WorkspaceWarpError | VcsApplyError | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The experimentalWorkspaceWarp request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalWorkspaceWarpWithResponseSpec(ExperimentalWorkspaceWarpRequest requestParameters) throws WebClientResponseException {
        return this.experimentalWorkspaceWarpWithResponseSpec(requestParameters.directory(), requestParameters.workspace(), requestParameters.experimentalWorkspaceWarpRequest());
    }


    /**
     * Warp session into workspace
     * Move a session&#39;s sync history into the target workspace, or detach it to the local project.
     * <p><b>204</b> - Session warped
     * <p><b>400</b> - WorkspaceWarpError | VcsApplyError | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param experimentalWorkspaceWarpRequest The experimentalWorkspaceWarpRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalWorkspaceWarpRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ExperimentalWorkspaceWarpRequest experimentalWorkspaceWarpRequest) throws WebClientResponseException {
        Object postBody = experimentalWorkspaceWarpRequest;
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", directory));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", workspace));

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
        return apiClient.invokeAPI("/experimental/workspace/warp", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Warp session into workspace
     * Move a session&#39;s sync history into the target workspace, or detach it to the local project.
     * <p><b>204</b> - Session warped
     * <p><b>400</b> - WorkspaceWarpError | VcsApplyError | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param experimentalWorkspaceWarpRequest The experimentalWorkspaceWarpRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> experimentalWorkspaceWarp(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ExperimentalWorkspaceWarpRequest experimentalWorkspaceWarpRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return experimentalWorkspaceWarpRequestCreation(directory, workspace, experimentalWorkspaceWarpRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Warp session into workspace
     * Move a session&#39;s sync history into the target workspace, or detach it to the local project.
     * <p><b>204</b> - Session warped
     * <p><b>400</b> - WorkspaceWarpError | VcsApplyError | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param experimentalWorkspaceWarpRequest The experimentalWorkspaceWarpRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> experimentalWorkspaceWarpWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ExperimentalWorkspaceWarpRequest experimentalWorkspaceWarpRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return experimentalWorkspaceWarpRequestCreation(directory, workspace, experimentalWorkspaceWarpRequest).toEntity(localVarReturnType);
    }

    /**
     * Warp session into workspace
     * Move a session&#39;s sync history into the target workspace, or detach it to the local project.
     * <p><b>204</b> - Session warped
     * <p><b>400</b> - WorkspaceWarpError | VcsApplyError | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param experimentalWorkspaceWarpRequest The experimentalWorkspaceWarpRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalWorkspaceWarpWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ExperimentalWorkspaceWarpRequest experimentalWorkspaceWarpRequest) throws WebClientResponseException {
        return experimentalWorkspaceWarpRequestCreation(directory, workspace, experimentalWorkspaceWarpRequest);
    }
}
