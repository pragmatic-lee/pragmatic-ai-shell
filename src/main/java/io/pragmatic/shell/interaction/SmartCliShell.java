package io.pragmatic.shell.interaction;

import io.pragmatic.shell.audit.AuditEntry;
import io.pragmatic.shell.audit.AuditLogger;
import io.pragmatic.shell.config.AppConfig;
import io.pragmatic.shell.config.ConfigValidator;
import io.pragmatic.shell.execution.CommandExecutor;
import io.pragmatic.shell.execution.ExecutionRequest;
import io.pragmatic.shell.execution.ExecutionResult;
import io.pragmatic.shell.execution.ProcessCommandExecutor;
import io.pragmatic.shell.nlu.ContextTurn;
import io.pragmatic.shell.nlu.EnvironmentProfile;
import io.pragmatic.shell.nlu.EnvironmentProbe;
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

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
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
    /** 目录栈（pushd/popd，策略一 M2）。 */
    private final Deque<Path> dirStack = new ArrayDeque<>();
    /** 会话环境覆盖表（export/unset/source，策略二 M3），执行时注入子进程。 */
    private final Map<String, String> envOverrides = new LinkedHashMap<>();
    /** 多轮对话上下文（FR-CTX-01）：会话内内存历史，退出即清空。 */
    private final ConversationContext context;
    /** 环境指纹（环境感知）：会话内采集一次，注入语义模式 LLM 调用。 */
    private volatile EnvironmentProfile profile;

    public SmartCliShell(AppConfig config) {
        this(config, resolveInitialMode(config));
    }

    /** 从 shell.initialMode 解析初始模式（缺省/非法值回退语义模式，v4 FR-14）。 */
    private static ShellMode resolveInitialMode(AppConfig config) {
        String mode = config.getShell() != null ? config.getShell().getInitialMode() : null;
        return "direct".equalsIgnoreCase(mode) ? ShellMode.DIRECT : ShellMode.SMART;
    }

    public SmartCliShell(AppConfig config, ShellMode initialMode) {
        this.config = config;
        this.mode = initialMode;
        this.executor = new ProcessCommandExecutor(System.out);
        this.safety = new SafetyFilterChain(config);
        this.audit = new io.pragmatic.shell.audit.FileAuditLogger(
                config.getLogging().getAuditPath(), config.getLogging().isAuditEnabled());
        this.confirm = null; // terminal 在 start 内创建
        this.context = new ConversationContext(config.getLlm().getContext());
        this.profile = probeProfile();
    }

    /** 采集环境指纹（环境感知）：profile.enabled=false 或采集异常时返回 null，不影响其它功能。 */
    private EnvironmentProfile probeProfile() {
        try {
            if (config.getLlm().getProfile() == null || !config.getLlm().getProfile().isEnabled()) {
                return null;
            }
            return new EnvironmentProbe(config.getLlm().getProfile()).probe();
        } catch (Exception e) {
            return null;
        }
    }

    /** 懒加载 NLU，仅在进入语义模式并实际调用 LLM 时初始化（避免直通模式下因缺少 API Key 崩溃）。 */
    private NluService nlu() {
        NluService s = nlu;
        if (s == null) {
            synchronized (this) {
                s = nlu;
                if (s == null) {
                    nlu = s = new LangChainNluService(config, profile);
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
            // 先尝试内建状态命令（cd/pwd/export/alias 等），避免落入子进程导致状态丢失
            if (handleBuiltinStateCommand(cmd, terminal, confirm, completer)) {
                context.add(ContextTurn.stateCommand(cmd, "（REPL 进程内处理，已生效）"));
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
            call = new CancelableNluCall(nlu(), line, context.snapshot(), profile,
                    config.getLlm().getTimeoutSeconds());
            NluResult result = call.await();   // 内部处理超时/取消（Ctrl+C 中断）
            pi.stop();
            switch (result.status()) {
                case UNSAFE -> {
                    context.add(ContextTurn.modelRefused(line, "（模型判定为不安全，已拒绝执行）"));
                    out.println("该操作被判定为不安全，已拒绝执行。");
                }
                case IMPOSSIBLE -> {
                    context.add(ContextTurn.modelRefused(line, "（模型判定不可行）"));
                    out.println("无法执行该请求（模型判定不可行）。");
                }
                case COMMAND -> {
                    // 语义模式下 LLM 生成的状态命令同样在 REPL 层拦截（与直通模式一致）
                    if (handleBuiltinStateCommand(result.command(), terminal, confirm, completer)) {
                        context.add(ContextTurn.stateCommand(result.command(), "（REPL 进程内处理，已生效）"));
                        return;
                    }
                    routeSmart(result.command(), line, terminal, confirm);
                }
            }
        } catch (CancellationException e) {
            pi.stop();
            out.println("已取消本次请求。");
        } catch (TimeoutException e) {
            pi.stop();
            context.add(ContextTurn.modelRefused(line, "（模型调用超时）"));
            out.println("模型响应超时（" + config.getLlm().getTimeoutSeconds() + "s）。");
            downgradeToDirect(out, "超时");
        } catch (Exception e) {
            pi.stop();
            context.add(ContextTurn.modelRefused(line, "（语义服务异常）"));
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
            context.add(ContextTurn.rejected("LLM", input, command, v.message()));
            return;
        }
        boolean needConfirm = v.type() == FilterVerdict.VerdictType.CONFIRM;
        out.println("➜ 建议执行: " + command);
        if (needConfirm) {
            if (!confirm.ask(v.message())) {
                out.println("已跳过。");
                context.add(ContextTurn.skipped("LLM", input, command));
                return;
            }
        } else {
            if (!confirm.ask("")) {
                out.println("已跳过。");
                context.add(ContextTurn.skipped("LLM", input, command));
                return;
            }
        }
        ExecutionResult result = runAndAudit(command, "LLM", input, terminal);
        context.add(ContextTurn.completed(input, command, result.output(), result.exitCode(), result.timedOut()));
    }

    /**
     * 内建状态命令分发器（见 docs/design/状态类命令REPL层处理变更计划.md）：
     * - 目录类（cd/pwd/pushd/popd/dirs）：进程内模拟，持久生效（策略一 M2）；
     * - 环境类（export/unset/source）：维护环境覆盖表，执行时注入子进程（策略二 M3）；
     * - 其余状态类（alias/function）：显式提示不持久，放行子进程原样执行（策略三 M1）。
     * 返回 true 表示已被处理（无需再走子进程执行）。
     */
    private boolean handleBuiltinStateCommand(String command, Terminal terminal,
                                              ConfirmationPrompt confirm, PathAndBuiltinCompleter completer) {
        PrintWriter out = terminal.writer();
        String[] parts = command.trim().split("\\s+");
        String name = parts[0].toLowerCase();
        switch (name) {
            case "pwd":
                out.println(currentDir.toString());
                out.flush();
                return true;
            case "cd":
                return doCd(parts, out, completer);
            case "pushd":
                return doPushd(parts, out, completer);
            case "popd":
                return doPopd(out, completer);
            case "dirs":
                printDirs(out);
                return true;
            case "export":
                return doExport(parts, out);
            case "unset":
                return doUnset(parts, out);
            case "source", ".":
                return doSource(parts, out);
            case "alias", "function":
                // 策略三（M1）：显式提示不持久，不阻断执行
                out.println("⚠ " + name + " 为 shell 会话状态命令，在子进程中执行不会持久生效；"
                        + "如需使用请每次重新定义，或用 ! 前缀确认原样执行");
                out.flush();
                return false;
            default:
                return false;
        }
    }

    /** cd：无参回 workDir，否则基于 currentDir 解析；校验目录存在后更新。 */
    private boolean doCd(String[] parts, PrintWriter out, PathAndBuiltinCompleter completer) {
        Path target;
        if (parts.length < 2 || parts[1].isBlank()) {
            target = Path.of(config.getExecution().getWorkDir()).toAbsolutePath().normalize();
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

    /** pushd：带参时当前目录入栈并进入目标目录；无参时与栈顶交换（对齐 bash 语义）。 */
    private boolean doPushd(String[] parts, PrintWriter out, PathAndBuiltinCompleter completer) {
        Path target;
        if (parts.length < 2 || parts[1].isBlank()) {
            if (dirStack.isEmpty()) {
                out.println("pushd: 目录栈为空");
                out.flush();
                return true;
            }
            Path top = dirStack.pop();
            dirStack.push(currentDir);
            target = top;
        } else {
            dirStack.push(currentDir);
            target = currentDir.resolve(parts[1]).normalize();
        }
        java.io.File dir = target.toFile();
        if (!dir.exists() || !dir.isDirectory()) {
            out.println("pushd: 不是有效目录: " + target);
            out.flush();
            return true;
        }
        currentDir = target.toAbsolutePath().normalize();
        completer.setWorkDir(currentDir);
        out.println("（当前目录 " + currentDir + "）");
        out.flush();
        return true;
    }

    /** popd：栈顶出栈并切换过去；空栈报错。 */
    private boolean doPopd(PrintWriter out, PathAndBuiltinCompleter completer) {
        if (dirStack.isEmpty()) {
            out.println("popd: 目录栈为空");
            out.flush();
            return true;
        }
        currentDir = dirStack.pop().toAbsolutePath().normalize();
        completer.setWorkDir(currentDir);
        out.println("（当前目录 " + currentDir + "）");
        out.flush();
        return true;
    }

    /** dirs：打印目录栈（当前目录在前）。 */
    private void printDirs(PrintWriter out) {
        StringBuilder sb = new StringBuilder("目录栈: ").append(currentDir);
        for (Path p : dirStack) {
            sb.append(" ← ").append(p);
        }
        out.println(sb);
        out.flush();
    }

    /** export：写入环境覆盖表，执行时注入子进程；无参时列出当前会话已导出项。 */
    private boolean doExport(String[] parts, PrintWriter out) {
        if (parts.length < 2) {
            if (envOverrides.isEmpty()) {
                out.println("（当前会话无自定义环境变量）");
            } else {
                envOverrides.forEach((k, v) -> out.println("export " + k + "=" + v));
            }
            out.flush();
            return true;
        }
        for (int i = 1; i < parts.length; i++) {
            String arg = parts[i];
            int eq = arg.indexOf('=');
            if (eq <= 0) {
                // export FOO（无赋值）：保留已有值或置空标记
                envOverrides.put(arg, envOverrides.getOrDefault(arg, ""));
            } else {
                envOverrides.put(arg.substring(0, eq), stripQuotes(arg.substring(eq + 1)));
            }
        }
        out.println("（已设置环境变量，后续命令生效）");
        out.flush();
        return true;
    }

    /** unset：从环境覆盖表移除。 */
    private boolean doUnset(String[] parts, PrintWriter out) {
        for (int i = 1; i < parts.length; i++) {
            envOverrides.remove(parts[i]);
        }
        out.println("（已移除环境变量，后续命令生效）");
        out.flush();
        return true;
    }

    /** source 浅解析：仅提取脚本中的 export KEY=VAL / KEY=VAL 行，其余逻辑不生效。 */
    private boolean doSource(String[] parts, PrintWriter out) {
        if (parts.length < 2 || parts[1].isBlank()) {
            out.println("source: 用法: source <file>");
            out.flush();
            return true;
        }
        Path path = currentDir.resolve(parts[1]).normalize();
        if (!path.toFile().exists() || !path.toFile().isFile()) {
            out.println("source: 文件不存在: " + path);
            out.flush();
            return true;
        }
        int loaded = 0;
        try {
            for (String line : Files.readAllLines(path)) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) {
                    continue;
                }
                String body = t.startsWith("export ") ? t.substring(7).trim() : t;
                int eq = body.indexOf('=');
                if (eq > 0 && body.substring(0, eq).matches("[A-Za-z_][A-Za-z0-9_]*")) {
                    envOverrides.put(body.substring(0, eq), stripQuotes(body.substring(eq + 1).trim()));
                    loaded++;
                }
            }
        } catch (IOException e) {
            out.println("source: 读取失败: " + e.getMessage());
            out.flush();
            return true;
        }
        out.println("（source 已加载 " + loaded + " 个环境变量；脚本中其他逻辑不会生效）");
        out.flush();
        return true;
    }

    /** 去掉值首尾的成对引号（' 或 "）。 */
    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private void directExecute(String command, Terminal terminal, ConfirmationPrompt confirm,
                               String source, String input) {
        FilterVerdict v = safety.evaluate(command);
        PrintWriter out = terminal.writer();
        if (v.type() == FilterVerdict.VerdictType.REJECT) {
            out.println("❌ " + v.message());
            audit.log(entry(source, input, command, -1, 0));
            context.add(ContextTurn.rejected(source, input, command, v.message()));
            return;
        }
        if (v.type() == FilterVerdict.VerdictType.CONFIRM) {
            if (!confirm.ask(v.message())) {
                out.println("已跳过。");
                context.add(ContextTurn.skipped(source, input, command));
                return;
            }
        }
        out.println("➜ 直接执行: " + command);
        ExecutionResult result = runAndAudit(command, source, input, terminal);
        context.add(ContextTurn.direct(input, command, result.output(), result.exitCode(), result.timedOut()));
    }

    private ExecutionResult runAndAudit(String command, String source, String input, Terminal terminal) {
        PrintWriter out = terminal.writer();
        long start = System.currentTimeMillis();
        var result = executor.execute(new ExecutionRequest(
                command, currentDir,
                Duration.ofSeconds(config.getExecution().getDefaultTimeoutSeconds()),
                envOverrides.isEmpty() ? null : Map.copyOf(envOverrides)));
        long duration = System.currentTimeMillis() - start;
        if (result.timedOut()) {
            out.println("⏱ 命令执行超时（" + config.getExecution().getDefaultTimeoutSeconds()
                    + "s），已强制终止。");
        } else {
            out.println("（退出码 " + result.exitCode() + "）");
        }
        audit.log(entry(source, input, command, result.exitCode(), duration));
        out.flush();
        return result;
    }

    private void builtin(String line, Terminal terminal, ConfirmationPrompt confirm) {
        PrintWriter out = terminal.writer();
        String[] parts = line.trim().split("\\s+");
        String name = parts[0].substring(1).toLowerCase();
        switch (name) {
            case "help" -> out.println("/help 帮助  /exit 退出  /mode smart|direct 切换模式  /config 查看配置"
                    + "  /context 查看多轮上下文  /clear 清空上下文  /profile [refresh] 查看/刷新环境指纹");
            case "exit", "quit" -> {
                out.println("再见。");
                System.exit(0);
            }
            case "clear" -> {
                context.clear();
                out.println("（多轮上下文已清空，后续对话不再引用此前轮次）");
            }
            case "context" -> printContext(out);
            case "profile" -> handleProfile(parts, out);
            case "mode" -> {
                if (parts.length > 1) {
                    if (parts[1].equalsIgnoreCase("smart") && !ConfigValidator.llmConfigured(config)) {
                        out.println("语义模式不可用：LLM 配置不完整（apiKey/baseUrl/model），请检查 config.yaml");
                    } else {
                        mode = parts[1].equalsIgnoreCase("direct") ? ShellMode.DIRECT : ShellMode.SMART;
                    }
                }
                out.println("当前模式: " + mode);
            }
            case "config" -> out.println(config.toDisplayString());
            default -> out.println("未知内置命令: " + name + "，输入 /help 查看帮助");
        }
        out.flush();
    }

    /** /context：展示当前多轮上下文轮次（FR-CTX-05-02，内容在入史时已脱敏）。 */
    private void printContext(PrintWriter out) {
        var turns = context.snapshot();
        if (turns.isEmpty()) {
            out.println("（多轮上下文为空" + (context.enabled() ? "" : "；且 llm.context.enabled=false，已禁用记录")
                    + "）");
        } else {
            out.println("（多轮上下文已启用，保留最近 " + config.getLlm().getContext().getMaxTurns()
                    + " 轮，当前 " + turns.size() + " 轮）");
            for (int i = 0; i < turns.size(); i++) {
                ContextTurn t = turns.get(i);
                out.println("[" + (i + 1) + "] [" + t.source() + "] 用户: " + t.userInput());
                if (t.command() != null) {
                    out.println("    命令: " + t.command());
                }
                if (t.rejectReason() != null) {
                    out.println("    结果: ❌ 被拒绝: " + t.rejectReason());
                } else if (t.resultSummary() != null) {
                    String summary = t.resultSummary().length() > 300
                            ? t.resultSummary().substring(0, 300) + "…（完整摘要见发送给模型的上下文）"
                            : t.resultSummary();
                    out.println("    结果: " + (t.timedOut() ? "⏱ 超时; " : "退出码 " + t.exitCode() + "; ")
                            + summary.replace("\n", "\\n"));
                }
            }
        }
        out.flush();
    }

    /** /profile [refresh]：查看当前环境指纹；带 refresh 则强制重新采集（环境感知）。 */
    private void handleProfile(String[] parts, PrintWriter out) {
        boolean refresh = parts.length > 1 && "refresh".equalsIgnoreCase(parts[1]);
        if (refresh) {
            profile = probeProfile();
        }
        if (profile == null) {
            String reason = (config.getLlm().getProfile() != null && !config.getLlm().getProfile().isEnabled())
                    ? "（llm.profile.enabled=false，已禁用环境指纹）"
                    : "（环境指纹不可用，已降级：不向模型注入环境信息）";
            out.println("（环境指纹为空 " + reason + "）");
            out.flush();
            return;
        }
        out.println("（环境指纹，采集于 " + profile.collectedAt() + "）");
        out.println(profile.toPromptBlock());
        out.flush();
    }

    private AuditEntry entry(String source, String input, String command, int exitCode, long durationMs) {
        return new AuditEntry(
                DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                source, input, command, exitCode, durationMs);
    }
}
