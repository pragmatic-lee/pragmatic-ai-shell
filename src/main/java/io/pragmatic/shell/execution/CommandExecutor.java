package io.pragmatic.shell.execution;

/**
 * 命令执行器接口，便于测试时 Mock。
 */
public interface CommandExecutor {
    ExecutionResult execute(ExecutionRequest req);

    /** 中断当前正在执行的命令（Ctrl+C 入口，FR-UTO-02）；无命令在执行时无操作。 */
    void interruptCurrent();
}
