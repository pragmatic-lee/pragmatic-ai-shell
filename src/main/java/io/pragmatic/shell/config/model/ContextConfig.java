package io.pragmatic.shell.config.model;

/**
 * 语义模式多轮对话上下文配置（FR-CTX-07）。
 * - enabled：默认开启；false 时每次 LLM 调用独立，恢复 v2 无上下文行为；
 * - maxTurns：上下文保留的最近轮数；
 * - maxResultChars：单轮命令执行结果摘要上限（字符），超过截取尾部。
 * 使用普通 POJO 以便 SnakeYAML 反序列化。
 */
public class ContextConfig {
    private boolean enabled = true;
    private int maxTurns = 10;
    private int maxResultChars = 2000;

    public ContextConfig() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxTurns() {
        return maxTurns;
    }

    public void setMaxTurns(int maxTurns) {
        this.maxTurns = maxTurns;
    }

    public int getMaxResultChars() {
        return maxResultChars;
    }

    public void setMaxResultChars(int maxResultChars) {
        this.maxResultChars = maxResultChars;
    }

    @Override
    public String toString() {
        return "ContextConfig{enabled=" + enabled
                + ", maxTurns=" + maxTurns
                + ", maxResultChars=" + maxResultChars + '}';
    }
}
