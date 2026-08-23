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
