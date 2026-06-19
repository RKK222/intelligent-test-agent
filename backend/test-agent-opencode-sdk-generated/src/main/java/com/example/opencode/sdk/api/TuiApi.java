package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.AuthSet400Response;
import com.example.opencode.sdk.model.BadRequestError;
import com.example.opencode.sdk.model.FindText200ResponseInnerPath;
import com.example.opencode.sdk.model.NotFoundError;
import com.example.opencode.sdk.model.SyncStealRequest;
import com.example.opencode.sdk.model.TuiControlNext200Response;
import com.example.opencode.sdk.model.TuiExecuteCommandRequest;
import com.example.opencode.sdk.model.TuiPublishRequest;
import com.example.opencode.sdk.model.TuiShowToastRequest;

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
public class TuiApi {
    private ApiClient apiClient;

    public TuiApi() {
        this(new ApiClient());
    }

    public TuiApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public class TuiAppendPromptRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable FindText200ResponseInnerPath findText200ResponseInnerPath;

        public TuiAppendPromptRequest() {}

        public TuiAppendPromptRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable FindText200ResponseInnerPath findText200ResponseInnerPath) {
            this.directory = directory;
            this.workspace = workspace;
            this.findText200ResponseInnerPath = findText200ResponseInnerPath;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public TuiAppendPromptRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public TuiAppendPromptRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable FindText200ResponseInnerPath findText200ResponseInnerPath() {
            return this.findText200ResponseInnerPath;
        }
        public TuiAppendPromptRequest findText200ResponseInnerPath(@jakarta.annotation.Nullable FindText200ResponseInnerPath findText200ResponseInnerPath) {
            this.findText200ResponseInnerPath = findText200ResponseInnerPath;
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
            TuiAppendPromptRequest request = (TuiAppendPromptRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.findText200ResponseInnerPath, request.findText200ResponseInnerPath());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace, findText200ResponseInnerPath);
        }
    }

    /**
     * Append TUI prompt
     * Append prompt to the TUI.
     * <p><b>200</b> - Prompt processed successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The tuiAppendPrompt request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> tuiAppendPrompt(TuiAppendPromptRequest requestParameters) throws WebClientResponseException {
        return this.tuiAppendPrompt(requestParameters.directory(), requestParameters.workspace(), requestParameters.findText200ResponseInnerPath());
    }

    /**
     * Append TUI prompt
     * Append prompt to the TUI.
     * <p><b>200</b> - Prompt processed successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The tuiAppendPrompt request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> tuiAppendPromptWithHttpInfo(TuiAppendPromptRequest requestParameters) throws WebClientResponseException {
        return this.tuiAppendPromptWithHttpInfo(requestParameters.directory(), requestParameters.workspace(), requestParameters.findText200ResponseInnerPath());
    }

    /**
     * Append TUI prompt
     * Append prompt to the TUI.
     * <p><b>200</b> - Prompt processed successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The tuiAppendPrompt request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tuiAppendPromptWithResponseSpec(TuiAppendPromptRequest requestParameters) throws WebClientResponseException {
        return this.tuiAppendPromptWithResponseSpec(requestParameters.directory(), requestParameters.workspace(), requestParameters.findText200ResponseInnerPath());
    }


    /**
     * Append TUI prompt
     * Append prompt to the TUI.
     * <p><b>200</b> - Prompt processed successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param findText200ResponseInnerPath The findText200ResponseInnerPath parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec tuiAppendPromptRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable FindText200ResponseInnerPath findText200ResponseInnerPath) throws WebClientResponseException {
        Object postBody = findText200ResponseInnerPath;
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

        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return apiClient.invokeAPI("/tui/append-prompt", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Append TUI prompt
     * Append prompt to the TUI.
     * <p><b>200</b> - Prompt processed successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param findText200ResponseInnerPath The findText200ResponseInnerPath parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> tuiAppendPrompt(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable FindText200ResponseInnerPath findText200ResponseInnerPath) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return tuiAppendPromptRequestCreation(directory, workspace, findText200ResponseInnerPath).bodyToMono(localVarReturnType);
    }

    /**
     * Append TUI prompt
     * Append prompt to the TUI.
     * <p><b>200</b> - Prompt processed successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param findText200ResponseInnerPath The findText200ResponseInnerPath parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> tuiAppendPromptWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable FindText200ResponseInnerPath findText200ResponseInnerPath) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return tuiAppendPromptRequestCreation(directory, workspace, findText200ResponseInnerPath).toEntity(localVarReturnType);
    }

    /**
     * Append TUI prompt
     * Append prompt to the TUI.
     * <p><b>200</b> - Prompt processed successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param findText200ResponseInnerPath The findText200ResponseInnerPath parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tuiAppendPromptWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable FindText200ResponseInnerPath findText200ResponseInnerPath) throws WebClientResponseException {
        return tuiAppendPromptRequestCreation(directory, workspace, findText200ResponseInnerPath);
    }

    public class TuiClearPromptRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public TuiClearPromptRequest() {}

        public TuiClearPromptRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public TuiClearPromptRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public TuiClearPromptRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            TuiClearPromptRequest request = (TuiClearPromptRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * Clear TUI prompt
     * Clear the prompt.
     * <p><b>200</b> - Prompt cleared successfully
     * <p><b>400</b> - Bad request
     * @param requestParameters The tuiClearPrompt request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> tuiClearPrompt(TuiClearPromptRequest requestParameters) throws WebClientResponseException {
        return this.tuiClearPrompt(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Clear TUI prompt
     * Clear the prompt.
     * <p><b>200</b> - Prompt cleared successfully
     * <p><b>400</b> - Bad request
     * @param requestParameters The tuiClearPrompt request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> tuiClearPromptWithHttpInfo(TuiClearPromptRequest requestParameters) throws WebClientResponseException {
        return this.tuiClearPromptWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Clear TUI prompt
     * Clear the prompt.
     * <p><b>200</b> - Prompt cleared successfully
     * <p><b>400</b> - Bad request
     * @param requestParameters The tuiClearPrompt request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tuiClearPromptWithResponseSpec(TuiClearPromptRequest requestParameters) throws WebClientResponseException {
        return this.tuiClearPromptWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Clear TUI prompt
     * Clear the prompt.
     * <p><b>200</b> - Prompt cleared successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec tuiClearPromptRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return apiClient.invokeAPI("/tui/clear-prompt", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Clear TUI prompt
     * Clear the prompt.
     * <p><b>200</b> - Prompt cleared successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> tuiClearPrompt(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return tuiClearPromptRequestCreation(directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Clear TUI prompt
     * Clear the prompt.
     * <p><b>200</b> - Prompt cleared successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> tuiClearPromptWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return tuiClearPromptRequestCreation(directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Clear TUI prompt
     * Clear the prompt.
     * <p><b>200</b> - Prompt cleared successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tuiClearPromptWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return tuiClearPromptRequestCreation(directory, workspace);
    }

    public class TuiControlNextRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public TuiControlNextRequest() {}

        public TuiControlNextRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public TuiControlNextRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public TuiControlNextRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            TuiControlNextRequest request = (TuiControlNextRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * Get next TUI request
     * Retrieve the next TUI request from the queue for processing.
     * <p><b>200</b> - Next TUI request
     * <p><b>400</b> - Bad request
     * @param requestParameters The tuiControlNext request parameters as object
     * @return TuiControlNext200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<TuiControlNext200Response> tuiControlNext(TuiControlNextRequest requestParameters) throws WebClientResponseException {
        return this.tuiControlNext(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get next TUI request
     * Retrieve the next TUI request from the queue for processing.
     * <p><b>200</b> - Next TUI request
     * <p><b>400</b> - Bad request
     * @param requestParameters The tuiControlNext request parameters as object
     * @return ResponseEntity&lt;TuiControlNext200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<TuiControlNext200Response>> tuiControlNextWithHttpInfo(TuiControlNextRequest requestParameters) throws WebClientResponseException {
        return this.tuiControlNextWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get next TUI request
     * Retrieve the next TUI request from the queue for processing.
     * <p><b>200</b> - Next TUI request
     * <p><b>400</b> - Bad request
     * @param requestParameters The tuiControlNext request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tuiControlNextWithResponseSpec(TuiControlNextRequest requestParameters) throws WebClientResponseException {
        return this.tuiControlNextWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Get next TUI request
     * Retrieve the next TUI request from the queue for processing.
     * <p><b>200</b> - Next TUI request
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return TuiControlNext200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec tuiControlNextRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<TuiControlNext200Response> localVarReturnType = new ParameterizedTypeReference<TuiControlNext200Response>() {};
        return apiClient.invokeAPI("/tui/control/next", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get next TUI request
     * Retrieve the next TUI request from the queue for processing.
     * <p><b>200</b> - Next TUI request
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return TuiControlNext200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<TuiControlNext200Response> tuiControlNext(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<TuiControlNext200Response> localVarReturnType = new ParameterizedTypeReference<TuiControlNext200Response>() {};
        return tuiControlNextRequestCreation(directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Get next TUI request
     * Retrieve the next TUI request from the queue for processing.
     * <p><b>200</b> - Next TUI request
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;TuiControlNext200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<TuiControlNext200Response>> tuiControlNextWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<TuiControlNext200Response> localVarReturnType = new ParameterizedTypeReference<TuiControlNext200Response>() {};
        return tuiControlNextRequestCreation(directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Get next TUI request
     * Retrieve the next TUI request from the queue for processing.
     * <p><b>200</b> - Next TUI request
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tuiControlNextWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return tuiControlNextRequestCreation(directory, workspace);
    }

    public class TuiControlResponseRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable Object body;

        public TuiControlResponseRequest() {}

        public TuiControlResponseRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Object body) {
            this.directory = directory;
            this.workspace = workspace;
            this.body = body;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public TuiControlResponseRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public TuiControlResponseRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable Object body() {
            return this.body;
        }
        public TuiControlResponseRequest body(@jakarta.annotation.Nullable Object body) {
            this.body = body;
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
            TuiControlResponseRequest request = (TuiControlResponseRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.body, request.body());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace, body);
        }
    }

    /**
     * Submit TUI response
     * Submit a response to the TUI request queue to complete a pending request.
     * <p><b>200</b> - Response submitted successfully
     * <p><b>400</b> - Bad request
     * @param requestParameters The tuiControlResponse request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> tuiControlResponse(TuiControlResponseRequest requestParameters) throws WebClientResponseException {
        return this.tuiControlResponse(requestParameters.directory(), requestParameters.workspace(), requestParameters.body());
    }

    /**
     * Submit TUI response
     * Submit a response to the TUI request queue to complete a pending request.
     * <p><b>200</b> - Response submitted successfully
     * <p><b>400</b> - Bad request
     * @param requestParameters The tuiControlResponse request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> tuiControlResponseWithHttpInfo(TuiControlResponseRequest requestParameters) throws WebClientResponseException {
        return this.tuiControlResponseWithHttpInfo(requestParameters.directory(), requestParameters.workspace(), requestParameters.body());
    }

    /**
     * Submit TUI response
     * Submit a response to the TUI request queue to complete a pending request.
     * <p><b>200</b> - Response submitted successfully
     * <p><b>400</b> - Bad request
     * @param requestParameters The tuiControlResponse request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tuiControlResponseWithResponseSpec(TuiControlResponseRequest requestParameters) throws WebClientResponseException {
        return this.tuiControlResponseWithResponseSpec(requestParameters.directory(), requestParameters.workspace(), requestParameters.body());
    }


    /**
     * Submit TUI response
     * Submit a response to the TUI request queue to complete a pending request.
     * <p><b>200</b> - Response submitted successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param body The body parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec tuiControlResponseRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Object body) throws WebClientResponseException {
        Object postBody = body;
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

        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return apiClient.invokeAPI("/tui/control/response", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Submit TUI response
     * Submit a response to the TUI request queue to complete a pending request.
     * <p><b>200</b> - Response submitted successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param body The body parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> tuiControlResponse(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Object body) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return tuiControlResponseRequestCreation(directory, workspace, body).bodyToMono(localVarReturnType);
    }

    /**
     * Submit TUI response
     * Submit a response to the TUI request queue to complete a pending request.
     * <p><b>200</b> - Response submitted successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param body The body parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> tuiControlResponseWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Object body) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return tuiControlResponseRequestCreation(directory, workspace, body).toEntity(localVarReturnType);
    }

    /**
     * Submit TUI response
     * Submit a response to the TUI request queue to complete a pending request.
     * <p><b>200</b> - Response submitted successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param body The body parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tuiControlResponseWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Object body) throws WebClientResponseException {
        return tuiControlResponseRequestCreation(directory, workspace, body);
    }

    public class TuiExecuteCommandRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable TuiExecuteCommandRequest tuiExecuteCommandRequest;

        public TuiExecuteCommandRequest() {}

        public TuiExecuteCommandRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable TuiExecuteCommandRequest tuiExecuteCommandRequest) {
            this.directory = directory;
            this.workspace = workspace;
            this.tuiExecuteCommandRequest = tuiExecuteCommandRequest;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public TuiExecuteCommandRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public TuiExecuteCommandRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable TuiExecuteCommandRequest tuiExecuteCommandRequest() {
            return this.tuiExecuteCommandRequest;
        }
        public TuiExecuteCommandRequest tuiExecuteCommandRequest(@jakarta.annotation.Nullable TuiExecuteCommandRequest tuiExecuteCommandRequest) {
            this.tuiExecuteCommandRequest = tuiExecuteCommandRequest;
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
            TuiExecuteCommandRequest request = (TuiExecuteCommandRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.tuiExecuteCommandRequest, request.tuiExecuteCommandRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace, tuiExecuteCommandRequest);
        }
    }

    /**
     * Execute TUI command
     * Execute a TUI command.
     * <p><b>200</b> - Command executed successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The tuiExecuteCommand request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> tuiExecuteCommand(TuiExecuteCommandRequest requestParameters) throws WebClientResponseException {
        return this.tuiExecuteCommand(requestParameters.directory(), requestParameters.workspace(), requestParameters.tuiExecuteCommandRequest());
    }

    /**
     * Execute TUI command
     * Execute a TUI command.
     * <p><b>200</b> - Command executed successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The tuiExecuteCommand request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> tuiExecuteCommandWithHttpInfo(TuiExecuteCommandRequest requestParameters) throws WebClientResponseException {
        return this.tuiExecuteCommandWithHttpInfo(requestParameters.directory(), requestParameters.workspace(), requestParameters.tuiExecuteCommandRequest());
    }

    /**
     * Execute TUI command
     * Execute a TUI command.
     * <p><b>200</b> - Command executed successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The tuiExecuteCommand request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tuiExecuteCommandWithResponseSpec(TuiExecuteCommandRequest requestParameters) throws WebClientResponseException {
        return this.tuiExecuteCommandWithResponseSpec(requestParameters.directory(), requestParameters.workspace(), requestParameters.tuiExecuteCommandRequest());
    }


    /**
     * Execute TUI command
     * Execute a TUI command.
     * <p><b>200</b> - Command executed successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param tuiExecuteCommandRequest The tuiExecuteCommandRequest parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec tuiExecuteCommandRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable TuiExecuteCommandRequest tuiExecuteCommandRequest) throws WebClientResponseException {
        Object postBody = tuiExecuteCommandRequest;
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

        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return apiClient.invokeAPI("/tui/execute-command", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Execute TUI command
     * Execute a TUI command.
     * <p><b>200</b> - Command executed successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param tuiExecuteCommandRequest The tuiExecuteCommandRequest parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> tuiExecuteCommand(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable TuiExecuteCommandRequest tuiExecuteCommandRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return tuiExecuteCommandRequestCreation(directory, workspace, tuiExecuteCommandRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Execute TUI command
     * Execute a TUI command.
     * <p><b>200</b> - Command executed successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param tuiExecuteCommandRequest The tuiExecuteCommandRequest parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> tuiExecuteCommandWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable TuiExecuteCommandRequest tuiExecuteCommandRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return tuiExecuteCommandRequestCreation(directory, workspace, tuiExecuteCommandRequest).toEntity(localVarReturnType);
    }

    /**
     * Execute TUI command
     * Execute a TUI command.
     * <p><b>200</b> - Command executed successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param tuiExecuteCommandRequest The tuiExecuteCommandRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tuiExecuteCommandWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable TuiExecuteCommandRequest tuiExecuteCommandRequest) throws WebClientResponseException {
        return tuiExecuteCommandRequestCreation(directory, workspace, tuiExecuteCommandRequest);
    }

    public class TuiOpenHelpRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public TuiOpenHelpRequest() {}

        public TuiOpenHelpRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public TuiOpenHelpRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public TuiOpenHelpRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            TuiOpenHelpRequest request = (TuiOpenHelpRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * Open help dialog
     * Open the help dialog in the TUI to display user assistance information.
     * <p><b>200</b> - Help dialog opened successfully
     * <p><b>400</b> - Bad request
     * @param requestParameters The tuiOpenHelp request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> tuiOpenHelp(TuiOpenHelpRequest requestParameters) throws WebClientResponseException {
        return this.tuiOpenHelp(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Open help dialog
     * Open the help dialog in the TUI to display user assistance information.
     * <p><b>200</b> - Help dialog opened successfully
     * <p><b>400</b> - Bad request
     * @param requestParameters The tuiOpenHelp request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> tuiOpenHelpWithHttpInfo(TuiOpenHelpRequest requestParameters) throws WebClientResponseException {
        return this.tuiOpenHelpWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Open help dialog
     * Open the help dialog in the TUI to display user assistance information.
     * <p><b>200</b> - Help dialog opened successfully
     * <p><b>400</b> - Bad request
     * @param requestParameters The tuiOpenHelp request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tuiOpenHelpWithResponseSpec(TuiOpenHelpRequest requestParameters) throws WebClientResponseException {
        return this.tuiOpenHelpWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Open help dialog
     * Open the help dialog in the TUI to display user assistance information.
     * <p><b>200</b> - Help dialog opened successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec tuiOpenHelpRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return apiClient.invokeAPI("/tui/open-help", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Open help dialog
     * Open the help dialog in the TUI to display user assistance information.
     * <p><b>200</b> - Help dialog opened successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> tuiOpenHelp(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return tuiOpenHelpRequestCreation(directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Open help dialog
     * Open the help dialog in the TUI to display user assistance information.
     * <p><b>200</b> - Help dialog opened successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> tuiOpenHelpWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return tuiOpenHelpRequestCreation(directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Open help dialog
     * Open the help dialog in the TUI to display user assistance information.
     * <p><b>200</b> - Help dialog opened successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tuiOpenHelpWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return tuiOpenHelpRequestCreation(directory, workspace);
    }

    public class TuiOpenModelsRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public TuiOpenModelsRequest() {}

        public TuiOpenModelsRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public TuiOpenModelsRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public TuiOpenModelsRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            TuiOpenModelsRequest request = (TuiOpenModelsRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * Open models dialog
     * Open the model dialog.
     * <p><b>200</b> - Model dialog opened successfully
     * <p><b>400</b> - Bad request
     * @param requestParameters The tuiOpenModels request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> tuiOpenModels(TuiOpenModelsRequest requestParameters) throws WebClientResponseException {
        return this.tuiOpenModels(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Open models dialog
     * Open the model dialog.
     * <p><b>200</b> - Model dialog opened successfully
     * <p><b>400</b> - Bad request
     * @param requestParameters The tuiOpenModels request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> tuiOpenModelsWithHttpInfo(TuiOpenModelsRequest requestParameters) throws WebClientResponseException {
        return this.tuiOpenModelsWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Open models dialog
     * Open the model dialog.
     * <p><b>200</b> - Model dialog opened successfully
     * <p><b>400</b> - Bad request
     * @param requestParameters The tuiOpenModels request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tuiOpenModelsWithResponseSpec(TuiOpenModelsRequest requestParameters) throws WebClientResponseException {
        return this.tuiOpenModelsWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Open models dialog
     * Open the model dialog.
     * <p><b>200</b> - Model dialog opened successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec tuiOpenModelsRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return apiClient.invokeAPI("/tui/open-models", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Open models dialog
     * Open the model dialog.
     * <p><b>200</b> - Model dialog opened successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> tuiOpenModels(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return tuiOpenModelsRequestCreation(directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Open models dialog
     * Open the model dialog.
     * <p><b>200</b> - Model dialog opened successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> tuiOpenModelsWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return tuiOpenModelsRequestCreation(directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Open models dialog
     * Open the model dialog.
     * <p><b>200</b> - Model dialog opened successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tuiOpenModelsWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return tuiOpenModelsRequestCreation(directory, workspace);
    }

    public class TuiOpenSessionsRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public TuiOpenSessionsRequest() {}

        public TuiOpenSessionsRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public TuiOpenSessionsRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public TuiOpenSessionsRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            TuiOpenSessionsRequest request = (TuiOpenSessionsRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * Open sessions dialog
     * Open the session dialog.
     * <p><b>200</b> - Session dialog opened successfully
     * <p><b>400</b> - Bad request
     * @param requestParameters The tuiOpenSessions request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> tuiOpenSessions(TuiOpenSessionsRequest requestParameters) throws WebClientResponseException {
        return this.tuiOpenSessions(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Open sessions dialog
     * Open the session dialog.
     * <p><b>200</b> - Session dialog opened successfully
     * <p><b>400</b> - Bad request
     * @param requestParameters The tuiOpenSessions request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> tuiOpenSessionsWithHttpInfo(TuiOpenSessionsRequest requestParameters) throws WebClientResponseException {
        return this.tuiOpenSessionsWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Open sessions dialog
     * Open the session dialog.
     * <p><b>200</b> - Session dialog opened successfully
     * <p><b>400</b> - Bad request
     * @param requestParameters The tuiOpenSessions request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tuiOpenSessionsWithResponseSpec(TuiOpenSessionsRequest requestParameters) throws WebClientResponseException {
        return this.tuiOpenSessionsWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Open sessions dialog
     * Open the session dialog.
     * <p><b>200</b> - Session dialog opened successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec tuiOpenSessionsRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return apiClient.invokeAPI("/tui/open-sessions", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Open sessions dialog
     * Open the session dialog.
     * <p><b>200</b> - Session dialog opened successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> tuiOpenSessions(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return tuiOpenSessionsRequestCreation(directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Open sessions dialog
     * Open the session dialog.
     * <p><b>200</b> - Session dialog opened successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> tuiOpenSessionsWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return tuiOpenSessionsRequestCreation(directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Open sessions dialog
     * Open the session dialog.
     * <p><b>200</b> - Session dialog opened successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tuiOpenSessionsWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return tuiOpenSessionsRequestCreation(directory, workspace);
    }

    public class TuiOpenThemesRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public TuiOpenThemesRequest() {}

        public TuiOpenThemesRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public TuiOpenThemesRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public TuiOpenThemesRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            TuiOpenThemesRequest request = (TuiOpenThemesRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * Open themes dialog
     * Open the theme dialog.
     * <p><b>200</b> - Theme dialog opened successfully
     * <p><b>400</b> - Bad request
     * @param requestParameters The tuiOpenThemes request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> tuiOpenThemes(TuiOpenThemesRequest requestParameters) throws WebClientResponseException {
        return this.tuiOpenThemes(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Open themes dialog
     * Open the theme dialog.
     * <p><b>200</b> - Theme dialog opened successfully
     * <p><b>400</b> - Bad request
     * @param requestParameters The tuiOpenThemes request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> tuiOpenThemesWithHttpInfo(TuiOpenThemesRequest requestParameters) throws WebClientResponseException {
        return this.tuiOpenThemesWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Open themes dialog
     * Open the theme dialog.
     * <p><b>200</b> - Theme dialog opened successfully
     * <p><b>400</b> - Bad request
     * @param requestParameters The tuiOpenThemes request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tuiOpenThemesWithResponseSpec(TuiOpenThemesRequest requestParameters) throws WebClientResponseException {
        return this.tuiOpenThemesWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Open themes dialog
     * Open the theme dialog.
     * <p><b>200</b> - Theme dialog opened successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec tuiOpenThemesRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return apiClient.invokeAPI("/tui/open-themes", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Open themes dialog
     * Open the theme dialog.
     * <p><b>200</b> - Theme dialog opened successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> tuiOpenThemes(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return tuiOpenThemesRequestCreation(directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Open themes dialog
     * Open the theme dialog.
     * <p><b>200</b> - Theme dialog opened successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> tuiOpenThemesWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return tuiOpenThemesRequestCreation(directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Open themes dialog
     * Open the theme dialog.
     * <p><b>200</b> - Theme dialog opened successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tuiOpenThemesWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return tuiOpenThemesRequestCreation(directory, workspace);
    }

    public class TuiPublishRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable TuiPublishRequest tuiPublishRequest;

        public TuiPublishRequest() {}

        public TuiPublishRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable TuiPublishRequest tuiPublishRequest) {
            this.directory = directory;
            this.workspace = workspace;
            this.tuiPublishRequest = tuiPublishRequest;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public TuiPublishRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public TuiPublishRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable TuiPublishRequest tuiPublishRequest() {
            return this.tuiPublishRequest;
        }
        public TuiPublishRequest tuiPublishRequest(@jakarta.annotation.Nullable TuiPublishRequest tuiPublishRequest) {
            this.tuiPublishRequest = tuiPublishRequest;
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
            TuiPublishRequest request = (TuiPublishRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.tuiPublishRequest, request.tuiPublishRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace, tuiPublishRequest);
        }
    }

    /**
     * Publish TUI event
     * Publish a TUI event.
     * <p><b>200</b> - Event published successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The tuiPublish request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> tuiPublish(TuiPublishRequest requestParameters) throws WebClientResponseException {
        return this.tuiPublish(requestParameters.directory(), requestParameters.workspace(), requestParameters.tuiPublishRequest());
    }

    /**
     * Publish TUI event
     * Publish a TUI event.
     * <p><b>200</b> - Event published successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The tuiPublish request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> tuiPublishWithHttpInfo(TuiPublishRequest requestParameters) throws WebClientResponseException {
        return this.tuiPublishWithHttpInfo(requestParameters.directory(), requestParameters.workspace(), requestParameters.tuiPublishRequest());
    }

    /**
     * Publish TUI event
     * Publish a TUI event.
     * <p><b>200</b> - Event published successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The tuiPublish request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tuiPublishWithResponseSpec(TuiPublishRequest requestParameters) throws WebClientResponseException {
        return this.tuiPublishWithResponseSpec(requestParameters.directory(), requestParameters.workspace(), requestParameters.tuiPublishRequest());
    }


    /**
     * Publish TUI event
     * Publish a TUI event.
     * <p><b>200</b> - Event published successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param tuiPublishRequest The tuiPublishRequest parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec tuiPublishRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable TuiPublishRequest tuiPublishRequest) throws WebClientResponseException {
        Object postBody = tuiPublishRequest;
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

        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return apiClient.invokeAPI("/tui/publish", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Publish TUI event
     * Publish a TUI event.
     * <p><b>200</b> - Event published successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param tuiPublishRequest The tuiPublishRequest parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> tuiPublish(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable TuiPublishRequest tuiPublishRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return tuiPublishRequestCreation(directory, workspace, tuiPublishRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Publish TUI event
     * Publish a TUI event.
     * <p><b>200</b> - Event published successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param tuiPublishRequest The tuiPublishRequest parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> tuiPublishWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable TuiPublishRequest tuiPublishRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return tuiPublishRequestCreation(directory, workspace, tuiPublishRequest).toEntity(localVarReturnType);
    }

    /**
     * Publish TUI event
     * Publish a TUI event.
     * <p><b>200</b> - Event published successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param tuiPublishRequest The tuiPublishRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tuiPublishWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable TuiPublishRequest tuiPublishRequest) throws WebClientResponseException {
        return tuiPublishRequestCreation(directory, workspace, tuiPublishRequest);
    }

    public class TuiSelectSessionRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable SyncStealRequest syncStealRequest;

        public TuiSelectSessionRequest() {}

        public TuiSelectSessionRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SyncStealRequest syncStealRequest) {
            this.directory = directory;
            this.workspace = workspace;
            this.syncStealRequest = syncStealRequest;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public TuiSelectSessionRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public TuiSelectSessionRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable SyncStealRequest syncStealRequest() {
            return this.syncStealRequest;
        }
        public TuiSelectSessionRequest syncStealRequest(@jakarta.annotation.Nullable SyncStealRequest syncStealRequest) {
            this.syncStealRequest = syncStealRequest;
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
            TuiSelectSessionRequest request = (TuiSelectSessionRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.syncStealRequest, request.syncStealRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace, syncStealRequest);
        }
    }

    /**
     * Select session
     * Navigate the TUI to display the specified session.
     * <p><b>200</b> - Session selected successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The tuiSelectSession request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> tuiSelectSession(TuiSelectSessionRequest requestParameters) throws WebClientResponseException {
        return this.tuiSelectSession(requestParameters.directory(), requestParameters.workspace(), requestParameters.syncStealRequest());
    }

    /**
     * Select session
     * Navigate the TUI to display the specified session.
     * <p><b>200</b> - Session selected successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The tuiSelectSession request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> tuiSelectSessionWithHttpInfo(TuiSelectSessionRequest requestParameters) throws WebClientResponseException {
        return this.tuiSelectSessionWithHttpInfo(requestParameters.directory(), requestParameters.workspace(), requestParameters.syncStealRequest());
    }

    /**
     * Select session
     * Navigate the TUI to display the specified session.
     * <p><b>200</b> - Session selected successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param requestParameters The tuiSelectSession request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tuiSelectSessionWithResponseSpec(TuiSelectSessionRequest requestParameters) throws WebClientResponseException {
        return this.tuiSelectSessionWithResponseSpec(requestParameters.directory(), requestParameters.workspace(), requestParameters.syncStealRequest());
    }


    /**
     * Select session
     * Navigate the TUI to display the specified session.
     * <p><b>200</b> - Session selected successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param syncStealRequest The syncStealRequest parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec tuiSelectSessionRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SyncStealRequest syncStealRequest) throws WebClientResponseException {
        Object postBody = syncStealRequest;
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

        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return apiClient.invokeAPI("/tui/select-session", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Select session
     * Navigate the TUI to display the specified session.
     * <p><b>200</b> - Session selected successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param syncStealRequest The syncStealRequest parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> tuiSelectSession(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SyncStealRequest syncStealRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return tuiSelectSessionRequestCreation(directory, workspace, syncStealRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Select session
     * Navigate the TUI to display the specified session.
     * <p><b>200</b> - Session selected successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param syncStealRequest The syncStealRequest parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> tuiSelectSessionWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SyncStealRequest syncStealRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return tuiSelectSessionRequestCreation(directory, workspace, syncStealRequest).toEntity(localVarReturnType);
    }

    /**
     * Select session
     * Navigate the TUI to display the specified session.
     * <p><b>200</b> - Session selected successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * <p><b>404</b> - NotFoundError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param syncStealRequest The syncStealRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tuiSelectSessionWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable SyncStealRequest syncStealRequest) throws WebClientResponseException {
        return tuiSelectSessionRequestCreation(directory, workspace, syncStealRequest);
    }

    public class TuiShowToastRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable TuiShowToastRequest tuiShowToastRequest;

        public TuiShowToastRequest() {}

        public TuiShowToastRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable TuiShowToastRequest tuiShowToastRequest) {
            this.directory = directory;
            this.workspace = workspace;
            this.tuiShowToastRequest = tuiShowToastRequest;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public TuiShowToastRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public TuiShowToastRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable TuiShowToastRequest tuiShowToastRequest() {
            return this.tuiShowToastRequest;
        }
        public TuiShowToastRequest tuiShowToastRequest(@jakarta.annotation.Nullable TuiShowToastRequest tuiShowToastRequest) {
            this.tuiShowToastRequest = tuiShowToastRequest;
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
            TuiShowToastRequest request = (TuiShowToastRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.tuiShowToastRequest, request.tuiShowToastRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace, tuiShowToastRequest);
        }
    }

    /**
     * Show TUI toast
     * Show a toast notification in the TUI.
     * <p><b>200</b> - Toast notification shown successfully
     * <p><b>400</b> - Bad request
     * @param requestParameters The tuiShowToast request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> tuiShowToast(TuiShowToastRequest requestParameters) throws WebClientResponseException {
        return this.tuiShowToast(requestParameters.directory(), requestParameters.workspace(), requestParameters.tuiShowToastRequest());
    }

    /**
     * Show TUI toast
     * Show a toast notification in the TUI.
     * <p><b>200</b> - Toast notification shown successfully
     * <p><b>400</b> - Bad request
     * @param requestParameters The tuiShowToast request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> tuiShowToastWithHttpInfo(TuiShowToastRequest requestParameters) throws WebClientResponseException {
        return this.tuiShowToastWithHttpInfo(requestParameters.directory(), requestParameters.workspace(), requestParameters.tuiShowToastRequest());
    }

    /**
     * Show TUI toast
     * Show a toast notification in the TUI.
     * <p><b>200</b> - Toast notification shown successfully
     * <p><b>400</b> - Bad request
     * @param requestParameters The tuiShowToast request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tuiShowToastWithResponseSpec(TuiShowToastRequest requestParameters) throws WebClientResponseException {
        return this.tuiShowToastWithResponseSpec(requestParameters.directory(), requestParameters.workspace(), requestParameters.tuiShowToastRequest());
    }


    /**
     * Show TUI toast
     * Show a toast notification in the TUI.
     * <p><b>200</b> - Toast notification shown successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param tuiShowToastRequest The tuiShowToastRequest parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec tuiShowToastRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable TuiShowToastRequest tuiShowToastRequest) throws WebClientResponseException {
        Object postBody = tuiShowToastRequest;
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

        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return apiClient.invokeAPI("/tui/show-toast", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Show TUI toast
     * Show a toast notification in the TUI.
     * <p><b>200</b> - Toast notification shown successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param tuiShowToastRequest The tuiShowToastRequest parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> tuiShowToast(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable TuiShowToastRequest tuiShowToastRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return tuiShowToastRequestCreation(directory, workspace, tuiShowToastRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Show TUI toast
     * Show a toast notification in the TUI.
     * <p><b>200</b> - Toast notification shown successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param tuiShowToastRequest The tuiShowToastRequest parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> tuiShowToastWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable TuiShowToastRequest tuiShowToastRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return tuiShowToastRequestCreation(directory, workspace, tuiShowToastRequest).toEntity(localVarReturnType);
    }

    /**
     * Show TUI toast
     * Show a toast notification in the TUI.
     * <p><b>200</b> - Toast notification shown successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param tuiShowToastRequest The tuiShowToastRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tuiShowToastWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable TuiShowToastRequest tuiShowToastRequest) throws WebClientResponseException {
        return tuiShowToastRequestCreation(directory, workspace, tuiShowToastRequest);
    }

    public class TuiSubmitPromptRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public TuiSubmitPromptRequest() {}

        public TuiSubmitPromptRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public TuiSubmitPromptRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public TuiSubmitPromptRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            TuiSubmitPromptRequest request = (TuiSubmitPromptRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * Submit TUI prompt
     * Submit the prompt.
     * <p><b>200</b> - Prompt submitted successfully
     * <p><b>400</b> - Bad request
     * @param requestParameters The tuiSubmitPrompt request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> tuiSubmitPrompt(TuiSubmitPromptRequest requestParameters) throws WebClientResponseException {
        return this.tuiSubmitPrompt(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Submit TUI prompt
     * Submit the prompt.
     * <p><b>200</b> - Prompt submitted successfully
     * <p><b>400</b> - Bad request
     * @param requestParameters The tuiSubmitPrompt request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> tuiSubmitPromptWithHttpInfo(TuiSubmitPromptRequest requestParameters) throws WebClientResponseException {
        return this.tuiSubmitPromptWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Submit TUI prompt
     * Submit the prompt.
     * <p><b>200</b> - Prompt submitted successfully
     * <p><b>400</b> - Bad request
     * @param requestParameters The tuiSubmitPrompt request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tuiSubmitPromptWithResponseSpec(TuiSubmitPromptRequest requestParameters) throws WebClientResponseException {
        return this.tuiSubmitPromptWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Submit TUI prompt
     * Submit the prompt.
     * <p><b>200</b> - Prompt submitted successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec tuiSubmitPromptRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return apiClient.invokeAPI("/tui/submit-prompt", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Submit TUI prompt
     * Submit the prompt.
     * <p><b>200</b> - Prompt submitted successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> tuiSubmitPrompt(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return tuiSubmitPromptRequestCreation(directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Submit TUI prompt
     * Submit the prompt.
     * <p><b>200</b> - Prompt submitted successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> tuiSubmitPromptWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return tuiSubmitPromptRequestCreation(directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Submit TUI prompt
     * Submit the prompt.
     * <p><b>200</b> - Prompt submitted successfully
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tuiSubmitPromptWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return tuiSubmitPromptRequestCreation(directory, workspace);
    }
}
