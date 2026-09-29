package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.AgentListLocationParameter;
import com.example.opencode.sdk.model.RpcCall400Response;
import com.example.opencode.sdk.model.RpcInput;
import com.example.opencode.sdk.model.RpcInternalErrorEncoded;
import com.example.opencode.sdk.model.RpcOutput;
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
public class RpcApi {
    private ApiClient apiClient;

    public RpcApi() {
        this(new ApiClient());
    }

    public RpcApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public class RpcCallRequest {
        private @jakarta.annotation.Nullable String rpcID;
        private @jakarta.annotation.Nullable String method;
        private @jakarta.annotation.Nonnull RpcInput rpcInput;
        private @jakarta.annotation.Nullable AgentListLocationParameter location;

        public RpcCallRequest() {}

        public RpcCallRequest(@jakarta.annotation.Nullable String rpcID, @jakarta.annotation.Nullable String method, @jakarta.annotation.Nonnull RpcInput rpcInput, @jakarta.annotation.Nullable AgentListLocationParameter location) {
            this.rpcID = rpcID;
            this.method = method;
            this.rpcInput = rpcInput;
            this.location = location;
        }

        public @jakarta.annotation.Nullable String rpcID() {
            return this.rpcID;
        }
        public RpcCallRequest rpcID(@jakarta.annotation.Nullable String rpcID) {
            this.rpcID = rpcID;
            return this;
        }

        public @jakarta.annotation.Nullable String method() {
            return this.method;
        }
        public RpcCallRequest method(@jakarta.annotation.Nullable String method) {
            this.method = method;
            return this;
        }

        public @jakarta.annotation.Nonnull RpcInput rpcInput() {
            return this.rpcInput;
        }
        public RpcCallRequest rpcInput(@jakarta.annotation.Nonnull RpcInput rpcInput) {
            this.rpcInput = rpcInput;
            return this;
        }

        public @jakarta.annotation.Nullable AgentListLocationParameter location() {
            return this.location;
        }
        public RpcCallRequest location(@jakarta.annotation.Nullable AgentListLocationParameter location) {
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
            RpcCallRequest request = (RpcCallRequest) o;
            return Objects.equals(this.rpcID, request.rpcID()) &&
                Objects.equals(this.method, request.method()) &&
                Objects.equals(this.rpcInput, request.rpcInput()) &&
                Objects.equals(this.location, request.location());
        }

        @Override
        public int hashCode() {
            return Objects.hash(rpcID, method, rpcInput, location);
        }
    }

    /**
     * Call a plugin RPC
     * Dispatch a method to the currently registered RPC at the requested location.
     * <p><b>200</b> - Rpc.Output
     * <p><b>400</b> - RpcError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>500</b> - RpcInternalError
     * @param requestParameters The rpcCall request parameters as object
     * @return RpcOutput
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<RpcOutput> rpcCall(RpcCallRequest requestParameters) throws WebClientResponseException {
        return this.rpcCall(requestParameters.rpcID(), requestParameters.method(), requestParameters.rpcInput(), requestParameters.location());
    }

    /**
     * Call a plugin RPC
     * Dispatch a method to the currently registered RPC at the requested location.
     * <p><b>200</b> - Rpc.Output
     * <p><b>400</b> - RpcError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>500</b> - RpcInternalError
     * @param requestParameters The rpcCall request parameters as object
     * @return ResponseEntity&lt;RpcOutput&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<RpcOutput>> rpcCallWithHttpInfo(RpcCallRequest requestParameters) throws WebClientResponseException {
        return this.rpcCallWithHttpInfo(requestParameters.rpcID(), requestParameters.method(), requestParameters.rpcInput(), requestParameters.location());
    }

    /**
     * Call a plugin RPC
     * Dispatch a method to the currently registered RPC at the requested location.
     * <p><b>200</b> - Rpc.Output
     * <p><b>400</b> - RpcError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>500</b> - RpcInternalError
     * @param requestParameters The rpcCall request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec rpcCallWithResponseSpec(RpcCallRequest requestParameters) throws WebClientResponseException {
        return this.rpcCallWithResponseSpec(requestParameters.rpcID(), requestParameters.method(), requestParameters.rpcInput(), requestParameters.location());
    }


    /**
     * Call a plugin RPC
     * Dispatch a method to the currently registered RPC at the requested location.
     * <p><b>200</b> - Rpc.Output
     * <p><b>400</b> - RpcError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>500</b> - RpcInternalError
     * @param rpcID The rpcID parameter
     * @param method The method parameter
     * @param rpcInput The rpcInput parameter
     * @param location The location parameter
     * @return RpcOutput
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec rpcCallRequestCreation(@jakarta.annotation.Nullable String rpcID, @jakarta.annotation.Nullable String method, @jakarta.annotation.Nonnull RpcInput rpcInput, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        Object postBody = rpcInput;
        // verify the required parameter 'rpcID' is set
        if (rpcID == null) {
            throw new WebClientResponseException("Missing the required parameter 'rpcID' when calling rpcCall", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'method' is set
        if (method == null) {
            throw new WebClientResponseException("Missing the required parameter 'method' when calling rpcCall", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'rpcInput' is set
        if (rpcInput == null) {
            throw new WebClientResponseException("Missing the required parameter 'rpcInput' when calling rpcCall", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("rpcID", rpcID);
        pathParams.put("method", method);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", location.getDirectory()));

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<RpcOutput> localVarReturnType = new ParameterizedTypeReference<RpcOutput>() {};
        return apiClient.invokeAPI("/api/rpc/{rpcID}/{method}", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Call a plugin RPC
     * Dispatch a method to the currently registered RPC at the requested location.
     * <p><b>200</b> - Rpc.Output
     * <p><b>400</b> - RpcError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>500</b> - RpcInternalError
     * @param rpcID The rpcID parameter
     * @param method The method parameter
     * @param rpcInput The rpcInput parameter
     * @param location The location parameter
     * @return RpcOutput
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<RpcOutput> rpcCall(@jakarta.annotation.Nullable String rpcID, @jakarta.annotation.Nullable String method, @jakarta.annotation.Nonnull RpcInput rpcInput, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<RpcOutput> localVarReturnType = new ParameterizedTypeReference<RpcOutput>() {};
        return rpcCallRequestCreation(rpcID, method, rpcInput, location).bodyToMono(localVarReturnType);
    }

    /**
     * Call a plugin RPC
     * Dispatch a method to the currently registered RPC at the requested location.
     * <p><b>200</b> - Rpc.Output
     * <p><b>400</b> - RpcError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>500</b> - RpcInternalError
     * @param rpcID The rpcID parameter
     * @param method The method parameter
     * @param rpcInput The rpcInput parameter
     * @param location The location parameter
     * @return ResponseEntity&lt;RpcOutput&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<RpcOutput>> rpcCallWithHttpInfo(@jakarta.annotation.Nullable String rpcID, @jakarta.annotation.Nullable String method, @jakarta.annotation.Nonnull RpcInput rpcInput, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        ParameterizedTypeReference<RpcOutput> localVarReturnType = new ParameterizedTypeReference<RpcOutput>() {};
        return rpcCallRequestCreation(rpcID, method, rpcInput, location).toEntity(localVarReturnType);
    }

    /**
     * Call a plugin RPC
     * Dispatch a method to the currently registered RPC at the requested location.
     * <p><b>200</b> - Rpc.Output
     * <p><b>400</b> - RpcError | InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * <p><b>500</b> - RpcInternalError
     * @param rpcID The rpcID parameter
     * @param method The method parameter
     * @param rpcInput The rpcInput parameter
     * @param location The location parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec rpcCallWithResponseSpec(@jakarta.annotation.Nullable String rpcID, @jakarta.annotation.Nullable String method, @jakarta.annotation.Nonnull RpcInput rpcInput, @jakarta.annotation.Nullable AgentListLocationParameter location) throws WebClientResponseException {
        return rpcCallRequestCreation(rpcID, method, rpcInput, location);
    }
}
