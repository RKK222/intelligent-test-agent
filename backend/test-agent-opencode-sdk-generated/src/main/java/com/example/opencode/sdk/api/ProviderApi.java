package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.BadRequestError;
import com.example.opencode.sdk.model.ProviderAuthAuthorization;
import com.example.opencode.sdk.model.ProviderAuthMethod;
import com.example.opencode.sdk.model.ProviderList200Response;
import com.example.opencode.sdk.model.ProviderOauthAuthorize400Response;
import com.example.opencode.sdk.model.ProviderOauthAuthorizeRequest;
import com.example.opencode.sdk.model.ProviderOauthCallbackRequest;

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
public class ProviderApi {
    private ApiClient apiClient;

    public ProviderApi() {
        this(new ApiClient());
    }

    public ProviderApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public class ProviderAuthRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public ProviderAuthRequest() {}

        public ProviderAuthRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public ProviderAuthRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public ProviderAuthRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            ProviderAuthRequest request = (ProviderAuthRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * Get provider auth methods
     * Retrieve available authentication methods for all AI providers.
     * <p><b>200</b> - Provider auth methods
     * <p><b>400</b> - Bad request
     * @param requestParameters The providerAuth request parameters as object
     * @return Map&lt;String, List&lt;ProviderAuthMethod&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Map<String, List<ProviderAuthMethod>>> providerAuth(ProviderAuthRequest requestParameters) throws WebClientResponseException {
        return this.providerAuth(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get provider auth methods
     * Retrieve available authentication methods for all AI providers.
     * <p><b>200</b> - Provider auth methods
     * <p><b>400</b> - Bad request
     * @param requestParameters The providerAuth request parameters as object
     * @return ResponseEntity&lt;Map&lt;String, List&lt;ProviderAuthMethod&gt;&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Map<String, List<ProviderAuthMethod>>>> providerAuthWithHttpInfo(ProviderAuthRequest requestParameters) throws WebClientResponseException {
        return this.providerAuthWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get provider auth methods
     * Retrieve available authentication methods for all AI providers.
     * <p><b>200</b> - Provider auth methods
     * <p><b>400</b> - Bad request
     * @param requestParameters The providerAuth request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec providerAuthWithResponseSpec(ProviderAuthRequest requestParameters) throws WebClientResponseException {
        return this.providerAuthWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Get provider auth methods
     * Retrieve available authentication methods for all AI providers.
     * <p><b>200</b> - Provider auth methods
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Map&lt;String, List&lt;ProviderAuthMethod&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec providerAuthRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<Map<String, List<ProviderAuthMethod>>> localVarReturnType = new ParameterizedTypeReference<Map<String, List<ProviderAuthMethod>>>() {};
        return apiClient.invokeAPI("/provider/auth", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get provider auth methods
     * Retrieve available authentication methods for all AI providers.
     * <p><b>200</b> - Provider auth methods
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return Map&lt;String, List&lt;ProviderAuthMethod&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Map<String, List<ProviderAuthMethod>>> providerAuth(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Map<String, List<ProviderAuthMethod>>> localVarReturnType = new ParameterizedTypeReference<Map<String, List<ProviderAuthMethod>>>() {};
        return providerAuthRequestCreation(directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Get provider auth methods
     * Retrieve available authentication methods for all AI providers.
     * <p><b>200</b> - Provider auth methods
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;Map&lt;String, List&lt;ProviderAuthMethod&gt;&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Map<String, List<ProviderAuthMethod>>>> providerAuthWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Map<String, List<ProviderAuthMethod>>> localVarReturnType = new ParameterizedTypeReference<Map<String, List<ProviderAuthMethod>>>() {};
        return providerAuthRequestCreation(directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Get provider auth methods
     * Retrieve available authentication methods for all AI providers.
     * <p><b>200</b> - Provider auth methods
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec providerAuthWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return providerAuthRequestCreation(directory, workspace);
    }

    public class ProviderListRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public ProviderListRequest() {}

        public ProviderListRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public ProviderListRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public ProviderListRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            ProviderListRequest request = (ProviderListRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * List providers
     * Get a list of all available AI providers, including both available and connected ones.
     * <p><b>200</b> - List of providers
     * <p><b>400</b> - Bad request
     * @param requestParameters The providerList request parameters as object
     * @return ProviderList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ProviderList200Response> providerList(ProviderListRequest requestParameters) throws WebClientResponseException {
        return this.providerList(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List providers
     * Get a list of all available AI providers, including both available and connected ones.
     * <p><b>200</b> - List of providers
     * <p><b>400</b> - Bad request
     * @param requestParameters The providerList request parameters as object
     * @return ResponseEntity&lt;ProviderList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ProviderList200Response>> providerListWithHttpInfo(ProviderListRequest requestParameters) throws WebClientResponseException {
        return this.providerListWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List providers
     * Get a list of all available AI providers, including both available and connected ones.
     * <p><b>200</b> - List of providers
     * <p><b>400</b> - Bad request
     * @param requestParameters The providerList request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec providerListWithResponseSpec(ProviderListRequest requestParameters) throws WebClientResponseException {
        return this.providerListWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * List providers
     * Get a list of all available AI providers, including both available and connected ones.
     * <p><b>200</b> - List of providers
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ProviderList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec providerListRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<ProviderList200Response> localVarReturnType = new ParameterizedTypeReference<ProviderList200Response>() {};
        return apiClient.invokeAPI("/provider", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List providers
     * Get a list of all available AI providers, including both available and connected ones.
     * <p><b>200</b> - List of providers
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ProviderList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ProviderList200Response> providerList(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<ProviderList200Response> localVarReturnType = new ParameterizedTypeReference<ProviderList200Response>() {};
        return providerListRequestCreation(directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * List providers
     * Get a list of all available AI providers, including both available and connected ones.
     * <p><b>200</b> - List of providers
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;ProviderList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ProviderList200Response>> providerListWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<ProviderList200Response> localVarReturnType = new ParameterizedTypeReference<ProviderList200Response>() {};
        return providerListRequestCreation(directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * List providers
     * Get a list of all available AI providers, including both available and connected ones.
     * <p><b>200</b> - List of providers
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec providerListWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return providerListRequestCreation(directory, workspace);
    }

    public class ProviderOauthAuthorizeRequest {
        private @jakarta.annotation.Nonnull String providerID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable ProviderOauthAuthorizeRequest providerOauthAuthorizeRequest;

        public ProviderOauthAuthorizeRequest() {}

        public ProviderOauthAuthorizeRequest(@jakarta.annotation.Nonnull String providerID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ProviderOauthAuthorizeRequest providerOauthAuthorizeRequest) {
            this.providerID = providerID;
            this.directory = directory;
            this.workspace = workspace;
            this.providerOauthAuthorizeRequest = providerOauthAuthorizeRequest;
        }

        public @jakarta.annotation.Nonnull String providerID() {
            return this.providerID;
        }
        public ProviderOauthAuthorizeRequest providerID(@jakarta.annotation.Nonnull String providerID) {
            this.providerID = providerID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public ProviderOauthAuthorizeRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public ProviderOauthAuthorizeRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable ProviderOauthAuthorizeRequest providerOauthAuthorizeRequest() {
            return this.providerOauthAuthorizeRequest;
        }
        public ProviderOauthAuthorizeRequest providerOauthAuthorizeRequest(@jakarta.annotation.Nullable ProviderOauthAuthorizeRequest providerOauthAuthorizeRequest) {
            this.providerOauthAuthorizeRequest = providerOauthAuthorizeRequest;
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
            ProviderOauthAuthorizeRequest request = (ProviderOauthAuthorizeRequest) o;
            return Objects.equals(this.providerID, request.providerID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.providerOauthAuthorizeRequest, request.providerOauthAuthorizeRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(providerID, directory, workspace, providerOauthAuthorizeRequest);
        }
    }

    /**
     * Start OAuth authorization
     * Start the OAuth authorization flow for a provider.
     * <p><b>200</b> - Authorization URL and method
     * <p><b>400</b> - ProviderAuthError | InvalidRequestError
     * @param requestParameters The providerOauthAuthorize request parameters as object
     * @return ProviderAuthAuthorization
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ProviderAuthAuthorization> providerOauthAuthorize(ProviderOauthAuthorizeRequest requestParameters) throws WebClientResponseException {
        return this.providerOauthAuthorize(requestParameters.providerID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.providerOauthAuthorizeRequest());
    }

    /**
     * Start OAuth authorization
     * Start the OAuth authorization flow for a provider.
     * <p><b>200</b> - Authorization URL and method
     * <p><b>400</b> - ProviderAuthError | InvalidRequestError
     * @param requestParameters The providerOauthAuthorize request parameters as object
     * @return ResponseEntity&lt;ProviderAuthAuthorization&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ProviderAuthAuthorization>> providerOauthAuthorizeWithHttpInfo(ProviderOauthAuthorizeRequest requestParameters) throws WebClientResponseException {
        return this.providerOauthAuthorizeWithHttpInfo(requestParameters.providerID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.providerOauthAuthorizeRequest());
    }

    /**
     * Start OAuth authorization
     * Start the OAuth authorization flow for a provider.
     * <p><b>200</b> - Authorization URL and method
     * <p><b>400</b> - ProviderAuthError | InvalidRequestError
     * @param requestParameters The providerOauthAuthorize request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec providerOauthAuthorizeWithResponseSpec(ProviderOauthAuthorizeRequest requestParameters) throws WebClientResponseException {
        return this.providerOauthAuthorizeWithResponseSpec(requestParameters.providerID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.providerOauthAuthorizeRequest());
    }


    /**
     * Start OAuth authorization
     * Start the OAuth authorization flow for a provider.
     * <p><b>200</b> - Authorization URL and method
     * <p><b>400</b> - ProviderAuthError | InvalidRequestError
     * @param providerID The providerID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param providerOauthAuthorizeRequest The providerOauthAuthorizeRequest parameter
     * @return ProviderAuthAuthorization
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec providerOauthAuthorizeRequestCreation(@jakarta.annotation.Nonnull String providerID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ProviderOauthAuthorizeRequest providerOauthAuthorizeRequest) throws WebClientResponseException {
        Object postBody = providerOauthAuthorizeRequest;
        // verify the required parameter 'providerID' is set
        if (providerID == null) {
            throw new WebClientResponseException("Missing the required parameter 'providerID' when calling providerOauthAuthorize", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("providerID", providerID);

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

        ParameterizedTypeReference<ProviderAuthAuthorization> localVarReturnType = new ParameterizedTypeReference<ProviderAuthAuthorization>() {};
        return apiClient.invokeAPI("/provider/{providerID}/oauth/authorize", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Start OAuth authorization
     * Start the OAuth authorization flow for a provider.
     * <p><b>200</b> - Authorization URL and method
     * <p><b>400</b> - ProviderAuthError | InvalidRequestError
     * @param providerID The providerID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param providerOauthAuthorizeRequest The providerOauthAuthorizeRequest parameter
     * @return ProviderAuthAuthorization
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ProviderAuthAuthorization> providerOauthAuthorize(@jakarta.annotation.Nonnull String providerID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ProviderOauthAuthorizeRequest providerOauthAuthorizeRequest) throws WebClientResponseException {
        ParameterizedTypeReference<ProviderAuthAuthorization> localVarReturnType = new ParameterizedTypeReference<ProviderAuthAuthorization>() {};
        return providerOauthAuthorizeRequestCreation(providerID, directory, workspace, providerOauthAuthorizeRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Start OAuth authorization
     * Start the OAuth authorization flow for a provider.
     * <p><b>200</b> - Authorization URL and method
     * <p><b>400</b> - ProviderAuthError | InvalidRequestError
     * @param providerID The providerID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param providerOauthAuthorizeRequest The providerOauthAuthorizeRequest parameter
     * @return ResponseEntity&lt;ProviderAuthAuthorization&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ProviderAuthAuthorization>> providerOauthAuthorizeWithHttpInfo(@jakarta.annotation.Nonnull String providerID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ProviderOauthAuthorizeRequest providerOauthAuthorizeRequest) throws WebClientResponseException {
        ParameterizedTypeReference<ProviderAuthAuthorization> localVarReturnType = new ParameterizedTypeReference<ProviderAuthAuthorization>() {};
        return providerOauthAuthorizeRequestCreation(providerID, directory, workspace, providerOauthAuthorizeRequest).toEntity(localVarReturnType);
    }

    /**
     * Start OAuth authorization
     * Start the OAuth authorization flow for a provider.
     * <p><b>200</b> - Authorization URL and method
     * <p><b>400</b> - ProviderAuthError | InvalidRequestError
     * @param providerID The providerID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param providerOauthAuthorizeRequest The providerOauthAuthorizeRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec providerOauthAuthorizeWithResponseSpec(@jakarta.annotation.Nonnull String providerID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ProviderOauthAuthorizeRequest providerOauthAuthorizeRequest) throws WebClientResponseException {
        return providerOauthAuthorizeRequestCreation(providerID, directory, workspace, providerOauthAuthorizeRequest);
    }

    public class ProviderOauthCallbackRequest {
        private @jakarta.annotation.Nonnull String providerID;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable ProviderOauthCallbackRequest providerOauthCallbackRequest;

        public ProviderOauthCallbackRequest() {}

        public ProviderOauthCallbackRequest(@jakarta.annotation.Nonnull String providerID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ProviderOauthCallbackRequest providerOauthCallbackRequest) {
            this.providerID = providerID;
            this.directory = directory;
            this.workspace = workspace;
            this.providerOauthCallbackRequest = providerOauthCallbackRequest;
        }

        public @jakarta.annotation.Nonnull String providerID() {
            return this.providerID;
        }
        public ProviderOauthCallbackRequest providerID(@jakarta.annotation.Nonnull String providerID) {
            this.providerID = providerID;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public ProviderOauthCallbackRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public ProviderOauthCallbackRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable ProviderOauthCallbackRequest providerOauthCallbackRequest() {
            return this.providerOauthCallbackRequest;
        }
        public ProviderOauthCallbackRequest providerOauthCallbackRequest(@jakarta.annotation.Nullable ProviderOauthCallbackRequest providerOauthCallbackRequest) {
            this.providerOauthCallbackRequest = providerOauthCallbackRequest;
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
            ProviderOauthCallbackRequest request = (ProviderOauthCallbackRequest) o;
            return Objects.equals(this.providerID, request.providerID()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.providerOauthCallbackRequest, request.providerOauthCallbackRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(providerID, directory, workspace, providerOauthCallbackRequest);
        }
    }

    /**
     * Handle OAuth callback
     * Handle the OAuth callback from a provider after user authorization.
     * <p><b>200</b> - OAuth callback processed successfully
     * <p><b>400</b> - ProviderAuthError | InvalidRequestError
     * @param requestParameters The providerOauthCallback request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> providerOauthCallback(ProviderOauthCallbackRequest requestParameters) throws WebClientResponseException {
        return this.providerOauthCallback(requestParameters.providerID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.providerOauthCallbackRequest());
    }

    /**
     * Handle OAuth callback
     * Handle the OAuth callback from a provider after user authorization.
     * <p><b>200</b> - OAuth callback processed successfully
     * <p><b>400</b> - ProviderAuthError | InvalidRequestError
     * @param requestParameters The providerOauthCallback request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> providerOauthCallbackWithHttpInfo(ProviderOauthCallbackRequest requestParameters) throws WebClientResponseException {
        return this.providerOauthCallbackWithHttpInfo(requestParameters.providerID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.providerOauthCallbackRequest());
    }

    /**
     * Handle OAuth callback
     * Handle the OAuth callback from a provider after user authorization.
     * <p><b>200</b> - OAuth callback processed successfully
     * <p><b>400</b> - ProviderAuthError | InvalidRequestError
     * @param requestParameters The providerOauthCallback request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec providerOauthCallbackWithResponseSpec(ProviderOauthCallbackRequest requestParameters) throws WebClientResponseException {
        return this.providerOauthCallbackWithResponseSpec(requestParameters.providerID(), requestParameters.directory(), requestParameters.workspace(), requestParameters.providerOauthCallbackRequest());
    }


    /**
     * Handle OAuth callback
     * Handle the OAuth callback from a provider after user authorization.
     * <p><b>200</b> - OAuth callback processed successfully
     * <p><b>400</b> - ProviderAuthError | InvalidRequestError
     * @param providerID The providerID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param providerOauthCallbackRequest The providerOauthCallbackRequest parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec providerOauthCallbackRequestCreation(@jakarta.annotation.Nonnull String providerID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ProviderOauthCallbackRequest providerOauthCallbackRequest) throws WebClientResponseException {
        Object postBody = providerOauthCallbackRequest;
        // verify the required parameter 'providerID' is set
        if (providerID == null) {
            throw new WebClientResponseException("Missing the required parameter 'providerID' when calling providerOauthCallback", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("providerID", providerID);

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
        return apiClient.invokeAPI("/provider/{providerID}/oauth/callback", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Handle OAuth callback
     * Handle the OAuth callback from a provider after user authorization.
     * <p><b>200</b> - OAuth callback processed successfully
     * <p><b>400</b> - ProviderAuthError | InvalidRequestError
     * @param providerID The providerID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param providerOauthCallbackRequest The providerOauthCallbackRequest parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> providerOauthCallback(@jakarta.annotation.Nonnull String providerID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ProviderOauthCallbackRequest providerOauthCallbackRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return providerOauthCallbackRequestCreation(providerID, directory, workspace, providerOauthCallbackRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Handle OAuth callback
     * Handle the OAuth callback from a provider after user authorization.
     * <p><b>200</b> - OAuth callback processed successfully
     * <p><b>400</b> - ProviderAuthError | InvalidRequestError
     * @param providerID The providerID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param providerOauthCallbackRequest The providerOauthCallbackRequest parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> providerOauthCallbackWithHttpInfo(@jakarta.annotation.Nonnull String providerID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ProviderOauthCallbackRequest providerOauthCallbackRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return providerOauthCallbackRequestCreation(providerID, directory, workspace, providerOauthCallbackRequest).toEntity(localVarReturnType);
    }

    /**
     * Handle OAuth callback
     * Handle the OAuth callback from a provider after user authorization.
     * <p><b>200</b> - OAuth callback processed successfully
     * <p><b>400</b> - ProviderAuthError | InvalidRequestError
     * @param providerID The providerID parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param providerOauthCallbackRequest The providerOauthCallbackRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec providerOauthCallbackWithResponseSpec(@jakarta.annotation.Nonnull String providerID, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable ProviderOauthCallbackRequest providerOauthCallbackRequest) throws WebClientResponseException {
        return providerOauthCallbackRequestCreation(providerID, directory, workspace, providerOauthCallbackRequest);
    }
}
