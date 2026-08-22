package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceGeneration;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/** 自动化引用的别名和逻辑路径规则；配置 API、Run 对账与文件树必须共用同一实现。 */
final class AutomationReferencePathPolicy {

    private static final Pattern ENGLISH_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");

    private AutomationReferencePathPolicy() {
    }

    static String logicalPath(
            ApplicationId appId,
            CodeRepository repository,
            ApplicationAutomationReferenceGeneration generation) {
        List<String> segments = new ArrayList<>();
        segments.add("{env:OPENCODE_REFERENCES_DIR}");
        segments.add("automation");
        segments.add(applicationPathFragment(appId));
        segments.add(validatedEnglishName(repository));
        segments.add(Long.toString(generation.generation()));
        if (!generation.directoryPath().isEmpty()) {
            segments.add(generation.directoryPath());
        }
        return String.join("/", segments);
    }

    static String alias(CodeRepository repository) {
        return "automation-" + validatedEnglishName(repository);
    }

    static String directoryName(CodeRepository repository, String directoryPath) {
        if (directoryPath.isEmpty()) {
            return validatedEnglishName(repository);
        }
        int slash = directoryPath.lastIndexOf('/');
        return slash < 0 ? directoryPath : directoryPath.substring(slash + 1);
    }

    static String validatedEnglishName(CodeRepository repository) {
        String value = repository.englishName();
        if (value == null || !ENGLISH_NAME_PATTERN.matcher(value).matches()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "自动化代码库英文名称无效");
        }
        return value;
    }

    static String applicationPathFragment(ApplicationId appId) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(appId.value().getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
