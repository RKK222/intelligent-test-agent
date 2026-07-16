package com.enterprise.testagent.domain.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.CommonParameter;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import com.enterprise.testagent.domain.configuration.ResolvedParameter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 托管工作区路径解析器：数据库保存逻辑路径，业务执行 Git/文件/PTY 时按通用参数解析为当前服务器物理路径。
 */
public final class ManagedWorkspacePathResolver {

    public static final String PARAM_OPENCODE_APP_WORKSPACE_ROOT = "OPENCODE_APP_WORKSPACE_ROOT";
    public static final String PARAM_OPENCODE_PERSONAL_WORKTREE_ROOT = "OPENCODE_PERSONAL_WORKTREE_ROOT";

    private static final String APP_PREFIX = "appworkspace:";
    private static final String PERSONAL_PREFIX = "personalworktree:";
    private static final Pattern WINDOWS_DRIVE_ABSOLUTE = Pattern.compile("^[A-Za-z]:[\\\\/].*");
    private static final Pattern WINDOWS_UNC_ABSOLUTE = Pattern.compile("^(\\\\\\\\|//).+");
    private static final CommonParameterValues EMPTY_VALUES = new CommonParameterValues() {
        @Override
        public Optional<String> resolvedValue(String englishName) {
            return Optional.empty();
        }

        @Override
        public Optional<String> resolvedValue(String englishName, ParameterPlatform platform) {
            return Optional.empty();
        }

        @Override
        public Optional<CommonParameter> raw(String englishName, ParameterPlatform platform) {
            return Optional.empty();
        }

        @Override
        public List<CommonParameter> findAll() {
            return List.of();
        }

        @Override
        public List<ResolvedParameter> resolvedAll() {
            return List.of();
        }
    };

    private final CommonParameterValues commonParameterValues;

    public ManagedWorkspacePathResolver(CommonParameterValues commonParameterValues) {
        this.commonParameterValues = Objects.requireNonNull(commonParameterValues, "commonParameterValues must not be null");
    }

    /**
     * 兼容旧测试和非托管路径调用方：只解析绝对/相对原始路径，遇到逻辑路径但无通用参数时按配置缺失报错。
     */
    public static ManagedWorkspacePathResolver legacyOnly() {
        return new ManagedWorkspacePathResolver(EMPTY_VALUES);
    }

    /**
     * 把数据库中保存的路径值解析为当前服务器可使用的物理路径；旧绝对路径原样兼容。
     */
    public Path resolve(String storedPath) {
        String value = requireText(storedPath, "path");
        if (value.startsWith(APP_PREFIX)) {
            return resolveLogical(PARAM_OPENCODE_APP_WORKSPACE_ROOT, APP_PREFIX, value);
        }
        if (value.startsWith(PERSONAL_PREFIX)) {
            return resolveLogical(PARAM_OPENCODE_PERSONAL_WORKTREE_ROOT, PERSONAL_PREFIX, value);
        }
        if (isLegacyAbsolutePath(value)) {
            return Path.of(value).normalize();
        }
        return Path.of(value).toAbsolutePath().normalize();
    }

    /**
     * 生成应用版本工作区逻辑路径值，调用方负责传入已业务安全化的版本、仓库英文名和模板相对目录。
     */
    public String appValue(String... fragments) {
        return logicalValue(APP_PREFIX, fragments);
    }

    /**
     * 生成个人 worktree 逻辑路径值，调用方负责传入已业务安全化的版本、用户、仓库、分支和模板相对目录。
     */
    public String personalValue(String... fragments) {
        return logicalValue(PERSONAL_PREFIX, fragments);
    }

    /**
     * 判定旧数据是否是绝对路径；包含当前系统绝对路径、Windows 盘符路径和 UNC 路径。
     */
    public boolean isLegacyAbsolutePath(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim();
        return Path.of(trimmed).isAbsolute()
                || WINDOWS_DRIVE_ABSOLUTE.matcher(trimmed).matches()
                || WINDOWS_UNC_ABSOLUTE.matcher(trimmed).matches();
    }

    public Workspace withResolvedRootPath(Workspace workspace) {
        return workspace.withRootPath(resolve(workspace.rootPath()).toString());
    }

    private Path resolveLogical(String rootParameter, String prefix, String value) {
        Path relative = logicalRelativePath(prefix, value.substring(prefix.length()));
        return configuredRoot(rootParameter).resolve(relative).normalize();
    }

    private String logicalValue(String prefix, String... fragments) {
        String joined = Arrays.stream(fragments == null ? new String[0] : fragments)
                .filter(fragment -> fragment != null && !fragment.isBlank())
                .map(String::trim)
                .map(fragment -> fragment.replace('\\', '/'))
                .reduce((left, right) -> left + "/" + right)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.VALIDATION_ERROR,
                        "托管工作区逻辑路径不能为空",
                        Map.of("prefix", prefix)));
        Path relative = logicalRelativePath(prefix, joined);
        return prefix + relative.toString().replace('\\', '/');
    }

    private Path logicalRelativePath(String prefix, String relativeValue) {
        String normalizedText = requireText(relativeValue, "relativePath").replace('\\', '/');
        Path relative = Path.of(normalizedText).normalize();
        if (relative.isAbsolute()
                || relative.toString().isBlank()
                || relative.startsWith("..")
                || normalizedText.equals("..")
                || normalizedText.startsWith("../")
                || normalizedText.contains("/../")
                || normalizedText.endsWith("/..")) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "托管工作区逻辑路径非法",
                    Map.of("prefix", prefix, "relativePath", relativeValue));
        }
        return relative;
    }

    private Path configuredRoot(String parameterEnglishName) {
        return commonParameterValues.resolvedValue(parameterEnglishName)
                .filter(value -> !value.isBlank())
                .map(Path::of)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.INTERNAL_ERROR,
                        "通用参数未配置：" + parameterEnglishName,
                        Map.of("parameter", parameterEnglishName)))
                .toAbsolutePath()
                .normalize();
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "路径不能为空",
                    Map.of("field", field));
        }
        return value.trim();
    }
}
