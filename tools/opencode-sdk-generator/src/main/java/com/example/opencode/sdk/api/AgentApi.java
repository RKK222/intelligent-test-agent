package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.AgentGet200Response;
import com.example.opencode.sdk.model.AgentList200Response;
import com.example.opencode.sdk.model.AgentListLocationParameter;
import com.example.opencode.sdk.model.AgentNotFoundErrorEncoded;
import com.example.opencode.sdk.model.InvalidRequestErrorEncoded;
import com.example.opencode.sdk.model.UnauthorizedErrorEncoded;

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
public class AgentApi {
    private ApiClient apiClient;

    public AgentApi() {
        this(new ApiClient());
    }

    public AgentApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public class AgentGetRequest {
        private @jakarta.annotation.Nullable String agentID;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;

        public AgentGetRequest() {}

        public AgentGetRequest(@jakarta.annotation.Nullable String agentID, @jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.agentID = agentID;
            this.location = location;
        }

        public @jakarta.annotation.Nullable String agentID() {
            return this.agentID;
        }
        public AgentGetRequest agentID(@jakarta.annotation.Nullable String agentID) {
            this.agentID = agentID;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public AgentGetRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.location = location;
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
            AgentGetRequest request = (AgentGetRequest) o;
            return Objects.equals(this.agentID, request.agentID()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(agentID, location);
        }
    }

    /**
     * Get agent
     * Retrieve a single currently registered agent.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - AgentNotFoundError
     * @param requestParameters The agentGet request parameters as object
     * @return AgentGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<AgentGet200Response> agentGet(AgentGetRequest requestParameters) throws WebClientResponseException {
        return this.agentGet(requestParameters.agentID(), requestParameters.location());
    }

    /**
     * Get agent
     * Retrieve a single currently registered agent.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - AgentNotFoundError
     * @param requestParameters The agentGet request parameters as object
     * @return ResponseEntity&lt;AgentGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<AgentGet200Response>> agentGetWithHttpInfo(AgentGetRequest requestParameters) throws WebClientResponseException {
        return this.agentGetWithHttpInfo(requestParameters.agentID(), requestParameters.location());
    }

    /**
     * Get agent
     * Retrieve a single currently registered agent.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - AgentNotFoundError
     * @param requestParameters The agentGet request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec agentGetWithResponseSpec(AgentGetRequest requestParameters) throws WebClientResponseException {
        return this.agentGetWithResponseSpec(requestParameters.agentID(), requestParameters.location());
    }


    /**
     * Get agent
     * Retrieve a single currently registered agent.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - AgentNotFoundError
     * @param agentID The agentID parameter
     * @param location The location parameter
     * @return AgentGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec agentGetRequestCreation(@jakarta.annotation.Nullable String agentID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'agentID' is set
        if (agentID == null) {
            throw new WebClientResponseException("Missing the required parameter 'agentID' when calling agentGet", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("agentID", agentID);

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

        ParameterizedTypeReference<AgentGet200Response> localVarReturnType = new ParameterizedTypeReference<AgentGet200Response>() {};
        return apiClient.invokeAPI("/api/agent/{agentID}", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get agent
     * Retrieve a single currently registered agent.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - AgentNotFoundError
     * @param agentID The agentID parameter
     * @param location The location parameter
     * @return AgentGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<AgentGet200Response> agentGet(@jakarta.annotation.Nullable String agentID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<AgentGet200Response> localVarReturnType = new ParameterizedTypeReference<AgentGet200Response>() {};
        return agentGetRequestCreation(agentID, location).bodyToMono(localVarReturnType);
    }

    /**
     * Get agent
     * Retrieve a single currently registered agent.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - AgentNotFoundError
     * @param agentID The agentID parameter
     * @param location The location parameter
     * @return ResponseEntity&lt;AgentGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<AgentGet200Response>> agentGetWithHttpInfo(@jakarta.annotation.Nullable String agentID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<AgentGet200Response> localVarReturnType = new ParameterizedTypeReference<AgentGet200Response>() {};
        return agentGetRequestCreation(agentID, location).toEntity(localVarReturnType);
    }

    /**
     * Get agent
     * Retrieve a single currently registered agent.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>404</b> - AgentNotFoundError
     * @param agentID The agentID parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec agentGetWithResponseSpec(@jakarta.annotation.Nullable String agentID, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return agentGetRequestCreation(agentID, location);
    }

    /**
     * List agents
     * Retrieve currently registered agents.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return AgentList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec agentListRequestCreation(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
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

        ParameterizedTypeReference<AgentList200Response> localVarReturnType = new ParameterizedTypeReference<AgentList200Response>() {};
        return apiClient.invokeAPI("/api/agent", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List agents
     * Retrieve currently registered agents.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return AgentList200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<AgentList200Response> agentList(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<AgentList200Response> localVarReturnType = new ParameterizedTypeReference<AgentList200Response>() {};
        return agentListRequestCreation(location).bodyToMono(localVarReturnType);
    }

    /**
     * List agents
     * Retrieve currently registered agents.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseEntity&lt;AgentList200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<AgentList200Response>> agentListWithHttpInfo(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<AgentList200Response> localVarReturnType = new ParameterizedTypeReference<AgentList200Response>() {};
        return agentListRequestCreation(location).toEntity(localVarReturnType);
    }

    /**
     * List agents
     * Retrieve currently registered agents.
     * <p><b>200</b> - Success
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec agentListWithResponseSpec(@jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return agentListRequestCreation(location);
    }
}
