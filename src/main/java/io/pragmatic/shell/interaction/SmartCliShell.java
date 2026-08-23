package io.pragmatic.shell.interaction;

import io.pragmatic.shell.audit.AuditEntry;
import io.pragmatic.shell.audit.AuditLogger;
import io.pragmatic.shell.config.AppConfig;
import io.pragmatic.shell.execution.CommandExecutor;
import io.pragmatic.shell.execution.ExecutionRequest;
import io.pragmatic.shell.execution.ProcessCommandExecutor;
import io.pragmatic.shell.nlu.LangChainNluService;
import io.pragmatic.shell.nlu.NluResult;
import io.pragmatic.shell.nlu.NluService;
import io.pragmatic.shell.safety.FilterVerdict;
import io.pragmatic.shell.safety.SafetyFilterChain;
import org.jline.reader.History;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.PrintWriter;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

/**
 * 交互层核心：JLine REPL 主循环（FR-01）。
 * 路由：/ 内置命令 → builtin；! 前缀或 DIRECT 模式 → 直通；否则语义模式。
 */
public final class SmartCliShell {

    private final AppConfig config;
    private volatile NluService nlu;
    private final CommandExecutor executor;
    private final SafetyFilterChain safety;
    private final AuditLogger audit;
    private final ConfirmationPrompt confirm;
    private ShellMode mode = ShellMode.SMART;
    private java.nio.file.Path currentDir;

    public SmartCliShell(AppConfig config) {
        this(config, ShellMode.SMART);
    }

    public SmartCliShell(AppConfig config, ShellMode initialMode) {
        this.config = config;
        this.mode = initialMode;
        this.executor = new ProcessCommandExecutor(System.out);
        this.safety = new SafetyFilterChain(config);
        this.audit = new io.pragmatic.shell.audit.FileAuditLogger(
                config.getLogging().getAuditPath(), config.getLogging().isAuditEnabled());
        this.confirm = null; // terminal 在 start 内创建
    }

    /** 懒加载 NLU，仅在进入语义模式并实际调用 LLM 时初始化（避免直通模式下因缺少 API Key 崩溃）。 */
    private NluService nlu() {
        NluService s = nlu;
        if (s == null) {
            synchronized (this) {
                s = nlu;
                if (s == null) {
                    nlu = s = new LangChainNluService(config);
                }
            }
        }
        return s;
    }

    public void start() throws Exception {
        Terminal terminal = TerminalBuilder.builder().system(true).build();
        PrintWriter out = terminal.writer();
        History history = new DefaultHistory();
        this.currentDir = java.nio.file.Path.of(config.getExecution().getWorkDir())
                .toAbsolutePath().normalize();
        PathAndBuiltinCompleter completer = new PathAndBuiltinCompleter(currentDir);
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .history(history)
                .completer(completer)
                .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                .build();
        ConfirmationPrompt confirm = new ConfirmationPrompt(terminal, reader);

        out.println("🤖 Smart CLI v1.0 已启动（" + (mode == ShellMode.SMART ? "语义" : "直通") + "模式）");
        out.println("输入 /help 查看帮助，! 开头直接进入直通模式");
        out.flush();

        while (true) {
            String prompt = mode == ShellMode.SMART ? "🤖 > " : "▶ > ";
            String line;
            try {
                line = reader.readLine(prompt);
            } catch (org.jline.reader.EndOfFileException eof) {
                break; // Ctrl+D / 输入流结束
            }
            if (line == null) {
                break; // Ctrl+D
            }
            if (line.isBlank()) {
                continue;
            }
            handle(line, terminal, confirm, completer);
        }
        terminal.close();
    }

    private void handle(String line, Terminal terminal, ConfirmationPrompt confirm,
                        PathAndBuiltinCompleter completer) {
        PrintWriter out = terminal.writer();
        if (line.startsWith("/")) {
            builtin(line, terminal, confirm);
            return;
        }
        if (line.startsWith("!") || mode == ShellMode.DIRECT) {
            String cmd = line.startsWith("!") ? line.substring(1).strip() : line.strip();
            // 先尝试 shell 内建目录命令（cd/pwd），避免落入子进程无法持久切换目录
            if (tryBuiltinDirCommand(cmd, terminal, confirm, completer)) {
                return;
            }
            directExecute(cmd, terminal, confirm, "USER", line);
            return;
        }
        // 语义模式：异步调用 + 进度指示器 + 可取消（FR-WAIT 系列）
        CancelableNluCall call = null;
        ProgressIndicator pi = new SpinnerProgressIndicator(
                out, config.getLlm().isShowProgress(),
                java.time.Duration.ofSeconds(config.getLlm().getTimeoutSeconds()));
        try {
            pi.start();
            call = new CancelableNluCall(nlu(), line, config.getLlm().getTimeoutSeconds());
            NluResult result = call.await();   // 内部处理超时/取消（Ctrl+C 中断）
            pi.stop();
            switch (result.status()) {
                case UNSAFE -> out.println("该操作被判定为不安全，已拒绝执行。");
                case IMPOSSIBLE -> out.println("无法执行该请求（模型判定不可行）。");
                case COMMAND -> routeSmart(result.command(), line, terminal, confirm);
            }
        } catch (CancellationException e) {
            if (pi != null) {
                pi.stop();
            }
            out.println("已取消本次请求。");
        } catch (TimeoutException e) {
            if (pi != null) {
                pi.stop();
            }
            out.println("模型响应超时（" + config.getLlm().getTimeoutSeconds() + "s）。");
            downgradeToDirect(out, "超时");
        } catch (Exception e) {
            if (pi != null) {
                pi.stop();
            }
            out.println("语义服务不可用，已切换为直通模式。原因: " + e.getMessage());
            downgradeToDirect(out, "异常: " + e.getMessage());
        } finally {
            if (call != null) {
                call.shutdown();
            }
            out.flush();
        }
    }

    /** 降级到直通模式（FR-WAIT-04/05：异常/超时后进入直通，而非崩溃）。 */
    private void downgradeToDirect(PrintWriter out, String reason) {
        out.println("[降级] 语义能力不可用（" + reason + "），已切换为直通模式。后续命令将直接执行。");
        mode = ShellMode.DIRECT;
    }

    private void routeSmart(String command, String input, Terminal terminal, ConfirmationPrompt confirm) {
        FilterVerdict v = safety.evaluate(command);
        PrintWriter out = terminal.writer();
        if (v.type() == FilterVerdict.VerdictType.REJECT) {
            out.println("❌ " + v.message());
            audit.log(entry("LLM", input, command, -1, 0));
            return;
        }
        boolean needConfirm = v.type() == FilterVerdict.VerdictType.CONFIRM;
        out.println("➜ 建议执行: " + command);
        if (needConfirm) {
            if (!confirm.ask(v.message())) {
                out.println("已跳过。");
                return;
            }
        } else {
            if (!confirm.ask("")) {
                out.println("已跳过。");
                return;
            }
        }
        runAndAudit(command, "LLM", input, terminal);
    }

    /**
     * 拦截 shell 内建目录命令 cd / pwd，使其在 REPL 层面持久生效。
     * 返回 true 表示已被处理（无需再走子进程执行）。
     */
    private boolean tryBuiltinDirCommand(String command, Terminal terminal,
                                         ConfirmationPrompt confirm, PathAndBuiltinCompleter completer) {
        PrintWriter out = terminal.writer();
        String[] parts = command.trim().split("\\s+");
        String name = parts[0].toLowerCase();
        if (name.equals("pwd")) {
            out.println(currentDir.toString());
            out.flush();
            return true;
        }
        if (name.equals("cd")) {
            java.nio.file.Path target;
            if (parts.length < 2 || parts[1].isBlank()) {
                target = java.nio.file.Path.of(config.getExecution().getWorkDir()).toAbsolutePath().normalize();
            } else {
                target = currentDir.resolve(parts[1]).normalize();
            }
            java.io.File dir = target.toFile();
            if (!dir.exists() || !dir.isDirectory()) {
                out.println("cd: 不是有效目录: " + target);
                out.flush();
                return true;
            }
            currentDir = target.toAbsolutePath().normalize();
            completer.setWorkDir(currentDir);   // 同步更新 Tab 补全基准目录
            out.println("（当前目录 " + currentDir + "）");
            out.flush();
            return true;
        }
        return false;
    }

    private void directExecute(String command, Terminal terminal, ConfirmationPrompt confirm,
                               String source, String input) {
        FilterVerdict v = safety.evaluate(command);
        PrintWriter out = terminal.writer();
        if (v.type() == FilterVerdict.VerdictType.REJECT) {
            out.println("❌ " + v.message());
            audit.log(entry(source, input, command, -1, 0));
            return;
        }
        if (v.type() == FilterVerdict.VerdictType.CONFIRM) {
            if (!confirm.ask(v.message())) {
                out.println("已跳过。");
                return;
            }
        }
        out.println("➜ 直接执行: " + command);
        runAndAudit(command, source, input, terminal);
    }

    private void runAndAudit(String command, String source, String input, Terminal terminal) {
        PrintWriter out = terminal.writer();
        long start = System.currentTimeMillis();
        var result = executor.execute(new ExecutionRequest(
                command, currentDir,
                Duration.ofSeconds(config.getExecution().getDefaultTimeoutSeconds())));
        long duration = System.currentTimeMillis() - start;
        if (result.timedOut()) {
            out.println("⏱ 命令执行超时（" + config.getExecution().getDefaultTimeoutSeconds()
                    + "s），已强制终止。");
        } else {
            out.println("（退出码 " + result.exitCode() + "）");
        }
        audit.log(entry(source, input, command, result.exitCode(), duration));
        out.flush();
    }

    private void builtin(String line, Terminal terminal, ConfirmationPrompt confirm) {
        PrintWriter out = terminal.writer();
        String[] parts = line.trim().split("\\s+");
        String name = parts[0].substring(1).toLowerCase();
        switch (name) {
            case "help" -> out.println("/help 帮助  /exit 退出  /mode smart|direct 切换模式  /config 查看配置");
            case "exit", "quit" -> {
                out.println("再见。");
                System.exit(0);
            }
            case "mode" -> {
                if (parts.length > 1) {
                    mode = parts[1].equalsIgnoreCase("direct") ? ShellMode.DIRECT : ShellMode.SMART;
                }
                out.println("当前模式: " + mode);
            }
            case "config" -> out.println(config);
            default -> out.println("未知内置命令: " + name + "，输入 /help 查看帮助");
        }
        out.flush();
    }

    private AuditEntry entry(String source, String input, String command, int exitCode, long durationMs) {
        return new AuditEntry(
                DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                source, input, command, exitCode, durationMs);
    }
}
