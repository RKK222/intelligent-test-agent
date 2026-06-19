package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.BadRequestError;
import com.example.opencode.sdk.model.ExperimentalProjectCopyGenerateName200Response;
import com.example.opencode.sdk.model.ExperimentalProjectCopyGenerateNameRequest;
import com.example.opencode.sdk.model.ProjectCopyCopy;
import com.example.opencode.sdk.model.V2AgentListLocationParameter;
import com.example.opencode.sdk.model.V2ProjectCopyCreate400Response;
import com.example.opencode.sdk.model.V2ProjectCopyCreateRequest;
import com.example.opencode.sdk.model.V2ProjectCopyRemoveRequest;

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
public class ProjectCopyApi {
    private ApiClient apiClient;

    public ProjectCopyApi() {
        this(new ApiClient());
    }

    public ProjectCopyApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public class ExperimentalProjectCopyGenerateNameRequest {
        private @jakarta.annotation.Nonnull String projectID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable ExperimentalProjectCopyGenerateNameRequest experimentalProjectCopyGenerateNameRequest;

        public ExperimentalProjectCopyGenerateNameRequest() {}

        public ExperimentalProjectCopyGenerateNameRequest(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ExperimentalProjectCopyGenerateNameRequest experimentalProjectCopyGenerateNameRequest) {
            this.projectID = projectID;
            this.directory = directory;
            this.workspace = workspace;
            this.experimentalProjectCopyGenerateNameRequest = experimentalProjectCopyGenerateNameRequest;
        }

        public @jakarta.annotation.Nonnull String projectID() {
            return this.projectID;
        }
        public ExperimentalProjectCopyGenerateNameRequest projectID(@jakarta.annotation.Nonnull String projectID) {
            this.projectID = projectID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public ExperimentalProjectCopyGenerateNameRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public ExperimentalProjectCopyGenerateNameRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable ExperimentalProjectCopyGenerateNameRequest experimentalProjectCopyGenerateNameRequest() {
            return this.experimentalProjectCopyGenerateNameRequest;
        }
        public ExperimentalProjectCopyGenerateNameRequest experimentalProjectCopyGenerateNameRequest(@jakarta.annotation.Nullable ExperimentalProjectCopyGenerateNameRequest experimentalProjectCopyGenerateNameRequest) {
            this.experimentalProjectCopyGenerateNameRequest = experimentalProjectCopyGenerateNameRequest;
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
            ExperimentalProjectCopyGenerateNameRequest request = (ExperimentalProjectCopyGenerateNameRequest) o;
            return Objects.equals(this.projectID, request.projectID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.experimentalProjectCopyGenerateNameRequest, request.experimentalProjectCopyGenerateNameRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(projectID, directory, workspace, experimentalProjectCopyGenerateNameRequest);
        }
    }

    /**
     * Generate project copy name
     * Generate a short name for a project copy from task context.
     * <p><b>200</b> - Success
     * <p><b>400</b> - Bad request
     * @param requestParameters The experimentalProjectCopyGenerateName request parameters as object
     * @return ExperimentalProjectCopyGenerateName200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ExperimentalProjectCopyGenerateName200Response> experimentalProjectCopyGenerateName(ExperimentalProjectCopyGenerateNameRequest requestParameters) throws WebClientResponseException {
        return this.experimentalProjectCopyGenerateName(requestParameters.projectID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.experimentalProjectCopyGenerateNameRequest());
    }

    /**
     * Generate project copy name
     * Generate a short name for a project copy from task context.
     * <p><b>200</b> - Success
     * <p><b>400</b> - Bad request
     * @param requestParameters The experimentalProjectCopyGenerateName request parameters as object
     * @return ResponseEntity&lt;ExperimentalProjectCopyGenerateName200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ExperimentalProjectCopyGenerateName200Response>> experimentalProjectCopyGenerateNameWithHttpInfo(ExperimentalProjectCopyGenerateNameRequest requestParameters) throws WebClientResponseException {
        return this.experimentalProjectCopyGenerateNameWithHttpInfo(requestParameters.projectID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.experimentalProjectCopyGenerateNameRequest());
    }

    /**
     * Generate project copy name
     * Generate a short name for a project copy from task context.
     * <p><b>200</b> - Success
     * <p><b>400</b> - Bad request
     * @param requestParameters The experimentalProjectCopyGenerateName request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalProjectCopyGenerateNameWithResponseSpec(ExperimentalProjectCopyGenerateNameRequest requestParameters) throws WebClientResponseException {
        return this.experimentalProjectCopyGenerateNameWithResponseSpec(requestParameters.projectID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.experimentalProjectCopyGenerateNameRequest());
    }


    /**
     * Generate project copy name
     * Generate a short name for a project copy from task context.
     * <p><b>200</b> - Success
     * <p><b>400</b> - Bad request
     * @param projectID The projectID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param experimentalProjectCopyGenerateNameRequest The experimentalProjectCopyGenerateNameRequest parameter
     * @return ExperimentalProjectCopyGenerateName200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalProjectCopyGenerateNameRequestCreation(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ExperimentalProjectCopyGenerateNameRequest experimentalProjectCopyGenerateNameRequest) throws WebClientResponseException {
        Object postBody = experimentalProjectCopyGenerateNameRequest;
        // verify the required parameter 'projectID' is set
        if (projectID == null) {
            throw new WebClientResponseException("Missing the required parameter 'projectID' when calling experimentalProjectCopyGenerateName", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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

        ParameterizedTypeReference<ExperimentalProjectCopyGenerateName200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalProjectCopyGenerateName200Response>() {};
        return apiClient.invokeAPI("/experimental/project/{projectID}/copy/generate-name", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Generate project copy name
     * Generate a short name for a project copy from task context.
     * <p><b>200</b> - Success
     * <p><b>400</b> - Bad request
     * @param projectID The projectID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param experimentalProjectCopyGenerateNameRequest The experimentalProjectCopyGenerateNameRequest parameter
     * @return ExperimentalProjectCopyGenerateName200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ExperimentalProjectCopyGenerateName200Response> experimentalProjectCopyGenerateName(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ExperimentalProjectCopyGenerateNameRequest experimentalProjectCopyGenerateNameRequest) throws WebClientResponseException {
        ParameterizedTypeReference<ExperimentalProjectCopyGenerateName200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalProjectCopyGenerateName200Response>() {};
        return experimentalProjectCopyGenerateNameRequestCreation(projectID, directory, workspace, experimentalProjectCopyGenerateNameRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Generate project copy name
     * Generate a short name for a project copy from task context.
     * <p><b>200</b> - Success
     * <p><b>400</b> - Bad request
     * @param projectID The projectID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param experimentalProjectCopyGenerateNameRequest The experimentalProjectCopyGenerateNameRequest parameter
     * @return ResponseEntity&lt;ExperimentalProjectCopyGenerateName200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ExperimentalProjectCopyGenerateName200Response>> experimentalProjectCopyGenerateNameWithHttpInfo(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ExperimentalProjectCopyGenerateNameRequest experimentalProjectCopyGenerateNameRequest) throws WebClientResponseException {
        ParameterizedTypeReference<ExperimentalProjectCopyGenerateName200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalProjectCopyGenerateName200Response>() {};
        return experimentalProjectCopyGenerateNameRequestCreation(projectID, directory, workspace, experimentalProjectCopyGenerateNameRequest).toEntity(localVarReturnType);
    }

    /**
     * Generate project copy name
     * Generate a short name for a project copy from task context.
     * <p><b>200</b> - Success
     * <p><b>400</b> - Bad request
     * @param projectID The projectID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param experimentalProjectCopyGenerateNameRequest The experimentalProjectCopyGenerateNameRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalProjectCopyGenerateNameWithResponseSpec(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ExperimentalProjectCopyGenerateNameRequest experimentalProjectCopyGenerateNameRequest) throws WebClientResponseException {
        return experimentalProjectCopyGenerateNameRequestCreation(projectID, directory, workspace, experimentalProjectCopyGenerateNameRequest);
    }

    public class V2ProjectCopyCreateRequest {
        private @jakarta.annotation.Nonnull String projectID;
        private @jakarta.annotation.Nullable V2AgentListLocationParameter location;
        private @jakarta.annotation.Nullable V2ProjectCopyCreateRequest v2ProjectCopyCreateRequest;

        public V2ProjectCopyCreateRequest() {}

        public V2ProjectCopyCreateRequest(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable V2AgentListLocationParameter location, @jakarta.annotation.Nullable V2ProjectCopyCreateRequest v2ProjectCopyCreateRequest) {
            this.projectID = projectID;
            this.location = location;
            this.v2ProjectCopyCreateRequest = v2ProjectCopyCreateRequest;
        }

        public @jakarta.annotation.Nonnull String projectID() {
            return this.projectID;
        }
        public V2ProjectCopyCreateRequest projectID(@jakarta.annotation.Nonnull String projectID) {
            this.projectID = projectID;
            return this;
        }

        public @jakarta.annotation.Nullable V2AgentListLocationParameter location() {
            return this.location;
        }
        public V2ProjectCopyCreateRequest location(@jakarta.annotation.Nullable V2AgentListLocationParameter location) {
            this.location = location;
            return this;
        }

        public @jakarta.annotation.Nullable V2ProjectCopyCreateRequest v2ProjectCopyCreateRequest() {
            return this.v2ProjectCopyCreateRequest;
        }
        public V2ProjectCopyCreateRequest v2ProjectCopyCreateRequest(@jakarta.annotation.Nullable V2ProjectCopyCreateRequest v2ProjectCopyCreateRequest) {
            this.v2ProjectCopyCreateRequest = v2ProjectCopyCreateRequest;
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
            V2ProjectCopyCreateRequest request = (V2ProjectCopyCreateRequest) o;
            return Objects.equals(this.projectID, request.projectID()) &&
                Objects.equals(this.location, request.location()) &&
                Objects.equals(this.v2ProjectCopyCreateRequest, request.v2ProjectCopyCreateRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(projectID, location, v2ProjectCopyCreateRequest);
        }
    }

    /**
     * 
     * 
     * <p><b>200</b> - ProjectCopy.Copy
     * <p><b>400</b> - ProjectCopyError | InvalidRequestError
     * @param requestParameters The v2ProjectCopyCreate request parameters as object
     * @return ProjectCopyCopy
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ProjectCopyCopy> v2ProjectCopyCreate(V2ProjectCopyCreateRequest requestParameters) throws WebClientResponseException {
        return this.v2ProjectCopyCreate(requestParameters.projectID(), requestParameters.location(), requestParameters.v2ProjectCopyCreateRequest());
    }

    /**
     * 
     * 
     * <p><b>200</b> - ProjectCopy.Copy
     * <p><b>400</b> - ProjectCopyError | InvalidRequestError
     * @param requestParameters The v2ProjectCopyCreate request parameters as object
     * @return ResponseEntity&lt;ProjectCopyCopy&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ProjectCopyCopy>> v2ProjectCopyCreateWithHttpInfo(V2ProjectCopyCreateRequest requestParameters) throws WebClientResponseException {
        return this.v2ProjectCopyCreateWithHttpInfo(requestParameters.projectID(), requestParameters.location(), requestParameters.v2ProjectCopyCreateRequest());
    }

    /**
     * 
     * 
     * <p><b>200</b> - ProjectCopy.Copy
     * <p><b>400</b> - ProjectCopyError | InvalidRequestError
     * @param requestParameters The v2ProjectCopyCreate request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2ProjectCopyCreateWithResponseSpec(V2ProjectCopyCreateRequest requestParameters) throws WebClientResponseException {
        return this.v2ProjectCopyCreateWithResponseSpec(requestParameters.projectID(), requestParameters.location(), requestParameters.v2ProjectCopyCreateRequest());
    }


    /**
     * 
     * 
     * <p><b>200</b> - ProjectCopy.Copy
     * <p><b>400</b> - ProjectCopyError | InvalidRequestError
     * @param projectID The projectID parameter
     * @param location The location parameter
     * @param v2ProjectCopyCreateRequest The v2ProjectCopyCreateRequest parameter
     * @return ProjectCopyCopy
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2ProjectCopyCreateRequestCreation(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable V2AgentListLocationParameter location, @jakarta.annotation.Nullable V2ProjectCopyCreateRequest v2ProjectCopyCreateRequest) throws WebClientResponseException {
        Object postBody = v2ProjectCopyCreateRequest;
        // verify the required parameter 'projectID' is set
        if (projectID == null) {
            throw new WebClientResponseException("Missing the required parameter 'projectID' when calling v2ProjectCopyCreate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("projectID", projectID);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", location.getWorkspace()));

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { 
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<ProjectCopyCopy> localVarReturnType = new ParameterizedTypeReference<ProjectCopyCopy>() {};
        return apiClient.invokeAPI("/experimental/project/{projectID}/copy", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * 
     * 
     * <p><b>200</b> - ProjectCopy.Copy
     * <p><b>400</b> - ProjectCopyError | InvalidRequestError
     * @param projectID The projectID parameter
     * @param location The location parameter
     * @param v2ProjectCopyCreateRequest The v2ProjectCopyCreateRequest parameter
     * @return ProjectCopyCopy
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ProjectCopyCopy> v2ProjectCopyCreate(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable V2AgentListLocationParameter location, @jakarta.annotation.Nullable V2ProjectCopyCreateRequest v2ProjectCopyCreateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<ProjectCopyCopy> localVarReturnType = new ParameterizedTypeReference<ProjectCopyCopy>() {};
        return v2ProjectCopyCreateRequestCreation(projectID, location, v2ProjectCopyCreateRequest).bodyToMono(localVarReturnType);
    }

    /**
     * 
     * 
     * <p><b>200</b> - ProjectCopy.Copy
     * <p><b>400</b> - ProjectCopyError | InvalidRequestError
     * @param projectID The projectID parameter
     * @param location The location parameter
     * @param v2ProjectCopyCreateRequest The v2ProjectCopyCreateRequest parameter
     * @return ResponseEntity&lt;ProjectCopyCopy&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ProjectCopyCopy>> v2ProjectCopyCreateWithHttpInfo(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable V2AgentListLocationParameter location, @jakarta.annotation.Nullable V2ProjectCopyCreateRequest v2ProjectCopyCreateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<ProjectCopyCopy> localVarReturnType = new ParameterizedTypeReference<ProjectCopyCopy>() {};
        return v2ProjectCopyCreateRequestCreation(projectID, location, v2ProjectCopyCreateRequest).toEntity(localVarReturnType);
    }

    /**
     * 
     * 
     * <p><b>200</b> - ProjectCopy.Copy
     * <p><b>400</b> - ProjectCopyError | InvalidRequestError
     * @param projectID The projectID parameter
     * @param location The location parameter
     * @param v2ProjectCopyCreateRequest The v2ProjectCopyCreateRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2ProjectCopyCreateWithResponseSpec(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable V2AgentListLocationParameter location, @jakarta.annotation.Nullable V2ProjectCopyCreateRequest v2ProjectCopyCreateRequest) throws WebClientResponseException {
        return v2ProjectCopyCreateRequestCreation(projectID, location, v2ProjectCopyCreateRequest);
    }

    public class V2ProjectCopyRefreshRequest {
        private @jakarta.annotation.Nonnull String projectID;
        private @jakarta.annotation.Nullable V2AgentListLocationParameter location;

        public V2ProjectCopyRefreshRequest() {}

        public V2ProjectCopyRefreshRequest(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) {
            this.projectID = projectID;
            this.location = location;
        }

        public @jakarta.annotation.Nonnull String projectID() {
            return this.projectID;
        }
        public V2ProjectCopyRefreshRequest projectID(@jakarta.annotation.Nonnull String projectID) {
            this.projectID = projectID;
            return this;
        }

        public @jakarta.annotation.Nullable V2AgentListLocationParameter location() {
            return this.location;
        }
        public V2ProjectCopyRefreshRequest location(@jakarta.annotation.Nullable V2AgentListLocationParameter location) {
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
            V2ProjectCopyRefreshRequest request = (V2ProjectCopyRefreshRequest) o;
            return Objects.equals(this.projectID, request.projectID()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(projectID, location);
        }
    }

    /**
     * 
     * 
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - ProjectCopyError | InvalidRequestError
     * @param requestParameters The v2ProjectCopyRefresh request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2ProjectCopyRefresh(V2ProjectCopyRefreshRequest requestParameters) throws WebClientResponseException {
        return this.v2ProjectCopyRefresh(requestParameters.projectID(), requestParameters.location());
    }

    /**
     * 
     * 
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - ProjectCopyError | InvalidRequestError
     * @param requestParameters The v2ProjectCopyRefresh request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2ProjectCopyRefreshWithHttpInfo(V2ProjectCopyRefreshRequest requestParameters) throws WebClientResponseException {
        return this.v2ProjectCopyRefreshWithHttpInfo(requestParameters.projectID(), requestParameters.location());
    }

    /**
     * 
     * 
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - ProjectCopyError | InvalidRequestError
     * @param requestParameters The v2ProjectCopyRefresh request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2ProjectCopyRefreshWithResponseSpec(V2ProjectCopyRefreshRequest requestParameters) throws WebClientResponseException {
        return this.v2ProjectCopyRefreshWithResponseSpec(requestParameters.projectID(), requestParameters.location());
    }


    /**
     * 
     * 
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - ProjectCopyError | InvalidRequestError
     * @param projectID The projectID parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2ProjectCopyRefreshRequestCreation(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'projectID' is set
        if (projectID == null) {
            throw new WebClientResponseException("Missing the required parameter 'projectID' when calling v2ProjectCopyRefresh", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("projectID", projectID);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", location.getWorkspace()));

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/experimental/project/{projectID}/copy/refresh", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * 
     * 
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - ProjectCopyError | InvalidRequestError
     * @param projectID The projectID parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2ProjectCopyRefresh(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2ProjectCopyRefreshRequestCreation(projectID, location).bodyToMono(localVarReturnType);
    }

    /**
     * 
     * 
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - ProjectCopyError | InvalidRequestError
     * @param projectID The projectID parameter
     * @param location The location parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2ProjectCopyRefreshWithHttpInfo(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2ProjectCopyRefreshRequestCreation(projectID, location).toEntity(localVarReturnType);
    }

    /**
     * 
     * 
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - ProjectCopyError | InvalidRequestError
     * @param projectID The projectID parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2ProjectCopyRefreshWithResponseSpec(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        return v2ProjectCopyRefreshRequestCreation(projectID, location);
    }

    public class V2ProjectCopyRemoveRequest {
        private @jakarta.annotation.Nonnull String projectID;
        private @jakarta.annotation.Nullable V2AgentListLocationParameter location;
        private @jakarta.annotation.Nullable V2ProjectCopyRemoveRequest v2ProjectCopyRemoveRequest;

        public V2ProjectCopyRemoveRequest() {}

        public V2ProjectCopyRemoveRequest(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable V2AgentListLocationParameter location, @jakarta.annotation.Nullable V2ProjectCopyRemoveRequest v2ProjectCopyRemoveRequest) {
            this.projectID = projectID;
            this.location = location;
            this.v2ProjectCopyRemoveRequest = v2ProjectCopyRemoveRequest;
        }

        public @jakarta.annotation.Nonnull String projectID() {
            return this.projectID;
        }
        public V2ProjectCopyRemoveRequest projectID(@jakarta.annotation.Nonnull String projectID) {
            this.projectID = projectID;
            return this;
        }

        public @jakarta.annotation.Nullable V2AgentListLocationParameter location() {
            return this.location;
        }
        public V2ProjectCopyRemoveRequest location(@jakarta.annotation.Nullable V2AgentListLocationParameter location) {
            this.location = location;
            return this;
        }

        public @jakarta.annotation.Nullable V2ProjectCopyRemoveRequest v2ProjectCopyRemoveRequest() {
            return this.v2ProjectCopyRemoveRequest;
        }
        public V2ProjectCopyRemoveRequest v2ProjectCopyRemoveRequest(@jakarta.annotation.Nullable V2ProjectCopyRemoveRequest v2ProjectCopyRemoveRequest) {
            this.v2ProjectCopyRemoveRequest = v2ProjectCopyRemoveRequest;
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
            V2ProjectCopyRemoveRequest request = (V2ProjectCopyRemoveRequest) o;
            return Objects.equals(this.projectID, request.projectID()) &&
                Objects.equals(this.location, request.location()) &&
                Objects.equals(this.v2ProjectCopyRemoveRequest, request.v2ProjectCopyRemoveRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(projectID, location, v2ProjectCopyRemoveRequest);
        }
    }

    /**
     * 
     * 
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - ProjectCopyError | InvalidRequestError
     * @param requestParameters The v2ProjectCopyRemove request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2ProjectCopyRemove(V2ProjectCopyRemoveRequest requestParameters) throws WebClientResponseException {
        return this.v2ProjectCopyRemove(requestParameters.projectID(), requestParameters.location(), requestParameters.v2ProjectCopyRemoveRequest());
    }

    /**
     * 
     * 
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - ProjectCopyError | InvalidRequestError
     * @param requestParameters The v2ProjectCopyRemove request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2ProjectCopyRemoveWithHttpInfo(V2ProjectCopyRemoveRequest requestParameters) throws WebClientResponseException {
        return this.v2ProjectCopyRemoveWithHttpInfo(requestParameters.projectID(), requestParameters.location(), requestParameters.v2ProjectCopyRemoveRequest());
    }

    /**
     * 
     * 
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - ProjectCopyError | InvalidRequestError
     * @param requestParameters The v2ProjectCopyRemove request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2ProjectCopyRemoveWithResponseSpec(V2ProjectCopyRemoveRequest requestParameters) throws WebClientResponseException {
        return this.v2ProjectCopyRemoveWithResponseSpec(requestParameters.projectID(), requestParameters.location(), requestParameters.v2ProjectCopyRemoveRequest());
    }


    /**
     * 
     * 
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - ProjectCopyError | InvalidRequestError
     * @param projectID The projectID parameter
     * @param location The location parameter
     * @param v2ProjectCopyRemoveRequest The v2ProjectCopyRemoveRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2ProjectCopyRemoveRequestCreation(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable V2AgentListLocationParameter location, @jakarta.annotation.Nullable V2ProjectCopyRemoveRequest v2ProjectCopyRemoveRequest) throws WebClientResponseException {
        Object postBody = v2ProjectCopyRemoveRequest;
        // verify the required parameter 'projectID' is set
        if (projectID == null) {
            throw new WebClientResponseException("Missing the required parameter 'projectID' when calling v2ProjectCopyRemove", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("projectID", projectID);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", location.getWorkspace()));

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
        return apiClient.invokeAPI("/experimental/project/{projectID}/copy", HttpMethod.DELETE, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * 
     * 
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - ProjectCopyError | InvalidRequestError
     * @param projectID The projectID parameter
     * @param location The location parameter
     * @param v2ProjectCopyRemoveRequest The v2ProjectCopyRemoveRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2ProjectCopyRemove(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable V2AgentListLocationParameter location, @jakarta.annotation.Nullable V2ProjectCopyRemoveRequest v2ProjectCopyRemoveRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2ProjectCopyRemoveRequestCreation(projectID, location, v2ProjectCopyRemoveRequest).bodyToMono(localVarReturnType);
    }

    /**
     * 
     * 
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - ProjectCopyError | InvalidRequestError
     * @param projectID The projectID parameter
     * @param location The location parameter
     * @param v2ProjectCopyRemoveRequest The v2ProjectCopyRemoveRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2ProjectCopyRemoveWithHttpInfo(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable V2AgentListLocationParameter location, @jakarta.annotation.Nullable V2ProjectCopyRemoveRequest v2ProjectCopyRemoveRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2ProjectCopyRemoveRequestCreation(projectID, location, v2ProjectCopyRemoveRequest).toEntity(localVarReturnType);
    }

    /**
     * 
     * 
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - ProjectCopyError | InvalidRequestError
     * @param projectID The projectID parameter
     * @param location The location parameter
     * @param v2ProjectCopyRemoveRequest The v2ProjectCopyRemoveRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2ProjectCopyRemoveWithResponseSpec(@jakarta.annotation.Nonnull String projectID, @jakarta.annotation.Nullable V2AgentListLocationParameter location, @jakarta.annotation.Nullable V2ProjectCopyRemoveRequest v2ProjectCopyRemoveRequest) throws WebClientResponseException {
        return v2ProjectCopyRemoveRequestCreation(projectID, location, v2ProjectCopyRemoveRequest);
    }
}
