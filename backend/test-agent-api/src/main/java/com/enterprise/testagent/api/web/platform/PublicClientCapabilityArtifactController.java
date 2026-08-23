package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.localclient.LocalClientPublicCapabilityModels;
import com.enterprise.testagent.workspace.PublicClientCapabilityPackageService;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/** 供受控客户端安装构建导出指定公共 commit 的完整能力包；仅超级管理员可读取。 */
@RestController
public class PublicClientCapabilityArtifactController {

    private static final String PATH =
            "/api/internal/platform/workspace-management/agent-config/public/client-capabilities/{bundleDigest}/artifact";
    private final PublicClientCapabilityPackageService capabilityPackageService;

    public PublicClientCapabilityArtifactController(PublicClientCapabilityPackageService capabilityPackageService) {
        this.capabilityPackageService = capabilityPackageService;
    }

    @GetMapping(PATH)
    public ResponseEntity<byte[]> download(
            @PathVariable String bundleDigest,
            ServerWebExchange exchange) {
        AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        if (bundleDigest == null || !bundleDigest.matches("[0-9a-f]{64}")) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "公共能力包摘要无效");
        }
        LocalClientPublicCapabilityModels.Release release = capabilityPackageService
                .availableReleaseForDigest(bundleDigest);
        if (release == null) {
            throw new PlatformException(
                    ErrorCode.NOT_FOUND, "公共能力客户端制品不存在", Map.of("bundleDigest", bundleDigest));
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(release.compressedSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=public-capabilities.tar.gz")
                .header("X-TestAgent-Source-Commit", release.sourceCommit())
                .header("X-TestAgent-Bundle-Digest", release.bundleDigest())
                .body(release.artifact());
    }
}
