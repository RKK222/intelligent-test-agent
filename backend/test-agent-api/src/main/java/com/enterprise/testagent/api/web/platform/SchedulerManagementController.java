package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 旧定时任务管理 API 的显式作废入口。周期任务管理已改为同源 iframe 中的 XXL-JOB Admin。
 */
@RestController
@RequestMapping("/api/internal/platform/scheduler-management")
public class SchedulerManagementController {

    /** 任意旧子路径和 HTTP 方法都返回统一 410，避免调用方误以为临时 404。 */
    @RequestMapping("/**")
    public void gone() {
        throw new PlatformException(ErrorCode.API_GONE, "定时任务管理接口已迁移至 XXL-JOB");
    }
}
