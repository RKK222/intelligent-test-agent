package com.enterprise.testagent.domain.hub;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;

/**
 * 网页 Agent 目录中的受保护 Agent 选择标识。
 *
 * <p>该值只是不可解释的目录选择句柄，不包含 Agent 提示词或 Skill 正文。</p>
 */
public final class ProtectedAgentSelection {

    public static final String PREFIX = "protected:";

    private ProtectedAgentSelection() {
    }

    public static boolean isProtected(String value) {
        return value != null && value.trim().startsWith(PREFIX);
    }

    public static String catalogId(String revisionId) {
        if (revisionId == null || revisionId.isBlank()) {
            throw new IllegalArgumentException("revisionId must not be blank");
        }
        return PREFIX + revisionId.trim();
    }

    public static String revisionId(String selection) {
        if (!isProtected(selection)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "受保护 Agent 选择无效");
        }
        String revisionId = selection.trim().substring(PREFIX.length()).trim();
        if (revisionId.isEmpty() || revisionId.length() > 200) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "受保护 Agent 修订无效");
        }
        return revisionId;
    }
}
