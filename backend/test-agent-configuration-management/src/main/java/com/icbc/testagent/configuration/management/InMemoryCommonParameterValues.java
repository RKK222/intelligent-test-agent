package com.icbc.testagent.configuration.management;

import com.icbc.testagent.domain.configuration.CommonParameter;
import com.icbc.testagent.domain.configuration.CommonParameterReferenceResolver;
import com.icbc.testagent.domain.configuration.CommonParameterRepository;
import com.icbc.testagent.domain.configuration.CommonParameterValues;
import com.icbc.testagent.domain.configuration.ParameterPlatform;
import com.icbc.testagent.domain.configuration.ResolvedParameter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 通用参数内存缓存实现：启动时全量加载，刷新时整体原子替换快照；读操作命中内存并按需展开变量引用。
 *
 * <p>缓存只存原始值，变量引用 {@code ${englishName}} 在读取时由 {@link CommonParameterReferenceResolver} 展开，
 * 保证引用链中间值更新后不会读到陈旧的展开结果。读语义与原 {@link CommonParameterRepository} 一致：
 * 先按指定平台精确匹配，缺失回退 ALL。
 */
@Service
public class InMemoryCommonParameterValues implements CommonParameterValues {

    private final CommonParameterRepository repository;
    private final CommonParameterReferenceResolver resolver;

    private volatile Map<Key, CommonParameter> snapshot = Map.of();
    private volatile List<CommonParameter> sortedAll = List.of();
    private volatile List<ResolvedParameter> resolvedAll = List.of();

    /**
     * Spring 装配构造器；解析器为无状态领域服务，内部直接创建。
     */
    @Autowired
    public InMemoryCommonParameterValues(CommonParameterRepository repository) {
        this(repository, new CommonParameterReferenceResolver());
    }

    /**
     * 测试构造器：允许注入可控解析器。
     */
    InMemoryCommonParameterValues(CommonParameterRepository repository, CommonParameterReferenceResolver resolver) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
    }

    /**
     * 从仓储全量拉取并重建内存快照；整体原子替换，读取不阻塞刷新。失败抛出由调用方决定降级策略。
     */
    public void reload() {
        List<CommonParameter> all = repository.findAll();
        Map<Key, CommonParameter> map = new HashMap<>();
        for (CommonParameter parameter : all) {
            map.put(new Key(parameter.englishName(), parameter.platform()), parameter);
        }
        List<CommonParameter> sorted = all.stream()
                .sorted(Comparator
                        .comparing(CommonParameter::englishName)
                        .thenComparing(parameter -> parameter.platform().value()))
                .toList();
        List<ResolvedParameter> resolved = sorted.stream()
                .map(parameter -> toResolved(parameter, map))
                .toList();
        this.snapshot = map;
        this.sortedAll = sorted;
        this.resolvedAll = resolved;
    }

    @Override
    public Optional<String> resolvedValue(String englishName) {
        return resolvedValue(englishName, ParameterPlatform.current());
    }

    @Override
    public Optional<String> resolvedValue(String englishName, ParameterPlatform platform) {
        return raw(englishName, platform)
                .map(parameter -> resolver.resolve(parameter, platform, exactLookup(snapshot)).resolvedValue());
    }

    @Override
    public Optional<CommonParameter> raw(String englishName, ParameterPlatform platform) {
        CommonParameter exact = snapshot.get(new Key(englishName, platform));
        if (exact != null) {
            return Optional.of(exact);
        }
        return Optional.ofNullable(snapshot.get(new Key(englishName, ParameterPlatform.ALL)));
    }

    @Override
    public List<CommonParameter> findAll() {
        return sortedAll;
    }

    @Override
    public List<ResolvedParameter> resolvedAll() {
        return resolvedAll;
    }

    private ResolvedParameter toResolved(CommonParameter parameter, Map<Key, CommonParameter> map) {
        // 管理端展示用：ALL 行按当前 JVM 平台作为上下文展开，使引用平台参数的 ALL 行也能展示已展开值。
        ParameterPlatform context = parameter.platform() == ParameterPlatform.ALL
                ? ParameterPlatform.current()
                : parameter.platform();
        CommonParameterReferenceResolver.ResolvedValue resolved = resolver.resolve(parameter, context, exactLookup(map));
        return new ResolvedParameter(
                parameter, resolved.resolvedValue(), resolved.hasReference(), resolved.resolutionError());
    }

    private java.util.function.BiFunction<String, ParameterPlatform, Optional<CommonParameter>> exactLookup(
            Map<Key, CommonParameter> map) {
        return (englishName, platform) -> Optional.ofNullable(map.get(new Key(englishName, platform)));
    }

    private record Key(String englishName, ParameterPlatform platform) {
    }
}
