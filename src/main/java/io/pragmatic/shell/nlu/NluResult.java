package io.pragmatic.shell.nlu;

import io.pragmatic.shell.safety.CommandRisk;

/**
 * NLU 解析结果。
 * status: COMMAND 表示成功生成命令；UNSAFE / IMPOSSIBLE 表示被模型判定为不安全或不可行。
 */
public record NluResult(NluStatus status, String command) {

    public static NluResult command(String command) {
        return new NluResult(NluStatus.COMMAND, command);
    }

    public static NluResult unsafe() {
        return new NluResult(NluStatus.UNSAFE, null);
    }

    public static NluResult impossible() {
        return new NluResult(NluStatus.IMPOSSIBLE, null);
    }

    /** 用于「无上下文」场景下占位（当前未启用分级判定）。 */
    public CommandRisk risk() {
        return CommandRisk.READ;
    }
}
