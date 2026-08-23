package io.pragmatic.shell.execution;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * 命令执行请求。
 *
 * @param env 环境变量覆盖表（策略二 M3：export/unset 注入子进程），可为 null 表示继承 JVM 环境
 */
public record ExecutionRequest(String command, Path workDir, Duration timeout, Map<String, String> env) {
}
