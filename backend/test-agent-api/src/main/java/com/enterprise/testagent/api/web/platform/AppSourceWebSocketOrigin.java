package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** 应用源码进度通道 Origin 规范化器，保证签票、白名单与 upgrade 使用同一来源身份。 */
final class AppSourceWebSocketOrigin {

    private AppSourceWebSocketOrigin() {
    }

    static String canonicalize(String origin) {
        try {
            URI uri = new URI(origin == null ? "" : origin.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            if (!("http".equals(scheme) || "https".equals(scheme))
                    || host.isBlank()
                    || port > 65_535
                    || (uri.getRawAuthority() != null && uri.getRawAuthority().endsWith(":"))
                    || uri.getRawUserInfo() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null) {
                throw denied();
            }
            if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
                port = -1;
            }
            return new URI(scheme, null, host, port, null, null, null).toASCIIString();
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw denied();
        }
    }

    private static PlatformException denied() {
        return new PlatformException(ErrorCode.FORBIDDEN, "应用源码进度 Origin 无效");
    }
}
