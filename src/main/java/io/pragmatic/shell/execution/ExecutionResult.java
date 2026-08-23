package io.pragmatic.shell.execution;

/**
 * 命令执行结果。
 */
public record ExecutionResult(int exitCode, String output, long durationMs, boolean timedOut) {

    public boolean success() {
        return exitCode == 0;
    }
}
