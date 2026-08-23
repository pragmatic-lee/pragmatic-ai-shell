package io.pragmatic.shell.interaction;

import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;

import java.io.PrintWriter;

/**
 * 统一的「回车确认 / n 跳过」提示（FR-01-05）。
 */
public final class ConfirmationPrompt {

    private final Terminal terminal;
    private final LineReader reader;

    public ConfirmationPrompt(Terminal terminal, LineReader reader) {
        this.terminal = terminal;
        this.reader = reader;
    }

    /**
     * 展示提示并等待用户输入。
     * @return true 表示确认执行，false 表示跳过。
     */
    public boolean ask(String message) {
        PrintWriter writer = terminal.writer();
        writer.print(message + " 按回车执行，输入 n 跳过: ");
        writer.flush();
        try {
            String line = reader.readLine();
            return line == null || !line.trim().equalsIgnoreCase("n");
        } catch (Exception e) {
            return false;
        }
    }
}
