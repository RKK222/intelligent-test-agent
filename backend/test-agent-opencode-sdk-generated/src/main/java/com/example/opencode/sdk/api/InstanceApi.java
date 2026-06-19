package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.Agent;
import com.example.opencode.sdk.model.AppSkills200ResponseInner;
import com.example.opencode.sdk.model.BadRequestError;
import com.example.opencode.sdk.model.Command;
import com.example.opencode.sdk.model.FormatterStatus;
import com.example.opencode.sdk.model.LSPStatus;
import com.example.opencode.sdk.model.Path;
import com.example.opencode.sdk.model.VcsApply200Response;
import com.example.opencode.sdk.model.VcsApply400Response;
import com.example.opencode.sdk.model.VcsApplyRequest;
import com.example.opencode.sdk.model.VcsFileDiff;
import com.example.opencode.sdk.model.VcsFileStatus;
import com.example.opencode.sdk.model.VcsInfo;

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
public class InstanceApi {
    private ApiClient apiClient;

    public InstanceApi() {
        this(new ApiClient());
    }

    public InstanceApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public class AppAgentsRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public AppAgentsRequest() {}

        public AppAgentsRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public AppAgentsRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public AppAgentsRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            AppAgentsRequest request = (AppAgentsRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * List agents
     * Get a list of all available AI agents in the OpenCode system.
     * <p><b>200</b> - List of agents
     * <p><b>400</b> - Bad request
     * @param requestParameters The appAgents request parameters as object
     * @return List&lt;Agent&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<Agent> appAgents(AppAgentsRequest requestParameters) throws WebClientResponseException {
        return this.appAgents(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List agents
     * Get a list of all available AI agents in the OpenCode system.
     * <p><b>200</b> - List of agents
     * <p><b>400</b> - Bad request
     * @param requestParameters The appAgents request parameters as object
     * @return ResponseEntity&lt;List&lt;Agent&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<Agent>>> appAgentsWithHttpInfo(AppAgentsRequest requestParameters) throws WebClientResponseException {
        return this.appAgentsWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List agents
     * Get a list of all available AI agents in the OpenCode system.
     * <p><b>200</b> - List of agents
     * <p><b>400</b> - Bad request
     * @param requestParameters The appAgents request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec appAgentsWithResponseSpec(AppAgentsRequest requestParameters) throws WebClientResponseException {
        return this.appAgentsWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * List agents
     * Get a list of all available AI agents in the OpenCode system.
     * <p><b>200</b> - List of agents
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;Agent&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec appAgentsRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<Agent> localVarReturnType = new ParameterizedTypeReference<Agent>() {};
        return apiClient.invokeAPI("/agent", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List agents
     * Get a list of all available AI agents in the OpenCode system.
     * <p><b>200</b> - List of agents
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;Agent&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<Agent> appAgents(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Agent> localVarReturnType = new ParameterizedTypeReference<Agent>() {};
        return appAgentsRequestCreation(directory, workspace).bodyToFlux(localVarReturnType);
    }

    /**
     * List agents
     * Get a list of all available AI agents in the OpenCode system.
     * <p><b>200</b> - List of agents
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;List&lt;Agent&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<Agent>>> appAgentsWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Agent> localVarReturnType = new ParameterizedTypeReference<Agent>() {};
        return appAgentsRequestCreation(directory, workspace).toEntityList(localVarReturnType);
    }

    /**
     * List agents
     * Get a list of all available AI agents in the OpenCode system.
     * <p><b>200</b> - List of agents
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec appAgentsWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return appAgentsRequestCreation(directory, workspace);
    }

    public class AppSkillsRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public AppSkillsRequest() {}

        public AppSkillsRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public AppSkillsRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public AppSkillsRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            AppSkillsRequest request = (AppSkillsRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * List skills
     * Get a list of all available skills in the OpenCode system.
     * <p><b>200</b> - List of skills
     * <p><b>400</b> - Bad request
     * @param requestParameters The appSkills request parameters as object
     * @return List&lt;AppSkills200ResponseInner&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<AppSkills200ResponseInner> appSkills(AppSkillsRequest requestParameters) throws WebClientResponseException {
        return this.appSkills(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List skills
     * Get a list of all available skills in the OpenCode system.
     * <p><b>200</b> - List of skills
     * <p><b>400</b> - Bad request
     * @param requestParameters The appSkills request parameters as object
     * @return ResponseEntity&lt;List&lt;AppSkills200ResponseInner&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<AppSkills200ResponseInner>>> appSkillsWithHttpInfo(AppSkillsRequest requestParameters) throws WebClientResponseException {
        return this.appSkillsWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List skills
     * Get a list of all available skills in the OpenCode system.
     * <p><b>200</b> - List of skills
     * <p><b>400</b> - Bad request
     * @param requestParameters The appSkills request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec appSkillsWithResponseSpec(AppSkillsRequest requestParameters) throws WebClientResponseException {
        return this.appSkillsWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * List skills
     * Get a list of all available skills in the OpenCode system.
     * <p><b>200</b> - List of skills
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;AppSkills200ResponseInner&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec appSkillsRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<AppSkills200ResponseInner> localVarReturnType = new ParameterizedTypeReference<AppSkills200ResponseInner>() {};
        return apiClient.invokeAPI("/skill", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List skills
     * Get a list of all available skills in the OpenCode system.
     * <p><b>200</b> - List of skills
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;AppSkills200ResponseInner&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<AppSkills200ResponseInner> appSkills(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<AppSkills200ResponseInner> localVarReturnType = new ParameterizedTypeReference<AppSkills200ResponseInner>() {};
        return appSkillsRequestCreation(directory, workspace).bodyToFlux(localVarReturnType);
    }

    /**
     * List skills
     * Get a list of all available skills in the OpenCode system.
     * <p><b>200</b> - List of skills
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;List&lt;AppSkills200ResponseInner&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<AppSkills200ResponseInner>>> appSkillsWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<AppSkills200ResponseInner> localVarReturnType = new ParameterizedTypeReference<AppSkills200ResponseInner>() {};
        return appSkillsRequestCreation(directory, workspace).toEntityList(localVarReturnType);
    }

    /**
     * List skills
     * Get a list of all available skills in the OpenCode system.
     * <p><b>200</b> - List of skills
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec appSkillsWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return appSkillsRequestCreation(directory, workspace);
    }

    public class CommandListRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public CommandListRequest() {}

        public CommandListRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public CommandListRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public CommandListRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            CommandListRequest request = (CommandListRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * List commands
     * Get a list of all available commands in the OpenCode system.
     * <p><b>200</b> - List of commands
     * <p><b>400</b> - Bad request
     * @param requestParameters The commandList request parameters as object
     * @return List&lt;Command&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<Command> commandList(CommandListRequest requestParameters) throws WebClientResponseException {
        return this.commandList(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List commands
     * Get a list of all available commands in the OpenCode system.
     * <p><b>200</b> - List of commands
     * <p><b>400</b> - Bad request
     * @param requestParameters The commandList request parameters as object
     * @return ResponseEntity&lt;List&lt;Command&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<Command>>> commandListWithHttpInfo(CommandListRequest requestParameters) throws WebClientResponseException {
        return this.commandListWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List commands
     * Get a list of all available commands in the OpenCode system.
     * <p><b>200</b> - List of commands
     * <p><b>400</b> - Bad request
     * @param requestParameters The commandList request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec commandListWithResponseSpec(CommandListRequest requestParameters) throws WebClientResponseException {
        return this.commandListWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * List commands
     * Get a list of all available commands in the OpenCode system.
     * <p><b>200</b> - List of commands
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;Command&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec commandListRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<Command> localVarReturnType = new ParameterizedTypeReference<Command>() {};
        return apiClient.invokeAPI("/command", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List commands
     * Get a list of all available commands in the OpenCode system.
     * <p><b>200</b> - List of commands
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;Command&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<Command> commandList(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Command> localVarReturnType = new ParameterizedTypeReference<Command>() {};
        return commandListRequestCreation(directory, workspace).bodyToFlux(localVarReturnType);
    }

    /**
     * List commands
     * Get a list of all available commands in the OpenCode system.
     * <p><b>200</b> - List of commands
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;List&lt;Command&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<Command>>> commandListWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Command> localVarReturnType = new ParameterizedTypeReference<Command>() {};
        return commandListRequestCreation(directory, workspace).toEntityList(localVarReturnType);
    }

    /**
     * List commands
     * Get a list of all available commands in the OpenCode system.
     * <p><b>200</b> - List of commands
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec commandListWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return commandListRequestCreation(directory, workspace);
    }

    public class FormatterStatusRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public FormatterStatusRequest() {}

        public FormatterStatusRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public FormatterStatusRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public FormatterStatusRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            FormatterStatusRequest request = (FormatterStatusRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * Get formatter status
     * Get formatter status
     * <p><b>200</b> - Formatter status
     * <p><b>400</b> - Bad request
     * @param requestParameters The formatterStatus request parameters as object
     * @return List&lt;FormatterStatus&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<FormatterStatus> formatterStatus(FormatterStatusRequest requestParameters) throws WebClientResponseException {
        return this.formatterStatus(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get formatter status
     * Get formatter status
     * <p><b>200</b> - Formatter status
     * <p><b>400</b> - Bad request
     * @param requestParameters The formatterStatus request parameters as object
     * @return ResponseEntity&lt;List&lt;FormatterStatus&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<FormatterStatus>>> formatterStatusWithHttpInfo(FormatterStatusRequest requestParameters) throws WebClientResponseException {
        return this.formatterStatusWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get formatter status
     * Get formatter status
     * <p><b>200</b> - Formatter status
     * <p><b>400</b> - Bad request
     * @param requestParameters The formatterStatus request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec formatterStatusWithResponseSpec(FormatterStatusRequest requestParameters) throws WebClientResponseException {
        return this.formatterStatusWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Get formatter status
     * Get formatter status
     * <p><b>200</b> - Formatter status
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;FormatterStatus&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec formatterStatusRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<FormatterStatus> localVarReturnType = new ParameterizedTypeReference<FormatterStatus>() {};
        return apiClient.invokeAPI("/formatter", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get formatter status
     * Get formatter status
     * <p><b>200</b> - Formatter status
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;FormatterStatus&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<FormatterStatus> formatterStatus(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<FormatterStatus> localVarReturnType = new ParameterizedTypeReference<FormatterStatus>() {};
        return formatterStatusRequestCreation(directory, workspace).bodyToFlux(localVarReturnType);
    }

    /**
     * Get formatter status
     * Get formatter status
     * <p><b>200</b> - Formatter status
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;List&lt;FormatterStatus&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<FormatterStatus>>> formatterStatusWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<FormatterStatus> localVarReturnType = new ParameterizedTypeReference<FormatterStatus>() {};
        return formatterStatusRequestCreation(directory, workspace).toEntityList(localVarReturnType);
    }

    /**
     * Get formatter status
     * Get formatter status
     * <p><b>200</b> - Formatter status
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec formatterStatusWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return formatterStatusRequestCreation(directory, workspace);
    }

    public class InstanceDisposeRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public InstanceDisposeRequest() {}

        public InstanceDisposeRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public InstanceDisposeRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public InstanceDisposeRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            InstanceDisposeRequest request = (InstanceDisposeRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * Dispose instance
     * Clean up and dispose the current OpenCode instance, releasing all resources.
     * <p><b>200</b> - Instance disposed
     * <p><b>400</b> - Bad request
     * @param requestParameters The instanceDispose request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> instanceDispose(InstanceDisposeRequest requestParameters) throws WebClientResponseException {
        return this.instanceDispose(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Dispose instance
     * Clean up and dispose the current OpenCode instance, releasing all resources.
     * <p><b>200</b> - Instance disposed
     * <p><b>400</b> - Bad request
     * @param requestParameters The instanceDispose request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> instanceDisposeWithHttpInfo(InstanceDisposeRequest requestParameters) throws WebClientResponseException {
        return this.instanceDisposeWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Dispose instance
     * Clean up and dispose the current OpenCode instance, releasing all resources.
     * <p><b>200</b> - Instance disposed
     * <p><b>400</b> - Bad request
     * @param requestParameters The instanceDispose request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec instanceDisposeWithResponseSpec(InstanceDisposeRequest requestParameters) throws WebClientResponseException {
        return this.instanceDisposeWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Dispose instance
     * Clean up and dispose the current OpenCode instance, releasing all resources.
     * <p><b>200</b> - Instance disposed
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec instanceDisposeRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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
        return apiClient.invokeAPI("/instance/dispose", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Dispose instance
     * Clean up and dispose the current OpenCode instance, releasing all resources.
     * <p><b>200</b> - Instance disposed
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> instanceDispose(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return instanceDisposeRequestCreation(directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Dispose instance
     * Clean up and dispose the current OpenCode instance, releasing all resources.
     * <p><b>200</b> - Instance disposed
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> instanceDisposeWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return instanceDisposeRequestCreation(directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Dispose instance
     * Clean up and dispose the current OpenCode instance, releasing all resources.
     * <p><b>200</b> - Instance disposed
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec instanceDisposeWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return instanceDisposeRequestCreation(directory, workspace);
    }

    public class LspStatusRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public LspStatusRequest() {}

        public LspStatusRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public LspStatusRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public LspStatusRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            LspStatusRequest request = (LspStatusRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * Get LSP status
     * Get LSP server status
     * <p><b>200</b> - LSP server status
     * <p><b>400</b> - Bad request
     * @param requestParameters The lspStatus request parameters as object
     * @return List&lt;LSPStatus&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<LSPStatus> lspStatus(LspStatusRequest requestParameters) throws WebClientResponseException {
        return this.lspStatus(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get LSP status
     * Get LSP server status
     * <p><b>200</b> - LSP server status
     * <p><b>400</b> - Bad request
     * @param requestParameters The lspStatus request parameters as object
     * @return ResponseEntity&lt;List&lt;LSPStatus&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<LSPStatus>>> lspStatusWithHttpInfo(LspStatusRequest requestParameters) throws WebClientResponseException {
        return this.lspStatusWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get LSP status
     * Get LSP server status
     * <p><b>200</b> - LSP server status
     * <p><b>400</b> - Bad request
     * @param requestParameters The lspStatus request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec lspStatusWithResponseSpec(LspStatusRequest requestParameters) throws WebClientResponseException {
        return this.lspStatusWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Get LSP status
     * Get LSP server status
     * <p><b>200</b> - LSP server status
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;LSPStatus&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec lspStatusRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<LSPStatus> localVarReturnType = new ParameterizedTypeReference<LSPStatus>() {};
        return apiClient.invokeAPI("/lsp", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get LSP status
     * Get LSP server status
     * <p><b>200</b> - LSP server status
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;LSPStatus&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<LSPStatus> lspStatus(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<LSPStatus> localVarReturnType = new ParameterizedTypeReference<LSPStatus>() {};
        return lspStatusRequestCreation(directory, workspace).bodyToFlux(localVarReturnType);
    }

    /**
     * Get LSP status
     * Get LSP server status
     * <p><b>200</b> - LSP server status
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;List&lt;LSPStatus&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<LSPStatus>>> lspStatusWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<LSPStatus> localVarReturnType = new ParameterizedTypeReference<LSPStatus>() {};
        return lspStatusRequestCreation(directory, workspace).toEntityList(localVarReturnType);
    }

    /**
     * Get LSP status
     * Get LSP server status
     * <p><b>200</b> - LSP server status
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec lspStatusWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return lspStatusRequestCreation(directory, workspace);
    }

    public class PathGetRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public PathGetRequest() {}

        public PathGetRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public PathGetRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public PathGetRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            PathGetRequest request = (PathGetRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * Get paths
     * Retrieve the current working directory and related path information for the OpenCode instance.
     * <p><b>200</b> - Path
     * <p><b>400</b> - Bad request
     * @param requestParameters The pathGet request parameters as object
     * @return Path
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Path> pathGet(PathGetRequest requestParameters) throws WebClientResponseException {
        return this.pathGet(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get paths
     * Retrieve the current working directory and related path information for the OpenCode instance.
     * <p><b>200</b> - Path
     * <p><b>400</b> - Bad request
     * @param requestParameters The pathGet request parameters as object
     * @return ResponseEntity&lt;Path&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Path>> pathGetWithHttpInfo(PathGetRequest requestParameters) throws WebClientResponseException {
        return this.pathGetWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get paths
     * Retrieve the current working directory and related path information for the OpenCode instance.
     * <p><b>200</b> - Path
     * <p><b>400</b> - Bad request
     * @param requestParameters The pathGet request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec pathGetWithResponseSpec(PathGetRequest requestParameters) throws WebClientResponseException {
        return this.pathGetWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Get paths
     * Retrieve the current working directory and related path information for the OpenCode instance.
     * <p><b>200</b> - Path
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Path
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec pathGetRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<Path> localVarReturnType = new ParameterizedTypeReference<Path>() {};
        return apiClient.invokeAPI("/path", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get paths
     * Retrieve the current working directory and related path information for the OpenCode instance.
     * <p><b>200</b> - Path
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Path
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Path> pathGet(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Path> localVarReturnType = new ParameterizedTypeReference<Path>() {};
        return pathGetRequestCreation(directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Get paths
     * Retrieve the current working directory and related path information for the OpenCode instance.
     * <p><b>200</b> - Path
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Path&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Path>> pathGetWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Path> localVarReturnType = new ParameterizedTypeReference<Path>() {};
        return pathGetRequestCreation(directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Get paths
     * Retrieve the current working directory and related path information for the OpenCode instance.
     * <p><b>200</b> - Path
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec pathGetWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return pathGetRequestCreation(directory, workspace);
    }

    public class VcsApplyRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable VcsApplyRequest vcsApplyRequest;

        public VcsApplyRequest() {}

        public VcsApplyRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable VcsApplyRequest vcsApplyRequest) {
            this.directory = directory;
            this.workspace = workspace;
            this.vcsApplyRequest = vcsApplyRequest;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public VcsApplyRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public VcsApplyRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable VcsApplyRequest vcsApplyRequest() {
            return this.vcsApplyRequest;
        }
        public VcsApplyRequest vcsApplyRequest(@jakarta.annotation.Nullable VcsApplyRequest vcsApplyRequest) {
            this.vcsApplyRequest = vcsApplyRequest;
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
            VcsApplyRequest request = (VcsApplyRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.vcsApplyRequest, request.vcsApplyRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace, vcsApplyRequest);
        }
    }

    /**
     * Apply VCS patch
     * Apply a raw patch to the current working tree.
     * <p><b>200</b> - VCS patch applied
     * <p><b>400</b> - VcsApplyError | InvalidRequestError
     * @param requestParameters The vcsApply request parameters as object
     * @return VcsApply200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<VcsApply200Response> vcsApply(VcsApplyRequest requestParameters) throws WebClientResponseException {
        return this.vcsApply(requestParameters.directory(), requestParameters.workspace(), requestParameters.vcsApplyRequest());
    }

    /**
     * Apply VCS patch
     * Apply a raw patch to the current working tree.
     * <p><b>200</b> - VCS patch applied
     * <p><b>400</b> - VcsApplyError | InvalidRequestError
     * @param requestParameters The vcsApply request parameters as object
     * @return ResponseEntity&lt;VcsApply200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<VcsApply200Response>> vcsApplyWithHttpInfo(VcsApplyRequest requestParameters) throws WebClientResponseException {
        return this.vcsApplyWithHttpInfo(requestParameters.directory(), requestParameters.workspace(), requestParameters.vcsApplyRequest());
    }

    /**
     * Apply VCS patch
     * Apply a raw patch to the current working tree.
     * <p><b>200</b> - VCS patch applied
     * <p><b>400</b> - VcsApplyError | InvalidRequestError
     * @param requestParameters The vcsApply request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec vcsApplyWithResponseSpec(VcsApplyRequest requestParameters) throws WebClientResponseException {
        return this.vcsApplyWithResponseSpec(requestParameters.directory(), requestParameters.workspace(), requestParameters.vcsApplyRequest());
    }


    /**
     * Apply VCS patch
     * Apply a raw patch to the current working tree.
     * <p><b>200</b> - VCS patch applied
     * <p><b>400</b> - VcsApplyError | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param vcsApplyRequest The vcsApplyRequest parameter
     * @return VcsApply200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec vcsApplyRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable VcsApplyRequest vcsApplyRequest) throws WebClientResponseException {
        Object postBody = vcsApplyRequest;
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

        ParameterizedTypeReference<VcsApply200Response> localVarReturnType = new ParameterizedTypeReference<VcsApply200Response>() {};
        return apiClient.invokeAPI("/vcs/apply", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Apply VCS patch
     * Apply a raw patch to the current working tree.
     * <p><b>200</b> - VCS patch applied
     * <p><b>400</b> - VcsApplyError | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param vcsApplyRequest The vcsApplyRequest parameter
     * @return VcsApply200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<VcsApply200Response> vcsApply(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable VcsApplyRequest vcsApplyRequest) throws WebClientResponseException {
        ParameterizedTypeReference<VcsApply200Response> localVarReturnType = new ParameterizedTypeReference<VcsApply200Response>() {};
        return vcsApplyRequestCreation(directory, workspace, vcsApplyRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Apply VCS patch
     * Apply a raw patch to the current working tree.
     * <p><b>200</b> - VCS patch applied
     * <p><b>400</b> - VcsApplyError | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param vcsApplyRequest The vcsApplyRequest parameter
     * @return ResponseEntity&lt;VcsApply200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<VcsApply200Response>> vcsApplyWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable VcsApplyRequest vcsApplyRequest) throws WebClientResponseException {
        ParameterizedTypeReference<VcsApply200Response> localVarReturnType = new ParameterizedTypeReference<VcsApply200Response>() {};
        return vcsApplyRequestCreation(directory, workspace, vcsApplyRequest).toEntity(localVarReturnType);
    }

    /**
     * Apply VCS patch
     * Apply a raw patch to the current working tree.
     * <p><b>200</b> - VCS patch applied
     * <p><b>400</b> - VcsApplyError | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param vcsApplyRequest The vcsApplyRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec vcsApplyWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable VcsApplyRequest vcsApplyRequest) throws WebClientResponseException {
        return vcsApplyRequestCreation(directory, workspace, vcsApplyRequest);
    }

    public class VcsDiffRequest {
        private @jakarta.annotation.Nonnull String mode;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable Integer context;

        public VcsDiffRequest() {}

        public VcsDiffRequest(@jakarta.annotation.Nonnull String mode, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Integer context) {
            this.mode = mode;
            this.directory = directory;
            this.workspace = workspace;
            this.context = context;
        }

        public @jakarta.annotation.Nonnull String mode() {
            return this.mode;
        }
        public VcsDiffRequest mode(@jakarta.annotation.Nonnull String mode) {
            this.mode = mode;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public VcsDiffRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public VcsDiffRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable Integer context() {
            return this.context;
        }
        public VcsDiffRequest context(@jakarta.annotation.Nullable Integer context) {
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
            VcsDiffRequest request = (VcsDiffRequest) o;
            return Objects.equals(this.mode, request.mode()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.context, request.context());
        }

        @Override
        public int hashCode() {
            return Objects.hash(mode, directory, workspace, context);
        }
    }

    /**
     * Get VCS diff
     * Retrieve the current git diff for the working tree or against the default branch.
     * <p><b>200</b> - VCS diff
     * <p><b>400</b> - Bad request
     * @param requestParameters The vcsDiff request parameters as object
     * @return List&lt;VcsFileDiff&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<VcsFileDiff> vcsDiff(VcsDiffRequest requestParameters) throws WebClientResponseException {
        return this.vcsDiff(requestParameters.mode(), requestParameters.directory(), requestParameters.workspace(), requestParameters.context());
    }

    /**
     * Get VCS diff
     * Retrieve the current git diff for the working tree or against the default branch.
     * <p><b>200</b> - VCS diff
     * <p><b>400</b> - Bad request
     * @param requestParameters The vcsDiff request parameters as object
     * @return ResponseEntity&lt;List&lt;VcsFileDiff&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<VcsFileDiff>>> vcsDiffWithHttpInfo(VcsDiffRequest requestParameters) throws WebClientResponseException {
        return this.vcsDiffWithHttpInfo(requestParameters.mode(), requestParameters.directory(), requestParameters.workspace(), requestParameters.context());
    }

    /**
     * Get VCS diff
     * Retrieve the current git diff for the working tree or against the default branch.
     * <p><b>200</b> - VCS diff
     * <p><b>400</b> - Bad request
     * @param requestParameters The vcsDiff request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec vcsDiffWithResponseSpec(VcsDiffRequest requestParameters) throws WebClientResponseException {
        return this.vcsDiffWithResponseSpec(requestParameters.mode(), requestParameters.directory(), requestParameters.workspace(), requestParameters.context());
    }


    /**
     * Get VCS diff
     * Retrieve the current git diff for the working tree or against the default branch.
     * <p><b>200</b> - VCS diff
     * <p><b>400</b> - Bad request
     * @param mode The mode parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param context The context parameter
     * @return List&lt;VcsFileDiff&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec vcsDiffRequestCreation(@jakarta.annotation.Nonnull String mode, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Integer context) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'mode' is set
        if (mode == null) {
            throw new WebClientResponseException("Missing the required parameter 'mode' when calling vcsDiff", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", directory));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", workspace));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "mode", mode));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "context", context));

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<VcsFileDiff> localVarReturnType = new ParameterizedTypeReference<VcsFileDiff>() {};
        return apiClient.invokeAPI("/vcs/diff", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get VCS diff
     * Retrieve the current git diff for the working tree or against the default branch.
     * <p><b>200</b> - VCS diff
     * <p><b>400</b> - Bad request
     * @param mode The mode parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param context The context parameter
     * @return List&lt;VcsFileDiff&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<VcsFileDiff> vcsDiff(@jakarta.annotation.Nonnull String mode, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Integer context) throws WebClientResponseException {
        ParameterizedTypeReference<VcsFileDiff> localVarReturnType = new ParameterizedTypeReference<VcsFileDiff>() {};
        return vcsDiffRequestCreation(mode, directory, workspace, context).bodyToFlux(localVarReturnType);
    }

    /**
     * Get VCS diff
     * Retrieve the current git diff for the working tree or against the default branch.
     * <p><b>200</b> - VCS diff
     * <p><b>400</b> - Bad request
     * @param mode The mode parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param context The context parameter
     * @return ResponseEntity&lt;List&lt;VcsFileDiff&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<VcsFileDiff>>> vcsDiffWithHttpInfo(@jakarta.annotation.Nonnull String mode, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Integer context) throws WebClientResponseException {
        ParameterizedTypeReference<VcsFileDiff> localVarReturnType = new ParameterizedTypeReference<VcsFileDiff>() {};
        return vcsDiffRequestCreation(mode, directory, workspace, context).toEntityList(localVarReturnType);
    }

    /**
     * Get VCS diff
     * Retrieve the current git diff for the working tree or against the default branch.
     * <p><b>200</b> - VCS diff
     * <p><b>400</b> - Bad request
     * @param mode The mode parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param context The context parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec vcsDiffWithResponseSpec(@jakarta.annotation.Nonnull String mode, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable Integer context) throws WebClientResponseException {
        return vcsDiffRequestCreation(mode, directory, workspace, context);
    }

    public class VcsDiffRawRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public VcsDiffRawRequest() {}

        public VcsDiffRawRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public VcsDiffRawRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public VcsDiffRawRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            VcsDiffRawRequest request = (VcsDiffRawRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * Get raw VCS diff
     * Retrieve a raw patch for current uncommitted changes.
     * <p><b>200</b> - Raw VCS diff
     * <p><b>400</b> - Bad request
     * @param requestParameters The vcsDiffRaw request parameters as object
     * @return String
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<String> vcsDiffRaw(VcsDiffRawRequest requestParameters) throws WebClientResponseException {
        return this.vcsDiffRaw(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get raw VCS diff
     * Retrieve a raw patch for current uncommitted changes.
     * <p><b>200</b> - Raw VCS diff
     * <p><b>400</b> - Bad request
     * @param requestParameters The vcsDiffRaw request parameters as object
     * @return ResponseEntity&lt;String&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<String>> vcsDiffRawWithHttpInfo(VcsDiffRawRequest requestParameters) throws WebClientResponseException {
        return this.vcsDiffRawWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get raw VCS diff
     * Retrieve a raw patch for current uncommitted changes.
     * <p><b>200</b> - Raw VCS diff
     * <p><b>400</b> - Bad request
     * @param requestParameters The vcsDiffRaw request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec vcsDiffRawWithResponseSpec(VcsDiffRawRequest requestParameters) throws WebClientResponseException {
        return this.vcsDiffRawWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Get raw VCS diff
     * Retrieve a raw patch for current uncommitted changes.
     * <p><b>200</b> - Raw VCS diff
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return String
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec vcsDiffRawRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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
            "text/x-diff; charset=utf-8", "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<String> localVarReturnType = new ParameterizedTypeReference<String>() {};
        return apiClient.invokeAPI("/vcs/diff/raw", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get raw VCS diff
     * Retrieve a raw patch for current uncommitted changes.
     * <p><b>200</b> - Raw VCS diff
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return String
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<String> vcsDiffRaw(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<String> localVarReturnType = new ParameterizedTypeReference<String>() {};
        return vcsDiffRawRequestCreation(directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Get raw VCS diff
     * Retrieve a raw patch for current uncommitted changes.
     * <p><b>200</b> - Raw VCS diff
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;String&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<String>> vcsDiffRawWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<String> localVarReturnType = new ParameterizedTypeReference<String>() {};
        return vcsDiffRawRequestCreation(directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Get raw VCS diff
     * Retrieve a raw patch for current uncommitted changes.
     * <p><b>200</b> - Raw VCS diff
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec vcsDiffRawWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return vcsDiffRawRequestCreation(directory, workspace);
    }

    public class VcsGetRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public VcsGetRequest() {}

        public VcsGetRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public VcsGetRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public VcsGetRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            VcsGetRequest request = (VcsGetRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * Get VCS info
     * Retrieve version control system (VCS) information for the current project, such as git branch.
     * <p><b>200</b> - VCS info
     * <p><b>400</b> - Bad request
     * @param requestParameters The vcsGet request parameters as object
     * @return VcsInfo
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<VcsInfo> vcsGet(VcsGetRequest requestParameters) throws WebClientResponseException {
        return this.vcsGet(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get VCS info
     * Retrieve version control system (VCS) information for the current project, such as git branch.
     * <p><b>200</b> - VCS info
     * <p><b>400</b> - Bad request
     * @param requestParameters The vcsGet request parameters as object
     * @return ResponseEntity&lt;VcsInfo&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<VcsInfo>> vcsGetWithHttpInfo(VcsGetRequest requestParameters) throws WebClientResponseException {
        return this.vcsGetWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get VCS info
     * Retrieve version control system (VCS) information for the current project, such as git branch.
     * <p><b>200</b> - VCS info
     * <p><b>400</b> - Bad request
     * @param requestParameters The vcsGet request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec vcsGetWithResponseSpec(VcsGetRequest requestParameters) throws WebClientResponseException {
        return this.vcsGetWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Get VCS info
     * Retrieve version control system (VCS) information for the current project, such as git branch.
     * <p><b>200</b> - VCS info
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return VcsInfo
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec vcsGetRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<VcsInfo> localVarReturnType = new ParameterizedTypeReference<VcsInfo>() {};
        return apiClient.invokeAPI("/vcs", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get VCS info
     * Retrieve version control system (VCS) information for the current project, such as git branch.
     * <p><b>200</b> - VCS info
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return VcsInfo
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<VcsInfo> vcsGet(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<VcsInfo> localVarReturnType = new ParameterizedTypeReference<VcsInfo>() {};
        return vcsGetRequestCreation(directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Get VCS info
     * Retrieve version control system (VCS) information for the current project, such as git branch.
     * <p><b>200</b> - VCS info
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;VcsInfo&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<VcsInfo>> vcsGetWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<VcsInfo> localVarReturnType = new ParameterizedTypeReference<VcsInfo>() {};
        return vcsGetRequestCreation(directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Get VCS info
     * Retrieve version control system (VCS) information for the current project, such as git branch.
     * <p><b>200</b> - VCS info
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec vcsGetWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return vcsGetRequestCreation(directory, workspace);
    }

    public class VcsStatusRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public VcsStatusRequest() {}

        public VcsStatusRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public VcsStatusRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public VcsStatusRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            VcsStatusRequest request = (VcsStatusRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * Get VCS status
     * Retrieve changed files in the current working tree without patches.
     * <p><b>200</b> - VCS status
     * <p><b>400</b> - Bad request
     * @param requestParameters The vcsStatus request parameters as object
     * @return List&lt;VcsFileStatus&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<VcsFileStatus> vcsStatus(VcsStatusRequest requestParameters) throws WebClientResponseException {
        return this.vcsStatus(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get VCS status
     * Retrieve changed files in the current working tree without patches.
     * <p><b>200</b> - VCS status
     * <p><b>400</b> - Bad request
     * @param requestParameters The vcsStatus request parameters as object
     * @return ResponseEntity&lt;List&lt;VcsFileStatus&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<VcsFileStatus>>> vcsStatusWithHttpInfo(VcsStatusRequest requestParameters) throws WebClientResponseException {
        return this.vcsStatusWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get VCS status
     * Retrieve changed files in the current working tree without patches.
     * <p><b>200</b> - VCS status
     * <p><b>400</b> - Bad request
     * @param requestParameters The vcsStatus request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec vcsStatusWithResponseSpec(VcsStatusRequest requestParameters) throws WebClientResponseException {
        return this.vcsStatusWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Get VCS status
     * Retrieve changed files in the current working tree without patches.
     * <p><b>200</b> - VCS status
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;VcsFileStatus&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec vcsStatusRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<VcsFileStatus> localVarReturnType = new ParameterizedTypeReference<VcsFileStatus>() {};
        return apiClient.invokeAPI("/vcs/status", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get VCS status
     * Retrieve changed files in the current working tree without patches.
     * <p><b>200</b> - VCS status
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;VcsFileStatus&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<VcsFileStatus> vcsStatus(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<VcsFileStatus> localVarReturnType = new ParameterizedTypeReference<VcsFileStatus>() {};
        return vcsStatusRequestCreation(directory, workspace).bodyToFlux(localVarReturnType);
    }

    /**
     * Get VCS status
     * Retrieve changed files in the current working tree without patches.
     * <p><b>200</b> - VCS status
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;List&lt;VcsFileStatus&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<VcsFileStatus>>> vcsStatusWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<VcsFileStatus> localVarReturnType = new ParameterizedTypeReference<VcsFileStatus>() {};
        return vcsStatusRequestCreation(directory, workspace).toEntityList(localVarReturnType);
    }

    /**
     * Get VCS status
     * Retrieve changed files in the current working tree without patches.
     * <p><b>200</b> - VCS status
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec vcsStatusWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return vcsStatusRequestCreation(directory, workspace);
    }
}
