package com.enterprise.testagent.integration.uitest;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.util.regex.Pattern;

/** 一行四列测试案例；每个 command 只能创建一次 UI 自动化。 */
public record UiTestExecutionCommand(
        String requestId,
        String caseName,
        String testSteps,
        String testData,
        String expectedResult) {

    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9_.:-]{1,200}");

    public UiTestExecutionCommand {
        requestId = normalize(requestId);
        caseName = normalize(caseName);
        testSteps = normalize(testSteps);
        testData = normalize(testData);
        expectedResult = normalize(expectedResult);
        if (!REQUEST_ID.matcher(requestId).matches()) {
            throw invalid("requestId 格式无效");
        }
        if (testSteps.isBlank()) {
            throw invalid("测试步骤不能为空");
        }
        requireLength(caseName, 500, "案例名称");
        requireLength(testSteps, 20_000, "测试步骤");
        requireLength(testData, 20_000, "测试数据");
        requireLength(expectedResult, 20_000, "预期结果");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static void requireLength(String value, int max, String field) {
        if (value.length() > max) {
            throw invalid(field + "超过长度上限");
        }
    }

    private static PlatformException invalid(String message) {
        return new PlatformException(ErrorCode.VALIDATION_ERROR, message);
    }
}
