package io.pragmatic.shell.execution;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 命令执行请求。
 */
public record ExecutionRequest(String command, Path workDir, Duration timeout) {
}
