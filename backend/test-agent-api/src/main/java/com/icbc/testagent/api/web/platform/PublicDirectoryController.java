package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.RuntimeApiSupport;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.domain.dictionary.Dictionary;
import com.icbc.testagent.workspace.FileContentResponse;
import com.icbc.testagent.workspace.FileTreeEntryResponse;
import com.icbc.testagent.workspace.PublicDirectoryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * 公共目录 HTTP Controller：所有登录用户可浏览/读取，SUPER_ADMIN 额外可写。
 *
 * <p>公共目录根路径由 application.yml 中 test-agent.public-directory.path 指定；
 * 根路径为空时所有接口返回 NOT_FOUND，前端按未启用处理。
 */
@RestController
public class PublicDirectoryController {

    private final PublicDirectoryService publicDirectoryService;

    public PublicDirectoryController(PublicDirectoryService publicDirectoryService) {
        this.publicDirectoryService = Objects.requireNonNull(publicDirectoryService, "publicDirectoryService must not be null");
    }

    /**
     * 公共目录下的一层目录列表，path 为空时表示公共根目录。
     */
    @GetMapping({"/api/public/files", "/api/internal/platform/public-directory/files"})
    public ApiResponse<List<FileTreeEntryResponse>> listPublicFiles(
            @RequestParam(required = false) String path,
            ServerWebExchange exchange) {
        AuthWebSupport.getAuthPrincipal(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(publicDirectoryService.listDirectory(path), traceId);
    }

    /**
     * 读取公共目录下的 UTF-8 文本文件内容。
     */
    @GetMapping({"/api/public/files/content", "/api/internal/platform/public-directory/files/content"})
    public ApiResponse<FileContentResponse> readPublicFile(
            @RequestParam String path,
            ServerWebExchange exchange) {
        AuthWebSupport.getAuthPrincipal(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(publicDirectoryService.readContent(path), traceId);
    }

    /**
     * 写入公共目录下的 UTF-8 文本文件，仅 SUPER_ADMIN 可调用。
     */
    @PutMapping({"/api/public/files/content", "/api/internal/platform/public-directory/files/content"})
    public ApiResponse<Void> writePublicFile(
            @Valid @RequestBody RuntimeDtos.WriteFileRequest request,
            ServerWebExchange exchange) {
        AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        String traceId = RuntimeApiSupport.traceId(exchange);
        publicDirectoryService.writeContent(request.path(), request.content());
        return ApiResponse.ok(null, traceId);
    }
}
