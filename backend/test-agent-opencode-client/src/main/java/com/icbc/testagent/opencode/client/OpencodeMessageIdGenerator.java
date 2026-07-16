package com.icbc.testagent.opencode.client;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 生成与 OpenCode 原生 {@code MessageID.ascending()} 字典序兼容的消息 ID。
 */
public final class OpencodeMessageIdGenerator {

    private static final String PREFIX = "msg_";
    private static final int COUNTER_BITS = 12;
    private static final long TIME_MASK = 0xFFFFFFFFFFFFL;
    private static final int RANDOM_SUFFIX_LENGTH = 14;
    private static final char[] BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private final LongSupplier currentTimeMillis;
    private final Supplier<String> randomSuffix;
    private final AtomicLong lastLogicalTime = new AtomicLong();

    /**
     * 使用系统时钟和安全随机后缀创建生产生成器。
     */
    public OpencodeMessageIdGenerator() {
        this(System::currentTimeMillis, secureRandomSuffixSupplier());
    }

    OpencodeMessageIdGenerator(LongSupplier currentTimeMillis, Supplier<String> randomSuffix) {
        this.currentTimeMillis = Objects.requireNonNull(currentTimeMillis, "currentTimeMillis must not be null");
        this.randomSuffix = Objects.requireNonNull(randomSuffix, "randomSuffix must not be null");
    }

    /**
     * 生成一个 OpenCode 时序消息 ID；同毫秒并发或时钟回退时仍在当前 JVM 内单调递增。
     */
    public String nextId() {
        long timestampBase = currentTimeMillis.getAsLong() << COUNTER_BITS;
        // 状态保留未截断逻辑时间，避免系统时钟回退时重新生成更小的时序前缀。
        long logicalTime = lastLogicalTime.updateAndGet(previous -> Math.max(timestampBase + 1, previous + 1));
        long encodedTime = logicalTime & TIME_MASK;
        return PREFIX + String.format(Locale.ROOT, "%012x", encodedTime) + randomSuffix.get();
    }

    private static Supplier<String> secureRandomSuffixSupplier() {
        SecureRandom random = new SecureRandom();
        return () -> randomBase62(random);
    }

    private static String randomBase62(SecureRandom random) {
        byte[] bytes = new byte[RANDOM_SUFFIX_LENGTH];
        random.nextBytes(bytes);
        StringBuilder suffix = new StringBuilder(RANDOM_SUFFIX_LENGTH);
        for (byte value : bytes) {
            suffix.append(BASE62[Byte.toUnsignedInt(value) % BASE62.length]);
        }
        return suffix.toString();
    }
}
