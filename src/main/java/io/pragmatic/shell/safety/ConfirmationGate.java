package io.pragmatic.shell.safety;

/**
 * 二次确认闸口（FR-04-03）。
 * 对 DESTRUCTIVE 级别且开启确认时，返回 CONFIRM。
 */
public final class ConfirmationGate implements SafetyFilter {

    private final boolean confirmDestructive;
    private final boolean readOnly;

    public ConfirmationGate(boolean confirmDestructive, boolean readOnly) {
        this.confirmDestructive = confirmDestructive;
        this.readOnly = readOnly;
    }

    @Override
    public FilterVerdict evaluate(String command, CommandRisk risk) {
        if (readOnly && risk != CommandRisk.READ) {
            return FilterVerdict.reject("只读模式下禁止执行非只读命令: " + command);
        }
        if (confirmDestructive && risk == CommandRisk.DESTRUCTIVE) {
            return FilterVerdict.confirm("该命令为破坏性操作，请确认是否执行: " + command);
        }
        return FilterVerdict.pass();
    }
}
