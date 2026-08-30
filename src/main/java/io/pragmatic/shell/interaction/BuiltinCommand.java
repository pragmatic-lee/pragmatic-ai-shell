package io.pragmatic.shell.interaction;

import java.util.Arrays;
import java.util.List;

/**
 * 内置命令单一数据源（设计文档 G2/G6）：
 * 同时供补全（命令名 + 参数候选）与 SmartCliShell.builtin() 分发使用，
 * 新增命令只需在此加一行枚举，根治硬编码常量表与 switch 两处维护导致的遗漏
 * （如 /setup 未同步进入补全表）。
 *
 * <p>name 为小写命令名（不含前导 /）；args 为该命令的参数候选，
 * 空列表表示无参数级补全。
 */
public enum BuiltinCommand {
    HELP("help", List.of()),
    EXIT("exit", List.of()),
    QUIT("quit", List.of()),
    MODE("mode", List.of("smart", "direct")),
    CONFIG("config", List.of()),
    MODEL("model", List.of("switch", "check")),
    CONTEXT("context", List.of()),
    CLEAR("clear", List.of()),
    PROFILE("profile", List.of("refresh")),
    SETUP("setup", List.of());

    private final String name;
    private final List<String> args;

    BuiltinCommand(String name, List<String> args) {
        this.name = name;
        this.args = args;
    }

    /** 小写命令名（不含前导 /）。 */
    public String commandName() {
        return name;
    }

    /** 该命令的参数候选；空表示无参数级补全。 */
    public List<String> args() {
        return args;
    }

    /** 补全用全名：带前导 /。 */
    public String fullName() {
        return "/" + name;
    }

    /** 按小写名（不含前导 /）查枚举，忽略大小写；未命中返回 null。 */
    public static BuiltinCommand byName(String lowerName) {
        if (lowerName == null) {
            return null;
        }
        for (BuiltinCommand c : values()) {
            if (c.name.equals(lowerName.toLowerCase())) {
                return c;
            }
        }
        return null;
    }

    /** 所有内置命令全名（/xxx），供首个 token 命令名补全。 */
    public static List<String> allFullNames() {
        return Arrays.stream(values()).map(BuiltinCommand::fullName).toList();
    }
}
