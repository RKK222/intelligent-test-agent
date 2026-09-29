package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.CommandExecutionErrorEncoded;
import com.example.opencode.sdk.model.ConflictErrorEncoded;
import com.example.opencode.sdk.model.ExperimentalSessionExport200Response;
import com.example.opencode.sdk.model.ExperimentalSessionImport200Response;
import com.example.opencode.sdk.model.ExperimentalSessionImportRequest;
import com.example.opencode.sdk.model.ExperimentalSessionInstructionsEntryList200Response;
import com.example.opencode.sdk.model.ExperimentalSessionInstructionsEntryPutRequest;
import com.example.opencode.sdk.model.ExperimentalSessionSkill404Response;
import com.example.opencode.sdk.model.ExperimentalSessionSkillRequest;
import com.example.opencode.sdk.model.ExperimentalSessionStats200Response;
import com.example.opencode.sdk.model.FormAlreadySettledErrorEncoded;
import com.example.opencode.sdk.model.FormCreatePayload;
import com.example.opencode.sdk.model.FormReply;
import com.example.opencode.sdk.model.InstructionEntryValueTooLargeErrorEncoded;
import com.example.opencode.sdk.model.InvalidRequestErrorEncoded;
import com.example.opencode.sdk.model.PluginCheck400Response;
import com.example.opencode.sdk.model.ServiceUnavailableErrorEncoded;
import com.example.opencode.sdk.model.SessionActive200Response;
import com.example.opencode.sdk.model.SessionBusyErrorEncoded;
import com.example.opencode.sdk.model.SessionCommand404Response;
import com.example.opencode.sdk.model.SessionCommandRequest;
import com.example.opencode.sdk.model.SessionCompact200Response;
import com.example.opencode.sdk.model.SessionCompactRequest;
import com.example.opencode.sdk.model.SessionContext200Response;
import com.example.opencode.sdk.model.SessionCreate200Response;
import com.example.opencode.sdk.model.SessionCreateRequest;
import com.example.opencode.sdk.model.SessionDiff200Response;
import com.example.opencode.sdk.model.SessionDiff404Response;
import com.example.opencode.sdk.model.SessionEnvironmentRequest;
import com.example.opencode.sdk.model.SessionFork404Response;
import com.example.opencode.sdk.model.SessionForkRequest;
import com.example.opencode.sdk.model.SessionFormCreate200Response;
import com.example.opencode.sdk.model.SessionFormGet200Response;
import com.example.opencode.sdk.model.SessionFormGet404Response;
import com.example.opencode.sdk.model.SessionFormList200Response;
import com.example.opencode.sdk.model.SessionFormReply400Response;
import com.example.opencode.sdk.model.SessionGenerateRequest;
import com.example.opencode.sdk.model.SessionGenerateResponse;
import com.example.opencode.sdk.model.SessionInboxList200Response;
import com.example.opencode.sdk.model.SessionInboxUpdateRequest;
import com.example.opencode.sdk.model.SessionInterruptResponse;
import com.example.opencode.sdk.model.SessionList400Response;
import com.example.opencode.sdk.model.SessionLog200Response;
import com.example.opencode.sdk.model.SessionMessageGet200Response;
import com.example.opencode.sdk.model.SessionMessageGet404Response;
import com.example.opencode.sdk.model.SessionMessageList400Response;
import com.example.opencode.sdk.model.SessionMessagesResponse;
import com.example.opencode.sdk.model.SessionMoveRequest;
import com.example.opencode.sdk.model.SessionNotFoundErrorEncoded;
import com.example.opencode.sdk.model.SessionPrompt200Response;
import com.example.opencode.sdk.model.SessionPromptRequest;
import com.example.opencode.sdk.model.SessionRevertStage200Response;
import com.example.opencode.sdk.model.SessionRevertStageRequest;
import com.example.opencode.sdk.model.SessionShellRequest;
import com.example.opencode.sdk.model.SessionSwitchAgentRequest;
import com.example.opencode.sdk.model.SessionSwitchModelRequest;
import com.example.opencode.sdk.model.SessionSynthetic200Response;
import com.example.opencode.sdk.model.SessionSyntheticRequest;
import com.example.opencode.sdk.model.SessionUpdate404Response;
import com.example.opencode.sdk.model.SessionUpdateRequest;
import com.example.opencode.sdk.model.SessionViewRequest;
import com.example.opencode.sdk.model.SessionsResponse;
import com.example.opencode.sdk.model.UnauthorizedErrorEncoded;
import com.example.opencode.sdk.model.UnknownErrorEncoded;

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

    public class ExperimentalSessionExportRequest {
        private @jakarta.annotation.Nullable String sessionID;
        private @jakarta.annotation.Nullable String sanitize;

        public ExperimentalSessionExportRequest() {}

        public ExperimentalSessionExportRequest(@jakarta.annotation.Nullable String sessionID, @jakarta.annotation.Nullable String sanitize) {
            this.sessionID = sessionID;
            this.sanitize = sanitize;
        }

        public @jakarta.annotation.Nullable String sessionID() {
            return this.sessionID;
        }
        public ExperimentalSessionExportRequest sessionID(@jakarta.annotation.Nullable String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String sanitize() {
            return this.sanitize;
        }
        public ExperimentalSessionExportRequest sanitize(@jakarta.annotation.Nullable String sanitize) {
            this.sanitize = sanitize;
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
            ExperimentalSessionExportRequest request = (ExperimentalSessionExportRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.sanitize, request.sanitize());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, sanitize);
        }
    }

    /**
     * Export session
     * Export a complete projected session transcript.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param requestParameters The experimentalSessionExport request parameters as object
     * @return ExperimentalSessionExport200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ExperimentalSessionExport200Response> experimentalSessionExport(ExperimentalSessionExportRequest requestParameters) throws WebClientResponseException {
        return this.experimentalSessionExport(requestParameters.sessionID(), requestParameters.sanitize());
    }

    /**
     * Export session
     * Export a complete projected session transcript.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param requestParameters The experimentalSessionExport request parameters as object
     * @return ResponseEntity&lt;ExperimentalSessionExport200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ExperimentalSessionExport200Response>> experimentalSessionExportWithHttpInfo(ExperimentalSessionExportRequest requestParameters) throws WebClientResponseException {
        return this.experimentalSessionExportWithHttpInfo(requestParameters.sessionID(), requestParameters.sanitize());
    }

    /**
     * Export session
     * Export a complete projected session transcript.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param requestParameters The experimentalSessionExport request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalSessionExportWithResponseSpec(ExperimentalSessionExportRequest requestParameters) throws WebClientResponseException {
        return this.experimentalSessionExportWithResponseSpec(requestParameters.sessionID(), requestParameters.sanitize());
    }


    /**
     * Export session
     * Export a complete projected session transcript.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @param sanitize The sanitize parameter
     * @return ExperimentalSessionExport200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalSessionExportRequestCreation(@jakarta.annotation.Nullable String sessionID, @jakarta.annotation.Nullable String sanitize) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling experimentalSessionExport", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "sanitize", sanitize));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<ExperimentalSessionExport200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalSessionExport200Response>() {};
        return apiClient.invokeAPI("/api/experimental/session/{sessionID}/export", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Export session
     * Export a complete projected session transcript.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @param sanitize The sanitize parameter
     * @return ExperimentalSessionExport200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ExperimentalSessionExport200Response> experimentalSessionExport(@jakarta.annotation.Nullable String sessionID, @jakarta.annotation.Nullable String sanitize) throws WebClientResponseException {
        ParameterizedTypeReference<ExperimentalSessionExport200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalSessionExport200Response>() {};
        return experimentalSessionExportRequestCreation(sessionID, sanitize).bodyToMono(localVarReturnType);
    }

    /**
     * Export session
     * Export a complete projected session transcript.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @param sanitize The sanitize parameter
     * @return ResponseEntity&lt;ExperimentalSessionExport200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ExperimentalSessionExport200Response>> experimentalSessionExportWithHttpInfo(@jakarta.annotation.Nullable String sessionID, @jakarta.annotation.Nullable String sanitize) throws WebClientResponseException {
        ParameterizedTypeReference<ExperimentalSessionExport200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalSessionExport200Response>() {};
        return experimentalSessionExportRequestCreation(sessionID, sanitize).toEntity(localVarReturnType);
    }

    /**
     * Export session
     * Export a complete projected session transcript.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @param sanitize The sanitize parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalSessionExportWithResponseSpec(@jakarta.annotation.Nullable String sessionID, @jakarta.annotation.Nullable String sanitize) throws WebClientResponseException {
        return experimentalSessionExportRequestCreation(sessionID, sanitize);
    }

    /**
     * Import session
     * Import a projected session transcript at the requested location. If parentID is supplied, the parent session must already exist; import parents before children.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param experimentalSessionImportRequest The experimentalSessionImportRequest parameter
     * @return ExperimentalSessionImport200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalSessionImportRequestCreation(@jakarta.annotation.Nonnull ExperimentalSessionImportRequest experimentalSessionImportRequest) throws WebClientResponseException {
        Object postBody = experimentalSessionImportRequest;
        // verify the required parameter 'experimentalSessionImportRequest' is set
        if (experimentalSessionImportRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'experimentalSessionImportRequest' when calling experimentalSessionImport", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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

        ParameterizedTypeReference<ExperimentalSessionImport200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalSessionImport200Response>() {};
        return apiClient.invokeAPI("/api/experimental/session/import", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Import session
     * Import a projected session transcript at the requested location. If parentID is supplied, the parent session must already exist; import parents before children.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param experimentalSessionImportRequest The experimentalSessionImportRequest parameter
     * @return ExperimentalSessionImport200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ExperimentalSessionImport200Response> experimentalSessionImport(@jakarta.annotation.Nonnull ExperimentalSessionImportRequest experimentalSessionImportRequest) throws WebClientResponseException {
        ParameterizedTypeReference<ExperimentalSessionImport200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalSessionImport200Response>() {};
        return experimentalSessionImportRequestCreation(experimentalSessionImportRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Import session
     * Import a projected session transcript at the requested location. If parentID is supplied, the parent session must already exist; import parents before children.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param experimentalSessionImportRequest The experimentalSessionImportRequest parameter
     * @return ResponseEntity&lt;ExperimentalSessionImport200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ExperimentalSessionImport200Response>> experimentalSessionImportWithHttpInfo(@jakarta.annotation.Nonnull ExperimentalSessionImportRequest experimentalSessionImportRequest) throws WebClientResponseException {
        ParameterizedTypeReference<ExperimentalSessionImport200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalSessionImport200Response>() {};
        return experimentalSessionImportRequestCreation(experimentalSessionImportRequest).toEntity(localVarReturnType);
    }

    /**
     * Import session
     * Import a projected session transcript at the requested location. If parentID is supplied, the parent session must already exist; import parents before children.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param experimentalSessionImportRequest The experimentalSessionImportRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalSessionImportWithResponseSpec(@jakarta.annotation.Nonnull ExperimentalSessionImportRequest experimentalSessionImportRequest) throws WebClientResponseException {
        return experimentalSessionImportRequestCreation(experimentalSessionImportRequest);
    }

    /**
     * List instruction entries
     * List API-managed instruction entries attached to the session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return ExperimentalSessionInstructionsEntryList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalSessionInstructionsEntryListRequestCreation(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling experimentalSessionInstructionsEntryList", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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

        ParameterizedTypeReference<ExperimentalSessionInstructionsEntryList200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalSessionInstructionsEntryList200Response>() {};
        return apiClient.invokeAPI("/api/experimental/session/{sessionID}/instructions/entries", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List instruction entries
     * List API-managed instruction entries attached to the session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return ExperimentalSessionInstructionsEntryList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ExperimentalSessionInstructionsEntryList200Response> experimentalSessionInstructionsEntryList(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<ExperimentalSessionInstructionsEntryList200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalSessionInstructionsEntryList200Response>() {};
        return experimentalSessionInstructionsEntryListRequestCreation(sessionID).bodyToMono(localVarReturnType);
    }

    /**
     * List instruction entries
     * List API-managed instruction entries attached to the session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return ResponseEntity&lt;ExperimentalSessionInstructionsEntryList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ExperimentalSessionInstructionsEntryList200Response>> experimentalSessionInstructionsEntryListWithHttpInfo(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<ExperimentalSessionInstructionsEntryList200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalSessionInstructionsEntryList200Response>() {};
        return experimentalSessionInstructionsEntryListRequestCreation(sessionID).toEntity(localVarReturnType);
    }

    /**
     * List instruction entries
     * List API-managed instruction entries attached to the session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalSessionInstructionsEntryListWithResponseSpec(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        return experimentalSessionInstructionsEntryListRequestCreation(sessionID);
    }

    public class ExperimentalSessionInstructionsEntryPutRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull String key;
        private @jakarta.annotation.Nonnull ExperimentalSessionInstructionsEntryPutRequest experimentalSessionInstructionsEntryPutRequest;

        public ExperimentalSessionInstructionsEntryPutRequest() {}

        public ExperimentalSessionInstructionsEntryPutRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String key, @jakarta.annotation.Nonnull ExperimentalSessionInstructionsEntryPutRequest experimentalSessionInstructionsEntryPutRequest) {
            this.sessionID = sessionID;
            this.key = key;
            this.experimentalSessionInstructionsEntryPutRequest = experimentalSessionInstructionsEntryPutRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public ExperimentalSessionInstructionsEntryPutRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull String key() {
            return this.key;
        }
        public ExperimentalSessionInstructionsEntryPutRequest key(@jakarta.annotation.Nonnull String key) {
            this.key = key;
            return this;
        }

        public @jakarta.annotation.Nonnull ExperimentalSessionInstructionsEntryPutRequest experimentalSessionInstructionsEntryPutRequest() {
            return this.experimentalSessionInstructionsEntryPutRequest;
        }
        public ExperimentalSessionInstructionsEntryPutRequest experimentalSessionInstructionsEntryPutRequest(@jakarta.annotation.Nonnull ExperimentalSessionInstructionsEntryPutRequest experimentalSessionInstructionsEntryPutRequest) {
            this.experimentalSessionInstructionsEntryPutRequest = experimentalSessionInstructionsEntryPutRequest;
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
            ExperimentalSessionInstructionsEntryPutRequest request = (ExperimentalSessionInstructionsEntryPutRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.key, request.key()) &&
                Objects.equals(this.experimentalSessionInstructionsEntryPutRequest, request.experimentalSessionInstructionsEntryPutRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, key, experimentalSessionInstructionsEntryPutRequest);
        }
    }

    /**
     * Put instruction entry
     * Attach or replace one durable instruction entry. Changes announce as updates at the next step boundary.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>413</b> - InstructionEntryValueTooLargeError
     * @param requestParameters The experimentalSessionInstructionsEntryPut request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> experimentalSessionInstructionsEntryPut(ExperimentalSessionInstructionsEntryPutRequest requestParameters) throws WebClientResponseException {
        return this.experimentalSessionInstructionsEntryPut(requestParameters.sessionID(), requestParameters.key(), requestParameters.experimentalSessionInstructionsEntryPutRequest());
    }

    /**
     * Put instruction entry
     * Attach or replace one durable instruction entry. Changes announce as updates at the next step boundary.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>413</b> - InstructionEntryValueTooLargeError
     * @param requestParameters The experimentalSessionInstructionsEntryPut request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> experimentalSessionInstructionsEntryPutWithHttpInfo(ExperimentalSessionInstructionsEntryPutRequest requestParameters) throws WebClientResponseException {
        return this.experimentalSessionInstructionsEntryPutWithHttpInfo(requestParameters.sessionID(), requestParameters.key(), requestParameters.experimentalSessionInstructionsEntryPutRequest());
    }

    /**
     * Put instruction entry
     * Attach or replace one durable instruction entry. Changes announce as updates at the next step boundary.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>413</b> - InstructionEntryValueTooLargeError
     * @param requestParameters The experimentalSessionInstructionsEntryPut request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalSessionInstructionsEntryPutWithResponseSpec(ExperimentalSessionInstructionsEntryPutRequest requestParameters) throws WebClientResponseException {
        return this.experimentalSessionInstructionsEntryPutWithResponseSpec(requestParameters.sessionID(), requestParameters.key(), requestParameters.experimentalSessionInstructionsEntryPutRequest());
    }


    /**
     * Put instruction entry
     * Attach or replace one durable instruction entry. Changes announce as updates at the next step boundary.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>413</b> - InstructionEntryValueTooLargeError
     * @param sessionID The sessionID parameter
     * @param key The key parameter
     * @param experimentalSessionInstructionsEntryPutRequest The experimentalSessionInstructionsEntryPutRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalSessionInstructionsEntryPutRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String key, @jakarta.annotation.Nonnull ExperimentalSessionInstructionsEntryPutRequest experimentalSessionInstructionsEntryPutRequest) throws WebClientResponseException {
        Object postBody = experimentalSessionInstructionsEntryPutRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling experimentalSessionInstructionsEntryPut", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'key' is set
        if (key == null) {
            throw new WebClientResponseException("Missing the required parameter 'key' when calling experimentalSessionInstructionsEntryPut", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'experimentalSessionInstructionsEntryPutRequest' is set
        if (experimentalSessionInstructionsEntryPutRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'experimentalSessionInstructionsEntryPutRequest' when calling experimentalSessionInstructionsEntryPut", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);
        pathParams.put("key", key);

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
        return apiClient.invokeAPI("/api/experimental/session/{sessionID}/instructions/entries/{key}", HttpMethod.PUT, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Put instruction entry
     * Attach or replace one durable instruction entry. Changes announce as updates at the next step boundary.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>413</b> - InstructionEntryValueTooLargeError
     * @param sessionID The sessionID parameter
     * @param key The key parameter
     * @param experimentalSessionInstructionsEntryPutRequest The experimentalSessionInstructionsEntryPutRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> experimentalSessionInstructionsEntryPut(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String key, @jakarta.annotation.Nonnull ExperimentalSessionInstructionsEntryPutRequest experimentalSessionInstructionsEntryPutRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return experimentalSessionInstructionsEntryPutRequestCreation(sessionID, key, experimentalSessionInstructionsEntryPutRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Put instruction entry
     * Attach or replace one durable instruction entry. Changes announce as updates at the next step boundary.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>413</b> - InstructionEntryValueTooLargeError
     * @param sessionID The sessionID parameter
     * @param key The key parameter
     * @param experimentalSessionInstructionsEntryPutRequest The experimentalSessionInstructionsEntryPutRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> experimentalSessionInstructionsEntryPutWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String key, @jakarta.annotation.Nonnull ExperimentalSessionInstructionsEntryPutRequest experimentalSessionInstructionsEntryPutRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return experimentalSessionInstructionsEntryPutRequestCreation(sessionID, key, experimentalSessionInstructionsEntryPutRequest).toEntity(localVarReturnType);
    }

    /**
     * Put instruction entry
     * Attach or replace one durable instruction entry. Changes announce as updates at the next step boundary.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>413</b> - InstructionEntryValueTooLargeError
     * @param sessionID The sessionID parameter
     * @param key The key parameter
     * @param experimentalSessionInstructionsEntryPutRequest The experimentalSessionInstructionsEntryPutRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalSessionInstructionsEntryPutWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String key, @jakarta.annotation.Nonnull ExperimentalSessionInstructionsEntryPutRequest experimentalSessionInstructionsEntryPutRequest) throws WebClientResponseException {
        return experimentalSessionInstructionsEntryPutRequestCreation(sessionID, key, experimentalSessionInstructionsEntryPutRequest);
    }

    public class ExperimentalSessionInstructionsEntryRemoveRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull String key;

        public ExperimentalSessionInstructionsEntryRemoveRequest() {}

        public ExperimentalSessionInstructionsEntryRemoveRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String key) {
            this.sessionID = sessionID;
            this.key = key;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public ExperimentalSessionInstructionsEntryRemoveRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull String key() {
            return this.key;
        }
        public ExperimentalSessionInstructionsEntryRemoveRequest key(@jakarta.annotation.Nonnull String key) {
            this.key = key;
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
            ExperimentalSessionInstructionsEntryRemoveRequest request = (ExperimentalSessionInstructionsEntryRemoveRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.key, request.key());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, key);
        }
    }

    /**
     * Remove instruction entry
     * Remove one instruction entry; the removal is announced to the model at the next step boundary.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The experimentalSessionInstructionsEntryRemove request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> experimentalSessionInstructionsEntryRemove(ExperimentalSessionInstructionsEntryRemoveRequest requestParameters) throws WebClientResponseException {
        return this.experimentalSessionInstructionsEntryRemove(requestParameters.sessionID(), requestParameters.key());
    }

    /**
     * Remove instruction entry
     * Remove one instruction entry; the removal is announced to the model at the next step boundary.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The experimentalSessionInstructionsEntryRemove request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> experimentalSessionInstructionsEntryRemoveWithHttpInfo(ExperimentalSessionInstructionsEntryRemoveRequest requestParameters) throws WebClientResponseException {
        return this.experimentalSessionInstructionsEntryRemoveWithHttpInfo(requestParameters.sessionID(), requestParameters.key());
    }

    /**
     * Remove instruction entry
     * Remove one instruction entry; the removal is announced to the model at the next step boundary.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The experimentalSessionInstructionsEntryRemove request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalSessionInstructionsEntryRemoveWithResponseSpec(ExperimentalSessionInstructionsEntryRemoveRequest requestParameters) throws WebClientResponseException {
        return this.experimentalSessionInstructionsEntryRemoveWithResponseSpec(requestParameters.sessionID(), requestParameters.key());
    }


    /**
     * Remove instruction entry
     * Remove one instruction entry; the removal is announced to the model at the next step boundary.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param key The key parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalSessionInstructionsEntryRemoveRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String key) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling experimentalSessionInstructionsEntryRemove", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'key' is set
        if (key == null) {
            throw new WebClientResponseException("Missing the required parameter 'key' when calling experimentalSessionInstructionsEntryRemove", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);
        pathParams.put("key", key);

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
        return apiClient.invokeAPI("/api/experimental/session/{sessionID}/instructions/entries/{key}", HttpMethod.DELETE, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Remove instruction entry
     * Remove one instruction entry; the removal is announced to the model at the next step boundary.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param key The key parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> experimentalSessionInstructionsEntryRemove(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String key) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return experimentalSessionInstructionsEntryRemoveRequestCreation(sessionID, key).bodyToMono(localVarReturnType);
    }

    /**
     * Remove instruction entry
     * Remove one instruction entry; the removal is announced to the model at the next step boundary.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param key The key parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> experimentalSessionInstructionsEntryRemoveWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String key) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return experimentalSessionInstructionsEntryRemoveRequestCreation(sessionID, key).toEntity(localVarReturnType);
    }

    /**
     * Remove instruction entry
     * Remove one instruction entry; the removal is announced to the model at the next step boundary.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param key The key parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalSessionInstructionsEntryRemoveWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String key) throws WebClientResponseException {
        return experimentalSessionInstructionsEntryRemoveRequestCreation(sessionID, key);
    }

    public class ExperimentalSessionSkillRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull ExperimentalSessionSkillRequest experimentalSessionSkillRequest;

        public ExperimentalSessionSkillRequest() {}

        public ExperimentalSessionSkillRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull ExperimentalSessionSkillRequest experimentalSessionSkillRequest) {
            this.sessionID = sessionID;
            this.experimentalSessionSkillRequest = experimentalSessionSkillRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public ExperimentalSessionSkillRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull ExperimentalSessionSkillRequest experimentalSessionSkillRequest() {
            return this.experimentalSessionSkillRequest;
        }
        public ExperimentalSessionSkillRequest experimentalSessionSkillRequest(@jakarta.annotation.Nonnull ExperimentalSessionSkillRequest experimentalSessionSkillRequest) {
            this.experimentalSessionSkillRequest = experimentalSessionSkillRequest;
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
            ExperimentalSessionSkillRequest request = (ExperimentalSessionSkillRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.experimentalSessionSkillRequest, request.experimentalSessionSkillRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, experimentalSessionSkillRequest);
        }
    }

    /**
     * Activate skill
     * Activate a skill for a session by appending a skill message and resuming execution.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | SkillNotFoundError
     * @param requestParameters The experimentalSessionSkill request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> experimentalSessionSkill(ExperimentalSessionSkillRequest requestParameters) throws WebClientResponseException {
        return this.experimentalSessionSkill(requestParameters.sessionID(), requestParameters.experimentalSessionSkillRequest());
    }

    /**
     * Activate skill
     * Activate a skill for a session by appending a skill message and resuming execution.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | SkillNotFoundError
     * @param requestParameters The experimentalSessionSkill request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> experimentalSessionSkillWithHttpInfo(ExperimentalSessionSkillRequest requestParameters) throws WebClientResponseException {
        return this.experimentalSessionSkillWithHttpInfo(requestParameters.sessionID(), requestParameters.experimentalSessionSkillRequest());
    }

    /**
     * Activate skill
     * Activate a skill for a session by appending a skill message and resuming execution.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | SkillNotFoundError
     * @param requestParameters The experimentalSessionSkill request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalSessionSkillWithResponseSpec(ExperimentalSessionSkillRequest requestParameters) throws WebClientResponseException {
        return this.experimentalSessionSkillWithResponseSpec(requestParameters.sessionID(), requestParameters.experimentalSessionSkillRequest());
    }


    /**
     * Activate skill
     * Activate a skill for a session by appending a skill message and resuming execution.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | SkillNotFoundError
     * @param sessionID The sessionID parameter
     * @param experimentalSessionSkillRequest The experimentalSessionSkillRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalSessionSkillRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull ExperimentalSessionSkillRequest experimentalSessionSkillRequest) throws WebClientResponseException {
        Object postBody = experimentalSessionSkillRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling experimentalSessionSkill", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'experimentalSessionSkillRequest' is set
        if (experimentalSessionSkillRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'experimentalSessionSkillRequest' when calling experimentalSessionSkill", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/experimental/session/{sessionID}/skill", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Activate skill
     * Activate a skill for a session by appending a skill message and resuming execution.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | SkillNotFoundError
     * @param sessionID The sessionID parameter
     * @param experimentalSessionSkillRequest The experimentalSessionSkillRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> experimentalSessionSkill(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull ExperimentalSessionSkillRequest experimentalSessionSkillRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return experimentalSessionSkillRequestCreation(sessionID, experimentalSessionSkillRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Activate skill
     * Activate a skill for a session by appending a skill message and resuming execution.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | SkillNotFoundError
     * @param sessionID The sessionID parameter
     * @param experimentalSessionSkillRequest The experimentalSessionSkillRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> experimentalSessionSkillWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull ExperimentalSessionSkillRequest experimentalSessionSkillRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return experimentalSessionSkillRequestCreation(sessionID, experimentalSessionSkillRequest).toEntity(localVarReturnType);
    }

    /**
     * Activate skill
     * Activate a skill for a session by appending a skill message and resuming execution.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | SkillNotFoundError
     * @param sessionID The sessionID parameter
     * @param experimentalSessionSkillRequest The experimentalSessionSkillRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalSessionSkillWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull ExperimentalSessionSkillRequest experimentalSessionSkillRequest) throws WebClientResponseException {
        return experimentalSessionSkillRequestCreation(sessionID, experimentalSessionSkillRequest);
    }

    public class ExperimentalSessionStatsRequest {
        private @jakarta.annotation.Nullable String from;
        private @jakarta.annotation.Nullable String to;
        private @jakarta.annotation.Nullable String project;
        private @jakarta.annotation.Nullable String timezone;
        private @jakarta.annotation.Nullable String tools;

        public ExperimentalSessionStatsRequest() {}

        public ExperimentalSessionStatsRequest(@jakarta.annotation.Nullable String from, @jakarta.annotation.Nullable String to, @jakarta.annotation.Nullable String project, @jakarta.annotation.Nullable String timezone, @jakarta.annotation.Nullable String tools) {
            this.from = from;
            this.to = to;
            this.project = project;
            this.timezone = timezone;
            this.tools = tools;
        }

        public @jakarta.annotation.Nullable String from() {
            return this.from;
        }
        public ExperimentalSessionStatsRequest from(@jakarta.annotation.Nullable String from) {
            this.from = from;
            return this;
        }

        public @jakarta.annotation.Nullable String to() {
            return this.to;
        }
        public ExperimentalSessionStatsRequest to(@jakarta.annotation.Nullable String to) {
            this.to = to;
            return this;
        }

        public @jakarta.annotation.Nullable String project() {
            return this.project;
        }
        public ExperimentalSessionStatsRequest project(@jakarta.annotation.Nullable String project) {
            this.project = project;
            return this;
        }

        public @jakarta.annotation.Nullable String timezone() {
            return this.timezone;
        }
        public ExperimentalSessionStatsRequest timezone(@jakarta.annotation.Nullable String timezone) {
            this.timezone = timezone;
            return this;
        }

        public @jakarta.annotation.Nullable String tools() {
            return this.tools;
        }
        public ExperimentalSessionStatsRequest tools(@jakarta.annotation.Nullable String tools) {
            this.tools = tools;
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
            ExperimentalSessionStatsRequest request = (ExperimentalSessionStatsRequest) o;
            return Objects.equals(this.from, request.from()) &&
                Objects.equals(this.to, request.to()) &&
                Objects.equals(this.project, request.project()) &&
                Objects.equals(this.timezone, request.timezone()) &&
                Objects.equals(this.tools, request.tools());
        }

        @Override
        public int hashCode() {
            return Objects.hash(from, to, project, timezone, tools);
        }
    }

    /**
     * Get session statistics
     * Aggregate local session activity, usage, and tool reliability for a time range.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The experimentalSessionStats request parameters as object
     * @return ExperimentalSessionStats200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ExperimentalSessionStats200Response> experimentalSessionStats(ExperimentalSessionStatsRequest requestParameters) throws WebClientResponseException {
        return this.experimentalSessionStats(requestParameters.from(), requestParameters.to(), requestParameters.project(), requestParameters.timezone(), requestParameters.tools());
    }

    /**
     * Get session statistics
     * Aggregate local session activity, usage, and tool reliability for a time range.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The experimentalSessionStats request parameters as object
     * @return ResponseEntity&lt;ExperimentalSessionStats200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ExperimentalSessionStats200Response>> experimentalSessionStatsWithHttpInfo(ExperimentalSessionStatsRequest requestParameters) throws WebClientResponseException {
        return this.experimentalSessionStatsWithHttpInfo(requestParameters.from(), requestParameters.to(), requestParameters.project(), requestParameters.timezone(), requestParameters.tools());
    }

    /**
     * Get session statistics
     * Aggregate local session activity, usage, and tool reliability for a time range.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The experimentalSessionStats request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalSessionStatsWithResponseSpec(ExperimentalSessionStatsRequest requestParameters) throws WebClientResponseException {
        return this.experimentalSessionStatsWithResponseSpec(requestParameters.from(), requestParameters.to(), requestParameters.project(), requestParameters.timezone(), requestParameters.tools());
    }


    /**
     * Get session statistics
     * Aggregate local session activity, usage, and tool reliability for a time range.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param from The from parameter
     * @param to The to parameter
     * @param project The project parameter
     * @param timezone The timezone parameter
     * @param tools The tools parameter
     * @return ExperimentalSessionStats200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalSessionStatsRequestCreation(@jakarta.annotation.Nullable String from, @jakarta.annotation.Nullable String to, @jakarta.annotation.Nullable String project, @jakarta.annotation.Nullable String timezone, @jakarta.annotation.Nullable String tools) throws WebClientResponseException {
        Object postBody = null;
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "from", from));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "to", to));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "project", project));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "timezone", timezone));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "tools", tools));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<ExperimentalSessionStats200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalSessionStats200Response>() {};
        return apiClient.invokeAPI("/api/experimental/session/stats", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get session statistics
     * Aggregate local session activity, usage, and tool reliability for a time range.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param from The from parameter
     * @param to The to parameter
     * @param project The project parameter
     * @param timezone The timezone parameter
     * @param tools The tools parameter
     * @return ExperimentalSessionStats200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ExperimentalSessionStats200Response> experimentalSessionStats(@jakarta.annotation.Nullable String from, @jakarta.annotation.Nullable String to, @jakarta.annotation.Nullable String project, @jakarta.annotation.Nullable String timezone, @jakarta.annotation.Nullable String tools) throws WebClientResponseException {
        ParameterizedTypeReference<ExperimentalSessionStats200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalSessionStats200Response>() {};
        return experimentalSessionStatsRequestCreation(from, to, project, timezone, tools).bodyToMono(localVarReturnType);
    }

    /**
     * Get session statistics
     * Aggregate local session activity, usage, and tool reliability for a time range.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param from The from parameter
     * @param to The to parameter
     * @param project The project parameter
     * @param timezone The timezone parameter
     * @param tools The tools parameter
     * @return ResponseEntity&lt;ExperimentalSessionStats200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ExperimentalSessionStats200Response>> experimentalSessionStatsWithHttpInfo(@jakarta.annotation.Nullable String from, @jakarta.annotation.Nullable String to, @jakarta.annotation.Nullable String project, @jakarta.annotation.Nullable String timezone, @jakarta.annotation.Nullable String tools) throws WebClientResponseException {
        ParameterizedTypeReference<ExperimentalSessionStats200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalSessionStats200Response>() {};
        return experimentalSessionStatsRequestCreation(from, to, project, timezone, tools).toEntity(localVarReturnType);
    }

    /**
     * Get session statistics
     * Aggregate local session activity, usage, and tool reliability for a time range.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param from The from parameter
     * @param to The to parameter
     * @param project The project parameter
     * @param timezone The timezone parameter
     * @param tools The tools parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalSessionStatsWithResponseSpec(@jakarta.annotation.Nullable String from, @jakarta.annotation.Nullable String to, @jakarta.annotation.Nullable String project, @jakarta.annotation.Nullable String timezone, @jakarta.annotation.Nullable String tools) throws WebClientResponseException {
        return experimentalSessionStatsRequestCreation(from, to, project, timezone, tools);
    }

    /**
     * Wait for session
     * Wait for a session agent loop to become idle.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalSessionWaitRequestCreation(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling experimentalSessionWait", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/experimental/session/{sessionID}/wait", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Wait for session
     * Wait for a session agent loop to become idle.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> experimentalSessionWait(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return experimentalSessionWaitRequestCreation(sessionID).bodyToMono(localVarReturnType);
    }

    /**
     * Wait for session
     * Wait for a session agent loop to become idle.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> experimentalSessionWaitWithHttpInfo(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return experimentalSessionWaitRequestCreation(sessionID).toEntity(localVarReturnType);
    }

    /**
     * Wait for session
     * Wait for a session agent loop to become idle.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param sessionID The sessionID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalSessionWaitWithResponseSpec(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        return experimentalSessionWaitRequestCreation(sessionID);
    }

    /**
     * List active sessions
     * Retrieve foreground Session drains currently owned by this OpenCode process. Sessions absent from the result are inactive.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return SessionActive200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionActiveRequestCreation() throws WebClientResponseException {
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

        ParameterizedTypeReference<SessionActive200Response> localVarReturnType = new ParameterizedTypeReference<SessionActive200Response>() {};
        return apiClient.invokeAPI("/api/session/active", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List active sessions
     * Retrieve foreground Session drains currently owned by this OpenCode process. Sessions absent from the result are inactive.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return SessionActive200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionActive200Response> sessionActive() throws WebClientResponseException {
        ParameterizedTypeReference<SessionActive200Response> localVarReturnType = new ParameterizedTypeReference<SessionActive200Response>() {};
        return sessionActiveRequestCreation().bodyToMono(localVarReturnType);
    }

    /**
     * List active sessions
     * Retrieve foreground Session drains currently owned by this OpenCode process. Sessions absent from the result are inactive.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return ResponseEntity&lt;SessionActive200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionActive200Response>> sessionActiveWithHttpInfo() throws WebClientResponseException {
        ParameterizedTypeReference<SessionActive200Response> localVarReturnType = new ParameterizedTypeReference<SessionActive200Response>() {};
        return sessionActiveRequestCreation().toEntity(localVarReturnType);
    }

    /**
     * List active sessions
     * Retrieve foreground Session drains currently owned by this OpenCode process. Sessions absent from the result are inactive.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionActiveWithResponseSpec() throws WebClientResponseException {
        return sessionActiveRequestCreation();
    }

    /**
     * Background blocking session tools
     * Move active foreground backgroundable tools for this session into background observation. Idle requests are a no-op.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionBackgroundRequestCreation(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionBackground", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/background", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Background blocking session tools
     * Move active foreground backgroundable tools for this session into background observation. Idle requests are a no-op.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionBackground(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionBackgroundRequestCreation(sessionID).bodyToMono(localVarReturnType);
    }

    /**
     * Background blocking session tools
     * Move active foreground backgroundable tools for this session into background observation. Idle requests are a no-op.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionBackgroundWithHttpInfo(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionBackgroundRequestCreation(sessionID).toEntity(localVarReturnType);
    }

    /**
     * Background blocking session tools
     * Move active foreground backgroundable tools for this session into background observation. Idle requests are a no-op.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionBackgroundWithResponseSpec(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        return sessionBackgroundRequestCreation(sessionID);
    }

    public class SessionCommandRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull SessionCommandRequest sessionCommandRequest;

        public SessionCommandRequest() {}

        public SessionCommandRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionCommandRequest sessionCommandRequest) {
            this.sessionID = sessionID;
            this.sessionCommandRequest = sessionCommandRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionCommandRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull SessionCommandRequest sessionCommandRequest() {
            return this.sessionCommandRequest;
        }
        public SessionCommandRequest sessionCommandRequest(@jakarta.annotation.Nonnull SessionCommandRequest sessionCommandRequest) {
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
                Objects.equals(this.sessionCommandRequest, request.sessionCommandRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, sessionCommandRequest);
        }
    }

    /**
     * Run command
     * Execute a slash command callback immediately.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | CommandNotFoundError
     * <p><b>500</b> - CommandExecutionError
     * @param requestParameters The sessionCommand request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionCommand(SessionCommandRequest requestParameters) throws WebClientResponseException {
        return this.sessionCommand(requestParameters.sessionID(), requestParameters.sessionCommandRequest());
    }

    /**
     * Run command
     * Execute a slash command callback immediately.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | CommandNotFoundError
     * <p><b>500</b> - CommandExecutionError
     * @param requestParameters The sessionCommand request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionCommandWithHttpInfo(SessionCommandRequest requestParameters) throws WebClientResponseException {
        return this.sessionCommandWithHttpInfo(requestParameters.sessionID(), requestParameters.sessionCommandRequest());
    }

    /**
     * Run command
     * Execute a slash command callback immediately.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | CommandNotFoundError
     * <p><b>500</b> - CommandExecutionError
     * @param requestParameters The sessionCommand request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionCommandWithResponseSpec(SessionCommandRequest requestParameters) throws WebClientResponseException {
        return this.sessionCommandWithResponseSpec(requestParameters.sessionID(), requestParameters.sessionCommandRequest());
    }


    /**
     * Run command
     * Execute a slash command callback immediately.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | CommandNotFoundError
     * <p><b>500</b> - CommandExecutionError
     * @param sessionID The sessionID parameter
     * @param sessionCommandRequest The sessionCommandRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionCommandRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionCommandRequest sessionCommandRequest) throws WebClientResponseException {
        Object postBody = sessionCommandRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionCommand", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'sessionCommandRequest' is set
        if (sessionCommandRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionCommandRequest' when calling sessionCommand", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/command", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Run command
     * Execute a slash command callback immediately.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | CommandNotFoundError
     * <p><b>500</b> - CommandExecutionError
     * @param sessionID The sessionID parameter
     * @param sessionCommandRequest The sessionCommandRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionCommand(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionCommandRequest sessionCommandRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionCommandRequestCreation(sessionID, sessionCommandRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Run command
     * Execute a slash command callback immediately.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | CommandNotFoundError
     * <p><b>500</b> - CommandExecutionError
     * @param sessionID The sessionID parameter
     * @param sessionCommandRequest The sessionCommandRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionCommandWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionCommandRequest sessionCommandRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionCommandRequestCreation(sessionID, sessionCommandRequest).toEntity(localVarReturnType);
    }

    /**
     * Run command
     * Execute a slash command callback immediately.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | CommandNotFoundError
     * <p><b>500</b> - CommandExecutionError
     * @param sessionID The sessionID parameter
     * @param sessionCommandRequest The sessionCommandRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionCommandWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionCommandRequest sessionCommandRequest) throws WebClientResponseException {
        return sessionCommandRequestCreation(sessionID, sessionCommandRequest);
    }

    public class SessionCompactRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull SessionCompactRequest sessionCompactRequest;

        public SessionCompactRequest() {}

        public SessionCompactRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionCompactRequest sessionCompactRequest) {
            this.sessionID = sessionID;
            this.sessionCompactRequest = sessionCompactRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionCompactRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull SessionCompactRequest sessionCompactRequest() {
            return this.sessionCompactRequest;
        }
        public SessionCompactRequest sessionCompactRequest(@jakarta.annotation.Nonnull SessionCompactRequest sessionCompactRequest) {
            this.sessionCompactRequest = sessionCompactRequest;
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
            SessionCompactRequest request = (SessionCompactRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.sessionCompactRequest, request.sessionCompactRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, sessionCompactRequest);
        }
    }

    /**
     * Compact session
     * Durably admit a session compaction request. Steers by default: it runs at the next step boundary instead of waiting behind queued prompts.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param requestParameters The sessionCompact request parameters as object
     * @return SessionCompact200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionCompact200Response> sessionCompact(SessionCompactRequest requestParameters) throws WebClientResponseException {
        return this.sessionCompact(requestParameters.sessionID(), requestParameters.sessionCompactRequest());
    }

    /**
     * Compact session
     * Durably admit a session compaction request. Steers by default: it runs at the next step boundary instead of waiting behind queued prompts.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param requestParameters The sessionCompact request parameters as object
     * @return ResponseEntity&lt;SessionCompact200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionCompact200Response>> sessionCompactWithHttpInfo(SessionCompactRequest requestParameters) throws WebClientResponseException {
        return this.sessionCompactWithHttpInfo(requestParameters.sessionID(), requestParameters.sessionCompactRequest());
    }

    /**
     * Compact session
     * Durably admit a session compaction request. Steers by default: it runs at the next step boundary instead of waiting behind queued prompts.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param requestParameters The sessionCompact request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionCompactWithResponseSpec(SessionCompactRequest requestParameters) throws WebClientResponseException {
        return this.sessionCompactWithResponseSpec(requestParameters.sessionID(), requestParameters.sessionCompactRequest());
    }


    /**
     * Compact session
     * Durably admit a session compaction request. Steers by default: it runs at the next step boundary instead of waiting behind queued prompts.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param sessionID The sessionID parameter
     * @param sessionCompactRequest The sessionCompactRequest parameter
     * @return SessionCompact200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionCompactRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionCompactRequest sessionCompactRequest) throws WebClientResponseException {
        Object postBody = sessionCompactRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionCompact", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'sessionCompactRequest' is set
        if (sessionCompactRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionCompactRequest' when calling sessionCompact", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<SessionCompact200Response> localVarReturnType = new ParameterizedTypeReference<SessionCompact200Response>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/compact", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Compact session
     * Durably admit a session compaction request. Steers by default: it runs at the next step boundary instead of waiting behind queued prompts.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param sessionID The sessionID parameter
     * @param sessionCompactRequest The sessionCompactRequest parameter
     * @return SessionCompact200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionCompact200Response> sessionCompact(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionCompactRequest sessionCompactRequest) throws WebClientResponseException {
        ParameterizedTypeReference<SessionCompact200Response> localVarReturnType = new ParameterizedTypeReference<SessionCompact200Response>() {};
        return sessionCompactRequestCreation(sessionID, sessionCompactRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Compact session
     * Durably admit a session compaction request. Steers by default: it runs at the next step boundary instead of waiting behind queued prompts.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param sessionID The sessionID parameter
     * @param sessionCompactRequest The sessionCompactRequest parameter
     * @return ResponseEntity&lt;SessionCompact200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionCompact200Response>> sessionCompactWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionCompactRequest sessionCompactRequest) throws WebClientResponseException {
        ParameterizedTypeReference<SessionCompact200Response> localVarReturnType = new ParameterizedTypeReference<SessionCompact200Response>() {};
        return sessionCompactRequestCreation(sessionID, sessionCompactRequest).toEntity(localVarReturnType);
    }

    /**
     * Compact session
     * Durably admit a session compaction request. Steers by default: it runs at the next step boundary instead of waiting behind queued prompts.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param sessionID The sessionID parameter
     * @param sessionCompactRequest The sessionCompactRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionCompactWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionCompactRequest sessionCompactRequest) throws WebClientResponseException {
        return sessionCompactRequestCreation(sessionID, sessionCompactRequest);
    }

    /**
     * Get session context
     * Retrieve the active context messages for a session (all messages after the last compaction).
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @return SessionContext200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionContextRequestCreation(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionContext", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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

        ParameterizedTypeReference<SessionContext200Response> localVarReturnType = new ParameterizedTypeReference<SessionContext200Response>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/context", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get session context
     * Retrieve the active context messages for a session (all messages after the last compaction).
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @return SessionContext200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionContext200Response> sessionContext(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<SessionContext200Response> localVarReturnType = new ParameterizedTypeReference<SessionContext200Response>() {};
        return sessionContextRequestCreation(sessionID).bodyToMono(localVarReturnType);
    }

    /**
     * Get session context
     * Retrieve the active context messages for a session (all messages after the last compaction).
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @return ResponseEntity&lt;SessionContext200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionContext200Response>> sessionContextWithHttpInfo(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<SessionContext200Response> localVarReturnType = new ParameterizedTypeReference<SessionContext200Response>() {};
        return sessionContextRequestCreation(sessionID).toEntity(localVarReturnType);
    }

    /**
     * Get session context
     * Retrieve the active context messages for a session (all messages after the last compaction).
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionContextWithResponseSpec(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        return sessionContextRequestCreation(sessionID);
    }

    /**
     * Create session
     * Create a session at the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param sessionCreateRequest The sessionCreateRequest parameter
     * @return SessionCreate200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionCreateRequestCreation(@jakarta.annotation.Nonnull SessionCreateRequest sessionCreateRequest) throws WebClientResponseException {
        Object postBody = sessionCreateRequest;
        // verify the required parameter 'sessionCreateRequest' is set
        if (sessionCreateRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionCreateRequest' when calling sessionCreate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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

        ParameterizedTypeReference<SessionCreate200Response> localVarReturnType = new ParameterizedTypeReference<SessionCreate200Response>() {};
        return apiClient.invokeAPI("/api/session", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Create session
     * Create a session at the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param sessionCreateRequest The sessionCreateRequest parameter
     * @return SessionCreate200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionCreate200Response> sessionCreate(@jakarta.annotation.Nonnull SessionCreateRequest sessionCreateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<SessionCreate200Response> localVarReturnType = new ParameterizedTypeReference<SessionCreate200Response>() {};
        return sessionCreateRequestCreation(sessionCreateRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Create session
     * Create a session at the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param sessionCreateRequest The sessionCreateRequest parameter
     * @return ResponseEntity&lt;SessionCreate200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionCreate200Response>> sessionCreateWithHttpInfo(@jakarta.annotation.Nonnull SessionCreateRequest sessionCreateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<SessionCreate200Response> localVarReturnType = new ParameterizedTypeReference<SessionCreate200Response>() {};
        return sessionCreateRequestCreation(sessionCreateRequest).toEntity(localVarReturnType);
    }

    /**
     * Create session
     * Create a session at the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param sessionCreateRequest The sessionCreateRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionCreateWithResponseSpec(@jakarta.annotation.Nonnull SessionCreateRequest sessionCreateRequest) throws WebClientResponseException {
        return sessionCreateRequestCreation(sessionCreateRequest);
    }

    public class SessionDiffRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String from;
        private @jakarta.annotation.Nullable String to;
        private @jakarta.annotation.Nullable String context;

        public SessionDiffRequest() {}

        public SessionDiffRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String from, @jakarta.annotation.Nullable String to, @jakarta.annotation.Nullable String context) {
            this.sessionID = sessionID;
            this.from = from;
            this.to = to;
            this.context = context;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionDiffRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String from() {
            return this.from;
        }
        public SessionDiffRequest from(@jakarta.annotation.Nullable String from) {
            this.from = from;
            return this;
        }

        public @jakarta.annotation.Nullable String to() {
            return this.to;
        }
        public SessionDiffRequest to(@jakarta.annotation.Nullable String to) {
            this.to = to;
            return this;
        }

        public @jakarta.annotation.Nullable String context() {
            return this.context;
        }
        public SessionDiffRequest context(@jakarta.annotation.Nullable String context) {
            this.context = context;
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
                Objects.equals(this.from, request.from()) &&
                Objects.equals(this.to, request.to()) &&
                Objects.equals(this.context, request.context());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, from, to, context);
        }
    }

    /**
     * Diff session turns
     * Structured per-file diffs of the files a turn changed. A turn runs from the first prompt after the session was last idle until its next idle marker, so prompts steered in while it was busy belong to the same turn; &#x60;to&#x60; extends the range through a later turn. Compares the range&#39;s first recorded snapshot with its last; a step still running in the active session compares against the working copy. Ranges that span a location change are rejected. In sessions without any idle marker, a prompt&#39;s turn spans until the next user message.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - MessageNotFoundError | SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param requestParameters The sessionDiff request parameters as object
     * @return SessionDiff200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionDiff200Response> sessionDiff(SessionDiffRequest requestParameters) throws WebClientResponseException {
        return this.sessionDiff(requestParameters.sessionID(), requestParameters.from(), requestParameters.to(), requestParameters.context());
    }

    /**
     * Diff session turns
     * Structured per-file diffs of the files a turn changed. A turn runs from the first prompt after the session was last idle until its next idle marker, so prompts steered in while it was busy belong to the same turn; &#x60;to&#x60; extends the range through a later turn. Compares the range&#39;s first recorded snapshot with its last; a step still running in the active session compares against the working copy. Ranges that span a location change are rejected. In sessions without any idle marker, a prompt&#39;s turn spans until the next user message.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - MessageNotFoundError | SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param requestParameters The sessionDiff request parameters as object
     * @return ResponseEntity&lt;SessionDiff200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionDiff200Response>> sessionDiffWithHttpInfo(SessionDiffRequest requestParameters) throws WebClientResponseException {
        return this.sessionDiffWithHttpInfo(requestParameters.sessionID(), requestParameters.from(), requestParameters.to(), requestParameters.context());
    }

    /**
     * Diff session turns
     * Structured per-file diffs of the files a turn changed. A turn runs from the first prompt after the session was last idle until its next idle marker, so prompts steered in while it was busy belong to the same turn; &#x60;to&#x60; extends the range through a later turn. Compares the range&#39;s first recorded snapshot with its last; a step still running in the active session compares against the working copy. Ranges that span a location change are rejected. In sessions without any idle marker, a prompt&#39;s turn spans until the next user message.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - MessageNotFoundError | SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param requestParameters The sessionDiff request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionDiffWithResponseSpec(SessionDiffRequest requestParameters) throws WebClientResponseException {
        return this.sessionDiffWithResponseSpec(requestParameters.sessionID(), requestParameters.from(), requestParameters.to(), requestParameters.context());
    }


    /**
     * Diff session turns
     * Structured per-file diffs of the files a turn changed. A turn runs from the first prompt after the session was last idle until its next idle marker, so prompts steered in while it was busy belong to the same turn; &#x60;to&#x60; extends the range through a later turn. Compares the range&#39;s first recorded snapshot with its last; a step still running in the active session compares against the working copy. Ranges that span a location change are rejected. In sessions without any idle marker, a prompt&#39;s turn spans until the next user message.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - MessageNotFoundError | SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @param from The from parameter
     * @param to The to parameter
     * @param context The context parameter
     * @return SessionDiff200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionDiffRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String from, @jakarta.annotation.Nullable String to, @jakarta.annotation.Nullable String context) throws WebClientResponseException {
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

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "from", from));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "to", to));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "context", context));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<SessionDiff200Response> localVarReturnType = new ParameterizedTypeReference<SessionDiff200Response>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/diff", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Diff session turns
     * Structured per-file diffs of the files a turn changed. A turn runs from the first prompt after the session was last idle until its next idle marker, so prompts steered in while it was busy belong to the same turn; &#x60;to&#x60; extends the range through a later turn. Compares the range&#39;s first recorded snapshot with its last; a step still running in the active session compares against the working copy. Ranges that span a location change are rejected. In sessions without any idle marker, a prompt&#39;s turn spans until the next user message.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - MessageNotFoundError | SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @param from The from parameter
     * @param to The to parameter
     * @param context The context parameter
     * @return SessionDiff200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionDiff200Response> sessionDiff(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String from, @jakarta.annotation.Nullable String to, @jakarta.annotation.Nullable String context) throws WebClientResponseException {
        ParameterizedTypeReference<SessionDiff200Response> localVarReturnType = new ParameterizedTypeReference<SessionDiff200Response>() {};
        return sessionDiffRequestCreation(sessionID, from, to, context).bodyToMono(localVarReturnType);
    }

    /**
     * Diff session turns
     * Structured per-file diffs of the files a turn changed. A turn runs from the first prompt after the session was last idle until its next idle marker, so prompts steered in while it was busy belong to the same turn; &#x60;to&#x60; extends the range through a later turn. Compares the range&#39;s first recorded snapshot with its last; a step still running in the active session compares against the working copy. Ranges that span a location change are rejected. In sessions without any idle marker, a prompt&#39;s turn spans until the next user message.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - MessageNotFoundError | SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @param from The from parameter
     * @param to The to parameter
     * @param context The context parameter
     * @return ResponseEntity&lt;SessionDiff200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionDiff200Response>> sessionDiffWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String from, @jakarta.annotation.Nullable String to, @jakarta.annotation.Nullable String context) throws WebClientResponseException {
        ParameterizedTypeReference<SessionDiff200Response> localVarReturnType = new ParameterizedTypeReference<SessionDiff200Response>() {};
        return sessionDiffRequestCreation(sessionID, from, to, context).toEntity(localVarReturnType);
    }

    /**
     * Diff session turns
     * Structured per-file diffs of the files a turn changed. A turn runs from the first prompt after the session was last idle until its next idle marker, so prompts steered in while it was busy belong to the same turn; &#x60;to&#x60; extends the range through a later turn. Compares the range&#39;s first recorded snapshot with its last; a step still running in the active session compares against the working copy. Ranges that span a location change are rejected. In sessions without any idle marker, a prompt&#39;s turn spans until the next user message.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - MessageNotFoundError | SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @param from The from parameter
     * @param to The to parameter
     * @param context The context parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionDiffWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String from, @jakarta.annotation.Nullable String to, @jakarta.annotation.Nullable String context) throws WebClientResponseException {
        return sessionDiffRequestCreation(sessionID, from, to, context);
    }

    public class SessionEnvironmentRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull SessionEnvironmentRequest sessionEnvironmentRequest;

        public SessionEnvironmentRequest() {}

        public SessionEnvironmentRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionEnvironmentRequest sessionEnvironmentRequest) {
            this.sessionID = sessionID;
            this.sessionEnvironmentRequest = sessionEnvironmentRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionEnvironmentRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull SessionEnvironmentRequest sessionEnvironmentRequest() {
            return this.sessionEnvironmentRequest;
        }
        public SessionEnvironmentRequest sessionEnvironmentRequest(@jakarta.annotation.Nonnull SessionEnvironmentRequest sessionEnvironmentRequest) {
            this.sessionEnvironmentRequest = sessionEnvironmentRequest;
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
            SessionEnvironmentRequest request = (SessionEnvironmentRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.sessionEnvironmentRequest, request.sessionEnvironmentRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, sessionEnvironmentRequest);
        }
    }

    /**
     * Set session environment
     * Replace the process environment used by local shell commands for this session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionEnvironment request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionEnvironment(SessionEnvironmentRequest requestParameters) throws WebClientResponseException {
        return this.sessionEnvironment(requestParameters.sessionID(), requestParameters.sessionEnvironmentRequest());
    }

    /**
     * Set session environment
     * Replace the process environment used by local shell commands for this session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionEnvironment request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionEnvironmentWithHttpInfo(SessionEnvironmentRequest requestParameters) throws WebClientResponseException {
        return this.sessionEnvironmentWithHttpInfo(requestParameters.sessionID(), requestParameters.sessionEnvironmentRequest());
    }

    /**
     * Set session environment
     * Replace the process environment used by local shell commands for this session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionEnvironment request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionEnvironmentWithResponseSpec(SessionEnvironmentRequest requestParameters) throws WebClientResponseException {
        return this.sessionEnvironmentWithResponseSpec(requestParameters.sessionID(), requestParameters.sessionEnvironmentRequest());
    }


    /**
     * Set session environment
     * Replace the process environment used by local shell commands for this session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionEnvironmentRequest The sessionEnvironmentRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionEnvironmentRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionEnvironmentRequest sessionEnvironmentRequest) throws WebClientResponseException {
        Object postBody = sessionEnvironmentRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionEnvironment", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'sessionEnvironmentRequest' is set
        if (sessionEnvironmentRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionEnvironmentRequest' when calling sessionEnvironment", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/environment", HttpMethod.PUT, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Set session environment
     * Replace the process environment used by local shell commands for this session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionEnvironmentRequest The sessionEnvironmentRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionEnvironment(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionEnvironmentRequest sessionEnvironmentRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionEnvironmentRequestCreation(sessionID, sessionEnvironmentRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Set session environment
     * Replace the process environment used by local shell commands for this session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionEnvironmentRequest The sessionEnvironmentRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionEnvironmentWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionEnvironmentRequest sessionEnvironmentRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionEnvironmentRequestCreation(sessionID, sessionEnvironmentRequest).toEntity(localVarReturnType);
    }

    /**
     * Set session environment
     * Replace the process environment used by local shell commands for this session.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionEnvironmentRequest The sessionEnvironmentRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionEnvironmentWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionEnvironmentRequest sessionEnvironmentRequest) throws WebClientResponseException {
        return sessionEnvironmentRequestCreation(sessionID, sessionEnvironmentRequest);
    }

    public class SessionForkRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull SessionForkRequest sessionForkRequest;

        public SessionForkRequest() {}

        public SessionForkRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionForkRequest sessionForkRequest) {
            this.sessionID = sessionID;
            this.sessionForkRequest = sessionForkRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionForkRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull SessionForkRequest sessionForkRequest() {
            return this.sessionForkRequest;
        }
        public SessionForkRequest sessionForkRequest(@jakarta.annotation.Nonnull SessionForkRequest sessionForkRequest) {
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
                Objects.equals(this.sessionForkRequest, request.sessionForkRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, sessionForkRequest);
        }
    }

    /**
     * Fork session
     * Create a child session by copying projected history before a message. Omit before to copy the full history.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | MessageNotFoundError
     * @param requestParameters The sessionFork request parameters as object
     * @return ExperimentalSessionImport200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ExperimentalSessionImport200Response> sessionFork(SessionForkRequest requestParameters) throws WebClientResponseException {
        return this.sessionFork(requestParameters.sessionID(), requestParameters.sessionForkRequest());
    }

    /**
     * Fork session
     * Create a child session by copying projected history before a message. Omit before to copy the full history.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | MessageNotFoundError
     * @param requestParameters The sessionFork request parameters as object
     * @return ResponseEntity&lt;ExperimentalSessionImport200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ExperimentalSessionImport200Response>> sessionForkWithHttpInfo(SessionForkRequest requestParameters) throws WebClientResponseException {
        return this.sessionForkWithHttpInfo(requestParameters.sessionID(), requestParameters.sessionForkRequest());
    }

    /**
     * Fork session
     * Create a child session by copying projected history before a message. Omit before to copy the full history.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | MessageNotFoundError
     * @param requestParameters The sessionFork request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionForkWithResponseSpec(SessionForkRequest requestParameters) throws WebClientResponseException {
        return this.sessionForkWithResponseSpec(requestParameters.sessionID(), requestParameters.sessionForkRequest());
    }


    /**
     * Fork session
     * Create a child session by copying projected history before a message. Omit before to copy the full history.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | MessageNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionForkRequest The sessionForkRequest parameter
     * @return ExperimentalSessionImport200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionForkRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionForkRequest sessionForkRequest) throws WebClientResponseException {
        Object postBody = sessionForkRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionFork", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'sessionForkRequest' is set
        if (sessionForkRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionForkRequest' when calling sessionFork", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<ExperimentalSessionImport200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalSessionImport200Response>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/fork", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Fork session
     * Create a child session by copying projected history before a message. Omit before to copy the full history.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | MessageNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionForkRequest The sessionForkRequest parameter
     * @return ExperimentalSessionImport200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ExperimentalSessionImport200Response> sessionFork(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionForkRequest sessionForkRequest) throws WebClientResponseException {
        ParameterizedTypeReference<ExperimentalSessionImport200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalSessionImport200Response>() {};
        return sessionForkRequestCreation(sessionID, sessionForkRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Fork session
     * Create a child session by copying projected history before a message. Omit before to copy the full history.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | MessageNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionForkRequest The sessionForkRequest parameter
     * @return ResponseEntity&lt;ExperimentalSessionImport200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ExperimentalSessionImport200Response>> sessionForkWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionForkRequest sessionForkRequest) throws WebClientResponseException {
        ParameterizedTypeReference<ExperimentalSessionImport200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalSessionImport200Response>() {};
        return sessionForkRequestCreation(sessionID, sessionForkRequest).toEntity(localVarReturnType);
    }

    /**
     * Fork session
     * Create a child session by copying projected history before a message. Omit before to copy the full history.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | MessageNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionForkRequest The sessionForkRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionForkWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionForkRequest sessionForkRequest) throws WebClientResponseException {
        return sessionForkRequestCreation(sessionID, sessionForkRequest);
    }

    public class SessionFormCancelRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull String formID;

        public SessionFormCancelRequest() {}

        public SessionFormCancelRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String formID) {
            this.sessionID = sessionID;
            this.formID = formID;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionFormCancelRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull String formID() {
            return this.formID;
        }
        public SessionFormCancelRequest formID(@jakarta.annotation.Nonnull String formID) {
            this.formID = formID;
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
            SessionFormCancelRequest request = (SessionFormCancelRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.formID, request.formID());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, formID);
        }
    }

    /**
     * Cancel form
     * Cancel a pending form.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | FormNotFoundError
     * <p><b>409</b> - FormAlreadySettledError
     * @param requestParameters The sessionFormCancel request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionFormCancel(SessionFormCancelRequest requestParameters) throws WebClientResponseException {
        return this.sessionFormCancel(requestParameters.sessionID(), requestParameters.formID());
    }

    /**
     * Cancel form
     * Cancel a pending form.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | FormNotFoundError
     * <p><b>409</b> - FormAlreadySettledError
     * @param requestParameters The sessionFormCancel request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionFormCancelWithHttpInfo(SessionFormCancelRequest requestParameters) throws WebClientResponseException {
        return this.sessionFormCancelWithHttpInfo(requestParameters.sessionID(), requestParameters.formID());
    }

    /**
     * Cancel form
     * Cancel a pending form.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | FormNotFoundError
     * <p><b>409</b> - FormAlreadySettledError
     * @param requestParameters The sessionFormCancel request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionFormCancelWithResponseSpec(SessionFormCancelRequest requestParameters) throws WebClientResponseException {
        return this.sessionFormCancelWithResponseSpec(requestParameters.sessionID(), requestParameters.formID());
    }


    /**
     * Cancel form
     * Cancel a pending form.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | FormNotFoundError
     * <p><b>409</b> - FormAlreadySettledError
     * @param sessionID The sessionID parameter
     * @param formID The formID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionFormCancelRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String formID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionFormCancel", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'formID' is set
        if (formID == null) {
            throw new WebClientResponseException("Missing the required parameter 'formID' when calling sessionFormCancel", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);
        pathParams.put("formID", formID);

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
        return apiClient.invokeAPI("/api/session/{sessionID}/form/{formID}", HttpMethod.DELETE, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Cancel form
     * Cancel a pending form.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | FormNotFoundError
     * <p><b>409</b> - FormAlreadySettledError
     * @param sessionID The sessionID parameter
     * @param formID The formID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionFormCancel(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String formID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionFormCancelRequestCreation(sessionID, formID).bodyToMono(localVarReturnType);
    }

    /**
     * Cancel form
     * Cancel a pending form.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | FormNotFoundError
     * <p><b>409</b> - FormAlreadySettledError
     * @param sessionID The sessionID parameter
     * @param formID The formID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionFormCancelWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String formID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionFormCancelRequestCreation(sessionID, formID).toEntity(localVarReturnType);
    }

    /**
     * Cancel form
     * Cancel a pending form.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | FormNotFoundError
     * <p><b>409</b> - FormAlreadySettledError
     * @param sessionID The sessionID parameter
     * @param formID The formID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionFormCancelWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String formID) throws WebClientResponseException {
        return sessionFormCancelRequestCreation(sessionID, formID);
    }

    public class SessionFormCreateRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull FormCreatePayload formCreatePayload;

        public SessionFormCreateRequest() {}

        public SessionFormCreateRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull FormCreatePayload formCreatePayload) {
            this.sessionID = sessionID;
            this.formCreatePayload = formCreatePayload;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionFormCreateRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull FormCreatePayload formCreatePayload() {
            return this.formCreatePayload;
        }
        public SessionFormCreateRequest formCreatePayload(@jakarta.annotation.Nonnull FormCreatePayload formCreatePayload) {
            this.formCreatePayload = formCreatePayload;
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
            SessionFormCreateRequest request = (SessionFormCreateRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.formCreatePayload, request.formCreatePayload());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, formCreatePayload);
        }
    }

    /**
     * Create session form
     * Create a form for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param requestParameters The sessionFormCreate request parameters as object
     * @return SessionFormCreate200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionFormCreate200Response> sessionFormCreate(SessionFormCreateRequest requestParameters) throws WebClientResponseException {
        return this.sessionFormCreate(requestParameters.sessionID(), requestParameters.formCreatePayload());
    }

    /**
     * Create session form
     * Create a form for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param requestParameters The sessionFormCreate request parameters as object
     * @return ResponseEntity&lt;SessionFormCreate200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionFormCreate200Response>> sessionFormCreateWithHttpInfo(SessionFormCreateRequest requestParameters) throws WebClientResponseException {
        return this.sessionFormCreateWithHttpInfo(requestParameters.sessionID(), requestParameters.formCreatePayload());
    }

    /**
     * Create session form
     * Create a form for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param requestParameters The sessionFormCreate request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionFormCreateWithResponseSpec(SessionFormCreateRequest requestParameters) throws WebClientResponseException {
        return this.sessionFormCreateWithResponseSpec(requestParameters.sessionID(), requestParameters.formCreatePayload());
    }


    /**
     * Create session form
     * Create a form for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param sessionID The sessionID parameter
     * @param formCreatePayload The formCreatePayload parameter
     * @return SessionFormCreate200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionFormCreateRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull FormCreatePayload formCreatePayload) throws WebClientResponseException {
        Object postBody = formCreatePayload;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionFormCreate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'formCreatePayload' is set
        if (formCreatePayload == null) {
            throw new WebClientResponseException("Missing the required parameter 'formCreatePayload' when calling sessionFormCreate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<SessionFormCreate200Response> localVarReturnType = new ParameterizedTypeReference<SessionFormCreate200Response>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/form", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Create session form
     * Create a form for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param sessionID The sessionID parameter
     * @param formCreatePayload The formCreatePayload parameter
     * @return SessionFormCreate200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionFormCreate200Response> sessionFormCreate(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull FormCreatePayload formCreatePayload) throws WebClientResponseException {
        ParameterizedTypeReference<SessionFormCreate200Response> localVarReturnType = new ParameterizedTypeReference<SessionFormCreate200Response>() {};
        return sessionFormCreateRequestCreation(sessionID, formCreatePayload).bodyToMono(localVarReturnType);
    }

    /**
     * Create session form
     * Create a form for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param sessionID The sessionID parameter
     * @param formCreatePayload The formCreatePayload parameter
     * @return ResponseEntity&lt;SessionFormCreate200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionFormCreate200Response>> sessionFormCreateWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull FormCreatePayload formCreatePayload) throws WebClientResponseException {
        ParameterizedTypeReference<SessionFormCreate200Response> localVarReturnType = new ParameterizedTypeReference<SessionFormCreate200Response>() {};
        return sessionFormCreateRequestCreation(sessionID, formCreatePayload).toEntity(localVarReturnType);
    }

    /**
     * Create session form
     * Create a form for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param sessionID The sessionID parameter
     * @param formCreatePayload The formCreatePayload parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionFormCreateWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull FormCreatePayload formCreatePayload) throws WebClientResponseException {
        return sessionFormCreateRequestCreation(sessionID, formCreatePayload);
    }

    public class SessionFormGetRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull String formID;

        public SessionFormGetRequest() {}

        public SessionFormGetRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String formID) {
            this.sessionID = sessionID;
            this.formID = formID;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionFormGetRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull String formID() {
            return this.formID;
        }
        public SessionFormGetRequest formID(@jakarta.annotation.Nonnull String formID) {
            this.formID = formID;
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
            SessionFormGetRequest request = (SessionFormGetRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.formID, request.formID());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, formID);
        }
    }

    /**
     * Get session form
     * Retrieve a form and its current state for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | FormNotFoundError
     * @param requestParameters The sessionFormGet request parameters as object
     * @return SessionFormGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionFormGet200Response> sessionFormGet(SessionFormGetRequest requestParameters) throws WebClientResponseException {
        return this.sessionFormGet(requestParameters.sessionID(), requestParameters.formID());
    }

    /**
     * Get session form
     * Retrieve a form and its current state for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | FormNotFoundError
     * @param requestParameters The sessionFormGet request parameters as object
     * @return ResponseEntity&lt;SessionFormGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionFormGet200Response>> sessionFormGetWithHttpInfo(SessionFormGetRequest requestParameters) throws WebClientResponseException {
        return this.sessionFormGetWithHttpInfo(requestParameters.sessionID(), requestParameters.formID());
    }

    /**
     * Get session form
     * Retrieve a form and its current state for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | FormNotFoundError
     * @param requestParameters The sessionFormGet request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionFormGetWithResponseSpec(SessionFormGetRequest requestParameters) throws WebClientResponseException {
        return this.sessionFormGetWithResponseSpec(requestParameters.sessionID(), requestParameters.formID());
    }


    /**
     * Get session form
     * Retrieve a form and its current state for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | FormNotFoundError
     * @param sessionID The sessionID parameter
     * @param formID The formID parameter
     * @return SessionFormGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionFormGetRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String formID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionFormGet", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'formID' is set
        if (formID == null) {
            throw new WebClientResponseException("Missing the required parameter 'formID' when calling sessionFormGet", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);
        pathParams.put("formID", formID);

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

        ParameterizedTypeReference<SessionFormGet200Response> localVarReturnType = new ParameterizedTypeReference<SessionFormGet200Response>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/form/{formID}", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get session form
     * Retrieve a form and its current state for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | FormNotFoundError
     * @param sessionID The sessionID parameter
     * @param formID The formID parameter
     * @return SessionFormGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionFormGet200Response> sessionFormGet(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String formID) throws WebClientResponseException {
        ParameterizedTypeReference<SessionFormGet200Response> localVarReturnType = new ParameterizedTypeReference<SessionFormGet200Response>() {};
        return sessionFormGetRequestCreation(sessionID, formID).bodyToMono(localVarReturnType);
    }

    /**
     * Get session form
     * Retrieve a form and its current state for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | FormNotFoundError
     * @param sessionID The sessionID parameter
     * @param formID The formID parameter
     * @return ResponseEntity&lt;SessionFormGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionFormGet200Response>> sessionFormGetWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String formID) throws WebClientResponseException {
        ParameterizedTypeReference<SessionFormGet200Response> localVarReturnType = new ParameterizedTypeReference<SessionFormGet200Response>() {};
        return sessionFormGetRequestCreation(sessionID, formID).toEntity(localVarReturnType);
    }

    /**
     * Get session form
     * Retrieve a form and its current state for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | FormNotFoundError
     * @param sessionID The sessionID parameter
     * @param formID The formID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionFormGetWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String formID) throws WebClientResponseException {
        return sessionFormGetRequestCreation(sessionID, formID);
    }

    /**
     * List session forms
     * Retrieve pending forms for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return SessionFormList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionFormListRequestCreation(@jakarta.annotation.Nullable String sessionID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionFormList", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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

        ParameterizedTypeReference<SessionFormList200Response> localVarReturnType = new ParameterizedTypeReference<SessionFormList200Response>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/form", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List session forms
     * Retrieve pending forms for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return SessionFormList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionFormList200Response> sessionFormList(@jakarta.annotation.Nullable String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<SessionFormList200Response> localVarReturnType = new ParameterizedTypeReference<SessionFormList200Response>() {};
        return sessionFormListRequestCreation(sessionID).bodyToMono(localVarReturnType);
    }

    /**
     * List session forms
     * Retrieve pending forms for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return ResponseEntity&lt;SessionFormList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionFormList200Response>> sessionFormListWithHttpInfo(@jakarta.annotation.Nullable String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<SessionFormList200Response> localVarReturnType = new ParameterizedTypeReference<SessionFormList200Response>() {};
        return sessionFormListRequestCreation(sessionID).toEntity(localVarReturnType);
    }

    /**
     * List session forms
     * Retrieve pending forms for a session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionFormListWithResponseSpec(@jakarta.annotation.Nullable String sessionID) throws WebClientResponseException {
        return sessionFormListRequestCreation(sessionID);
    }

    public class SessionFormReplyRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull String formID;
        private @jakarta.annotation.Nonnull FormReply formReply;

        public SessionFormReplyRequest() {}

        public SessionFormReplyRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String formID, @jakarta.annotation.Nonnull FormReply formReply) {
            this.sessionID = sessionID;
            this.formID = formID;
            this.formReply = formReply;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionFormReplyRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull String formID() {
            return this.formID;
        }
        public SessionFormReplyRequest formID(@jakarta.annotation.Nonnull String formID) {
            this.formID = formID;
            return this;
        }

        public @jakarta.annotation.Nonnull FormReply formReply() {
            return this.formReply;
        }
        public SessionFormReplyRequest formReply(@jakarta.annotation.Nonnull FormReply formReply) {
            this.formReply = formReply;
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
            SessionFormReplyRequest request = (SessionFormReplyRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.formID, request.formID()) &&
                Objects.equals(this.formReply, request.formReply());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, formID, formReply);
        }
    }

    /**
     * Reply to form
     * Submit an answer to a pending form.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - FormInvalidAnswerError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | FormNotFoundError
     * <p><b>409</b> - FormAlreadySettledError
     * @param requestParameters The sessionFormReply request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionFormReply(SessionFormReplyRequest requestParameters) throws WebClientResponseException {
        return this.sessionFormReply(requestParameters.sessionID(), requestParameters.formID(), requestParameters.formReply());
    }

    /**
     * Reply to form
     * Submit an answer to a pending form.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - FormInvalidAnswerError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | FormNotFoundError
     * <p><b>409</b> - FormAlreadySettledError
     * @param requestParameters The sessionFormReply request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionFormReplyWithHttpInfo(SessionFormReplyRequest requestParameters) throws WebClientResponseException {
        return this.sessionFormReplyWithHttpInfo(requestParameters.sessionID(), requestParameters.formID(), requestParameters.formReply());
    }

    /**
     * Reply to form
     * Submit an answer to a pending form.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - FormInvalidAnswerError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | FormNotFoundError
     * <p><b>409</b> - FormAlreadySettledError
     * @param requestParameters The sessionFormReply request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionFormReplyWithResponseSpec(SessionFormReplyRequest requestParameters) throws WebClientResponseException {
        return this.sessionFormReplyWithResponseSpec(requestParameters.sessionID(), requestParameters.formID(), requestParameters.formReply());
    }


    /**
     * Reply to form
     * Submit an answer to a pending form.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - FormInvalidAnswerError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | FormNotFoundError
     * <p><b>409</b> - FormAlreadySettledError
     * @param sessionID The sessionID parameter
     * @param formID The formID parameter
     * @param formReply The formReply parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionFormReplyRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String formID, @jakarta.annotation.Nonnull FormReply formReply) throws WebClientResponseException {
        Object postBody = formReply;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionFormReply", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'formID' is set
        if (formID == null) {
            throw new WebClientResponseException("Missing the required parameter 'formID' when calling sessionFormReply", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'formReply' is set
        if (formReply == null) {
            throw new WebClientResponseException("Missing the required parameter 'formReply' when calling sessionFormReply", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);
        pathParams.put("formID", formID);

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
        return apiClient.invokeAPI("/api/session/{sessionID}/form/{formID}/reply", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Reply to form
     * Submit an answer to a pending form.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - FormInvalidAnswerError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | FormNotFoundError
     * <p><b>409</b> - FormAlreadySettledError
     * @param sessionID The sessionID parameter
     * @param formID The formID parameter
     * @param formReply The formReply parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionFormReply(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String formID, @jakarta.annotation.Nonnull FormReply formReply) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionFormReplyRequestCreation(sessionID, formID, formReply).bodyToMono(localVarReturnType);
    }

    /**
     * Reply to form
     * Submit an answer to a pending form.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - FormInvalidAnswerError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | FormNotFoundError
     * <p><b>409</b> - FormAlreadySettledError
     * @param sessionID The sessionID parameter
     * @param formID The formID parameter
     * @param formReply The formReply parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionFormReplyWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String formID, @jakarta.annotation.Nonnull FormReply formReply) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionFormReplyRequestCreation(sessionID, formID, formReply).toEntity(localVarReturnType);
    }

    /**
     * Reply to form
     * Submit an answer to a pending form.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - FormInvalidAnswerError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | FormNotFoundError
     * <p><b>409</b> - FormAlreadySettledError
     * @param sessionID The sessionID parameter
     * @param formID The formID parameter
     * @param formReply The formReply parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionFormReplyWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String formID, @jakarta.annotation.Nonnull FormReply formReply) throws WebClientResponseException {
        return sessionFormReplyRequestCreation(sessionID, formID, formReply);
    }

    public class SessionGenerateRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull SessionGenerateRequest sessionGenerateRequest;

        public SessionGenerateRequest() {}

        public SessionGenerateRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionGenerateRequest sessionGenerateRequest) {
            this.sessionID = sessionID;
            this.sessionGenerateRequest = sessionGenerateRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionGenerateRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull SessionGenerateRequest sessionGenerateRequest() {
            return this.sessionGenerateRequest;
        }
        public SessionGenerateRequest sessionGenerateRequest(@jakarta.annotation.Nonnull SessionGenerateRequest sessionGenerateRequest) {
            this.sessionGenerateRequest = sessionGenerateRequest;
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
            SessionGenerateRequest request = (SessionGenerateRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.sessionGenerateRequest, request.sessionGenerateRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, sessionGenerateRequest);
        }
    }

    /**
     * Generate text from session context
     * Generate transient text from the current session context without mutating session history.
     * <p><b>200</b> - SessionGenerateResponse
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The sessionGenerate request parameters as object
     * @return SessionGenerateResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionGenerateResponse> sessionGenerate(SessionGenerateRequest requestParameters) throws WebClientResponseException {
        return this.sessionGenerate(requestParameters.sessionID(), requestParameters.sessionGenerateRequest());
    }

    /**
     * Generate text from session context
     * Generate transient text from the current session context without mutating session history.
     * <p><b>200</b> - SessionGenerateResponse
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The sessionGenerate request parameters as object
     * @return ResponseEntity&lt;SessionGenerateResponse&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionGenerateResponse>> sessionGenerateWithHttpInfo(SessionGenerateRequest requestParameters) throws WebClientResponseException {
        return this.sessionGenerateWithHttpInfo(requestParameters.sessionID(), requestParameters.sessionGenerateRequest());
    }

    /**
     * Generate text from session context
     * Generate transient text from the current session context without mutating session history.
     * <p><b>200</b> - SessionGenerateResponse
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The sessionGenerate request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionGenerateWithResponseSpec(SessionGenerateRequest requestParameters) throws WebClientResponseException {
        return this.sessionGenerateWithResponseSpec(requestParameters.sessionID(), requestParameters.sessionGenerateRequest());
    }


    /**
     * Generate text from session context
     * Generate transient text from the current session context without mutating session history.
     * <p><b>200</b> - SessionGenerateResponse
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param sessionID The sessionID parameter
     * @param sessionGenerateRequest The sessionGenerateRequest parameter
     * @return SessionGenerateResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionGenerateRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionGenerateRequest sessionGenerateRequest) throws WebClientResponseException {
        Object postBody = sessionGenerateRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionGenerate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'sessionGenerateRequest' is set
        if (sessionGenerateRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionGenerateRequest' when calling sessionGenerate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<SessionGenerateResponse> localVarReturnType = new ParameterizedTypeReference<SessionGenerateResponse>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/generate", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Generate text from session context
     * Generate transient text from the current session context without mutating session history.
     * <p><b>200</b> - SessionGenerateResponse
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param sessionID The sessionID parameter
     * @param sessionGenerateRequest The sessionGenerateRequest parameter
     * @return SessionGenerateResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionGenerateResponse> sessionGenerate(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionGenerateRequest sessionGenerateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<SessionGenerateResponse> localVarReturnType = new ParameterizedTypeReference<SessionGenerateResponse>() {};
        return sessionGenerateRequestCreation(sessionID, sessionGenerateRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Generate text from session context
     * Generate transient text from the current session context without mutating session history.
     * <p><b>200</b> - SessionGenerateResponse
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param sessionID The sessionID parameter
     * @param sessionGenerateRequest The sessionGenerateRequest parameter
     * @return ResponseEntity&lt;SessionGenerateResponse&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionGenerateResponse>> sessionGenerateWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionGenerateRequest sessionGenerateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<SessionGenerateResponse> localVarReturnType = new ParameterizedTypeReference<SessionGenerateResponse>() {};
        return sessionGenerateRequestCreation(sessionID, sessionGenerateRequest).toEntity(localVarReturnType);
    }

    /**
     * Generate text from session context
     * Generate transient text from the current session context without mutating session history.
     * <p><b>200</b> - SessionGenerateResponse
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>503</b> - ServiceUnavailableError
     * @param sessionID The sessionID parameter
     * @param sessionGenerateRequest The sessionGenerateRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionGenerateWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionGenerateRequest sessionGenerateRequest) throws WebClientResponseException {
        return sessionGenerateRequestCreation(sessionID, sessionGenerateRequest);
    }

    /**
     * Get session
     * Retrieve a session by ID.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return ExperimentalSessionImport200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionGetRequestCreation(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
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

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<ExperimentalSessionImport200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalSessionImport200Response>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get session
     * Retrieve a session by ID.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return ExperimentalSessionImport200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ExperimentalSessionImport200Response> sessionGet(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<ExperimentalSessionImport200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalSessionImport200Response>() {};
        return sessionGetRequestCreation(sessionID).bodyToMono(localVarReturnType);
    }

    /**
     * Get session
     * Retrieve a session by ID.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return ResponseEntity&lt;ExperimentalSessionImport200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ExperimentalSessionImport200Response>> sessionGetWithHttpInfo(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<ExperimentalSessionImport200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalSessionImport200Response>() {};
        return sessionGetRequestCreation(sessionID).toEntity(localVarReturnType);
    }

    /**
     * Get session
     * Retrieve a session by ID.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionGetWithResponseSpec(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        return sessionGetRequestCreation(sessionID);
    }

    public class SessionInboxCancelRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String inboxID;

        public SessionInboxCancelRequest() {}

        public SessionInboxCancelRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String inboxID) {
            this.sessionID = sessionID;
            this.inboxID = inboxID;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionInboxCancelRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String inboxID() {
            return this.inboxID;
        }
        public SessionInboxCancelRequest inboxID(@jakarta.annotation.Nullable String inboxID) {
            this.inboxID = inboxID;
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
            SessionInboxCancelRequest request = (SessionInboxCancelRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.inboxID, request.inboxID());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, inboxID);
        }
    }

    /**
     * Cancel inbox input
     * Cancel an inbox item that has not yet been delivered. Unavailable items are a no-op.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionInboxCancel request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionInboxCancel(SessionInboxCancelRequest requestParameters) throws WebClientResponseException {
        return this.sessionInboxCancel(requestParameters.sessionID(), requestParameters.inboxID());
    }

    /**
     * Cancel inbox input
     * Cancel an inbox item that has not yet been delivered. Unavailable items are a no-op.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionInboxCancel request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionInboxCancelWithHttpInfo(SessionInboxCancelRequest requestParameters) throws WebClientResponseException {
        return this.sessionInboxCancelWithHttpInfo(requestParameters.sessionID(), requestParameters.inboxID());
    }

    /**
     * Cancel inbox input
     * Cancel an inbox item that has not yet been delivered. Unavailable items are a no-op.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionInboxCancel request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionInboxCancelWithResponseSpec(SessionInboxCancelRequest requestParameters) throws WebClientResponseException {
        return this.sessionInboxCancelWithResponseSpec(requestParameters.sessionID(), requestParameters.inboxID());
    }


    /**
     * Cancel inbox input
     * Cancel an inbox item that has not yet been delivered. Unavailable items are a no-op.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param inboxID The inboxID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionInboxCancelRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String inboxID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionInboxCancel", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'inboxID' is set
        if (inboxID == null) {
            throw new WebClientResponseException("Missing the required parameter 'inboxID' when calling sessionInboxCancel", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);
        pathParams.put("inboxID", inboxID);

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
        return apiClient.invokeAPI("/api/session/{sessionID}/inbox/{inboxID}", HttpMethod.DELETE, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Cancel inbox input
     * Cancel an inbox item that has not yet been delivered. Unavailable items are a no-op.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param inboxID The inboxID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionInboxCancel(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String inboxID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionInboxCancelRequestCreation(sessionID, inboxID).bodyToMono(localVarReturnType);
    }

    /**
     * Cancel inbox input
     * Cancel an inbox item that has not yet been delivered. Unavailable items are a no-op.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param inboxID The inboxID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionInboxCancelWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String inboxID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionInboxCancelRequestCreation(sessionID, inboxID).toEntity(localVarReturnType);
    }

    /**
     * Cancel inbox input
     * Cancel an inbox item that has not yet been delivered. Unavailable items are a no-op.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param inboxID The inboxID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionInboxCancelWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String inboxID) throws WebClientResponseException {
        return sessionInboxCancelRequestCreation(sessionID, inboxID);
    }

    /**
     * List session inbox
     * List durable enqueued session work not yet delivered, ordered by enqueue sequence. Includes user, synthetic, compaction, and move items.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return SessionInboxList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionInboxListRequestCreation(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionInboxList", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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

        ParameterizedTypeReference<SessionInboxList200Response> localVarReturnType = new ParameterizedTypeReference<SessionInboxList200Response>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/inbox", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List session inbox
     * List durable enqueued session work not yet delivered, ordered by enqueue sequence. Includes user, synthetic, compaction, and move items.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return SessionInboxList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionInboxList200Response> sessionInboxList(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<SessionInboxList200Response> localVarReturnType = new ParameterizedTypeReference<SessionInboxList200Response>() {};
        return sessionInboxListRequestCreation(sessionID).bodyToMono(localVarReturnType);
    }

    /**
     * List session inbox
     * List durable enqueued session work not yet delivered, ordered by enqueue sequence. Includes user, synthetic, compaction, and move items.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return ResponseEntity&lt;SessionInboxList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionInboxList200Response>> sessionInboxListWithHttpInfo(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<SessionInboxList200Response> localVarReturnType = new ParameterizedTypeReference<SessionInboxList200Response>() {};
        return sessionInboxListRequestCreation(sessionID).toEntity(localVarReturnType);
    }

    /**
     * List session inbox
     * List durable enqueued session work not yet delivered, ordered by enqueue sequence. Includes user, synthetic, compaction, and move items.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionInboxListWithResponseSpec(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        return sessionInboxListRequestCreation(sessionID);
    }

    public class SessionInboxUpdateRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull String inboxID;
        private @jakarta.annotation.Nonnull SessionInboxUpdateRequest sessionInboxUpdateRequest;

        public SessionInboxUpdateRequest() {}

        public SessionInboxUpdateRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String inboxID, @jakarta.annotation.Nonnull SessionInboxUpdateRequest sessionInboxUpdateRequest) {
            this.sessionID = sessionID;
            this.inboxID = inboxID;
            this.sessionInboxUpdateRequest = sessionInboxUpdateRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionInboxUpdateRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull String inboxID() {
            return this.inboxID;
        }
        public SessionInboxUpdateRequest inboxID(@jakarta.annotation.Nonnull String inboxID) {
            this.inboxID = inboxID;
            return this;
        }

        public @jakarta.annotation.Nonnull SessionInboxUpdateRequest sessionInboxUpdateRequest() {
            return this.sessionInboxUpdateRequest;
        }
        public SessionInboxUpdateRequest sessionInboxUpdateRequest(@jakarta.annotation.Nonnull SessionInboxUpdateRequest sessionInboxUpdateRequest) {
            this.sessionInboxUpdateRequest = sessionInboxUpdateRequest;
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
            SessionInboxUpdateRequest request = (SessionInboxUpdateRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.inboxID, request.inboxID()) &&
                Objects.equals(this.sessionInboxUpdateRequest, request.sessionInboxUpdateRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, inboxID, sessionInboxUpdateRequest);
        }
    }

    /**
     * Update inbox item
     * Change a pending inbox item&#39;s delivery mode. Steering wakes session execution.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param requestParameters The sessionInboxUpdate request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionInboxUpdate(SessionInboxUpdateRequest requestParameters) throws WebClientResponseException {
        return this.sessionInboxUpdate(requestParameters.sessionID(), requestParameters.inboxID(), requestParameters.sessionInboxUpdateRequest());
    }

    /**
     * Update inbox item
     * Change a pending inbox item&#39;s delivery mode. Steering wakes session execution.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param requestParameters The sessionInboxUpdate request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionInboxUpdateWithHttpInfo(SessionInboxUpdateRequest requestParameters) throws WebClientResponseException {
        return this.sessionInboxUpdateWithHttpInfo(requestParameters.sessionID(), requestParameters.inboxID(), requestParameters.sessionInboxUpdateRequest());
    }

    /**
     * Update inbox item
     * Change a pending inbox item&#39;s delivery mode. Steering wakes session execution.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param requestParameters The sessionInboxUpdate request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionInboxUpdateWithResponseSpec(SessionInboxUpdateRequest requestParameters) throws WebClientResponseException {
        return this.sessionInboxUpdateWithResponseSpec(requestParameters.sessionID(), requestParameters.inboxID(), requestParameters.sessionInboxUpdateRequest());
    }


    /**
     * Update inbox item
     * Change a pending inbox item&#39;s delivery mode. Steering wakes session execution.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param sessionID The sessionID parameter
     * @param inboxID The inboxID parameter
     * @param sessionInboxUpdateRequest The sessionInboxUpdateRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionInboxUpdateRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String inboxID, @jakarta.annotation.Nonnull SessionInboxUpdateRequest sessionInboxUpdateRequest) throws WebClientResponseException {
        Object postBody = sessionInboxUpdateRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionInboxUpdate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'inboxID' is set
        if (inboxID == null) {
            throw new WebClientResponseException("Missing the required parameter 'inboxID' when calling sessionInboxUpdate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'sessionInboxUpdateRequest' is set
        if (sessionInboxUpdateRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionInboxUpdateRequest' when calling sessionInboxUpdate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);
        pathParams.put("inboxID", inboxID);

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
        return apiClient.invokeAPI("/api/session/{sessionID}/inbox/{inboxID}", HttpMethod.PATCH, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Update inbox item
     * Change a pending inbox item&#39;s delivery mode. Steering wakes session execution.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param sessionID The sessionID parameter
     * @param inboxID The inboxID parameter
     * @param sessionInboxUpdateRequest The sessionInboxUpdateRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionInboxUpdate(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String inboxID, @jakarta.annotation.Nonnull SessionInboxUpdateRequest sessionInboxUpdateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionInboxUpdateRequestCreation(sessionID, inboxID, sessionInboxUpdateRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Update inbox item
     * Change a pending inbox item&#39;s delivery mode. Steering wakes session execution.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param sessionID The sessionID parameter
     * @param inboxID The inboxID parameter
     * @param sessionInboxUpdateRequest The sessionInboxUpdateRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionInboxUpdateWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String inboxID, @jakarta.annotation.Nonnull SessionInboxUpdateRequest sessionInboxUpdateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionInboxUpdateRequestCreation(sessionID, inboxID, sessionInboxUpdateRequest).toEntity(localVarReturnType);
    }

    /**
     * Update inbox item
     * Change a pending inbox item&#39;s delivery mode. Steering wakes session execution.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param sessionID The sessionID parameter
     * @param inboxID The inboxID parameter
     * @param sessionInboxUpdateRequest The sessionInboxUpdateRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionInboxUpdateWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull String inboxID, @jakarta.annotation.Nonnull SessionInboxUpdateRequest sessionInboxUpdateRequest) throws WebClientResponseException {
        return sessionInboxUpdateRequestCreation(sessionID, inboxID, sessionInboxUpdateRequest);
    }

    public class SessionInterruptRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String resume;

        public SessionInterruptRequest() {}

        public SessionInterruptRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String resume) {
            this.sessionID = sessionID;
            this.resume = resume;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionInterruptRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String resume() {
            return this.resume;
        }
        public SessionInterruptRequest resume(@jakarta.annotation.Nullable String resume) {
            this.resume = resume;
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
            SessionInterruptRequest request = (SessionInterruptRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.resume, request.resume());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, resume);
        }
    }

    /**
     * Interrupt session execution
     * Interrupt active execution owned by this OpenCode process. Returns interrupted&#x3D;true when an active execution was interrupted and false for the idle no-op. When resume&#x3D;true, execution resumes pending steering input and next-in-line control items (manual compaction, moves) while queued prompts remain parked.
     * <p><b>200</b> - SessionInterruptResponse
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionInterrupt request parameters as object
     * @return SessionInterruptResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionInterruptResponse> sessionInterrupt(SessionInterruptRequest requestParameters) throws WebClientResponseException {
        return this.sessionInterrupt(requestParameters.sessionID(), requestParameters.resume());
    }

    /**
     * Interrupt session execution
     * Interrupt active execution owned by this OpenCode process. Returns interrupted&#x3D;true when an active execution was interrupted and false for the idle no-op. When resume&#x3D;true, execution resumes pending steering input and next-in-line control items (manual compaction, moves) while queued prompts remain parked.
     * <p><b>200</b> - SessionInterruptResponse
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionInterrupt request parameters as object
     * @return ResponseEntity&lt;SessionInterruptResponse&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionInterruptResponse>> sessionInterruptWithHttpInfo(SessionInterruptRequest requestParameters) throws WebClientResponseException {
        return this.sessionInterruptWithHttpInfo(requestParameters.sessionID(), requestParameters.resume());
    }

    /**
     * Interrupt session execution
     * Interrupt active execution owned by this OpenCode process. Returns interrupted&#x3D;true when an active execution was interrupted and false for the idle no-op. When resume&#x3D;true, execution resumes pending steering input and next-in-line control items (manual compaction, moves) while queued prompts remain parked.
     * <p><b>200</b> - SessionInterruptResponse
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionInterrupt request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionInterruptWithResponseSpec(SessionInterruptRequest requestParameters) throws WebClientResponseException {
        return this.sessionInterruptWithResponseSpec(requestParameters.sessionID(), requestParameters.resume());
    }


    /**
     * Interrupt session execution
     * Interrupt active execution owned by this OpenCode process. Returns interrupted&#x3D;true when an active execution was interrupted and false for the idle no-op. When resume&#x3D;true, execution resumes pending steering input and next-in-line control items (manual compaction, moves) while queued prompts remain parked.
     * <p><b>200</b> - SessionInterruptResponse
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param resume The resume parameter
     * @return SessionInterruptResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionInterruptRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String resume) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionInterrupt", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "resume", resume));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<SessionInterruptResponse> localVarReturnType = new ParameterizedTypeReference<SessionInterruptResponse>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/interrupt", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Interrupt session execution
     * Interrupt active execution owned by this OpenCode process. Returns interrupted&#x3D;true when an active execution was interrupted and false for the idle no-op. When resume&#x3D;true, execution resumes pending steering input and next-in-line control items (manual compaction, moves) while queued prompts remain parked.
     * <p><b>200</b> - SessionInterruptResponse
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param resume The resume parameter
     * @return SessionInterruptResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionInterruptResponse> sessionInterrupt(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String resume) throws WebClientResponseException {
        ParameterizedTypeReference<SessionInterruptResponse> localVarReturnType = new ParameterizedTypeReference<SessionInterruptResponse>() {};
        return sessionInterruptRequestCreation(sessionID, resume).bodyToMono(localVarReturnType);
    }

    /**
     * Interrupt session execution
     * Interrupt active execution owned by this OpenCode process. Returns interrupted&#x3D;true when an active execution was interrupted and false for the idle no-op. When resume&#x3D;true, execution resumes pending steering input and next-in-line control items (manual compaction, moves) while queued prompts remain parked.
     * <p><b>200</b> - SessionInterruptResponse
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param resume The resume parameter
     * @return ResponseEntity&lt;SessionInterruptResponse&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionInterruptResponse>> sessionInterruptWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String resume) throws WebClientResponseException {
        ParameterizedTypeReference<SessionInterruptResponse> localVarReturnType = new ParameterizedTypeReference<SessionInterruptResponse>() {};
        return sessionInterruptRequestCreation(sessionID, resume).toEntity(localVarReturnType);
    }

    /**
     * Interrupt session execution
     * Interrupt active execution owned by this OpenCode process. Returns interrupted&#x3D;true when an active execution was interrupted and false for the idle no-op. When resume&#x3D;true, execution resumes pending steering input and next-in-line control items (manual compaction, moves) while queued prompts remain parked.
     * <p><b>200</b> - SessionInterruptResponse
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param resume The resume parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionInterruptWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String resume) throws WebClientResponseException {
        return sessionInterruptRequestCreation(sessionID, resume);
    }

    public class SessionListRequest {
        private @jakarta.annotation.Nullable String limit;
        private @jakarta.annotation.Nullable String order;
        private @jakarta.annotation.Nullable String search;
        private @jakarta.annotation.Nullable String parentID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String project;
        private @jakarta.annotation.Nullable String subpath;
        private @jakarta.annotation.Nullable String cursor;

        public SessionListRequest() {}

        public SessionListRequest(@jakarta.annotation.Nullable String limit, @jakarta.annotation.Nullable String order, @jakarta.annotation.Nullable String search, @jakarta.annotation.Nullable String parentID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String project, @jakarta.annotation.Nullable String subpath, @jakarta.annotation.Nullable String cursor) {
            this.limit = limit;
            this.order = order;
            this.search = search;
            this.parentID = parentID;
            this.directory = directory;
            this.project = project;
            this.subpath = subpath;
            this.cursor = cursor;
        }

        public @jakarta.annotation.Nullable String limit() {
            return this.limit;
        }
        public SessionListRequest limit(@jakarta.annotation.Nullable String limit) {
            this.limit = limit;
            return this;
        }

        public @jakarta.annotation.Nullable String order() {
            return this.order;
        }
        public SessionListRequest order(@jakarta.annotation.Nullable String order) {
            this.order = order;
            return this;
        }

        public @jakarta.annotation.Nullable String search() {
            return this.search;
        }
        public SessionListRequest search(@jakarta.annotation.Nullable String search) {
            this.search = search;
            return this;
        }

        public @jakarta.annotation.Nullable String parentID() {
            return this.parentID;
        }
        public SessionListRequest parentID(@jakarta.annotation.Nullable String parentID) {
            this.parentID = parentID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public SessionListRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String project() {
            return this.project;
        }
        public SessionListRequest project(@jakarta.annotation.Nullable String project) {
            this.project = project;
            return this;
        }

        public @jakarta.annotation.Nullable String subpath() {
            return this.subpath;
        }
        public SessionListRequest subpath(@jakarta.annotation.Nullable String subpath) {
            this.subpath = subpath;
            return this;
        }

        public @jakarta.annotation.Nullable String cursor() {
            return this.cursor;
        }
        public SessionListRequest cursor(@jakarta.annotation.Nullable String cursor) {
            this.cursor = cursor;
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
            return Objects.equals(this.limit, request.limit()) &&
                Objects.equals(this.order, request.order()) &&
                Objects.equals(this.search, request.search()) &&
                Objects.equals(this.parentID, request.parentID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.project, request.project()) &&
                Objects.equals(this.subpath, request.subpath()) &&
                Objects.equals(this.cursor, request.cursor());
        }

        @Override
        public int hashCode() {
            return Objects.hash(limit, order, search, parentID, directory, project, subpath, cursor);
        }
    }

    /**
     * List sessions
     * Retrieve sessions in the requested order. Items keep that order across pages; use cursor.next or cursor.previous to move through the ordered list.
     * <p><b>200</b> - SessionsResponse
     * <p><b>400</b> - InvalidCursorError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The sessionList request parameters as object
     * @return SessionsResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionsResponse> sessionList(SessionListRequest requestParameters) throws WebClientResponseException {
        return this.sessionList(requestParameters.limit(), requestParameters.order(), requestParameters.search(), requestParameters.parentID(), requestParameters.directory(), requestParameters.project(), requestParameters.subpath(), requestParameters.cursor());
    }

    /**
     * List sessions
     * Retrieve sessions in the requested order. Items keep that order across pages; use cursor.next or cursor.previous to move through the ordered list.
     * <p><b>200</b> - SessionsResponse
     * <p><b>400</b> - InvalidCursorError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The sessionList request parameters as object
     * @return ResponseEntity&lt;SessionsResponse&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionsResponse>> sessionListWithHttpInfo(SessionListRequest requestParameters) throws WebClientResponseException {
        return this.sessionListWithHttpInfo(requestParameters.limit(), requestParameters.order(), requestParameters.search(), requestParameters.parentID(), requestParameters.directory(), requestParameters.project(), requestParameters.subpath(), requestParameters.cursor());
    }

    /**
     * List sessions
     * Retrieve sessions in the requested order. Items keep that order across pages; use cursor.next or cursor.previous to move through the ordered list.
     * <p><b>200</b> - SessionsResponse
     * <p><b>400</b> - InvalidCursorError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The sessionList request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionListWithResponseSpec(SessionListRequest requestParameters) throws WebClientResponseException {
        return this.sessionListWithResponseSpec(requestParameters.limit(), requestParameters.order(), requestParameters.search(), requestParameters.parentID(), requestParameters.directory(), requestParameters.project(), requestParameters.subpath(), requestParameters.cursor());
    }


    /**
     * List sessions
     * Retrieve sessions in the requested order. Items keep that order across pages; use cursor.next or cursor.previous to move through the ordered list.
     * <p><b>200</b> - SessionsResponse
     * <p><b>400</b> - InvalidCursorError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param limit The limit parameter
     * @param order The order parameter
     * @param search The search parameter
     * @param parentID The parentID parameter
     * @param directory The directory parameter
     * @param project The project parameter
     * @param subpath The subpath parameter
     * @param cursor The cursor parameter
     * @return SessionsResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionListRequestCreation(@jakarta.annotation.Nullable String limit, @jakarta.annotation.Nullable String order, @jakarta.annotation.Nullable String search, @jakarta.annotation.Nullable String parentID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String project, @jakarta.annotation.Nullable String subpath, @jakarta.annotation.Nullable String cursor) throws WebClientResponseException {
        Object postBody = null;
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "limit", limit));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "order", order));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "search", search));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "parentID", parentID));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", directory));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "project", project));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "subpath", subpath));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "cursor", cursor));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<SessionsResponse> localVarReturnType = new ParameterizedTypeReference<SessionsResponse>() {};
        return apiClient.invokeAPI("/api/session", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List sessions
     * Retrieve sessions in the requested order. Items keep that order across pages; use cursor.next or cursor.previous to move through the ordered list.
     * <p><b>200</b> - SessionsResponse
     * <p><b>400</b> - InvalidCursorError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param limit The limit parameter
     * @param order The order parameter
     * @param search The search parameter
     * @param parentID The parentID parameter
     * @param directory The directory parameter
     * @param project The project parameter
     * @param subpath The subpath parameter
     * @param cursor The cursor parameter
     * @return SessionsResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionsResponse> sessionList(@jakarta.annotation.Nullable String limit, @jakarta.annotation.Nullable String order, @jakarta.annotation.Nullable String search, @jakarta.annotation.Nullable String parentID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String project, @jakarta.annotation.Nullable String subpath, @jakarta.annotation.Nullable String cursor) throws WebClientResponseException {
        ParameterizedTypeReference<SessionsResponse> localVarReturnType = new ParameterizedTypeReference<SessionsResponse>() {};
        return sessionListRequestCreation(limit, order, search, parentID, directory, project, subpath, cursor).bodyToMono(localVarReturnType);
    }

    /**
     * List sessions
     * Retrieve sessions in the requested order. Items keep that order across pages; use cursor.next or cursor.previous to move through the ordered list.
     * <p><b>200</b> - SessionsResponse
     * <p><b>400</b> - InvalidCursorError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param limit The limit parameter
     * @param order The order parameter
     * @param search The search parameter
     * @param parentID The parentID parameter
     * @param directory The directory parameter
     * @param project The project parameter
     * @param subpath The subpath parameter
     * @param cursor The cursor parameter
     * @return ResponseEntity&lt;SessionsResponse&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionsResponse>> sessionListWithHttpInfo(@jakarta.annotation.Nullable String limit, @jakarta.annotation.Nullable String order, @jakarta.annotation.Nullable String search, @jakarta.annotation.Nullable String parentID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String project, @jakarta.annotation.Nullable String subpath, @jakarta.annotation.Nullable String cursor) throws WebClientResponseException {
        ParameterizedTypeReference<SessionsResponse> localVarReturnType = new ParameterizedTypeReference<SessionsResponse>() {};
        return sessionListRequestCreation(limit, order, search, parentID, directory, project, subpath, cursor).toEntity(localVarReturnType);
    }

    /**
     * List sessions
     * Retrieve sessions in the requested order. Items keep that order across pages; use cursor.next or cursor.previous to move through the ordered list.
     * <p><b>200</b> - SessionsResponse
     * <p><b>400</b> - InvalidCursorError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param limit The limit parameter
     * @param order The order parameter
     * @param search The search parameter
     * @param parentID The parentID parameter
     * @param directory The directory parameter
     * @param project The project parameter
     * @param subpath The subpath parameter
     * @param cursor The cursor parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionListWithResponseSpec(@jakarta.annotation.Nullable String limit, @jakarta.annotation.Nullable String order, @jakarta.annotation.Nullable String search, @jakarta.annotation.Nullable String parentID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String project, @jakarta.annotation.Nullable String subpath, @jakarta.annotation.Nullable String cursor) throws WebClientResponseException {
        return sessionListRequestCreation(limit, order, search, parentID, directory, project, subpath, cursor);
    }

    public class SessionLogRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String after;
        private @jakarta.annotation.Nullable String follow;

        public SessionLogRequest() {}

        public SessionLogRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String after, @jakarta.annotation.Nullable String follow) {
            this.sessionID = sessionID;
            this.after = after;
            this.follow = follow;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionLogRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String after() {
            return this.after;
        }
        public SessionLogRequest after(@jakarta.annotation.Nullable String after) {
            this.after = after;
            return this;
        }

        public @jakarta.annotation.Nullable String follow() {
            return this.follow;
        }
        public SessionLogRequest follow(@jakarta.annotation.Nullable String follow) {
            this.follow = follow;
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
            SessionLogRequest request = (SessionLogRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.after, request.after()) &&
                Objects.equals(this.follow, request.follow());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, after, follow);
        }
    }

    /**
     * Read the session log
     * Experimental durable session event log. Reads events after an exclusive aggregate sequence and continues with live events when follow&#x3D;true.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionLog request parameters as object
     * @return SessionLog200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionLog200Response> sessionLog(SessionLogRequest requestParameters) throws WebClientResponseException {
        return this.sessionLog(requestParameters.sessionID(), requestParameters.after(), requestParameters.follow());
    }

    /**
     * Read the session log
     * Experimental durable session event log. Reads events after an exclusive aggregate sequence and continues with live events when follow&#x3D;true.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionLog request parameters as object
     * @return ResponseEntity&lt;SessionLog200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionLog200Response>> sessionLogWithHttpInfo(SessionLogRequest requestParameters) throws WebClientResponseException {
        return this.sessionLogWithHttpInfo(requestParameters.sessionID(), requestParameters.after(), requestParameters.follow());
    }

    /**
     * Read the session log
     * Experimental durable session event log. Reads events after an exclusive aggregate sequence and continues with live events when follow&#x3D;true.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionLog request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionLogWithResponseSpec(SessionLogRequest requestParameters) throws WebClientResponseException {
        return this.sessionLogWithResponseSpec(requestParameters.sessionID(), requestParameters.after(), requestParameters.follow());
    }


    /**
     * Read the session log
     * Experimental durable session event log. Reads events after an exclusive aggregate sequence and continues with live events when follow&#x3D;true.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param after The after parameter
     * @param follow The follow parameter
     * @return SessionLog200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionLogRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String after, @jakarta.annotation.Nullable String follow) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionLog", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "after", after));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "follow", follow));

        final String[] localVarAccepts = {
            "text/event-stream", "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<SessionLog200Response> localVarReturnType = new ParameterizedTypeReference<SessionLog200Response>() {};
        return apiClient.invokeAPI("/api/experimental/session/{sessionID}/log", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Read the session log
     * Experimental durable session event log. Reads events after an exclusive aggregate sequence and continues with live events when follow&#x3D;true.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param after The after parameter
     * @param follow The follow parameter
     * @return SessionLog200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionLog200Response> sessionLog(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String after, @jakarta.annotation.Nullable String follow) throws WebClientResponseException {
        ParameterizedTypeReference<SessionLog200Response> localVarReturnType = new ParameterizedTypeReference<SessionLog200Response>() {};
        return sessionLogRequestCreation(sessionID, after, follow).bodyToMono(localVarReturnType);
    }

    /**
     * Read the session log
     * Experimental durable session event log. Reads events after an exclusive aggregate sequence and continues with live events when follow&#x3D;true.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param after The after parameter
     * @param follow The follow parameter
     * @return ResponseEntity&lt;SessionLog200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionLog200Response>> sessionLogWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String after, @jakarta.annotation.Nullable String follow) throws WebClientResponseException {
        ParameterizedTypeReference<SessionLog200Response> localVarReturnType = new ParameterizedTypeReference<SessionLog200Response>() {};
        return sessionLogRequestCreation(sessionID, after, follow).toEntity(localVarReturnType);
    }

    /**
     * Read the session log
     * Experimental durable session event log. Reads events after an exclusive aggregate sequence and continues with live events when follow&#x3D;true.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param after The after parameter
     * @param follow The follow parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionLogWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String after, @jakarta.annotation.Nullable String follow) throws WebClientResponseException {
        return sessionLogRequestCreation(sessionID, after, follow);
    }

    public class SessionMessageGetRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String messageID;

        public SessionMessageGetRequest() {}

        public SessionMessageGetRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String messageID) {
            this.sessionID = sessionID;
            this.messageID = messageID;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionMessageGetRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String messageID() {
            return this.messageID;
        }
        public SessionMessageGetRequest messageID(@jakarta.annotation.Nullable String messageID) {
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
            SessionMessageGetRequest request = (SessionMessageGetRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.messageID, request.messageID());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, messageID);
        }
    }

    /**
     * Get session message
     * Retrieve one projected message owned by the Session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | MessageNotFoundError
     * @param requestParameters The sessionMessageGet request parameters as object
     * @return SessionMessageGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionMessageGet200Response> sessionMessageGet(SessionMessageGetRequest requestParameters) throws WebClientResponseException {
        return this.sessionMessageGet(requestParameters.sessionID(), requestParameters.messageID());
    }

    /**
     * Get session message
     * Retrieve one projected message owned by the Session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | MessageNotFoundError
     * @param requestParameters The sessionMessageGet request parameters as object
     * @return ResponseEntity&lt;SessionMessageGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionMessageGet200Response>> sessionMessageGetWithHttpInfo(SessionMessageGetRequest requestParameters) throws WebClientResponseException {
        return this.sessionMessageGetWithHttpInfo(requestParameters.sessionID(), requestParameters.messageID());
    }

    /**
     * Get session message
     * Retrieve one projected message owned by the Session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | MessageNotFoundError
     * @param requestParameters The sessionMessageGet request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionMessageGetWithResponseSpec(SessionMessageGetRequest requestParameters) throws WebClientResponseException {
        return this.sessionMessageGetWithResponseSpec(requestParameters.sessionID(), requestParameters.messageID());
    }


    /**
     * Get session message
     * Retrieve one projected message owned by the Session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | MessageNotFoundError
     * @param sessionID The sessionID parameter
     * @param messageID The messageID parameter
     * @return SessionMessageGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionMessageGetRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String messageID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionMessageGet", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'messageID' is set
        if (messageID == null) {
            throw new WebClientResponseException("Missing the required parameter 'messageID' when calling sessionMessageGet", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);
        pathParams.put("messageID", messageID);

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

        ParameterizedTypeReference<SessionMessageGet200Response> localVarReturnType = new ParameterizedTypeReference<SessionMessageGet200Response>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/message/{messageID}", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get session message
     * Retrieve one projected message owned by the Session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | MessageNotFoundError
     * @param sessionID The sessionID parameter
     * @param messageID The messageID parameter
     * @return SessionMessageGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionMessageGet200Response> sessionMessageGet(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String messageID) throws WebClientResponseException {
        ParameterizedTypeReference<SessionMessageGet200Response> localVarReturnType = new ParameterizedTypeReference<SessionMessageGet200Response>() {};
        return sessionMessageGetRequestCreation(sessionID, messageID).bodyToMono(localVarReturnType);
    }

    /**
     * Get session message
     * Retrieve one projected message owned by the Session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | MessageNotFoundError
     * @param sessionID The sessionID parameter
     * @param messageID The messageID parameter
     * @return ResponseEntity&lt;SessionMessageGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionMessageGet200Response>> sessionMessageGetWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String messageID) throws WebClientResponseException {
        ParameterizedTypeReference<SessionMessageGet200Response> localVarReturnType = new ParameterizedTypeReference<SessionMessageGet200Response>() {};
        return sessionMessageGetRequestCreation(sessionID, messageID).toEntity(localVarReturnType);
    }

    /**
     * Get session message
     * Retrieve one projected message owned by the Session.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError | MessageNotFoundError
     * @param sessionID The sessionID parameter
     * @param messageID The messageID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionMessageGetWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String messageID) throws WebClientResponseException {
        return sessionMessageGetRequestCreation(sessionID, messageID);
    }

    public class SessionMessageListRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String limit;
        private @jakarta.annotation.Nullable String order;
        private @jakarta.annotation.Nullable String cursor;
        private @jakarta.annotation.Nullable String type;

        public SessionMessageListRequest() {}

        public SessionMessageListRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String limit, @jakarta.annotation.Nullable String order, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String type) {
            this.sessionID = sessionID;
            this.limit = limit;
            this.order = order;
            this.cursor = cursor;
            this.type = type;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionMessageListRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String limit() {
            return this.limit;
        }
        public SessionMessageListRequest limit(@jakarta.annotation.Nullable String limit) {
            this.limit = limit;
            return this;
        }

        public @jakarta.annotation.Nullable String order() {
            return this.order;
        }
        public SessionMessageListRequest order(@jakarta.annotation.Nullable String order) {
            this.order = order;
            return this;
        }

        public @jakarta.annotation.Nullable String cursor() {
            return this.cursor;
        }
        public SessionMessageListRequest cursor(@jakarta.annotation.Nullable String cursor) {
            this.cursor = cursor;
            return this;
        }

        public @jakarta.annotation.Nullable String type() {
            return this.type;
        }
        public SessionMessageListRequest type(@jakarta.annotation.Nullable String type) {
            this.type = type;
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
            SessionMessageListRequest request = (SessionMessageListRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.limit, request.limit()) &&
                Objects.equals(this.order, request.order()) &&
                Objects.equals(this.cursor, request.cursor()) &&
                Objects.equals(this.type, request.type());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, limit, order, cursor, type);
        }
    }

    /**
     * Get session messages
     * Retrieve projected messages for a session, optionally filtered by type. Items keep the requested order across pages; use cursor.next or cursor.previous to move through the ordered timeline, passing the same type filter on each page.
     * <p><b>200</b> - SessionMessagesResponse
     * <p><b>400</b> - InvalidCursorError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param requestParameters The sessionMessageList request parameters as object
     * @return SessionMessagesResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionMessagesResponse> sessionMessageList(SessionMessageListRequest requestParameters) throws WebClientResponseException {
        return this.sessionMessageList(requestParameters.sessionID(), requestParameters.limit(), requestParameters.order(), requestParameters.cursor(), requestParameters.type());
    }

    /**
     * Get session messages
     * Retrieve projected messages for a session, optionally filtered by type. Items keep the requested order across pages; use cursor.next or cursor.previous to move through the ordered timeline, passing the same type filter on each page.
     * <p><b>200</b> - SessionMessagesResponse
     * <p><b>400</b> - InvalidCursorError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param requestParameters The sessionMessageList request parameters as object
     * @return ResponseEntity&lt;SessionMessagesResponse&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionMessagesResponse>> sessionMessageListWithHttpInfo(SessionMessageListRequest requestParameters) throws WebClientResponseException {
        return this.sessionMessageListWithHttpInfo(requestParameters.sessionID(), requestParameters.limit(), requestParameters.order(), requestParameters.cursor(), requestParameters.type());
    }

    /**
     * Get session messages
     * Retrieve projected messages for a session, optionally filtered by type. Items keep the requested order across pages; use cursor.next or cursor.previous to move through the ordered timeline, passing the same type filter on each page.
     * <p><b>200</b> - SessionMessagesResponse
     * <p><b>400</b> - InvalidCursorError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param requestParameters The sessionMessageList request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionMessageListWithResponseSpec(SessionMessageListRequest requestParameters) throws WebClientResponseException {
        return this.sessionMessageListWithResponseSpec(requestParameters.sessionID(), requestParameters.limit(), requestParameters.order(), requestParameters.cursor(), requestParameters.type());
    }


    /**
     * Get session messages
     * Retrieve projected messages for a session, optionally filtered by type. Items keep the requested order across pages; use cursor.next or cursor.previous to move through the ordered timeline, passing the same type filter on each page.
     * <p><b>200</b> - SessionMessagesResponse
     * <p><b>400</b> - InvalidCursorError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @param limit The limit parameter
     * @param order The order parameter
     * @param cursor The cursor parameter
     * @param type The type parameter
     * @return SessionMessagesResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionMessageListRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String limit, @jakarta.annotation.Nullable String order, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String type) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionMessageList", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("sessionID", sessionID);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "limit", limit));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "order", order));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "cursor", cursor));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "type", type));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<SessionMessagesResponse> localVarReturnType = new ParameterizedTypeReference<SessionMessagesResponse>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/message", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get session messages
     * Retrieve projected messages for a session, optionally filtered by type. Items keep the requested order across pages; use cursor.next or cursor.previous to move through the ordered timeline, passing the same type filter on each page.
     * <p><b>200</b> - SessionMessagesResponse
     * <p><b>400</b> - InvalidCursorError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @param limit The limit parameter
     * @param order The order parameter
     * @param cursor The cursor parameter
     * @param type The type parameter
     * @return SessionMessagesResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionMessagesResponse> sessionMessageList(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String limit, @jakarta.annotation.Nullable String order, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String type) throws WebClientResponseException {
        ParameterizedTypeReference<SessionMessagesResponse> localVarReturnType = new ParameterizedTypeReference<SessionMessagesResponse>() {};
        return sessionMessageListRequestCreation(sessionID, limit, order, cursor, type).bodyToMono(localVarReturnType);
    }

    /**
     * Get session messages
     * Retrieve projected messages for a session, optionally filtered by type. Items keep the requested order across pages; use cursor.next or cursor.previous to move through the ordered timeline, passing the same type filter on each page.
     * <p><b>200</b> - SessionMessagesResponse
     * <p><b>400</b> - InvalidCursorError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @param limit The limit parameter
     * @param order The order parameter
     * @param cursor The cursor parameter
     * @param type The type parameter
     * @return ResponseEntity&lt;SessionMessagesResponse&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionMessagesResponse>> sessionMessageListWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String limit, @jakarta.annotation.Nullable String order, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String type) throws WebClientResponseException {
        ParameterizedTypeReference<SessionMessagesResponse> localVarReturnType = new ParameterizedTypeReference<SessionMessagesResponse>() {};
        return sessionMessageListRequestCreation(sessionID, limit, order, cursor, type).toEntity(localVarReturnType);
    }

    /**
     * Get session messages
     * Retrieve projected messages for a session, optionally filtered by type. Items keep the requested order across pages; use cursor.next or cursor.previous to move through the ordered timeline, passing the same type filter on each page.
     * <p><b>200</b> - SessionMessagesResponse
     * <p><b>400</b> - InvalidCursorError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @param limit The limit parameter
     * @param order The order parameter
     * @param cursor The cursor parameter
     * @param type The type parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionMessageListWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String limit, @jakarta.annotation.Nullable String order, @jakarta.annotation.Nullable String cursor, @jakarta.annotation.Nullable String type) throws WebClientResponseException {
        return sessionMessageListRequestCreation(sessionID, limit, order, cursor, type);
    }

    public class SessionMoveRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull SessionMoveRequest sessionMoveRequest;

        public SessionMoveRequest() {}

        public SessionMoveRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionMoveRequest sessionMoveRequest) {
            this.sessionID = sessionID;
            this.sessionMoveRequest = sessionMoveRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionMoveRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull SessionMoveRequest sessionMoveRequest() {
            return this.sessionMoveRequest;
        }
        public SessionMoveRequest sessionMoveRequest(@jakarta.annotation.Nonnull SessionMoveRequest sessionMoveRequest) {
            this.sessionMoveRequest = sessionMoveRequest;
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
            SessionMoveRequest request = (SessionMoveRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.sessionMoveRequest, request.sessionMoveRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, sessionMoveRequest);
        }
    }

    /**
     * Move session
     * Move a session to another project directory at the requested delivery boundary.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionMove request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionMove(SessionMoveRequest requestParameters) throws WebClientResponseException {
        return this.sessionMove(requestParameters.sessionID(), requestParameters.sessionMoveRequest());
    }

    /**
     * Move session
     * Move a session to another project directory at the requested delivery boundary.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionMove request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionMoveWithHttpInfo(SessionMoveRequest requestParameters) throws WebClientResponseException {
        return this.sessionMoveWithHttpInfo(requestParameters.sessionID(), requestParameters.sessionMoveRequest());
    }

    /**
     * Move session
     * Move a session to another project directory at the requested delivery boundary.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionMove request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionMoveWithResponseSpec(SessionMoveRequest requestParameters) throws WebClientResponseException {
        return this.sessionMoveWithResponseSpec(requestParameters.sessionID(), requestParameters.sessionMoveRequest());
    }


    /**
     * Move session
     * Move a session to another project directory at the requested delivery boundary.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionMoveRequest The sessionMoveRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionMoveRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionMoveRequest sessionMoveRequest) throws WebClientResponseException {
        Object postBody = sessionMoveRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionMove", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'sessionMoveRequest' is set
        if (sessionMoveRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionMoveRequest' when calling sessionMove", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/move", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Move session
     * Move a session to another project directory at the requested delivery boundary.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionMoveRequest The sessionMoveRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionMove(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionMoveRequest sessionMoveRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionMoveRequestCreation(sessionID, sessionMoveRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Move session
     * Move a session to another project directory at the requested delivery boundary.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionMoveRequest The sessionMoveRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionMoveWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionMoveRequest sessionMoveRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionMoveRequestCreation(sessionID, sessionMoveRequest).toEntity(localVarReturnType);
    }

    /**
     * Move session
     * Move a session to another project directory at the requested delivery boundary.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionMoveRequest The sessionMoveRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionMoveWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionMoveRequest sessionMoveRequest) throws WebClientResponseException {
        return sessionMoveRequestCreation(sessionID, sessionMoveRequest);
    }

    public class SessionPromptRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull SessionPromptRequest sessionPromptRequest;

        public SessionPromptRequest() {}

        public SessionPromptRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionPromptRequest sessionPromptRequest) {
            this.sessionID = sessionID;
            this.sessionPromptRequest = sessionPromptRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionPromptRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull SessionPromptRequest sessionPromptRequest() {
            return this.sessionPromptRequest;
        }
        public SessionPromptRequest sessionPromptRequest(@jakarta.annotation.Nonnull SessionPromptRequest sessionPromptRequest) {
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
                Objects.equals(this.sessionPromptRequest, request.sessionPromptRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, sessionPromptRequest);
        }
    }

    /**
     * Send message
     * Durably admit one session input and schedule agent-loop execution unless resume is false.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param requestParameters The sessionPrompt request parameters as object
     * @return SessionPrompt200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionPrompt200Response> sessionPrompt(SessionPromptRequest requestParameters) throws WebClientResponseException {
        return this.sessionPrompt(requestParameters.sessionID(), requestParameters.sessionPromptRequest());
    }

    /**
     * Send message
     * Durably admit one session input and schedule agent-loop execution unless resume is false.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param requestParameters The sessionPrompt request parameters as object
     * @return ResponseEntity&lt;SessionPrompt200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionPrompt200Response>> sessionPromptWithHttpInfo(SessionPromptRequest requestParameters) throws WebClientResponseException {
        return this.sessionPromptWithHttpInfo(requestParameters.sessionID(), requestParameters.sessionPromptRequest());
    }

    /**
     * Send message
     * Durably admit one session input and schedule agent-loop execution unless resume is false.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param requestParameters The sessionPrompt request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionPromptWithResponseSpec(SessionPromptRequest requestParameters) throws WebClientResponseException {
        return this.sessionPromptWithResponseSpec(requestParameters.sessionID(), requestParameters.sessionPromptRequest());
    }


    /**
     * Send message
     * Durably admit one session input and schedule agent-loop execution unless resume is false.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param sessionID The sessionID parameter
     * @param sessionPromptRequest The sessionPromptRequest parameter
     * @return SessionPrompt200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionPromptRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionPromptRequest sessionPromptRequest) throws WebClientResponseException {
        Object postBody = sessionPromptRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionPrompt", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'sessionPromptRequest' is set
        if (sessionPromptRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionPromptRequest' when calling sessionPrompt", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<SessionPrompt200Response> localVarReturnType = new ParameterizedTypeReference<SessionPrompt200Response>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/prompt", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Send message
     * Durably admit one session input and schedule agent-loop execution unless resume is false.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param sessionID The sessionID parameter
     * @param sessionPromptRequest The sessionPromptRequest parameter
     * @return SessionPrompt200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionPrompt200Response> sessionPrompt(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionPromptRequest sessionPromptRequest) throws WebClientResponseException {
        ParameterizedTypeReference<SessionPrompt200Response> localVarReturnType = new ParameterizedTypeReference<SessionPrompt200Response>() {};
        return sessionPromptRequestCreation(sessionID, sessionPromptRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Send message
     * Durably admit one session input and schedule agent-loop execution unless resume is false.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param sessionID The sessionID parameter
     * @param sessionPromptRequest The sessionPromptRequest parameter
     * @return ResponseEntity&lt;SessionPrompt200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionPrompt200Response>> sessionPromptWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionPromptRequest sessionPromptRequest) throws WebClientResponseException {
        ParameterizedTypeReference<SessionPrompt200Response> localVarReturnType = new ParameterizedTypeReference<SessionPrompt200Response>() {};
        return sessionPromptRequestCreation(sessionID, sessionPromptRequest).toEntity(localVarReturnType);
    }

    /**
     * Send message
     * Durably admit one session input and schedule agent-loop execution unless resume is false.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param sessionID The sessionID parameter
     * @param sessionPromptRequest The sessionPromptRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionPromptWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionPromptRequest sessionPromptRequest) throws WebClientResponseException {
        return sessionPromptRequestCreation(sessionID, sessionPromptRequest);
    }

    /**
     * Delete session
     * Delete a session and its child sessions.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionRemoveRequestCreation(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionRemove", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}", HttpMethod.DELETE, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Delete session
     * Delete a session and its child sessions.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionRemove(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionRemoveRequestCreation(sessionID).bodyToMono(localVarReturnType);
    }

    /**
     * Delete session
     * Delete a session and its child sessions.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionRemoveWithHttpInfo(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionRemoveRequestCreation(sessionID).toEntity(localVarReturnType);
    }

    /**
     * Delete session
     * Delete a session and its child sessions.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionRemoveWithResponseSpec(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        return sessionRemoveRequestCreation(sessionID);
    }

    /**
     * Clear staged revert
     *
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - SessionBusyError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionRevertClearRequestCreation(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionRevertClear", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/revert", HttpMethod.DELETE, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Clear staged revert
     *
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - SessionBusyError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionRevertClear(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionRevertClearRequestCreation(sessionID).bodyToMono(localVarReturnType);
    }

    /**
     * Clear staged revert
     *
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - SessionBusyError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionRevertClearWithHttpInfo(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionRevertClearRequestCreation(sessionID).toEntity(localVarReturnType);
    }

    /**
     * Clear staged revert
     *
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - SessionBusyError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionRevertClearWithResponseSpec(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        return sessionRevertClearRequestCreation(sessionID);
    }

    /**
     * Commit staged revert
     *
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionRevertCommitRequestCreation(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionRevertCommit", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/revert/commit", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Commit staged revert
     *
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionRevertCommit(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionRevertCommitRequestCreation(sessionID).bodyToMono(localVarReturnType);
    }

    /**
     * Commit staged revert
     *
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param sessionID The sessionID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionRevertCommitWithHttpInfo(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionRevertCommitRequestCreation(sessionID).toEntity(localVarReturnType);
    }

    /**
     * Commit staged revert
     *
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - SessionBusyError
     * @param sessionID The sessionID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionRevertCommitWithResponseSpec(@jakarta.annotation.Nonnull String sessionID) throws WebClientResponseException {
        return sessionRevertCommitRequestCreation(sessionID);
    }

    public class SessionRevertStageRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull SessionRevertStageRequest sessionRevertStageRequest;

        public SessionRevertStageRequest() {}

        public SessionRevertStageRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionRevertStageRequest sessionRevertStageRequest) {
            this.sessionID = sessionID;
            this.sessionRevertStageRequest = sessionRevertStageRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionRevertStageRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull SessionRevertStageRequest sessionRevertStageRequest() {
            return this.sessionRevertStageRequest;
        }
        public SessionRevertStageRequest sessionRevertStageRequest(@jakarta.annotation.Nonnull SessionRevertStageRequest sessionRevertStageRequest) {
            this.sessionRevertStageRequest = sessionRevertStageRequest;
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
            SessionRevertStageRequest request = (SessionRevertStageRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.sessionRevertStageRequest, request.sessionRevertStageRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, sessionRevertStageRequest);
        }
    }

    /**
     * Stage session revert
     * Stage or move a reversible session boundary and optionally apply its file changes.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - MessageNotFoundError | SessionNotFoundError
     * <p><b>409</b> - SessionBusyError
     * <p><b>500</b> - UnknownError
     * @param requestParameters The sessionRevertStage request parameters as object
     * @return SessionRevertStage200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionRevertStage200Response> sessionRevertStage(SessionRevertStageRequest requestParameters) throws WebClientResponseException {
        return this.sessionRevertStage(requestParameters.sessionID(), requestParameters.sessionRevertStageRequest());
    }

    /**
     * Stage session revert
     * Stage or move a reversible session boundary and optionally apply its file changes.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - MessageNotFoundError | SessionNotFoundError
     * <p><b>409</b> - SessionBusyError
     * <p><b>500</b> - UnknownError
     * @param requestParameters The sessionRevertStage request parameters as object
     * @return ResponseEntity&lt;SessionRevertStage200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionRevertStage200Response>> sessionRevertStageWithHttpInfo(SessionRevertStageRequest requestParameters) throws WebClientResponseException {
        return this.sessionRevertStageWithHttpInfo(requestParameters.sessionID(), requestParameters.sessionRevertStageRequest());
    }

    /**
     * Stage session revert
     * Stage or move a reversible session boundary and optionally apply its file changes.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - MessageNotFoundError | SessionNotFoundError
     * <p><b>409</b> - SessionBusyError
     * <p><b>500</b> - UnknownError
     * @param requestParameters The sessionRevertStage request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionRevertStageWithResponseSpec(SessionRevertStageRequest requestParameters) throws WebClientResponseException {
        return this.sessionRevertStageWithResponseSpec(requestParameters.sessionID(), requestParameters.sessionRevertStageRequest());
    }


    /**
     * Stage session revert
     * Stage or move a reversible session boundary and optionally apply its file changes.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - MessageNotFoundError | SessionNotFoundError
     * <p><b>409</b> - SessionBusyError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @param sessionRevertStageRequest The sessionRevertStageRequest parameter
     * @return SessionRevertStage200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionRevertStageRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionRevertStageRequest sessionRevertStageRequest) throws WebClientResponseException {
        Object postBody = sessionRevertStageRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionRevertStage", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'sessionRevertStageRequest' is set
        if (sessionRevertStageRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionRevertStageRequest' when calling sessionRevertStage", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<SessionRevertStage200Response> localVarReturnType = new ParameterizedTypeReference<SessionRevertStage200Response>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/revert/stage", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Stage session revert
     * Stage or move a reversible session boundary and optionally apply its file changes.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - MessageNotFoundError | SessionNotFoundError
     * <p><b>409</b> - SessionBusyError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @param sessionRevertStageRequest The sessionRevertStageRequest parameter
     * @return SessionRevertStage200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionRevertStage200Response> sessionRevertStage(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionRevertStageRequest sessionRevertStageRequest) throws WebClientResponseException {
        ParameterizedTypeReference<SessionRevertStage200Response> localVarReturnType = new ParameterizedTypeReference<SessionRevertStage200Response>() {};
        return sessionRevertStageRequestCreation(sessionID, sessionRevertStageRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Stage session revert
     * Stage or move a reversible session boundary and optionally apply its file changes.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - MessageNotFoundError | SessionNotFoundError
     * <p><b>409</b> - SessionBusyError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @param sessionRevertStageRequest The sessionRevertStageRequest parameter
     * @return ResponseEntity&lt;SessionRevertStage200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionRevertStage200Response>> sessionRevertStageWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionRevertStageRequest sessionRevertStageRequest) throws WebClientResponseException {
        ParameterizedTypeReference<SessionRevertStage200Response> localVarReturnType = new ParameterizedTypeReference<SessionRevertStage200Response>() {};
        return sessionRevertStageRequestCreation(sessionID, sessionRevertStageRequest).toEntity(localVarReturnType);
    }

    /**
     * Stage session revert
     * Stage or move a reversible session boundary and optionally apply its file changes.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - MessageNotFoundError | SessionNotFoundError
     * <p><b>409</b> - SessionBusyError
     * <p><b>500</b> - UnknownError
     * @param sessionID The sessionID parameter
     * @param sessionRevertStageRequest The sessionRevertStageRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionRevertStageWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionRevertStageRequest sessionRevertStageRequest) throws WebClientResponseException {
        return sessionRevertStageRequestCreation(sessionID, sessionRevertStageRequest);
    }

    public class SessionShellRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull SessionShellRequest sessionShellRequest;

        public SessionShellRequest() {}

        public SessionShellRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionShellRequest sessionShellRequest) {
            this.sessionID = sessionID;
            this.sessionShellRequest = sessionShellRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionShellRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull SessionShellRequest sessionShellRequest() {
            return this.sessionShellRequest;
        }
        public SessionShellRequest sessionShellRequest(@jakarta.annotation.Nonnull SessionShellRequest sessionShellRequest) {
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
                Objects.equals(this.sessionShellRequest, request.sessionShellRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, sessionShellRequest);
        }
    }

    /**
     * Run shell command
     * Execute one shell command in the session&#39;s working directory. Emits a shell.started event before execution and a shell.ended event with the merged output after.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionShell request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionShell(SessionShellRequest requestParameters) throws WebClientResponseException {
        return this.sessionShell(requestParameters.sessionID(), requestParameters.sessionShellRequest());
    }

    /**
     * Run shell command
     * Execute one shell command in the session&#39;s working directory. Emits a shell.started event before execution and a shell.ended event with the merged output after.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionShell request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionShellWithHttpInfo(SessionShellRequest requestParameters) throws WebClientResponseException {
        return this.sessionShellWithHttpInfo(requestParameters.sessionID(), requestParameters.sessionShellRequest());
    }

    /**
     * Run shell command
     * Execute one shell command in the session&#39;s working directory. Emits a shell.started event before execution and a shell.ended event with the merged output after.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionShell request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionShellWithResponseSpec(SessionShellRequest requestParameters) throws WebClientResponseException {
        return this.sessionShellWithResponseSpec(requestParameters.sessionID(), requestParameters.sessionShellRequest());
    }


    /**
     * Run shell command
     * Execute one shell command in the session&#39;s working directory. Emits a shell.started event before execution and a shell.ended event with the merged output after.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionShellRequest The sessionShellRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionShellRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionShellRequest sessionShellRequest) throws WebClientResponseException {
        Object postBody = sessionShellRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionShell", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'sessionShellRequest' is set
        if (sessionShellRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionShellRequest' when calling sessionShell", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/shell", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Run shell command
     * Execute one shell command in the session&#39;s working directory. Emits a shell.started event before execution and a shell.ended event with the merged output after.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionShellRequest The sessionShellRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionShell(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionShellRequest sessionShellRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionShellRequestCreation(sessionID, sessionShellRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Run shell command
     * Execute one shell command in the session&#39;s working directory. Emits a shell.started event before execution and a shell.ended event with the merged output after.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionShellRequest The sessionShellRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionShellWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionShellRequest sessionShellRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionShellRequestCreation(sessionID, sessionShellRequest).toEntity(localVarReturnType);
    }

    /**
     * Run shell command
     * Execute one shell command in the session&#39;s working directory. Emits a shell.started event before execution and a shell.ended event with the merged output after.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionShellRequest The sessionShellRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionShellWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionShellRequest sessionShellRequest) throws WebClientResponseException {
        return sessionShellRequestCreation(sessionID, sessionShellRequest);
    }

    public class SessionSwitchAgentRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull SessionSwitchAgentRequest sessionSwitchAgentRequest;

        public SessionSwitchAgentRequest() {}

        public SessionSwitchAgentRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionSwitchAgentRequest sessionSwitchAgentRequest) {
            this.sessionID = sessionID;
            this.sessionSwitchAgentRequest = sessionSwitchAgentRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionSwitchAgentRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull SessionSwitchAgentRequest sessionSwitchAgentRequest() {
            return this.sessionSwitchAgentRequest;
        }
        public SessionSwitchAgentRequest sessionSwitchAgentRequest(@jakarta.annotation.Nonnull SessionSwitchAgentRequest sessionSwitchAgentRequest) {
            this.sessionSwitchAgentRequest = sessionSwitchAgentRequest;
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
            SessionSwitchAgentRequest request = (SessionSwitchAgentRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.sessionSwitchAgentRequest, request.sessionSwitchAgentRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, sessionSwitchAgentRequest);
        }
    }

    /**
     * Switch session agent
     * Switch the agent used by subsequent provider turns.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionSwitchAgent request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionSwitchAgent(SessionSwitchAgentRequest requestParameters) throws WebClientResponseException {
        return this.sessionSwitchAgent(requestParameters.sessionID(), requestParameters.sessionSwitchAgentRequest());
    }

    /**
     * Switch session agent
     * Switch the agent used by subsequent provider turns.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionSwitchAgent request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionSwitchAgentWithHttpInfo(SessionSwitchAgentRequest requestParameters) throws WebClientResponseException {
        return this.sessionSwitchAgentWithHttpInfo(requestParameters.sessionID(), requestParameters.sessionSwitchAgentRequest());
    }

    /**
     * Switch session agent
     * Switch the agent used by subsequent provider turns.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionSwitchAgent request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionSwitchAgentWithResponseSpec(SessionSwitchAgentRequest requestParameters) throws WebClientResponseException {
        return this.sessionSwitchAgentWithResponseSpec(requestParameters.sessionID(), requestParameters.sessionSwitchAgentRequest());
    }


    /**
     * Switch session agent
     * Switch the agent used by subsequent provider turns.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionSwitchAgentRequest The sessionSwitchAgentRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionSwitchAgentRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionSwitchAgentRequest sessionSwitchAgentRequest) throws WebClientResponseException {
        Object postBody = sessionSwitchAgentRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionSwitchAgent", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'sessionSwitchAgentRequest' is set
        if (sessionSwitchAgentRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionSwitchAgentRequest' when calling sessionSwitchAgent", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/agent", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Switch session agent
     * Switch the agent used by subsequent provider turns.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionSwitchAgentRequest The sessionSwitchAgentRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionSwitchAgent(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionSwitchAgentRequest sessionSwitchAgentRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionSwitchAgentRequestCreation(sessionID, sessionSwitchAgentRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Switch session agent
     * Switch the agent used by subsequent provider turns.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionSwitchAgentRequest The sessionSwitchAgentRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionSwitchAgentWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionSwitchAgentRequest sessionSwitchAgentRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionSwitchAgentRequestCreation(sessionID, sessionSwitchAgentRequest).toEntity(localVarReturnType);
    }

    /**
     * Switch session agent
     * Switch the agent used by subsequent provider turns.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionSwitchAgentRequest The sessionSwitchAgentRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionSwitchAgentWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionSwitchAgentRequest sessionSwitchAgentRequest) throws WebClientResponseException {
        return sessionSwitchAgentRequestCreation(sessionID, sessionSwitchAgentRequest);
    }

    public class SessionSwitchModelRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull SessionSwitchModelRequest sessionSwitchModelRequest;

        public SessionSwitchModelRequest() {}

        public SessionSwitchModelRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionSwitchModelRequest sessionSwitchModelRequest) {
            this.sessionID = sessionID;
            this.sessionSwitchModelRequest = sessionSwitchModelRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionSwitchModelRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull SessionSwitchModelRequest sessionSwitchModelRequest() {
            return this.sessionSwitchModelRequest;
        }
        public SessionSwitchModelRequest sessionSwitchModelRequest(@jakarta.annotation.Nonnull SessionSwitchModelRequest sessionSwitchModelRequest) {
            this.sessionSwitchModelRequest = sessionSwitchModelRequest;
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
            SessionSwitchModelRequest request = (SessionSwitchModelRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.sessionSwitchModelRequest, request.sessionSwitchModelRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, sessionSwitchModelRequest);
        }
    }

    /**
     * Switch session model
     * Switch the model used by subsequent provider turns.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionSwitchModel request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionSwitchModel(SessionSwitchModelRequest requestParameters) throws WebClientResponseException {
        return this.sessionSwitchModel(requestParameters.sessionID(), requestParameters.sessionSwitchModelRequest());
    }

    /**
     * Switch session model
     * Switch the model used by subsequent provider turns.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionSwitchModel request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionSwitchModelWithHttpInfo(SessionSwitchModelRequest requestParameters) throws WebClientResponseException {
        return this.sessionSwitchModelWithHttpInfo(requestParameters.sessionID(), requestParameters.sessionSwitchModelRequest());
    }

    /**
     * Switch session model
     * Switch the model used by subsequent provider turns.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionSwitchModel request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionSwitchModelWithResponseSpec(SessionSwitchModelRequest requestParameters) throws WebClientResponseException {
        return this.sessionSwitchModelWithResponseSpec(requestParameters.sessionID(), requestParameters.sessionSwitchModelRequest());
    }


    /**
     * Switch session model
     * Switch the model used by subsequent provider turns.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionSwitchModelRequest The sessionSwitchModelRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionSwitchModelRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionSwitchModelRequest sessionSwitchModelRequest) throws WebClientResponseException {
        Object postBody = sessionSwitchModelRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionSwitchModel", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'sessionSwitchModelRequest' is set
        if (sessionSwitchModelRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionSwitchModelRequest' when calling sessionSwitchModel", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/model", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Switch session model
     * Switch the model used by subsequent provider turns.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionSwitchModelRequest The sessionSwitchModelRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionSwitchModel(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionSwitchModelRequest sessionSwitchModelRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionSwitchModelRequestCreation(sessionID, sessionSwitchModelRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Switch session model
     * Switch the model used by subsequent provider turns.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionSwitchModelRequest The sessionSwitchModelRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionSwitchModelWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionSwitchModelRequest sessionSwitchModelRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionSwitchModelRequestCreation(sessionID, sessionSwitchModelRequest).toEntity(localVarReturnType);
    }

    /**
     * Switch session model
     * Switch the model used by subsequent provider turns.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionSwitchModelRequest The sessionSwitchModelRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionSwitchModelWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionSwitchModelRequest sessionSwitchModelRequest) throws WebClientResponseException {
        return sessionSwitchModelRequestCreation(sessionID, sessionSwitchModelRequest);
    }

    public class SessionSyntheticRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull SessionSyntheticRequest sessionSyntheticRequest;

        public SessionSyntheticRequest() {}

        public SessionSyntheticRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionSyntheticRequest sessionSyntheticRequest) {
            this.sessionID = sessionID;
            this.sessionSyntheticRequest = sessionSyntheticRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionSyntheticRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull SessionSyntheticRequest sessionSyntheticRequest() {
            return this.sessionSyntheticRequest;
        }
        public SessionSyntheticRequest sessionSyntheticRequest(@jakarta.annotation.Nonnull SessionSyntheticRequest sessionSyntheticRequest) {
            this.sessionSyntheticRequest = sessionSyntheticRequest;
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
            SessionSyntheticRequest request = (SessionSyntheticRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.sessionSyntheticRequest, request.sessionSyntheticRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, sessionSyntheticRequest);
        }
    }

    /**
     * Add synthetic message
     * Durably admit synthetic session input and schedule execution unless resume is false.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param requestParameters The sessionSynthetic request parameters as object
     * @return SessionSynthetic200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionSynthetic200Response> sessionSynthetic(SessionSyntheticRequest requestParameters) throws WebClientResponseException {
        return this.sessionSynthetic(requestParameters.sessionID(), requestParameters.sessionSyntheticRequest());
    }

    /**
     * Add synthetic message
     * Durably admit synthetic session input and schedule execution unless resume is false.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param requestParameters The sessionSynthetic request parameters as object
     * @return ResponseEntity&lt;SessionSynthetic200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionSynthetic200Response>> sessionSyntheticWithHttpInfo(SessionSyntheticRequest requestParameters) throws WebClientResponseException {
        return this.sessionSyntheticWithHttpInfo(requestParameters.sessionID(), requestParameters.sessionSyntheticRequest());
    }

    /**
     * Add synthetic message
     * Durably admit synthetic session input and schedule execution unless resume is false.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param requestParameters The sessionSynthetic request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionSyntheticWithResponseSpec(SessionSyntheticRequest requestParameters) throws WebClientResponseException {
        return this.sessionSyntheticWithResponseSpec(requestParameters.sessionID(), requestParameters.sessionSyntheticRequest());
    }


    /**
     * Add synthetic message
     * Durably admit synthetic session input and schedule execution unless resume is false.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param sessionID The sessionID parameter
     * @param sessionSyntheticRequest The sessionSyntheticRequest parameter
     * @return SessionSynthetic200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionSyntheticRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionSyntheticRequest sessionSyntheticRequest) throws WebClientResponseException {
        Object postBody = sessionSyntheticRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionSynthetic", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'sessionSyntheticRequest' is set
        if (sessionSyntheticRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionSyntheticRequest' when calling sessionSynthetic", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<SessionSynthetic200Response> localVarReturnType = new ParameterizedTypeReference<SessionSynthetic200Response>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/synthetic", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Add synthetic message
     * Durably admit synthetic session input and schedule execution unless resume is false.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param sessionID The sessionID parameter
     * @param sessionSyntheticRequest The sessionSyntheticRequest parameter
     * @return SessionSynthetic200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SessionSynthetic200Response> sessionSynthetic(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionSyntheticRequest sessionSyntheticRequest) throws WebClientResponseException {
        ParameterizedTypeReference<SessionSynthetic200Response> localVarReturnType = new ParameterizedTypeReference<SessionSynthetic200Response>() {};
        return sessionSyntheticRequestCreation(sessionID, sessionSyntheticRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Add synthetic message
     * Durably admit synthetic session input and schedule execution unless resume is false.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param sessionID The sessionID parameter
     * @param sessionSyntheticRequest The sessionSyntheticRequest parameter
     * @return ResponseEntity&lt;SessionSynthetic200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SessionSynthetic200Response>> sessionSyntheticWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionSyntheticRequest sessionSyntheticRequest) throws WebClientResponseException {
        ParameterizedTypeReference<SessionSynthetic200Response> localVarReturnType = new ParameterizedTypeReference<SessionSynthetic200Response>() {};
        return sessionSyntheticRequestCreation(sessionID, sessionSyntheticRequest).toEntity(localVarReturnType);
    }

    /**
     * Add synthetic message
     * Durably admit synthetic session input and schedule execution unless resume is false.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * <p><b>409</b> - ConflictError
     * @param sessionID The sessionID parameter
     * @param sessionSyntheticRequest The sessionSyntheticRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionSyntheticWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionSyntheticRequest sessionSyntheticRequest) throws WebClientResponseException {
        return sessionSyntheticRequestCreation(sessionID, sessionSyntheticRequest);
    }

    public class SessionUpdateRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull SessionUpdateRequest sessionUpdateRequest;

        public SessionUpdateRequest() {}

        public SessionUpdateRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionUpdateRequest sessionUpdateRequest) {
            this.sessionID = sessionID;
            this.sessionUpdateRequest = sessionUpdateRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionUpdateRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull SessionUpdateRequest sessionUpdateRequest() {
            return this.sessionUpdateRequest;
        }
        public SessionUpdateRequest sessionUpdateRequest(@jakarta.annotation.Nonnull SessionUpdateRequest sessionUpdateRequest) {
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
                Objects.equals(this.sessionUpdateRequest, request.sessionUpdateRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, sessionUpdateRequest);
        }
    }

    /**
     * Update session
     * Update mutable session properties.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionUpdate request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionUpdate(SessionUpdateRequest requestParameters) throws WebClientResponseException {
        return this.sessionUpdate(requestParameters.sessionID(), requestParameters.sessionUpdateRequest());
    }

    /**
     * Update session
     * Update mutable session properties.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionUpdate request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionUpdateWithHttpInfo(SessionUpdateRequest requestParameters) throws WebClientResponseException {
        return this.sessionUpdateWithHttpInfo(requestParameters.sessionID(), requestParameters.sessionUpdateRequest());
    }

    /**
     * Update session
     * Update mutable session properties.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionUpdate request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionUpdateWithResponseSpec(SessionUpdateRequest requestParameters) throws WebClientResponseException {
        return this.sessionUpdateWithResponseSpec(requestParameters.sessionID(), requestParameters.sessionUpdateRequest());
    }


    /**
     * Update session
     * Update mutable session properties.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionUpdateRequest The sessionUpdateRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionUpdateRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionUpdateRequest sessionUpdateRequest) throws WebClientResponseException {
        Object postBody = sessionUpdateRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionUpdate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'sessionUpdateRequest' is set
        if (sessionUpdateRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionUpdateRequest' when calling sessionUpdate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}", HttpMethod.PATCH, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Update session
     * Update mutable session properties.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionUpdateRequest The sessionUpdateRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionUpdate(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionUpdateRequest sessionUpdateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionUpdateRequestCreation(sessionID, sessionUpdateRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Update session
     * Update mutable session properties.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionUpdateRequest The sessionUpdateRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionUpdateWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionUpdateRequest sessionUpdateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionUpdateRequestCreation(sessionID, sessionUpdateRequest).toEntity(localVarReturnType);
    }

    /**
     * Update session
     * Update mutable session properties.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionUpdateRequest The sessionUpdateRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionUpdateWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionUpdateRequest sessionUpdateRequest) throws WebClientResponseException {
        return sessionUpdateRequestCreation(sessionID, sessionUpdateRequest);
    }

    public class SessionViewRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nonnull SessionViewRequest sessionViewRequest;

        public SessionViewRequest() {}

        public SessionViewRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionViewRequest sessionViewRequest) {
            this.sessionID = sessionID;
            this.sessionViewRequest = sessionViewRequest;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public SessionViewRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nonnull SessionViewRequest sessionViewRequest() {
            return this.sessionViewRequest;
        }
        public SessionViewRequest sessionViewRequest(@jakarta.annotation.Nonnull SessionViewRequest sessionViewRequest) {
            this.sessionViewRequest = sessionViewRequest;
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
            SessionViewRequest request = (SessionViewRequest) o;
            return Objects.equals(this.sessionID, request.sessionID()) &&
                Objects.equals(this.sessionViewRequest, request.sessionViewRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionID, sessionViewRequest);
        }
    }

    /**
     * View session
     * Mark the idle transition observed by the viewer as viewed.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionView request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionView(SessionViewRequest requestParameters) throws WebClientResponseException {
        return this.sessionView(requestParameters.sessionID(), requestParameters.sessionViewRequest());
    }

    /**
     * View session
     * Mark the idle transition observed by the viewer as viewed.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionView request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionViewWithHttpInfo(SessionViewRequest requestParameters) throws WebClientResponseException {
        return this.sessionViewWithHttpInfo(requestParameters.sessionID(), requestParameters.sessionViewRequest());
    }

    /**
     * View session
     * Mark the idle transition observed by the viewer as viewed.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param requestParameters The sessionView request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionViewWithResponseSpec(SessionViewRequest requestParameters) throws WebClientResponseException {
        return this.sessionViewWithResponseSpec(requestParameters.sessionID(), requestParameters.sessionViewRequest());
    }


    /**
     * View session
     * Mark the idle transition observed by the viewer as viewed.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionViewRequest The sessionViewRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec sessionViewRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionViewRequest sessionViewRequest) throws WebClientResponseException {
        Object postBody = sessionViewRequest;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling sessionView", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'sessionViewRequest' is set
        if (sessionViewRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionViewRequest' when calling sessionView", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/api/session/{sessionID}/view", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * View session
     * Mark the idle transition observed by the viewer as viewed.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionViewRequest The sessionViewRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> sessionView(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionViewRequest sessionViewRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionViewRequestCreation(sessionID, sessionViewRequest).bodyToMono(localVarReturnType);
    }

    /**
     * View session
     * Mark the idle transition observed by the viewer as viewed.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionViewRequest The sessionViewRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> sessionViewWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionViewRequest sessionViewRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return sessionViewRequestCreation(sessionID, sessionViewRequest).toEntity(localVarReturnType);
    }

    /**
     * View session
     * Mark the idle transition observed by the viewer as viewed.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - SessionNotFoundError
     * @param sessionID The sessionID parameter
     * @param sessionViewRequest The sessionViewRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec sessionViewWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nonnull SessionViewRequest sessionViewRequest) throws WebClientResponseException {
        return sessionViewRequestCreation(sessionID, sessionViewRequest);
    }
}
