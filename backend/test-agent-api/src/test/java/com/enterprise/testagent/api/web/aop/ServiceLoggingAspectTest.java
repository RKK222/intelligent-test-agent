package com.enterprise.testagent.api.web.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * ServiceLoggingAspect 单元测试。
 */
class ServiceLoggingAspectTest {

    private ServiceLoggingAspect aspect;
    private ProceedingJoinPoint joinPoint;
    private MethodSignature signature;

    @BeforeEach
    void setUp() {
        aspect = new ServiceLoggingAspect();
        joinPoint = mock(ProceedingJoinPoint.class);
        signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringType()).thenReturn(TestService.class);
        when(signature.getName()).thenReturn("execute");
    }

    @Test
    @DisplayName("参数摘要只记录类型和轻量值")
    void argsSummaryUsesSafeShape() {
        String summary = aspect.argsSummary(new Object[]{"secret prompt", 3, true, null, new TestRequest("value")});

        assertThat(summary)
                .isEqualTo("[String(length=13),Integer(3),Boolean(true),null,TestRequest]");
    }

    @Test
    @DisplayName("同步返回值正常穿透")
    void logServiceCallSyncReturn() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{"input"});
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.logServiceCall(joinPoint);

        assertThat(result).isEqualTo("ok");
    }

    @Test
    @DisplayName("Mono 返回值在订阅时保留原结果")
    void logServiceCallMonoReturn() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{"input"});
        when(joinPoint.proceed()).thenReturn(Mono.just("ok"));

        Object result = aspect.logServiceCall(joinPoint);

        assertThat(result).isInstanceOf(Mono.class);
        StepVerifier.create((Mono<?>) result)
                .expectNextMatches("ok"::equals)
                .verifyComplete();
    }

    @Test
    @DisplayName("同步异常继续抛给调用方")
    void logServiceCallRethrowsError() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{"input"});
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("failed"));

        assertThatThrownBy(() -> aspect.logServiceCall(joinPoint))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("failed");
    }

    static class TestService {
        String execute() {
            return "ok";
        }
    }

    record TestRequest(String value) {
    }
}
