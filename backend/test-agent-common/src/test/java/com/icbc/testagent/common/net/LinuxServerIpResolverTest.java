package com.icbc.testagent.common.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.common.net.LinuxServerIpResolver.InterfaceInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

class LinuxServerIpResolverTest {

    @Test
    void picksSiteLocalAddressFromPhysicalInterface() {
        List<InterfaceInfo> interfaces = List.of(
                new InterfaceInfo("lo", true, true, false, List.of("127.0.0.1")),
                new InterfaceInfo("docker0", true, false, false, List.of("172.17.0.1")),
                new InterfaceInfo("eth0", true, false, false, List.of("192.168.1.10")));

        assertThat(LinuxServerIpResolver.detect(interfaces)).isEqualTo("192.168.1.10");
    }

    @Test
    void skipsVirtualAndLinkLocalAndLoopback() {
        List<InterfaceInfo> interfaces = List.of(
                new InterfaceInfo("lo", true, true, false, List.of("127.0.0.1")),
                new InterfaceInfo("br-abc", true, false, true, List.of("172.18.0.1")),
                new InterfaceInfo("veth12345", true, false, true, List.of("169.254.1.2")),
                new InterfaceInfo("ens33", true, false, false, List.of("10.0.0.5")));

        assertThat(LinuxServerIpResolver.detect(interfaces)).isEqualTo("10.0.0.5");
    }

    @Test
    void picksStableFirstWhenMultipleCandidates() {
        List<InterfaceInfo> interfaces = List.of(
                new InterfaceInfo("eth1", true, false, false, List.of("192.168.1.10")),
                new InterfaceInfo("eth0", true, false, false, List.of("10.0.0.5")));

        // 多候选时按地址自然序取第一个，保证结果稳定。
        assertThat(LinuxServerIpResolver.detect(interfaces)).isEqualTo("10.0.0.5");
    }

    @Test
    void acceptsAllSiteLocalRanges() {
        assertThat(LinuxServerIpResolver.detect(List.of(
                new InterfaceInfo("eth0", true, false, false, List.of("10.1.2.3"))))).isEqualTo("10.1.2.3");
        assertThat(LinuxServerIpResolver.detect(List.of(
                new InterfaceInfo("eth0", true, false, false, List.of("172.20.1.2"))))).isEqualTo("172.20.1.2");
        assertThat(LinuxServerIpResolver.detect(List.of(
                new InterfaceInfo("eth0", true, false, false, List.of("192.168.0.1"))))).isEqualTo("192.168.0.1");
    }

    @Test
    void rejectsNonSiteLocal172Range() {
        // 172.15.x 和 172.32.x 不是 site-local，应被拒绝并抛异常。
        List<InterfaceInfo> interfaces = List.of(
                new InterfaceInfo("eth0", true, false, false, List.of("172.15.0.1")),
                new InterfaceInfo("eth1", true, false, false, List.of("172.32.0.1")));

        assertThatThrownBy(() -> LinuxServerIpResolver.detect(interfaces))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未能探测到");
    }

    @Test
    void throwsWhenNoUsableInterface() {
        List<InterfaceInfo> interfaces = List.of(
                new InterfaceInfo("lo", true, true, false, List.of("127.0.0.1")),
                new InterfaceInfo("docker0", true, false, false, List.of("172.17.0.1")));

        assertThatThrownBy(() -> LinuxServerIpResolver.detect(interfaces))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未能探测到")
                .hasMessageContaining("docker0");
    }

    @Test
    void throwsWhenInterfaceDown() {
        List<InterfaceInfo> interfaces = List.of(
                new InterfaceInfo("eth0", false, false, false, List.of("192.168.1.10")));

        assertThatThrownBy(() -> LinuxServerIpResolver.detect(interfaces))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resolverCachesResolvedValue() {
        LinuxServerIpResolver resolver = new LinuxServerIpResolver(
                () -> List.of(new InterfaceInfo("eth0", true, false, false, List.of("192.168.1.20"))));

        assertThat(resolver.resolve()).isEqualTo("192.168.1.20");
    }

    @Test
    void preferNonLoopbackListenUrlIpv4OverDetectedInterface() {
        LinuxServerIpResolver resolver = new LinuxServerIpResolver(
                () -> List.of(new InterfaceInfo("eth0", true, false, false, List.of("192.168.1.20"))));

        assertThat(resolver.resolveForListenUrl("http://10.8.0.21:8080")).isEqualTo("10.8.0.21");
    }

    @Test
    void loopbackListenUrlFallsBackToDetectedInterface() {
        LinuxServerIpResolver resolver = new LinuxServerIpResolver(
                () -> List.of(new InterfaceInfo("eth0", true, false, false, List.of("192.168.1.20"))));

        assertThat(resolver.resolveForListenUrl("http://127.0.0.1:8080")).isEqualTo("192.168.1.20");
    }
}
