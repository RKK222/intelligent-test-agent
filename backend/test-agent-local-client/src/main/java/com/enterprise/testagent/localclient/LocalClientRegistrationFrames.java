package com.enterprise.testagent.localclient;

import com.enterprise.testagent.localclient.protocol.LocalClientFrame;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameCodec;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameType;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.enterprise.testagent.localclient.protocol.LocalClientProtocol;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 统一构造常规连接和首次接入探测使用的 REGISTER，避免认证字段在两个入口漂移。 */
final class LocalClientRegistrationFrames {

    private static final LocalClientFrameCodec CODEC = new LocalClientFrameCodec();

    private LocalClientRegistrationFrames() {
    }

    static LocalClientFrame create(
            LocalClientConfiguration configuration,
            LocalClientCredentialFile.Credentials credentials,
            LocalClientPersistentState state,
            LocalClientBuildInfo buildInfo,
            String requestId,
            String traceId) {
        // 只有下载根、公钥和安装根三项齐全时才声明自更新，避免服务端向无法安全下载的实例发命令。
        List<String> capabilities = configuration.selfUpdateConfigured()
                ? buildInfo.capabilities()
                : buildInfo.capabilities().stream()
                        .filter(capability -> !LocalClientBuildInfo.SELF_UPDATE_CAPABILITY.equals(capability))
                        .toList();
        LocalClientPayloads.Register register = new LocalClientPayloads.Register(
                credentials.clientKey(),
                state.clientInstanceId(),
                configuration.clientName(),
                platform(),
                architecture(),
                buildInfo.clientVersion(),
                "2.0.18",
                reportedAddresses(),
                credentials.unifiedAuthId(),
                buildInfo.launcherVersion(),
                capabilities);
        return new LocalClientFrame(
                LocalClientProtocol.VERSION,
                LocalClientFrameType.REGISTER,
                requestId,
                traceId,
                null,
                CODEC.payload(register));
    }

    static List<String> reportedAddresses() {
        try {
            List<String> result = new ArrayList<>();
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp()) {
                    continue;
                }
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (!address.isLoopbackAddress() && result.size() < 16) {
                        result.add(address.getHostAddress());
                    }
                }
            }
            return List.copyOf(result);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private static String platform() {
        return LocalClientPlatform.current().platform();
    }

    private static String architecture() {
        return LocalClientPlatform.current().architecture();
    }
}
