package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.InvalidRequestErrorEncoded;
import com.example.opencode.sdk.model.ProjectNotFoundErrorEncoded;
import com.example.opencode.sdk.model.UnauthorizedErrorEncoded;
import com.example.opencode.sdk.model.WorktreeCreate400Response;
import com.example.opencode.sdk.model.WorktreeCreateInput;
import com.example.opencode.sdk.model.WorktreeDirectory;
import com.example.opencode.sdk.model.WorktreeInfo;
import com.example.opencode.sdk.model.WorktreeRefreshRequest;
import com.example.opencode.sdk.model.WorktreeRemoveInput;

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
public class WorktreeApi {
    private ApiClient apiClient;

    public WorktreeApi() {
        this(new ApiClient());
    }

    public WorktreeApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Create worktree
     * Load the project&#39;s canonical configuration, create a local worktree using its selected strategy, then run the project&#39;s setup script.
     * <p><b>200</b> - Worktree.Info
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProjectNotFoundError
     * @param worktreeCreateInput The worktreeCreateInput parameter
     * @return WorktreeInfo
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec worktreeCreateRequestCreation(@jakarta.annotation.Nonnull WorktreeCreateInput worktreeCreateInput) throws WebClientResponseException {
        Object postBody = worktreeCreateInput;
        // verify the required parameter 'worktreeCreateInput' is set
        if (worktreeCreateInput == null) {
            throw new WebClientResponseException("Missing the required parameter 'worktreeCreateInput' when calling worktreeCreate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
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
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<WorktreeInfo> localVarReturnType = new ParameterizedTypeReference<WorktreeInfo>() {};
        return apiClient.invokeAPI("/api/worktree", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Create worktree
     * Load the project&#39;s canonical configuration, create a local worktree using its selected strategy, then run the project&#39;s setup script.
     * <p><b>200</b> - Worktree.Info
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProjectNotFoundError
     * @param worktreeCreateInput The worktreeCreateInput parameter
     * @return WorktreeInfo
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<WorktreeInfo> worktreeCreate(@jakarta.annotation.Nonnull WorktreeCreateInput worktreeCreateInput) throws WebClientResponseException {
        ParameterizedTypeReference<WorktreeInfo> localVarReturnType = new ParameterizedTypeReference<WorktreeInfo>() {};
        return worktreeCreateRequestCreation(worktreeCreateInput).bodyToMono(localVarReturnType);
    }

    /**
     * Create worktree
     * Load the project&#39;s canonical configuration, create a local worktree using its selected strategy, then run the project&#39;s setup script.
     * <p><b>200</b> - Worktree.Info
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProjectNotFoundError
     * @param worktreeCreateInput The worktreeCreateInput parameter
     * @return ResponseEntity&lt;WorktreeInfo&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<WorktreeInfo>> worktreeCreateWithHttpInfo(@jakarta.annotation.Nonnull WorktreeCreateInput worktreeCreateInput) throws WebClientResponseException {
        ParameterizedTypeReference<WorktreeInfo> localVarReturnType = new ParameterizedTypeReference<WorktreeInfo>() {};
        return worktreeCreateRequestCreation(worktreeCreateInput).toEntity(localVarReturnType);
    }

    /**
     * Create worktree
     * Load the project&#39;s canonical configuration, create a local worktree using its selected strategy, then run the project&#39;s setup script.
     * <p><b>200</b> - Worktree.Info
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProjectNotFoundError
     * @param worktreeCreateInput The worktreeCreateInput parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec worktreeCreateWithResponseSpec(@jakarta.annotation.Nonnull WorktreeCreateInput worktreeCreateInput) throws WebClientResponseException {
        return worktreeCreateRequestCreation(worktreeCreateInput);
    }

    /**
     * List worktrees
     * Return the project&#39;s saved worktree inventory without loading configuration or running discovery.
     * <p><b>200</b> - Worktree.List
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProjectNotFoundError
     * @param projectID The projectID parameter
     * @return List&lt;WorktreeDirectory&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec worktreeListRequestCreation(@jakarta.annotation.Nullable String projectID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'projectID' is set
        if (projectID == null) {
            throw new WebClientResponseException("Missing the required parameter 'projectID' when calling worktreeList", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "projectID", projectID));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<WorktreeDirectory> localVarReturnType = new ParameterizedTypeReference<WorktreeDirectory>() {};
        return apiClient.invokeAPI("/api/worktree", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List worktrees
     * Return the project&#39;s saved worktree inventory without loading configuration or running discovery.
     * <p><b>200</b> - Worktree.List
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProjectNotFoundError
     * @param projectID The projectID parameter
     * @return List&lt;WorktreeDirectory&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<WorktreeDirectory> worktreeList(@jakarta.annotation.Nullable String projectID) throws WebClientResponseException {
        ParameterizedTypeReference<WorktreeDirectory> localVarReturnType = new ParameterizedTypeReference<WorktreeDirectory>() {};
        return worktreeListRequestCreation(projectID).bodyToFlux(localVarReturnType);
    }

    /**
     * List worktrees
     * Return the project&#39;s saved worktree inventory without loading configuration or running discovery.
     * <p><b>200</b> - Worktree.List
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProjectNotFoundError
     * @param projectID The projectID parameter
     * @return ResponseEntity&lt;List&lt;WorktreeDirectory&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<WorktreeDirectory>>> worktreeListWithHttpInfo(@jakarta.annotation.Nullable String projectID) throws WebClientResponseException {
        ParameterizedTypeReference<WorktreeDirectory> localVarReturnType = new ParameterizedTypeReference<WorktreeDirectory>() {};
        return worktreeListRequestCreation(projectID).toEntityList(localVarReturnType);
    }

    /**
     * List worktrees
     * Return the project&#39;s saved worktree inventory without loading configuration or running discovery.
     * <p><b>200</b> - Worktree.List
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProjectNotFoundError
     * @param projectID The projectID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec worktreeListWithResponseSpec(@jakarta.annotation.Nullable String projectID) throws WebClientResponseException {
        return worktreeListRequestCreation(projectID);
    }

    /**
     * Refresh worktrees
     * Load the project&#39;s canonical configuration, discover worktrees across known checkout roots using all available strategies, and reconcile saved state.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProjectNotFoundError
     * @param worktreeRefreshRequest The worktreeRefreshRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec worktreeRefreshRequestCreation(@jakarta.annotation.Nonnull WorktreeRefreshRequest worktreeRefreshRequest) throws WebClientResponseException {
        Object postBody = worktreeRefreshRequest;
        // verify the required parameter 'worktreeRefreshRequest' is set
        if (worktreeRefreshRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'worktreeRefreshRequest' when calling worktreeRefresh", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
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
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/worktree/refresh", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Refresh worktrees
     * Load the project&#39;s canonical configuration, discover worktrees across known checkout roots using all available strategies, and reconcile saved state.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProjectNotFoundError
     * @param worktreeRefreshRequest The worktreeRefreshRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> worktreeRefresh(@jakarta.annotation.Nonnull WorktreeRefreshRequest worktreeRefreshRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return worktreeRefreshRequestCreation(worktreeRefreshRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Refresh worktrees
     * Load the project&#39;s canonical configuration, discover worktrees across known checkout roots using all available strategies, and reconcile saved state.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProjectNotFoundError
     * @param worktreeRefreshRequest The worktreeRefreshRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> worktreeRefreshWithHttpInfo(@jakarta.annotation.Nonnull WorktreeRefreshRequest worktreeRefreshRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return worktreeRefreshRequestCreation(worktreeRefreshRequest).toEntity(localVarReturnType);
    }

    /**
     * Refresh worktrees
     * Load the project&#39;s canonical configuration, discover worktrees across known checkout roots using all available strategies, and reconcile saved state.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProjectNotFoundError
     * @param worktreeRefreshRequest The worktreeRefreshRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec worktreeRefreshWithResponseSpec(@jakarta.annotation.Nonnull WorktreeRefreshRequest worktreeRefreshRequest) throws WebClientResponseException {
        return worktreeRefreshRequestCreation(worktreeRefreshRequest);
    }

    /**
     * Remove worktree
     * Load the project&#39;s canonical configuration and remove a saved worktree using its recorded strategy.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProjectNotFoundError
     * @param worktreeRemoveInput The worktreeRemoveInput parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec worktreeRemoveRequestCreation(@jakarta.annotation.Nonnull WorktreeRemoveInput worktreeRemoveInput) throws WebClientResponseException {
        Object postBody = worktreeRemoveInput;
        // verify the required parameter 'worktreeRemoveInput' is set
        if (worktreeRemoveInput == null) {
            throw new WebClientResponseException("Missing the required parameter 'worktreeRemoveInput' when calling worktreeRemove", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
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
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/worktree", HttpMethod.DELETE, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Remove worktree
     * Load the project&#39;s canonical configuration and remove a saved worktree using its recorded strategy.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProjectNotFoundError
     * @param worktreeRemoveInput The worktreeRemoveInput parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> worktreeRemove(@jakarta.annotation.Nonnull WorktreeRemoveInput worktreeRemoveInput) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return worktreeRemoveRequestCreation(worktreeRemoveInput).bodyToMono(localVarReturnType);
    }

    /**
     * Remove worktree
     * Load the project&#39;s canonical configuration and remove a saved worktree using its recorded strategy.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProjectNotFoundError
     * @param worktreeRemoveInput The worktreeRemoveInput parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> worktreeRemoveWithHttpInfo(@jakarta.annotation.Nonnull WorktreeRemoveInput worktreeRemoveInput) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return worktreeRemoveRequestCreation(worktreeRemoveInput).toEntity(localVarReturnType);
    }

    /**
     * Remove worktree
     * Load the project&#39;s canonical configuration and remove a saved worktree using its recorded strategy.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - ProjectNotFoundError
     * @param worktreeRemoveInput The worktreeRemoveInput parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec worktreeRemoveWithResponseSpec(@jakarta.annotation.Nonnull WorktreeRemoveInput worktreeRemoveInput) throws WebClientResponseException {
        return worktreeRemoveRequestCreation(worktreeRemoveInput);
    }
}
