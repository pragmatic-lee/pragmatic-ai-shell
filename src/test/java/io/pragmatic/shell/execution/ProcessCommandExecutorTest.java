package io.pragmatic.shell.execution;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证策略二 M3 的环境注入：ExecutionRequest.env 应传递给子进程环境。
 */
class ProcessCommandExecutorTest {

    @Test
    void envInjectedIntoChildProcess() {
        StringBuilder out = new StringBuilder();
        ProcessCommandExecutor executor = new ProcessCommandExecutor(out);
        ExecutionResult result = executor.execute(new ExecutionRequest(
                "echo $SMARTCLI_TEST_VAR", Path.of("/tmp"),
                Duration.ofSeconds(10), Map.of("SMARTCLI_TEST_VAR", "hello-env")));
        assertEquals(0, result.exitCode());
        assertTrue(out.toString().contains("hello-env"), "子进程应看到注入的环境变量, 实际输出: " + out);
    }

    @Test
    void nullEnvKeepsInheritedEnvironment() {
        StringBuilder out = new StringBuilder();
        ProcessCommandExecutor executor = new ProcessCommandExecutor(out);
        ExecutionResult result = executor.execute(new ExecutionRequest(
                "echo ok", Path.of("/tmp"), Duration.ofSeconds(10), null));
        assertEquals(0, result.exitCode());
        assertTrue(out.toString().contains("ok"), "null env 时命令应正常执行, 实际输出: " + out);
    }

    @Test
    void containerInteractiveCommandsNeedInheritIo() {
        // docker exec -it 进入容器交互会话：必须走 inheritIO（否则 stdin 非 TTY，-t 报错进不去）
        assertTrue(ProcessCommandExecutor.isInteractive("docker exec -it 2c6a71254de4 /bin/bash"));
        assertTrue(ProcessCommandExecutor.isInteractive("docker exec --interactive --tty abc bash"));
        assertTrue(ProcessCommandExecutor.isInteractive("kubectl exec -it pod-name -- bash"));
        assertTrue(ProcessCommandExecutor.isInteractive("docker attach abc"));
        assertTrue(ProcessCommandExecutor.isInteractive("docker run -it ubuntu bash"));
        // 传统交互命令仍生效
        assertTrue(ProcessCommandExecutor.isInteractive("ssh user@host"));
    }

    @Test
    void fileTransferCommandsNeedInheritIo() {
        // 文件传输命令进度显示依赖 TTY 且长时运行，必须走 inheritIO（否则无进度条、60 秒被强杀、密码认证失败）
        assertTrue(ProcessCommandExecutor.isInteractive("scp big.tar.gz user@host:/tmp/"));
        assertTrue(ProcessCommandExecutor.isInteractive("sftp user@host"));
        assertTrue(ProcessCommandExecutor.isInteractive("rsync -avz src/ user@host:/backup/"));
        assertTrue(ProcessCommandExecutor.isInteractive("rsync --progress a b"));
        // 绝对路径调用同样命中（baseName 归一化）
        assertTrue(ProcessCommandExecutor.isInteractive("/usr/bin/scp file user@host:/tmp/"));
        assertTrue(ProcessCommandExecutor.isInteractive("/usr/bin/sftp user@host"));
    }

    @Test
    void plainContainerCommandsKeepPipeRelay() {
        // 非交互容器命令（无 -it）：保持管道中继与超时兜底
        assertFalse(ProcessCommandExecutor.isInteractive("docker ps"));
        assertFalse(ProcessCommandExecutor.isInteractive("docker exec abc ls"));
        assertFalse(ProcessCommandExecutor.isInteractive("docker run -d nginx"));
        assertFalse(ProcessCommandExecutor.isInteractive("ls -la"));
        assertFalse(ProcessCommandExecutor.isInteractive("echo scp file"));
        assertFalse(ProcessCommandExecutor.isInteractive(null));
    }

    @Test
    void configuredMaxOutputCharsIsRespected() {
        // 生成约 2800 字符输出：超过默认上限 2000，但低于 5000。
        // 用 5000 构造应完整捕获（无截断标记），用默认构造应触发截断 —— 证明配置真正生效。
        String script = "for i in $(seq 1 200); do echo line-$i-xxxx; done";

        StringBuilder outLarge = new StringBuilder();
        ExecutionResult large = new ProcessCommandExecutor(outLarge, 5000).execute(
                new ExecutionRequest(script, Path.of("/tmp"), Duration.ofSeconds(20), null));
        assertEquals(0, large.exitCode());
        assertFalse(large.output().startsWith("…"),
                "上限 5000 时约 2800 字符输出不应触发截断, 实际开头: " + large.output().substring(0, 20));

        StringBuilder outDefault = new StringBuilder();
        ExecutionResult def = new ProcessCommandExecutor(outDefault).execute(
                new ExecutionRequest(script, Path.of("/tmp"), Duration.ofSeconds(20), null));
        assertEquals(0, def.exitCode());
        assertTrue(def.output().startsWith("…"),
                "默认上限 2000 时应触发截断（对照组）");

        assertTrue(large.output().length() > def.output().length(),
                "配置更大的上限应捕获更多内容: " + large.output().length() + " vs " + def.output().length());
    }

    @Test
    void nonPositiveMaxOutputCharsFallsBackToDefault() {
        StringBuilder out = new StringBuilder();
        // ≤ 0 应回退为默认上限，而非退化成只捕获 1 个字符
        ProcessCommandExecutor executor = new ProcessCommandExecutor(out, 0);
        ExecutionResult result = executor.execute(new ExecutionRequest(
                "echo fallback-check-marker", Path.of("/tmp"), Duration.ofSeconds(10), null));
        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("fallback-check-marker"),
                "回退默认值后应能捕获完整输出, 实际: " + result.output());
    }

    @Test
    void stdinReadingCommandReturnsImmediately() {
        StringBuilder out = new StringBuilder();
        ProcessCommandExecutor executor = new ProcessCommandExecutor(out);
        long start = System.currentTimeMillis();
        // cat 不带文件参数会读取 stdin；修复前会阻塞至超时，修复后应立即 EOF 退出
        ExecutionResult result = executor.execute(new ExecutionRequest(
                "cat", Path.of("/tmp"), Duration.ofSeconds(3), null));
        long elapsed = System.currentTimeMillis() - start;
        assertFalse(result.timedOut(), "cat 应立即读到 EOF 退出，而非阻塞到超时");
        assertEquals(0, result.exitCode());
        assertTrue(elapsed < 3000, "应立即返回，实际耗时: " + elapsed + "ms");
    }

    @Test
    void stdinReadingCommandKeepsOwnExitCode() {
        StringBuilder out = new StringBuilder();
        ProcessCommandExecutor executor = new ProcessCommandExecutor(out);
        // grep 无匹配时退出码为 1；修复前会因阻塞超时返回 -1
        ExecutionResult result = executor.execute(new ExecutionRequest(
                "grep zzz-no-such-pattern", Path.of("/tmp"), Duration.ofSeconds(3), null));
        assertFalse(result.timedOut());
        assertEquals(1, result.exitCode(), "grep 无匹配应返回自身退出码 1，不应被超时掩盖");
    }

    @Test
    void pipedInputStillWorks() {
        StringBuilder out = new StringBuilder();
        ProcessCommandExecutor executor = new ProcessCommandExecutor(out);
        // 命令内部的管道由 shell 自建，不受 stdin 关闭影响
        ExecutionResult result = executor.execute(new ExecutionRequest(
                "printf 'b\\na\\n' | sort", Path.of("/tmp"), Duration.ofSeconds(10), null));
        assertEquals(0, result.exitCode());
        assertTrue(out.toString().contains("a"), "内部管道应正常工作, 实际输出: " + out);
    }

    @Test
    void timeoutDestroysWholeProcessTree() throws Exception {
        StringBuilder out = new StringBuilder();
        ProcessCommandExecutor executor = new ProcessCommandExecutor(out);
        // shell 内先启动后台任务（孙进程），前台命令阻塞直到超时；
        // 用独特参数 4321 避免与系统中其他 sleep 进程误判
        ExecutionResult result = executor.execute(new ExecutionRequest(
                "sleep 4321 & sleep 4322", Path.of("/tmp"),
                Duration.ofMillis(300), null));
        assertTrue(result.timedOut());
        // 等待 destroyProcessTree 清理完成（两轮各至多 500ms）
        Thread.sleep(1500);
        boolean orphanAlive = ProcessHandle.allProcesses().anyMatch(p -> {
            var info = p.info();
            return info.command().map(c -> c.endsWith("sleep")).orElse(false)
                    && info.arguments().map(args -> java.util.List.of(args).contains("4321")).orElse(false);
        });
        assertFalse(orphanAlive, "后台任务应随超时销毁一并清理，不能残留为孤儿进程");
    }
}
