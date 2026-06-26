package com.icbc.testagent.common.net;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 探测当前后端 Java 进程所在 Linux 服务器的真实内网 IPv4 地址。
 *
 * <p>遍历主机网络接口，排除回环、虚拟/容器桥接网卡、IPv6 和链路本地地址，只接受
 * site-local 内网地址（10.x / 172.16-31.x / 192.168.x）。探测不到任何符合条件的地址时
 * 直接抛 {@link IllegalStateException} 让启动失败——不兜底 127.0.0.1，也不读任何配置。
 *
 * <p>探测逻辑拆分为纯函数 {@link #detect(List)}，便于用合成数据做单元测试；
 * {@link #enumerateSystemInterfaces()} 负责桥接真实操作系统网络接口。
 */
public final class LinuxServerIpResolver {

    private static final Logger log = LoggerFactory.getLogger(LinuxServerIpResolver.class);

    /** 虚拟/容器桥接网卡名前缀，枚举时跳过，避免误选 docker0/br-/veth 等桥接地址。 */
    private static final Pattern VIRTUAL_INTERFACE = Pattern.compile(
            "docker0|br-.*|veth.*|virbr.*|cni.*|flannel.*|calico.*|tun.*|tap.*");

    private final String resolved;

    /**
     * 使用真实操作系统网络接口枚举器构造，启动时立即探测并缓存结果。
     */
    public LinuxServerIpResolver() {
        this(LinuxServerIpResolver::enumerateSystemInterfaces);
    }

    /**
     * 使用可注入的枚举器构造，便于测试注入合成网卡数据。
     */
    LinuxServerIpResolver(InterfaceEnumerator enumerator) {
        this.resolved = detect(enumerator.enumerate());
        log.info("Linux 服务器真实 IP 探测结果: {}", resolved);
    }

    /**
     * 返回探测到的真实内网 IPv4 地址。
     */
    public String resolve() {
        return resolved;
    }

    /**
     * 纯函数探测逻辑：从给定的网卡信息列表中选出真实内网 IPv4 地址。
     *
     * <p>选择规则：排除回环、未启用、虚拟网卡、IPv6、链路本地地址；只保留 site-local
     * 内网地址；按地址自然序取第一个，保证多网卡环境下结果稳定。无候选时抛异常。
     *
     * @param interfaces 主机网络接口信息列表
     * @return 真实内网 IPv4 地址字符串
     * @throws IllegalStateException 没有任何符合条件的内网 IPv4 地址
     */
    static String detect(List<InterfaceInfo> interfaces) {
        List<String> candidates = new ArrayList<>();
        for (InterfaceInfo nif : interfaces) {
            if (!nif.up || nif.loopback || nif.virtual) {
                continue;
            }
            if (nif.name == null || VIRTUAL_INTERFACE.matcher(nif.name).matches()) {
                continue;
            }
            for (String addr : nif.ipv4Addresses) {
                if (isLoopback(addr) || isLinkLocal(addr) || !isSiteLocal(addr)) {
                    continue;
                }
                candidates.add(addr);
            }
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException(
                    "未能探测到当前 Linux 服务器的真实内网 IPv4 地址（无 site-local 地址）。"
                    + "枚举到的网络接口: " + describe(interfaces));
        }
        Collections.sort(candidates);
        return candidates.get(0);
    }

    /**
     * 枚举真实操作系统网络接口，转换为 {@link InterfaceInfo} 列表。
     */
    static List<InterfaceInfo> enumerateSystemInterfaces() {
        List<InterfaceInfo> result = new ArrayList<>();
        Enumeration<NetworkInterface> enumeration;
        try {
            enumeration = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            throw new IllegalStateException("枚举主机网络接口失败: " + e.getMessage(), e);
        }
        if (enumeration == null) {
            return result;
        }
        for (NetworkInterface nif : Collections.list(enumeration)) {
            List<String> ipv4 = new ArrayList<>();
            for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
                if (addr instanceof Inet4Address) {
                    ipv4.add(addr.getHostAddress());
                }
            }
            try {
                result.add(new InterfaceInfo(
                        nif.getName(),
                        nif.isUp(),
                        nif.isLoopback(),
                        nif.isVirtual(),
                        List.copyOf(ipv4)));
            } catch (SocketException e) {
                throw new IllegalStateException("读取网络接口 " + nif.getName() + " 状态失败: " + e.getMessage(), e);
            }
        }
        return result;
    }

    private static boolean isLoopback(String addr) {
        return addr.startsWith("127.");
    }

    private static boolean isLinkLocal(String addr) {
        return addr.startsWith("169.254.");
    }

    private static boolean isSiteLocal(String addr) {
        if (addr.startsWith("10.")) {
            return true;
        }
        if (addr.startsWith("192.168.")) {
            return true;
        }
        if (addr.startsWith("172.")) {
            String[] parts = addr.split("\\.");
            if (parts.length >= 2) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    private static String describe(List<InterfaceInfo> interfaces) {
        if (interfaces.isEmpty()) {
            return "(无)";
        }
        List<String> entries = new ArrayList<>();
        for (InterfaceInfo nif : interfaces) {
            entries.add(nif.name + "{up=" + nif.up + ", loopback=" + nif.loopback
                    + ", virtual=" + nif.virtual + ", ipv4=" + nif.ipv4Addresses + "}");
        }
        return String.join(", ", entries);
    }

    /** 网卡枚举器，返回当前主机的网络接口信息列表。 */
    @FunctionalInterface
    interface InterfaceEnumerator {
        List<InterfaceInfo> enumerate();
    }

    /** 网络接口的可测试快照，屏蔽 {@link NetworkInterface} 的不可构造性。 */
    record InterfaceInfo(String name, boolean up, boolean loopback, boolean virtual, List<String> ipv4Addresses) {
    }
}
