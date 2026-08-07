package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.domain.run.RunResendId;
import com.enterprise.testagent.opencode.runtime.run.RunResendDispatchBatchResult;
import com.enterprise.testagent.opencode.runtime.run.RunResendDispatchResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 跨 Java 只传重发控制面 ID，不传 prompt、附件、回答或供应商正文。 */
final class RunResendInternalDispatchDtos {

    private RunResendInternalDispatchDtos() { }

    record Request(
            @NotBlank @Size(max = 128) String linuxServerId,
            @NotEmpty @Size(max = 50) List<@NotBlank @Size(max = 128) String> resendIds) {

        List<RunResendId> domainResendIds() {
            return resendIds.stream().map(RunResendId::new).toList();
        }
    }

    record Response(String linuxServerId, List<Item> results) {
        static Response from(RunResendDispatchBatchResult batch) {
            return new Response(
                    batch.linuxServerId(),
                    batch.results().stream().map(Item::from).toList());
        }

        RunResendDispatchBatchResult toDomain() {
            return new RunResendDispatchBatchResult(
                    linuxServerId,
                    results.stream().map(Item::toDomain).toList());
        }
    }

    record Item(String resendId, boolean successful, String status, String errorCode) {
        static Item from(RunResendDispatchResult result) {
            return new Item(result.resendId(), result.successful(), result.status(), result.errorCode());
        }

        RunResendDispatchResult toDomain() {
            return new RunResendDispatchResult(resendId, successful, status, errorCode);
        }
    }
}
