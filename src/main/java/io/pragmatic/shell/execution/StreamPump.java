package io.pragmatic.shell.execution;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 实时读取子进程输出流，边读边转发到控制台，同时收集到有上限的尾部缓冲
 * （FR-CTX-02：命令执行结果回流，超过 maxChars 仅保留尾部并带截断标记）。
 */
final class StreamPump implements Runnable {
    private final BufferedReader reader;
    private final Appendable console;
    /** 收集缓冲（实时打印之外的结构化副本）。 */
    private final StringBuilder buffer = new StringBuilder();
    private final int maxChars;
    private boolean truncated;

    StreamPump(InputStream stream, Appendable console, int maxChars) {
        this.reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        this.console = console;
        this.maxChars = Math.max(1, maxChars);
    }

    @Override
    public void run() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                append(line);
                console.append(line).append(System.lineSeparator());
            }
        } catch (IOException ignored) {
            // 进程已结束
        } catch (Exception ignored) {
            // 控制台写入异常忽略
        }
    }

    /** 追加一行到尾部缓冲；超限时丢弃头部、保留尾部，并标记截断。 */
    private void append(String line) {
        buffer.append(line).append(System.lineSeparator());
        if (buffer.length() > maxChars) {
            buffer.delete(0, buffer.length() - maxChars);
            truncated = true;
        }
    }

    /** 已收集的输出（截断时带前缀标记）。 */
    String collected() {
        String s = buffer.toString();
        return truncated ? "…（输出过长，仅保留尾部）" + System.lineSeparator() + s : s;
    }
}
