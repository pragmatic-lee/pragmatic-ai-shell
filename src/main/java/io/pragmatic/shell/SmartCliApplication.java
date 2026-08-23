package io.pragmatic.shell;

import io.pragmatic.shell.config.AppConfig;
import io.pragmatic.shell.config.ConfigLoader;
import io.pragmatic.shell.interaction.ShellMode;
import io.pragmatic.shell.interaction.SmartCliShell;
import picocli.CommandLine;

import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * 启动入口（FR-09 运行参数）。
 */
@Command(name = "smartcli", mixinStandardHelpOptions = true,
        version = "smartcli 1.0", description = "基于 LLM 的智能命令行工具（单命令模式）")
public final class SmartCliApplication implements Callable<Integer> {

    @Option(names = "--config", description = "配置文件路径（config.yaml）")
    String configPath;

    @Option(names = "--read-only", description = "仅允许执行只读命令")
    boolean readOnly;

    @Option(names = "--mode", description = "启动模式: smart | direct")
    String mode;

    @Override
    public Integer call() throws Exception {
        AppConfig config = ConfigLoader.load(configPath);
        if (readOnly) {
            config = config.withReadOnly(true);
        }
        ShellMode initial = "direct".equalsIgnoreCase(mode) ? ShellMode.DIRECT : ShellMode.SMART;
        SmartCliShell shell = new SmartCliShell(config, initial);
        shell.start();
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new SmartCliApplication()).execute(args);
        System.exit(exitCode);
    }
}
