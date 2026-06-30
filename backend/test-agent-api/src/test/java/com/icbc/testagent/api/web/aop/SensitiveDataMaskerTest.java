package com.icbc.testagent.api.web.aop;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
