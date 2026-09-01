package io.pragmatic.shell.config.model;

/**
 * 安全模块配置（普通 POJO，便于 SnakeYAML 反序列化）。
 */
public class SafetyConfig {

    /** sudo 策略：reject=一律拒绝（现状）；confirm=强确认后放行；allow=放行。 */
    public static final String SUDO_REJECT = "reject";
    public static final String SUDO_CONFIRM = "confirm";
    public static final String SUDO_ALLOW = "allow";

    private boolean strictMode = false;
    private boolean confirmDestructive = true;
    private boolean blockPrivateAddresses = true;
    /**
     * sudo 前置命令的处理策略（运维场景：重启服务等常需提权，硬性拒绝会导致命令不可用）。
     * 默认 confirm（宽松优先）：提权命令确认后放行，安全底线仍在；
     * 需更严格时改 reject，需更宽松时改 allow（高危正则已同步覆盖 sudo 前缀）。
     */
    private String sudoPolicy = SUDO_CONFIRM;

    public SafetyConfig() {
    }

    public String getSudoPolicy() {
        return sudoPolicy;
    }

    public void setSudoPolicy(String sudoPolicy) {
        this.sudoPolicy = sudoPolicy;
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
