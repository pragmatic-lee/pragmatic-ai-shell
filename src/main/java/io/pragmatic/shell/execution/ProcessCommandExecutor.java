package io.pragmatic.shell.execution;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 基于 ProcessBuilder 的命令执行器。
 * - 跨平台：Unix 用 /bin/sh -c，Windows 用 cmd.exe /c
 * - 实时输出：两个 StreamPump 线程消费 stdout/stderr
 * - 交互命令（ssh/vim 等）、文件传输命令（scp/sftp/rsync）与下载命令（curl/wget）：inheritIO 直连终端，子进程可分配 PTY
 * - 超时：≤ 0 不限时（无限等待，由 Ctrl+C 中断兜底，FR-UTO-01）；显式 > 0 时 waitFor(timeout)，超时则递归销毁整棵进程树（含后台任务等孙进程）
 * - 输出回流（FR-CTX-02）：实时打印的同时收集尾部摘要，随 ExecutionResult.output 返回，供多轮上下文使用
 */
public final class ProcessCommandExecutor implements CommandExecutor {

    /** 需要真实终端（TTY/PTY）的交互式命令，不走管道中继。 */
    private static final List<String> INTERACTIVE_COMMANDS =
            List.of("ssh", "telnet", "vim", "vi", "nvim", "nano", "emacs", "less", "more", "top", "htop");

    /** 容器 CLI：exec/attach（或 run -it）进入容器交互会话，同样需要 TTY。 */
    private static final List<String> CONTAINER_CLIS =
            List.of("docker", "podman", "nerdctl", "crictl", "kubectl");

    /** 文件传输命令：进度显示依赖真实终端（TTY），且长时运行不应受默认超时约束。 */
    private static final List<String> FILE_TRANSFER_COMMANDS = List.of("scp", "sftp", "rsync");

    /** 下载命令：大文件下载长时运行，不应受默认超时约束（否则传输中途被强杀）。 */
    private static final List<String> DOWNLOAD_COMMANDS = List.of("curl", "wget");

    /** 输出捕获上限默认值（FR-CTX-02，与 llm.context.maxResultChars 默认一致）。 */
    public static final int DEFAULT_MAX_OUTPUT_CHARS = 2000;

    private final Appendable console;
    private final int maxOutputChars;
    /** 当前正在执行的进程（管道/直连两分支共用），供 Ctrl+C 中断（FR-UTO-02）；空闲为 null。 */
    private volatile Process currentProcess;

    /** 输出捕获上限取默认值 2000 字符。 */
    public ProcessCommandExecutor(Appendable console) {
        this(console, DEFAULT_MAX_OUTPUT_CHARS);
    }

    /**
     * @param maxOutputChars 输出捕获上限（字符）；≤ 0 表示采用 {@link #DEFAULT_MAX_OUTPUT_CHARS}
     *                       （配置缺省或非法值时的兜底，避免退化成只捕获 1 个字符）
     */
    public ProcessCommandExecutor(Appendable console, int maxOutputChars) {
        this.console = console;
        this.maxOutputChars = maxOutputChars > 0 ? maxOutputChars : DEFAULT_MAX_OUTPUT_CHARS;
    }

    @Override
    public ExecutionResult execute(ExecutionRequest req) {
        Path workDir = req.workDir() != null ? req.workDir() : Path.of(".");
        List<String> cmd = buildCommand(req.command());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(false);
        // 策略二 M3：注入会话环境覆盖表（export/unset 结果），子进程可见前序命令的设置
        if (req.env() != null && !req.env().isEmpty()) {
            pb.environment().putAll(req.env());
        }

        long start = System.currentTimeMillis();
        // 交互式命令直接继承终端 IO，避免 stdin 非 TTY 导致 ssh 等无法分配伪终端
        if (isInteractive(req.command())) {
            return executeInherited(pb, start);
        }
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Process process = pb.start();
            currentProcess = process; // 记录当前进程，供 Ctrl+C 中断（FR-UTO-02）
            // 非交互命令不提供输入：立即关闭子进程 stdin，使其读到 EOF 而非无限阻塞（P0-1）。
            // 管道仅当写入端全部关闭时才产生 EOF；父进程若一直持有写入端，cat/sort/grep 等
            // 读取 stdin 的命令会阻塞至超时才被强杀。关闭后行为等同于在原生终端按下 Ctrl+D。
            try {
                process.getOutputStream().close();
            } catch (IOException ignored) {
                // 流已关闭或不支持写入（如已重定向）时忽略，不影响后续执行
            }
            StreamPump outPump = new StreamPump(process.getInputStream(), console, maxOutputChars);
            StreamPump errPump = new StreamPump(process.getErrorStream(), console, maxOutputChars);
            Future<?> outF = pool.submit(outPump);
            Future<?> errF = pool.submit(errPump);

            // 超时 ≤ 0 = 不限时：无限等待，挂死命令由 Ctrl+C 中断兜底（FR-UTO-01）
            long timeoutMs = req.timeout().toMillis();
            boolean finished;
            if (timeoutMs > 0) {
                finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            } else {
                process.waitFor();
                finished = true;
            }
            if (!finished) {
                destroyProcessTree(process);
                pool.shutdownNow();
                long duration = System.currentTimeMillis() - start;
                return new ExecutionResult(-1, "", duration, true);
            }
            outF.get();
            errF.get();
            long duration = System.currentTimeMillis() - start;
            return new ExecutionResult(process.exitValue(), mergeOutput(outPump, errPump), duration, false);
        } catch (IOException e) {
            pool.shutdownNow();
            long duration = System.currentTimeMillis() - start;
            return new ExecutionResult(-1, "启动失败: " + e.getMessage(), duration, false);
        } catch (InterruptedException | ExecutionException e) {
            pool.shutdownNow();
            long duration = System.currentTimeMillis() - start;
            return new ExecutionResult(-1, "执行异常: " + e.getMessage(), duration, false);
        } finally {
            currentProcess = null;
        }
    }

    /** 合并 stdout/stderr 收集结果，超过上限截取尾部（FR-CTX-02-02，最新输出优先）。 */
    private String mergeOutput(StreamPump out, StreamPump err) {
        String stdout = out.collected();
        String stderr = err.collected();
        String merged = stderr.isBlank() ? stdout
                : (stdout.isBlank() ? stderr
                : stdout + System.lineSeparator() + "[stderr]" + System.lineSeparator() + stderr);
        if (merged.length() > maxOutputChars) {
            return "…（输出过长，仅保留尾部）" + System.lineSeparator()
                    + merged.substring(merged.length() - maxOutputChars);
        }
        return merged;
    }

    /**
     * 递归销毁整棵进程树（超时场景），避免后台任务（如 nohup/& 启动的孙进程）残留为孤儿进程。
     * 双保险：第一轮枚举并强杀全部后代与 shell 本身；短暂等待后若 shell 仍未退出
     * （枚举瞬间新 fork 的进程），再兜底强杀一轮。
     */
    private void destroyProcessTree(Process process) {
        for (int round = 0; round < 2; round++) {
            process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            try {
                if (process.waitFor(500, TimeUnit.MILLISECONDS)) {
                    return; // shell 已退出，后代也已强杀
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return; // 被中断时不再等待（进程已收到强杀信号）
            }
        }
    }

    /**
     * 中断当前正在执行的命令（Ctrl+C 入口，FR-UTO-02）：
     * 强杀运行中进程的整棵进程树，与超时清理同一实现；无命令在执行时无操作。
     */
    public void interruptCurrent() {
        Process p = currentProcess;
        if (p != null && p.isAlive()) {
            destroyProcessTree(p);
        }
    }

    /** inheritIO 方式执行：输出直达终端，不设超时（交互会话由用户自行退出）。 */
    private ExecutionResult executeInherited(ProcessBuilder pb, long start) {
        try {
            pb.inheritIO();
            Process process = pb.start();
            currentProcess = process; // 记录当前进程，供 Ctrl+C 中断（FR-UTO-02）
            int exit = process.waitFor();
            long duration = System.currentTimeMillis() - start;
            return new ExecutionResult(exit, "", duration, false);
        } catch (IOException e) {
            long duration = System.currentTimeMillis() - start;
            return new ExecutionResult(-1, "启动失败: " + e.getMessage(), duration, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long duration = System.currentTimeMillis() - start;
            return new ExecutionResult(-1, "执行被中断: " + e.getMessage(), duration, false);
        } finally {
            currentProcess = null;
        }
    }

    /**
     * 首个 token 命中交互式命令清单（如 ssh/vim/top）、文件传输命令清单（scp/sftp/rsync）、
     * 下载命令清单（curl/wget），或容器 CLI 的 exec/attach/run -it 时走 inheritIO。
     */
    static boolean isInteractive(String command) {
        if (command == null) {
            return false;
        }
        String[] parts = command.trim().split("\\s+");
        if (parts.length == 0) {
            return false;
        }
        String name = baseName(parts[0]);
        if (INTERACTIVE_COMMANDS.contains(name) || FILE_TRANSFER_COMMANDS.contains(name)
                || DOWNLOAD_COMMANDS.contains(name)) {
            return true;
        }
        return CONTAINER_CLIS.contains(name) && isContainerInteractive(parts);
    }

    /** 提取命令名，兼容绝对路径调用，如 /usr/bin/ssh。 */
    private static String baseName(String token) {
        String name = token.toLowerCase();
        int idx = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return idx >= 0 ? name.substring(idx + 1) : name;
    }

    /**
     * 容器交互会话判定：docker exec -it <容器> bash / docker attach <容器> /
     * docker run -it <镜像> bash 等；attach 本身即交互会话，exec/run 需带 -i/-t 交互标志。
     */
    private static boolean isContainerInteractive(String[] parts) {
        boolean interactiveSubcommand = false; // exec / run
        boolean wantsTty = false;
        for (int i = 1; i < parts.length; i++) {
            String arg = parts[i];
            if (arg.equals("attach")) {
                return true; // attach 直接进入容器交互会话
            }
            if (arg.equals("exec") || arg.equals("run")) {
                interactiveSubcommand = true;
                continue;
            }
            String lower = arg.toLowerCase();
            if (lower.equals("-it") || lower.equals("-ti") || lower.equals("-i")
                    || lower.equals("-t") || lower.equals("--interactive") || lower.equals("--tty")) {
                wantsTty = true;
            }
        }
        return interactiveSubcommand && wantsTty;
    }

    private List<String> buildCommand(String command) {
        List<String> cmd = new ArrayList<>();
        if (isWindows()) {
            cmd.add("cmd.exe");
            cmd.add("/c");
        } else {
            cmd.add("/bin/sh");
            cmd.add("-c");
        }
        cmd.add(command);
        return cmd;
    }

    static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
