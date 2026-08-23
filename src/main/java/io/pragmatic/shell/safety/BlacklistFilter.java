package io.pragmatic.shell.safety;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 高危命令黑名单过滤（FR-04-01）。
 * 命中即 REJECT 并审计（由调用方记录）。
 */
public final class BlacklistFilter implements SafetyFilter {

    private static final List<Pattern> CRITICAL_PATTERNS = List.of(
            Pattern.compile("^\\s*rm\\s+(-[a-zA-Z]*r[a-zA-Z]*f[a-zA-Z]*|-[a-zA-Z]*f[a-zA-Z]*r[a-zA-Z]*)\\s+/\\s*$"),
            Pattern.compile("^\\s*rm\\s+-rf\\s+/"),
            Pattern.compile("dd\\s+if="),
            Pattern.compile("mkfs"),
            Pattern.compile(":\\(\\)\\s*\\{\\s*:\\|:&\\s*\\};:"),   // fork bomb
            Pattern.compile("^\\s*sudo\\s+")
    );

    @Override
    public FilterVerdict evaluate(String command, CommandRisk risk) {
        for (Pattern p : CRITICAL_PATTERNS) {
            if (p.matcher(command).find()) {
                return FilterVerdict.reject("命令命中高危黑名单，已拒绝执行: " + command);
            }
        }
        return FilterVerdict.pass();
    }
}
