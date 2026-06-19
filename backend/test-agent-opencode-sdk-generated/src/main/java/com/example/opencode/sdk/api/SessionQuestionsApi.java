package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.InvalidRequestError;
import com.example.opencode.sdk.model.QuestionV2Reply;
import com.example.opencode.sdk.model.UnauthorizedError;
import com.example.opencode.sdk.model.V2AgentListLocationParameter;
import com.example.opencode.sdk.model.V2QuestionRequestList200Response;
import com.example.opencode.sdk.model.V2SessionGet404Response;
import com.example.opencode.sdk.model.V2SessionQuestionList200Response;
import com.example.opencode.sdk.model.V2SessionQuestionReply404Response;

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
public class SessionQuestionsApi {
    private ApiClient apiClient;

    public SessionQuestionsApi() {
        this(new ApiClient());
    }

    public SessionQuestionsApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * List pending question requests
     * Retrieve pending question requests for a location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return V2QuestionRequestList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2QuestionRequestListRequestCreation(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = null;
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

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

        ParameterizedTypeReference<V2QuestionRequestList200Response> localVarReturnType = new ParameterizedTypeReference<V2QuestionRequestList200Response>() {};
        return apiClient.invokeAPI("/api/question/request", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List pending question requests
     * Retrieve pending question requests for a location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return V2QuestionRequestList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2QuestionRequestList200Response> v2QuestionRequestList(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2QuestionRequestList200Response> localVarReturnType = new ParameterizedTypeReference<V2QuestionRequestList200Response>() {};
        return v2QuestionRequestListRequestCreation(location).bodyToMono(localVarReturnType);
    }

    /**
     * List pending question requests
     * Retrieve pending question requests for a location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseEntity&lt;V2QuestionRequestList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2QuestionRequestList200Response>> v2QuestionRequestListWithHttpInfo(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<V2QuestionRequestList200Response> localVarReturnType = new ParameterizedTypeReference<V2QuestionRequestList200Response>() {};
        return v2QuestionRequestListRequestCreation(location).toEntity(localVarReturnType);
    }

    /**
     * List pending question requests
     * Retrieve pending question requests for a location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2QuestionRequestListWithResponseSpec(@jakarta.annotation.Nullable V2AgentListLocationParameter location) throws WebClientResponseException {
        return v2QuestionRequestListRequestCreation(location);
    }

    /**
     * List session question requests
     * Retrieve pending question requests owned by a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return V2SessionQuestionList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2SessionQuestionListRequestCreation(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling v2SessionQuestionList", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

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

        ParameterizedTypeReference<V2SessionQuestionList200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionQuestionList200Response>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/question", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List session question requests
     * Retrieve pending question requests owned by a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return V2SessionQuestionList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<V2SessionQuestionList200Response> v2SessionQuestionList(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<V2SessionQuestionList200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionQuestionList200Response>() {};
        return v2SessionQuestionListRequestCreation(sessionID).bodyToMono(localVarReturnType);
    }

    /**
     * List session question requests
     * Retrieve pending question requests owned by a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return ResponseEntity&lt;V2SessionQuestionList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<V2SessionQuestionList200Response>> v2SessionQuestionListWithHttpInfo(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<V2SessionQuestionList200Response> localVarReturnType = new ParameterizedTypeReference<V2SessionQuestionList200Response>() {};
        return v2SessionQuestionListRequestCreation(sessionID).toEntity(localVarReturnType);
    }

    /**
     * List session question requests
     * Retrieve pending question requests owned by a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionQuestionListWithResponseSpec(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        return v2SessionQuestionListRequestCreation(sessionID);
    }

    public class V2SessionQuestionRejectRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull String requestID;

        public V2SessionQuestionRejectRequest() {}

        public V2SessionQuestionRejectRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String requestID) {
            this.sessionID = sessionID;
            this.requestID = requestID;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public V2SessionQuestionRejectRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull String requestID() {
            return this.requestID;
        }
        public V2SessionQuestionRejectRequest requestID(@jakarta.annotation.Nonnull String requestID) {
            this.requestID = requestID;
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
            V2SessionQuestionRejectRequest request = (V2SessionQuestionRejectRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.requestID, request.requestID());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, requestID);
        }
    }

    /**
     * Reject pending question request
     * Reject a pending question request owned by a session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | QuestionNotFoundError
     * @param requestParameters The v2SessionQuestionReject request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2SessionQuestionReject(V2SessionQuestionRejectRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionQuestionReject(requestParameters.sessionID(), requestParameters.requestID());
    }

    /**
     * Reject pending question request
     * Reject a pending question request owned by a session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | QuestionNotFoundError
     * @param requestParameters The v2SessionQuestionReject request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2SessionQuestionRejectWithHttpInfo(V2SessionQuestionRejectRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionQuestionRejectWithHttpInfo(requestParameters.sessionID(), requestParameters.requestID());
    }

    /**
     * Reject pending question request
     * Reject a pending question request owned by a session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | QuestionNotFoundError
     * @param requestParameters The v2SessionQuestionReject request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionQuestionRejectWithResponseSpec(V2SessionQuestionRejectRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionQuestionRejectWithResponseSpec(requestParameters.sessionID(), requestParameters.requestID());
    }


    /**
     * Reject pending question request
     * Reject a pending question request owned by a session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | QuestionNotFoundError
     * @param sessionID The sessionID parameter
     * @param requestID The requestID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2SessionQuestionRejectRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String requestID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling v2SessionQuestionReject", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'requestID' is set
        if (requestID == null) {
            throw new WebClientResponseException("Missing the required parameter 'requestID' when calling v2SessionQuestionReject", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);
        pathParams.put("requestID", requestID);

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

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/question/{requestID}/reject", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Reject pending question request
     * Reject a pending question request owned by a session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | QuestionNotFoundError
     * @param sessionID The sessionID parameter
     * @param requestID The requestID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2SessionQuestionReject(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String requestID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2SessionQuestionRejectRequestCreation(sessionID, requestID).bodyToMono(localVarReturnType);
    }

    /**
     * Reject pending question request
     * Reject a pending question request owned by a session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | QuestionNotFoundError
     * @param sessionID The sessionID parameter
     * @param requestID The requestID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2SessionQuestionRejectWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String requestID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2SessionQuestionRejectRequestCreation(sessionID, requestID).toEntity(localVarReturnType);
    }

    /**
     * Reject pending question request
     * Reject a pending question request owned by a session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | QuestionNotFoundError
     * @param sessionID The sessionID parameter
     * @param requestID The requestID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionQuestionRejectWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String requestID) throws WebClientResponseException {
        return v2SessionQuestionRejectRequestCreation(sessionID, requestID);
    }

    public class V2SessionQuestionReplyRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull String requestID;
        private @jakarta.annotation.Nonnull QuestionV2Reply questionV2Reply;

        public V2SessionQuestionReplyRequest() {}

        public V2SessionQuestionReplyRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nonnull QuestionV2Reply questionV2Reply) {
            this.sessionID = sessionID;
            this.requestID = requestID;
            this.questionV2Reply = questionV2Reply;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public V2SessionQuestionReplyRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull String requestID() {
            return this.requestID;
        }
        public V2SessionQuestionReplyRequest requestID(@jakarta.annotation.Nonnull String requestID) {
            this.requestID = requestID;
            return this;
        }

        public @jakarta.annotation.Nonnull QuestionV2Reply questionV2Reply() {
            return this.questionV2Reply;
        }
        public V2SessionQuestionReplyRequest questionV2Reply(@jakarta.annotation.Nonnull QuestionV2Reply questionV2Reply) {
            this.questionV2Reply = questionV2Reply;
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
            V2SessionQuestionReplyRequest request = (V2SessionQuestionReplyRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.requestID, request.requestID()) &&
                Objects.equals(this.questionV2Reply, request.questionV2Reply());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, requestID, questionV2Reply);
        }
    }

    /**
     * Reply to pending question request
     * Answer a pending question request owned by a session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | QuestionNotFoundError
     * @param requestParameters The v2SessionQuestionReply request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2SessionQuestionReply(V2SessionQuestionReplyRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionQuestionReply(requestParameters.sessionID(), requestParameters.requestID(), requestParameters.questionV2Reply());
    }

    /**
     * Reply to pending question request
     * Answer a pending question request owned by a session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | QuestionNotFoundError
     * @param requestParameters The v2SessionQuestionReply request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2SessionQuestionReplyWithHttpInfo(V2SessionQuestionReplyRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionQuestionReplyWithHttpInfo(requestParameters.sessionID(), requestParameters.requestID(), requestParameters.questionV2Reply());
    }

    /**
     * Reply to pending question request
     * Answer a pending question request owned by a session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | QuestionNotFoundError
     * @param requestParameters The v2SessionQuestionReply request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionQuestionReplyWithResponseSpec(V2SessionQuestionReplyRequest requestParameters) throws WebClientResponseException {
        return this.v2SessionQuestionReplyWithResponseSpec(requestParameters.sessionID(), requestParameters.requestID(), requestParameters.questionV2Reply());
    }


    /**
     * Reply to pending question request
     * Answer a pending question request owned by a session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | QuestionNotFoundError
     * @param sessionID The sessionID parameter
     * @param requestID The requestID parameter
     * @param questionV2Reply The questionV2Reply parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec v2SessionQuestionReplyRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nonnull QuestionV2Reply questionV2Reply) throws WebClientResponseException {
        Object postBody = questionV2Reply;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling v2SessionQuestionReply", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'requestID' is set
        if (requestID == null) {
            throw new WebClientResponseException("Missing the required parameter 'requestID' when calling v2SessionQuestionReply", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'questionV2Reply' is set
        if (questionV2Reply == null) {
            throw new WebClientResponseException("Missing the required parameter 'questionV2Reply' when calling v2SessionQuestionReply", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);
        pathParams.put("requestID", requestID);

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
        return apiClient.invokeAPI("/api/session/{sessionID}/question/{requestID}/reply", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Reply to pending question request
     * Answer a pending question request owned by a session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | QuestionNotFoundError
     * @param sessionID The sessionID parameter
     * @param requestID The requestID parameter
     * @param questionV2Reply The questionV2Reply parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> v2SessionQuestionReply(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nonnull QuestionV2Reply questionV2Reply) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2SessionQuestionReplyRequestCreation(sessionID, requestID, questionV2Reply).bodyToMono(localVarReturnType);
    }

    /**
     * Reply to pending question request
     * Answer a pending question request owned by a session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | QuestionNotFoundError
     * @param sessionID The sessionID parameter
     * @param requestID The requestID parameter
     * @param questionV2Reply The questionV2Reply parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> v2SessionQuestionReplyWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nonnull QuestionV2Reply questionV2Reply) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return v2SessionQuestionReplyRequestCreation(sessionID, requestID, questionV2Reply).toEntity(localVarReturnType);
    }

    /**
     * Reply to pending question request
     * Answer a pending question request owned by a session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | QuestionNotFoundError
     * @param sessionID The sessionID parameter
     * @param requestID The requestID parameter
     * @param questionV2Reply The questionV2Reply parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec v2SessionQuestionReplyWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String requestID, @jakarta.annotation.Nonnull QuestionV2Reply questionV2Reply) throws WebClientResponseException {
        return v2SessionQuestionReplyRequestCreation(sessionID, requestID, questionV2Reply);
    }
}
