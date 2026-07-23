package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.AppLogRequest;
import com.example.opencode.sdk.model.Auth;
import com.example.opencode.sdk.model.AuthSet400Response;

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
public class ControlApi {
    private ApiClient apiClient;

    public ControlApi() {
        this(new ApiClient());
    }

    public ControlApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public class AppLogRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable AppLogRequest appLogRequest;

        public AppLogRequest() {}

        public AppLogRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable AppLogRequest appLogRequest) {
            this.directory = directory;
            this.workspace = workspace;
            this.appLogRequest = appLogRequest;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public AppLogRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public AppLogRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable AppLogRequest appLogRequest() {
            return this.appLogRequest;
        }
        public AppLogRequest appLogRequest(@jakarta.annotation.Nullable AppLogRequest appLogRequest) {
            this.appLogRequest = appLogRequest;
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
            AppLogRequest request = (AppLogRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.appLogRequest, request.appLogRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace, appLogRequest);
        }
    }

    /**
     * Write log
     * Write a log entry to the server logs with specified level and metadata.
     * <p><b>200</b> - Log entry written successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The appLog request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> appLog(AppLogRequest requestParameters) throws WebClientResponseException {
        return this.appLog(requestParameters.directory(), requestParameters.workspace(), requestParameters.appLogRequest());
    }

    /**
     * Write log
     * Write a log entry to the server logs with specified level and metadata.
     * <p><b>200</b> - Log entry written successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The appLog request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> appLogWithHttpInfo(AppLogRequest requestParameters) throws WebClientResponseException {
        return this.appLogWithHttpInfo(requestParameters.directory(), requestParameters.workspace(), requestParameters.appLogRequest());
    }

    /**
     * Write log
     * Write a log entry to the server logs with specified level and metadata.
     * <p><b>200</b> - Log entry written successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The appLog request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec appLogWithResponseSpec(AppLogRequest requestParameters) throws WebClientResponseException {
        return this.appLogWithResponseSpec(requestParameters.directory(), requestParameters.workspace(), requestParameters.appLogRequest());
    }


    /**
     * Write log
     * Write a log entry to the server logs with specified level and metadata.
     * <p><b>200</b> - Log entry written successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param appLogRequest The appLogRequest parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec appLogRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable AppLogRequest appLogRequest) throws WebClientResponseException {
        Object postBody = appLogRequest;
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
        return apiClient.invokeAPI("/log", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Write log
     * Write a log entry to the server logs with specified level and metadata.
     * <p><b>200</b> - Log entry written successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param appLogRequest The appLogRequest parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> appLog(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable AppLogRequest appLogRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return appLogRequestCreation(directory, workspace, appLogRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Write log
     * Write a log entry to the server logs with specified level and metadata.
     * <p><b>200</b> - Log entry written successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param appLogRequest The appLogRequest parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> appLogWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable AppLogRequest appLogRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return appLogRequestCreation(directory, workspace, appLogRequest).toEntity(localVarReturnType);
    }

    /**
     * Write log
     * Write a log entry to the server logs with specified level and metadata.
     * <p><b>200</b> - Log entry written successfully
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param appLogRequest The appLogRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec appLogWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable AppLogRequest appLogRequest) throws WebClientResponseException {
        return appLogRequestCreation(directory, workspace, appLogRequest);
    }

    /**
     * Remove auth credentials
     * Remove authentication credentials
     * <p><b>200</b> - Successfully removed authentication credentials
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param providerID The providerID parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec authRemoveRequestCreation(@jakarta.annotation.Nonnull String providerID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'providerID' is set
        if (providerID == null) {
            throw new WebClientResponseException("Missing the required parameter 'providerID' when calling authRemove", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("providerID", providerID);

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

        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return apiClient.invokeAPI("/auth/{providerID}", HttpMethod.DELETE, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Remove auth credentials
     * Remove authentication credentials
     * <p><b>200</b> - Successfully removed authentication credentials
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param providerID The providerID parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> authRemove(@jakarta.annotation.Nonnull String providerID) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return authRemoveRequestCreation(providerID).bodyToMono(localVarReturnType);
    }

    /**
     * Remove auth credentials
     * Remove authentication credentials
     * <p><b>200</b> - Successfully removed authentication credentials
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param providerID The providerID parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> authRemoveWithHttpInfo(@jakarta.annotation.Nonnull String providerID) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return authRemoveRequestCreation(providerID).toEntity(localVarReturnType);
    }

    /**
     * Remove auth credentials
     * Remove authentication credentials
     * <p><b>200</b> - Successfully removed authentication credentials
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param providerID The providerID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec authRemoveWithResponseSpec(@jakarta.annotation.Nonnull String providerID) throws WebClientResponseException {
        return authRemoveRequestCreation(providerID);
    }

    public class AuthSetRequest {
        private @jakarta.annotation.Nonnull String providerID;
        private @jakarta.annotation.Nullable Auth auth;

        public AuthSetRequest() {}

        public AuthSetRequest(@jakarta.annotation.Nonnull String providerID, @jakarta.annotation.Nullable Auth auth) {
            this.providerID = providerID;
            this.auth = auth;
        }

        public @jakarta.annotation.Nonnull String providerID() {
            return this.providerID;
        }
        public AuthSetRequest providerID(@jakarta.annotation.Nonnull String providerID) {
            this.providerID = providerID;
            return this;
        }

        public @jakarta.annotation.Nullable Auth auth() {
            return this.auth;
        }
        public AuthSetRequest auth(@jakarta.annotation.Nullable Auth auth) {
            this.auth = auth;
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
            AuthSetRequest request = (AuthSetRequest) o;
            return Objects.equals(this.providerID, request.providerID()) &&
                Objects.equals(this.auth, request.auth());
        }

        @Override
        public int hashCode() {
            return Objects.hash(providerID, auth);
        }
    }

    /**
     * Set auth credentials
     * Set authentication credentials
     * <p><b>200</b> - Successfully set authentication credentials
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The authSet request parameters as object
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> authSet(AuthSetRequest requestParameters) throws WebClientResponseException {
        return this.authSet(requestParameters.providerID(), requestParameters.auth());
    }

    /**
     * Set auth credentials
     * Set authentication credentials
     * <p><b>200</b> - Successfully set authentication credentials
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The authSet request parameters as object
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> authSetWithHttpInfo(AuthSetRequest requestParameters) throws WebClientResponseException {
        return this.authSetWithHttpInfo(requestParameters.providerID(), requestParameters.auth());
    }

    /**
     * Set auth credentials
     * Set authentication credentials
     * <p><b>200</b> - Successfully set authentication credentials
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param requestParameters The authSet request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec authSetWithResponseSpec(AuthSetRequest requestParameters) throws WebClientResponseException {
        return this.authSetWithResponseSpec(requestParameters.providerID(), requestParameters.auth());
    }


    /**
     * Set auth credentials
     * Set authentication credentials
     * <p><b>200</b> - Successfully set authentication credentials
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param providerID The providerID parameter
     * @param auth The auth parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec authSetRequestCreation(@jakarta.annotation.Nonnull String providerID, @jakarta.annotation.Nullable Auth auth) throws WebClientResponseException {
        Object postBody = auth;
        // verify the required parameter 'providerID' is set
        if (providerID == null) {
            throw new WebClientResponseException("Missing the required parameter 'providerID' when calling authSet", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("providerID", providerID);

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

        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return apiClient.invokeAPI("/auth/{providerID}", HttpMethod.PUT, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Set auth credentials
     * Set authentication credentials
     * <p><b>200</b> - Successfully set authentication credentials
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param providerID The providerID parameter
     * @param auth The auth parameter
     * @return Boolean
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Boolean> authSet(@jakarta.annotation.Nonnull String providerID, @jakarta.annotation.Nullable Auth auth) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return authSetRequestCreation(providerID, auth).bodyToMono(localVarReturnType);
    }

    /**
     * Set auth credentials
     * Set authentication credentials
     * <p><b>200</b> - Successfully set authentication credentials
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param providerID The providerID parameter
     * @param auth The auth parameter
     * @return ResponseEntity&lt;Boolean&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Boolean>> authSetWithHttpInfo(@jakarta.annotation.Nonnull String providerID, @jakarta.annotation.Nullable Auth auth) throws WebClientResponseException {
        ParameterizedTypeReference<Boolean> localVarReturnType = new ParameterizedTypeReference<Boolean>() {};
        return authSetRequestCreation(providerID, auth).toEntity(localVarReturnType);
    }

    /**
     * Set auth credentials
     * Set authentication credentials
     * <p><b>200</b> - Successfully set authentication credentials
     * <p><b>400</b> - BadRequest | InvalidRequestError
     * @param providerID The providerID parameter
     * @param auth The auth parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec authSetWithResponseSpec(@jakarta.annotation.Nonnull String providerID, @jakarta.annotation.Nullable Auth auth) throws WebClientResponseException {
        return authSetRequestCreation(providerID, auth);
    }
}
