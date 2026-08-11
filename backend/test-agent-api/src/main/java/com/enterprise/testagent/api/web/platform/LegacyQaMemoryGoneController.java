package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 已发布过的 QA 专用入口明确返回 410，避免客户端静默使用过期语义。 */
@RestController
@RequestMapping("/api/internal/platform/qa-memory/v1")
public class LegacyQaMemoryGoneController {
    @RequestMapping({"", "/**"})
    public void gone() {
        throw new PlatformException(ErrorCode.API_GONE, "QA 专用记忆接口已迁移至通用 memory/v1");
    }
}
