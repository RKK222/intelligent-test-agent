package com.enterprise.testagent.opencode.runtime.terminal;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 单 PTY 连接输出预算控制，只截断推送给浏览器的内容，不把明文输出写入服务端日志。
 */
public class TerminalOutputLimiter {

    private final int maxFrameBytes;
    private final int maxConnectionBytes;
    private int emittedBytes;
    private boolean warned;

    /**
     * 创建输出预算控制器，帧预算和连接预算至少为 1 字节。
     */
    public TerminalOutputLimiter(int maxFrameBytes, int maxConnectionBytes) {
        this.maxFrameBytes = Math.max(1, maxFrameBytes);
        this.maxConnectionBytes = Math.max(1, maxConnectionBytes);
    }

    /**
     * 生成输出 envelope；超出帧或连接预算时截断，预算耗尽后只发一次 warning。
     */
    public List<TerminalServerMessage> output(String data, int seq) {
        if (data == null || data.isEmpty()) {
            return List.of();
        }
        int remaining = maxConnectionBytes - emittedBytes;
        if (remaining <= 0) {
            return warningOnce();
        }
        int limit = Math.min(maxFrameBytes, remaining);
        String next = truncateUtf8(data, limit);
        emittedBytes += next.getBytes(StandardCharsets.UTF_8).length;
        boolean truncated = next.length() < data.length()
                || next.getBytes(StandardCharsets.UTF_8).length < data.getBytes(StandardCharsets.UTF_8).length;
        return List.of(TerminalServerMessage.output(next, seq, truncated));
    }

    /**
     * 连接输出预算耗尽时返回单次 warning，避免持续刷屏。
     */
    private List<TerminalServerMessage> warningOnce() {
        if (warned) {
            return List.of();
        }
        warned = true;
        return List.of(TerminalServerMessage.warning("PTY_OUTPUT_TRUNCATED", "terminal output truncated"));
    }

    /**
     * 按 UTF-8 字节预算截断字符串，不切断 Unicode code point。
     */
    private String truncateUtf8(String value, int maxBytes) {
        StringBuilder builder = new StringBuilder();
        int bytes = 0;
        for (int index = 0; index < value.length(); ) {
            int codePoint = value.codePointAt(index);
            String text = new String(Character.toChars(codePoint));
            int nextBytes = text.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + nextBytes > maxBytes) {
                break;
            }
            builder.append(text);
            bytes += nextBytes;
            index += Character.charCount(codePoint);
        }
        return builder.toString();
    }
}
