package io.pragmatic.shell.config.model;

import java.util.List;

/**
 * LLM 后端配置。
 * apiKey 直接保存明文密钥（由 config.yaml 的 llm.apiKey 配置），不再通过环境变量读取。
 * 使用普通 POJO 以便 SnakeYAML 反序列化（record 无无参构造，SnakeYAML 默认不支持）。
 *
 * <p>多模型接入（FR-MLLM-01）：{@link #profiles} 声明多个模型，{@link #defaultProfile} 指定缺省激活项；
 * 顶层 provider/baseUrl/model/temperature/apiKey/timeoutSeconds 作为**单模型旧配置**保留（FR-MLLM-02），
 * 当 profiles 为空时由 {@link #resolvedProfiles()} 自动合成一个 id 为 {@code (inline)} 的隐式 Profile，
 * 存量 config.yaml 零迁移成本。context/profile 保持 llm 级全局共享（本期不下沉到 Profile）。
 */
public class LlmConfig {
    /** 隐式合成 Profile 的 id（v1.1 决议 8：用括号前缀区别于用户显式声明）。 */
    public static final String INLINE_PROFILE_ID = "(inline)";

    private String provider = "deepseek";
    private String baseUrl = "https://api.deepseek.com/v1";
    private String model = "deepseek-chat";
    private double temperature = 0.0;
    private String apiKey;
    private int timeoutSeconds = 30;
    private boolean showProgress = true;
    /** 缺省激活的 Profile id（启动直接进入 REPL 所用；非交互环境亦采用值）。 */
    private String defaultProfile;
    /** 多模型列表（FR-MLLM-01）；为空时回退到旧版单 llm 节点合成。 */
    private List<LlmProfile> profiles;
    /** 多轮对话上下文配置（FR-CTX-07）：默认开启，保留最近 maxTurns 轮，单轮结果摘要上限 maxResultChars 字符。 */
    private ContextConfig context = new ContextConfig();
    /** 环境指纹配置（环境感知）：默认开启，采集 OS/Shell/已装工具并注入 LLM。 */
    private ProfileConfig profile = new ProfileConfig();

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

    public String getDefaultProfile() {
        return defaultProfile;
    }

    public void setDefaultProfile(String defaultProfile) {
        this.defaultProfile = defaultProfile;
    }

    public List<LlmProfile> getProfiles() {
        return profiles;
    }

    public void setProfiles(List<LlmProfile> profiles) {
        this.profiles = profiles;
    }

    /** 是否声明了显式多模型列表。 */
    public boolean hasExplicitProfiles() {
        return profiles != null && !profiles.isEmpty();
    }

    /**
     * 生效的 Profile 列表（FR-MLLM-02 向后兼容核心）：
     * 有 profiles 则原样返回；否则用顶层旧单 llm 字段合成一个 id={(inline)} 的隐式 Profile。
     */
    public List<LlmProfile> resolvedProfiles() {
        if (hasExplicitProfiles()) {
            return profiles;
        }
        LlmProfile inline = new LlmProfile(INLINE_PROFILE_ID, provider, baseUrl, model,
                temperature, apiKey, timeoutSeconds);
        return List.of(inline);
    }

    /** 按 id 查找生效 Profile（找不到返回 null）。 */
    public LlmProfile findProfile(String id) {
        if (id == null) {
            return null;
        }
        for (LlmProfile p : resolvedProfiles()) {
            if (id.equals(p.getId())) {
                return p;
            }
        }
        return null;
    }

    public ContextConfig getContext() {
        return context;
    }

    public void setContext(ContextConfig context) {
        this.context = context;
    }

    public ProfileConfig getProfile() {
        return profile;
    }

    public void setProfile(ProfileConfig profile) {
        this.profile = profile;
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

    /** /config 展示用：与 toString 一致但 apiKey 打码。多模型时附各 Profile 概要。 */
    public String maskedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("LlmConfig{defaultProfile='").append(defaultProfile).append("'");
        List<LlmProfile> list = resolvedProfiles();
        sb.append(", profiles=[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(list.get(i));
        }
        sb.append("], showProgress=").append(showProgress)
                .append(", context=").append(context)
                .append(", profile=").append(profile).append('}');
        return sb.toString();
    }
}
