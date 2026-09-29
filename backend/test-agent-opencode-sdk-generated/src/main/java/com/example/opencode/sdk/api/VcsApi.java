package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.AgentListLocationParameter;
import com.example.opencode.sdk.model.InvalidRequestErrorEncoded;
import com.example.opencode.sdk.model.ServiceUnavailableErrorEncoded;
import com.example.opencode.sdk.model.UnauthorizedErrorEncoded;
import com.example.opencode.sdk.model.VcsBase200Response;
import com.example.opencode.sdk.model.VcsBranchList200Response;
import com.example.opencode.sdk.model.VcsDiff200Response;
import com.example.opencode.sdk.model.VcsGet200Response;
import com.example.opencode.sdk.model.VcsMode;
import com.example.opencode.sdk.model.VcsStatus200Response;

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
public class VcsApi {
    private ApiClient apiClient;

    public VcsApi() {
        this(new ApiClient());
    }

    public VcsApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * VCS review base
     * Infer a local review base from named branch creation history, or the repository default only when currently on that branch. Returns null before the first commit or when the provider lacks base metadata; ambiguous Git history requires an explicit base on diff requests.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param location The location parameter
     * @return VcsBase200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec vcsBaseRequestCreation(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = null;
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<VcsBase200Response> localVarReturnType = new ParameterizedTypeReference<VcsBase200Response>() {};
        return apiClient.invokeAPI("/api/vcs/base", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * VCS review base
     * Infer a local review base from named branch creation history, or the repository default only when currently on that branch. Returns null before the first commit or when the provider lacks base metadata; ambiguous Git history requires an explicit base on diff requests.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param location The location parameter
     * @return VcsBase200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<VcsBase200Response> vcsBase(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<VcsBase200Response> localVarReturnType = new ParameterizedTypeReference<VcsBase200Response>() {};
        return vcsBaseRequestCreation(location).bodyToMono(localVarReturnType);
    }

    /**
     * VCS review base
     * Infer a local review base from named branch creation history, or the repository default only when currently on that branch. Returns null before the first commit or when the provider lacks base metadata; ambiguous Git history requires an explicit base on diff requests.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param location The location parameter
     * @return ResponseEntity&lt;VcsBase200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<VcsBase200Response>> vcsBaseWithHttpInfo(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<VcsBase200Response> localVarReturnType = new ParameterizedTypeReference<VcsBase200Response>() {};
        return vcsBaseRequestCreation(location).toEntity(localVarReturnType);
    }

    /**
     * VCS review base
     * Infer a local review base from named branch creation history, or the repository default only when currently on that branch. Returns null before the first commit or when the provider lacks base metadata; ambiguous Git history requires an explicit base on diff requests.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec vcsBaseWithResponseSpec(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return vcsBaseRequestCreation(location);
    }

    public class VcsBranchListRequest {
        private @jakarta.annotation.Nullable AgentListLocationParameter location;
        private @jakarta.annotation.Nullable String search;
        private @jakarta.annotation.Nullable String limit;

        public VcsBranchListRequest() {}

        public VcsBranchListRequest(@jakarta.annotation.Nullable AgentListLocationParameter location, @jakarta.annotation.Nullable String search, @jakarta.annotation.Nullable String limit) {
            this.location = location;
            this.search = search;
            this.limit = limit;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public VcsBranchListRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.location = location;
            return this;
        }

        public @jakarta.annotation.Nullable String search() {
            return this.search;
        }
        public VcsBranchListRequest search(@jakarta.annotation.Nullable String search) {
            this.search = search;
            return this;
        }

        public @jakarta.annotation.Nullable String limit() {
            return this.limit;
        }
        public VcsBranchListRequest limit(@jakarta.annotation.Nullable String limit) {
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
            VcsBranchListRequest request = (VcsBranchListRequest) o;
            return Objects.equals(this.location, request.location()) &&
                Objects.equals(this.search, request.search()) &&
                Objects.equals(this.limit, request.limit());
        }

        @Override
        public int hashCode() {
            return Objects.hash(location, search, limit);
        }
    }

    /**
     * VCS branches
     * List local and remote branches available at the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The vcsBranchList request parameters as object
     * @return VcsBranchList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<VcsBranchList200Response> vcsBranchList(VcsBranchListRequest requestParameters) throws WebClientResponseException {
        return this.vcsBranchList(requestParameters.location(), requestParameters.search(), requestParameters.limit());
    }

    /**
     * VCS branches
     * List local and remote branches available at the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The vcsBranchList request parameters as object
     * @return ResponseEntity&lt;VcsBranchList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<VcsBranchList200Response>> vcsBranchListWithHttpInfo(VcsBranchListRequest requestParameters) throws WebClientResponseException {
        return this.vcsBranchListWithHttpInfo(requestParameters.location(), requestParameters.search(), requestParameters.limit());
    }

    /**
     * VCS branches
     * List local and remote branches available at the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The vcsBranchList request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec vcsBranchListWithResponseSpec(VcsBranchListRequest requestParameters) throws WebClientResponseException {
        return this.vcsBranchListWithResponseSpec(requestParameters.location(), requestParameters.search(), requestParameters.limit());
    }


    /**
     * VCS branches
     * List local and remote branches available at the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @param search The search parameter
     * @param limit The limit parameter
     * @return VcsBranchList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec vcsBranchListRequestCreation(@jakarta.annotation.Nullable AgentListLocationParameter location, @jakarta.annotation.Nullable String search, @jakarta.annotation.Nullable String limit) throws WebClientResponseException {
        Object postBody = null;
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "search", search));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "limit", limit));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<VcsBranchList200Response> localVarReturnType = new ParameterizedTypeReference<VcsBranchList200Response>() {};
        return apiClient.invokeAPI("/api/vcs/branch", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * VCS branches
     * List local and remote branches available at the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @param search The search parameter
     * @param limit The limit parameter
     * @return VcsBranchList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<VcsBranchList200Response> vcsBranchList(@jakarta.annotation.Nullable AgentListLocationParameter location, @jakarta.annotation.Nullable String search, @jakarta.annotation.Nullable String limit) throws WebClientResponseException {
        ParameterizedTypeReference<VcsBranchList200Response> localVarReturnType = new ParameterizedTypeReference<VcsBranchList200Response>() {};
        return vcsBranchListRequestCreation(location, search, limit).bodyToMono(localVarReturnType);
    }

    /**
     * VCS branches
     * List local and remote branches available at the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @param search The search parameter
     * @param limit The limit parameter
     * @return ResponseEntity&lt;VcsBranchList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<VcsBranchList200Response>> vcsBranchListWithHttpInfo(@jakarta.annotation.Nullable AgentListLocationParameter location, @jakarta.annotation.Nullable String search, @jakarta.annotation.Nullable String limit) throws WebClientResponseException {
        ParameterizedTypeReference<VcsBranchList200Response> localVarReturnType = new ParameterizedTypeReference<VcsBranchList200Response>() {};
        return vcsBranchListRequestCreation(location, search, limit).toEntity(localVarReturnType);
    }

    /**
     * VCS branches
     * List local and remote branches available at the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @param search The search parameter
     * @param limit The limit parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec vcsBranchListWithResponseSpec(@jakarta.annotation.Nullable AgentListLocationParameter location, @jakarta.annotation.Nullable String search, @jakarta.annotation.Nullable String limit) throws WebClientResponseException {
        return vcsBranchListRequestCreation(location, search, limit);
    }

    public class VcsDiffRequest {
        private @jakarta.annotation.Nonnull VcsMode mode;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;
        private @jakarta.annotation.Nullable String base;
        private @jakarta.annotation.Nullable String context;

        public VcsDiffRequest() {}

        public VcsDiffRequest(@jakarta.annotation.Nonnull VcsMode mode, @jakarta.annotation.Nullable AgentListLocationParameter location, @jakarta.annotation.Nullable String base, @jakarta.annotation.Nullable String context) {
            this.mode = mode;
            this.location = location;
            this.base = base;
            this.context = context;
        }

        public @jakarta.annotation.Nonnull VcsMode mode() {
            return this.mode;
        }
        public VcsDiffRequest mode(@jakarta.annotation.Nonnull VcsMode mode) {
            this.mode = mode;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public VcsDiffRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.location = location;
            return this;
        }

        public @jakarta.annotation.Nullable String base() {
            return this.base;
        }
        public VcsDiffRequest base(@jakarta.annotation.Nullable String base) {
            this.base = base;
            return this;
        }

        public @jakarta.annotation.Nullable String context() {
            return this.context;
        }
        public VcsDiffRequest context(@jakarta.annotation.Nullable String context) {
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
                Objects.equals(this.location, request.location()) &&
                Objects.equals(this.base, request.base()) &&
                Objects.equals(this.context, request.context());
        }

        @Override
        public int hashCode() {
            return Objects.hash(mode, location, base, context);
        }
    }

    /**
     * VCS diff
     * Diff HEAD to the working copy (working), the base merge-base to the working copy (branch), or the base merge-base to HEAD (committed). Omitting base preserves repository-default comparison; supplying it overrides the comparison without saving it.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The vcsDiff request parameters as object
     * @return VcsDiff200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<VcsDiff200Response> vcsDiff(VcsDiffRequest requestParameters) throws WebClientResponseException {
        return this.vcsDiff(requestParameters.mode(), requestParameters.location(), requestParameters.base(), requestParameters.context());
    }

    /**
     * VCS diff
     * Diff HEAD to the working copy (working), the base merge-base to the working copy (branch), or the base merge-base to HEAD (committed). Omitting base preserves repository-default comparison; supplying it overrides the comparison without saving it.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The vcsDiff request parameters as object
     * @return ResponseEntity&lt;VcsDiff200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<VcsDiff200Response>> vcsDiffWithHttpInfo(VcsDiffRequest requestParameters) throws WebClientResponseException {
        return this.vcsDiffWithHttpInfo(requestParameters.mode(), requestParameters.location(), requestParameters.base(), requestParameters.context());
    }

    /**
     * VCS diff
     * Diff HEAD to the working copy (working), the base merge-base to the working copy (branch), or the base merge-base to HEAD (committed). Omitting base preserves repository-default comparison; supplying it overrides the comparison without saving it.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param requestParameters The vcsDiff request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec vcsDiffWithResponseSpec(VcsDiffRequest requestParameters) throws WebClientResponseException {
        return this.vcsDiffWithResponseSpec(requestParameters.mode(), requestParameters.location(), requestParameters.base(), requestParameters.context());
    }


    /**
     * VCS diff
     * Diff HEAD to the working copy (working), the base merge-base to the working copy (branch), or the base merge-base to HEAD (committed). Omitting base preserves repository-default comparison; supplying it overrides the comparison without saving it.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param mode The mode parameter
     * @param location The location parameter
     * @param base The base parameter
     * @param context The context parameter
     * @return VcsDiff200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec vcsDiffRequestCreation(@jakarta.annotation.Nonnull VcsMode mode, @jakarta.annotation.Nullable AgentListLocationParameter location, @jakarta.annotation.Nullable String base, @jakarta.annotation.Nullable String context) throws WebClientResponseException {
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

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "mode", mode));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "base", base));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "context", context));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<VcsDiff200Response> localVarReturnType = new ParameterizedTypeReference<VcsDiff200Response>() {};
        return apiClient.invokeAPI("/api/vcs/diff", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * VCS diff
     * Diff HEAD to the working copy (working), the base merge-base to the working copy (branch), or the base merge-base to HEAD (committed). Omitting base preserves repository-default comparison; supplying it overrides the comparison without saving it.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param mode The mode parameter
     * @param location The location parameter
     * @param base The base parameter
     * @param context The context parameter
     * @return VcsDiff200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<VcsDiff200Response> vcsDiff(@jakarta.annotation.Nonnull VcsMode mode, @jakarta.annotation.Nullable AgentListLocationParameter location, @jakarta.annotation.Nullable String base, @jakarta.annotation.Nullable String context) throws WebClientResponseException {
        ParameterizedTypeReference<VcsDiff200Response> localVarReturnType = new ParameterizedTypeReference<VcsDiff200Response>() {};
        return vcsDiffRequestCreation(mode, location, base, context).bodyToMono(localVarReturnType);
    }

    /**
     * VCS diff
     * Diff HEAD to the working copy (working), the base merge-base to the working copy (branch), or the base merge-base to HEAD (committed). Omitting base preserves repository-default comparison; supplying it overrides the comparison without saving it.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param mode The mode parameter
     * @param location The location parameter
     * @param base The base parameter
     * @param context The context parameter
     * @return ResponseEntity&lt;VcsDiff200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<VcsDiff200Response>> vcsDiffWithHttpInfo(@jakarta.annotation.Nonnull VcsMode mode, @jakarta.annotation.Nullable AgentListLocationParameter location, @jakarta.annotation.Nullable String base, @jakarta.annotation.Nullable String context) throws WebClientResponseException {
        ParameterizedTypeReference<VcsDiff200Response> localVarReturnType = new ParameterizedTypeReference<VcsDiff200Response>() {};
        return vcsDiffRequestCreation(mode, location, base, context).toEntity(localVarReturnType);
    }

    /**
     * VCS diff
     * Diff HEAD to the working copy (working), the base merge-base to the working copy (branch), or the base merge-base to HEAD (committed). Omitting base preserves repository-default comparison; supplying it overrides the comparison without saving it.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>503</b> - ServiceUnavailableError
     * @param mode The mode parameter
     * @param location The location parameter
     * @param base The base parameter
     * @param context The context parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec vcsDiffWithResponseSpec(@jakarta.annotation.Nonnull VcsMode mode, @jakarta.annotation.Nullable AgentListLocationParameter location, @jakarta.annotation.Nullable String base, @jakarta.annotation.Nullable String context) throws WebClientResponseException {
        return vcsDiffRequestCreation(mode, location, base, context);
    }

    /**
     * VCS info
     * Get current and default branch information for the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return VcsGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec vcsGetRequestCreation(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = null;
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<VcsGet200Response> localVarReturnType = new ParameterizedTypeReference<VcsGet200Response>() {};
        return apiClient.invokeAPI("/api/vcs", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * VCS info
     * Get current and default branch information for the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return VcsGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<VcsGet200Response> vcsGet(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<VcsGet200Response> localVarReturnType = new ParameterizedTypeReference<VcsGet200Response>() {};
        return vcsGetRequestCreation(location).bodyToMono(localVarReturnType);
    }

    /**
     * VCS info
     * Get current and default branch information for the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseEntity&lt;VcsGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<VcsGet200Response>> vcsGetWithHttpInfo(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<VcsGet200Response> localVarReturnType = new ParameterizedTypeReference<VcsGet200Response>() {};
        return vcsGetRequestCreation(location).toEntity(localVarReturnType);
    }

    /**
     * VCS info
     * Get current and default branch information for the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec vcsGetWithResponseSpec(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return vcsGetRequestCreation(location);
    }

    /**
     * VCS status
     * List uncommitted working-copy changes relative to the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return VcsStatus200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec vcsStatusRequestCreation(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = null;
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<VcsStatus200Response> localVarReturnType = new ParameterizedTypeReference<VcsStatus200Response>() {};
        return apiClient.invokeAPI("/api/vcs/status", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * VCS status
     * List uncommitted working-copy changes relative to the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return VcsStatus200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<VcsStatus200Response> vcsStatus(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<VcsStatus200Response> localVarReturnType = new ParameterizedTypeReference<VcsStatus200Response>() {};
        return vcsStatusRequestCreation(location).bodyToMono(localVarReturnType);
    }

    /**
     * VCS status
     * List uncommitted working-copy changes relative to the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseEntity&lt;VcsStatus200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<VcsStatus200Response>> vcsStatusWithHttpInfo(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<VcsStatus200Response> localVarReturnType = new ParameterizedTypeReference<VcsStatus200Response>() {};
        return vcsStatusRequestCreation(location).toEntity(localVarReturnType);
    }

    /**
     * VCS status
     * List uncommitted working-copy changes relative to the requested location.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec vcsStatusWithResponseSpec(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return vcsStatusRequestCreation(location);
    }
}
