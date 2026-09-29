package com.enterprise.testagent.opencode.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpencodeV2RouteMapperTest {

    @Test
    void mapsStableRuntimeRoutesToV2Api() {
        assertThat(OpencodeV2RouteMapper.map("/global/health")).isEqualTo("/api/info");
        assertThat(OpencodeV2RouteMapper.map("/global/config")).isEqualTo("/api/config");
        assertThat(OpencodeV2RouteMapper.map("/experimental/tool")).isEqualTo("/api/rpc/testagent.runtime/tools");
        assertThat(OpencodeV2RouteMapper.map("/session/ses_demo/abort"))
                .isEqualTo("/api/session/ses_demo/interrupt");
        assertThat(OpencodeV2RouteMapper.map("/session/ses_demo/message"))
                .isEqualTo("/api/session/ses_demo/message");
        assertThat(OpencodeV2RouteMapper.map("/session/ses_demo/children"))
                .isEqualTo("/api/session");
        assertThat(OpencodeV2RouteMapper.map("/lsp")).isEqualTo("/api/config");
        assertThat(OpencodeV2RouteMapper.map("/provider/anthropic/oauth/callback"))
                .isEqualTo("/api/integration/anthropic/connect/oauth");
        assertThat(OpencodeV2RouteMapper.map("/auth/anthropic"))
                .isEqualTo("/api/integration/anthropic/connect/key");
        assertThat(OpencodeV2RouteMapper.map("/mcp/github/auth"))
                .isEqualTo("/api/experimental/mcp/github/connect");
        assertThat(OpencodeV2RouteMapper.map("/mcp/github/auth/disconnect"))
                .isEqualTo("/api/experimental/mcp/github/disconnect");
    }

    @Test
    void keepsV2AndUnknownRoutesStable() {
        assertThat(OpencodeV2RouteMapper.map("/api/model")).isEqualTo("/api/model");
        assertThat(OpencodeV2RouteMapper.map("/custom/plugin-route")).isEqualTo("/custom/plugin-route");
    }
}
