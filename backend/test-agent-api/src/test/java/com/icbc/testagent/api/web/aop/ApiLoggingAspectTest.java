package com.icbc.testagent.api.web.aop;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.user.UserId;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * ApiLoggingAspect 单元测试。
 */
class ApiLoggingAspectTest {

    private ApiLoggingAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new ApiLoggingAspect();
    }

    @Nested
    @DisplayName("extractExchange 方法测试")
    class ExtractExchangeTest {

        @Test
        @DisplayName("从参数中提取 ServerWebExchange")
        void extractExchange_found() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test").build());
            Object[] args = new Object[]{"stringArg", exchange, 123};

            ServerWebExchange result = aspect.extractExchange(args);

            assertEquals(exchange, result);
        }

        @Test
        @DisplayName("参数中没有 ServerWebExchange 返回 null")
        void extractExchange_notFound() {
            Object[] args = new Object[]{"stringArg", 123, null};

            ServerWebExchange result = aspect.extractExchange(args);

            assertNull(result);
        }

        @Test
        @DisplayName("null 参数数组返回 null")
        void extractExchange_nullArgs() {
            ServerWebExchange result = aspect.extractExchange(null);
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("extractUserId 方法测试")
    class ExtractUserIdTest {

        @Test
        @DisplayName("从已认证用户获取 userId")
        void extractUserId_authenticated() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test").build());
            AuthPrincipal principal = new AuthPrincipal(
                    "token123",
                    new UserId("user_001"),
                    "testuser",
                    "auth001",
                    List.of("USER"),
                    Instant.now(),
                    Instant.now().plusSeconds(3600)
            );
            exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);

            String userId = aspect.extractUserId(exchange);

            assertEquals("user_001", userId);
        }

        @Test
        @DisplayName("未认证用户返回 anonymous")
        void extractUserId_unauthenticated() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test").build());

            String userId = aspect.extractUserId(exchange);

            assertEquals("anonymous", userId);
        }

        @Test
        @DisplayName("null exchange 返回 anonymous")
        void extractUserId_nullExchange() {
            String userId = aspect.extractUserId(null);
            assertEquals("anonymous", userId);
        }
    }

    @Nested
    @DisplayName("clientIp 方法测试")
    class ClientIpTest {

        @Test
        @DisplayName("从 X-Forwarded-For 获取客户端 IP")
        void clientIp_fromXForwardedFor() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test")
                            .header("X-Forwarded-For", "192.168.1.100, 10.0.0.1")
                            .build());

            String clientIp = aspect.clientIp(exchange);

            assertEquals("192.168.1.100", clientIp);
        }

        @Test
        @DisplayName("从 RemoteAddress 获取客户端 IP")
        void clientIp_fromRemoteAddress() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test")
                            .remoteAddress(new InetSocketAddress("192.168.1.200", 12345))
                            .build());

            String clientIp = aspect.clientIp(exchange);

            assertEquals("192.168.1.200", clientIp);
        }

        @Test
        @DisplayName("null exchange 返回 unknown")
        void clientIp_nullExchange() {
            String clientIp = aspect.clientIp(null);
            assertEquals("unknown", clientIp);
        }
    }

    @Nested
    @DisplayName("requestPath 方法测试")
    class RequestPathTest {

        @Test
        @DisplayName("获取请求路径")
        void requestPath_success() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/auth/login").build());

            String path = aspect.requestPath(exchange);

            assertEquals("/api/auth/login", path);
        }

        @Test
        @DisplayName("null exchange 返回 unknown")
        void requestPath_nullExchange() {
            String path = aspect.requestPath(null);
            assertEquals("unknown", path);
        }
    }

    @Nested
    @DisplayName("httpMethod 方法测试")
    class HttpMethodTest {

        @Test
        @DisplayName("获取 HTTP 方法")
        void httpMethod_success() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/test").build());

            String method = aspect.httpMethod(exchange);

            assertEquals("POST", method);
        }

        @Test
        @DisplayName("null exchange 返回 UNKNOWN")
        void httpMethod_nullExchange() {
            String method = aspect.httpMethod(null);
            assertEquals("UNKNOWN", method);
        }
    }

    @Nested
    @DisplayName("extractRequestBody 方法测试")
    class ExtractRequestBodyTest {

        @Test
        @DisplayName("提取普通对象请求体")
        void extractRequestBody_pojo() {
            TestRequest request = new TestRequest("testuser", "password123");
            Object[] args = new Object[]{request};

            String body = aspect.extractRequestBody(args);

            assertTrue(body.contains("\"username\":\"testuser\""));
            assertTrue(body.contains("\"password\":\"***\""));
        }

        @Test
        @DisplayName("跳过 ServerWebExchange 参数")
        void extractRequestBody_skipExchange() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test").build());
            TestRequest request = new TestRequest("testuser", "password123");
            Object[] args = new Object[]{exchange, request};

            String body = aspect.extractRequestBody(args);

            assertTrue(body.contains("\"username\":\"testuser\""));
            assertTrue(body.contains("\"password\":\"***\""));
        }

        @Test
        @DisplayName("null 参数数组返回空字符串")
        void extractRequestBody_nullArgs() {
            String body = aspect.extractRequestBody(null);
            assertEquals("", body);
        }
    }

    @Nested
    @DisplayName("logApiCall 方法测试")
    class LogApiCallTest {

        private ProceedingJoinPoint joinPoint;
        private MethodSignature signature;

        @BeforeEach
        @SuppressWarnings("unchecked")
        void setUp() {
            joinPoint = mock(ProceedingJoinPoint.class);
            signature = mock(MethodSignature.class);
            when(joinPoint.getSignature()).thenReturn(signature);
            when(signature.getDeclaringType()).thenReturn(TestController.class);
            when(signature.getName()).thenReturn("testMethod");
        }

        @Test
        @DisplayName("同步返回值正常记录日志")
        void logApiCall_syncReturn() throws Throwable {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test").build());
            ApiResponse<String> response = ApiResponse.ok("success", "trace_001");

            when(joinPoint.getArgs()).thenReturn(new Object[]{exchange});
            when(joinPoint.proceed()).thenReturn(response);

            Object result = aspect.logApiCall(joinPoint);

            assertEquals(response, result);
        }

        @Test
        @DisplayName("Mono 返回值正确处理")
        void logApiCall_monoReturn() throws Throwable {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test").build());
            Mono<ApiResponse<String>> responseMono = Mono.just(ApiResponse.ok("success", "trace_001"));

            when(joinPoint.getArgs()).thenReturn(new Object[]{exchange});
            when(joinPoint.proceed()).thenReturn(responseMono);

            Object result = aspect.logApiCall(joinPoint);

            assertTrue(result instanceof Mono);
            StepVerifier.create((Mono<?>) result)
                    .expectNextMatches(r -> r instanceof ApiResponse)
                    .verifyComplete();
        }
    }

    // 测试辅助类
    static class TestRequest {
        public String username;
        public String password;

        TestRequest(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    static class TestController {
        public void testMethod() {
        }
    }
}
