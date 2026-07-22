package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.AuthSet400Response;
import com.example.opencode.sdk.model.BadRequestError;
import com.example.opencode.sdk.model.PermissionNotFoundError;
import com.example.opencode.sdk.model.PermissionReplyRequest;
import com.example.opencode.sdk.model.PermissionRequest;

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
public class PermissionApi {
    private ApiClient apiClient;

    public PermissionApi() {
        this(new ApiClient());
    }

    public PermissionApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public class PermissionListRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public PermissionListRequest() {}

        public PermissionListRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public PermissionListRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public PermissionListRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            PermissionListRequest request = (PermissionListRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * List pending permissions
     * Get all pending permission requests across all sessions.
     * <p><b>200</b> - List of pending permissions
     * <p><b>400</b> - Bad request
     * @param requestParameters The permissionList request parameters as object
     * @return List&lt;PermissionRequest&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<PermissionRequest> permissionList(PermissionListRequest requestParameters) throws WebClientResponseException {
        return this.permissionList(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List pending permissions
     * Get all pending permission requests across all sessions.
     * <p><b>200</b> - List of pending permissions
     * <p><b>400</b> - Bad request
     * @param requestParameters The permissionList request parameters as object
     * @return ResponseEntity&lt;List&lt;PermissionRequest&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<PermissionRequest>>> permissionListWithHttpInfo(PermissionListRequest requestParameters) throws WebClientResponseException {
        return this.permissionListWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List pending permissions
     * Get all pending permission requests across all sessions.
     * <p><b>200</b> - List of pending permissions
     * <p><b>400</b> - Bad request
     * @param requestParameters The permissionList request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec permissionListWithResponseSpec(PermissionListRequest requestParameters) throws WebClientResponseException {
        return this.permissionListWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * List pending permissions
     * Get all pending permission requests across all sessions.
     * <p><b>200</b> - List of pending permissions
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;PermissionRequest&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec permissionListRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<PermissionRequest> localVarReturnType = new ParameterizedTypeReference<PermissionRequest>() {};
        return apiClient.invokeAPI("/permission", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List pending permissions
     * Get all pending permission requests across all sessions.
     * <p><b>200</b> - List of pending permissions
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;PermissionRequest&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<PermissionRequest> permissionList(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<PermissionRequest> localVarReturnType = new ParameterizedTypeReference<PermissionRequest>() {};
        return permissionListRequestCreation(directory, workspace).bodyToFlux(localVarReturnType);
    }

    /**
     * List pending permissions
     * Get all pending permission requests across all sessions.
     * <p><b>200</b> - List of pending permissions
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;List&lt;PermissionRequest&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<PermissionRequest>>> permissionListWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<PermissionRequest> localVarReturnType = new ParameterizedTypeReference<PermissionRequest>() {};
        return permissionListRequestCreation(directory, workspace).toEntityList(localVarReturnType);
    }

    /**
     * List pending permissions
     * Get all pending permission requests across all sessions.
     * <p><b>200</b> - List of pending permissions
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec permissionListWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return permissionListRequestCreation(directory, workspace);
    }

    public class PermissionReplyRequest {
        private @jakarta.annotation.Nonnull String requestID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable PermissionReplyRequest permissionReplyRequest;

        public PermissionReplyRequest() {}

        public PermissionReplyRequest(@jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable PermissionReplyRequest permissionReplyRequest) {
            this.requestID = requestID;
            this.directory = directory;
            this.workspace = workspace;
            this.permissionReplyRequest = permissionReplyRequest;
        }

        public @jakarta.annotation.Nonnull String requestID() {
            return this.requestID;
        }
        public PermissionReplyRequest requestID(@jakarta.annotation.Nonnull String requestID) {
            this.requestID = requestID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public PermissionReplyRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public PermissionReplyRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable PermissionReplyRequest permissionReplyRequest() {
            return this.permissionReplyRequest;
        }
        public PermissionReplyRequest permissionReplyRequest(@jakarta.annotation.Nullable PermissionReplyRequest permissionReplyRequest) {
            this.permissionReplyRequest = permissionReplyRequest;
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
            PermissionReplyRequest request = (PermissionReplyRequest) o;
            return Objects.equals(this.requestID, request.requestID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.permissionReplyRequest, request.permissionReplyRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(requestID, directory, workspace, permissionReplyRequest);
        }
    }

    /**
     * Respond to permission request
     * Approve or deny a permission request from the AI assistant.
     * <p><b>200</b> - Permission processed successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - PermissionNotFoundError
     * @param requestParameters The permissionReply request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> permissionReply(PermissionReplyRequest requestParameters) throws WebClientResponseException {
        return this.permissionReply(requestParameters.requestID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.permissionReplyRequest());
    }

    /**
     * Respond to permission request
     * Approve or deny a permission request from the AI assistant.
     * <p><b>200</b> - Permission processed successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - PermissionNotFoundError
     * @param requestParameters The permissionReply request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> permissionReplyWithHttpInfo(PermissionReplyRequest requestParameters) throws WebClientResponseException {
        return this.permissionReplyWithHttpInfo(requestParameters.requestID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.permissionReplyRequest());
    }

    /**
     * Respond to permission request
     * Approve or deny a permission request from the AI assistant.
     * <p><b>200</b> - Permission processed successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - PermissionNotFoundError
     * @param requestParameters The permissionReply request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec permissionReplyWithResponseSpec(PermissionReplyRequest requestParameters) throws WebClientResponseException {
        return this.permissionReplyWithResponseSpec(requestParameters.requestID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.permissionReplyRequest());
    }


    /**
     * Respond to permission request
     * Approve or deny a permission request from the AI assistant.
     * <p><b>200</b> - Permission processed successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - PermissionNotFoundError
     * @param requestID The requestID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param permissionReplyRequest The permissionReplyRequest parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec permissionReplyRequestCreation(@jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable PermissionReplyRequest permissionReplyRequest) throws WebClientResponseException {
        Object postBody = permissionReplyRequest;
        // verify the required parameter 'requestID' is set
        if (requestID == null) {
            throw new WebClientResponseException("Missing the required parameter 'requestID' when calling permissionReply", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("requestID", requestID);

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

        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return apiClient.invokeAPI("/permission/{requestID}/reply", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Respond to permission request
     * Approve or deny a permission request from the AI assistant.
     * <p><b>200</b> - Permission processed successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - PermissionNotFoundError
     * @param requestID The requestID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param permissionReplyRequest The permissionReplyRequest parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> permissionReply(@jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable PermissionReplyRequest permissionReplyRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return permissionReplyRequestCreation(requestID, directory, workspace, permissionReplyRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Respond to permission request
     * Approve or deny a permission request from the AI assistant.
     * <p><b>200</b> - Permission processed successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - PermissionNotFoundError
     * @param requestID The requestID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param permissionReplyRequest The permissionReplyRequest parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> permissionReplyWithHttpInfo(@jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable PermissionReplyRequest permissionReplyRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return permissionReplyRequestCreation(requestID, directory, workspace, permissionReplyRequest).toEntity(localVarReturnType);
    }

    /**
     * Respond to permission request
     * Approve or deny a permission request from the AI assistant.
     * <p><b>200</b> - Permission processed successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - PermissionNotFoundError
     * @param requestID The requestID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param permissionReplyRequest The permissionReplyRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec permissionReplyWithResponseSpec(@jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable PermissionReplyRequest permissionReplyRequest) throws WebClientResponseException {
        return permissionReplyRequestCreation(requestID, directory, workspace, permissionReplyRequest);
    }
}
