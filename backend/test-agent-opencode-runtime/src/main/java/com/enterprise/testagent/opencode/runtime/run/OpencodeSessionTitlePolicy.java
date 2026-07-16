package com.enterprise.testagent.opencode.runtime.run;

import java.util.Arrays;

/**
 * OpenCode 标题兼容规则：默认时间戳标题不能作为平台最终标题，平台临时标题须与前端创建规则一致。
 */
final class OpencodeSessionTitlePolicy {

    private OpencodeSessionTitlePolicy() {
    }

    static boolean isDefaultTitle(String title) {
        return title != null && title.trim().matches("^(New session|Child session) - \\d{4}-\\d{2}-\\d{2}T.*Z$");
    }

    static String initialPlatformTitle(String prompt) {
        String firstLine = Arrays.stream(prompt.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .findFirst()
                .orElse("新对话");
        return firstLine.length() > 72 ? firstLine.substring(0, 69) + "..." : firstLine;
    }
}
