package io.pragmatic.shell.config.model;

/**
 * LLM 后端配置。
 * apiKey 直接保存明文密钥（由 config.yaml 的 llm.apiKey 配置），不再通过环境变量读取。
 * 使用普通 POJO 以便 SnakeYAML 反序列化（record 无无参构造，SnakeYAML 默认不支持）。
 */
public class LlmConfig {
    private String provider = "deepseek";
    private String baseUrl = "https://api.deepseek.com/v1";
    private String model = "deepseek-chat";
    private double temperature = 0.0;
    private String apiKey;
    private int timeoutSeconds = 30;
    private boolean showProgress = true;

    public LlmConfig() {
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isShowProgress() {
        return showProgress;
    }

    public void setShowProgress(boolean showProgress) {
        this.showProgress = showProgress;
    }

    /** apiKey 打码展示（如 sk-****c0a），未配置显示 (未配置)，避免明文泄露（FR-13-06）。 */
    public String maskedApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            return "(未配置)";
        }
        if (apiKey.length() <= 6) {
            return "****";
        }
        return apiKey.substring(0, 3) + "****" + apiKey.substring(apiKey.length() - 3);
    }

    /** /config 展示用：与 toString 一致但 apiKey 打码。 */
    public String maskedString() {
        return "LlmConfig{provider='" + provider + "', baseUrl='" + baseUrl + "', model='" + model
                + "', temperature=" + temperature + ", apiKey='" + maskedApiKey()
                + "', timeoutSeconds=" + timeoutSeconds + ", showProgress=" + showProgress + '}';
    }
}
