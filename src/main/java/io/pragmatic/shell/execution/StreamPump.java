package io.pragmatic.shell.execution;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 实时读取子进程输出流，边读边转发到控制台，同时收集到 buffer。
 */
final class StreamPump implements Runnable {
    private final BufferedReader reader;
    private final Appendable console;
    private final StringBuilder buffer = new StringBuilder();

    StreamPump(InputStream stream, Appendable console) {
        this.reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        this.console = console;
    }

    @Override
    public void run() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line).append(System.lineSeparator());
                console.append(line).append(System.lineSeparator());
            }
        } catch (IOException ignored) {
            // 进程已结束
        } catch (Exception ignored) {
            // 控制台写入异常忽略
        }
    }

    String collected() {
        return buffer.toString();
    }
}
