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

    @Option(names = "--config", description = "配置文件路径（未指定时优先当前目录 config.yaml，否则取/生成于 ~/.smartcli/config.yaml）")
    String configPath;

    @Override
    public Integer call() {
        ConfigLoader.LoadResult loaded;
        try {
            // 未通过 --config 显式指定时，缺失即自动生成默认配置（FR-ZERO-01，首次启动友好）
            loaded = ConfigLoader.load(configPath, configPath == null);
        } catch (ConfigLoadException e) {
            System.err.println(e.getMessage());
            return 1;
        }
        if (loaded.generated()) {
            System.out.println("已为你生成默认配置文件: " + loaded.configPath());
            System.out.println("（尚未配置大模型，将以直通模式启动；进入后输入 /setup 可引导配置）");
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
            new SmartCliShell(loaded.config(), initial, loaded.configPath()).start();
        } catch (Exception e) {
            System.err.println("启动失败: " + e.getMessage());
            return 1;
        }
        return 0;
    }

    /** 从 shell.initialMode 解析初始模式：显式 smart 才进语义，缺省/非法值回退直通（FR-14 调整）。
     *  默认直通：零配置首次启动即直通，用户按需 /mode smart 切换。 */
    private static ShellMode resolveInitialMode(AppConfig config) {
        String mode = config.getShell() != null ? config.getShell().getInitialMode() : null;
        return "smart".equalsIgnoreCase(mode) ? ShellMode.SMART : ShellMode.DIRECT;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new SmartCliApplication()).execute(args);
        System.exit(exitCode);
    }
}
