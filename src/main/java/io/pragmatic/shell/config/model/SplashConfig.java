package io.pragmatic.shell.config.model;

/**
 * 启动界面配置（FR-SPLASH）。
 * 使用普通 POJO 以便 SnakeYAML 反序列化。
 * v1.1 决议取消启动界面模型选择交互，故仅保留总开关（无 modelSelection）。
 */
public class SplashConfig {
    /** 启动界面总开关：false 时跳过线框界面，仅保留两行纯文本欢迎语。 */
    private boolean enabled = true;

    public SplashConfig() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String toString() {
        return "SplashConfig{enabled=" + enabled + '}';
    }
}
