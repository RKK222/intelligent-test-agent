package com.icbc.testagent.opencode.runtime.run;

/** owner lease 已失效；旧执行者必须立即停止消费，不能继续产生 Redis 或远端副作用。 */
final class RunOwnershipLostException extends RuntimeException {
    RunOwnershipLostException(String message) {
        super(message);
    }
}
