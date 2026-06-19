package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.AuthSet400Response;
import com.example.opencode.sdk.model.BadRequestError;
import com.example.opencode.sdk.model.QuestionNotFoundError;
import com.example.opencode.sdk.model.QuestionReplyRequest;
import com.example.opencode.sdk.model.QuestionRequest;

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
public class QuestionApi {
    private ApiClient apiClient;

    public QuestionApi() {
        this(new ApiClient());
    }

    public QuestionApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public class QuestionListRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public QuestionListRequest() {}

        public QuestionListRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public QuestionListRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public QuestionListRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            QuestionListRequest request = (QuestionListRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * List pending questions
     * Get all pending question requests across all sessions.
     * <p><b>200</b> - List of pending questions
     * <p><b>400</b> - Bad request
     * @param requestParameters The questionList request parameters as object
     * @return List&lt;QuestionRequest&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<QuestionRequest> questionList(QuestionListRequest requestParameters) throws WebClientResponseException {
        return this.questionList(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List pending questions
     * Get all pending question requests across all sessions.
     * <p><b>200</b> - List of pending questions
     * <p><b>400</b> - Bad request
     * @param requestParameters The questionList request parameters as object
     * @return ResponseEntity&lt;List&lt;QuestionRequest&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<QuestionRequest>>> questionListWithHttpInfo(QuestionListRequest requestParameters) throws WebClientResponseException {
        return this.questionListWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List pending questions
     * Get all pending question requests across all sessions.
     * <p><b>200</b> - List of pending questions
     * <p><b>400</b> - Bad request
     * @param requestParameters The questionList request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec questionListWithResponseSpec(QuestionListRequest requestParameters) throws WebClientResponseException {
        return this.questionListWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * List pending questions
     * Get all pending question requests across all sessions.
     * <p><b>200</b> - List of pending questions
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;QuestionRequest&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec questionListRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<QuestionRequest> localVarReturnType = new ParameterizedTypeReference<QuestionRequest>() {};
        return apiClient.invokeAPI("/question", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List pending questions
     * Get all pending question requests across all sessions.
     * <p><b>200</b> - List of pending questions
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;QuestionRequest&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<QuestionRequest> questionList(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<QuestionRequest> localVarReturnType = new ParameterizedTypeReference<QuestionRequest>() {};
        return questionListRequestCreation(directory, workspace).bodyToFlux(localVarReturnType);
    }

    /**
     * List pending questions
     * Get all pending question requests across all sessions.
     * <p><b>200</b> - List of pending questions
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;List&lt;QuestionRequest&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<QuestionRequest>>> questionListWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<QuestionRequest> localVarReturnType = new ParameterizedTypeReference<QuestionRequest>() {};
        return questionListRequestCreation(directory, workspace).toEntityList(localVarReturnType);
    }

    /**
     * List pending questions
     * Get all pending question requests across all sessions.
     * <p><b>200</b> - List of pending questions
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec questionListWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return questionListRequestCreation(directory, workspace);
    }

    public class QuestionRejectRequest {
        private @jakarta.annotation.Nonnull String requestID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public QuestionRejectRequest() {}

        public QuestionRejectRequest(@jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.requestID = requestID;
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nonnull String requestID() {
            return this.requestID;
        }
        public QuestionRejectRequest requestID(@jakarta.annotation.Nonnull String requestID) {
            this.requestID = requestID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public QuestionRejectRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public QuestionRejectRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            QuestionRejectRequest request = (QuestionRejectRequest) o;
            return Objects.equals(this.requestID, request.requestID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(requestID, directory, workspace);
        }
    }

    /**
     * Reject question request
     * Reject a question request from the AI assistant.
     * <p><b>200</b> - Question rejected successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - QuestionNotFoundError
     * @param requestParameters The questionReject request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> questionReject(QuestionRejectRequest requestParameters) throws WebClientResponseException {
        return this.questionReject(requestParameters.requestID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Reject question request
     * Reject a question request from the AI assistant.
     * <p><b>200</b> - Question rejected successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - QuestionNotFoundError
     * @param requestParameters The questionReject request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> questionRejectWithHttpInfo(QuestionRejectRequest requestParameters) throws WebClientResponseException {
        return this.questionRejectWithHttpInfo(requestParameters.requestID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Reject question request
     * Reject a question request from the AI assistant.
     * <p><b>200</b> - Question rejected successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - QuestionNotFoundError
     * @param requestParameters The questionReject request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec questionRejectWithResponseSpec(QuestionRejectRequest requestParameters) throws WebClientResponseException {
        return this.questionRejectWithResponseSpec(requestParameters.requestID(), requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Reject question request
     * Reject a question request from the AI assistant.
     * <p><b>200</b> - Question rejected successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - QuestionNotFoundError
     * @param requestID The requestID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec questionRejectRequestCreation(@jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'requestID' is set
        if (requestID == null) {
            throw new WebClientResponseException("Missing the required parameter 'requestID' when calling questionReject", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return apiClient.invokeAPI("/question/{requestID}/reject", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Reject question request
     * Reject a question request from the AI assistant.
     * <p><b>200</b> - Question rejected successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - QuestionNotFoundError
     * @param requestID The requestID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> questionReject(@jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return questionRejectRequestCreation(requestID, directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Reject question request
     * Reject a question request from the AI assistant.
     * <p><b>200</b> - Question rejected successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - QuestionNotFoundError
     * @param requestID The requestID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> questionRejectWithHttpInfo(@jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return questionRejectRequestCreation(requestID, directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Reject question request
     * Reject a question request from the AI assistant.
     * <p><b>200</b> - Question rejected successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - QuestionNotFoundError
     * @param requestID The requestID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec questionRejectWithResponseSpec(@jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return questionRejectRequestCreation(requestID, directory, workspace);
    }

    public class QuestionReplyRequest {
        private @jakarta.annotation.Nonnull String requestID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable QuestionReplyRequest questionReplyRequest;

        public QuestionReplyRequest() {}

        public QuestionReplyRequest(@jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable QuestionReplyRequest questionReplyRequest) {
            this.requestID = requestID;
            this.directory = directory;
            this.workspace = workspace;
            this.questionReplyRequest = questionReplyRequest;
        }

        public @jakarta.annotation.Nonnull String requestID() {
            return this.requestID;
        }
        public QuestionReplyRequest requestID(@jakarta.annotation.Nonnull String requestID) {
            this.requestID = requestID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public QuestionReplyRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public QuestionReplyRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable QuestionReplyRequest questionReplyRequest() {
            return this.questionReplyRequest;
        }
        public QuestionReplyRequest questionReplyRequest(@jakarta.annotation.Nullable QuestionReplyRequest questionReplyRequest) {
            this.questionReplyRequest = questionReplyRequest;
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
            QuestionReplyRequest request = (QuestionReplyRequest) o;
            return Objects.equals(this.requestID, request.requestID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.questionReplyRequest, request.questionReplyRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(requestID, directory, workspace, questionReplyRequest);
        }
    }

    /**
     * Reply to question request
     * Provide answers to a question request from the AI assistant.
     * <p><b>200</b> - Question answered successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - QuestionNotFoundError
     * @param requestParameters The questionReply request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> questionReply(QuestionReplyRequest requestParameters) throws WebClientResponseException {
        return this.questionReply(requestParameters.requestID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.questionReplyRequest());
    }

    /**
     * Reply to question request
     * Provide answers to a question request from the AI assistant.
     * <p><b>200</b> - Question answered successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - QuestionNotFoundError
     * @param requestParameters The questionReply request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> questionReplyWithHttpInfo(QuestionReplyRequest requestParameters) throws WebClientResponseException {
        return this.questionReplyWithHttpInfo(requestParameters.requestID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.questionReplyRequest());
    }

    /**
     * Reply to question request
     * Provide answers to a question request from the AI assistant.
     * <p><b>200</b> - Question answered successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - QuestionNotFoundError
     * @param requestParameters The questionReply request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec questionReplyWithResponseSpec(QuestionReplyRequest requestParameters) throws WebClientResponseException {
        return this.questionReplyWithResponseSpec(requestParameters.requestID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.questionReplyRequest());
    }


    /**
     * Reply to question request
     * Provide answers to a question request from the AI assistant.
     * <p><b>200</b> - Question answered successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - QuestionNotFoundError
     * @param requestID The requestID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param questionReplyRequest The questionReplyRequest parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec questionReplyRequestCreation(@jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable QuestionReplyRequest questionReplyRequest) throws WebClientResponseException {
        Object postBody = questionReplyRequest;
        // verify the required parameter 'requestID' is set
        if (requestID == null) {
            throw new WebClientResponseException("Missing the required parameter 'requestID' when calling questionReply", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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
        return apiClient.invokeAPI("/question/{requestID}/reply", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Reply to question request
     * Provide answers to a question request from the AI assistant.
     * <p><b>200</b> - Question answered successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - QuestionNotFoundError
     * @param requestID The requestID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param questionReplyRequest The questionReplyRequest parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> questionReply(@jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable QuestionReplyRequest questionReplyRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return questionReplyRequestCreation(requestID, directory, workspace, questionReplyRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Reply to question request
     * Provide answers to a question request from the AI assistant.
     * <p><b>200</b> - Question answered successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - QuestionNotFoundError
     * @param requestID The requestID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param questionReplyRequest The questionReplyRequest parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> questionReplyWithHttpInfo(@jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable QuestionReplyRequest questionReplyRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return questionReplyRequestCreation(requestID, directory, workspace, questionReplyRequest).toEntity(localVarReturnType);
    }

    /**
     * Reply to question request
     * Provide answers to a question request from the AI assistant.
     * <p><b>200</b> - Question answered successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - QuestionNotFoundError
     * @param requestID The requestID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param questionReplyRequest The questionReplyRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec questionReplyWithResponseSpec(@jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable QuestionReplyRequest questionReplyRequest) throws WebClientResponseException {
        return questionReplyRequestCreation(requestID, directory, workspace, questionReplyRequest);
    }
}
