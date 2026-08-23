package io.pragmatic.shell.safety;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 命令安全分级判定（FR-08 安全分级）。
 * 仅做判定，不直接拦截；由 ConfirmationGate 依据分级触发二次确认。
 */
public final class RiskClassifier {

    private static final List<Pattern> DESTRUCTIVE = List.of(
            Pattern.compile("^\\s*rm\\b"),
            Pattern.compile("^\\s*kill\\b"),
            Pattern.compile("systemctl\\s+(restart|stop|disable)"),
            Pattern.compile("^\\s*shutdown\\b"),
            Pattern.compile("^\\s*reboot\\b")
    );

    private static final List<Pattern> WRITE = List.of(
            Pattern.compile("^\\s*cp\\b"),
            Pattern.compile("^\\s*mv\\b"),
            Pattern.compile("^\\s*mkdir\\b"),
            Pattern.compile("^\\s*touch\\b"),
            Pattern.compile("^\\s*chmod\\b"),
            Pattern.compile("^\\s*chown\\b")
    );

    public static CommandRisk classify(String command) {
        String c = command.trim();
        for (Pattern p : DESTRUCTIVE) {
            if (p.matcher(c).find()) {
                return CommandRisk.DESTRUCTIVE;
            }
        }
        for (Pattern p : WRITE) {
            if (p.matcher(c).find()) {
                return CommandRisk.WRITE;
            }
        }
        return CommandRisk.READ;
    }
}
