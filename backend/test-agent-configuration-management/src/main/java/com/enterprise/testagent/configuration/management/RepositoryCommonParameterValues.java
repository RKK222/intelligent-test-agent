package com.enterprise.testagent.configuration.management;

import com.enterprise.testagent.domain.configuration.CommonParameter;
import com.enterprise.testagent.domain.configuration.CommonParameterReferenceResolver;
import com.enterprise.testagent.domain.configuration.CommonParameterRepository;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import com.enterprise.testagent.domain.configuration.ResolvedParameter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 通用参数数据库直读实现：每次读取都通过 Repository 访问数据库，并按需展开变量引用。
 *
 * <p>不在 JVM 内存或 Redis 中缓存参数值，避免多 Java 进程读到陈旧配置。通用参数表是有界系统配置表，
 * 管理端列表和引用解析允许按需多次查询数据库。
 */
@Service
public class RepositoryCommonParameterValues implements CommonParameterValues {

    private final CommonParameterRepository repository;
    private final CommonParameterReferenceResolver resolver;

    /**
     * Spring 装配构造器；解析器为无状态领域服务，内部直接创建。
     */
    @Autowired
    public RepositoryCommonParameterValues(CommonParameterRepository repository) {
        this(repository, new CommonParameterReferenceResolver());
    }

    /**
     * 测试构造器：允许注入可控解析器。
     */
    RepositoryCommonParameterValues(CommonParameterRepository repository, CommonParameterReferenceResolver resolver) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
    }

    @Override
    public Optional<String> resolvedValue(String englishName) {
        return resolvedValue(englishName, ParameterPlatform.current());
    }

    @Override
    public Optional<String> resolvedValue(String englishName, ParameterPlatform platform) {
        ParameterPlatform context = platform == null ? ParameterPlatform.current() : platform;
        return raw(englishName, context)
                .map(parameter -> resolver.resolve(parameter, context, this::exactLookup).resolvedValue());
    }

    @Override
    public Optional<CommonParameter> raw(String englishName, ParameterPlatform platform) {
        ParameterPlatform context = platform == null ? ParameterPlatform.current() : platform;
        Optional<CommonParameter> exact = repository.findByEnglishNameAndPlatform(englishName, context);
        if (exact.isPresent() || context == ParameterPlatform.ALL) {
            return exact;
        }
        return repository.findByEnglishNameAndPlatform(englishName, ParameterPlatform.ALL);
    }

    @Override
    public List<CommonParameter> findAll() {
        return sortedAll();
    }

    @Override
    public List<ResolvedParameter> resolvedAll() {
        return sortedAll().stream()
                .map(this::toResolved)
                .toList();
    }

    private List<CommonParameter> sortedAll() {
        return repository.findAll().stream()
                .sorted(Comparator
                        .comparing(CommonParameter::englishName)
                        .thenComparing(parameter -> parameter.platform().value()))
                .toList();
    }

    private ResolvedParameter toResolved(CommonParameter parameter) {
        // 管理端展示用：ALL 行按当前 JVM 平台作为上下文展开，使引用平台参数的 ALL 行也能展示已展开值。
        ParameterPlatform context = parameter.platform() == ParameterPlatform.ALL
                ? ParameterPlatform.current()
                : parameter.platform();
        CommonParameterReferenceResolver.ResolvedValue resolved = resolver.resolve(parameter, context, this::exactLookup);
        return new ResolvedParameter(
                parameter, resolved.resolvedValue(), resolved.hasReference(), resolved.resolutionError());
    }

    private Optional<CommonParameter> exactLookup(String englishName, ParameterPlatform platform) {
        return repository.findByEnglishNameAndPlatform(englishName, platform);
    }
}
