package io.pragmatic.shell.audit;

/**
 * 审计日志接口。
 */
public interface AuditLogger {
    void log(AuditEntry entry);
}
