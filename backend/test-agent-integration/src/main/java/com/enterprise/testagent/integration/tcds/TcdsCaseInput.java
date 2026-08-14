package com.enterprise.testagent.integration.tcds;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

/** TCDS 案例维护的一条案例输入，只承载 Markdown 解析结果和已选择的任务类型。 */
public record TcdsCaseInput(
        String name,
        String step,
        String data,
        String expect,
        String taskType) {

    /** 收敛空文本并规范多任务类型格式，具体名称由服务使用实时 TCDS 数据校验。 */
    public TcdsCaseInput {
        name = normalize(name);
        step = normalize(step);
        data = normalize(data);
        expect = normalize(expect);
        taskType = normalizeTaskTypes(taskType);
        if (name.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "案例名称不能为空");
        }
        if (taskType.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "任务类型无效");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    /** 多任务类型只接受英文逗号，并生成去空白、无重复的规范值。 */
    private static String normalizeTaskTypes(String value) {
        if (value == null || value.isBlank() || value.contains("，")) {
            return "";
        }
        LinkedHashSet<String> taskTypes = Arrays.stream(value.split(",", -1))
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (taskTypes.stream().anyMatch(String::isBlank)) {
            return "";
        }
        return String.join(",", taskTypes);
    }
}
