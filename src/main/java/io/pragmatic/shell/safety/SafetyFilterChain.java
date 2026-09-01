package io.pragmatic.shell.safety;

import io.pragmatic.shell.config.AppConfig;

import java.util.List;

/**
 * 安全过滤责任链编排（FR-04）。
 * 顺序：Blacklist → Address → ConfirmationGate（含分级判定）。
 */
public final class SafetyFilterChain {

    private final List<SafetyFilter> filters;

    public SafetyFilterChain(AppConfig config) {
        this.filters = List.of(
                // sudo 策略（运维场景需提权），未配置时回退 reject（现状）
                new BlacklistFilter(config.getSafety().getSudoPolicy()),
                new AddressFilter(config.getSafety().isBlockPrivateAddresses()),
                new ConfirmationGate(config.getSafety().isConfirmDestructive(), config.getExecution().isReadOnly())
        );
    }

    public FilterVerdict evaluate(String command) {
        CommandRisk risk = RiskClassifier.classify(command);
        for (SafetyFilter f : filters) {
            FilterVerdict v = f.evaluate(command, risk);
            if (v.type() != FilterVerdict.VerdictType.PASS) {
                return v;
            }
        }
        return FilterVerdict.pass();
    }
}
