package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.ApiRequestLogSummary;
import com.enterprise.testagent.integration.tcds.TcdsCaseInput;
import com.enterprise.testagent.integration.tcds.TcdsTaskTypeOption;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/** TCDS 案例维护 HTTP DTO；固定字段和当前用户统一认证号不进入客户端协议。 */
final class TcdsCaseMaintenanceDtos {

    private static final String TASK_TYPE_PATTERN = "[^,，]+(?:,[^,，]+)*";

    private TcdsCaseMaintenanceDtos() {
    }

    /** 只向浏览器暴露任务类型的展示名称和稳定值。 */
    record TaskTypeResponse(String name, String value) {

        static TaskTypeResponse from(TcdsTaskTypeOption option) {
            return new TaskTypeResponse(option.name(), option.value());
        }
    }

    record MaintenanceRequest(
            @NotBlank(message = "itemNo 不能为空")
            @Size(max = 128, message = "itemNo 长度不能超过 128")
            String itemNo,
            @NotEmpty(message = "caseList 不能为空")
            @Size(max = 500, message = "caseList 数量不能超过 500")
            List<@Valid CaseRequest> caseList) implements ApiRequestLogSummary {

        List<TcdsCaseInput> toInputs() {
            return caseList.stream().map(CaseRequest::toInput).toList();
        }

        /** 案例正文不进入访问日志，只保留需求子条目与案例数量。 */
        @Override
        public Object apiRequestLogSummary() {
            return Map.of("itemNo", itemNo, "caseCount", caseList == null ? 0 : caseList.size());
        }
    }

    record CaseRequest(
            @NotBlank(message = "案例名称不能为空")
            @Size(max = 1024, message = "案例名称长度不能超过 1024") String name,
            @Size(max = 65535, message = "测试步骤长度不能超过 65535") String step,
            @Size(max = 65535, message = "测试数据长度不能超过 65535") String data,
            @Size(max = 65535, message = "预期结果长度不能超过 65535") String expect,
            @NotBlank(message = "任务类型不能为空")
            @Size(max = 128, message = "任务类型长度不能超过 128")
            @Pattern(regexp = TASK_TYPE_PATTERN, message = "任务类型无效")
            String taskType) {

        TcdsCaseInput toInput() {
            return new TcdsCaseInput(name, step, data, expect, taskType);
        }
    }
}
