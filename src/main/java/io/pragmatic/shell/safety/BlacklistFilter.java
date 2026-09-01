package io.pragmatic.shell.safety;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 高危命令黑名单过滤（FR-04-01）。
 * 命中即 REJECT（sudoPolicy=reject/allow 时）或 CONFIRM（sudoPolicy=confirm 时，仅 sudo 项）并审计（由调用方记录）。
 *
 * <p>sudo 策略（运维场景）：重启服务等常需提权，硬性拒绝会导致命令不可用，
 * 故提供 reject（默认，现状）/ confirm（强确认后放行）/ allow（放行）三档。
 *
 * <p><b>安全说明</b>：放开 sudo 后，本过滤器中原本以行首锚定（{@code ^\s*rm}）的高危模式
 * 会被 {@code sudo rm -rf /} 绕过。因此高危模式统一加上可选的命令前缀
 * （sudo / env / nohup / command / sudo env），确保提权情形下依然被拦截。
 */
public final class BlacklistFilter implements SafetyFilter {

    /**
     * 可选的命令包装前缀（允许重复出现，如 sudo env）。
     * 放开 sudo 后，高危模式必须容忍这些前缀，否则会被绕过。
     */
    private static final String PREFIX = "(?:\\s*(?:sudo|env|nohup|command)\\b(?:\\s+[A-Za-z_][A-Za-z0-9_]*=(?:\\S*|\"[^\"]*\"|'[^']*'))*\\s+)*";

    private static final List<Pattern> CRITICAL_PATTERNS = List.of(
            // rm -rf / / rm -fr /：容忍 sudo 等前缀，避免提权绕过
            Pattern.compile("^\\s*" + PREFIX + "rm\\s+(-[a-zA-Z]*r[a-zA-Z]*f[a-zA-Z]*|-[a-zA-Z]*f[a-zA-Z]*r[a-zA-Z]*)\\s+/\\s*$"),
            Pattern.compile("^\\s*" + PREFIX + "rm\\s+-rf\\s+/"),
            Pattern.compile("^\\s*" + PREFIX + "rm\\s+-rf\\s+/\\s*$"),
            // 磁盘/文件系统破坏类：无行首锚定，本身不受前缀影响
            Pattern.compile("dd\\s+if="),
            Pattern.compile("mkfs"),
            Pattern.compile(":\\(\\)\\s*\\{\\s*:\\|:&\\s*\\};:")   // fork bomb
    );

    /** sudo 前置命令：策略由配置决定处理方式。 */
    private static final Pattern SUDO = Pattern.compile("^\\s*sudo\\s+");

    private final String sudoPolicy;

    public BlacklistFilter() {
        this(SafetyConfigDefaults.SUDO_CONFIRM);
    }

    public BlacklistFilter(String sudoPolicy) {
        this.sudoPolicy = sudoPolicy == null
                ? SafetyConfigDefaults.SUDO_CONFIRM
                : sudoPolicy.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public FilterVerdict evaluate(String command, CommandRisk risk) {
        if (command == null) {
            return FilterVerdict.pass();
        }
        for (Pattern p : CRITICAL_PATTERNS) {
            if (p.matcher(command).find()) {
                return FilterVerdict.reject("命令命中高危黑名单，已拒绝执行: " + command);
            }
        }
        if (SUDO.matcher(command).find()) {
            return switch (sudoPolicy) {
                case SafetyConfigDefaults.SUDO_ALLOW -> FilterVerdict.pass();
                case SafetyConfigDefaults.SUDO_CONFIRM ->
                        FilterVerdict.confirm("该命令需要提权（sudo）执行，请确认: " + command);
                default -> FilterVerdict.reject("以 sudo 开头的命令已被安全策略拒绝: " + command);
            };
        }
        return FilterVerdict.pass();
    }

    /** 常量内嵌，避免 safety 过滤器反向依赖 config.model 包。 */
    private static final class SafetyConfigDefaults {
        static final String SUDO_REJECT = "reject";
        static final String SUDO_CONFIRM = "confirm";
        static final String SUDO_ALLOW = "allow";
    }
}
