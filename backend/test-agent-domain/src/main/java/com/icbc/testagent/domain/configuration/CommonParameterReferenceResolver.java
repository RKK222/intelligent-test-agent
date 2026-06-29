package com.icbc.testagent.domain.configuration;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通用参数变量引用解析器；把参数值中的 {@code ${englishName}} 占位符展开为被引用参数的实际值。
 *
 * <p>解析规则：
 * <ul>
 *   <li>占位符 {@code ${englishName}} 按 {@code parameter_english} 精确匹配，允许多层嵌套展开。</li>
 *   <li>若 {@code ${name}} 未命中通用参数，则按同名进程环境变量展开；{@code $NAME} 也按环境变量展开。</li>
 *   <li>路径值开头的 {@code $HOME/}、{@code $HOME\}、{@code $HOME} 和 {@code ~/} 会展开为用户主目录。</li>
 *   <li>被引用参数按"解析上下文平台"查找：先该上下文平台、再回退 ALL。上下文由调用方传入：
 *       目标为 ALL 行时，调用方以当前 JVM 平台或目标平台作为上下文，使 ALL 参数也能引用平台参数
 *       （如 {@code SYS_DATA_ROOT_DIR} 仅有平台行）；目标为 LINUX/WINDOWS/MACOS 时上下文即该平台。</li>
 *   <li>循环引用与超过 {@link #MAX_DEPTH} 的深层嵌套保留字面占位符不展开，避免抛异常阻断业务。</li>
 *   <li>被引用参数或环境变量缺失时同样保留字面占位符，由调用方根据结果中残留的变量判断解析失败。</li>
 * </ul>
 *
 * <p>该解析器是纯领域逻辑，不依赖 Spring 或持久化；调用方提供按 (englishName, platform) 精确查找的回调。
 */
public final class CommonParameterReferenceResolver {

    /** 嵌套展开深度上限，超过按循环引用处理，保留字面占位符。 */
    public static final int MAX_DEPTH = 16;

    private static final Pattern REFERENCE_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    private static final Pattern ENVIRONMENT_PATTERN = Pattern.compile("\\$([A-Za-z_][A-Za-z0-9_]*)");

    /**
     * 解析目标参数的值；返回原始值、展开值、是否含引用及解析错误描述。
     *
     * <p>上下文取目标参数自身平台：ALL 行只能引用 ALL 参数。需要让 ALL 行引用平台参数时，
     * 改用 {@link #resolve(CommonParameter, ParameterPlatform, BiFunction)} 显式传入上下文平台。
     *
     * @param target       待解析的通用参数
     * @param exactLookup  按 (englishName, platform) 精确匹配的查找回调（不做平台回退，回退由本解析器控制）
     */
    public ResolvedValue resolve(
            CommonParameter target,
            BiFunction<String, ParameterPlatform, Optional<CommonParameter>> exactLookup) {
        return resolve(target, target.platform(), exactLookup);
    }

    /**
     * 解析目标参数的值，按指定上下文平台展开被引用参数。
     *
     * <p>ALL 行可传入当前/目标平台作为上下文，从而引用平台参数（如 {@code SYS_DATA_ROOT_DIR}）。
     * 上下文为 ALL 时退化为只引用 ALL 参数。
     *
     * @param target       待解析的通用参数
     * @param context      解析被引用参数时使用的上下文平台
     * @param exactLookup  按 (englishName, platform) 精确匹配的查找回调（不做平台回退，回退由本解析器控制）
     */
    public ResolvedValue resolve(
            CommonParameter target,
            ParameterPlatform context,
            BiFunction<String, ParameterPlatform, Optional<CommonParameter>> exactLookup) {
        String raw = target.parameterValue();
        Set<String> resolving = new HashSet<>();
        resolving.add(target.englishName());
        String resolved = expand(raw, context, resolving, exactLookup, 0);
        boolean hasReference = hasAnyReference(raw);
        String error = null;
        if (hasReference && hasAnyReference(resolved)) {
            error = "存在未解析的变量引用（循环、缺失或环境变量不存在）";
        }
        return new ResolvedValue(raw, resolved, hasReference, error);
    }

    private String expand(
            String value,
            ParameterPlatform context,
            Set<String> resolving,
            BiFunction<String, ParameterPlatform, Optional<CommonParameter>> exactLookup,
            int depth) {
        if (depth >= MAX_DEPTH) {
            return value;
        }
        Matcher matcher = REFERENCE_PATTERN.matcher(value);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            if (resolving.contains(name)) {
                // 循环引用：保留字面占位符，不展开。
                matcher.appendReplacement(builder, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            Optional<CommonParameter> referenced = lookupReference(name, context, exactLookup);
            if (referenced.isEmpty()) {
                // 通用参数未命中时再尝试同名环境变量，仍未命中则保留字面占位符。
                String replacement = environmentVariable(name).orElse(matcher.group());
                matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
                continue;
            }
            resolving.add(name);
            String expanded = expand(referenced.get().parameterValue(), context, resolving, exactLookup, depth + 1);
            resolving.remove(name);
            matcher.appendReplacement(builder, Matcher.quoteReplacement(expanded));
        }
        matcher.appendTail(builder);
        return expandHomeReference(expandEnvironmentReferences(builder.toString()));
    }

    private Optional<CommonParameter> lookupReference(
            String name,
            ParameterPlatform context,
            BiFunction<String, ParameterPlatform, Optional<CommonParameter>> exactLookup) {
        // 引用按"解析上下文平台"查找：先该平台、再回退 ALL；上下文为 ALL 时退化为只查 ALL。
        return exactLookup.apply(name, context)
                .or(() -> exactLookup.apply(name, ParameterPlatform.ALL));
    }

    private String expandEnvironmentReferences(String value) {
        Matcher matcher = ENVIRONMENT_PATTERN.matcher(value);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement = environmentVariable(name).orElse(matcher.group());
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private Optional<String> environmentVariable(String name) {
        return Optional.ofNullable(System.getenv(name));
    }

    /**
     * 通用参数由数据库或 WebSocket 直接传递，不经过 shell；因此路径开头的 HOME 简写需在平台侧显式展开。
     */
    private String expandHomeReference(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String home = environmentVariable("HOME").orElse(System.getProperty("user.home", ""));
        if (home.isBlank()) {
            return value;
        }
        if (value.equals("$HOME")) {
            return home;
        }
        if (value.startsWith("$HOME/") || value.startsWith("$HOME\\")) {
            return home + value.substring("$HOME".length());
        }
        if (value.startsWith("~/") || value.startsWith("~\\")) {
            return home + value.substring(1);
        }
        return value;
    }

    private boolean hasAnyReference(String value) {
        return value != null && (REFERENCE_PATTERN.matcher(value).find()
                || ENVIRONMENT_PATTERN.matcher(value).find()
                || hasHomeReference(value));
    }

    private boolean hasHomeReference(String value) {
        return value != null && (value.equals("$HOME")
                || value.startsWith("$HOME/")
                || value.startsWith("$HOME\\")
                || value.startsWith("~/")
                || value.startsWith("~\\"));
    }

    /**
     * 解析结果；{@code resolutionError} 非 null 表示展开后仍残留未解析占位符。
     */
    public record ResolvedValue(
            String rawValue,
            String resolvedValue,
            boolean hasReference,
            String resolutionError) {
    }
}
