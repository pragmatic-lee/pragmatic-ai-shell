package io.pragmatic.shell.config.model;

/**
 * 单个 LLM 后端配置（多模型接入 FR-MLLM-01）。
 * 使用普通 POJO 以便 SnakeYAML 反序列化。字段与 {@link LlmConfig} 顶层同构，
 * 但携带会话内唯一 id，供启动激活、/model 切换与审计归属使用。
 */
public class LlmProfile {
    /** 会话内唯一标识（/model switch <id>、界面展示、审计 model 字段）。 */
    private String id;
    private String provider = "deepseek";
    private String baseUrl = "https://api.deepseek.com/v1";
    private String model = "deepseek-chat";
    private double temperature = 0.0;
    private String apiKey;
    private int timeoutSeconds = 30;

    public LlmProfile() {
    }

    public LlmProfile(String id, String provider, String baseUrl, String model,
                      double temperature, String apiKey, int timeoutSeconds) {
        this.id = id;
        this.provider = provider;
        this.baseUrl = baseUrl;
        this.model = model;
        this.temperature = temperature;
        this.apiKey = apiKey;
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    /** provider 归一（小写 trim），空安全。 */
    public String normalizedProvider() {
        return provider == null ? "" : provider.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * 可用性判定（FR-MLLM-01）：ollama 本地模型无需 apiKey，其余需 apiKey/baseUrl/model。
     * 与 {@code ConfigValidator} / {@code ModelRegistry} 的可用项过滤保持一致。
     */
    public boolean isUsable() {
        return !isBlank(baseUrl) && !isBlank(model)
                && ("ollama".equals(normalizedProvider()) || !isBlank(apiKey));
    }

    /** 不可用原因（可用时返回 null），用于界面标注与错误文案，不含密钥值（FR-13-06）。 */
    public String unusableReason() {
        if (isUsable()) {
            return null;
        }
        if (isBlank(baseUrl) || isBlank(model)) {
            return "未配置(baseUrl/model)";
        }
        return "未配置(缺apiKey)";
    }

    /** provider/model 展示标签（审计 model 字段、切换提示用）。 */
    public String displayLabel() {
        return normalizedProvider() + "/" + model;
    }

    /** apiKey 打码展示（sk-****c0a），未配置显示 (未配置)，避免明文泄露（FR-13-06）。 */
    public String maskedApiKey() {
        if (isBlank(apiKey)) {
            return "(未配置)";
        }
        if (apiKey.length() <= 6) {
            return "****";
        }
        return apiKey.substring(0, 3) + "****" + apiKey.substring(apiKey.length() - 3);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Override
    public String toString() {
        return "LlmProfile{id='" + id + "', provider='" + provider + "', baseUrl='" + baseUrl
                + "', model='" + model + "', temperature=" + temperature
                + ", apiKey='" + maskedApiKey() + "', timeoutSeconds=" + timeoutSeconds + '}';
    }
}
