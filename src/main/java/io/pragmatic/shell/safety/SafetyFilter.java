package io.pragmatic.shell.safety;

/**
 * 安全过滤器接口。
 */
public interface SafetyFilter {
    FilterVerdict evaluate(String command, CommandRisk risk);
}
