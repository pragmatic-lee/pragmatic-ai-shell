package io.pragmatic.shell.config.model;

/**
 * 启动交互行为配置（v4 新增，替代 --mode 启动参数）。
 * 使用普通 POJO 以便 SnakeYAML 反序列化。
 */
public class ShellConfig {
    private String initialMode = "smart";
    /** 启动界面配置（FR-SPLASH）：默认开启线框 Splash。 */
    private SplashConfig splash = new SplashConfig();

    public ShellConfig() {
    }

    public String getInitialMode() {
        return initialMode;
    }

    public void setInitialMode(String initialMode) {
        this.initialMode = initialMode;
    }

    public SplashConfig getSplash() {
        return splash;
    }

    public void setSplash(SplashConfig splash) {
        this.splash = splash;
    }

    @Override
    public String toString() {
        return "ShellConfig{initialMode='" + initialMode + "', splash=" + splash + '}';
    }
}
