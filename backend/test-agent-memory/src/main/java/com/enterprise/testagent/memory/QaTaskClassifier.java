package com.enterprise.testagent.memory;

import com.enterprise.testagent.domain.memory.QaTaskType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 运行时轻量任务分类器；只决定记忆适用范围，不把分类结果当业务事实。 */
@Component
public class QaTaskClassifier {
    private static final Map<QaTaskType, List<String>> KEYWORDS = keywords();

    public QaTaskType classify(String prompt) {
        String normalized = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        QaTaskType best = QaTaskType.GENERAL;
        int bestScore = 0;
        for (Map.Entry<QaTaskType, List<String>> entry : KEYWORDS.entrySet()) {
            int score = (int) entry.getValue().stream().filter(normalized::contains).count();
            if (score > bestScore) {
                best = entry.getKey();
                bestScore = score;
            }
        }
        return best;
    }

    private static Map<QaTaskType, List<String>> keywords() {
        Map<QaTaskType, List<String>> values = new LinkedHashMap<>();
        values.put(QaTaskType.TEST_CASE_GENERATION, List.of("测试案例", "测试用例", "用例生成", "test case"));
        values.put(QaTaskType.TEST_DATA_PREPARATION, List.of("测试数据", "造数", "数据准备", "mock data"));
        values.put(QaTaskType.REQUIREMENT_ANALYSIS, List.of("需求分析", "需求评审", "验收条件", "requirement"));
        values.put(QaTaskType.TEST_PLAN_DESIGN, List.of("测试方案", "测试计划", "测试策略", "test plan"));
        values.put(QaTaskType.DEFECT_ANALYSIS, List.of("缺陷分析", "bug 分析", "缺陷", "defect"));
        values.put(QaTaskType.ROOT_CAUSE_ANALYSIS, List.of("根因", "原因定位", "故障定位", "root cause"));
        values.put(QaTaskType.AUTOMATION_TESTING, List.of("自动化测试", "自动化脚本", "接口自动化", "automation"));
        values.put(QaTaskType.RISK_ANALYSIS, List.of("风险分析", "风险点", "测试风险", "risk"));
        values.put(QaTaskType.TEST_REPORTING, List.of("测试报告", "测试总结", "质量报告", "test report"));
        values.put(QaTaskType.RESULT_ACCEPTANCE, List.of("结果验收", "验收结论", "通过标准", "acceptance"));
        return Map.copyOf(values);
    }
}
