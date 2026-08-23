package io.pragmatic.shell.safety;

/**
 * 安全过滤裁决。
 * PASS：放行；CONFIRM：需用户二次确认；REJECT：直接拒绝。
 */
public record FilterVerdict(VerdictType type, String message) {

    public static FilterVerdict pass() {
        return new FilterVerdict(VerdictType.PASS, null);
    }

    public static FilterVerdict confirm(String message) {
        return new FilterVerdict(VerdictType.CONFIRM, message);
    }

    public static FilterVerdict reject(String message) {
        return new FilterVerdict(VerdictType.REJECT, message);
    }

    public enum VerdictType {
        PASS, CONFIRM, REJECT
    }
}
