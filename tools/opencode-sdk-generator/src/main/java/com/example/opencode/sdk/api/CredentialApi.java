package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.CredentialUpdateRequest;
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
public class CredentialApi {
    private ApiClient apiClient;

    public CredentialApi() {
        this(new ApiClient());
    }

    public CredentialApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Activate credential
     * Activate a stored integration credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param credentialID The credentialID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec credentialActivateRequestCreation(@jakarta.annotation.Nonnull String credentialID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'credentialID' is set
        if (credentialID == null) {
            throw new WebClientResponseException("Missing the required parameter 'credentialID' when calling credentialActivate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("credentialID", credentialID);

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
        return apiClient.invokeAPI("/api/credential/{credentialID}/activate", HttpMethod.POST, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Activate credential
     * Activate a stored integration credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param credentialID The credentialID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> credentialActivate(@jakarta.annotation.Nonnull String credentialID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return credentialActivateRequestCreation(credentialID).bodyToMono(localVarReturnType);
    }

    /**
     * Activate credential
     * Activate a stored integration credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param credentialID The credentialID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> credentialActivateWithHttpInfo(@jakarta.annotation.Nonnull String credentialID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return credentialActivateRequestCreation(credentialID).toEntity(localVarReturnType);
    }

    /**
     * Activate credential
     * Activate a stored integration credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param credentialID The credentialID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec credentialActivateWithResponseSpec(@jakarta.annotation.Nonnull String credentialID) throws WebClientResponseException {
        return credentialActivateRequestCreation(credentialID);
    }

    /**
     * Remove credential
     * Remove a stored integration credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param credentialID The credentialID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec credentialRemoveRequestCreation(@jakarta.annotation.Nullable String credentialID) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'credentialID' is set
        if (credentialID == null) {
            throw new WebClientResponseException("Missing the required parameter 'credentialID' when calling credentialRemove", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("credentialID", credentialID);

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
        return apiClient.invokeAPI("/api/credential/{credentialID}", HttpMethod.DELETE, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Remove credential
     * Remove a stored integration credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param credentialID The credentialID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> credentialRemove(@jakarta.annotation.Nullable String credentialID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return credentialRemoveRequestCreation(credentialID).bodyToMono(localVarReturnType);
    }

    /**
     * Remove credential
     * Remove a stored integration credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param credentialID The credentialID parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> credentialRemoveWithHttpInfo(@jakarta.annotation.Nullable String credentialID) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return credentialRemoveRequestCreation(credentialID).toEntity(localVarReturnType);
    }

    /**
     * Remove credential
     * Remove a stored integration credential.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param credentialID The credentialID parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec credentialRemoveWithResponseSpec(@jakarta.annotation.Nullable String credentialID) throws WebClientResponseException {
        return credentialRemoveRequestCreation(credentialID);
    }

    public class CredentialUpdateRequest {
        private @jakarta.annotation.Nonnull String credentialID;
        private @jakarta.annotation.Nonnull CredentialUpdateRequest credentialUpdateRequest;

        public CredentialUpdateRequest() {}

        public CredentialUpdateRequest(@jakarta.annotation.Nonnull String credentialID, @jakarta.annotation.Nonnull CredentialUpdateRequest credentialUpdateRequest) {
            this.credentialID = credentialID;
            this.credentialUpdateRequest = credentialUpdateRequest;
        }

        public @jakarta.annotation.Nonnull String credentialID() {
            return this.credentialID;
        }
        public CredentialUpdateRequest credentialID(@jakarta.annotation.Nonnull String credentialID) {
            this.credentialID = credentialID;
            return this;
        }

        public @jakarta.annotation.Nonnull CredentialUpdateRequest credentialUpdateRequest() {
            return this.credentialUpdateRequest;
        }
        public CredentialUpdateRequest credentialUpdateRequest(@jakarta.annotation.Nonnull CredentialUpdateRequest credentialUpdateRequest) {
            this.credentialUpdateRequest = credentialUpdateRequest;
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
            CredentialUpdateRequest request = (CredentialUpdateRequest) o;
            return Objects.equals(this.credentialID, request.credentialID()) &&
                Objects.equals(this.credentialUpdateRequest, request.credentialUpdateRequest());
        }

        @Override
        public int hashCode() {
            return Objects.hash(credentialID, credentialUpdateRequest);
        }
    }

    /**
     * Update credential
     * Update a stored credential label.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The credentialUpdate request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> credentialUpdate(CredentialUpdateRequest requestParameters) throws WebClientResponseException {
        return this.credentialUpdate(requestParameters.credentialID(), requestParameters.credentialUpdateRequest());
    }

    /**
     * Update credential
     * Update a stored credential label.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The credentialUpdate request parameters as object
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> credentialUpdateWithHttpInfo(CredentialUpdateRequest requestParameters) throws WebClientResponseException {
        return this.credentialUpdateWithHttpInfo(requestParameters.credentialID(), requestParameters.credentialUpdateRequest());
    }

    /**
     * Update credential
     * Update a stored credential label.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param requestParameters The credentialUpdate request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec credentialUpdateWithResponseSpec(CredentialUpdateRequest requestParameters) throws WebClientResponseException {
        return this.credentialUpdateWithResponseSpec(requestParameters.credentialID(), requestParameters.credentialUpdateRequest());
    }


    /**
     * Update credential
     * Update a stored credential label.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param credentialID The credentialID parameter
     * @param credentialUpdateRequest The credentialUpdateRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec credentialUpdateRequestCreation(@jakarta.annotation.Nonnull String credentialID, @jakarta.annotation.Nonnull CredentialUpdateRequest credentialUpdateRequest) throws WebClientResponseException {
        Object postBody = credentialUpdateRequest;
        // verify the required parameter 'credentialID' is set
        if (credentialID == null) {
            throw new WebClientResponseException("Missing the required parameter 'credentialID' when calling credentialUpdate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'credentialUpdateRequest' is set
        if (credentialUpdateRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'credentialUpdateRequest' when calling credentialUpdate", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("credentialID", credentialID);

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
        return apiClient.invokeAPI("/api/credential/{credentialID}", HttpMethod.PATCH, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Update credential
     * Update a stored credential label.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param credentialID The credentialID parameter
     * @param credentialUpdateRequest The credentialUpdateRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> credentialUpdate(@jakarta.annotation.Nonnull String credentialID, @jakarta.annotation.Nonnull CredentialUpdateRequest credentialUpdateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return credentialUpdateRequestCreation(credentialID, credentialUpdateRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Update credential
     * Update a stored credential label.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param credentialID The credentialID parameter
     * @param credentialUpdateRequest The credentialUpdateRequest parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> credentialUpdateWithHttpInfo(@jakarta.annotation.Nonnull String credentialID, @jakarta.annotation.Nonnull CredentialUpdateRequest credentialUpdateRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return credentialUpdateRequestCreation(credentialID, credentialUpdateRequest).toEntity(localVarReturnType);
    }

    /**
     * Update credential
     * Update a stored credential label.
     * <p><b>204</b> - &lt;No Content&gt;
     * <p><b>400</b> - InvalidRequestError
     * <p><b>401</b> - UnauthorizedError
     * @param credentialID The credentialID parameter
     * @param credentialUpdateRequest The credentialUpdateRequest parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec credentialUpdateWithResponseSpec(@jakarta.annotation.Nonnull String credentialID, @jakarta.annotation.Nonnull CredentialUpdateRequest credentialUpdateRequest) throws WebClientResponseException {
        return credentialUpdateRequestCreation(credentialID, credentialUpdateRequest);
    }
}
