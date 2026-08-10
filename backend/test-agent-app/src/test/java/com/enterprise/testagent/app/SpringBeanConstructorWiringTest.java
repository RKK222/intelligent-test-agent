package com.enterprise.testagent.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/** 锁定生产 Spring Bean 的多构造器装配规则，避免发布后回退到不存在的无参构造器。 */
class SpringBeanConstructorWiringTest {

    private static final Set<String> INJECTION_ANNOTATIONS = Set.of(
            Autowired.class.getName(),
            "org.springframework.beans.factory.annotation.Value",
            "jakarta.inject.Inject",
            "javax.inject.Inject");

    @Test
    void multiConstructorSpringBeansDeclareAnInjectionConstructorOrNoArgConstructor() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        List<String> violations = new ArrayList<>();

        for (var candidate : scanner.findCandidateComponents("com.enterprise.testagent")) {
            // 测试配置也在同一 classpath 下，只审计最终发布包会携带的生产组件。
            if (candidate.getResourceDescription().contains("test-classes")) {
                continue;
            }
            Class<?> beanType = ClassUtils.forName(
                    candidate.getBeanClassName(), SpringBeanConstructorWiringTest.class.getClassLoader());
            Constructor<?>[] constructors = beanType.getDeclaredConstructors();
            if (constructors.length <= 1 || hasNoArgConstructor(constructors)
                    || hasInjectionConstructor(constructors)) {
                continue;
            }
            violations.add(beanType.getName());
        }

        assertThat(violations)
                .as("多构造器 Spring Bean 必须显式声明注入构造器，或提供无参构造器")
                .isEmpty();
    }

    private static boolean hasNoArgConstructor(Constructor<?>[] constructors) {
        return Arrays.stream(constructors).anyMatch(constructor -> constructor.getParameterCount() == 0);
    }

    private static boolean hasInjectionConstructor(Constructor<?>[] constructors) {
        return Arrays.stream(constructors)
                .flatMap(constructor -> Arrays.stream(constructor.getDeclaredAnnotations()))
                .map(Annotation::annotationType)
                .map(Class::getName)
                .anyMatch(INJECTION_ANNOTATIONS::contains);
    }
}
