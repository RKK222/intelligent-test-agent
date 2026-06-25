package com.icbc.testagent.workspace;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 公共目录服务：读取 application.yml 中固定配置的根目录，封装路径越权拦截与禁用态降级。
 *
 * <p>该服务用于"固定路径内容扫描/公共目录读取"场景：所有登录用户可浏览/读取，SUPER_ADMIN 额外可写。
 * 配置为空时整个服务视为禁用，list/read/write 全部返回 NOT_FOUND，避免误暴露。
 */
@Service
public class PublicDirectoryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicDirectoryService.class);

    private final WorkspaceFileService fileService;
    private final String configuredRootPath;

    /**
     * 注入文件服务与配置的公共目录根路径；根路径为空字符串表示该功能整体禁用。
     */
    @Autowired
    public PublicDirectoryService(
            WorkspaceFileService fileService,
            @Value("${test-agent.public-directory.path:}") String configuredRootPath) {
        this.fileService = Objects.requireNonNull(fileService, "fileService must not be null");
        this.configuredRootPath = configuredRootPath == null ? "" : configuredRootPath.trim();
    }

    /**
     * 返回当前是否启用了公共目录（配置了非空根目录且该根目录存在且是目录）。
     */
    public boolean isEnabled() {
        if (configuredRootPath.isEmpty()) {
            return false;
        }
        try {
            Path root = Path.of(configuredRootPath).toRealPath();
            return Files.isDirectory(root);
        } catch (Exception exception) {
            return false;
        }
    }

    /**
     * 列出公共目录下的一层子项；服务未启用或路径越权时返回平台错误。
     */
    public List<FileTreeEntryResponse> listDirectory(String relativePath) {
        return fileService.listDirectory(resolveRealRoot(), relativePath);
    }

    /**
     * 读取公共目录下 UTF-8 文本文件内容；服务未启用或路径越权时返回平台错误。
     */
    public FileContentResponse readContent(String relativePath) {
        return fileService.readContent(resolveRealRoot(), relativePath);
    }

    /**
     * 写入公共目录下 UTF-8 文本文件；调用方需在 Controller 层校验 SUPER_ADMIN 角色。
     */
    public void writeContent(String relativePath, String content) {
        fileService.writeContent(resolveRealRoot(), relativePath, content);
    }

    /**
     * 解析配置的根目录为真实路径；未配置或目录不存在时抛 NOT_FOUND，让 Controller 统一包装。
     */
    private String resolveRealRoot() {
        if (configuredRootPath.isEmpty()) {
            LOGGER.debug("Public directory is not configured");
            throw new PlatformException(
                    ErrorCode.NOT_FOUND,
                    "公共目录未配置",
                    Map.of());
        }
        try {
            Path root = Path.of(configuredRootPath).toRealPath();
            if (!Files.isDirectory(root)) {
                throw new PlatformException(
                        ErrorCode.NOT_FOUND,
                        "公共目录不存在",
                        Map.of("path", configuredRootPath));
            }
            return root.toString();
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(
                    ErrorCode.NOT_FOUND,
                    "公共目录不可访问",
                    Map.of("path", configuredRootPath),
                    exception);
        }
    }
}
