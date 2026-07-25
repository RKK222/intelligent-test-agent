package com.enterprise.testagent.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.regex.Pattern;

/**
 * Agent/Skill Markdown 展示元数据解析器。配置树和 Hub 索引共用同一兼容顺序，
 * 避免同一文件在两个入口显示不同名称。
 */
final class AgentConfigMetadataParser {

    private static final Pattern FRONTMATTER = Pattern.compile("\\A---\\R(.*?)\\R---(?:\\R|\\z)", Pattern.DOTALL);
    private static final Pattern BILINGUAL_DESCRIPTION = Pattern.compile(
            "^([A-Za-z][A-Za-z0-9 &+./_-]*?)（([^）\\r\\n]+)）[。；]");
    private static final Pattern MARKDOWN_TITLE = Pattern.compile("(?m)^#\\s+(.+?)\\s*$");
    private static final ObjectMapper JSON = new ObjectMapper();

    Metadata parse(String content, boolean skill, String technicalName) {
        var matcher = FRONTMATTER.matcher(content == null ? "" : content);
        String frontmatter = matcher.find() ? matcher.group(1) : "";
        String description = yamlTopLevelScalar(frontmatter, "description");
        if (skill) {
            String chineseName = yamlNestedScalar(frontmatter, "display-name-zh");
            String englishName = yamlNestedScalar(frontmatter, "display-name");
            if (chineseName != null) {
                return new Metadata(chineseName, englishName == null ? technicalName : englishName, description);
            }
        }
        if (description != null) {
            var bilingual = BILINGUAL_DESCRIPTION.matcher(description);
            if (bilingual.find()) {
                String englishName = normalize(bilingual.group(1));
                String chineseName = normalize(bilingual.group(2));
                if (chineseName != null) {
                    return new Metadata(chineseName, englishName == null ? technicalName : englishName, description);
                }
            }
        }
        var title = MARKDOWN_TITLE.matcher(content == null ? "" : content);
        if (title.find()) {
            String value = normalize(title.group(1));
            if (value != null && value.codePoints().anyMatch(codePoint -> codePoint >= 0x3400 && codePoint <= 0x9fff)) {
                return new Metadata(value, technicalName, description);
            }
        }
        return new Metadata(null, technicalName, description);
    }

    private String yamlTopLevelScalar(String frontmatter, String field) {
        return yamlScalar(frontmatter, Pattern.compile("(?m)^" + Pattern.quote(field) + "\\s*:\\s*(.+?)\\s*$"));
    }

    private String yamlNestedScalar(String frontmatter, String field) {
        return yamlScalar(frontmatter, Pattern.compile("(?m)^[ \\t]+" + Pattern.quote(field) + "\\s*:\\s*(.+?)\\s*$"));
    }

    private String yamlScalar(String frontmatter, Pattern pattern) {
        var matcher = pattern.matcher(frontmatter);
        if (!matcher.find()) return null;
        String raw = matcher.group(1).trim();
        try {
            if (raw.startsWith("\"") && raw.endsWith("\"")) return normalize(JSON.readValue(raw, String.class));
            if (raw.startsWith("'") && raw.endsWith("'") && raw.length() >= 2) {
                return normalize(raw.substring(1, raw.length() - 1).replace("''", "'"));
            }
            return normalize(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalize(String value) {
        if (value == null) return null;
        String normalized = value.replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.isEmpty()) return null;
        return normalized.length() <= 1024 ? normalized : normalized.substring(0, 1024);
    }

    record Metadata(String displayName, String displayNameEn, String description) {
    }
}
