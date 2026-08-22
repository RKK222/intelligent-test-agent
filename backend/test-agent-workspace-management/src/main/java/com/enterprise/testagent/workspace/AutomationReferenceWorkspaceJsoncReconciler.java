package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 在服务端对账平台托管的自动化 JSONC 节点。
 *
 * <p>编辑器只替换平台拥有的引用对象和精确 external_directory 规则，用户字段、未知字段和其它区域注释
 * 均原样保留。这样定时任务等无浏览器入口也能在真正派发前复用同一份 JSONC 事实源。
 */
final class AutomationReferenceWorkspaceJsoncReconciler {

    private static final String SCHEMA = "https://opencode.ai/config.json";
    private static final String AUTOMATION_KIND = "automation";

    private final ObjectMapper objectMapper;

    AutomationReferenceWorkspaceJsoncReconciler(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    String reconcile(String content, String appId, List<Patch> patches) {
        String normalizedAppId = requireText(appId, "自动化引用缺少应用标识");
        List<Patch> safePatches = patches == null ? List.of() : List.copyOf(patches);
        Set<String> repositoryIds = new LinkedHashSet<>();
        Set<String> aliases = new LinkedHashSet<>();
        for (Patch patch : safePatches) {
            if (!normalizedAppId.equals(patch.appId())
                    || !repositoryIds.add(requireText(patch.repositoryId(), "自动化引用缺少版本库标识"))
                    || !aliases.add(requireText(patch.alias(), "自动化引用缺少别名"))) {
                throw invalid("同一应用的自动化版本库或引用别名不能重复");
            }
        }

        String output = content == null || content.isBlank()
                ? "{\n  \"$schema\": \"" + SCHEMA + "\"\n}\n"
                : content;
        output = ensureObject(output, List.of(), "references");

        ParsedDocument document = parse(output);
        ObjectNode references = requireObject(document.objectAt(List.of("references")), "配置文件 references 必须是对象");
        List<ManagedReference> stale = new ArrayList<>();
        for (Property property : references.properties()) {
            if (!(property.value() instanceof ObjectNode reference)) {
                continue;
            }
            if (!AUTOMATION_KIND.equals(stringValue(document.content(), reference, "testagent-reference-kind"))) {
                continue;
            }
            String managedAppId = stringValue(document.content(), reference, "testagent-automation-app-id");
            if (managedAppId != null && !normalizedAppId.equals(managedAppId)) {
                continue;
            }
            String repositoryId = stringValue(document.content(), reference, "testagent-automation-repository-id");
            if (!aliases.contains(property.key()) || repositoryId == null || !repositoryIds.contains(repositoryId)) {
                stale.add(new ManagedReference(
                        property.key(), stringValue(document.content(), reference, "path")));
            }
        }
        for (ManagedReference reference : stale) {
            output = removeProperty(output, List.of("references"), reference.alias());
            output = removeUnusedPermission(output, reference.path(), reference.alias());
        }

        for (Patch patch : safePatches) {
            document = parse(output);
            references = requireObject(document.objectAt(List.of("references")), "配置文件 references 必须是对象");
            Property existingProperty = references.property(patch.alias());
            String previousPath = null;
            if (existingProperty != null) {
                ObjectNode existing = requireObject(existingProperty.value(), "自动化引用别名已被非对象配置占用");
                String kind = stringValue(document.content(), existing, "testagent-reference-kind");
                String managedAppId = stringValue(document.content(), existing, "testagent-automation-app-id");
                String managedRepositoryId = stringValue(
                        document.content(), existing, "testagent-automation-repository-id");
                if (!AUTOMATION_KIND.equals(kind)
                        || (managedAppId != null && !normalizedAppId.equals(managedAppId))
                        || (managedRepositoryId != null && !patch.repositoryId().equals(managedRepositoryId))) {
                    throw invalid("自动化引用别名已被其它配置占用：" + patch.alias());
                }
                previousPath = stringValue(document.content(), existing, "path");
            }
            output = upsertProperty(output, List.of("references"), patch.alias(), managedValue(patch));
            if (previousPath != null && !previousPath.equals(patch.path())) {
                output = removeUnusedPermission(output, previousPath, patch.alias());
            }
            output = upsertExternalDirectoryPermission(output, patch.path());
        }
        return output;
    }

    private Map<String, Object> managedValue(Patch patch) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("path", patch.path());
        value.put("merge", false);
        value.put("sdd-folder-name", patch.directoryName());
        value.put("description", patch.description().trim());
        value.put("testagent-reference-kind", AUTOMATION_KIND);
        value.put("testagent-automation-app-id", patch.appId());
        value.put("testagent-automation-repository-id", patch.repositoryId());
        value.put("testagent-automation-generation", patch.generation());
        return value;
    }

    private String upsertExternalDirectoryPermission(String content, String path) {
        String pattern = path.replaceAll("/+$", "") + "/*";
        ParsedDocument document = parse(content);
        Node permission = document.valueAt(List.of("permission"));
        String output = content;
        if (permission == null) {
            output = upsertProperty(output, List.of(), "permission", Map.of());
        } else if (permission instanceof PrimitiveNode primitive && primitive.stringValue() != null) {
            String action = primitive.stringValue();
            requirePermissionAction(action);
            output = upsertProperty(output, List.of(), "permission", Map.of("*", action));
        } else if (!(permission instanceof ObjectNode)) {
            throw invalid("配置文件 permission 必须是 allow、ask、deny 字符串或对象");
        }
        output = ensureObjectOrPermissionFallback(output, List.of("permission"), "external_directory");

        document = parse(output);
        ObjectNode external = requireObject(
                document.objectAt(List.of("permission", "external_directory")),
                "配置文件 permission.external_directory 必须是规则对象");
        for (Property property : external.properties()) {
            String action = primitiveString(document.content(), property.value());
            requirePermissionAction(action);
        }
        // OpenCode 采用后匹配覆盖前匹配。精确 allow 必须移到对象最后，避免被后续宽规则再次覆盖。
        Property exact = external.property(pattern);
        if (exact != null
                && "allow".equals(primitiveString(document.content(), exact.value()))
                && external.properties().indexOf(exact) == external.properties().size() - 1) {
            return output;
        }
        if (exact != null) {
            output = removeProperty(output, List.of("permission", "external_directory"), pattern);
        }
        return upsertProperty(output, List.of("permission", "external_directory"), pattern, "allow");
    }

    private String ensureObjectOrPermissionFallback(String content, List<String> parentPath, String key) {
        ParsedDocument document = parse(content);
        Node node = document.valueAt(append(parentPath, key));
        if (node == null) {
            return upsertProperty(content, parentPath, key, Map.of());
        }
        if (node instanceof ObjectNode) {
            return content;
        }
        if (node instanceof PrimitiveNode primitive && primitive.stringValue() != null) {
            String action = primitive.stringValue();
            requirePermissionAction(action);
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("*", action);
            return upsertProperty(content, parentPath, key, fallback);
        }
        throw invalid("配置文件 permission.external_directory 必须是 allow、ask、deny 字符串或规则对象");
    }

    private String removeUnusedPermission(String content, String oldPath, String removedAlias) {
        if (oldPath == null || oldPath.isBlank()) {
            return content;
        }
        ParsedDocument document = parse(content);
        ObjectNode references = document.objectAt(List.of("references"));
        if (references != null) {
            for (Property property : references.properties()) {
                if (property.key().equals(removedAlias) || !(property.value() instanceof ObjectNode reference)) {
                    continue;
                }
                if (oldPath.equals(stringValue(document.content(), reference, "path"))) {
                    return content;
                }
            }
        }
        ObjectNode external = document.objectAt(List.of("permission", "external_directory"));
        String pattern = oldPath.replaceAll("/+$", "") + "/*";
        return external != null && external.property(pattern) != null
                ? removeProperty(content, List.of("permission", "external_directory"), pattern)
                : content;
    }

    private String ensureObject(String content, List<String> parentPath, String key) {
        ParsedDocument document = parse(content);
        Node node = document.valueAt(append(parentPath, key));
        if (node == null) {
            return upsertProperty(content, parentPath, key, Map.of());
        }
        requireObject(node, "配置文件 " + String.join(".", append(parentPath, key)) + " 必须是对象");
        return content;
    }

    private String upsertProperty(String content, List<String> objectPath, String key, Object value) {
        ParsedDocument document = parse(content);
        ObjectNode object = requireObject(document.objectAt(objectPath), "配置文件目标节点必须是对象");
        String serialized;
        try {
            serialized = objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize managed automation reference", exception);
        }
        Property existing = object.property(key);
        if (existing != null) {
            return replace(content, existing.value().start(), existing.value().end(), serialized);
        }
        int close = object.end() - 1;
        String closingIndent = lineIndent(content, close);
        String childIndent = object.properties().isEmpty()
                ? closingIndent + "  "
                : lineIndent(content, object.properties().get(0).keyStart());
        boolean trailingComma = !object.properties().isEmpty()
                && content.substring(object.properties().get(object.properties().size() - 1).value().end(), close)
                        .indexOf(',') >= 0;
        String prefix = object.properties().isEmpty() || trailingComma ? "" : ",";
        String insertion = prefix + "\n" + childIndent + jsonString(key) + ": " + serialized;
        if (object.properties().isEmpty()) {
            insertion += "\n" + closingIndent;
        }
        return replace(content, close, close, insertion);
    }

    private String removeProperty(String content, List<String> objectPath, String key) {
        ParsedDocument document = parse(content);
        ObjectNode object = document.objectAt(objectPath);
        if (object == null) {
            return content;
        }
        Property target = object.property(key);
        if (target == null) {
            return content;
        }
        List<Property> properties = object.properties();
        int index = properties.indexOf(target);
        int start = target.keyStart();
        int end = target.value().end();
        if (index + 1 < properties.size()) {
            end = properties.get(index + 1).keyStart();
        } else {
            int trailingComma = content.substring(end, object.end() - 1).indexOf(',');
            if (trailingComma >= 0) {
                end += trailingComma + 1;
            }
            if (index > 0) {
                int separator = content.substring(properties.get(index - 1).value().end(), start).lastIndexOf(',');
                if (separator >= 0) {
                    start = properties.get(index - 1).value().end() + separator;
                }
            }
        }
        return replace(content, start, end, "");
    }

    private String stringValue(String content, ObjectNode object, String key) {
        Property property = object.property(key);
        return property == null ? null : primitiveString(content, property.value());
    }

    private String primitiveString(String content, Node node) {
        if (!(node instanceof PrimitiveNode primitive) || primitive.stringValue() == null) {
            return null;
        }
        return primitive.stringValue();
    }

    private void requirePermissionAction(String value) {
        if (!"allow".equals(value) && !"ask".equals(value) && !"deny".equals(value)) {
            throw invalid("外部目录权限动作必须是 allow、ask 或 deny");
        }
    }

    private ObjectNode requireObject(Node node, String message) {
        if (node instanceof ObjectNode object) {
            return object;
        }
        throw invalid(message);
    }

    private ParsedDocument parse(String content) {
        try {
            return new Parser(content, objectMapper).parse();
        } catch (IllegalArgumentException exception) {
            throw invalid("配置文件 JSONC 无法解析：" + exception.getMessage());
        }
    }

    private String jsonString(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String lineIndent(String content, int offset) {
        int lineStart = content.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
        int current = lineStart;
        while (current < offset && (content.charAt(current) == ' ' || content.charAt(current) == '\t')) {
            current++;
        }
        return content.substring(lineStart, current);
    }

    private String replace(String content, int start, int end, String replacement) {
        return content.substring(0, start) + replacement + content.substring(end);
    }

    private List<String> append(List<String> values, String value) {
        List<String> result = new ArrayList<>(values);
        result.add(value);
        return List.copyOf(result);
    }

    private String requireText(String value, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw invalid(message);
        }
        return normalized;
    }

    private PlatformException invalid(String message) {
        return new PlatformException(ErrorCode.VALIDATION_ERROR, message);
    }

    record Patch(
            String appId,
            String repositoryId,
            long generation,
            String alias,
            String path,
            String directoryName,
            String description) {

        Patch {
            appId = Objects.requireNonNull(appId);
            repositoryId = Objects.requireNonNull(repositoryId);
            alias = Objects.requireNonNull(alias);
            path = Objects.requireNonNull(path);
            directoryName = Objects.requireNonNull(directoryName);
            description = description == null ? "" : description;
        }
    }

    private record ManagedReference(String alias, String path) {
    }

    private sealed interface Node permits ObjectNode, ArrayNode, PrimitiveNode {
        int start();

        int end();
    }

    private record Property(String key, int keyStart, Node value) {
    }

    private record ObjectNode(int start, int end, List<Property> properties) implements Node {
        private Property property(String key) {
            return properties.stream().filter(property -> property.key().equals(key)).findFirst().orElse(null);
        }
    }

    private record ArrayNode(int start, int end) implements Node {
    }

    private record PrimitiveNode(int start, int end, String stringValue) implements Node {
    }

    private record ParsedDocument(String content, ObjectNode root) {
        private Node valueAt(List<String> path) {
            Node current = root;
            for (String segment : path) {
                if (!(current instanceof ObjectNode object)) {
                    return null;
                }
                Property property = object.property(segment);
                if (property == null) {
                    return null;
                }
                current = property.value();
            }
            return current;
        }

        private ObjectNode objectAt(List<String> path) {
            Node node = valueAt(path);
            return node instanceof ObjectNode object ? object : null;
        }
    }

    /** JSONC 结构扫描器：只建立对象属性位置，不重新格式化正文。 */
    private static final class Parser {
        private final String content;
        private final ObjectMapper objectMapper;
        private int index;

        private Parser(String content, ObjectMapper objectMapper) {
            this.content = Objects.requireNonNull(content);
            this.objectMapper = objectMapper;
        }

        private ParsedDocument parse() {
            skipTrivia();
            Node node = parseValue();
            skipTrivia();
            if (!(node instanceof ObjectNode object) || index != content.length()) {
                throw error("根节点必须是对象");
            }
            return new ParsedDocument(content, object);
        }

        private Node parseValue() {
            skipTrivia();
            if (index >= content.length()) {
                throw error("缺少值");
            }
            return switch (content.charAt(index)) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseStringNode();
                default -> parsePrimitive();
            };
        }

        private ObjectNode parseObject() {
            int start = index++;
            List<Property> properties = new ArrayList<>();
            skipTrivia();
            if (consume('}')) {
                return new ObjectNode(start, index, List.copyOf(properties));
            }
            while (true) {
                skipTrivia();
                int keyStart = index;
                PrimitiveNode keyNode = parseStringNode();
                String key = keyNode.stringValue();
                skipTrivia();
                require(':');
                Node value = parseValue();
                properties.add(new Property(key, keyStart, value));
                skipTrivia();
                if (consume('}')) {
                    return new ObjectNode(start, index, List.copyOf(properties));
                }
                require(',');
                skipTrivia();
                if (consume('}')) {
                    return new ObjectNode(start, index, List.copyOf(properties));
                }
            }
        }

        private ArrayNode parseArray() {
            int start = index++;
            skipTrivia();
            if (consume(']')) {
                return new ArrayNode(start, index);
            }
            while (true) {
                parseValue();
                skipTrivia();
                if (consume(']')) {
                    return new ArrayNode(start, index);
                }
                require(',');
                skipTrivia();
                if (consume(']')) {
                    return new ArrayNode(start, index);
                }
            }
        }

        private PrimitiveNode parseStringNode() {
            int start = index;
            require('"');
            boolean escaped = false;
            while (index < content.length()) {
                char current = content.charAt(index++);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    String encoded = content.substring(start, index);
                    try {
                        return new PrimitiveNode(start, index, objectMapper.readValue(encoded, String.class));
                    } catch (JsonProcessingException exception) {
                        throw error("字符串转义无效");
                    }
                }
            }
            throw error("字符串未结束");
        }

        private PrimitiveNode parsePrimitive() {
            int start = index;
            while (index < content.length()) {
                char current = content.charAt(index);
                if (current == ',' || current == '}' || current == ']' || Character.isWhitespace(current)) {
                    break;
                }
                if (current == '/' && index + 1 < content.length()
                        && (content.charAt(index + 1) == '/' || content.charAt(index + 1) == '*')) {
                    break;
                }
                index++;
            }
            if (start == index) {
                throw error("值无效");
            }
            String raw = content.substring(start, index);
            if (!"true".equals(raw) && !"false".equals(raw) && !"null".equals(raw)
                    && !raw.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) {
                throw error("值无效");
            }
            return new PrimitiveNode(start, index, null);
        }

        private void skipTrivia() {
            while (index < content.length()) {
                char current = content.charAt(index);
                if (Character.isWhitespace(current)) {
                    index++;
                    continue;
                }
                if (current == '/' && index + 1 < content.length() && content.charAt(index + 1) == '/') {
                    index += 2;
                    while (index < content.length() && content.charAt(index) != '\n') {
                        index++;
                    }
                    continue;
                }
                if (current == '/' && index + 1 < content.length() && content.charAt(index + 1) == '*') {
                    int end = content.indexOf("*/", index + 2);
                    if (end < 0) {
                        throw error("块注释未结束");
                    }
                    index = end + 2;
                    continue;
                }
                return;
            }
        }

        private boolean consume(char expected) {
            if (index < content.length() && content.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void require(char expected) {
            if (!consume(expected)) {
                throw error("缺少 '" + expected + "'");
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + "（offset=" + index + "）");
        }
    }
}
