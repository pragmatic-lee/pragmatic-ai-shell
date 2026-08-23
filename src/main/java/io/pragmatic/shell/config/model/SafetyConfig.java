package io.pragmatic.shell.config.model;

/**
 * 安全模块配置（普通 POJO，便于 SnakeYAML 反序列化）。
 */
public class SafetyConfig {
    private boolean strictMode = false;
    private boolean confirmDestructive = true;
    private boolean blockPrivateAddresses = true;

    public SafetyConfig() {
    }

    public boolean isStrictMode() {
        return strictMode;
    }

    public void setStrictMode(boolean strictMode) {
        this.strictMode = strictMode;
    }

    public boolean isConfirmDestructive() {
        return confirmDestructive;
    }

    public void setConfirmDestructive(boolean confirmDestructive) {
        this.confirmDestructive = confirmDestructive;
    }

    public boolean isBlockPrivateAddresses() {
        return blockPrivateAddresses;
    }

    public void setBlockPrivateAddresses(boolean blockPrivateAddresses) {
        this.blockPrivateAddresses = blockPrivateAddresses;
    }
}
