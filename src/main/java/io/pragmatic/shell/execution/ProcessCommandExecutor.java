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
 * - 交互命令（ssh/vim 等）：inheritIO 直连终端，子进程可分配 PTY
 * - 超时：waitFor(timeout)，超时则强制销毁
 */
public final class ProcessCommandExecutor implements CommandExecutor {

    /** 需要真实终端（TTY/PTY）的交互式命令，不走管道中继。 */
    private static final List<String> INTERACTIVE_COMMANDS =
            List.of("ssh", "telnet", "vim", "vi", "nvim", "nano", "emacs", "less", "more", "top", "htop");

    private final Appendable console;

    public ProcessCommandExecutor(Appendable console) {
        this.console = console;
    }

    @Override
    public ExecutionResult execute(ExecutionRequest req) {
        Path workDir = req.workDir() != null ? req.workDir() : Path.of(".");
        List<String> cmd = buildCommand(req.command());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(false);

        long start = System.currentTimeMillis();
        // 交互式命令直接继承终端 IO，避免 stdin 非 TTY 导致 ssh 等无法分配伪终端
        if (isInteractive(req.command())) {
            return executeInherited(pb, start);
        }
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Process process = pb.start();
            Future<?> outF = pool.submit(new StreamPump(process.getInputStream(), console));
            Future<?> errF = pool.submit(new StreamPump(process.getErrorStream(), console));

            boolean finished = process.waitFor(req.timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                pool.shutdownNow();
                long duration = System.currentTimeMillis() - start;
                return new ExecutionResult(-1, "", duration, true);
            }
            outF.get();
            errF.get();
            long duration = System.currentTimeMillis() - start;
            return new ExecutionResult(process.exitValue(), "", duration, false);
        } catch (IOException e) {
            pool.shutdownNow();
            long duration = System.currentTimeMillis() - start;
            return new ExecutionResult(-1, "启动失败: " + e.getMessage(), duration, false);
        } catch (InterruptedException | ExecutionException e) {
            pool.shutdownNow();
            long duration = System.currentTimeMillis() - start;
            return new ExecutionResult(-1, "执行异常: " + e.getMessage(), duration, false);
        }
    }

    /** inheritIO 方式执行：输出直达终端，不设超时（交互会话由用户自行退出）。 */
    private ExecutionResult executeInherited(ProcessBuilder pb, long start) {
        try {
            pb.inheritIO();
            Process process = pb.start();
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
        }
    }

    /** 首个 token 命中交互式命令清单时走 inheritIO（如 ssh/vim/top）。 */
    static boolean isInteractive(String command) {
        if (command == null) {
            return false;
        }
        String[] parts = command.trim().split("\\s+");
        if (parts.length == 0) {
            return false;
        }
        String name = parts[0].toLowerCase();
        // 兼容绝对路径调用，如 /usr/bin/ssh
        int idx = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (idx >= 0) {
            name = name.substring(idx + 1);
        }
        return INTERACTIVE_COMMANDS.contains(name);
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
