package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.AuthSet400Response;
import com.example.opencode.sdk.model.BadRequestError;
import java.math.BigDecimal;
import com.example.opencode.sdk.model.EffectHttpApiErrorInternalServerError;
import com.example.opencode.sdk.model.ExperimentalSessionListRootsParameter;
import com.example.opencode.sdk.model.NotFoundError;
import com.example.opencode.sdk.model.Part;
import com.example.opencode.sdk.model.PermissionRespond404Response;
import com.example.opencode.sdk.model.PermissionRespondRequest;
import com.example.opencode.sdk.model.Session;
import com.example.opencode.sdk.model.SessionBusyError;
import com.example.opencode.sdk.model.SessionCommandRequest;
import com.example.opencode.sdk.model.SessionCreateRequest;
import com.example.opencode.sdk.model.SessionForkRequest;
import com.example.opencode.sdk.model.SessionInitRequest;
import com.example.opencode.sdk.model.SessionMessages200ResponseInner;
import com.example.opencode.sdk.model.SessionPrompt200Response;
import com.example.opencode.sdk.model.SessionPromptAsyncRequest;
import com.example.opencode.sdk.model.SessionPromptRequest;
import com.example.opencode.sdk.model.SessionRevertRequest;
import com.example.opencode.sdk.model.SessionShellRequest;
import com.example.opencode.sdk.model.SessionStatus;
import com.example.opencode.sdk.model.SessionSummarizeRequest;
import com.example.opencode.sdk.model.SessionUpdateRequest;
import com.example.opencode.sdk.model.SnapshotFileDiff;
import com.example.opencode.sdk.model.Todo;

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
public class SessionApi {
    private ApiClient apiClient;

    public SessionApi() {
        this(new ApiClient());
    }

    public SessionApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public class PartDeleteRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull String messageID;
        private @jakarta.annotation.Nonnull String partID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public PartDeleteRequest() {}

        public PartDeleteRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String messageID, @jakarta.annotation.Nonnull String partID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.sessionID = sessionID;
            this.messageID = messageID;
            this.partID = partID;
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public PartDeleteRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull String messageID() {
            return this.messageID;
        }
        public PartDeleteRequest messageID(@jakarta.annotation.Nonnull String messageID) {
            this.messageID = messageID;
            return this;
        }

        public @jakarta.annotation.Nonnull String partID() {
            return this.partID;
        }
        public PartDeleteRequest partID(@jakarta.annotation.Nonnull String partID) {
            this.partID = partID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public PartDeleteRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public PartDeleteRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            PartDeleteRequest request = (PartDeleteRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.messageID, request.messageID()) &&
                Objects.equals(this.partID, request.partID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, messageID, partID, directory, workspace);
        }
    }

    /**
     * 
     * Delete a part from a message.
     * <p><b>200</b> - Successfully deleted part
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The partDelete request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> partDelete(PartDeleteRequest requestParameters) throws WebClientResponseException {
        return this.partDelete(requestParameters.sessionID(), requestParameters.messageID(), requestParameters.partID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * 
     * Delete a part from a message.
     * <p><b>200</b> - Successfully deleted part
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The partDelete request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> partDeleteWithHttpInfo(PartDeleteRequest requestParameters) throws WebClientResponseException {
        return this.partDeleteWithHttpInfo(requestParameters.sessionID(), requestParameters.messageID(), requestParameters.partID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * 
     * Delete a part from a message.
     * <p><b>200</b> - Successfully deleted part
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The partDelete request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec partDeleteWithResponseSpec(PartDeleteRequest requestParameters) throws WebClientResponseException {
        return this.partDeleteWithResponseSpec(requestParameters.sessionID(), requestParameters.messageID(), requestParameters.partID(), requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * 
     * Delete a part from a message.
     * <p><b>200</b> - Successfully deleted part
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param messageID The messageID parameter
     * @param partID The partID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec partDeleteRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String messageID, @jakarta.annotation.Nonnull String partID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling partDelete", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'messageID' is set
        if (messageID == null) {
            throw new WebClientResponseException("Missing the required parameter 'messageID' when calling partDelete", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'partID' is set
        if (partID == null) {
            throw new WebClientResponseException("Missing the required parameter 'partID' when calling partDelete", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);
        pathParams.put("messageID", messageID);
        pathParams.put("partID", partID);

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

        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return apiClient.invokeAPI("/session/{sessionID}/message/{messageID}/part/{partID}", HttpMethod.DELETE, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * 
     * Delete a part from a message.
     * <p><b>200</b> - Successfully deleted part
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param messageID The messageID parameter
     * @param partID The partID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> partDelete(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String messageID, @jakarta.annotation.Nonnull String partID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return partDeleteRequestCreation(sessionID, messageID, partID, directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * 
     * Delete a part from a message.
     * <p><b>200</b> - Successfully deleted part
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param messageID The messageID parameter
     * @param partID The partID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> partDeleteWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String messageID, @jakarta.annotation.Nonnull String partID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return partDeleteRequestCreation(sessionID, messageID, partID, directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * 
     * Delete a part from a message.
     * <p><b>200</b> - Successfully deleted part
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param messageID The messageID parameter
     * @param partID The partID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec partDeleteWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String messageID, @jakarta.annotation.Nonnull String partID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return partDeleteRequestCreation(sessionID, messageID, partID, directory, workspace);
    }

    public class PartUpdateRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull String messageID;
        private @jakarta.annotation.Nonnull String partID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable Part part;

        public PartUpdateRequest() {}

        public PartUpdateRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String messageID, @jakarta.annotation.Nonnull String partID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Part part) {
            this.sessionID = sessionID;
            this.messageID = messageID;
            this.partID = partID;
            this.directory = directory;
            this.workspace = workspace;
            this.part = part;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public PartUpdateRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull String messageID() {
            return this.messageID;
        }
        public PartUpdateRequest messageID(@jakarta.annotation.Nonnull String messageID) {
            this.messageID = messageID;
            return this;
        }

        public @jakarta.annotation.Nonnull String partID() {
            return this.partID;
        }
        public PartUpdateRequest partID(@jakarta.annotation.Nonnull String partID) {
            this.partID = partID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public PartUpdateRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public PartUpdateRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable Part part() {
            return this.part;
        }
        public PartUpdateRequest part(@jakarta.annotation.Nullable Part part) {
            this.part = part;
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
            PartUpdateRequest request = (PartUpdateRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.messageID, request.messageID()) &&
                Objects.equals(this.partID, request.partID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.part, request.part());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, messageID, partID, directory, workspace, part);
        }
    }

    /**
     * 
     * Update a part in a message.
     * <p><b>200</b> - Successfully updated part
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The partUpdate request parameters as object
     * @return Part
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Part> partUpdate(PartUpdateRequest requestParameters) throws WebClientResponseException {
        return this.partUpdate(requestParameters.sessionID(), requestParameters.messageID(), requestParameters.partID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.part());
    }

    /**
     * 
     * Update a part in a message.
     * <p><b>200</b> - Successfully updated part
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The partUpdate request parameters as object
     * @return ResponseEntity&lt;Part&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Part>> partUpdateWithHttpInfo(PartUpdateRequest requestParameters) throws WebClientResponseException {
        return this.partUpdateWithHttpInfo(requestParameters.sessionID(), requestParameters.messageID(), requestParameters.partID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.part());
    }

    /**
     * 
     * Update a part in a message.
     * <p><b>200</b> - Successfully updated part
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The partUpdate request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec partUpdateWithResponseSpec(PartUpdateRequest requestParameters) throws WebClientResponseException {
        return this.partUpdateWithResponseSpec(requestParameters.sessionID(), requestParameters.messageID(), requestParameters.partID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.part());
    }


    /**
     * 
     * Update a part in a message.
     * <p><b>200</b> - Successfully updated part
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param messageID The messageID parameter
     * @param partID The partID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param part The part parameter
     * @return Part
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec partUpdateRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String messageID, @jakarta.annotation.Nonnull String partID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Part part) throws WebClientResponseException {
        Object postBody = part;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling partUpdate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'messageID' is set
        if (messageID == null) {
            throw new WebClientResponseException("Missing the required parameter 'messageID' when calling partUpdate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'partID' is set
        if (partID == null) {
            throw new WebClientResponseException("Missing the required parameter 'partID' when calling partUpdate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);
        pathParams.put("messageID", messageID);
        pathParams.put("partID", partID);

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

        ParameterizedTypeReference<Part> localVarReturnType = new ParameterizedTypeReference<Part>() {};
        return apiClient.invokeAPI("/session/{sessionID}/message/{messageID}/part/{partID}", HttpMethod.PATCH, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * 
     * Update a part in a message.
     * <p><b>200</b> - Successfully updated part
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param messageID The messageID parameter
     * @param partID The partID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param part The part parameter
     * @return Part
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Part> partUpdate(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String messageID, @jakarta.annotation.Nonnull String partID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Part part) throws WebClientResponseException {
        ParameterizedTypeReference<Part> localVarReturnType = new ParameterizedTypeReference<Part>() {};
        return partUpdateRequestCreation(sessionID, messageID, partID, directory, workspace, part).bodyToMono(localVarReturnType);
    }

    /**
     * 
     * Update a part in a message.
     * <p><b>200</b> - Successfully updated part
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param messageID The messageID parameter
     * @param partID The partID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param part The part parameter
     * @return ResponseEntity&lt;Part&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Part>> partUpdateWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String messageID, @jakarta.annotation.Nonnull String partID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Part part) throws WebClientResponseException {
        ParameterizedTypeReference<Part> localVarReturnType = new ParameterizedTypeReference<Part>() {};
        return partUpdateRequestCreation(sessionID, messageID, partID, directory, workspace, part).toEntity(localVarReturnType);
    }

    /**
     * 
     * Update a part in a message.
     * <p><b>200</b> - Successfully updated part
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param messageID The messageID parameter
     * @param partID The partID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param part The part parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec partUpdateWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String messageID, @jakarta.annotation.Nonnull String partID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Part part) throws WebClientResponseException {
        return partUpdateRequestCreation(sessionID, messageID, partID, directory, workspace, part);
    }

    public class PermissionRespondRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull String permissionID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable PermissionRespondRequest permissionRespondRequest;

        public PermissionRespondRequest() {}

        public PermissionRespondRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String permissionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable PermissionRespondRequest permissionRespondRequest) {
            this.sessionID = sessionID;
            this.permissionID = permissionID;
            this.directory = directory;
            this.workspace = workspace;
            this.permissionRespondRequest = permissionRespondRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public PermissionRespondRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull String permissionID() {
            return this.permissionID;
        }
        public PermissionRespondRequest permissionID(@jakarta.annotation.Nonnull String permissionID) {
            this.permissionID = permissionID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public PermissionRespondRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public PermissionRespondRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable PermissionRespondRequest permissionRespondRequest() {
            return this.permissionRespondRequest;
        }
        public PermissionRespondRequest permissionRespondRequest(@jakarta.annotation.Nullable PermissionRespondRequest permissionRespondRequest) {
            this.permissionRespondRequest = permissionRespondRequest;
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
            PermissionRespondRequest request = (PermissionRespondRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.permissionID, request.permissionID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.permissionRespondRequest, request.permissionRespondRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, permissionID, directory, workspace, permissionRespondRequest);
        }
    }

    /**
     * Respond to permission
     * Approve or deny a permission request from the AI assistant.
     * <p><b>200</b> - Permission processed successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError | PermissionNotFoundError
     * @param requestParameters The permissionRespond request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> permissionRespond(PermissionRespondRequest requestParameters) throws WebClientResponseException {
        return this.permissionRespond(requestParameters.sessionID(), requestParameters.permissionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.permissionRespondRequest());
    }

    /**
     * Respond to permission
     * Approve or deny a permission request from the AI assistant.
     * <p><b>200</b> - Permission processed successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError | PermissionNotFoundError
     * @param requestParameters The permissionRespond request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> permissionRespondWithHttpInfo(PermissionRespondRequest requestParameters) throws WebClientResponseException {
        return this.permissionRespondWithHttpInfo(requestParameters.sessionID(), requestParameters.permissionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.permissionRespondRequest());
    }

    /**
     * Respond to permission
     * Approve or deny a permission request from the AI assistant.
     * <p><b>200</b> - Permission processed successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError | PermissionNotFoundError
     * @param requestParameters The permissionRespond request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec permissionRespondWithResponseSpec(PermissionRespondRequest requestParameters) throws WebClientResponseException {
        return this.permissionRespondWithResponseSpec(requestParameters.sessionID(), requestParameters.permissionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.permissionRespondRequest());
    }


    /**
     * Respond to permission
     * Approve or deny a permission request from the AI assistant.
     * <p><b>200</b> - Permission processed successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError | PermissionNotFoundError
     * @param sessionID The sessionID parameter
     * @param permissionID The permissionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param permissionRespondRequest The permissionRespondRequest parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     * @deprecated
     */
    @Deprecated
    private ResponseSpec permissionRespondRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String permissionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable PermissionRespondRequest permissionRespondRequest) throws WebClientResponseException {
        Object postBody = permissionRespondRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling permissionRespond", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'permissionID' is set
        if (permissionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'permissionID' when calling permissionRespond", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);
        pathParams.put("permissionID", permissionID);

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
        return apiClient.invokeAPI("/session/{sessionID}/permissions/{permissionID}", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Respond to permission
     * Approve or deny a permission request from the AI assistant.
     * <p><b>200</b> - Permission processed successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError | PermissionNotFoundError
     * @param sessionID The sessionID parameter
     * @param permissionID The permissionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param permissionRespondRequest The permissionRespondRequest parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> permissionRespond(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String permissionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable PermissionRespondRequest permissionRespondRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return permissionRespondRequestCreation(sessionID, permissionID, directory, workspace, permissionRespondRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Respond to permission
     * Approve or deny a permission request from the AI assistant.
     * <p><b>200</b> - Permission processed successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError | PermissionNotFoundError
     * @param sessionID The sessionID parameter
     * @param permissionID The permissionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param permissionRespondRequest The permissionRespondRequest parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     * @deprecated
     */
    @Deprecated
    public Mono<ResponseEntity<Boolean>> permissionRespondWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String permissionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable PermissionRespondRequest permissionRespondRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return permissionRespondRequestCreation(sessionID, permissionID, directory, workspace, permissionRespondRequest).toEntity(localVarReturnType);
    }

    /**
     * Respond to permission
     * Approve or deny a permission request from the AI assistant.
     * <p><b>200</b> - Permission processed successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError | PermissionNotFoundError
     * @param sessionID The sessionID parameter
     * @param permissionID The permissionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param permissionRespondRequest The permissionRespondRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec permissionRespondWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String permissionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable PermissionRespondRequest permissionRespondRequest) throws WebClientResponseException {
        return permissionRespondRequestCreation(sessionID, permissionID, directory, workspace, permissionRespondRequest);
    }

    public class SessionAbortRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public SessionAbortRequest() {}

        public SessionAbortRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.sessionID = sessionID;
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionAbortRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public SessionAbortRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public SessionAbortRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            SessionAbortRequest request = (SessionAbortRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, directory, workspace);
        }
    }

    /**
     * Abort session
     * Abort an active session and stop any ongoing AI processing or command execution.
     * <p><b>200</b> - Aborted session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The sessionAbort request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> sessionAbort(SessionAbortRequest requestParameters) throws WebClientResponseException {
        return this.sessionAbort(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Abort session
     * Abort an active session and stop any ongoing AI processing or command execution.
     * <p><b>200</b> - Aborted session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The sessionAbort request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> sessionAbortWithHttpInfo(SessionAbortRequest requestParameters) throws WebClientResponseException {
        return this.sessionAbortWithHttpInfo(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Abort session
     * Abort an active session and stop any ongoing AI processing or command execution.
     * <p><b>200</b> - Aborted session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The sessionAbort request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionAbortWithResponseSpec(SessionAbortRequest requestParameters) throws WebClientResponseException {
        return this.sessionAbortWithResponseSpec(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Abort session
     * Abort an active session and stop any ongoing AI processing or command execution.
     * <p><b>200</b> - Aborted session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionAbortRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionAbort", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

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

        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return apiClient.invokeAPI("/session/{sessionID}/abort", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Abort session
     * Abort an active session and stop any ongoing AI processing or command execution.
     * <p><b>200</b> - Aborted session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> sessionAbort(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return sessionAbortRequestCreation(sessionID, directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Abort session
     * Abort an active session and stop any ongoing AI processing or command execution.
     * <p><b>200</b> - Aborted session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> sessionAbortWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return sessionAbortRequestCreation(sessionID, directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Abort session
     * Abort an active session and stop any ongoing AI processing or command execution.
     * <p><b>200</b> - Aborted session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionAbortWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return sessionAbortRequestCreation(sessionID, directory, workspace);
    }

    public class SessionChildrenRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public SessionChildrenRequest() {}

        public SessionChildrenRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.sessionID = sessionID;
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionChildrenRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public SessionChildrenRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public SessionChildrenRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            SessionChildrenRequest request = (SessionChildrenRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, directory, workspace);
        }
    }

    /**
     * Get session children
     * Retrieve all child sessions that were forked from the specified parent session.
     * <p><b>200</b> - List of children
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionChildren request parameters as object
     * @return List&lt;Session&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<Session> sessionChildren(SessionChildrenRequest requestParameters) throws WebClientResponseException {
        return this.sessionChildren(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get session children
     * Retrieve all child sessions that were forked from the specified parent session.
     * <p><b>200</b> - List of children
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionChildren request parameters as object
     * @return ResponseEntity&lt;List&lt;Session&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<Session>>> sessionChildrenWithHttpInfo(SessionChildrenRequest requestParameters) throws WebClientResponseException {
        return this.sessionChildrenWithHttpInfo(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get session children
     * Retrieve all child sessions that were forked from the specified parent session.
     * <p><b>200</b> - List of children
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionChildren request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionChildrenWithResponseSpec(SessionChildrenRequest requestParameters) throws WebClientResponseException {
        return this.sessionChildrenWithResponseSpec(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Get session children
     * Retrieve all child sessions that were forked from the specified parent session.
     * <p><b>200</b> - List of children
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;Session&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionChildrenRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionChildren", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

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

        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return apiClient.invokeAPI("/session/{sessionID}/children", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get session children
     * Retrieve all child sessions that were forked from the specified parent session.
     * <p><b>200</b> - List of children
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;Session&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<Session> sessionChildren(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return sessionChildrenRequestCreation(sessionID, directory, workspace).bodyToFlux(localVarReturnType);
    }

    /**
     * Get session children
     * Retrieve all child sessions that were forked from the specified parent session.
     * <p><b>200</b> - List of children
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;List&lt;Session&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<Session>>> sessionChildrenWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return sessionChildrenRequestCreation(sessionID, directory, workspace).toEntityList(localVarReturnType);
    }

    /**
     * Get session children
     * Retrieve all child sessions that were forked from the specified parent session.
     * <p><b>200</b> - List of children
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionChildrenWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return sessionChildrenRequestCreation(sessionID, directory, workspace);
    }

    public class SessionCommandRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable SessionCommandRequest sessionCommandRequest;

        public SessionCommandRequest() {}

        public SessionCommandRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionCommandRequest sessionCommandRequest) {
            this.sessionID = sessionID;
            this.directory = directory;
            this.workspace = workspace;
            this.sessionCommandRequest = sessionCommandRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionCommandRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public SessionCommandRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public SessionCommandRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable SessionCommandRequest sessionCommandRequest() {
            return this.sessionCommandRequest;
        }
        public SessionCommandRequest sessionCommandRequest(@jakarta.annotation.Nullable SessionCommandRequest sessionCommandRequest) {
            this.sessionCommandRequest = sessionCommandRequest;
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
            SessionCommandRequest request = (SessionCommandRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.sessionCommandRequest, request.sessionCommandRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, directory, workspace, sessionCommandRequest);
        }
    }

    /**
     * Send command
     * Send a new command to a session for execution by the AI assistant.
     * <p><b>200</b> - Created message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionCommand request parameters as object
     * @return SessionPrompt200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionPrompt200Response> sessionCommand(SessionCommandRequest requestParameters) throws WebClientResponseException {
        return this.sessionCommand(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionCommandRequest());
    }

    /**
     * Send command
     * Send a new command to a session for execution by the AI assistant.
     * <p><b>200</b> - Created message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionCommand request parameters as object
     * @return ResponseEntity&lt;SessionPrompt200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionPrompt200Response>> sessionCommandWithHttpInfo(SessionCommandRequest requestParameters) throws WebClientResponseException {
        return this.sessionCommandWithHttpInfo(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionCommandRequest());
    }

    /**
     * Send command
     * Send a new command to a session for execution by the AI assistant.
     * <p><b>200</b> - Created message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionCommand request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionCommandWithResponseSpec(SessionCommandRequest requestParameters) throws WebClientResponseException {
        return this.sessionCommandWithResponseSpec(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionCommandRequest());
    }


    /**
     * Send command
     * Send a new command to a session for execution by the AI assistant.
     * <p><b>200</b> - Created message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionCommandRequest The sessionCommandRequest parameter
     * @return SessionPrompt200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionCommandRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionCommandRequest sessionCommandRequest) throws WebClientResponseException {
        Object postBody = sessionCommandRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionCommand", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

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

        ParameterizedTypeReference<SessionPrompt200Response> localVarReturnType = new ParameterizedTypeReference<SessionPrompt200Response>() {};
        return apiClient.invokeAPI("/session/{sessionID}/command", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Send command
     * Send a new command to a session for execution by the AI assistant.
     * <p><b>200</b> - Created message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionCommandRequest The sessionCommandRequest parameter
     * @return SessionPrompt200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionPrompt200Response> sessionCommand(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionCommandRequest sessionCommandRequest) throws WebClientResponseException {
        ParameterizedTypeReference<SessionPrompt200Response> localVarReturnType = new ParameterizedTypeReference<SessionPrompt200Response>() {};
        return sessionCommandRequestCreation(sessionID, directory, workspace, sessionCommandRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Send command
     * Send a new command to a session for execution by the AI assistant.
     * <p><b>200</b> - Created message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionCommandRequest The sessionCommandRequest parameter
     * @return ResponseEntity&lt;SessionPrompt200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionPrompt200Response>> sessionCommandWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionCommandRequest sessionCommandRequest) throws WebClientResponseException {
        ParameterizedTypeReference<SessionPrompt200Response> localVarReturnType = new ParameterizedTypeReference<SessionPrompt200Response>() {};
        return sessionCommandRequestCreation(sessionID, directory, workspace, sessionCommandRequest).toEntity(localVarReturnType);
    }

    /**
     * Send command
     * Send a new command to a session for execution by the AI assistant.
     * <p><b>200</b> - Created message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionCommandRequest The sessionCommandRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionCommandWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionCommandRequest sessionCommandRequest) throws WebClientResponseException {
        return sessionCommandRequestCreation(sessionID, directory, workspace, sessionCommandRequest);
    }

    public class SessionCreateRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable SessionCreateRequest sessionCreateRequest;

        public SessionCreateRequest() {}

        public SessionCreateRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionCreateRequest sessionCreateRequest) {
            this.directory = directory;
            this.workspace = workspace;
            this.sessionCreateRequest = sessionCreateRequest;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public SessionCreateRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public SessionCreateRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable SessionCreateRequest sessionCreateRequest() {
            return this.sessionCreateRequest;
        }
        public SessionCreateRequest sessionCreateRequest(@jakarta.annotation.Nullable SessionCreateRequest sessionCreateRequest) {
            this.sessionCreateRequest = sessionCreateRequest;
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
            SessionCreateRequest request = (SessionCreateRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.sessionCreateRequest, request.sessionCreateRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace, sessionCreateRequest);
        }
    }

    /**
     * Create session
     * Create a new OpenCode session for interacting with AI assistants and managing conversations.
     * <p><b>200</b> - Successfully created session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The sessionCreate request parameters as object
     * @return Session
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Session> sessionCreate(SessionCreateRequest requestParameters) throws WebClientResponseException {
        return this.sessionCreate(requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionCreateRequest());
    }

    /**
     * Create session
     * Create a new OpenCode session for interacting with AI assistants and managing conversations.
     * <p><b>200</b> - Successfully created session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The sessionCreate request parameters as object
     * @return ResponseEntity&lt;Session&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Session>> sessionCreateWithHttpInfo(SessionCreateRequest requestParameters) throws WebClientResponseException {
        return this.sessionCreateWithHttpInfo(requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionCreateRequest());
    }

    /**
     * Create session
     * Create a new OpenCode session for interacting with AI assistants and managing conversations.
     * <p><b>200</b> - Successfully created session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The sessionCreate request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionCreateWithResponseSpec(SessionCreateRequest requestParameters) throws WebClientResponseException {
        return this.sessionCreateWithResponseSpec(requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionCreateRequest());
    }


    /**
     * Create session
     * Create a new OpenCode session for interacting with AI assistants and managing conversations.
     * <p><b>200</b> - Successfully created session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionCreateRequest The sessionCreateRequest parameter
     * @return Session
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionCreateRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionCreateRequest sessionCreateRequest) throws WebClientResponseException {
        Object postBody = sessionCreateRequest;
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

        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return apiClient.invokeAPI("/session", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Create session
     * Create a new OpenCode session for interacting with AI assistants and managing conversations.
     * <p><b>200</b> - Successfully created session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionCreateRequest The sessionCreateRequest parameter
     * @return Session
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Session> sessionCreate(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionCreateRequest sessionCreateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return sessionCreateRequestCreation(directory, workspace, sessionCreateRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Create session
     * Create a new OpenCode session for interacting with AI assistants and managing conversations.
     * <p><b>200</b> - Successfully created session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionCreateRequest The sessionCreateRequest parameter
     * @return ResponseEntity&lt;Session&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Session>> sessionCreateWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionCreateRequest sessionCreateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return sessionCreateRequestCreation(directory, workspace, sessionCreateRequest).toEntity(localVarReturnType);
    }

    /**
     * Create session
     * Create a new OpenCode session for interacting with AI assistants and managing conversations.
     * <p><b>200</b> - Successfully created session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionCreateRequest The sessionCreateRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionCreateWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionCreateRequest sessionCreateRequest) throws WebClientResponseException {
        return sessionCreateRequestCreation(directory, workspace, sessionCreateRequest);
    }

    public class SessionDeleteRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public SessionDeleteRequest() {}

        public SessionDeleteRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.sessionID = sessionID;
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionDeleteRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public SessionDeleteRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public SessionDeleteRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            SessionDeleteRequest request = (SessionDeleteRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, directory, workspace);
        }
    }

    /**
     * Delete session
     * Delete a session and permanently remove all associated data, including messages and history.
     * <p><b>200</b> - Successfully deleted session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionDelete request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> sessionDelete(SessionDeleteRequest requestParameters) throws WebClientResponseException {
        return this.sessionDelete(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Delete session
     * Delete a session and permanently remove all associated data, including messages and history.
     * <p><b>200</b> - Successfully deleted session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionDelete request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> sessionDeleteWithHttpInfo(SessionDeleteRequest requestParameters) throws WebClientResponseException {
        return this.sessionDeleteWithHttpInfo(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Delete session
     * Delete a session and permanently remove all associated data, including messages and history.
     * <p><b>200</b> - Successfully deleted session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionDelete request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionDeleteWithResponseSpec(SessionDeleteRequest requestParameters) throws WebClientResponseException {
        return this.sessionDeleteWithResponseSpec(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Delete session
     * Delete a session and permanently remove all associated data, including messages and history.
     * <p><b>200</b> - Successfully deleted session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionDeleteRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionDelete", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

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

        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return apiClient.invokeAPI("/session/{sessionID}", HttpMethod.DELETE, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Delete session
     * Delete a session and permanently remove all associated data, including messages and history.
     * <p><b>200</b> - Successfully deleted session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> sessionDelete(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return sessionDeleteRequestCreation(sessionID, directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Delete session
     * Delete a session and permanently remove all associated data, including messages and history.
     * <p><b>200</b> - Successfully deleted session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> sessionDeleteWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return sessionDeleteRequestCreation(sessionID, directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Delete session
     * Delete a session and permanently remove all associated data, including messages and history.
     * <p><b>200</b> - Successfully deleted session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionDeleteWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return sessionDeleteRequestCreation(sessionID, directory, workspace);
    }

    public class SessionDeleteMessageRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull String messageID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public SessionDeleteMessageRequest() {}

        public SessionDeleteMessageRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String messageID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.sessionID = sessionID;
            this.messageID = messageID;
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionDeleteMessageRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull String messageID() {
            return this.messageID;
        }
        public SessionDeleteMessageRequest messageID(@jakarta.annotation.Nonnull String messageID) {
            this.messageID = messageID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public SessionDeleteMessageRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public SessionDeleteMessageRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            SessionDeleteMessageRequest request = (SessionDeleteMessageRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.messageID, request.messageID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, messageID, directory, workspace);
        }
    }

    /**
     * Delete message
     * Permanently delete a specific message and all of its parts from a session without reverting file changes.
     * <p><b>200</b> - Successfully deleted message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param requestParameters The sessionDeleteMessage request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> sessionDeleteMessage(SessionDeleteMessageRequest requestParameters) throws WebClientResponseException {
        return this.sessionDeleteMessage(requestParameters.sessionID(), requestParameters.messageID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Delete message
     * Permanently delete a specific message and all of its parts from a session without reverting file changes.
     * <p><b>200</b> - Successfully deleted message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param requestParameters The sessionDeleteMessage request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> sessionDeleteMessageWithHttpInfo(SessionDeleteMessageRequest requestParameters) throws WebClientResponseException {
        return this.sessionDeleteMessageWithHttpInfo(requestParameters.sessionID(), requestParameters.messageID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Delete message
     * Permanently delete a specific message and all of its parts from a session without reverting file changes.
     * <p><b>200</b> - Successfully deleted message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param requestParameters The sessionDeleteMessage request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionDeleteMessageWithResponseSpec(SessionDeleteMessageRequest requestParameters) throws WebClientResponseException {
        return this.sessionDeleteMessageWithResponseSpec(requestParameters.sessionID(), requestParameters.messageID(), requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Delete message
     * Permanently delete a specific message and all of its parts from a session without reverting file changes.
     * <p><b>200</b> - Successfully deleted message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param sessionID The sessionID parameter
     * @param messageID The messageID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionDeleteMessageRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String messageID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionDeleteMessage", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'messageID' is set
        if (messageID == null) {
            throw new WebClientResponseException("Missing the required parameter 'messageID' when calling sessionDeleteMessage", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);
        pathParams.put("messageID", messageID);

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

        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return apiClient.invokeAPI("/session/{sessionID}/message/{messageID}", HttpMethod.DELETE, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Delete message
     * Permanently delete a specific message and all of its parts from a session without reverting file changes.
     * <p><b>200</b> - Successfully deleted message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param sessionID The sessionID parameter
     * @param messageID The messageID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> sessionDeleteMessage(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String messageID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return sessionDeleteMessageRequestCreation(sessionID, messageID, directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Delete message
     * Permanently delete a specific message and all of its parts from a session without reverting file changes.
     * <p><b>200</b> - Successfully deleted message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param sessionID The sessionID parameter
     * @param messageID The messageID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> sessionDeleteMessageWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String messageID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return sessionDeleteMessageRequestCreation(sessionID, messageID, directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Delete message
     * Permanently delete a specific message and all of its parts from a session without reverting file changes.
     * <p><b>200</b> - Successfully deleted message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param sessionID The sessionID parameter
     * @param messageID The messageID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionDeleteMessageWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String messageID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return sessionDeleteMessageRequestCreation(sessionID, messageID, directory, workspace);
    }

    public class SessionDiffRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable String messageID;

        public SessionDiffRequest() {}

        public SessionDiffRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable String messageID) {
            this.sessionID = sessionID;
            this.directory = directory;
            this.workspace = workspace;
            this.messageID = messageID;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionDiffRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public SessionDiffRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public SessionDiffRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable String messageID() {
            return this.messageID;
        }
        public SessionDiffRequest messageID(@jakarta.annotation.Nullable String messageID) {
            this.messageID = messageID;
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
            SessionDiffRequest request = (SessionDiffRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.messageID, request.messageID());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, directory, workspace, messageID);
        }
    }

    /**
     * Get message diff
     * Get the file changes (diff) that resulted from a specific user message in the session.
     * <p><b>200</b> - Successfully retrieved diff
     * <p><b>400</b> - Bad request
     * @param requestParameters The sessionDiff request parameters as object
     * @return List&lt;SnapshotFileDiff&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<SnapshotFileDiff> sessionDiff(SessionDiffRequest requestParameters) throws WebClientResponseException {
        return this.sessionDiff(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.messageID());
    }

    /**
     * Get message diff
     * Get the file changes (diff) that resulted from a specific user message in the session.
     * <p><b>200</b> - Successfully retrieved diff
     * <p><b>400</b> - Bad request
     * @param requestParameters The sessionDiff request parameters as object
     * @return ResponseEntity&lt;List&lt;SnapshotFileDiff&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<SnapshotFileDiff>>> sessionDiffWithHttpInfo(SessionDiffRequest requestParameters) throws WebClientResponseException {
        return this.sessionDiffWithHttpInfo(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.messageID());
    }

    /**
     * Get message diff
     * Get the file changes (diff) that resulted from a specific user message in the session.
     * <p><b>200</b> - Successfully retrieved diff
     * <p><b>400</b> - Bad request
     * @param requestParameters The sessionDiff request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionDiffWithResponseSpec(SessionDiffRequest requestParameters) throws WebClientResponseException {
        return this.sessionDiffWithResponseSpec(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.messageID());
    }


    /**
     * Get message diff
     * Get the file changes (diff) that resulted from a specific user message in the session.
     * <p><b>200</b> - Successfully retrieved diff
     * <p><b>400</b> - Bad request
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param messageID The messageID parameter
     * @return List&lt;SnapshotFileDiff&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionDiffRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable String messageID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionDiff", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", directory));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", workspace));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "messageID", messageID));

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<SnapshotFileDiff> localVarReturnType = new ParameterizedTypeReference<SnapshotFileDiff>() {};
        return apiClient.invokeAPI("/session/{sessionID}/diff", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get message diff
     * Get the file changes (diff) that resulted from a specific user message in the session.
     * <p><b>200</b> - Successfully retrieved diff
     * <p><b>400</b> - Bad request
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param messageID The messageID parameter
     * @return List&lt;SnapshotFileDiff&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<SnapshotFileDiff> sessionDiff(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable String messageID) throws WebClientResponseException {
        ParameterizedTypeReference<SnapshotFileDiff> localVarReturnType = new ParameterizedTypeReference<SnapshotFileDiff>() {};
        return sessionDiffRequestCreation(sessionID, directory, workspace, messageID).bodyToFlux(localVarReturnType);
    }

    /**
     * Get message diff
     * Get the file changes (diff) that resulted from a specific user message in the session.
     * <p><b>200</b> - Successfully retrieved diff
     * <p><b>400</b> - Bad request
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param messageID The messageID parameter
     * @return ResponseEntity&lt;List&lt;SnapshotFileDiff&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<SnapshotFileDiff>>> sessionDiffWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable String messageID) throws WebClientResponseException {
        ParameterizedTypeReference<SnapshotFileDiff> localVarReturnType = new ParameterizedTypeReference<SnapshotFileDiff>() {};
        return sessionDiffRequestCreation(sessionID, directory, workspace, messageID).toEntityList(localVarReturnType);
    }

    /**
     * Get message diff
     * Get the file changes (diff) that resulted from a specific user message in the session.
     * <p><b>200</b> - Successfully retrieved diff
     * <p><b>400</b> - Bad request
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param messageID The messageID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionDiffWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable String messageID) throws WebClientResponseException {
        return sessionDiffRequestCreation(sessionID, directory, workspace, messageID);
    }

    public class SessionForkRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable SessionForkRequest sessionForkRequest;

        public SessionForkRequest() {}

        public SessionForkRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionForkRequest sessionForkRequest) {
            this.sessionID = sessionID;
            this.directory = directory;
            this.workspace = workspace;
            this.sessionForkRequest = sessionForkRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionForkRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public SessionForkRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public SessionForkRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable SessionForkRequest sessionForkRequest() {
            return this.sessionForkRequest;
        }
        public SessionForkRequest sessionForkRequest(@jakarta.annotation.Nullable SessionForkRequest sessionForkRequest) {
            this.sessionForkRequest = sessionForkRequest;
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
            SessionForkRequest request = (SessionForkRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.sessionForkRequest, request.sessionForkRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, directory, workspace, sessionForkRequest);
        }
    }

    /**
     * Fork session
     * Create a new session by forking an existing session at a specific message point.
     * <p><b>200</b> - 200
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionFork request parameters as object
     * @return Session
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Session> sessionFork(SessionForkRequest requestParameters) throws WebClientResponseException {
        return this.sessionFork(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionForkRequest());
    }

    /**
     * Fork session
     * Create a new session by forking an existing session at a specific message point.
     * <p><b>200</b> - 200
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionFork request parameters as object
     * @return ResponseEntity&lt;Session&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Session>> sessionForkWithHttpInfo(SessionForkRequest requestParameters) throws WebClientResponseException {
        return this.sessionForkWithHttpInfo(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionForkRequest());
    }

    /**
     * Fork session
     * Create a new session by forking an existing session at a specific message point.
     * <p><b>200</b> - 200
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionFork request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionForkWithResponseSpec(SessionForkRequest requestParameters) throws WebClientResponseException {
        return this.sessionForkWithResponseSpec(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionForkRequest());
    }


    /**
     * Fork session
     * Create a new session by forking an existing session at a specific message point.
     * <p><b>200</b> - 200
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionForkRequest The sessionForkRequest parameter
     * @return Session
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionForkRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionForkRequest sessionForkRequest) throws WebClientResponseException {
        Object postBody = sessionForkRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionFork", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

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

        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return apiClient.invokeAPI("/session/{sessionID}/fork", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Fork session
     * Create a new session by forking an existing session at a specific message point.
     * <p><b>200</b> - 200
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionForkRequest The sessionForkRequest parameter
     * @return Session
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Session> sessionFork(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionForkRequest sessionForkRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return sessionForkRequestCreation(sessionID, directory, workspace, sessionForkRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Fork session
     * Create a new session by forking an existing session at a specific message point.
     * <p><b>200</b> - 200
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionForkRequest The sessionForkRequest parameter
     * @return ResponseEntity&lt;Session&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Session>> sessionForkWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionForkRequest sessionForkRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return sessionForkRequestCreation(sessionID, directory, workspace, sessionForkRequest).toEntity(localVarReturnType);
    }

    /**
     * Fork session
     * Create a new session by forking an existing session at a specific message point.
     * <p><b>200</b> - 200
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionForkRequest The sessionForkRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionForkWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionForkRequest sessionForkRequest) throws WebClientResponseException {
        return sessionForkRequestCreation(sessionID, directory, workspace, sessionForkRequest);
    }

    public class SessionGetRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public SessionGetRequest() {}

        public SessionGetRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.sessionID = sessionID;
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionGetRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public SessionGetRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public SessionGetRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            SessionGetRequest request = (SessionGetRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, directory, workspace);
        }
    }

    /**
     * Get session
     * Retrieve detailed information about a specific OpenCode session.
     * <p><b>200</b> - Get session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionGet request parameters as object
     * @return Session
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Session> sessionGet(SessionGetRequest requestParameters) throws WebClientResponseException {
        return this.sessionGet(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get session
     * Retrieve detailed information about a specific OpenCode session.
     * <p><b>200</b> - Get session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionGet request parameters as object
     * @return ResponseEntity&lt;Session&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Session>> sessionGetWithHttpInfo(SessionGetRequest requestParameters) throws WebClientResponseException {
        return this.sessionGetWithHttpInfo(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get session
     * Retrieve detailed information about a specific OpenCode session.
     * <p><b>200</b> - Get session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionGet request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionGetWithResponseSpec(SessionGetRequest requestParameters) throws WebClientResponseException {
        return this.sessionGetWithResponseSpec(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Get session
     * Retrieve detailed information about a specific OpenCode session.
     * <p><b>200</b> - Get session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Session
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionGetRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionGet", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

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

        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return apiClient.invokeAPI("/session/{sessionID}", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get session
     * Retrieve detailed information about a specific OpenCode session.
     * <p><b>200</b> - Get session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Session
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Session> sessionGet(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return sessionGetRequestCreation(sessionID, directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Get session
     * Retrieve detailed information about a specific OpenCode session.
     * <p><b>200</b> - Get session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Session&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Session>> sessionGetWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return sessionGetRequestCreation(sessionID, directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Get session
     * Retrieve detailed information about a specific OpenCode session.
     * <p><b>200</b> - Get session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionGetWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return sessionGetRequestCreation(sessionID, directory, workspace);
    }

    public class SessionInitRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable SessionInitRequest sessionInitRequest;

        public SessionInitRequest() {}

        public SessionInitRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionInitRequest sessionInitRequest) {
            this.sessionID = sessionID;
            this.directory = directory;
            this.workspace = workspace;
            this.sessionInitRequest = sessionInitRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionInitRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public SessionInitRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public SessionInitRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable SessionInitRequest sessionInitRequest() {
            return this.sessionInitRequest;
        }
        public SessionInitRequest sessionInitRequest(@jakarta.annotation.Nullable SessionInitRequest sessionInitRequest) {
            this.sessionInitRequest = sessionInitRequest;
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
            SessionInitRequest request = (SessionInitRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.sessionInitRequest, request.sessionInitRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, directory, workspace, sessionInitRequest);
        }
    }

    /**
     * Initialize session
     * Analyze the current application and create an AGENTS.md file with project-specific agent configurations.
     * <p><b>200</b> - 200
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionInit request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> sessionInit(SessionInitRequest requestParameters) throws WebClientResponseException {
        return this.sessionInit(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionInitRequest());
    }

    /**
     * Initialize session
     * Analyze the current application and create an AGENTS.md file with project-specific agent configurations.
     * <p><b>200</b> - 200
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionInit request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> sessionInitWithHttpInfo(SessionInitRequest requestParameters) throws WebClientResponseException {
        return this.sessionInitWithHttpInfo(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionInitRequest());
    }

    /**
     * Initialize session
     * Analyze the current application and create an AGENTS.md file with project-specific agent configurations.
     * <p><b>200</b> - 200
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionInit request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionInitWithResponseSpec(SessionInitRequest requestParameters) throws WebClientResponseException {
        return this.sessionInitWithResponseSpec(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionInitRequest());
    }


    /**
     * Initialize session
     * Analyze the current application and create an AGENTS.md file with project-specific agent configurations.
     * <p><b>200</b> - 200
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionInitRequest The sessionInitRequest parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionInitRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionInitRequest sessionInitRequest) throws WebClientResponseException {
        Object postBody = sessionInitRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionInit", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

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
        return apiClient.invokeAPI("/session/{sessionID}/init", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Initialize session
     * Analyze the current application and create an AGENTS.md file with project-specific agent configurations.
     * <p><b>200</b> - 200
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionInitRequest The sessionInitRequest parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> sessionInit(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionInitRequest sessionInitRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return sessionInitRequestCreation(sessionID, directory, workspace, sessionInitRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Initialize session
     * Analyze the current application and create an AGENTS.md file with project-specific agent configurations.
     * <p><b>200</b> - 200
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionInitRequest The sessionInitRequest parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> sessionInitWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionInitRequest sessionInitRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return sessionInitRequestCreation(sessionID, directory, workspace, sessionInitRequest).toEntity(localVarReturnType);
    }

    /**
     * Initialize session
     * Analyze the current application and create an AGENTS.md file with project-specific agent configurations.
     * <p><b>200</b> - 200
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionInitRequest The sessionInitRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionInitWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionInitRequest sessionInitRequest) throws WebClientResponseException {
        return sessionInitRequestCreation(sessionID, directory, workspace, sessionInitRequest);
    }

    public class SessionListRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable String scope;
        private @jakarta.annotation.Nullable String path;
        private @jakarta.annotation.Nullable ExperimentalSessionListRootsParameter roots;
        private @jakarta.annotation.Nullable BigDecimal start;
        private @jakarta.annotation.Nullable String search;
        private @jakarta.annotation.Nullable BigDecimal limit;

        public SessionListRequest() {}

        public SessionListRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable String scope, @jakarta.annotation.Nullable String path, @jakarta.annotation.Nullable ExperimentalSessionListRootsParameter roots, @jakarta.annotation.Nullable BigDecimal start, @jakarta.annotation.Nullable String search, @jakarta.annotation.Nullable BigDecimal limit) {
            this.directory = directory;
            this.workspace = workspace;
            this.scope = scope;
            this.path = path;
            this.roots = roots;
            this.start = start;
            this.search = search;
            this.limit = limit;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public SessionListRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public SessionListRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable String scope() {
            return this.scope;
        }
        public SessionListRequest scope(@jakarta.annotation.Nullable String scope) {
            this.scope = scope;
            return this;
        }

        public @jakarta.annotation.Nullable String path() {
            return this.path;
        }
        public SessionListRequest path(@jakarta.annotation.Nullable String path) {
            this.path = path;
            return this;
        }

        public @jakarta.annotation.Nullable ExperimentalSessionListRootsParameter roots() {
            return this.roots;
        }
        public SessionListRequest roots(@jakarta.annotation.Nullable ExperimentalSessionListRootsParameter roots) {
            this.roots = roots;
            return this;
        }

        public @jakarta.annotation.Nullable BigDecimal start() {
            return this.start;
        }
        public SessionListRequest start(@jakarta.annotation.Nullable BigDecimal start) {
            this.start = start;
            return this;
        }

        public @jakarta.annotation.Nullable String search() {
            return this.search;
        }
        public SessionListRequest search(@jakarta.annotation.Nullable String search) {
            this.search = search;
            return this;
        }

        public @jakarta.annotation.Nullable BigDecimal limit() {
            return this.limit;
        }
        public SessionListRequest limit(@jakarta.annotation.Nullable BigDecimal limit) {
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
            SessionListRequest request = (SessionListRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.scope, request.scope()) &&
                Objects.equals(this.path, request.path()) &&
                Objects.equals(this.roots, request.roots()) &&
                Objects.equals(this.start, request.start()) &&
                Objects.equals(this.search, request.search()) &&
                Objects.equals(this.limit, request.limit());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace, scope, path, roots, start, search, limit);
        }
    }

    /**
     * List sessions
     * Get a list of all OpenCode sessions, sorted by most recently updated.
     * <p><b>200</b> - List of sessions
     * <p><b>400</b> - Bad request
     * @param requestParameters The sessionList request parameters as object
     * @return List&lt;Session&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<Session> sessionList(SessionListRequest requestParameters) throws WebClientResponseException {
        return this.sessionList(requestParameters.directory(), requestParameters.workspace(), requestParameters.scope(), requestParameters.path(), requestParameters.roots(), requestParameters.start(), requestParameters.search(), requestParameters.limit());
    }

    /**
     * List sessions
     * Get a list of all OpenCode sessions, sorted by most recently updated.
     * <p><b>200</b> - List of sessions
     * <p><b>400</b> - Bad request
     * @param requestParameters The sessionList request parameters as object
     * @return ResponseEntity&lt;List&lt;Session&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<Session>>> sessionListWithHttpInfo(SessionListRequest requestParameters) throws WebClientResponseException {
        return this.sessionListWithHttpInfo(requestParameters.directory(), requestParameters.workspace(), requestParameters.scope(), requestParameters.path(), requestParameters.roots(), requestParameters.start(), requestParameters.search(), requestParameters.limit());
    }

    /**
     * List sessions
     * Get a list of all OpenCode sessions, sorted by most recently updated.
     * <p><b>200</b> - List of sessions
     * <p><b>400</b> - Bad request
     * @param requestParameters The sessionList request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionListWithResponseSpec(SessionListRequest requestParameters) throws WebClientResponseException {
        return this.sessionListWithResponseSpec(requestParameters.directory(), requestParameters.workspace(), requestParameters.scope(), requestParameters.path(), requestParameters.roots(), requestParameters.start(), requestParameters.search(), requestParameters.limit());
    }


    /**
     * List sessions
     * Get a list of all OpenCode sessions, sorted by most recently updated.
     * <p><b>200</b> - List of sessions
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param scope The scope parameter
     * @param path The path parameter
     * @param roots The roots parameter
     * @param start The start parameter
     * @param search The search parameter
     * @param limit The limit parameter
     * @return List&lt;Session&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionListRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable String scope, @jakarta.annotation.Nullable String path, @jakarta.annotation.Nullable ExperimentalSessionListRootsParameter roots, @jakarta.annotation.Nullable BigDecimal start, @jakarta.annotation.Nullable String search, @jakarta.annotation.Nullable BigDecimal limit) throws WebClientResponseException {
        Object postBody = null;
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", directory));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", workspace));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "scope", scope));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "path", path));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "roots", roots));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "start", start));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "search", search));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "limit", limit));

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return apiClient.invokeAPI("/session", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List sessions
     * Get a list of all OpenCode sessions, sorted by most recently updated.
     * <p><b>200</b> - List of sessions
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param scope The scope parameter
     * @param path The path parameter
     * @param roots The roots parameter
     * @param start The start parameter
     * @param search The search parameter
     * @param limit The limit parameter
     * @return List&lt;Session&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<Session> sessionList(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable String scope, @jakarta.annotation.Nullable String path, @jakarta.annotation.Nullable ExperimentalSessionListRootsParameter roots, @jakarta.annotation.Nullable BigDecimal start, @jakarta.annotation.Nullable String search, @jakarta.annotation.Nullable BigDecimal limit) throws WebClientResponseException {
        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return sessionListRequestCreation(directory, workspace, scope, path, roots, start, search, limit).bodyToFlux(localVarReturnType);
    }

    /**
     * List sessions
     * Get a list of all OpenCode sessions, sorted by most recently updated.
     * <p><b>200</b> - List of sessions
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param scope The scope parameter
     * @param path The path parameter
     * @param roots The roots parameter
     * @param start The start parameter
     * @param search The search parameter
     * @param limit The limit parameter
     * @return ResponseEntity&lt;List&lt;Session&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<Session>>> sessionListWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable String scope, @jakarta.annotation.Nullable String path, @jakarta.annotation.Nullable ExperimentalSessionListRootsParameter roots, @jakarta.annotation.Nullable BigDecimal start, @jakarta.annotation.Nullable String search, @jakarta.annotation.Nullable BigDecimal limit) throws WebClientResponseException {
        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return sessionListRequestCreation(directory, workspace, scope, path, roots, start, search, limit).toEntityList(localVarReturnType);
    }

    /**
     * List sessions
     * Get a list of all OpenCode sessions, sorted by most recently updated.
     * <p><b>200</b> - List of sessions
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param scope The scope parameter
     * @param path The path parameter
     * @param roots The roots parameter
     * @param start The start parameter
     * @param search The search parameter
     * @param limit The limit parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionListWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable String scope, @jakarta.annotation.Nullable String path, @jakarta.annotation.Nullable ExperimentalSessionListRootsParameter roots, @jakarta.annotation.Nullable BigDecimal start, @jakarta.annotation.Nullable String search, @jakarta.annotation.Nullable BigDecimal limit) throws WebClientResponseException {
        return sessionListRequestCreation(directory, workspace, scope, path, roots, start, search, limit);
    }

    public class SessionMessageRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull String messageID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public SessionMessageRequest() {}

        public SessionMessageRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String messageID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.sessionID = sessionID;
            this.messageID = messageID;
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionMessageRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull String messageID() {
            return this.messageID;
        }
        public SessionMessageRequest messageID(@jakarta.annotation.Nonnull String messageID) {
            this.messageID = messageID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public SessionMessageRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public SessionMessageRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            SessionMessageRequest request = (SessionMessageRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.messageID, request.messageID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, messageID, directory, workspace);
        }
    }

    /**
     * Get message
     * Retrieve a specific message from a session by its message ID.
     * <p><b>200</b> - Message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionMessage request parameters as object
     * @return SessionMessages200ResponseInner
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionMessages200ResponseInner> sessionMessage(SessionMessageRequest requestParameters) throws WebClientResponseException {
        return this.sessionMessage(requestParameters.sessionID(), requestParameters.messageID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get message
     * Retrieve a specific message from a session by its message ID.
     * <p><b>200</b> - Message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionMessage request parameters as object
     * @return ResponseEntity&lt;SessionMessages200ResponseInner&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionMessages200ResponseInner>> sessionMessageWithHttpInfo(SessionMessageRequest requestParameters) throws WebClientResponseException {
        return this.sessionMessageWithHttpInfo(requestParameters.sessionID(), requestParameters.messageID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get message
     * Retrieve a specific message from a session by its message ID.
     * <p><b>200</b> - Message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionMessage request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionMessageWithResponseSpec(SessionMessageRequest requestParameters) throws WebClientResponseException {
        return this.sessionMessageWithResponseSpec(requestParameters.sessionID(), requestParameters.messageID(), requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Get message
     * Retrieve a specific message from a session by its message ID.
     * <p><b>200</b> - Message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param messageID The messageID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return SessionMessages200ResponseInner
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionMessageRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String messageID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionMessage", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'messageID' is set
        if (messageID == null) {
            throw new WebClientResponseException("Missing the required parameter 'messageID' when calling sessionMessage", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);
        pathParams.put("messageID", messageID);

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

        ParameterizedTypeReference<SessionMessages200ResponseInner> localVarReturnType = new ParameterizedTypeReference<SessionMessages200ResponseInner>() {};
        return apiClient.invokeAPI("/session/{sessionID}/message/{messageID}", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get message
     * Retrieve a specific message from a session by its message ID.
     * <p><b>200</b> - Message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param messageID The messageID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return SessionMessages200ResponseInner
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionMessages200ResponseInner> sessionMessage(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String messageID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<SessionMessages200ResponseInner> localVarReturnType = new ParameterizedTypeReference<SessionMessages200ResponseInner>() {};
        return sessionMessageRequestCreation(sessionID, messageID, directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Get message
     * Retrieve a specific message from a session by its message ID.
     * <p><b>200</b> - Message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param messageID The messageID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;SessionMessages200ResponseInner&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionMessages200ResponseInner>> sessionMessageWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String messageID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<SessionMessages200ResponseInner> localVarReturnType = new ParameterizedTypeReference<SessionMessages200ResponseInner>() {};
        return sessionMessageRequestCreation(sessionID, messageID, directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Get message
     * Retrieve a specific message from a session by its message ID.
     * <p><b>200</b> - Message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param messageID The messageID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionMessageWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String messageID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return sessionMessageRequestCreation(sessionID, messageID, directory, workspace);
    }

    public class SessionMessagesRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable Integer limit;
        private @jakarta.annotation.Nullable String before;

        public SessionMessagesRequest() {}

        public SessionMessagesRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Integer limit, @jakarta.annotation.Nullable String before) {
            this.sessionID = sessionID;
            this.directory = directory;
            this.workspace = workspace;
            this.limit = limit;
            this.before = before;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionMessagesRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public SessionMessagesRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public SessionMessagesRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable Integer limit() {
            return this.limit;
        }
        public SessionMessagesRequest limit(@jakarta.annotation.Nullable Integer limit) {
            this.limit = limit;
            return this;
        }

        public @jakarta.annotation.Nullable String before() {
            return this.before;
        }
        public SessionMessagesRequest before(@jakarta.annotation.Nullable String before) {
            this.before = before;
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
            SessionMessagesRequest request = (SessionMessagesRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.limit, request.limit()) &&
                Objects.equals(this.before, request.before());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, directory, workspace, limit, before);
        }
    }

    /**
     * Get session messages
     * Retrieve all messages in a session, including user prompts and AI responses.
     * <p><b>200</b> - List of messages
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionMessages request parameters as object
     * @return List&lt;SessionMessages200ResponseInner&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<SessionMessages200ResponseInner> sessionMessages(SessionMessagesRequest requestParameters) throws WebClientResponseException {
        return this.sessionMessages(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.limit(), requestParameters.before());
    }

    /**
     * Get session messages
     * Retrieve all messages in a session, including user prompts and AI responses.
     * <p><b>200</b> - List of messages
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionMessages request parameters as object
     * @return ResponseEntity&lt;List&lt;SessionMessages200ResponseInner&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<SessionMessages200ResponseInner>>> sessionMessagesWithHttpInfo(SessionMessagesRequest requestParameters) throws WebClientResponseException {
        return this.sessionMessagesWithHttpInfo(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.limit(), requestParameters.before());
    }

    /**
     * Get session messages
     * Retrieve all messages in a session, including user prompts and AI responses.
     * <p><b>200</b> - List of messages
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionMessages request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionMessagesWithResponseSpec(SessionMessagesRequest requestParameters) throws WebClientResponseException {
        return this.sessionMessagesWithResponseSpec(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.limit(), requestParameters.before());
    }


    /**
     * Get session messages
     * Retrieve all messages in a session, including user prompts and AI responses.
     * <p><b>200</b> - List of messages
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param limit The limit parameter
     * @param before The before parameter
     * @return List&lt;SessionMessages200ResponseInner&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionMessagesRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Integer limit, @jakarta.annotation.Nullable String before) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionMessages", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", directory));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", workspace));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "limit", limit));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "before", before));

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<SessionMessages200ResponseInner> localVarReturnType = new ParameterizedTypeReference<SessionMessages200ResponseInner>() {};
        return apiClient.invokeAPI("/session/{sessionID}/message", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get session messages
     * Retrieve all messages in a session, including user prompts and AI responses.
     * <p><b>200</b> - List of messages
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param limit The limit parameter
     * @param before The before parameter
     * @return List&lt;SessionMessages200ResponseInner&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<SessionMessages200ResponseInner> sessionMessages(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Integer limit, @jakarta.annotation.Nullable String before) throws WebClientResponseException {
        ParameterizedTypeReference<SessionMessages200ResponseInner> localVarReturnType = new ParameterizedTypeReference<SessionMessages200ResponseInner>() {};
        return sessionMessagesRequestCreation(sessionID, directory, workspace, limit, before).bodyToFlux(localVarReturnType);
    }

    /**
     * Get session messages
     * Retrieve all messages in a session, including user prompts and AI responses.
     * <p><b>200</b> - List of messages
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param limit The limit parameter
     * @param before The before parameter
     * @return ResponseEntity&lt;List&lt;SessionMessages200ResponseInner&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<SessionMessages200ResponseInner>>> sessionMessagesWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Integer limit, @jakarta.annotation.Nullable String before) throws WebClientResponseException {
        ParameterizedTypeReference<SessionMessages200ResponseInner> localVarReturnType = new ParameterizedTypeReference<SessionMessages200ResponseInner>() {};
        return sessionMessagesRequestCreation(sessionID, directory, workspace, limit, before).toEntityList(localVarReturnType);
    }

    /**
     * Get session messages
     * Retrieve all messages in a session, including user prompts and AI responses.
     * <p><b>200</b> - List of messages
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param limit The limit parameter
     * @param before The before parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionMessagesWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Integer limit, @jakarta.annotation.Nullable String before) throws WebClientResponseException {
        return sessionMessagesRequestCreation(sessionID, directory, workspace, limit, before);
    }

    public class SessionPromptRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable SessionPromptRequest sessionPromptRequest;

        public SessionPromptRequest() {}

        public SessionPromptRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionPromptRequest sessionPromptRequest) {
            this.sessionID = sessionID;
            this.directory = directory;
            this.workspace = workspace;
            this.sessionPromptRequest = sessionPromptRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionPromptRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public SessionPromptRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public SessionPromptRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable SessionPromptRequest sessionPromptRequest() {
            return this.sessionPromptRequest;
        }
        public SessionPromptRequest sessionPromptRequest(@jakarta.annotation.Nullable SessionPromptRequest sessionPromptRequest) {
            this.sessionPromptRequest = sessionPromptRequest;
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
            SessionPromptRequest request = (SessionPromptRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.sessionPromptRequest, request.sessionPromptRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, directory, workspace, sessionPromptRequest);
        }
    }

    /**
     * Send message
     * Create and send a new message to a session, streaming the AI response.
     * <p><b>200</b> - Created message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionPrompt request parameters as object
     * @return SessionPrompt200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionPrompt200Response> sessionPrompt(SessionPromptRequest requestParameters) throws WebClientResponseException {
        return this.sessionPrompt(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionPromptRequest());
    }

    /**
     * Send message
     * Create and send a new message to a session, streaming the AI response.
     * <p><b>200</b> - Created message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionPrompt request parameters as object
     * @return ResponseEntity&lt;SessionPrompt200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionPrompt200Response>> sessionPromptWithHttpInfo(SessionPromptRequest requestParameters) throws WebClientResponseException {
        return this.sessionPromptWithHttpInfo(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionPromptRequest());
    }

    /**
     * Send message
     * Create and send a new message to a session, streaming the AI response.
     * <p><b>200</b> - Created message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionPrompt request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionPromptWithResponseSpec(SessionPromptRequest requestParameters) throws WebClientResponseException {
        return this.sessionPromptWithResponseSpec(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionPromptRequest());
    }


    /**
     * Send message
     * Create and send a new message to a session, streaming the AI response.
     * <p><b>200</b> - Created message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionPromptRequest The sessionPromptRequest parameter
     * @return SessionPrompt200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionPromptRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionPromptRequest sessionPromptRequest) throws WebClientResponseException {
        Object postBody = sessionPromptRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionPrompt", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

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

        ParameterizedTypeReference<SessionPrompt200Response> localVarReturnType = new ParameterizedTypeReference<SessionPrompt200Response>() {};
        return apiClient.invokeAPI("/session/{sessionID}/message", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Send message
     * Create and send a new message to a session, streaming the AI response.
     * <p><b>200</b> - Created message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionPromptRequest The sessionPromptRequest parameter
     * @return SessionPrompt200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionPrompt200Response> sessionPrompt(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionPromptRequest sessionPromptRequest) throws WebClientResponseException {
        ParameterizedTypeReference<SessionPrompt200Response> localVarReturnType = new ParameterizedTypeReference<SessionPrompt200Response>() {};
        return sessionPromptRequestCreation(sessionID, directory, workspace, sessionPromptRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Send message
     * Create and send a new message to a session, streaming the AI response.
     * <p><b>200</b> - Created message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionPromptRequest The sessionPromptRequest parameter
     * @return ResponseEntity&lt;SessionPrompt200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionPrompt200Response>> sessionPromptWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionPromptRequest sessionPromptRequest) throws WebClientResponseException {
        ParameterizedTypeReference<SessionPrompt200Response> localVarReturnType = new ParameterizedTypeReference<SessionPrompt200Response>() {};
        return sessionPromptRequestCreation(sessionID, directory, workspace, sessionPromptRequest).toEntity(localVarReturnType);
    }

    /**
     * Send message
     * Create and send a new message to a session, streaming the AI response.
     * <p><b>200</b> - Created message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionPromptRequest The sessionPromptRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionPromptWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionPromptRequest sessionPromptRequest) throws WebClientResponseException {
        return sessionPromptRequestCreation(sessionID, directory, workspace, sessionPromptRequest);
    }

    public class SessionPromptAsyncRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable SessionPromptAsyncRequest sessionPromptAsyncRequest;

        public SessionPromptAsyncRequest() {}

        public SessionPromptAsyncRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionPromptAsyncRequest sessionPromptAsyncRequest) {
            this.sessionID = sessionID;
            this.directory = directory;
            this.workspace = workspace;
            this.sessionPromptAsyncRequest = sessionPromptAsyncRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionPromptAsyncRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public SessionPromptAsyncRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public SessionPromptAsyncRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable SessionPromptAsyncRequest sessionPromptAsyncRequest() {
            return this.sessionPromptAsyncRequest;
        }
        public SessionPromptAsyncRequest sessionPromptAsyncRequest(@jakarta.annotation.Nullable SessionPromptAsyncRequest sessionPromptAsyncRequest) {
            this.sessionPromptAsyncRequest = sessionPromptAsyncRequest;
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
            SessionPromptAsyncRequest request = (SessionPromptAsyncRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.sessionPromptAsyncRequest, request.sessionPromptAsyncRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, directory, workspace, sessionPromptAsyncRequest);
        }
    }

    /**
     * Send async message
     * Create and send a new message to a session asynchronously, starting the session if needed and returning immediately.
     * <p><b>204</b> - Prompt accepted
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionPromptAsync request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionPromptAsync(SessionPromptAsyncRequest requestParameters) throws WebClientResponseException {
        return this.sessionPromptAsync(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionPromptAsyncRequest());
    }

    /**
     * Send async message
     * Create and send a new message to a session asynchronously, starting the session if needed and returning immediately.
     * <p><b>204</b> - Prompt accepted
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionPromptAsync request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionPromptAsyncWithHttpInfo(SessionPromptAsyncRequest requestParameters) throws WebClientResponseException {
        return this.sessionPromptAsyncWithHttpInfo(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionPromptAsyncRequest());
    }

    /**
     * Send async message
     * Create and send a new message to a session asynchronously, starting the session if needed and returning immediately.
     * <p><b>204</b> - Prompt accepted
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionPromptAsync request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionPromptAsyncWithResponseSpec(SessionPromptAsyncRequest requestParameters) throws WebClientResponseException {
        return this.sessionPromptAsyncWithResponseSpec(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionPromptAsyncRequest());
    }


    /**
     * Send async message
     * Create and send a new message to a session asynchronously, starting the session if needed and returning immediately.
     * <p><b>204</b> - Prompt accepted
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionPromptAsyncRequest The sessionPromptAsyncRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionPromptAsyncRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionPromptAsyncRequest sessionPromptAsyncRequest) throws WebClientResponseException {
        Object postBody = sessionPromptAsyncRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionPromptAsync", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

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
        return apiClient.invokeAPI("/session/{sessionID}/prompt_async", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Send async message
     * Create and send a new message to a session asynchronously, starting the session if needed and returning immediately.
     * <p><b>204</b> - Prompt accepted
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionPromptAsyncRequest The sessionPromptAsyncRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionPromptAsync(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionPromptAsyncRequest sessionPromptAsyncRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionPromptAsyncRequestCreation(sessionID, directory, workspace, sessionPromptAsyncRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Send async message
     * Create and send a new message to a session asynchronously, starting the session if needed and returning immediately.
     * <p><b>204</b> - Prompt accepted
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionPromptAsyncRequest The sessionPromptAsyncRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionPromptAsyncWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionPromptAsyncRequest sessionPromptAsyncRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionPromptAsyncRequestCreation(sessionID, directory, workspace, sessionPromptAsyncRequest).toEntity(localVarReturnType);
    }

    /**
     * Send async message
     * Create and send a new message to a session asynchronously, starting the session if needed and returning immediately.
     * <p><b>204</b> - Prompt accepted
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionPromptAsyncRequest The sessionPromptAsyncRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionPromptAsyncWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionPromptAsyncRequest sessionPromptAsyncRequest) throws WebClientResponseException {
        return sessionPromptAsyncRequestCreation(sessionID, directory, workspace, sessionPromptAsyncRequest);
    }

    public class SessionRevertRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable SessionRevertRequest sessionRevertRequest;

        public SessionRevertRequest() {}

        public SessionRevertRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionRevertRequest sessionRevertRequest) {
            this.sessionID = sessionID;
            this.directory = directory;
            this.workspace = workspace;
            this.sessionRevertRequest = sessionRevertRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionRevertRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public SessionRevertRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public SessionRevertRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable SessionRevertRequest sessionRevertRequest() {
            return this.sessionRevertRequest;
        }
        public SessionRevertRequest sessionRevertRequest(@jakarta.annotation.Nullable SessionRevertRequest sessionRevertRequest) {
            this.sessionRevertRequest = sessionRevertRequest;
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
            SessionRevertRequest request = (SessionRevertRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.sessionRevertRequest, request.sessionRevertRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, directory, workspace, sessionRevertRequest);
        }
    }

    /**
     * Revert message
     * Revert a specific message in a session, undoing its effects and restoring the previous state.
     * <p><b>200</b> - Updated session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param requestParameters The sessionRevert request parameters as object
     * @return Session
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Session> sessionRevert(SessionRevertRequest requestParameters) throws WebClientResponseException {
        return this.sessionRevert(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionRevertRequest());
    }

    /**
     * Revert message
     * Revert a specific message in a session, undoing its effects and restoring the previous state.
     * <p><b>200</b> - Updated session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param requestParameters The sessionRevert request parameters as object
     * @return ResponseEntity&lt;Session&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Session>> sessionRevertWithHttpInfo(SessionRevertRequest requestParameters) throws WebClientResponseException {
        return this.sessionRevertWithHttpInfo(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionRevertRequest());
    }

    /**
     * Revert message
     * Revert a specific message in a session, undoing its effects and restoring the previous state.
     * <p><b>200</b> - Updated session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param requestParameters The sessionRevert request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionRevertWithResponseSpec(SessionRevertRequest requestParameters) throws WebClientResponseException {
        return this.sessionRevertWithResponseSpec(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionRevertRequest());
    }


    /**
     * Revert message
     * Revert a specific message in a session, undoing its effects and restoring the previous state.
     * <p><b>200</b> - Updated session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionRevertRequest The sessionRevertRequest parameter
     * @return Session
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionRevertRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionRevertRequest sessionRevertRequest) throws WebClientResponseException {
        Object postBody = sessionRevertRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionRevert", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

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

        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return apiClient.invokeAPI("/session/{sessionID}/revert", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Revert message
     * Revert a specific message in a session, undoing its effects and restoring the previous state.
     * <p><b>200</b> - Updated session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionRevertRequest The sessionRevertRequest parameter
     * @return Session
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Session> sessionRevert(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionRevertRequest sessionRevertRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return sessionRevertRequestCreation(sessionID, directory, workspace, sessionRevertRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Revert message
     * Revert a specific message in a session, undoing its effects and restoring the previous state.
     * <p><b>200</b> - Updated session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionRevertRequest The sessionRevertRequest parameter
     * @return ResponseEntity&lt;Session&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Session>> sessionRevertWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionRevertRequest sessionRevertRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return sessionRevertRequestCreation(sessionID, directory, workspace, sessionRevertRequest).toEntity(localVarReturnType);
    }

    /**
     * Revert message
     * Revert a specific message in a session, undoing its effects and restoring the previous state.
     * <p><b>200</b> - Updated session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionRevertRequest The sessionRevertRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionRevertWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionRevertRequest sessionRevertRequest) throws WebClientResponseException {
        return sessionRevertRequestCreation(sessionID, directory, workspace, sessionRevertRequest);
    }

    public class SessionShareRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public SessionShareRequest() {}

        public SessionShareRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.sessionID = sessionID;
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionShareRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public SessionShareRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public SessionShareRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            SessionShareRequest request = (SessionShareRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, directory, workspace);
        }
    }

    /**
     * Share session
     * Create a shareable link for a session, allowing others to view the conversation.
     * <p><b>200</b> - Successfully shared session
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - NotFoundError
     * <p><b>500</b> - InternalServerError
     * @param requestParameters The sessionShare request parameters as object
     * @return Session
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Session> sessionShare(SessionShareRequest requestParameters) throws WebClientResponseException {
        return this.sessionShare(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Share session
     * Create a shareable link for a session, allowing others to view the conversation.
     * <p><b>200</b> - Successfully shared session
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - NotFoundError
     * <p><b>500</b> - InternalServerError
     * @param requestParameters The sessionShare request parameters as object
     * @return ResponseEntity&lt;Session&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Session>> sessionShareWithHttpInfo(SessionShareRequest requestParameters) throws WebClientResponseException {
        return this.sessionShareWithHttpInfo(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Share session
     * Create a shareable link for a session, allowing others to view the conversation.
     * <p><b>200</b> - Successfully shared session
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - NotFoundError
     * <p><b>500</b> - InternalServerError
     * @param requestParameters The sessionShare request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionShareWithResponseSpec(SessionShareRequest requestParameters) throws WebClientResponseException {
        return this.sessionShareWithResponseSpec(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Share session
     * Create a shareable link for a session, allowing others to view the conversation.
     * <p><b>200</b> - Successfully shared session
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - NotFoundError
     * <p><b>500</b> - InternalServerError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Session
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionShareRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionShare", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

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

        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return apiClient.invokeAPI("/session/{sessionID}/share", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Share session
     * Create a shareable link for a session, allowing others to view the conversation.
     * <p><b>200</b> - Successfully shared session
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - NotFoundError
     * <p><b>500</b> - InternalServerError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Session
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Session> sessionShare(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return sessionShareRequestCreation(sessionID, directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Share session
     * Create a shareable link for a session, allowing others to view the conversation.
     * <p><b>200</b> - Successfully shared session
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - NotFoundError
     * <p><b>500</b> - InternalServerError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Session&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Session>> sessionShareWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return sessionShareRequestCreation(sessionID, directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Share session
     * Create a shareable link for a session, allowing others to view the conversation.
     * <p><b>200</b> - Successfully shared session
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - NotFoundError
     * <p><b>500</b> - InternalServerError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionShareWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return sessionShareRequestCreation(sessionID, directory, workspace);
    }

    public class SessionShellRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable SessionShellRequest sessionShellRequest;

        public SessionShellRequest() {}

        public SessionShellRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionShellRequest sessionShellRequest) {
            this.sessionID = sessionID;
            this.directory = directory;
            this.workspace = workspace;
            this.sessionShellRequest = sessionShellRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionShellRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public SessionShellRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public SessionShellRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable SessionShellRequest sessionShellRequest() {
            return this.sessionShellRequest;
        }
        public SessionShellRequest sessionShellRequest(@jakarta.annotation.Nullable SessionShellRequest sessionShellRequest) {
            this.sessionShellRequest = sessionShellRequest;
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
            SessionShellRequest request = (SessionShellRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.sessionShellRequest, request.sessionShellRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, directory, workspace, sessionShellRequest);
        }
    }

    /**
     * Run shell command
     * Execute a shell command within the session context and return the AI&#39;s response.
     * <p><b>200</b> - Created message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param requestParameters The sessionShell request parameters as object
     * @return SessionMessages200ResponseInner
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionMessages200ResponseInner> sessionShell(SessionShellRequest requestParameters) throws WebClientResponseException {
        return this.sessionShell(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionShellRequest());
    }

    /**
     * Run shell command
     * Execute a shell command within the session context and return the AI&#39;s response.
     * <p><b>200</b> - Created message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param requestParameters The sessionShell request parameters as object
     * @return ResponseEntity&lt;SessionMessages200ResponseInner&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionMessages200ResponseInner>> sessionShellWithHttpInfo(SessionShellRequest requestParameters) throws WebClientResponseException {
        return this.sessionShellWithHttpInfo(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionShellRequest());
    }

    /**
     * Run shell command
     * Execute a shell command within the session context and return the AI&#39;s response.
     * <p><b>200</b> - Created message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param requestParameters The sessionShell request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionShellWithResponseSpec(SessionShellRequest requestParameters) throws WebClientResponseException {
        return this.sessionShellWithResponseSpec(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionShellRequest());
    }


    /**
     * Run shell command
     * Execute a shell command within the session context and return the AI&#39;s response.
     * <p><b>200</b> - Created message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionShellRequest The sessionShellRequest parameter
     * @return SessionMessages200ResponseInner
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionShellRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionShellRequest sessionShellRequest) throws WebClientResponseException {
        Object postBody = sessionShellRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionShell", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

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

        ParameterizedTypeReference<SessionMessages200ResponseInner> localVarReturnType = new ParameterizedTypeReference<SessionMessages200ResponseInner>() {};
        return apiClient.invokeAPI("/session/{sessionID}/shell", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Run shell command
     * Execute a shell command within the session context and return the AI&#39;s response.
     * <p><b>200</b> - Created message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionShellRequest The sessionShellRequest parameter
     * @return SessionMessages200ResponseInner
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionMessages200ResponseInner> sessionShell(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionShellRequest sessionShellRequest) throws WebClientResponseException {
        ParameterizedTypeReference<SessionMessages200ResponseInner> localVarReturnType = new ParameterizedTypeReference<SessionMessages200ResponseInner>() {};
        return sessionShellRequestCreation(sessionID, directory, workspace, sessionShellRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Run shell command
     * Execute a shell command within the session context and return the AI&#39;s response.
     * <p><b>200</b> - Created message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionShellRequest The sessionShellRequest parameter
     * @return ResponseEntity&lt;SessionMessages200ResponseInner&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionMessages200ResponseInner>> sessionShellWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionShellRequest sessionShellRequest) throws WebClientResponseException {
        ParameterizedTypeReference<SessionMessages200ResponseInner> localVarReturnType = new ParameterizedTypeReference<SessionMessages200ResponseInner>() {};
        return sessionShellRequestCreation(sessionID, directory, workspace, sessionShellRequest).toEntity(localVarReturnType);
    }

    /**
     * Run shell command
     * Execute a shell command within the session context and return the AI&#39;s response.
     * <p><b>200</b> - Created message
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionShellRequest The sessionShellRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionShellWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionShellRequest sessionShellRequest) throws WebClientResponseException {
        return sessionShellRequestCreation(sessionID, directory, workspace, sessionShellRequest);
    }

    public class SessionStatusRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public SessionStatusRequest() {}

        public SessionStatusRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public SessionStatusRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public SessionStatusRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            SessionStatusRequest request = (SessionStatusRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * Get session status
     * Retrieve the current status of all sessions, including active, idle, and completed states.
     * <p><b>200</b> - Get session status
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The sessionStatus request parameters as object
     * @return Map&lt;String, SessionStatus&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Map<String, SessionStatus>> sessionStatus(SessionStatusRequest requestParameters) throws WebClientResponseException {
        return this.sessionStatus(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get session status
     * Retrieve the current status of all sessions, including active, idle, and completed states.
     * <p><b>200</b> - Get session status
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The sessionStatus request parameters as object
     * @return ResponseEntity&lt;Map&lt;String, SessionStatus&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Map<String, SessionStatus>>> sessionStatusWithHttpInfo(SessionStatusRequest requestParameters) throws WebClientResponseException {
        return this.sessionStatusWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get session status
     * Retrieve the current status of all sessions, including active, idle, and completed states.
     * <p><b>200</b> - Get session status
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The sessionStatus request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionStatusWithResponseSpec(SessionStatusRequest requestParameters) throws WebClientResponseException {
        return this.sessionStatusWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Get session status
     * Retrieve the current status of all sessions, including active, idle, and completed states.
     * <p><b>200</b> - Get session status
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Map&lt;String, SessionStatus&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionStatusRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<Map<String, SessionStatus>> localVarReturnType = new ParameterizedTypeReference<Map<String, SessionStatus>>() {};
        return apiClient.invokeAPI("/session/status", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get session status
     * Retrieve the current status of all sessions, including active, idle, and completed states.
     * <p><b>200</b> - Get session status
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Map&lt;String, SessionStatus&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Map<String, SessionStatus>> sessionStatus(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Map<String, SessionStatus>> localVarReturnType = new ParameterizedTypeReference<Map<String, SessionStatus>>() {};
        return sessionStatusRequestCreation(directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Get session status
     * Retrieve the current status of all sessions, including active, idle, and completed states.
     * <p><b>200</b> - Get session status
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Map&lt;String, SessionStatus&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Map<String, SessionStatus>>> sessionStatusWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Map<String, SessionStatus>> localVarReturnType = new ParameterizedTypeReference<Map<String, SessionStatus>>() {};
        return sessionStatusRequestCreation(directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Get session status
     * Retrieve the current status of all sessions, including active, idle, and completed states.
     * <p><b>200</b> - Get session status
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionStatusWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return sessionStatusRequestCreation(directory, workspace);
    }

    public class SessionSummarizeRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable SessionSummarizeRequest sessionSummarizeRequest;

        public SessionSummarizeRequest() {}

        public SessionSummarizeRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionSummarizeRequest sessionSummarizeRequest) {
            this.sessionID = sessionID;
            this.directory = directory;
            this.workspace = workspace;
            this.sessionSummarizeRequest = sessionSummarizeRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionSummarizeRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public SessionSummarizeRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public SessionSummarizeRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable SessionSummarizeRequest sessionSummarizeRequest() {
            return this.sessionSummarizeRequest;
        }
        public SessionSummarizeRequest sessionSummarizeRequest(@jakarta.annotation.Nullable SessionSummarizeRequest sessionSummarizeRequest) {
            this.sessionSummarizeRequest = sessionSummarizeRequest;
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
            SessionSummarizeRequest request = (SessionSummarizeRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.sessionSummarizeRequest, request.sessionSummarizeRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, directory, workspace, sessionSummarizeRequest);
        }
    }

    /**
     * Summarize session
     * Generate a concise summary of the session using AI compaction to preserve key information.
     * <p><b>200</b> - Summarized session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionSummarize request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> sessionSummarize(SessionSummarizeRequest requestParameters) throws WebClientResponseException {
        return this.sessionSummarize(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionSummarizeRequest());
    }

    /**
     * Summarize session
     * Generate a concise summary of the session using AI compaction to preserve key information.
     * <p><b>200</b> - Summarized session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionSummarize request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> sessionSummarizeWithHttpInfo(SessionSummarizeRequest requestParameters) throws WebClientResponseException {
        return this.sessionSummarizeWithHttpInfo(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionSummarizeRequest());
    }

    /**
     * Summarize session
     * Generate a concise summary of the session using AI compaction to preserve key information.
     * <p><b>200</b> - Summarized session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionSummarize request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionSummarizeWithResponseSpec(SessionSummarizeRequest requestParameters) throws WebClientResponseException {
        return this.sessionSummarizeWithResponseSpec(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionSummarizeRequest());
    }


    /**
     * Summarize session
     * Generate a concise summary of the session using AI compaction to preserve key information.
     * <p><b>200</b> - Summarized session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionSummarizeRequest The sessionSummarizeRequest parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionSummarizeRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionSummarizeRequest sessionSummarizeRequest) throws WebClientResponseException {
        Object postBody = sessionSummarizeRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionSummarize", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

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
        return apiClient.invokeAPI("/session/{sessionID}/summarize", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Summarize session
     * Generate a concise summary of the session using AI compaction to preserve key information.
     * <p><b>200</b> - Summarized session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionSummarizeRequest The sessionSummarizeRequest parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> sessionSummarize(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionSummarizeRequest sessionSummarizeRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return sessionSummarizeRequestCreation(sessionID, directory, workspace, sessionSummarizeRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Summarize session
     * Generate a concise summary of the session using AI compaction to preserve key information.
     * <p><b>200</b> - Summarized session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionSummarizeRequest The sessionSummarizeRequest parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> sessionSummarizeWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionSummarizeRequest sessionSummarizeRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return sessionSummarizeRequestCreation(sessionID, directory, workspace, sessionSummarizeRequest).toEntity(localVarReturnType);
    }

    /**
     * Summarize session
     * Generate a concise summary of the session using AI compaction to preserve key information.
     * <p><b>200</b> - Summarized session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionSummarizeRequest The sessionSummarizeRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionSummarizeWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionSummarizeRequest sessionSummarizeRequest) throws WebClientResponseException {
        return sessionSummarizeRequestCreation(sessionID, directory, workspace, sessionSummarizeRequest);
    }

    public class SessionTodoRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public SessionTodoRequest() {}

        public SessionTodoRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.sessionID = sessionID;
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionTodoRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public SessionTodoRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public SessionTodoRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            SessionTodoRequest request = (SessionTodoRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, directory, workspace);
        }
    }

    /**
     * Get session todos
     * Retrieve the todo list associated with a specific session, showing tasks and action items.
     * <p><b>200</b> - Todo list
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionTodo request parameters as object
     * @return List&lt;Todo&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<Todo> sessionTodo(SessionTodoRequest requestParameters) throws WebClientResponseException {
        return this.sessionTodo(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get session todos
     * Retrieve the todo list associated with a specific session, showing tasks and action items.
     * <p><b>200</b> - Todo list
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionTodo request parameters as object
     * @return ResponseEntity&lt;List&lt;Todo&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<Todo>>> sessionTodoWithHttpInfo(SessionTodoRequest requestParameters) throws WebClientResponseException {
        return this.sessionTodoWithHttpInfo(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get session todos
     * Retrieve the todo list associated with a specific session, showing tasks and action items.
     * <p><b>200</b> - Todo list
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionTodo request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionTodoWithResponseSpec(SessionTodoRequest requestParameters) throws WebClientResponseException {
        return this.sessionTodoWithResponseSpec(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Get session todos
     * Retrieve the todo list associated with a specific session, showing tasks and action items.
     * <p><b>200</b> - Todo list
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;Todo&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionTodoRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionTodo", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

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

        ParameterizedTypeReference<Todo> localVarReturnType = new ParameterizedTypeReference<Todo>() {};
        return apiClient.invokeAPI("/session/{sessionID}/todo", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get session todos
     * Retrieve the todo list associated with a specific session, showing tasks and action items.
     * <p><b>200</b> - Todo list
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;Todo&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<Todo> sessionTodo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Todo> localVarReturnType = new ParameterizedTypeReference<Todo>() {};
        return sessionTodoRequestCreation(sessionID, directory, workspace).bodyToFlux(localVarReturnType);
    }

    /**
     * Get session todos
     * Retrieve the todo list associated with a specific session, showing tasks and action items.
     * <p><b>200</b> - Todo list
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;List&lt;Todo&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<Todo>>> sessionTodoWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Todo> localVarReturnType = new ParameterizedTypeReference<Todo>() {};
        return sessionTodoRequestCreation(sessionID, directory, workspace).toEntityList(localVarReturnType);
    }

    /**
     * Get session todos
     * Retrieve the todo list associated with a specific session, showing tasks and action items.
     * <p><b>200</b> - Todo list
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionTodoWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return sessionTodoRequestCreation(sessionID, directory, workspace);
    }

    public class SessionUnrevertRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public SessionUnrevertRequest() {}

        public SessionUnrevertRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.sessionID = sessionID;
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionUnrevertRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public SessionUnrevertRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public SessionUnrevertRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            SessionUnrevertRequest request = (SessionUnrevertRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, directory, workspace);
        }
    }

    /**
     * Restore reverted messages
     * Restore all previously reverted messages in a session.
     * <p><b>200</b> - Updated session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param requestParameters The sessionUnrevert request parameters as object
     * @return Session
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Session> sessionUnrevert(SessionUnrevertRequest requestParameters) throws WebClientResponseException {
        return this.sessionUnrevert(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Restore reverted messages
     * Restore all previously reverted messages in a session.
     * <p><b>200</b> - Updated session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param requestParameters The sessionUnrevert request parameters as object
     * @return ResponseEntity&lt;Session&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Session>> sessionUnrevertWithHttpInfo(SessionUnrevertRequest requestParameters) throws WebClientResponseException {
        return this.sessionUnrevertWithHttpInfo(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Restore reverted messages
     * Restore all previously reverted messages in a session.
     * <p><b>200</b> - Updated session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param requestParameters The sessionUnrevert request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionUnrevertWithResponseSpec(SessionUnrevertRequest requestParameters) throws WebClientResponseException {
        return this.sessionUnrevertWithResponseSpec(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Restore reverted messages
     * Restore all previously reverted messages in a session.
     * <p><b>200</b> - Updated session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Session
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionUnrevertRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionUnrevert", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

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

        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return apiClient.invokeAPI("/session/{sessionID}/unrevert", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Restore reverted messages
     * Restore all previously reverted messages in a session.
     * <p><b>200</b> - Updated session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Session
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Session> sessionUnrevert(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return sessionUnrevertRequestCreation(sessionID, directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Restore reverted messages
     * Restore all previously reverted messages in a session.
     * <p><b>200</b> - Updated session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Session&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Session>> sessionUnrevertWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return sessionUnrevertRequestCreation(sessionID, directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Restore reverted messages
     * Restore all previously reverted messages in a session.
     * <p><b>200</b> - Updated session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionUnrevertWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return sessionUnrevertRequestCreation(sessionID, directory, workspace);
    }

    public class SessionUnshareRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public SessionUnshareRequest() {}

        public SessionUnshareRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.sessionID = sessionID;
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionUnshareRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public SessionUnshareRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public SessionUnshareRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            SessionUnshareRequest request = (SessionUnshareRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, directory, workspace);
        }
    }

    /**
     * Unshare session
     * Remove the shareable link for a session, making it private again.
     * <p><b>200</b> - Successfully unshared session
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - NotFoundError
     * <p><b>500</b> - InternalServerError
     * @param requestParameters The sessionUnshare request parameters as object
     * @return Session
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Session> sessionUnshare(SessionUnshareRequest requestParameters) throws WebClientResponseException {
        return this.sessionUnshare(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Unshare session
     * Remove the shareable link for a session, making it private again.
     * <p><b>200</b> - Successfully unshared session
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - NotFoundError
     * <p><b>500</b> - InternalServerError
     * @param requestParameters The sessionUnshare request parameters as object
     * @return ResponseEntity&lt;Session&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Session>> sessionUnshareWithHttpInfo(SessionUnshareRequest requestParameters) throws WebClientResponseException {
        return this.sessionUnshareWithHttpInfo(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Unshare session
     * Remove the shareable link for a session, making it private again.
     * <p><b>200</b> - Successfully unshared session
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - NotFoundError
     * <p><b>500</b> - InternalServerError
     * @param requestParameters The sessionUnshare request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionUnshareWithResponseSpec(SessionUnshareRequest requestParameters) throws WebClientResponseException {
        return this.sessionUnshareWithResponseSpec(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Unshare session
     * Remove the shareable link for a session, making it private again.
     * <p><b>200</b> - Successfully unshared session
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - NotFoundError
     * <p><b>500</b> - InternalServerError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Session
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionUnshareRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionUnshare", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

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

        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return apiClient.invokeAPI("/session/{sessionID}/share", HttpMethod.DELETE, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Unshare session
     * Remove the shareable link for a session, making it private again.
     * <p><b>200</b> - Successfully unshared session
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - NotFoundError
     * <p><b>500</b> - InternalServerError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Session
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Session> sessionUnshare(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return sessionUnshareRequestCreation(sessionID, directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Unshare session
     * Remove the shareable link for a session, making it private again.
     * <p><b>200</b> - Successfully unshared session
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - NotFoundError
     * <p><b>500</b> - InternalServerError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Session&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Session>> sessionUnshareWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return sessionUnshareRequestCreation(sessionID, directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Unshare session
     * Remove the shareable link for a session, making it private again.
     * <p><b>200</b> - Successfully unshared session
     * <p><b>400</b> - Bad request
     * <p><b>404</b> - NotFoundError
     * <p><b>500</b> - InternalServerError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionUnshareWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return sessionUnshareRequestCreation(sessionID, directory, workspace);
    }

    public class SessionUpdateRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable SessionUpdateRequest sessionUpdateRequest;

        public SessionUpdateRequest() {}

        public SessionUpdateRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionUpdateRequest sessionUpdateRequest) {
            this.sessionID = sessionID;
            this.directory = directory;
            this.workspace = workspace;
            this.sessionUpdateRequest = sessionUpdateRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionUpdateRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public SessionUpdateRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public SessionUpdateRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable SessionUpdateRequest sessionUpdateRequest() {
            return this.sessionUpdateRequest;
        }
        public SessionUpdateRequest sessionUpdateRequest(@jakarta.annotation.Nullable SessionUpdateRequest sessionUpdateRequest) {
            this.sessionUpdateRequest = sessionUpdateRequest;
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
            SessionUpdateRequest request = (SessionUpdateRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.sessionUpdateRequest, request.sessionUpdateRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, directory, workspace, sessionUpdateRequest);
        }
    }

    /**
     * Update session
     * Update properties of an existing session, such as title or other metadata.
     * <p><b>200</b> - Successfully updated session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionUpdate request parameters as object
     * @return Session
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Session> sessionUpdate(SessionUpdateRequest requestParameters) throws WebClientResponseException {
        return this.sessionUpdate(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionUpdateRequest());
    }

    /**
     * Update session
     * Update properties of an existing session, such as title or other metadata.
     * <p><b>200</b> - Successfully updated session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionUpdate request parameters as object
     * @return ResponseEntity&lt;Session&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Session>> sessionUpdateWithHttpInfo(SessionUpdateRequest requestParameters) throws WebClientResponseException {
        return this.sessionUpdateWithHttpInfo(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionUpdateRequest());
    }

    /**
     * Update session
     * Update properties of an existing session, such as title or other metadata.
     * <p><b>200</b> - Successfully updated session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The sessionUpdate request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionUpdateWithResponseSpec(SessionUpdateRequest requestParameters) throws WebClientResponseException {
        return this.sessionUpdateWithResponseSpec(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.sessionUpdateRequest());
    }


    /**
     * Update session
     * Update properties of an existing session, such as title or other metadata.
     * <p><b>200</b> - Successfully updated session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionUpdateRequest The sessionUpdateRequest parameter
     * @return Session
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionUpdateRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionUpdateRequest sessionUpdateRequest) throws WebClientResponseException {
        Object postBody = sessionUpdateRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionUpdate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

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

        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return apiClient.invokeAPI("/session/{sessionID}", HttpMethod.PATCH, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Update session
     * Update properties of an existing session, such as title or other metadata.
     * <p><b>200</b> - Successfully updated session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionUpdateRequest The sessionUpdateRequest parameter
     * @return Session
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Session> sessionUpdate(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionUpdateRequest sessionUpdateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return sessionUpdateRequestCreation(sessionID, directory, workspace, sessionUpdateRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Update session
     * Update properties of an existing session, such as title or other metadata.
     * <p><b>200</b> - Successfully updated session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionUpdateRequest The sessionUpdateRequest parameter
     * @return ResponseEntity&lt;Session&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Session>> sessionUpdateWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionUpdateRequest sessionUpdateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Session> localVarReturnType = new ParameterizedTypeReference<Session>() {};
        return sessionUpdateRequestCreation(sessionID, directory, workspace, sessionUpdateRequest).toEntity(localVarReturnType);
    }

    /**
     * Update session
     * Update properties of an existing session, such as title or other metadata.
     * <p><b>200</b> - Successfully updated session
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param sessionUpdateRequest The sessionUpdateRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionUpdateWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SessionUpdateRequest sessionUpdateRequest) throws WebClientResponseException {
        return sessionUpdateRequestCreation(sessionID, directory, workspace, sessionUpdateRequest);
    }
}
