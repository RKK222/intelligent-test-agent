package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.AuthSet400Response;
import com.example.opencode.sdk.model.BadRequestError;
import java.math.BigDecimal;
import com.example.opencode.sdk.model.ConsoleState;
import com.example.opencode.sdk.model.EffectHttpApiErrorInternalServerError;
import com.example.opencode.sdk.model.ExperimentalConsoleListOrgs200Response;
import com.example.opencode.sdk.model.ExperimentalConsoleSwitchOrgRequest;
import com.example.opencode.sdk.model.ExperimentalSessionListRootsParameter;
import com.example.opencode.sdk.model.GlobalSession;
import com.example.opencode.sdk.model.McpResource;
import com.example.opencode.sdk.model.ToolListItem;
import com.example.opencode.sdk.model.Worktree;
import com.example.opencode.sdk.model.WorktreeCreateInput;
import com.example.opencode.sdk.model.WorktreeList400Response;
import com.example.opencode.sdk.model.WorktreeRemoveInput;
import com.example.opencode.sdk.model.WorktreeResetInput;

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
public class ExperimentalApi {
    private ApiClient apiClient;

    public ExperimentalApi() {
        this(new ApiClient());
    }

    public ExperimentalApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public class ExperimentalConsoleGetRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public ExperimentalConsoleGetRequest() {}

        public ExperimentalConsoleGetRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public ExperimentalConsoleGetRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public ExperimentalConsoleGetRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            ExperimentalConsoleGetRequest request = (ExperimentalConsoleGetRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * Get active Console provider metadata
     * Get the active Console org name and the set of provider IDs managed by that Console org.
     * <p><b>200</b> - Active Console provider metadata
     * <p><b>400</b> - Bad request
     * <p><b>500</b> - InternalServerError
     * @param requestParameters The experimentalConsoleGet request parameters as object
     * @return ConsoleState
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ConsoleState> experimentalConsoleGet(ExperimentalConsoleGetRequest requestParameters) throws WebClientResponseException {
        return this.experimentalConsoleGet(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get active Console provider metadata
     * Get the active Console org name and the set of provider IDs managed by that Console org.
     * <p><b>200</b> - Active Console provider metadata
     * <p><b>400</b> - Bad request
     * <p><b>500</b> - InternalServerError
     * @param requestParameters The experimentalConsoleGet request parameters as object
     * @return ResponseEntity&lt;ConsoleState&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ConsoleState>> experimentalConsoleGetWithHttpInfo(ExperimentalConsoleGetRequest requestParameters) throws WebClientResponseException {
        return this.experimentalConsoleGetWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get active Console provider metadata
     * Get the active Console org name and the set of provider IDs managed by that Console org.
     * <p><b>200</b> - Active Console provider metadata
     * <p><b>400</b> - Bad request
     * <p><b>500</b> - InternalServerError
     * @param requestParameters The experimentalConsoleGet request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalConsoleGetWithResponseSpec(ExperimentalConsoleGetRequest requestParameters) throws WebClientResponseException {
        return this.experimentalConsoleGetWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Get active Console provider metadata
     * Get the active Console org name and the set of provider IDs managed by that Console org.
     * <p><b>200</b> - Active Console provider metadata
     * <p><b>400</b> - Bad request
     * <p><b>500</b> - InternalServerError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ConsoleState
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalConsoleGetRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<ConsoleState> localVarReturnType = new ParameterizedTypeReference<ConsoleState>() {};
        return apiClient.invokeAPI("/experimental/console", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get active Console provider metadata
     * Get the active Console org name and the set of provider IDs managed by that Console org.
     * <p><b>200</b> - Active Console provider metadata
     * <p><b>400</b> - Bad request
     * <p><b>500</b> - InternalServerError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ConsoleState
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ConsoleState> experimentalConsoleGet(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<ConsoleState> localVarReturnType = new ParameterizedTypeReference<ConsoleState>() {};
        return experimentalConsoleGetRequestCreation(directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Get active Console provider metadata
     * Get the active Console org name and the set of provider IDs managed by that Console org.
     * <p><b>200</b> - Active Console provider metadata
     * <p><b>400</b> - Bad request
     * <p><b>500</b> - InternalServerError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;ConsoleState&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ConsoleState>> experimentalConsoleGetWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<ConsoleState> localVarReturnType = new ParameterizedTypeReference<ConsoleState>() {};
        return experimentalConsoleGetRequestCreation(directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Get active Console provider metadata
     * Get the active Console org name and the set of provider IDs managed by that Console org.
     * <p><b>200</b> - Active Console provider metadata
     * <p><b>400</b> - Bad request
     * <p><b>500</b> - InternalServerError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalConsoleGetWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return experimentalConsoleGetRequestCreation(directory, workspace);
    }

    public class ExperimentalConsoleListOrgsRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public ExperimentalConsoleListOrgsRequest() {}

        public ExperimentalConsoleListOrgsRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public ExperimentalConsoleListOrgsRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public ExperimentalConsoleListOrgsRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            ExperimentalConsoleListOrgsRequest request = (ExperimentalConsoleListOrgsRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * List switchable Console orgs
     * Get the available Console orgs across logged-in accounts, including the current active org.
     * <p><b>200</b> - Switchable Console orgs
     * <p><b>400</b> - Bad request
     * <p><b>500</b> - InternalServerError
     * @param requestParameters The experimentalConsoleListOrgs request parameters as object
     * @return ExperimentalConsoleListOrgs200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ExperimentalConsoleListOrgs200Response> experimentalConsoleListOrgs(ExperimentalConsoleListOrgsRequest requestParameters) throws WebClientResponseException {
        return this.experimentalConsoleListOrgs(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List switchable Console orgs
     * Get the available Console orgs across logged-in accounts, including the current active org.
     * <p><b>200</b> - Switchable Console orgs
     * <p><b>400</b> - Bad request
     * <p><b>500</b> - InternalServerError
     * @param requestParameters The experimentalConsoleListOrgs request parameters as object
     * @return ResponseEntity&lt;ExperimentalConsoleListOrgs200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ExperimentalConsoleListOrgs200Response>> experimentalConsoleListOrgsWithHttpInfo(ExperimentalConsoleListOrgsRequest requestParameters) throws WebClientResponseException {
        return this.experimentalConsoleListOrgsWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List switchable Console orgs
     * Get the available Console orgs across logged-in accounts, including the current active org.
     * <p><b>200</b> - Switchable Console orgs
     * <p><b>400</b> - Bad request
     * <p><b>500</b> - InternalServerError
     * @param requestParameters The experimentalConsoleListOrgs request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalConsoleListOrgsWithResponseSpec(ExperimentalConsoleListOrgsRequest requestParameters) throws WebClientResponseException {
        return this.experimentalConsoleListOrgsWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * List switchable Console orgs
     * Get the available Console orgs across logged-in accounts, including the current active org.
     * <p><b>200</b> - Switchable Console orgs
     * <p><b>400</b> - Bad request
     * <p><b>500</b> - InternalServerError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ExperimentalConsoleListOrgs200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalConsoleListOrgsRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<ExperimentalConsoleListOrgs200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalConsoleListOrgs200Response>() {};
        return apiClient.invokeAPI("/experimental/console/orgs", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List switchable Console orgs
     * Get the available Console orgs across logged-in accounts, including the current active org.
     * <p><b>200</b> - Switchable Console orgs
     * <p><b>400</b> - Bad request
     * <p><b>500</b> - InternalServerError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ExperimentalConsoleListOrgs200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ExperimentalConsoleListOrgs200Response> experimentalConsoleListOrgs(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<ExperimentalConsoleListOrgs200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalConsoleListOrgs200Response>() {};
        return experimentalConsoleListOrgsRequestCreation(directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * List switchable Console orgs
     * Get the available Console orgs across logged-in accounts, including the current active org.
     * <p><b>200</b> - Switchable Console orgs
     * <p><b>400</b> - Bad request
     * <p><b>500</b> - InternalServerError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;ExperimentalConsoleListOrgs200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ExperimentalConsoleListOrgs200Response>> experimentalConsoleListOrgsWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<ExperimentalConsoleListOrgs200Response> localVarReturnType = new ParameterizedTypeReference<ExperimentalConsoleListOrgs200Response>() {};
        return experimentalConsoleListOrgsRequestCreation(directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * List switchable Console orgs
     * Get the available Console orgs across logged-in accounts, including the current active org.
     * <p><b>200</b> - Switchable Console orgs
     * <p><b>400</b> - Bad request
     * <p><b>500</b> - InternalServerError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalConsoleListOrgsWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return experimentalConsoleListOrgsRequestCreation(directory, workspace);
    }

    public class ExperimentalConsoleSwitchOrgRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable ExperimentalConsoleSwitchOrgRequest experimentalConsoleSwitchOrgRequest;

        public ExperimentalConsoleSwitchOrgRequest() {}

        public ExperimentalConsoleSwitchOrgRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ExperimentalConsoleSwitchOrgRequest experimentalConsoleSwitchOrgRequest) {
            this.directory = directory;
            this.workspace = workspace;
            this.experimentalConsoleSwitchOrgRequest = experimentalConsoleSwitchOrgRequest;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public ExperimentalConsoleSwitchOrgRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public ExperimentalConsoleSwitchOrgRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable ExperimentalConsoleSwitchOrgRequest experimentalConsoleSwitchOrgRequest() {
            return this.experimentalConsoleSwitchOrgRequest;
        }
        public ExperimentalConsoleSwitchOrgRequest experimentalConsoleSwitchOrgRequest(@jakarta.annotation.Nullable ExperimentalConsoleSwitchOrgRequest experimentalConsoleSwitchOrgRequest) {
            this.experimentalConsoleSwitchOrgRequest = experimentalConsoleSwitchOrgRequest;
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
            ExperimentalConsoleSwitchOrgRequest request = (ExperimentalConsoleSwitchOrgRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.experimentalConsoleSwitchOrgRequest, request.experimentalConsoleSwitchOrgRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace, experimentalConsoleSwitchOrgRequest);
        }
    }

    /**
     * Switch active Console org
     * Persist a new active Console account/org selection for the current local OpenCode state.
     * <p><b>200</b> - Switch success
     * @param requestParameters The experimentalConsoleSwitchOrg request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> experimentalConsoleSwitchOrg(ExperimentalConsoleSwitchOrgRequest requestParameters) throws WebClientResponseException {
        return this.experimentalConsoleSwitchOrg(requestParameters.directory(), requestParameters.workspace(), requestParameters.experimentalConsoleSwitchOrgRequest());
    }

    /**
     * Switch active Console org
     * Persist a new active Console account/org selection for the current local OpenCode state.
     * <p><b>200</b> - Switch success
     * @param requestParameters The experimentalConsoleSwitchOrg request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> experimentalConsoleSwitchOrgWithHttpInfo(ExperimentalConsoleSwitchOrgRequest requestParameters) throws WebClientResponseException {
        return this.experimentalConsoleSwitchOrgWithHttpInfo(requestParameters.directory(), requestParameters.workspace(), requestParameters.experimentalConsoleSwitchOrgRequest());
    }

    /**
     * Switch active Console org
     * Persist a new active Console account/org selection for the current local OpenCode state.
     * <p><b>200</b> - Switch success
     * @param requestParameters The experimentalConsoleSwitchOrg request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalConsoleSwitchOrgWithResponseSpec(ExperimentalConsoleSwitchOrgRequest requestParameters) throws WebClientResponseException {
        return this.experimentalConsoleSwitchOrgWithResponseSpec(requestParameters.directory(), requestParameters.workspace(), requestParameters.experimentalConsoleSwitchOrgRequest());
    }


    /**
     * Switch active Console org
     * Persist a new active Console account/org selection for the current local OpenCode state.
     * <p><b>200</b> - Switch success
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param experimentalConsoleSwitchOrgRequest The experimentalConsoleSwitchOrgRequest parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalConsoleSwitchOrgRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ExperimentalConsoleSwitchOrgRequest experimentalConsoleSwitchOrgRequest) throws WebClientResponseException {
        Object postBody = experimentalConsoleSwitchOrgRequest;
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
        return apiClient.invokeAPI("/experimental/console/switch", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Switch active Console org
     * Persist a new active Console account/org selection for the current local OpenCode state.
     * <p><b>200</b> - Switch success
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param experimentalConsoleSwitchOrgRequest The experimentalConsoleSwitchOrgRequest parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> experimentalConsoleSwitchOrg(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ExperimentalConsoleSwitchOrgRequest experimentalConsoleSwitchOrgRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return experimentalConsoleSwitchOrgRequestCreation(directory, workspace, experimentalConsoleSwitchOrgRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Switch active Console org
     * Persist a new active Console account/org selection for the current local OpenCode state.
     * <p><b>200</b> - Switch success
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param experimentalConsoleSwitchOrgRequest The experimentalConsoleSwitchOrgRequest parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> experimentalConsoleSwitchOrgWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ExperimentalConsoleSwitchOrgRequest experimentalConsoleSwitchOrgRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return experimentalConsoleSwitchOrgRequestCreation(directory, workspace, experimentalConsoleSwitchOrgRequest).toEntity(localVarReturnType);
    }

    /**
     * Switch active Console org
     * Persist a new active Console account/org selection for the current local OpenCode state.
     * <p><b>200</b> - Switch success
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param experimentalConsoleSwitchOrgRequest The experimentalConsoleSwitchOrgRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalConsoleSwitchOrgWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ExperimentalConsoleSwitchOrgRequest experimentalConsoleSwitchOrgRequest) throws WebClientResponseException {
        return experimentalConsoleSwitchOrgRequestCreation(directory, workspace, experimentalConsoleSwitchOrgRequest);
    }

    public class ExperimentalResourceListRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public ExperimentalResourceListRequest() {}

        public ExperimentalResourceListRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public ExperimentalResourceListRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public ExperimentalResourceListRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            ExperimentalResourceListRequest request = (ExperimentalResourceListRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * Get MCP resources
     * Get all available MCP resources from connected servers. Optionally filter by name.
     * <p><b>200</b> - MCP resources
     * <p><b>400</b> - Bad request
     * @param requestParameters The experimentalResourceList request parameters as object
     * @return Map&lt;String, McpResource&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Map<String, McpResource>> experimentalResourceList(ExperimentalResourceListRequest requestParameters) throws WebClientResponseException {
        return this.experimentalResourceList(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get MCP resources
     * Get all available MCP resources from connected servers. Optionally filter by name.
     * <p><b>200</b> - MCP resources
     * <p><b>400</b> - Bad request
     * @param requestParameters The experimentalResourceList request parameters as object
     * @return ResponseEntity&lt;Map&lt;String, McpResource&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Map<String, McpResource>>> experimentalResourceListWithHttpInfo(ExperimentalResourceListRequest requestParameters) throws WebClientResponseException {
        return this.experimentalResourceListWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get MCP resources
     * Get all available MCP resources from connected servers. Optionally filter by name.
     * <p><b>200</b> - MCP resources
     * <p><b>400</b> - Bad request
     * @param requestParameters The experimentalResourceList request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalResourceListWithResponseSpec(ExperimentalResourceListRequest requestParameters) throws WebClientResponseException {
        return this.experimentalResourceListWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Get MCP resources
     * Get all available MCP resources from connected servers. Optionally filter by name.
     * <p><b>200</b> - MCP resources
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Map&lt;String, McpResource&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalResourceListRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<Map<String, McpResource>> localVarReturnType = new ParameterizedTypeReference<Map<String, McpResource>>() {};
        return apiClient.invokeAPI("/experimental/resource", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get MCP resources
     * Get all available MCP resources from connected servers. Optionally filter by name.
     * <p><b>200</b> - MCP resources
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Map&lt;String, McpResource&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Map<String, McpResource>> experimentalResourceList(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Map<String, McpResource>> localVarReturnType = new ParameterizedTypeReference<Map<String, McpResource>>() {};
        return experimentalResourceListRequestCreation(directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Get MCP resources
     * Get all available MCP resources from connected servers. Optionally filter by name.
     * <p><b>200</b> - MCP resources
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Map&lt;String, McpResource&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Map<String, McpResource>>> experimentalResourceListWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Map<String, McpResource>> localVarReturnType = new ParameterizedTypeReference<Map<String, McpResource>>() {};
        return experimentalResourceListRequestCreation(directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Get MCP resources
     * Get all available MCP resources from connected servers. Optionally filter by name.
     * <p><b>200</b> - MCP resources
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalResourceListWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return experimentalResourceListRequestCreation(directory, workspace);
    }

    public class ExperimentalSessionBackgroundRequest {
        private @jakarta.annotation.Nonnull String sessionID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public ExperimentalSessionBackgroundRequest() {}

        public ExperimentalSessionBackgroundRequest(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.sessionID = sessionID;
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nonnull String sessionID() {
            return this.sessionID;
        }
        public ExperimentalSessionBackgroundRequest sessionID(@jakarta.annotation.Nonnull String sessionID) {
            this.sessionID = sessionID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public ExperimentalSessionBackgroundRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public ExperimentalSessionBackgroundRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            ExperimentalSessionBackgroundRequest request = (ExperimentalSessionBackgroundRequest) o;
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
     * Background subagents
     * Detach any synchronous subagents currently blocking the session and continue them in the background.
     * <p><b>200</b> - Backgrounded subagents
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The experimentalSessionBackground request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> experimentalSessionBackground(ExperimentalSessionBackgroundRequest requestParameters) throws WebClientResponseException {
        return this.experimentalSessionBackground(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Background subagents
     * Detach any synchronous subagents currently blocking the session and continue them in the background.
     * <p><b>200</b> - Backgrounded subagents
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The experimentalSessionBackground request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> experimentalSessionBackgroundWithHttpInfo(ExperimentalSessionBackgroundRequest requestParameters) throws WebClientResponseException {
        return this.experimentalSessionBackgroundWithHttpInfo(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Background subagents
     * Detach any synchronous subagents currently blocking the session and continue them in the background.
     * <p><b>200</b> - Backgrounded subagents
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The experimentalSessionBackground request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalSessionBackgroundWithResponseSpec(ExperimentalSessionBackgroundRequest requestParameters) throws WebClientResponseException {
        return this.experimentalSessionBackgroundWithResponseSpec(requestParameters.sessionID(), requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Background subagents
     * Detach any synchronous subagents currently blocking the session and continue them in the background.
     * <p><b>200</b> - Backgrounded subagents
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalSessionBackgroundRequestCreation(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'sessionID' is set
        if (sessionID == null) {
            throw new WebClientResponseException("Missing the required parameter 'sessionID' when calling experimentalSessionBackground", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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
        return apiClient.invokeAPI("/experimental/session/{sessionID}/background", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Background subagents
     * Detach any synchronous subagents currently blocking the session and continue them in the background.
     * <p><b>200</b> - Backgrounded subagents
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> experimentalSessionBackground(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return experimentalSessionBackgroundRequestCreation(sessionID, directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Background subagents
     * Detach any synchronous subagents currently blocking the session and continue them in the background.
     * <p><b>200</b> - Backgrounded subagents
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> experimentalSessionBackgroundWithHttpInfo(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return experimentalSessionBackgroundRequestCreation(sessionID, directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Background subagents
     * Detach any synchronous subagents currently blocking the session and continue them in the background.
     * <p><b>200</b> - Backgrounded subagents
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param sessionID The sessionID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalSessionBackgroundWithResponseSpec(@jakarta.annotation.Nonnull String sessionID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return experimentalSessionBackgroundRequestCreation(sessionID, directory, workspace);
    }

    public class ExperimentalSessionListRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable ExperimentalSessionListRootsParameter roots;
        private @jakarta.annotation.Nullable BigDecimal start;
        private @jakarta.annotation.Nullable BigDecimal cursor;
        private @jakarta.annotation.Nullable String search;
        private @jakarta.annotation.Nullable BigDecimal limit;
        private @jakarta.annotation.Nullable ExperimentalSessionListRootsParameter archived;

        public ExperimentalSessionListRequest() {}

        public ExperimentalSessionListRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ExperimentalSessionListRootsParameter roots, @jakarta.annotation.Nullable BigDecimal start, @jakarta.annotation.Nullable BigDecimal cursor, @jakarta.annotation.Nullable String search, @jakarta.annotation.Nullable BigDecimal limit, @jakarta.annotation.Nullable ExperimentalSessionListRootsParameter archived) {
            this.directory = directory;
            this.workspace = workspace;
            this.roots = roots;
            this.start = start;
            this.cursor = cursor;
            this.search = search;
            this.limit = limit;
            this.archived = archived;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public ExperimentalSessionListRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public ExperimentalSessionListRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable ExperimentalSessionListRootsParameter roots() {
            return this.roots;
        }
        public ExperimentalSessionListRequest roots(@jakarta.annotation.Nullable ExperimentalSessionListRootsParameter roots) {
            this.roots = roots;
            return this;
        }

        public @jakarta.annotation.Nullable BigDecimal start() {
            return this.start;
        }
        public ExperimentalSessionListRequest start(@jakarta.annotation.Nullable BigDecimal start) {
            this.start = start;
            return this;
        }

        public @jakarta.annotation.Nullable BigDecimal cursor() {
            return this.cursor;
        }
        public ExperimentalSessionListRequest cursor(@jakarta.annotation.Nullable BigDecimal cursor) {
            this.cursor = cursor;
            return this;
        }

        public @jakarta.annotation.Nullable String search() {
            return this.search;
        }
        public ExperimentalSessionListRequest search(@jakarta.annotation.Nullable String search) {
            this.search = search;
            return this;
        }

        public @jakarta.annotation.Nullable BigDecimal limit() {
            return this.limit;
        }
        public ExperimentalSessionListRequest limit(@jakarta.annotation.Nullable BigDecimal limit) {
            this.limit = limit;
            return this;
        }

        public @jakarta.annotation.Nullable ExperimentalSessionListRootsParameter archived() {
            return this.archived;
        }
        public ExperimentalSessionListRequest archived(@jakarta.annotation.Nullable ExperimentalSessionListRootsParameter archived) {
            this.archived = archived;
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
            ExperimentalSessionListRequest request = (ExperimentalSessionListRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.roots, request.roots()) &&
                Objects.equals(this.start, request.start()) &&
                Objects.equals(this.cursor, request.cursor()) &&
                Objects.equals(this.search, request.search()) &&
                Objects.equals(this.limit, request.limit()) &&
                Objects.equals(this.archived, request.archived());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace, roots, start, cursor, search, limit, archived);
        }
    }

    /**
     * List sessions
     * Get a list of all OpenCode sessions across projects, sorted by most recently updated. Archived sessions are excluded by default.
     * <p><b>200</b> - List of sessions
     * <p><b>400</b> - Bad request
     * @param requestParameters The experimentalSessionList request parameters as object
     * @return List&lt;GlobalSession&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<GlobalSession> experimentalSessionList(ExperimentalSessionListRequest requestParameters) throws WebClientResponseException {
        return this.experimentalSessionList(requestParameters.directory(), requestParameters.workspace(), requestParameters.roots(), requestParameters.start(), requestParameters.cursor(), requestParameters.search(), requestParameters.limit(), requestParameters.archived());
    }

    /**
     * List sessions
     * Get a list of all OpenCode sessions across projects, sorted by most recently updated. Archived sessions are excluded by default.
     * <p><b>200</b> - List of sessions
     * <p><b>400</b> - Bad request
     * @param requestParameters The experimentalSessionList request parameters as object
     * @return ResponseEntity&lt;List&lt;GlobalSession&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<GlobalSession>>> experimentalSessionListWithHttpInfo(ExperimentalSessionListRequest requestParameters) throws WebClientResponseException {
        return this.experimentalSessionListWithHttpInfo(requestParameters.directory(), requestParameters.workspace(), requestParameters.roots(), requestParameters.start(), requestParameters.cursor(), requestParameters.search(), requestParameters.limit(), requestParameters.archived());
    }

    /**
     * List sessions
     * Get a list of all OpenCode sessions across projects, sorted by most recently updated. Archived sessions are excluded by default.
     * <p><b>200</b> - List of sessions
     * <p><b>400</b> - Bad request
     * @param requestParameters The experimentalSessionList request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalSessionListWithResponseSpec(ExperimentalSessionListRequest requestParameters) throws WebClientResponseException {
        return this.experimentalSessionListWithResponseSpec(requestParameters.directory(), requestParameters.workspace(), requestParameters.roots(), requestParameters.start(), requestParameters.cursor(), requestParameters.search(), requestParameters.limit(), requestParameters.archived());
    }


    /**
     * List sessions
     * Get a list of all OpenCode sessions across projects, sorted by most recently updated. Archived sessions are excluded by default.
     * <p><b>200</b> - List of sessions
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param roots The roots parameter
     * @param start The start parameter
     * @param cursor The cursor parameter
     * @param search The search parameter
     * @param limit The limit parameter
     * @param archived The archived parameter
     * @return List&lt;GlobalSession&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec experimentalSessionListRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ExperimentalSessionListRootsParameter roots, @jakarta.annotation.Nullable BigDecimal start, @jakarta.annotation.Nullable BigDecimal cursor, @jakarta.annotation.Nullable String search, @jakarta.annotation.Nullable BigDecimal limit, @jakarta.annotation.Nullable ExperimentalSessionListRootsParameter archived) throws WebClientResponseException {
        Object postBody = null;
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", directory));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", workspace));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "roots", roots));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "start", start));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "cursor", cursor));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "search", search));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "limit", limit));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "archived", archived));

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<GlobalSession> localVarReturnType = new ParameterizedTypeReference<GlobalSession>() {};
        return apiClient.invokeAPI("/experimental/session", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List sessions
     * Get a list of all OpenCode sessions across projects, sorted by most recently updated. Archived sessions are excluded by default.
     * <p><b>200</b> - List of sessions
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param roots The roots parameter
     * @param start The start parameter
     * @param cursor The cursor parameter
     * @param search The search parameter
     * @param limit The limit parameter
     * @param archived The archived parameter
     * @return List&lt;GlobalSession&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<GlobalSession> experimentalSessionList(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ExperimentalSessionListRootsParameter roots, @jakarta.annotation.Nullable BigDecimal start, @jakarta.annotation.Nullable BigDecimal cursor, @jakarta.annotation.Nullable String search, @jakarta.annotation.Nullable BigDecimal limit, @jakarta.annotation.Nullable ExperimentalSessionListRootsParameter archived) throws WebClientResponseException {
        ParameterizedTypeReference<GlobalSession> localVarReturnType = new ParameterizedTypeReference<GlobalSession>() {};
        return experimentalSessionListRequestCreation(directory, workspace, roots, start, cursor, search, limit, archived).bodyToFlux(localVarReturnType);
    }

    /**
     * List sessions
     * Get a list of all OpenCode sessions across projects, sorted by most recently updated. Archived sessions are excluded by default.
     * <p><b>200</b> - List of sessions
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param roots The roots parameter
     * @param start The start parameter
     * @param cursor The cursor parameter
     * @param search The search parameter
     * @param limit The limit parameter
     * @param archived The archived parameter
     * @return ResponseEntity&lt;List&lt;GlobalSession&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<GlobalSession>>> experimentalSessionListWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ExperimentalSessionListRootsParameter roots, @jakarta.annotation.Nullable BigDecimal start, @jakarta.annotation.Nullable BigDecimal cursor, @jakarta.annotation.Nullable String search, @jakarta.annotation.Nullable BigDecimal limit, @jakarta.annotation.Nullable ExperimentalSessionListRootsParameter archived) throws WebClientResponseException {
        ParameterizedTypeReference<GlobalSession> localVarReturnType = new ParameterizedTypeReference<GlobalSession>() {};
        return experimentalSessionListRequestCreation(directory, workspace, roots, start, cursor, search, limit, archived).toEntityList(localVarReturnType);
    }

    /**
     * List sessions
     * Get a list of all OpenCode sessions across projects, sorted by most recently updated. Archived sessions are excluded by default.
     * <p><b>200</b> - List of sessions
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param roots The roots parameter
     * @param start The start parameter
     * @param cursor The cursor parameter
     * @param search The search parameter
     * @param limit The limit parameter
     * @param archived The archived parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec experimentalSessionListWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ExperimentalSessionListRootsParameter roots, @jakarta.annotation.Nullable BigDecimal start, @jakarta.annotation.Nullable BigDecimal cursor, @jakarta.annotation.Nullable String search, @jakarta.annotation.Nullable BigDecimal limit, @jakarta.annotation.Nullable ExperimentalSessionListRootsParameter archived) throws WebClientResponseException {
        return experimentalSessionListRequestCreation(directory, workspace, roots, start, cursor, search, limit, archived);
    }

    public class ToolIdsRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public ToolIdsRequest() {}

        public ToolIdsRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public ToolIdsRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public ToolIdsRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            ToolIdsRequest request = (ToolIdsRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * List tool IDs
     * Get a list of all available tool IDs, including both built-in tools and dynamically registered tools.
     * <p><b>200</b> - Tool IDs
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The toolIds request parameters as object
     * @return List&lt;String&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<List<String>> toolIds(ToolIdsRequest requestParameters) throws WebClientResponseException {
        return this.toolIds(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List tool IDs
     * Get a list of all available tool IDs, including both built-in tools and dynamically registered tools.
     * <p><b>200</b> - Tool IDs
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The toolIds request parameters as object
     * @return ResponseEntity&lt;List&lt;String&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<String>>> toolIdsWithHttpInfo(ToolIdsRequest requestParameters) throws WebClientResponseException {
        return this.toolIdsWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List tool IDs
     * Get a list of all available tool IDs, including both built-in tools and dynamically registered tools.
     * <p><b>200</b> - Tool IDs
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The toolIds request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec toolIdsWithResponseSpec(ToolIdsRequest requestParameters) throws WebClientResponseException {
        return this.toolIdsWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * List tool IDs
     * Get a list of all available tool IDs, including both built-in tools and dynamically registered tools.
     * <p><b>200</b> - Tool IDs
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;String&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec toolIdsRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<List<String>> localVarReturnType = new ParameterizedTypeReference<List<String>>() {};
        return apiClient.invokeAPI("/experimental/tool/ids", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List tool IDs
     * Get a list of all available tool IDs, including both built-in tools and dynamically registered tools.
     * <p><b>200</b> - Tool IDs
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;String&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<List<String>> toolIds(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<List<String>> localVarReturnType = new ParameterizedTypeReference<List<String>>() {};
        return toolIdsRequestCreation(directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * List tool IDs
     * Get a list of all available tool IDs, including both built-in tools and dynamically registered tools.
     * <p><b>200</b> - Tool IDs
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;List&lt;String&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<String>>> toolIdsWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<List<String>> localVarReturnType = new ParameterizedTypeReference<List<String>>() {};
        return toolIdsRequestCreation(directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * List tool IDs
     * Get a list of all available tool IDs, including both built-in tools and dynamically registered tools.
     * <p><b>200</b> - Tool IDs
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec toolIdsWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return toolIdsRequestCreation(directory, workspace);
    }

    public class ToolListRequest {
        private @jakarta.annotation.Nonnull String provider;
        private @jakarta.annotation.Nonnull String model;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public ToolListRequest() {}

        public ToolListRequest(@jakarta.annotation.Nonnull String provider, @jakarta.annotation.Nonnull String model, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.provider = provider;
            this.model = model;
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nonnull String provider() {
            return this.provider;
        }
        public ToolListRequest provider(@jakarta.annotation.Nonnull String provider) {
            this.provider = provider;
            return this;
        }

        public @jakarta.annotation.Nonnull String model() {
            return this.model;
        }
        public ToolListRequest model(@jakarta.annotation.Nonnull String model) {
            this.model = model;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public ToolListRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public ToolListRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            ToolListRequest request = (ToolListRequest) o;
            return Objects.equals(this.provider, request.provider()) &&
                Objects.equals(this.model, request.model()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(provider, model, directory, workspace);
        }
    }

    /**
     * List tools
     * Get a list of available tools with their JSON schema parameters for a specific provider and model combination.
     * <p><b>200</b> - Tools
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The toolList request parameters as object
     * @return List&lt;ToolListItem&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<ToolListItem> toolList(ToolListRequest requestParameters) throws WebClientResponseException {
        return this.toolList(requestParameters.provider(), requestParameters.model(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List tools
     * Get a list of available tools with their JSON schema parameters for a specific provider and model combination.
     * <p><b>200</b> - Tools
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The toolList request parameters as object
     * @return ResponseEntity&lt;List&lt;ToolListItem&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<ToolListItem>>> toolListWithHttpInfo(ToolListRequest requestParameters) throws WebClientResponseException {
        return this.toolListWithHttpInfo(requestParameters.provider(), requestParameters.model(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List tools
     * Get a list of available tools with their JSON schema parameters for a specific provider and model combination.
     * <p><b>200</b> - Tools
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The toolList request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec toolListWithResponseSpec(ToolListRequest requestParameters) throws WebClientResponseException {
        return this.toolListWithResponseSpec(requestParameters.provider(), requestParameters.model(), requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * List tools
     * Get a list of available tools with their JSON schema parameters for a specific provider and model combination.
     * <p><b>200</b> - Tools
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param provider The provider parameter
     * @param model The model parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;ToolListItem&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec toolListRequestCreation(@jakarta.annotation.Nonnull String provider, @jakarta.annotation.Nonnull String model, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'provider' is set
        if (provider == null) {
            throw new WebClientResponseException("Missing the required parameter 'provider' when calling toolList", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'model' is set
        if (model == null) {
            throw new WebClientResponseException("Missing the required parameter 'model' when calling toolList", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", directory));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", workspace));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "provider", provider));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "model", model));

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<ToolListItem> localVarReturnType = new ParameterizedTypeReference<ToolListItem>() {};
        return apiClient.invokeAPI("/experimental/tool", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List tools
     * Get a list of available tools with their JSON schema parameters for a specific provider and model combination.
     * <p><b>200</b> - Tools
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param provider The provider parameter
     * @param model The model parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;ToolListItem&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<ToolListItem> toolList(@jakarta.annotation.Nonnull String provider, @jakarta.annotation.Nonnull String model, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<ToolListItem> localVarReturnType = new ParameterizedTypeReference<ToolListItem>() {};
        return toolListRequestCreation(provider, model, directory, workspace).bodyToFlux(localVarReturnType);
    }

    /**
     * List tools
     * Get a list of available tools with their JSON schema parameters for a specific provider and model combination.
     * <p><b>200</b> - Tools
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param provider The provider parameter
     * @param model The model parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;List&lt;ToolListItem&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<ToolListItem>>> toolListWithHttpInfo(@jakarta.annotation.Nonnull String provider, @jakarta.annotation.Nonnull String model, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<ToolListItem> localVarReturnType = new ParameterizedTypeReference<ToolListItem>() {};
        return toolListRequestCreation(provider, model, directory, workspace).toEntityList(localVarReturnType);
    }

    /**
     * List tools
     * Get a list of available tools with their JSON schema parameters for a specific provider and model combination.
     * <p><b>200</b> - Tools
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param provider The provider parameter
     * @param model The model parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec toolListWithResponseSpec(@jakarta.annotation.Nonnull String provider, @jakarta.annotation.Nonnull String model, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return toolListRequestCreation(provider, model, directory, workspace);
    }

    public class WorktreeCreateRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable WorktreeCreateInput worktreeCreateInput;

        public WorktreeCreateRequest() {}

        public WorktreeCreateRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable WorktreeCreateInput worktreeCreateInput) {
            this.directory = directory;
            this.workspace = workspace;
            this.worktreeCreateInput = worktreeCreateInput;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public WorktreeCreateRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public WorktreeCreateRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable WorktreeCreateInput worktreeCreateInput() {
            return this.worktreeCreateInput;
        }
        public WorktreeCreateRequest worktreeCreateInput(@jakarta.annotation.Nullable WorktreeCreateInput worktreeCreateInput) {
            this.worktreeCreateInput = worktreeCreateInput;
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
            WorktreeCreateRequest request = (WorktreeCreateRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.worktreeCreateInput, request.worktreeCreateInput());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace, worktreeCreateInput);
        }
    }

    /**
     * Create worktree
     * Create a new git worktree for the current project and run any configured startup scripts.
     * <p><b>200</b> - Worktree created
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * @param requestParameters The worktreeCreate request parameters as object
     * @return Worktree
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Worktree> worktreeCreate(WorktreeCreateRequest requestParameters) throws WebClientResponseException {
        return this.worktreeCreate(requestParameters.directory(), requestParameters.workspace(), requestParameters.worktreeCreateInput());
    }

    /**
     * Create worktree
     * Create a new git worktree for the current project and run any configured startup scripts.
     * <p><b>200</b> - Worktree created
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * @param requestParameters The worktreeCreate request parameters as object
     * @return ResponseEntity&lt;Worktree&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Worktree>> worktreeCreateWithHttpInfo(WorktreeCreateRequest requestParameters) throws WebClientResponseException {
        return this.worktreeCreateWithHttpInfo(requestParameters.directory(), requestParameters.workspace(), requestParameters.worktreeCreateInput());
    }

    /**
     * Create worktree
     * Create a new git worktree for the current project and run any configured startup scripts.
     * <p><b>200</b> - Worktree created
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * @param requestParameters The worktreeCreate request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec worktreeCreateWithResponseSpec(WorktreeCreateRequest requestParameters) throws WebClientResponseException {
        return this.worktreeCreateWithResponseSpec(requestParameters.directory(), requestParameters.workspace(), requestParameters.worktreeCreateInput());
    }


    /**
     * Create worktree
     * Create a new git worktree for the current project and run any configured startup scripts.
     * <p><b>200</b> - Worktree created
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param worktreeCreateInput The worktreeCreateInput parameter
     * @return Worktree
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec worktreeCreateRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable WorktreeCreateInput worktreeCreateInput) throws WebClientResponseException {
        Object postBody = worktreeCreateInput;
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

        ParameterizedTypeReference<Worktree> localVarReturnType = new ParameterizedTypeReference<Worktree>() {};
        return apiClient.invokeAPI("/experimental/worktree", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Create worktree
     * Create a new git worktree for the current project and run any configured startup scripts.
     * <p><b>200</b> - Worktree created
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param worktreeCreateInput The worktreeCreateInput parameter
     * @return Worktree
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Worktree> worktreeCreate(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable WorktreeCreateInput worktreeCreateInput) throws WebClientResponseException {
        ParameterizedTypeReference<Worktree> localVarReturnType = new ParameterizedTypeReference<Worktree>() {};
        return worktreeCreateRequestCreation(directory, workspace, worktreeCreateInput).bodyToMono(localVarReturnType);
    }

    /**
     * Create worktree
     * Create a new git worktree for the current project and run any configured startup scripts.
     * <p><b>200</b> - Worktree created
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param worktreeCreateInput The worktreeCreateInput parameter
     * @return ResponseEntity&lt;Worktree&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Worktree>> worktreeCreateWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable WorktreeCreateInput worktreeCreateInput) throws WebClientResponseException {
        ParameterizedTypeReference<Worktree> localVarReturnType = new ParameterizedTypeReference<Worktree>() {};
        return worktreeCreateRequestCreation(directory, workspace, worktreeCreateInput).toEntity(localVarReturnType);
    }

    /**
     * Create worktree
     * Create a new git worktree for the current project and run any configured startup scripts.
     * <p><b>200</b> - Worktree created
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param worktreeCreateInput The worktreeCreateInput parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec worktreeCreateWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable WorktreeCreateInput worktreeCreateInput) throws WebClientResponseException {
        return worktreeCreateRequestCreation(directory, workspace, worktreeCreateInput);
    }

    public class WorktreeListRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public WorktreeListRequest() {}

        public WorktreeListRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public WorktreeListRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public WorktreeListRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            WorktreeListRequest request = (WorktreeListRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * List worktrees
     * List all sandbox worktrees for the current project.
     * <p><b>200</b> - List of worktree directories
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * @param requestParameters The worktreeList request parameters as object
     * @return List&lt;String&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<List<String>> worktreeList(WorktreeListRequest requestParameters) throws WebClientResponseException {
        return this.worktreeList(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List worktrees
     * List all sandbox worktrees for the current project.
     * <p><b>200</b> - List of worktree directories
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * @param requestParameters The worktreeList request parameters as object
     * @return ResponseEntity&lt;List&lt;String&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<String>>> worktreeListWithHttpInfo(WorktreeListRequest requestParameters) throws WebClientResponseException {
        return this.worktreeListWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List worktrees
     * List all sandbox worktrees for the current project.
     * <p><b>200</b> - List of worktree directories
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * @param requestParameters The worktreeList request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec worktreeListWithResponseSpec(WorktreeListRequest requestParameters) throws WebClientResponseException {
        return this.worktreeListWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * List worktrees
     * List all sandbox worktrees for the current project.
     * <p><b>200</b> - List of worktree directories
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;String&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec worktreeListRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<List<String>> localVarReturnType = new ParameterizedTypeReference<List<String>>() {};
        return apiClient.invokeAPI("/experimental/worktree", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List worktrees
     * List all sandbox worktrees for the current project.
     * <p><b>200</b> - List of worktree directories
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;String&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<List<String>> worktreeList(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<List<String>> localVarReturnType = new ParameterizedTypeReference<List<String>>() {};
        return worktreeListRequestCreation(directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * List worktrees
     * List all sandbox worktrees for the current project.
     * <p><b>200</b> - List of worktree directories
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;List&lt;String&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<String>>> worktreeListWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<List<String>> localVarReturnType = new ParameterizedTypeReference<List<String>>() {};
        return worktreeListRequestCreation(directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * List worktrees
     * List all sandbox worktrees for the current project.
     * <p><b>200</b> - List of worktree directories
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec worktreeListWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return worktreeListRequestCreation(directory, workspace);
    }

    public class WorktreeRemoveRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable WorktreeRemoveInput worktreeRemoveInput;

        public WorktreeRemoveRequest() {}

        public WorktreeRemoveRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable WorktreeRemoveInput worktreeRemoveInput) {
            this.directory = directory;
            this.workspace = workspace;
            this.worktreeRemoveInput = worktreeRemoveInput;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public WorktreeRemoveRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public WorktreeRemoveRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable WorktreeRemoveInput worktreeRemoveInput() {
            return this.worktreeRemoveInput;
        }
        public WorktreeRemoveRequest worktreeRemoveInput(@jakarta.annotation.Nullable WorktreeRemoveInput worktreeRemoveInput) {
            this.worktreeRemoveInput = worktreeRemoveInput;
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
            WorktreeRemoveRequest request = (WorktreeRemoveRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.worktreeRemoveInput, request.worktreeRemoveInput());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace, worktreeRemoveInput);
        }
    }

    /**
     * Remove worktree
     * Remove a git worktree and delete its branch.
     * <p><b>200</b> - Worktree removed
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * @param requestParameters The worktreeRemove request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> worktreeRemove(WorktreeRemoveRequest requestParameters) throws WebClientResponseException {
        return this.worktreeRemove(requestParameters.directory(), requestParameters.workspace(), requestParameters.worktreeRemoveInput());
    }

    /**
     * Remove worktree
     * Remove a git worktree and delete its branch.
     * <p><b>200</b> - Worktree removed
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * @param requestParameters The worktreeRemove request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> worktreeRemoveWithHttpInfo(WorktreeRemoveRequest requestParameters) throws WebClientResponseException {
        return this.worktreeRemoveWithHttpInfo(requestParameters.directory(), requestParameters.workspace(), requestParameters.worktreeRemoveInput());
    }

    /**
     * Remove worktree
     * Remove a git worktree and delete its branch.
     * <p><b>200</b> - Worktree removed
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * @param requestParameters The worktreeRemove request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec worktreeRemoveWithResponseSpec(WorktreeRemoveRequest requestParameters) throws WebClientResponseException {
        return this.worktreeRemoveWithResponseSpec(requestParameters.directory(), requestParameters.workspace(), requestParameters.worktreeRemoveInput());
    }


    /**
     * Remove worktree
     * Remove a git worktree and delete its branch.
     * <p><b>200</b> - Worktree removed
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param worktreeRemoveInput The worktreeRemoveInput parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec worktreeRemoveRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable WorktreeRemoveInput worktreeRemoveInput) throws WebClientResponseException {
        Object postBody = worktreeRemoveInput;
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
        return apiClient.invokeAPI("/experimental/worktree", HttpMethod.DELETE, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Remove worktree
     * Remove a git worktree and delete its branch.
     * <p><b>200</b> - Worktree removed
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param worktreeRemoveInput The worktreeRemoveInput parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> worktreeRemove(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable WorktreeRemoveInput worktreeRemoveInput) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return worktreeRemoveRequestCreation(directory, workspace, worktreeRemoveInput).bodyToMono(localVarReturnType);
    }

    /**
     * Remove worktree
     * Remove a git worktree and delete its branch.
     * <p><b>200</b> - Worktree removed
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param worktreeRemoveInput The worktreeRemoveInput parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> worktreeRemoveWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable WorktreeRemoveInput worktreeRemoveInput) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return worktreeRemoveRequestCreation(directory, workspace, worktreeRemoveInput).toEntity(localVarReturnType);
    }

    /**
     * Remove worktree
     * Remove a git worktree and delete its branch.
     * <p><b>200</b> - Worktree removed
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param worktreeRemoveInput The worktreeRemoveInput parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec worktreeRemoveWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable WorktreeRemoveInput worktreeRemoveInput) throws WebClientResponseException {
        return worktreeRemoveRequestCreation(directory, workspace, worktreeRemoveInput);
    }

    public class WorktreeResetRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable WorktreeResetInput worktreeResetInput;

        public WorktreeResetRequest() {}

        public WorktreeResetRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable WorktreeResetInput worktreeResetInput) {
            this.directory = directory;
            this.workspace = workspace;
            this.worktreeResetInput = worktreeResetInput;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public WorktreeResetRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public WorktreeResetRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable WorktreeResetInput worktreeResetInput() {
            return this.worktreeResetInput;
        }
        public WorktreeResetRequest worktreeResetInput(@jakarta.annotation.Nullable WorktreeResetInput worktreeResetInput) {
            this.worktreeResetInput = worktreeResetInput;
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
            WorktreeResetRequest request = (WorktreeResetRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.worktreeResetInput, request.worktreeResetInput());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace, worktreeResetInput);
        }
    }

    /**
     * Reset worktree
     * Reset a worktree branch to the primary default branch.
     * <p><b>200</b> - Worktree reset
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * @param requestParameters The worktreeReset request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> worktreeReset(WorktreeResetRequest requestParameters) throws WebClientResponseException {
        return this.worktreeReset(requestParameters.directory(), requestParameters.workspace(), requestParameters.worktreeResetInput());
    }

    /**
     * Reset worktree
     * Reset a worktree branch to the primary default branch.
     * <p><b>200</b> - Worktree reset
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * @param requestParameters The worktreeReset request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> worktreeResetWithHttpInfo(WorktreeResetRequest requestParameters) throws WebClientResponseException {
        return this.worktreeResetWithHttpInfo(requestParameters.directory(), requestParameters.workspace(), requestParameters.worktreeResetInput());
    }

    /**
     * Reset worktree
     * Reset a worktree branch to the primary default branch.
     * <p><b>200</b> - Worktree reset
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * @param requestParameters The worktreeReset request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec worktreeResetWithResponseSpec(WorktreeResetRequest requestParameters) throws WebClientResponseException {
        return this.worktreeResetWithResponseSpec(requestParameters.directory(), requestParameters.workspace(), requestParameters.worktreeResetInput());
    }


    /**
     * Reset worktree
     * Reset a worktree branch to the primary default branch.
     * <p><b>200</b> - Worktree reset
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param worktreeResetInput The worktreeResetInput parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec worktreeResetRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable WorktreeResetInput worktreeResetInput) throws WebClientResponseException {
        Object postBody = worktreeResetInput;
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
        return apiClient.invokeAPI("/experimental/worktree/reset", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Reset worktree
     * Reset a worktree branch to the primary default branch.
     * <p><b>200</b> - Worktree reset
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param worktreeResetInput The worktreeResetInput parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> worktreeReset(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable WorktreeResetInput worktreeResetInput) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return worktreeResetRequestCreation(directory, workspace, worktreeResetInput).bodyToMono(localVarReturnType);
    }

    /**
     * Reset worktree
     * Reset a worktree branch to the primary default branch.
     * <p><b>200</b> - Worktree reset
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param worktreeResetInput The worktreeResetInput parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> worktreeResetWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable WorktreeResetInput worktreeResetInput) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return worktreeResetRequestCreation(directory, workspace, worktreeResetInput).toEntity(localVarReturnType);
    }

    /**
     * Reset worktree
     * Reset a worktree branch to the primary default branch.
     * <p><b>200</b> - Worktree reset
     * <p><b>400</b> - WorktreeError | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param worktreeResetInput The worktreeResetInput parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec worktreeResetWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable WorktreeResetInput worktreeResetInput) throws WebClientResponseException {
        return worktreeResetRequestCreation(directory, workspace, worktreeResetInput);
    }
}
