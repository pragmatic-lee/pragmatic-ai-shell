package io.pragmatic.shell.execution;

/**
 * 命令执行器接口，便于测试时 Mock。
 */
public interface CommandExecutor {
    ExecutionResult execute(ExecutionRequest req);
}
