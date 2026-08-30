package io.pragmatic.shell.config.model;

/**
 * 执行引擎配置（普通 POJO，便于 SnakeYAML 反序列化）。
 */
public class ExecutionConfig {
    /** 命令执行超时（秒）：0 = 不限时（FR-UTO-01，挂死命令由 Ctrl+C 中断兜底），> 0 = 超时强杀。 */
    private int defaultTimeoutSeconds = 0;
    private String workDir = ".";
    private boolean readOnly = false;

    public ExecutionConfig() {
    }

    public int getDefaultTimeoutSeconds() {
        return defaultTimeoutSeconds;
    }

    public void setDefaultTimeoutSeconds(int defaultTimeoutSeconds) {
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
    }

    public String getWorkDir() {
        return workDir;
    }

    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }
}
