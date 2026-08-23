package io.pragmatic.shell.audit;

/**
 * 审计日志条目（单行 JSON）。
 */
public record AuditEntry(
        String ts,        // ISO-8601
        String source,    // LLM / USER
        String input,
        String command,
        int exitCode,
        long durationMs
) {
}
