package io.pragmatic.shell.interaction;

import java.util.List;
import java.util.Map;

/**
 * 命令补全规格（数据驱动，设计文档 G6）：
 * 覆盖高频命令的子命令与选项候选。新增命令只需在 SPECS 加一行，
 * 无需改动分派逻辑。零外部依赖、native 友好（编译期静态数据）。
 */
record CommandSpec(List<String> subcommands, List<String> options) {
    /** 未收录命令的空规格。 */
    static final CommandSpec NONE = new CommandSpec(List.of(), List.of());
}

/** 高频命令补全规格表（内置静态数据，无外部依赖）。 */
public final class CommandSpecs {

    private static final Map<String, CommandSpec> SPECS = Map.of(
            "git", new CommandSpec(
                    List.of("add", "branch", "checkout", "commit", "diff", "log", "merge",
                            "pull", "push", "rebase", "reset", "restore", "stash", "status", "switch", "tag"),
                    List.of("--amend", "--branch", "--force", "--global", "--help", "--message", "--verbose")),
            "docker", new CommandSpec(
                    List.of("build", "compose", "exec", "images", "logs", "ps", "rm", "rmi", "run", "stop"),
                    List.of("--all", "--detach", "--filter", "--interactive", "--name", "--rm", "--tty", "--volume")),
            "kubectl", new CommandSpec(
                    List.of("apply", "delete", "describe", "get", "logs", "exec", "port-forward"),
                    List.of("--all-namespaces", "--namespace", "--output", "--selector")),
            "npm", new CommandSpec(
                    List.of("install", "run", "test", "build", "start", "ci", "audit"),
                    List.of()),
            "mvn", new CommandSpec(
                    List.of("clean", "compile", "test", "package", "install", "verify"),
                    List.of("-D", "-q", "-o", "-B", "-f")),
            "systemctl", new CommandSpec(
                    List.of("start", "stop", "restart", "status", "enable", "disable"),
                    List.of()),
            "brew", new CommandSpec(
                    List.of("install", "uninstall", "update", "upgrade", "list", "search"),
                    List.of())
    );

    private CommandSpecs() {
    }

    /** 按命令名（首 token，不含前导 /）查规格；未收录返回 NONE。 */
    public static CommandSpec forCommand(String name) {
        if (name == null) {
            return CommandSpec.NONE;
        }
        return SPECS.getOrDefault(name.toLowerCase(), CommandSpec.NONE);
    }
}
