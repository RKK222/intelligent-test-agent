package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.InvalidRequestErrorEncoded;
import com.example.opencode.sdk.model.PairingCode;
import com.example.opencode.sdk.model.PairingSession;
import com.example.opencode.sdk.model.ServerConnect401Response;
import com.example.opencode.sdk.model.ServerInfo;
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
public class ServerApi {
    private ApiClient apiClient;

    public ServerApi() {
        this(new ApiClient());
    }

    public ServerApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Redeem pairing code
     * Redeem a pairing code. Browsers receive a session cookie and a redirect to the web app; requests that accept JSON receive a session token to use as the password.
     * <p><b>200</b> - PairingSession
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param code The code parameter
     * @return PairingSession
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec serverConnectRequestCreation(@jakarta.annotation.Nullable String code) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'code' is set
        if (code == null) {
            throw new WebClientResponseException("Missing the required parameter 'code' when calling serverConnect", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("code", code);

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

        ParameterizedTypeReference<PairingSession> localVarReturnType = new ParameterizedTypeReference<PairingSession>() {};
        return apiClient.invokeAPI("/auth/connect/{code}", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Redeem pairing code
     * Redeem a pairing code. Browsers receive a session cookie and a redirect to the web app; requests that accept JSON receive a session token to use as the password.
     * <p><b>200</b> - PairingSession
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param code The code parameter
     * @return PairingSession
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<PairingSession> serverConnect(@jakarta.annotation.Nullable String code) throws WebClientResponseException {
        ParameterizedTypeReference<PairingSession> localVarReturnType = new ParameterizedTypeReference<PairingSession>() {};
        return serverConnectRequestCreation(code).bodyToMono(localVarReturnType);
    }

    /**
     * Redeem pairing code
     * Redeem a pairing code. Browsers receive a session cookie and a redirect to the web app; requests that accept JSON receive a session token to use as the password.
     * <p><b>200</b> - PairingSession
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param code The code parameter
     * @return ResponseEntity&lt;PairingSession&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<PairingSession>> serverConnectWithHttpInfo(@jakarta.annotation.Nullable String code) throws WebClientResponseException {
        ParameterizedTypeReference<PairingSession> localVarReturnType = new ParameterizedTypeReference<PairingSession>() {};
        return serverConnectRequestCreation(code).toEntity(localVarReturnType);
    }

    /**
     * Redeem pairing code
     * Redeem a pairing code. Browsers receive a session cookie and a redirect to the web app; requests that accept JSON receive a session token to use as the password.
     * <p><b>200</b> - PairingSession
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param code The code parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec serverConnectWithResponseSpec(@jakarta.annotation.Nullable String code) throws WebClientResponseException {
        return serverConnectRequestCreation(code);
    }

    /**
     * Get server info
     * Return the server identity, connection URLs, paths, and readiness status.
     * <p><b>200</b> - ServerInfo
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return ServerInfo
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec serverInfoRequestCreation() throws WebClientResponseException {
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

        ParameterizedTypeReference<ServerInfo> localVarReturnType = new ParameterizedTypeReference<ServerInfo>() {};
        return apiClient.invokeAPI("/api/info", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get server info
     * Return the server identity, connection URLs, paths, and readiness status.
     * <p><b>200</b> - ServerInfo
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return ServerInfo
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ServerInfo> serverInfo() throws WebClientResponseException {
        ParameterizedTypeReference<ServerInfo> localVarReturnType = new ParameterizedTypeReference<ServerInfo>() {};
        return serverInfoRequestCreation().bodyToMono(localVarReturnType);
    }

    /**
     * Get server info
     * Return the server identity, connection URLs, paths, and readiness status.
     * <p><b>200</b> - ServerInfo
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return ResponseEntity&lt;ServerInfo&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ServerInfo>> serverInfoWithHttpInfo() throws WebClientResponseException {
        ParameterizedTypeReference<ServerInfo> localVarReturnType = new ParameterizedTypeReference<ServerInfo>() {};
        return serverInfoRequestCreation().toEntity(localVarReturnType);
    }

    /**
     * Get server info
     * Return the server identity, connection URLs, paths, and readiness status.
     * <p><b>200</b> - ServerInfo
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec serverInfoWithResponseSpec() throws WebClientResponseException {
        return serverInfoRequestCreation();
    }

    /**
     * Create pairing code
     * Create a short-lived, single-use code for a /auth/connect/:code pairing link.
     * <p><b>200</b> - PairingCode
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return PairingCode
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec serverPairRequestCreation() throws WebClientResponseException {
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

        ParameterizedTypeReference<PairingCode> localVarReturnType = new ParameterizedTypeReference<PairingCode>() {};
        return apiClient.invokeAPI("/api/pair", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Create pairing code
     * Create a short-lived, single-use code for a /auth/connect/:code pairing link.
     * <p><b>200</b> - PairingCode
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return PairingCode
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<PairingCode> serverPair() throws WebClientResponseException {
        ParameterizedTypeReference<PairingCode> localVarReturnType = new ParameterizedTypeReference<PairingCode>() {};
        return serverPairRequestCreation().bodyToMono(localVarReturnType);
    }

    /**
     * Create pairing code
     * Create a short-lived, single-use code for a /auth/connect/:code pairing link.
     * <p><b>200</b> - PairingCode
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return ResponseEntity&lt;PairingCode&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<PairingCode>> serverPairWithHttpInfo() throws WebClientResponseException {
        ParameterizedTypeReference<PairingCode> localVarReturnType = new ParameterizedTypeReference<PairingCode>() {};
        return serverPairRequestCreation().toEntity(localVarReturnType);
    }

    /**
     * Create pairing code
     * Create a short-lived, single-use code for a /auth/connect/:code pairing link.
     * <p><b>200</b> - PairingCode
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec serverPairWithResponseSpec() throws WebClientResponseException {
        return serverPairRequestCreation();
    }
}
