package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.AuthSet400Response;
import com.example.opencode.sdk.model.BadRequestError;
import com.example.opencode.sdk.model.Project;
import com.example.opencode.sdk.model.ProjectDirectoriesInner;
import com.example.opencode.sdk.model.ProjectNotFoundError;
import com.example.opencode.sdk.model.ProjectUpdateRequest;

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
public class ProjectApi {
    private ApiClient apiClient;

    public ProjectApi() {
        this(new ApiClient());
    }

    public ProjectApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public class ProjectCurrentRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public ProjectCurrentRequest() {}

        public ProjectCurrentRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public ProjectCurrentRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public ProjectCurrentRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            ProjectCurrentRequest request = (ProjectCurrentRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * Get current project
     * Retrieve the currently active project that OpenCode is working with.
     * <p><b>200</b> - Current project information
     * <p><b>400</b> - Bad request
     * @param requestParameters The projectCurrent request parameters as object
     * @return Project
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Project> projectCurrent(ProjectCurrentRequest requestParameters) throws WebClientResponseException {
        return this.projectCurrent(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get current project
     * Retrieve the currently active project that OpenCode is working with.
     * <p><b>200</b> - Current project information
     * <p><b>400</b> - Bad request
     * @param requestParameters The projectCurrent request parameters as object
     * @return ResponseEntity&lt;Project&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Project>> projectCurrentWithHttpInfo(ProjectCurrentRequest requestParameters) throws WebClientResponseException {
        return this.projectCurrentWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get current project
     * Retrieve the currently active project that OpenCode is working with.
     * <p><b>200</b> - Current project information
     * <p><b>400</b> - Bad request
     * @param requestParameters The projectCurrent request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec projectCurrentWithResponseSpec(ProjectCurrentRequest requestParameters) throws WebClientResponseException {
        return this.projectCurrentWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Get current project
     * Retrieve the currently active project that OpenCode is working with.
     * <p><b>200</b> - Current project information
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Project
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec projectCurrentRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<Project> localVarReturnType = new ParameterizedTypeReference<Project>() {};
        return apiClient.invokeAPI("/project/current", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get current project
     * Retrieve the currently active project that OpenCode is working with.
     * <p><b>200</b> - Current project information
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Project
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Project> projectCurrent(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Project> localVarReturnType = new ParameterizedTypeReference<Project>() {};
        return projectCurrentRequestCreation(directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Get current project
     * Retrieve the currently active project that OpenCode is working with.
     * <p><b>200</b> - Current project information
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Project&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Project>> projectCurrentWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Project> localVarReturnType = new ParameterizedTypeReference<Project>() {};
        return projectCurrentRequestCreation(directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Get current project
     * Retrieve the currently active project that OpenCode is working with.
     * <p><b>200</b> - Current project information
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec projectCurrentWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return projectCurrentRequestCreation(directory, workspace);
    }

    public class ProjectDirectoriesRequest {
        private @jakarta.annotation.Nonnull String projectID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public ProjectDirectoriesRequest() {}

        public ProjectDirectoriesRequest(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.projectID = projectID;
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nonnull String projectID() {
            return this.projectID;
        }
        public ProjectDirectoriesRequest projectID(@jakarta.annotation.Nonnull String projectID) {
            this.projectID = projectID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public ProjectDirectoriesRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public ProjectDirectoriesRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            ProjectDirectoriesRequest request = (ProjectDirectoriesRequest) o;
            return Objects.equals(this.projectID, request.projectID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(projectID, directory, workspace);
        }
    }

    /**
     * List project directories
     * List known local absolute directories for a project.
     * <p><b>200</b> - Project directories
     * <p><b>400</b> - Bad request
     * @param requestParameters The projectDirectories request parameters as object
     * @return List&lt;ProjectDirectoriesInner&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<ProjectDirectoriesInner> projectDirectories(ProjectDirectoriesRequest requestParameters) throws WebClientResponseException {
        return this.projectDirectories(requestParameters.projectID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List project directories
     * List known local absolute directories for a project.
     * <p><b>200</b> - Project directories
     * <p><b>400</b> - Bad request
     * @param requestParameters The projectDirectories request parameters as object
     * @return ResponseEntity&lt;List&lt;ProjectDirectoriesInner&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<ProjectDirectoriesInner>>> projectDirectoriesWithHttpInfo(ProjectDirectoriesRequest requestParameters) throws WebClientResponseException {
        return this.projectDirectoriesWithHttpInfo(requestParameters.projectID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List project directories
     * List known local absolute directories for a project.
     * <p><b>200</b> - Project directories
     * <p><b>400</b> - Bad request
     * @param requestParameters The projectDirectories request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec projectDirectoriesWithResponseSpec(ProjectDirectoriesRequest requestParameters) throws WebClientResponseException {
        return this.projectDirectoriesWithResponseSpec(requestParameters.projectID(), requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * List project directories
     * List known local absolute directories for a project.
     * <p><b>200</b> - Project directories
     * <p><b>400</b> - Bad request
     * @param projectID The projectID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;ProjectDirectoriesInner&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec projectDirectoriesRequestCreation(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'projectID' is set
        if (projectID == null) {
            throw new WebClientResponseException("Missing the required parameter 'projectID' when calling projectDirectories", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("projectID", projectID);

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

        ParameterizedTypeReference<ProjectDirectoriesInner> localVarReturnType = new ParameterizedTypeReference<ProjectDirectoriesInner>() {};
        return apiClient.invokeAPI("/project/{projectID}/directories", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List project directories
     * List known local absolute directories for a project.
     * <p><b>200</b> - Project directories
     * <p><b>400</b> - Bad request
     * @param projectID The projectID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;ProjectDirectoriesInner&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<ProjectDirectoriesInner> projectDirectories(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<ProjectDirectoriesInner> localVarReturnType = new ParameterizedTypeReference<ProjectDirectoriesInner>() {};
        return projectDirectoriesRequestCreation(projectID, directory, workspace).bodyToFlux(localVarReturnType);
    }

    /**
     * List project directories
     * List known local absolute directories for a project.
     * <p><b>200</b> - Project directories
     * <p><b>400</b> - Bad request
     * @param projectID The projectID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;List&lt;ProjectDirectoriesInner&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<ProjectDirectoriesInner>>> projectDirectoriesWithHttpInfo(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<ProjectDirectoriesInner> localVarReturnType = new ParameterizedTypeReference<ProjectDirectoriesInner>() {};
        return projectDirectoriesRequestCreation(projectID, directory, workspace).toEntityList(localVarReturnType);
    }

    /**
     * List project directories
     * List known local absolute directories for a project.
     * <p><b>200</b> - Project directories
     * <p><b>400</b> - Bad request
     * @param projectID The projectID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec projectDirectoriesWithResponseSpec(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return projectDirectoriesRequestCreation(projectID, directory, workspace);
    }

    public class ProjectInitGitRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public ProjectInitGitRequest() {}

        public ProjectInitGitRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public ProjectInitGitRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public ProjectInitGitRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            ProjectInitGitRequest request = (ProjectInitGitRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * Initialize git repository
     * Create a git repository for the current project and return the refreshed project info.
     * <p><b>200</b> - Project information after git initialization
     * <p><b>400</b> - Bad request
     * @param requestParameters The projectInitGit request parameters as object
     * @return Project
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Project> projectInitGit(ProjectInitGitRequest requestParameters) throws WebClientResponseException {
        return this.projectInitGit(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Initialize git repository
     * Create a git repository for the current project and return the refreshed project info.
     * <p><b>200</b> - Project information after git initialization
     * <p><b>400</b> - Bad request
     * @param requestParameters The projectInitGit request parameters as object
     * @return ResponseEntity&lt;Project&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Project>> projectInitGitWithHttpInfo(ProjectInitGitRequest requestParameters) throws WebClientResponseException {
        return this.projectInitGitWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Initialize git repository
     * Create a git repository for the current project and return the refreshed project info.
     * <p><b>200</b> - Project information after git initialization
     * <p><b>400</b> - Bad request
     * @param requestParameters The projectInitGit request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec projectInitGitWithResponseSpec(ProjectInitGitRequest requestParameters) throws WebClientResponseException {
        return this.projectInitGitWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Initialize git repository
     * Create a git repository for the current project and return the refreshed project info.
     * <p><b>200</b> - Project information after git initialization
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Project
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec projectInitGitRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<Project> localVarReturnType = new ParameterizedTypeReference<Project>() {};
        return apiClient.invokeAPI("/project/git/init", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Initialize git repository
     * Create a git repository for the current project and return the refreshed project info.
     * <p><b>200</b> - Project information after git initialization
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Project
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Project> projectInitGit(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Project> localVarReturnType = new ParameterizedTypeReference<Project>() {};
        return projectInitGitRequestCreation(directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Initialize git repository
     * Create a git repository for the current project and return the refreshed project info.
     * <p><b>200</b> - Project information after git initialization
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Project&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Project>> projectInitGitWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Project> localVarReturnType = new ParameterizedTypeReference<Project>() {};
        return projectInitGitRequestCreation(directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Initialize git repository
     * Create a git repository for the current project and return the refreshed project info.
     * <p><b>200</b> - Project information after git initialization
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec projectInitGitWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return projectInitGitRequestCreation(directory, workspace);
    }

    public class ProjectListRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public ProjectListRequest() {}

        public ProjectListRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public ProjectListRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public ProjectListRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            ProjectListRequest request = (ProjectListRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * List all projects
     * Get a list of projects that have been opened with OpenCode.
     * <p><b>200</b> - List of projects
     * <p><b>400</b> - Bad request
     * @param requestParameters The projectList request parameters as object
     * @return List&lt;Project&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<Project> projectList(ProjectListRequest requestParameters) throws WebClientResponseException {
        return this.projectList(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List all projects
     * Get a list of projects that have been opened with OpenCode.
     * <p><b>200</b> - List of projects
     * <p><b>400</b> - Bad request
     * @param requestParameters The projectList request parameters as object
     * @return ResponseEntity&lt;List&lt;Project&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<Project>>> projectListWithHttpInfo(ProjectListRequest requestParameters) throws WebClientResponseException {
        return this.projectListWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List all projects
     * Get a list of projects that have been opened with OpenCode.
     * <p><b>200</b> - List of projects
     * <p><b>400</b> - Bad request
     * @param requestParameters The projectList request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec projectListWithResponseSpec(ProjectListRequest requestParameters) throws WebClientResponseException {
        return this.projectListWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * List all projects
     * Get a list of projects that have been opened with OpenCode.
     * <p><b>200</b> - List of projects
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;Project&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec projectListRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<Project> localVarReturnType = new ParameterizedTypeReference<Project>() {};
        return apiClient.invokeAPI("/project", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List all projects
     * Get a list of projects that have been opened with OpenCode.
     * <p><b>200</b> - List of projects
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;Project&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<Project> projectList(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Project> localVarReturnType = new ParameterizedTypeReference<Project>() {};
        return projectListRequestCreation(directory, workspace).bodyToFlux(localVarReturnType);
    }

    /**
     * List all projects
     * Get a list of projects that have been opened with OpenCode.
     * <p><b>200</b> - List of projects
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;List&lt;Project&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<Project>>> projectListWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Project> localVarReturnType = new ParameterizedTypeReference<Project>() {};
        return projectListRequestCreation(directory, workspace).toEntityList(localVarReturnType);
    }

    /**
     * List all projects
     * Get a list of projects that have been opened with OpenCode.
     * <p><b>200</b> - List of projects
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec projectListWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return projectListRequestCreation(directory, workspace);
    }

    public class ProjectUpdateRequest {
        private @jakarta.annotation.Nonnull String projectID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable ProjectUpdateRequest projectUpdateRequest;

        public ProjectUpdateRequest() {}

        public ProjectUpdateRequest(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ProjectUpdateRequest projectUpdateRequest) {
            this.projectID = projectID;
            this.directory = directory;
            this.workspace = workspace;
            this.projectUpdateRequest = projectUpdateRequest;
        }

        public @jakarta.annotation.Nonnull String projectID() {
            return this.projectID;
        }
        public ProjectUpdateRequest projectID(@jakarta.annotation.Nonnull String projectID) {
            this.projectID = projectID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public ProjectUpdateRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public ProjectUpdateRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable ProjectUpdateRequest projectUpdateRequest() {
            return this.projectUpdateRequest;
        }
        public ProjectUpdateRequest projectUpdateRequest(@jakarta.annotation.Nullable ProjectUpdateRequest projectUpdateRequest) {
            this.projectUpdateRequest = projectUpdateRequest;
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
            ProjectUpdateRequest request = (ProjectUpdateRequest) o;
            return Objects.equals(this.projectID, request.projectID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.projectUpdateRequest, request.projectUpdateRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(projectID, directory, workspace, projectUpdateRequest);
        }
    }

    /**
     * Update project
     * Update project properties such as name, icon, and commands.
     * <p><b>200</b> - Updated project information
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - ProjectNotFoundError
     * @param requestParameters The projectUpdate request parameters as object
     * @return Project
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Project> projectUpdate(ProjectUpdateRequest requestParameters) throws WebClientResponseException {
        return this.projectUpdate(requestParameters.projectID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.projectUpdateRequest());
    }

    /**
     * Update project
     * Update project properties such as name, icon, and commands.
     * <p><b>200</b> - Updated project information
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - ProjectNotFoundError
     * @param requestParameters The projectUpdate request parameters as object
     * @return ResponseEntity&lt;Project&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Project>> projectUpdateWithHttpInfo(ProjectUpdateRequest requestParameters) throws WebClientResponseException {
        return this.projectUpdateWithHttpInfo(requestParameters.projectID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.projectUpdateRequest());
    }

    /**
     * Update project
     * Update project properties such as name, icon, and commands.
     * <p><b>200</b> - Updated project information
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - ProjectNotFoundError
     * @param requestParameters The projectUpdate request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec projectUpdateWithResponseSpec(ProjectUpdateRequest requestParameters) throws WebClientResponseException {
        return this.projectUpdateWithResponseSpec(requestParameters.projectID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.projectUpdateRequest());
    }


    /**
     * Update project
     * Update project properties such as name, icon, and commands.
     * <p><b>200</b> - Updated project information
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - ProjectNotFoundError
     * @param projectID The projectID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param projectUpdateRequest The projectUpdateRequest parameter
     * @return Project
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec projectUpdateRequestCreation(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ProjectUpdateRequest projectUpdateRequest) throws WebClientResponseException {
        Object postBody = projectUpdateRequest;
        // verify the required parameter 'projectID' is set
        if (projectID == null) {
            throw new WebClientResponseException("Missing the required parameter 'projectID' when calling projectUpdate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("projectID", projectID);

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

        ParameterizedTypeReference<Project> localVarReturnType = new ParameterizedTypeReference<Project>() {};
        return apiClient.invokeAPI("/project/{projectID}", HttpMethod.PATCH, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Update project
     * Update project properties such as name, icon, and commands.
     * <p><b>200</b> - Updated project information
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - ProjectNotFoundError
     * @param projectID The projectID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param projectUpdateRequest The projectUpdateRequest parameter
     * @return Project
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Project> projectUpdate(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ProjectUpdateRequest projectUpdateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Project> localVarReturnType = new ParameterizedTypeReference<Project>() {};
        return projectUpdateRequestCreation(projectID, directory, workspace, projectUpdateRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Update project
     * Update project properties such as name, icon, and commands.
     * <p><b>200</b> - Updated project information
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - ProjectNotFoundError
     * @param projectID The projectID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param projectUpdateRequest The projectUpdateRequest parameter
     * @return ResponseEntity&lt;Project&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Project>> projectUpdateWithHttpInfo(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ProjectUpdateRequest projectUpdateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Project> localVarReturnType = new ParameterizedTypeReference<Project>() {};
        return projectUpdateRequestCreation(projectID, directory, workspace, projectUpdateRequest).toEntity(localVarReturnType);
    }

    /**
     * Update project
     * Update project properties such as name, icon, and commands.
     * <p><b>200</b> - Updated project information
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - ProjectNotFoundError
     * @param projectID The projectID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param projectUpdateRequest The projectUpdateRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec projectUpdateWithResponseSpec(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ProjectUpdateRequest projectUpdateRequest) throws WebClientResponseException {
        return projectUpdateRequestCreation(projectID, directory, workspace, projectUpdateRequest);
    }
}
