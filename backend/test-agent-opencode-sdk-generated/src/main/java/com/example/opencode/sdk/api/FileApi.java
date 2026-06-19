package com.example.opencode.sdk.api;

import com.example.opencode.sdk.ApiClient;

import com.example.opencode.sdk.model.BadRequestError;
import com.example.opencode.sdk.model.FileContent;
import com.example.opencode.sdk.model.FileNode;
import com.example.opencode.sdk.model.FindText200ResponseInner;
import com.example.opencode.sdk.model.ModelFile;
import com.example.opencode.sdk.model.Symbol;

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
public class FileApi {
    private ApiClient apiClient;

    public FileApi() {
        this(new ApiClient());
    }

    public FileApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public class FileListRequest {
        private @jakarta.annotation.Nonnull String path;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public FileListRequest() {}

        public FileListRequest(@jakarta.annotation.Nonnull String path, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.path = path;
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nonnull String path() {
            return this.path;
        }
        public FileListRequest path(@jakarta.annotation.Nonnull String path) {
            this.path = path;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public FileListRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public FileListRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            FileListRequest request = (FileListRequest) o;
            return Objects.equals(this.path, request.path()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, directory, workspace);
        }
    }

    /**
     * List files
     * List files and directories in a specified path.
     * <p><b>200</b> - Files and directories
     * <p><b>400</b> - Bad request
     * @param requestParameters The fileList request parameters as object
     * @return List&lt;FileNode&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<FileNode> fileList(FileListRequest requestParameters) throws WebClientResponseException {
        return this.fileList(requestParameters.path(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List files
     * List files and directories in a specified path.
     * <p><b>200</b> - Files and directories
     * <p><b>400</b> - Bad request
     * @param requestParameters The fileList request parameters as object
     * @return ResponseEntity&lt;List&lt;FileNode&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<FileNode>>> fileListWithHttpInfo(FileListRequest requestParameters) throws WebClientResponseException {
        return this.fileListWithHttpInfo(requestParameters.path(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * List files
     * List files and directories in a specified path.
     * <p><b>200</b> - Files and directories
     * <p><b>400</b> - Bad request
     * @param requestParameters The fileList request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec fileListWithResponseSpec(FileListRequest requestParameters) throws WebClientResponseException {
        return this.fileListWithResponseSpec(requestParameters.path(), requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * List files
     * List files and directories in a specified path.
     * <p><b>200</b> - Files and directories
     * <p><b>400</b> - Bad request
     * @param path The path parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;FileNode&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec fileListRequestCreation(@jakarta.annotation.Nonnull String path, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'path' is set
        if (path == null) {
            throw new WebClientResponseException("Missing the required parameter 'path' when calling fileList", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", directory));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", workspace));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "path", path));

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<FileNode> localVarReturnType = new ParameterizedTypeReference<FileNode>() {};
        return apiClient.invokeAPI("/file", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List files
     * List files and directories in a specified path.
     * <p><b>200</b> - Files and directories
     * <p><b>400</b> - Bad request
     * @param path The path parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;FileNode&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<FileNode> fileList(@jakarta.annotation.Nonnull String path, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<FileNode> localVarReturnType = new ParameterizedTypeReference<FileNode>() {};
        return fileListRequestCreation(path, directory, workspace).bodyToFlux(localVarReturnType);
    }

    /**
     * List files
     * List files and directories in a specified path.
     * <p><b>200</b> - Files and directories
     * <p><b>400</b> - Bad request
     * @param path The path parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;List&lt;FileNode&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<FileNode>>> fileListWithHttpInfo(@jakarta.annotation.Nonnull String path, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<FileNode> localVarReturnType = new ParameterizedTypeReference<FileNode>() {};
        return fileListRequestCreation(path, directory, workspace).toEntityList(localVarReturnType);
    }

    /**
     * List files
     * List files and directories in a specified path.
     * <p><b>200</b> - Files and directories
     * <p><b>400</b> - Bad request
     * @param path The path parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec fileListWithResponseSpec(@jakarta.annotation.Nonnull String path, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return fileListRequestCreation(path, directory, workspace);
    }

    public class FileReadRequest {
        private @jakarta.annotation.Nonnull String path;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public FileReadRequest() {}

        public FileReadRequest(@jakarta.annotation.Nonnull String path, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.path = path;
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nonnull String path() {
            return this.path;
        }
        public FileReadRequest path(@jakarta.annotation.Nonnull String path) {
            this.path = path;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public FileReadRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public FileReadRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            FileReadRequest request = (FileReadRequest) o;
            return Objects.equals(this.path, request.path()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, directory, workspace);
        }
    }

    /**
     * Read file
     * Read the content of a specified file.
     * <p><b>200</b> - File content
     * <p><b>400</b> - Bad request
     * @param requestParameters The fileRead request parameters as object
     * @return FileContent
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<FileContent> fileRead(FileReadRequest requestParameters) throws WebClientResponseException {
        return this.fileRead(requestParameters.path(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Read file
     * Read the content of a specified file.
     * <p><b>200</b> - File content
     * <p><b>400</b> - Bad request
     * @param requestParameters The fileRead request parameters as object
     * @return ResponseEntity&lt;FileContent&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<FileContent>> fileReadWithHttpInfo(FileReadRequest requestParameters) throws WebClientResponseException {
        return this.fileReadWithHttpInfo(requestParameters.path(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Read file
     * Read the content of a specified file.
     * <p><b>200</b> - File content
     * <p><b>400</b> - Bad request
     * @param requestParameters The fileRead request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec fileReadWithResponseSpec(FileReadRequest requestParameters) throws WebClientResponseException {
        return this.fileReadWithResponseSpec(requestParameters.path(), requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Read file
     * Read the content of a specified file.
     * <p><b>200</b> - File content
     * <p><b>400</b> - Bad request
     * @param path The path parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return FileContent
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec fileReadRequestCreation(@jakarta.annotation.Nonnull String path, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'path' is set
        if (path == null) {
            throw new WebClientResponseException("Missing the required parameter 'path' when calling fileRead", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", directory));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", workspace));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "path", path));

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<FileContent> localVarReturnType = new ParameterizedTypeReference<FileContent>() {};
        return apiClient.invokeAPI("/file/content", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Read file
     * Read the content of a specified file.
     * <p><b>200</b> - File content
     * <p><b>400</b> - Bad request
     * @param path The path parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return FileContent
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<FileContent> fileRead(@jakarta.annotation.Nonnull String path, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<FileContent> localVarReturnType = new ParameterizedTypeReference<FileContent>() {};
        return fileReadRequestCreation(path, directory, workspace).bodyToMono(localVarReturnType);
    }

    /**
     * Read file
     * Read the content of a specified file.
     * <p><b>200</b> - File content
     * <p><b>400</b> - Bad request
     * @param path The path parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;FileContent&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<FileContent>> fileReadWithHttpInfo(@jakarta.annotation.Nonnull String path, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<FileContent> localVarReturnType = new ParameterizedTypeReference<FileContent>() {};
        return fileReadRequestCreation(path, directory, workspace).toEntity(localVarReturnType);
    }

    /**
     * Read file
     * Read the content of a specified file.
     * <p><b>200</b> - File content
     * <p><b>400</b> - Bad request
     * @param path The path parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec fileReadWithResponseSpec(@jakarta.annotation.Nonnull String path, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return fileReadRequestCreation(path, directory, workspace);
    }

    public class FileStatusRequest {
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public FileStatusRequest() {}

        public FileStatusRequest(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public FileStatusRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public FileStatusRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            FileStatusRequest request = (FileStatusRequest) o;
            return Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(directory, workspace);
        }
    }

    /**
     * Get file status
     * Get the git status of all files in the project.
     * <p><b>200</b> - File status
     * <p><b>400</b> - Bad request
     * @param requestParameters The fileStatus request parameters as object
     * @return List&lt;ModelFile&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<ModelFile> fileStatus(FileStatusRequest requestParameters) throws WebClientResponseException {
        return this.fileStatus(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get file status
     * Get the git status of all files in the project.
     * <p><b>200</b> - File status
     * <p><b>400</b> - Bad request
     * @param requestParameters The fileStatus request parameters as object
     * @return ResponseEntity&lt;List&lt;ModelFile&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<ModelFile>>> fileStatusWithHttpInfo(FileStatusRequest requestParameters) throws WebClientResponseException {
        return this.fileStatusWithHttpInfo(requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Get file status
     * Get the git status of all files in the project.
     * <p><b>200</b> - File status
     * <p><b>400</b> - Bad request
     * @param requestParameters The fileStatus request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec fileStatusWithResponseSpec(FileStatusRequest requestParameters) throws WebClientResponseException {
        return this.fileStatusWithResponseSpec(requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Get file status
     * Get the git status of all files in the project.
     * <p><b>200</b> - File status
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;ModelFile&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec fileStatusRequestCreation(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
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

        ParameterizedTypeReference<ModelFile> localVarReturnType = new ParameterizedTypeReference<ModelFile>() {};
        return apiClient.invokeAPI("/file/status", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get file status
     * Get the git status of all files in the project.
     * <p><b>200</b> - File status
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;ModelFile&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<ModelFile> fileStatus(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<ModelFile> localVarReturnType = new ParameterizedTypeReference<ModelFile>() {};
        return fileStatusRequestCreation(directory, workspace).bodyToFlux(localVarReturnType);
    }

    /**
     * Get file status
     * Get the git status of all files in the project.
     * <p><b>200</b> - File status
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;List&lt;ModelFile&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<ModelFile>>> fileStatusWithHttpInfo(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<ModelFile> localVarReturnType = new ParameterizedTypeReference<ModelFile>() {};
        return fileStatusRequestCreation(directory, workspace).toEntityList(localVarReturnType);
    }

    /**
     * Get file status
     * Get the git status of all files in the project.
     * <p><b>200</b> - File status
     * <p><b>400</b> - Bad request
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec fileStatusWithResponseSpec(@jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return fileStatusRequestCreation(directory, workspace);
    }

    public class FindFilesRequest {
        private @jakarta.annotation.Nonnull String query;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;
        private @jakarta.annotation.Nullable String dirs;
        private @jakarta.annotation.Nullable String type;
        private @jakarta.annotation.Nullable Integer limit;

        public FindFilesRequest() {}

        public FindFilesRequest(@jakarta.annotation.Nonnull String query, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable String dirs, @jakarta.annotation.Nullable String type, @jakarta.annotation.Nullable Integer limit) {
            this.query = query;
            this.directory = directory;
            this.workspace = workspace;
            this.dirs = dirs;
            this.type = type;
            this.limit = limit;
        }

        public @jakarta.annotation.Nonnull String query() {
            return this.query;
        }
        public FindFilesRequest query(@jakarta.annotation.Nonnull String query) {
            this.query = query;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public FindFilesRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public FindFilesRequest workspace(@jakarta.annotation.Nullable String workspace) {
            this.workspace = workspace;
            return this;
        }

        public @jakarta.annotation.Nullable String dirs() {
            return this.dirs;
        }
        public FindFilesRequest dirs(@jakarta.annotation.Nullable String dirs) {
            this.dirs = dirs;
            return this;
        }

        public @jakarta.annotation.Nullable String type() {
            return this.type;
        }
        public FindFilesRequest type(@jakarta.annotation.Nullable String type) {
            this.type = type;
            return this;
        }

        public @jakarta.annotation.Nullable Integer limit() {
            return this.limit;
        }
        public FindFilesRequest limit(@jakarta.annotation.Nullable Integer limit) {
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
            FindFilesRequest request = (FindFilesRequest) o;
            return Objects.equals(this.query, request.query()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace()) &&
                Objects.equals(this.dirs, request.dirs()) &&
                Objects.equals(this.type, request.type()) &&
                Objects.equals(this.limit, request.limit());
        }

        @Override
        public int hashCode() {
            return Objects.hash(query, directory, workspace, dirs, type, limit);
        }
    }

    /**
     * Find files
     * Search for files or directories by name or pattern in the project directory.
     * <p><b>200</b> - File paths
     * <p><b>400</b> - Bad request
     * @param requestParameters The findFiles request parameters as object
     * @return List&lt;String&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<List<String>> findFiles(FindFilesRequest requestParameters) throws WebClientResponseException {
        return this.findFiles(requestParameters.query(), requestParameters.directory(), requestParameters.workspace(), requestParameters.dirs(), requestParameters.type(), requestParameters.limit());
    }

    /**
     * Find files
     * Search for files or directories by name or pattern in the project directory.
     * <p><b>200</b> - File paths
     * <p><b>400</b> - Bad request
     * @param requestParameters The findFiles request parameters as object
     * @return ResponseEntity&lt;List&lt;String&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<String>>> findFilesWithHttpInfo(FindFilesRequest requestParameters) throws WebClientResponseException {
        return this.findFilesWithHttpInfo(requestParameters.query(), requestParameters.directory(), requestParameters.workspace(), requestParameters.dirs(), requestParameters.type(), requestParameters.limit());
    }

    /**
     * Find files
     * Search for files or directories by name or pattern in the project directory.
     * <p><b>200</b> - File paths
     * <p><b>400</b> - Bad request
     * @param requestParameters The findFiles request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec findFilesWithResponseSpec(FindFilesRequest requestParameters) throws WebClientResponseException {
        return this.findFilesWithResponseSpec(requestParameters.query(), requestParameters.directory(), requestParameters.workspace(), requestParameters.dirs(), requestParameters.type(), requestParameters.limit());
    }


    /**
     * Find files
     * Search for files or directories by name or pattern in the project directory.
     * <p><b>200</b> - File paths
     * <p><b>400</b> - Bad request
     * @param query The query parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param dirs The dirs parameter
     * @param type The type parameter
     * @param limit The limit parameter
     * @return List&lt;String&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec findFilesRequestCreation(@jakarta.annotation.Nonnull String query, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable String dirs, @jakarta.annotation.Nullable String type, @jakarta.annotation.Nullable Integer limit) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'query' is set
        if (query == null) {
            throw new WebClientResponseException("Missing the required parameter 'query' when calling findFiles", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", directory));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", workspace));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "query", query));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "dirs", dirs));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "type", type));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "limit", limit));

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<List<String>> localVarReturnType = new ParameterizedTypeReference<List<String>>() {};
        return apiClient.invokeAPI("/find/file", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Find files
     * Search for files or directories by name or pattern in the project directory.
     * <p><b>200</b> - File paths
     * <p><b>400</b> - Bad request
     * @param query The query parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param dirs The dirs parameter
     * @param type The type parameter
     * @param limit The limit parameter
     * @return List&lt;String&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<List<String>> findFiles(@jakarta.annotation.Nonnull String query, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable String dirs, @jakarta.annotation.Nullable String type, @jakarta.annotation.Nullable Integer limit) throws WebClientResponseException {
        ParameterizedTypeReference<List<String>> localVarReturnType = new ParameterizedTypeReference<List<String>>() {};
        return findFilesRequestCreation(query, directory, workspace, dirs, type, limit).bodyToMono(localVarReturnType);
    }

    /**
     * Find files
     * Search for files or directories by name or pattern in the project directory.
     * <p><b>200</b> - File paths
     * <p><b>400</b> - Bad request
     * @param query The query parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param dirs The dirs parameter
     * @param type The type parameter
     * @param limit The limit parameter
     * @return ResponseEntity&lt;List&lt;String&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<String>>> findFilesWithHttpInfo(@jakarta.annotation.Nonnull String query, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable String dirs, @jakarta.annotation.Nullable String type, @jakarta.annotation.Nullable Integer limit) throws WebClientResponseException {
        ParameterizedTypeReference<List<String>> localVarReturnType = new ParameterizedTypeReference<List<String>>() {};
        return findFilesRequestCreation(query, directory, workspace, dirs, type, limit).toEntity(localVarReturnType);
    }

    /**
     * Find files
     * Search for files or directories by name or pattern in the project directory.
     * <p><b>200</b> - File paths
     * <p><b>400</b> - Bad request
     * @param query The query parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @param dirs The dirs parameter
     * @param type The type parameter
     * @param limit The limit parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec findFilesWithResponseSpec(@jakarta.annotation.Nonnull String query, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace, @jakarta.annotation.Nullable String dirs, @jakarta.annotation.Nullable String type, @jakarta.annotation.Nullable Integer limit) throws WebClientResponseException {
        return findFilesRequestCreation(query, directory, workspace, dirs, type, limit);
    }

    public class FindSymbolsRequest {
        private @jakarta.annotation.Nonnull String query;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public FindSymbolsRequest() {}

        public FindSymbolsRequest(@jakarta.annotation.Nonnull String query, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.query = query;
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nonnull String query() {
            return this.query;
        }
        public FindSymbolsRequest query(@jakarta.annotation.Nonnull String query) {
            this.query = query;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public FindSymbolsRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public FindSymbolsRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            FindSymbolsRequest request = (FindSymbolsRequest) o;
            return Objects.equals(this.query, request.query()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(query, directory, workspace);
        }
    }

    /**
     * Find symbols
     * Search for workspace symbols like functions, classes, and variables using LSP.
     * <p><b>200</b> - Symbols
     * <p><b>400</b> - Bad request
     * @param requestParameters The findSymbols request parameters as object
     * @return List&lt;Symbol&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<Symbol> findSymbols(FindSymbolsRequest requestParameters) throws WebClientResponseException {
        return this.findSymbols(requestParameters.query(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Find symbols
     * Search for workspace symbols like functions, classes, and variables using LSP.
     * <p><b>200</b> - Symbols
     * <p><b>400</b> - Bad request
     * @param requestParameters The findSymbols request parameters as object
     * @return ResponseEntity&lt;List&lt;Symbol&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<Symbol>>> findSymbolsWithHttpInfo(FindSymbolsRequest requestParameters) throws WebClientResponseException {
        return this.findSymbolsWithHttpInfo(requestParameters.query(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Find symbols
     * Search for workspace symbols like functions, classes, and variables using LSP.
     * <p><b>200</b> - Symbols
     * <p><b>400</b> - Bad request
     * @param requestParameters The findSymbols request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec findSymbolsWithResponseSpec(FindSymbolsRequest requestParameters) throws WebClientResponseException {
        return this.findSymbolsWithResponseSpec(requestParameters.query(), requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Find symbols
     * Search for workspace symbols like functions, classes, and variables using LSP.
     * <p><b>200</b> - Symbols
     * <p><b>400</b> - Bad request
     * @param query The query parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;Symbol&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec findSymbolsRequestCreation(@jakarta.annotation.Nonnull String query, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'query' is set
        if (query == null) {
            throw new WebClientResponseException("Missing the required parameter 'query' when calling findSymbols", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", directory));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", workspace));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "query", query));

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Symbol> localVarReturnType = new ParameterizedTypeReference<Symbol>() {};
        return apiClient.invokeAPI("/find/symbol", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Find symbols
     * Search for workspace symbols like functions, classes, and variables using LSP.
     * <p><b>200</b> - Symbols
     * <p><b>400</b> - Bad request
     * @param query The query parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;Symbol&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<Symbol> findSymbols(@jakarta.annotation.Nonnull String query, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Symbol> localVarReturnType = new ParameterizedTypeReference<Symbol>() {};
        return findSymbolsRequestCreation(query, directory, workspace).bodyToFlux(localVarReturnType);
    }

    /**
     * Find symbols
     * Search for workspace symbols like functions, classes, and variables using LSP.
     * <p><b>200</b> - Symbols
     * <p><b>400</b> - Bad request
     * @param query The query parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;List&lt;Symbol&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<Symbol>>> findSymbolsWithHttpInfo(@jakarta.annotation.Nonnull String query, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<Symbol> localVarReturnType = new ParameterizedTypeReference<Symbol>() {};
        return findSymbolsRequestCreation(query, directory, workspace).toEntityList(localVarReturnType);
    }

    /**
     * Find symbols
     * Search for workspace symbols like functions, classes, and variables using LSP.
     * <p><b>200</b> - Symbols
     * <p><b>400</b> - Bad request
     * @param query The query parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec findSymbolsWithResponseSpec(@jakarta.annotation.Nonnull String query, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return findSymbolsRequestCreation(query, directory, workspace);
    }

    public class FindTextRequest {
        private @jakarta.annotation.Nonnull String pattern;
        private @jakarta.annotation.Nullable String directory;
        private @jakarta.annotation.Nullable String workspace;

        public FindTextRequest() {}

        public FindTextRequest(@jakarta.annotation.Nonnull String pattern, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) {
            this.pattern = pattern;
            this.directory = directory;
            this.workspace = workspace;
        }

        public @jakarta.annotation.Nonnull String pattern() {
            return this.pattern;
        }
        public FindTextRequest pattern(@jakarta.annotation.Nonnull String pattern) {
            this.pattern = pattern;
            return this;
        }

        public @jakarta.annotation.Nullable String directory() {
            return this.directory;
        }
        public FindTextRequest directory(@jakarta.annotation.Nullable String directory) {
            this.directory = directory;
            return this;
        }

        public @jakarta.annotation.Nullable String workspace() {
            return this.workspace;
        }
        public FindTextRequest workspace(@jakarta.annotation.Nullable String workspace) {
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
            FindTextRequest request = (FindTextRequest) o;
            return Objects.equals(this.pattern, request.pattern()) &&
                Objects.equals(this.directory, request.directory()) &&
                Objects.equals(this.workspace, request.workspace());
        }

        @Override
        public int hashCode() {
            return Objects.hash(pattern, directory, workspace);
        }
    }

    /**
     * Find text
     * Search for text patterns across files in the project using ripgrep.
     * <p><b>200</b> - Matches
     * <p><b>400</b> - Bad request
     * @param requestParameters The findText request parameters as object
     * @return List&lt;FindText200ResponseInner&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<FindText200ResponseInner> findText(FindTextRequest requestParameters) throws WebClientResponseException {
        return this.findText(requestParameters.pattern(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Find text
     * Search for text patterns across files in the project using ripgrep.
     * <p><b>200</b> - Matches
     * <p><b>400</b> - Bad request
     * @param requestParameters The findText request parameters as object
     * @return ResponseEntity&lt;List&lt;FindText200ResponseInner&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<FindText200ResponseInner>>> findTextWithHttpInfo(FindTextRequest requestParameters) throws WebClientResponseException {
        return this.findTextWithHttpInfo(requestParameters.pattern(), requestParameters.directory(), requestParameters.workspace());
    }

    /**
     * Find text
     * Search for text patterns across files in the project using ripgrep.
     * <p><b>200</b> - Matches
     * <p><b>400</b> - Bad request
     * @param requestParameters The findText request parameters as object
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec findTextWithResponseSpec(FindTextRequest requestParameters) throws WebClientResponseException {
        return this.findTextWithResponseSpec(requestParameters.pattern(), requestParameters.directory(), requestParameters.workspace());
    }


    /**
     * Find text
     * Search for text patterns across files in the project using ripgrep.
     * <p><b>200</b> - Matches
     * <p><b>400</b> - Bad request
     * @param pattern The pattern parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;FindText200ResponseInner&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec findTextRequestCreation(@jakarta.annotation.Nonnull String pattern, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'pattern' is set
        if (pattern == null) {
            throw new WebClientResponseException("Missing the required parameter 'pattern' when calling findText", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "directory", directory));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "workspace", workspace));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "pattern", pattern));

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<FindText200ResponseInner> localVarReturnType = new ParameterizedTypeReference<FindText200ResponseInner>() {};
        return apiClient.invokeAPI("/find", HttpMethod.GET, pathParams, localVarQueryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Find text
     * Search for text patterns across files in the project using ripgrep.
     * <p><b>200</b> - Matches
     * <p><b>400</b> - Bad request
     * @param pattern The pattern parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return List&lt;FindText200ResponseInner&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<FindText200ResponseInner> findText(@jakarta.annotation.Nonnull String pattern, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<FindText200ResponseInner> localVarReturnType = new ParameterizedTypeReference<FindText200ResponseInner>() {};
        return findTextRequestCreation(pattern, directory, workspace).bodyToFlux(localVarReturnType);
    }

    /**
     * Find text
     * Search for text patterns across files in the project using ripgrep.
     * <p><b>200</b> - Matches
     * <p><b>400</b> - Bad request
     * @param pattern The pattern parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseEntity&lt;List&lt;FindText200ResponseInner&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<FindText200ResponseInner>>> findTextWithHttpInfo(@jakarta.annotation.Nonnull String pattern, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        ParameterizedTypeReference<FindText200ResponseInner> localVarReturnType = new ParameterizedTypeReference<FindText200ResponseInner>() {};
        return findTextRequestCreation(pattern, directory, workspace).toEntityList(localVarReturnType);
    }

    /**
     * Find text
     * Search for text patterns across files in the project using ripgrep.
     * <p><b>200</b> - Matches
     * <p><b>400</b> - Bad request
     * @param pattern The pattern parameter
     * @param directory The directory parameter
     * @param workspace The workspace parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec findTextWithResponseSpec(@jakarta.annotation.Nonnull String pattern, @jakarta.annotation.Nullable String directory, @jakarta.annotation.Nullable String workspace) throws WebClientResponseException {
        return findTextRequestCreation(pattern, directory, workspace);
    }
}
