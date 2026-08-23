package io.pragmatic.shell;

import io.pragmatic.shell.config.AppConfig;
import io.pragmatic.shell.config.ConfigLoadException;
import io.pragmatic.shell.config.ConfigLoader;
import io.pragmatic.shell.config.ConfigValidator;
import io.pragmatic.shell.interaction.ShellMode;
import io.pragmatic.shell.interaction.SmartCliShell;
import picocli.CommandLine;

import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * 启动入口（v4：配置中心化与强制加载，FR-12/13/14）。
 * 命令行参数仅保留 --config（+ 标准 help/version）；--mode、--read-only 已收编入 config.yaml
 * （shell.initialMode / execution.readOnly）。
 * 启动流程：强制加载配置 → 校验（非法值报错退出；语义必填项缺失警告并降级直通）→ 进入 REPL。
 */
@Command(name = "smartcli", mixinStandardHelpOptions = true,
        version = "smartcli 1.0", description = "基于 LLM 的智能命令行工具（单命令模式）")
public final class SmartCliApplication implements Callable<Integer> {

    @Option(names = "--config", description = "配置文件路径（默认当前目录 config.yaml）")
    String configPath;

    @Override
    public Integer call() {
        ConfigLoader.LoadResult loaded;
        try {
            loaded = ConfigLoader.load(configPath);
        } catch (ConfigLoadException e) {
            System.err.println(e.getMessage());
            return 1;
        }
        ConfigValidator.ValidationResult validation = ConfigValidator.validate(loaded);
        for (String error : validation.errors()) {
            System.err.println(error);
        }
        if (!validation.errors().isEmpty()) {
            return 1;
        }
        for (String warning : validation.warnings()) {
            System.err.println(warning);
        }
        System.out.println("已加载配置: " + loaded.configPath());
        ShellMode initial = validation.degradeToDirect()
                ? ShellMode.DIRECT
                : resolveInitialMode(loaded.config());
        try {
            new SmartCliShell(loaded.config(), initial).start();
        } catch (Exception e) {
            System.err.println("启动失败: " + e.getMessage());
            return 1;
        }
        return 0;
    }

    /** 从 shell.initialMode 解析初始模式（缺省/非法值回退语义模式，FR-14）。 */
    private static ShellMode resolveInitialMode(AppConfig config) {
        String mode = config.getShell() != null ? config.getShell().getInitialMode() : null;
        return "direct".equalsIgnoreCase(mode) ? ShellMode.DIRECT : ShellMode.SMART;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new SmartCliApplication()).execute(args);
        System.exit(exitCode);
    }
}
