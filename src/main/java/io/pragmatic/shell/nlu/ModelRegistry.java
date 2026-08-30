package io.pragmatic.shell.nlu;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.pragmatic.shell.config.AppConfig;
import io.pragmatic.shell.config.model.LlmProfile;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多模型注册中心（FR-MLLM-03）。
 * - 持有生效的 Profile 列表（含旧版单 llm 合成的 (inline) 项）与当前激活项；
 * - 客户端懒加载：仅在首次用到某 Profile 时构建 ChatLanguageModel 并缓存，切换不重复建连（NFR-05）；
 * - 提供切换（仅会话内生效，不回写 config.yaml）、可用项查询与按需健康检查。
 */
public final class ModelRegistry {

    private final List<LlmProfile> profiles;
    private final String defaultProfileId;
    private final Map<String, ChatLanguageModel> cache = new ConcurrentHashMap<>();
    private volatile LlmProfile active;

    public ModelRegistry(AppConfig config) {
        this.profiles = new ArrayList<>(config.getLlm().resolvedProfiles());
        this.defaultProfileId = config.getLlm().getDefaultProfile();
        this.active = resolveInitialActive();
    }

    /** 初始激活项：defaultProfile（存在且可用）> 首个可用 > 首项（可能不可用，交由降级逻辑处理）。 */
    private LlmProfile resolveInitialActive() {
        if (defaultProfileId != null && !defaultProfileId.isBlank()) {
            LlmProfile p = find(defaultProfileId);
            if (p != null && p.isUsable()) {
                return p;
            }
        }
        for (LlmProfile p : profiles) {
            if (p.isUsable()) {
                return p;
            }
        }
        return profiles.isEmpty() ? null : profiles.get(0);
    }

    public List<LlmProfile> profiles() {
        return profiles;
    }

    /** 是否为多模型（>1，决定 Splash 模型区展示形态，FR-SPLASH-03）。 */
    public boolean isMulti() {
        return profiles.size() > 1;
    }

    public LlmProfile activeProfile() {
        return active;
    }

    public LlmProfile find(String id) {
        if (id == null) {
            return null;
        }
        for (LlmProfile p : profiles) {
            if (id.equals(p.getId())) {
                return p;
            }
        }
        return null;
    }

    /** 全部可用 Profile（保持声明顺序）。 */
    public List<LlmProfile> usableProfiles() {
        List<LlmProfile> list = new ArrayList<>();
        for (LlmProfile p : profiles) {
            if (p.isUsable()) {
                list.add(p);
            }
        }
        return list;
    }

    /** 除当前激活项外的其他可用 Profile（降级前被动提醒用，FR-MLLM-06）。 */
    public List<LlmProfile> otherUsableProfiles() {
        List<LlmProfile> list = new ArrayList<>();
        for (LlmProfile p : profiles) {
            if (p.isUsable() && p != active) {
                list.add(p);
            }
        }
        return list;
    }

    /**
     * 切换到指定 id 的 Profile（FR-MLLM-05）。
     * @return true=切换成功；false=id 不存在或不可用
     */
    public boolean switchTo(String id) {
        LlmProfile p = find(id);
        if (p == null || !p.isUsable()) {
            return false;
        }
        this.active = p;
        return true;
    }

    /** 当前激活 Profile 对应的客户端（懒构建 + 缓存）。 */
    public ChatLanguageModel currentModel() {
        return modelFor(active);
    }

    public ChatLanguageModel modelFor(LlmProfile profile) {
        return cache.computeIfAbsent(profile.getId(), id -> buildModel(profile));
    }

    /** 按 provider 选择后端（从 LangChainNluService 抽出，行为不变）。 */
    static ChatLanguageModel buildModel(LlmProfile llm) {
        Duration timeout = Duration.ofSeconds(llm.getTimeoutSeconds());
        return switch (LlmProvider.valueOf(llm.normalizedProvider().toUpperCase())) {
            case OLLAMA -> OllamaChatModel.builder()
                    .baseUrl(llm.getBaseUrl())
                    .modelName(llm.getModel())
                    .temperature(llm.getTemperature())
                    .timeout(timeout)
                    .build();
            default -> OpenAiChatModel.builder()
                    .baseUrl(llm.getBaseUrl())
                    .apiKey(llm.getApiKey())
                    .modelName(llm.getModel())
                    .temperature(llm.getTemperature())
                    .timeout(timeout)
                    .build();
        };
    }

    /**
     * 健康检查（FR-MLLM-06）：发送固定探针并计时。不引入后台周期任务，仅按需触发。
     * @return ok=true 表示可用；否则 error 为失败原因（不含密钥，NFR-04）
     */
    public CheckResult check(LlmProfile profile) {
        long start = System.currentTimeMillis();
        try {
            modelFor(profile).generate("ping");
            return new CheckResult(true, System.currentTimeMillis() - start, null);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return new CheckResult(false, System.currentTimeMillis() - start, msg);
        }
    }

    public record CheckResult(boolean ok, long durationMs, String error) {
    }
}
