package com.enterprise.testagent.opencode.runtime.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.localclient.protocol.LocalClientFrame;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameCodec;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameType;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.enterprise.testagent.localclient.protocol.LocalClientProtocol;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class LocalClientWorkspaceFileGatewayTest {

    private final LocalClientFrameCodec codec = new LocalClientFrameCodec();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rootRegistrationUsesBoundedProtocolTimeoutInsteadOfFileTransferTimeout() {
        LocalClientTunnelGateway tunnel = mock(LocalClientTunnelGateway.class);
        LocalClientWorkspaceFileGateway gateway = new LocalClientWorkspaceFileGateway(tunnel);
        var result = objectMapper.createObjectNode().put("rootDigest", "digest");
        LocalClientFrame response = new LocalClientFrame(
                LocalClientProtocol.VERSION,
                LocalClientFrameType.FILE_RESPONSE,
                "lcr_root",
                "trace-root",
                7L,
                codec.payload(new LocalClientPayloads.FileResponse(true, result)));
        when(tunnel.request(
                eq(new LocalClientInstanceId("lci_root_timeout")),
                eq(7L),
                eq(LocalClientFrameType.FILE_REQUEST),
                any(LocalClientPayloads.FileRequest.class),
                eq("trace-root"),
                eq(LocalClientProtocol.REQUEST_TIMEOUT)))
                .thenReturn(Mono.just(response));

        assertThat(gateway.invokeRootRegistration(
                "lci_root_timeout",
                7L,
                "wrk_root_timeout",
                "workspace.registerRoot",
                objectMapper.createObjectNode().put("absolutePath", "/data/project"),
                "trace-root"))
                .isEqualTo(result);

        verify(tunnel).request(
                eq(new LocalClientInstanceId("lci_root_timeout")),
                eq(7L),
                eq(LocalClientFrameType.FILE_REQUEST),
                any(LocalClientPayloads.FileRequest.class),
                eq("trace-root"),
                eq(Duration.ofSeconds(30)));
    }

    @Test
    void rootRegistrationRejectsUnrelatedFileOperation() {
        LocalClientWorkspaceFileGateway gateway = new LocalClientWorkspaceFileGateway(
                mock(LocalClientTunnelGateway.class));

        assertThatIllegalArgumentException().isThrownBy(() -> gateway.invokeRootRegistration(
                "lci_root_timeout",
                7L,
                "wrk_root_timeout",
                "workspace.read",
                objectMapper.createObjectNode(),
                "trace-root"));
    }
}
