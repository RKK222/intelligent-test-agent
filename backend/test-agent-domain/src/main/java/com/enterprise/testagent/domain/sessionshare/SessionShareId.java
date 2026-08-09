package com.enterprise.testagent.domain.sessionshare;

import java.util.Objects;
import java.util.regex.Pattern;

/** 256 位随机会话分享标识；该标识永久绑定一个平台 Session。 */
public record SessionShareId(String value) {

    private static final Pattern PATTERN = Pattern.compile("shr_[0-9a-f]{64}");

    public SessionShareId {
        value = Objects.requireNonNull(value, "value must not be null").trim();
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("shareId must be shr_ followed by 64 lowercase hex characters");
        }
    }
}
