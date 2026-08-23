package io.pragmatic.shell.config.model;

/**
 * 日志 / 审计配置（普通 POJO，便于 SnakeYAML 反序列化）。
 */
public class LoggingConfig {
    private boolean auditEnabled = true;
    private String auditPath = System.getProperty("user.home") + "/.smartcli/audit.log";

    public LoggingConfig() {
    }

    public boolean isAuditEnabled() {
        return auditEnabled;
    }

    public void setAuditEnabled(boolean auditEnabled) {
        this.auditEnabled = auditEnabled;
    }

    public String getAuditPath() {
        return auditPath;
    }

    public void setAuditPath(String auditPath) {
        this.auditPath = auditPath;
    }
}
