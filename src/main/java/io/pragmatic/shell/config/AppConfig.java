package io.pragmatic.shell.config;

import io.pragmatic.shell.config.model.ExecutionConfig;
import io.pragmatic.shell.config.model.LlmConfig;
import io.pragmatic.shell.config.model.LoggingConfig;
import io.pragmatic.shell.config.model.NluConfig;
import io.pragmatic.shell.config.model.SafetyConfig;
import io.pragmatic.shell.config.model.ShellConfig;

/**
 * 顶层应用配置（普通 POJO，便于 SnakeYAML 反序列化）。
 */
public class AppConfig {
    private int version = 1;
    private ShellConfig shell = new ShellConfig();
    private LlmConfig llm = new LlmConfig();
    private ExecutionConfig execution = new ExecutionConfig();
    private SafetyConfig safety = new SafetyConfig();
    private NluConfig nlu = new NluConfig();
    private LoggingConfig logging = new LoggingConfig();

    public AppConfig() {
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public ShellConfig getShell() {
        return shell;
    }

    public void setShell(ShellConfig shell) {
        this.shell = shell;
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

    public NluConfig getNlu() {
        return nlu;
    }

    public void setNlu(NluConfig nlu) {
        this.nlu = nlu;
    }

    public LoggingConfig getLogging() {
        return logging;
    }

    public void setLogging(LoggingConfig logging) {
        this.logging = logging;
    }

    /** 返回 readOnly 覆盖后的新配置（仅测试/内部使用；v4 起只读由 execution.readOnly 配置驱动）。 */
    public AppConfig withReadOnly(boolean readOnly) {
        AppConfig copy = new AppConfig();
        copy.shell = this.shell;
        copy.llm = this.llm;
        copy.execution = new ExecutionConfig();
        copy.execution.setDefaultTimeoutSeconds(this.execution.getDefaultTimeoutSeconds());
        copy.execution.setWorkDir(this.execution.getWorkDir());
        copy.execution.setReadOnly(readOnly);
        copy.safety = this.safety;
        copy.nlu = this.nlu;
        copy.logging = this.logging;
        return copy;
    }

    /** 返回内置默认配置（provider=deepseek，各子配置取字段默认值）。 */
    public static AppConfig defaults() {
        return new AppConfig();
    }

    /** /config 展示用：apiKey 打码，避免明文输出（FR-13-06）。 */
    public String toDisplayString() {
        return "AppConfig{version=" + version +
                ", shell=" + shell +
                ", llm=" + llm.maskedString() +
                ", execution=" + execution +
                ", safety=" + safety +
                ", nlu=" + nlu +
                ", logging=" + logging +
                '}';
    }

    @Override
    public String toString() {
        return "AppConfig{" +
                "version=" + version +
                ", llm=" + llm +
                ", execution=" + execution +
                ", safety=" + safety +
                ", nlu=" + nlu +
                ", logging=" + logging +
                '}';
    }
}
