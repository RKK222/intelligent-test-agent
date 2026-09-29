package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.InvalidRequestErrorEncoded;
import com.example.opencode.sdk.model.Project;
import com.example.opencode.sdk.model.ProjectNotFoundErrorEncoded;
import com.example.opencode.sdk.model.ProjectUpdateRequest;
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

    /**
     * List projects
     * List known projects.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return List&lt;Project&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec projectListRequestCreation() throws WebClientResponseException {
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

        ParameterizedTypeReference<Project> localVarReturnType = new ParameterizedTypeReference<Project>() {};
        return apiClient.invokeAPI("/api/project", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List projects
     * List known projects.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return List&lt;Project&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<Project> projectList() throws WebClientResponseException {
        ParameterizedTypeReference<Project> localVarReturnType = new ParameterizedTypeReference<Project>() {};
        return projectListRequestCreation().bodyToFlux(localVarReturnType);
    }

    /**
     * List projects
     * List known projects.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return ResponseEntity&lt;List&lt;Project&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<Project>>> projectListWithHttpInfo() throws WebClientResponseException {
        ParameterizedTypeReference<Project> localVarReturnType = new ParameterizedTypeReference<Project>() {};
        return projectListRequestCreation().toEntityList(localVarReturnType);
    }

    /**
     * List projects
     * List known projects.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec projectListWithResponseSpec() throws WebClientResponseException {
        return projectListRequestCreation();
    }

    public class ProjectUpdateRequest {
        private @jakarta.annotation.Nullable String projectID;
        private @jakarta.annotation.Nonnull ProjectUpdateRequest projectUpdateRequest;

        public ProjectUpdateRequest() {}

        public ProjectUpdateRequest(@jakarta.annotation.Nullable String projectID, @jakarta.annotation.Nonnull ProjectUpdateRequest projectUpdateRequest) {
            this.projectID = projectID;
            this.projectUpdateRequest = projectUpdateRequest;
        }

        public @jakarta.annotation.Nullable String projectID() {
            return this.projectID;
        }
        public ProjectUpdateRequest projectID(@jakarta.annotation.Nullable String projectID) {
            this.projectID = projectID;
            return this;
        }

        public @jakarta.annotation.Nonnull ProjectUpdateRequest projectUpdateRequest() {
            return this.projectUpdateRequest;
        }
        public ProjectUpdateRequest projectUpdateRequest(@jakarta.annotation.Nonnull ProjectUpdateRequest projectUpdateRequest) {
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
                Objects.equals(this.projectUpdateRequest, request.projectUpdateRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(projectID, projectUpdateRequest);
        }
    }

    /**
     * Update project
     * Update the project canonical directory, display metadata, and workspace commands.
     * <p><b>200</b> - Project
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProjectNotFoundError
     * @param requestParameters The projectUpdate request parameters as object
     * @return Project
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Project> projectUpdate(ProjectUpdateRequest requestParameters) throws WebClientResponseException {
        return this.projectUpdate(requestParameters.projectID(), requestParameters.projectUpdateRequest());
    }

    /**
     * Update project
     * Update the project canonical directory, display metadata, and workspace commands.
     * <p><b>200</b> - Project
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProjectNotFoundError
     * @param requestParameters The projectUpdate request parameters as object
     * @return ResponseEntity&lt;Project&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Project>> projectUpdateWithHttpInfo(ProjectUpdateRequest requestParameters) throws WebClientResponseException {
        return this.projectUpdateWithHttpInfo(requestParameters.projectID(), requestParameters.projectUpdateRequest());
    }

    /**
     * Update project
     * Update the project canonical directory, display metadata, and workspace commands.
     * <p><b>200</b> - Project
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProjectNotFoundError
     * @param requestParameters The projectUpdate request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec projectUpdateWithResponseSpec(ProjectUpdateRequest requestParameters) throws WebClientResponseException {
        return this.projectUpdateWithResponseSpec(requestParameters.projectID(), requestParameters.projectUpdateRequest());
    }


    /**
     * Update project
     * Update the project canonical directory, display metadata, and workspace commands.
     * <p><b>200</b> - Project
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProjectNotFoundError
     * @param projectID The projectID parameter
     * @param projectUpdateRequest The projectUpdateRequest parameter
     * @return Project
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec projectUpdateRequestCreation(@jakarta.annotation.Nullable String projectID, @jakarta.annotation.Nonnull ProjectUpdateRequest projectUpdateRequest) throws WebClientResponseException {
        Object postBody = projectUpdateRequest;
        // verify the required parameter 'projectID' is set
        if (projectID == null) {
            throw new WebClientResponseException("Missing the required parameter 'projectID' when calling projectUpdate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'projectUpdateRequest' is set
        if (projectUpdateRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'projectUpdateRequest' when calling projectUpdate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("projectID", projectID);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

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
        return apiClient.invokeAPI("/api/project/{projectID}", HttpMethod.PATCH, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Update project
     * Update the project canonical directory, display metadata, and workspace commands.
     * <p><b>200</b> - Project
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProjectNotFoundError
     * @param projectID The projectID parameter
     * @param projectUpdateRequest The projectUpdateRequest parameter
     * @return Project
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Project> projectUpdate(@jakarta.annotation.Nullable String projectID, @jakarta.annotation.Nonnull ProjectUpdateRequest projectUpdateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Project> localVarReturnType = new ParameterizedTypeReference<Project>() {};
        return projectUpdateRequestCreation(projectID, projectUpdateRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Update project
     * Update the project canonical directory, display metadata, and workspace commands.
     * <p><b>200</b> - Project
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProjectNotFoundError
     * @param projectID The projectID parameter
     * @param projectUpdateRequest The projectUpdateRequest parameter
     * @return ResponseEntity&lt;Project&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Project>> projectUpdateWithHttpInfo(@jakarta.annotation.Nullable String projectID, @jakarta.annotation.Nonnull ProjectUpdateRequest projectUpdateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Project> localVarReturnType = new ParameterizedTypeReference<Project>() {};
        return projectUpdateRequestCreation(projectID, projectUpdateRequest).toEntity(localVarReturnType);
    }

    /**
     * Update project
     * Update the project canonical directory, display metadata, and workspace commands.
     * <p><b>200</b> - Project
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProjectNotFoundError
     * @param projectID The projectID parameter
     * @param projectUpdateRequest The projectUpdateRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec projectUpdateWithResponseSpec(@jakarta.annotation.Nullable String projectID, @jakarta.annotation.Nonnull ProjectUpdateRequest projectUpdateRequest) throws WebClientResponseException {
        return projectUpdateRequestCreation(projectID, projectUpdateRequest);
    }
}
