package com.enterprise.testagent.api.web.aop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * SensitiveDataMasker 单元测试。
 */
class SensitiveDataMaskerTest {

    @Nested
    @DisplayName("mask 方法测试")
    class MaskTest {

        @Test
        @DisplayName("null 输入返回 null")
        void mask_nullInput() {
            assertNull(SensitiveDataMasker.mask(null));
        }

        @Test
        @DisplayName("空字符串返回空字符串")
        void mask_emptyInput() {
            assertEquals("", SensitiveDataMasker.mask(""));
        }

        @Test
        @DisplayName("空白字符串返回原值")
        void mask_blankInput() {
            assertEquals("   ", SensitiveDataMasker.mask("   "));
        }

        @Test
        @DisplayName("脱敏 password 字段")
        void mask_password() {
            String input = "{\"username\":\"testuser\",\"password\":\"secret123\"}";
            String result = SensitiveDataMasker.mask(input);
            assertTrue(result.contains("\"password\":\"***\""));
            assertTrue(result.contains("\"username\":\"testuser\""));
        }

        @Test
        @DisplayName("脱敏 token 字段")
        void mask_token() {
            String input = "{\"token\":\"eyJhbGciOiJIUzI1NiJ9\",\"userId\":\"user001\"}";
            String result = SensitiveDataMasker.mask(input);
            assertTrue(result.contains("\"token\":\"***\""));
            assertTrue(result.contains("\"userId\":\"user001\""));
        }

        @Test
        @DisplayName("脱敏会话运行上下文 token 字段")
        void mask_contextToken() {
            String input = "{\"contextToken\":\"ctx_do-not-log\",\"contextVersion\":1}";

            String result = SensitiveDataMasker.mask(input);

            assertTrue(result.contains("\"contextToken\":\"***\""));
            assertTrue(result.contains("\"contextVersion\":1"));
        }

        @Test
        @DisplayName("脱敏内部模型兼容 authToken 字段")
        void mask_internalModelAuthToken() {
            String input = "{\"authToken\":\"legacy-do-not-log\",\"tokenConfigured\":true}";

            String result = SensitiveDataMasker.mask(input);

            assertTrue(result.contains("\"authToken\":\"***\""));
            assertFalse(result.contains("legacy-do-not-log"));
        }

        @Test
        @DisplayName("脱敏 XXL-JOB 一次性票据和会话摘要")
        void mask_xxlJobTicketAndSessionDigest() {
            String input = "{\"ticket\":\"one-time-secret\",\"sessionDigest\":\"sha256-secret\",\"formAction\":\"/xxl-job-admin/platform-sso/login\"}";

            String result = SensitiveDataMasker.mask(input);

            assertTrue(result.contains("\"ticket\":\"***\""));
            assertTrue(result.contains("\"sessionDigest\":\"***\""));
            assertFalse(result.contains("one-time-secret"));
            assertFalse(result.contains("sha256-secret"));
            assertTrue(result.contains("\"formAction\":\"/xxl-job-admin/platform-sso/login\""));
        }

        @Test
        @DisplayName("脱敏 LobeHub 模型委托")
        void mask_lobehubModelGrant() {
            String input = "{\"modelGrant\":\"server-side-grant\",\"department\":\"研发一部\"}";

            String result = SensitiveDataMasker.mask(input);

            assertTrue(result.contains("\"modelGrant\":\"***\""));
            assertFalse(result.contains("server-side-grant"));
        }

        @Test
        @DisplayName("脱敏 Workflow 一次性票据、模型授权和加密私钥信封")
        void mask_workflowCredentials() {
            String input = "{\"ticketId\":\"wfcheckout-secret\",\"grantId\":\"wfgrantid-secret\","
                    + "\"grant\":\"wfgrant-secret\",\"encryptedPrivateKey\":\"TAEC1.secret-envelope\"}";

            String result = SensitiveDataMasker.mask(input);

            assertTrue(result.contains("\"ticketId\":\"***\""));
            assertTrue(result.contains("\"grantId\":\"***\""));
            assertTrue(result.contains("\"grant\":\"***\""));
            assertTrue(result.contains("\"encryptedPrivateKey\":\"***\""));
            assertFalse(result.contains("wfcheckout-secret"));
            assertFalse(result.contains("wfgrant-secret"));
            assertFalse(result.contains("TAEC1.secret-envelope"));
        }

        @Test
        @DisplayName("长加密私钥信封脱敏不发生正则栈溢出")
        void mask_longEncryptedPrivateKeyWithoutStackOverflow() {
            String input = "{\"encryptedPrivateKey\":\"" + "A".repeat(16_384) + "\",\"name\":\"work\"}";

            String result = SensitiveDataMasker.mask(input);

            assertEquals("{\"encryptedPrivateKey\":\"***\",\"name\":\"work\"}", result);
        }

        @Test
        @DisplayName("脱敏 JVM 通用参数源值和内存值")
        void mask_commonParameterMemoryValues() {
            String input = "{\"sourceValue\":\"database-secret-like-value\",\"memoryValue\":\"effective-value\"}";

            String result = SensitiveDataMasker.mask(input);

            assertTrue(result.contains("\"sourceValue\":\"***\""));
            assertTrue(result.contains("\"memoryValue\":\"***\""));
        }

        @Test
        @DisplayName("完整脱敏含 JSON 转义字符的 JVM 通用参数值")
        void mask_commonParameterMemoryValuesWithEscapedQuotes() {
            String input = "{\"sourceValue\":\"database-secret\\\"-tail\",\"memoryValue\":\"effective-value\"}";

            String result = SensitiveDataMasker.mask(input);

            assertTrue(result.contains("\"sourceValue\":\"***\""));
            assertFalse(result.contains("database-secret"));
            assertFalse(result.contains("-tail"));
        }

        @Test
        @DisplayName("脱敏多个敏感字段")
        void mask_multipleSensitiveFields() {
            String input = "{\"password\":\"pass1\",\"token\":\"tok1\",\"secret\":\"sec1\",\"name\":\"test\"}";
            String result = SensitiveDataMasker.mask(input);
            assertTrue(result.contains("\"password\":\"***\""));
            assertTrue(result.contains("\"token\":\"***\""));
            assertTrue(result.contains("\"secret\":\"***\""));
            assertTrue(result.contains("\"name\":\"test\""));
        }

        @Test
        @DisplayName("不区分大小写脱敏")
        void mask_caseInsensitive() {
            String input = "{\"Password\":\"pass1\",\"TOKEN\":\"tok1\",\"Secret\":\"sec1\"}";
            String result = SensitiveDataMasker.mask(input);
            assertTrue(result.contains("\"Password\":\"***\""));
            assertTrue(result.contains("\"TOKEN\":\"***\""));
            assertTrue(result.contains("\"Secret\":\"***\""));
        }

        @Test
        @DisplayName("超长字符串截断")
        void mask_truncate() {
            StringBuilder sb = new StringBuilder("{\"data\":\"");
            sb.append("x".repeat(3000));
            sb.append("\"}");
            String input = sb.toString();
            String result = SensitiveDataMasker.mask(input);
            assertTrue(result.length() <= 2020); // 2000 + "...(truncated)"
            assertTrue(result.endsWith("...(truncated)"));
        }

        @Test
        @DisplayName("非 JSON 内容原样返回")
        void mask_nonJson() {
            String input = "This is plain text";
            String result = SensitiveDataMasker.mask(input);
            assertEquals(input, result);
        }
    }

    @Nested
    @DisplayName("maskPath 方法测试")
    class MaskPathTest {

        @Test
        @DisplayName("隐藏 Workflow 路径中的 checkout ticket 和 model grant")
        void maskPath_workflowCredentials() {
            assertEquals(
                    "/api/internal/workflow-capabilities/v1/checkout-tickets/***/consume",
                    SensitiveDataMasker.maskPath(
                            "/api/internal/workflow-capabilities/v1/checkout-tickets/wfcheckout-secret/consume"));
            assertEquals(
                    "/api/internal/workflow-capabilities/v1/model-grants/***/refresh",
                    SensitiveDataMasker.maskPath(
                            "/api/internal/workflow-capabilities/v1/model-grants/wfgrantid-secret/refresh"));
            assertEquals(
                    "/api/internal/workflow-capabilities/v1/model-grants/***/revoke",
                    SensitiveDataMasker.maskPath(
                            "/api/internal/workflow-capabilities/v1/model-grants/wfgrantid-secret/revoke"));
        }

        @Test
        @DisplayName("不改写没有凭据路径参数的固定路由")
        void maskPath_keepsFixedRoutes() {
            assertEquals(
                    "/api/internal/workflow-capabilities/v1/model-grants/revoke-run",
                    SensitiveDataMasker.maskPath(
                            "/api/internal/workflow-capabilities/v1/model-grants/revoke-run"));
        }
    }

    @Nested
    @DisplayName("maskAuthHeader 方法测试")
    class MaskAuthHeaderTest {

        @Test
        @DisplayName("null 输入返回 null")
        void maskAuthHeader_nullInput() {
            assertNull(SensitiveDataMasker.maskAuthHeader(null));
        }

        @Test
        @DisplayName("空字符串返回空字符串")
        void maskAuthHeader_emptyInput() {
            assertEquals("", SensitiveDataMasker.maskAuthHeader(""));
        }

        @Test
        @DisplayName("Bearer token 脱敏")
        void maskAuthHeader_bearer() {
            assertEquals("Bearer ***", SensitiveDataMasker.maskAuthHeader("Bearer eyJhbGciOiJIUzI1NiJ9"));
        }

        @Test
        @DisplayName("Basic auth 脱敏")
        void maskAuthHeader_basic() {
            assertEquals("Basic ***", SensitiveDataMasker.maskAuthHeader("Basic dXNlcjpwYXNz"));
        }

        @Test
        @DisplayName("其他格式脱敏为 ***")
        void maskAuthHeader_other() {
            assertEquals("***", SensitiveDataMasker.maskAuthHeader("SomeToken"));
        }

        @Test
        @DisplayName("大小写不敏感")
        void maskAuthHeader_caseInsensitive() {
            assertEquals("Bearer ***", SensitiveDataMasker.maskAuthHeader("bearer token123"));
        }
    }

    @Nested
    @DisplayName("truncate 方法测试")
    class TruncateTest {

        @Test
        @DisplayName("null 输入返回 null")
        void truncate_nullInput() {
            assertNull(SensitiveDataMasker.truncate(null));
        }

        @Test
        @DisplayName("短字符串原样返回")
        void truncate_shortString() {
            String input = "short string";
            assertEquals(input, SensitiveDataMasker.truncate(input));
        }

        @Test
        @DisplayName("超长字符串截断")
        void truncate_longString() {
            String input = "x".repeat(3000);
            String result = SensitiveDataMasker.truncate(input);
            assertEquals(2000 + "...(truncated)".length(), result.length());
            assertTrue(result.endsWith("...(truncated)"));
        }
    }
}
