package io.pragmatic.shell.config.model;

import java.util.List;

/**
 * 环境指纹配置（环境感知 v5+）。
 * - enabled：默认开启；false 时不采集、不向 LLM 注入环境信息；
 * - toolWhitelist：探测工具清单，空列表使用内置默认清单；
 * - toolProbeTimeoutMs：单工具探测超时（毫秒）。
 * 使用普通 POJO 以便 SnakeYAML 反序列化。
 */
public class ProfileConfig {
    private boolean enabled = true;
    private List<String> toolWhitelist = defaultTools();
    private int toolProbeTimeoutMs = 200;

    public ProfileConfig() {
    }

    /** 内置默认探测清单（仅探测是否存在及版本，不做项目/构建系统识别）。 */
    public static List<String> defaultTools() {
        return List.of("git", "docker", "kubectl", "helm", "rg", "fd", "bat", "fzf",
                "curl", "wget", "jq", "python3", "node", "npm", "mvn", "go",
                "rustc", "ssh", "lsof", "ss");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getToolWhitelist() {
        return toolWhitelist;
    }

    public void setToolWhitelist(List<String> toolWhitelist) {
        this.toolWhitelist = toolWhitelist;
    }

    public int getToolProbeTimeoutMs() {
        return toolProbeTimeoutMs;
    }

    public void setToolProbeTimeoutMs(int toolProbeTimeoutMs) {
        this.toolProbeTimeoutMs = toolProbeTimeoutMs;
    }

    @Override
    public String toString() {
        return "ProfileConfig{enabled=" + enabled
                + ", toolProbeTimeoutMs=" + toolProbeTimeoutMs
                + ", toolWhitelist=" + (toolWhitelist.isEmpty() ? "default" : toolWhitelist) + '}';
    }
}
