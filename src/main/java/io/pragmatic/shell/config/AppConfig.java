package io.pragmatic.shell.config;

import io.pragmatic.shell.config.model.ExecutionConfig;
import io.pragmatic.shell.config.model.LlmConfig;
import io.pragmatic.shell.config.model.LoggingConfig;
import io.pragmatic.shell.config.model.SafetyConfig;

/**
 * 顶层应用配置（普通 POJO，便于 SnakeYAML 反序列化）。
 */
public class AppConfig {
    private int version = 1;
    private LlmConfig llm = new LlmConfig();
    private ExecutionConfig execution = new ExecutionConfig();
    private SafetyConfig safety = new SafetyConfig();
    private LoggingConfig logging = new LoggingConfig();

    public AppConfig() {
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public LlmConfig getLlm() {
        return llm;
    }

    public void setLlm(LlmConfig llm) {
        this.llm = llm;
    }

    public ExecutionConfig getExecution() {
        return execution;
    }

    public void setExecution(ExecutionConfig execution) {
        this.execution = execution;
    }

    public SafetyConfig getSafety() {
        return safety;
    }

    public void setSafety(SafetyConfig safety) {
        this.safety = safety;
    }

    public LoggingConfig getLogging() {
        return logging;
    }

    public void setLogging(LoggingConfig logging) {
        this.logging = logging;
    }

    /** 返回 readOnly 覆盖后的新配置（用于 --read-only 启动参数）。 */
    public AppConfig withReadOnly(boolean readOnly) {
        AppConfig copy = new AppConfig();
        copy.llm = this.llm;
        copy.execution = new ExecutionConfig();
        copy.execution.setDefaultTimeoutSeconds(this.execution.getDefaultTimeoutSeconds());
        copy.execution.setWorkDir(this.execution.getWorkDir());
        copy.execution.setReadOnly(readOnly);
        copy.safety = this.safety;
        copy.logging = this.logging;
        return copy;
    }

    /** 返回内置默认配置（provider=deepseek，各子配置取字段默认值）。 */
    public static AppConfig defaults() {
        return new AppConfig();
    }

    @Override
    public String toString() {
        return "AppConfig{" +
                "version=" + version +
                ", llm=" + llm +
                ", execution=" + execution +
                ", safety=" + safety +
                ", logging=" + logging +
                '}';
    }
}
