package io.pragmatic.shell.config;

import io.pragmatic.shell.config.model.LlmConfig;
import io.pragmatic.shell.config.model.LlmProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 启动时配置校验（v4，FR-13）。
 * - errors：非法值（provider / temperature / 超时等），致命，启动失败退出；
 * - warnings：未知字段、版本不匹配、语义必填项缺失（缺失时置 degradeToDirect，
 *   由启动入口以直通模式进入，不阻断启动）；
 * - 所有错误/警告文案统一 "[配置错误|警告] <文件路径> → <字段路径>: ..." 格式，
 *   且不包含 apiKey 等敏感字段值（FR-13-05/06）。
 */
public final class ConfigValidator {

    private static final Set<String> PROVIDERS = Set.of("deepseek", "openai", "ollama");
    private static final int SUPPORTED_VERSION = 1;

    private ConfigValidator() {
    }

    public record ValidationResult(List<String> errors, List<String> warnings, boolean degradeToDirect) {
    }

    public static ValidationResult validate(ConfigLoader.LoadResult loaded) {
        AppConfig config = loaded.config();
        String file = String.valueOf(loaded.configPath());
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 版本兼容提示（FR-15-03）：仅提示不阻断
        if (config.getVersion() != SUPPORTED_VERSION) {
            warnings.add("[配置警告] " + file + " → version: 配置版本 " + config.getVersion()
                    + " 与程序支持版本 " + SUPPORTED_VERSION + " 不一致，可能存在不兼容字段");
        }

        // 未知字段告警（FR-13-04）：不阻断启动
        for (String field : loaded.unknownFields()) {
            warnings.add("[配置警告] " + file + " → " + field + ": 未知字段，请检查拼写");
        }

        // 非法值校验（FR-13-03）：致命
        LlmConfig llm = config.getLlm();
        String provider = providerOf(config);
        if (!PROVIDERS.contains(provider)) {
            errors.add("[配置错误] " + file + " → llm.provider: 不支持的值 \"" + llm.getProvider()
                    + "\"，可选: deepseek / openai / ollama");
        }
        if (llm.getTemperature() < 0 || llm.getTemperature() > 2) {
            errors.add("[配置错误] " + file + " → llm.temperature: 取值应在 [0, 2] 之间，当前 "
                    + llm.getTemperature());
        }
        if (llm.getTimeoutSeconds() < 1) {
            errors.add("[配置错误] " + file + " → llm.timeoutSeconds: 必须 ≥ 1，当前 "
                    + llm.getTimeoutSeconds());
        }
        if (config.getExecution().getDefaultTimeoutSeconds() < 1) {
            errors.add("[配置错误] " + file + " → execution.defaultTimeoutSeconds: 必须 ≥ 1，当前 "
                    + config.getExecution().getDefaultTimeoutSeconds());
        }

        // 多轮上下文配置校验（FR-CTX-07-02）：maxTurns / maxResultChars 非法值致命
        var ctx = llm.getContext();
        if (ctx != null) {
            if (ctx.getMaxTurns() < 1) {
                errors.add("[配置错误] " + file + " → llm.context.maxTurns: 必须 ≥ 1，当前 "
                        + ctx.getMaxTurns());
            }
            if (ctx.getMaxResultChars() < 100) {
                errors.add("[配置错误] " + file + " → llm.context.maxResultChars: 必须 ≥ 100，当前 "
                        + ctx.getMaxResultChars());
            }
        }

        // 多模型 Profile 校验（FR-MLLM-01）：仅在显式声明 profiles 时逐项校验，
        // 旧版单 llm 节点由上方顶层字段校验覆盖，两者互不干扰。
        validateProfiles(config, file, errors);

        // 语义模式必填项（FR-13-02）：无任何可用 Profile → 警告 + 降级直通（不阻断启动）
        // ollama 本地模型无需 apiKey，与 ModelRegistry 可用项过滤保持一致
        boolean anyUsable = llmConfigured(config);
        if (!anyUsable) {
            warnings.add("[配置警告] " + file + " → llm: 未配置可用的 LLM 参数（apiKey/baseUrl/model），"
                    + "语义模式不可用，本次启动已自动降级为直通模式（direct）");
        }

        return new ValidationResult(errors, warnings, !anyUsable);
    }

    /** 显式多 Profile 时的逐项校验：id 必填唯一、provider 合法、temperature/timeout 区间、defaultProfile 有效。 */
    private static void validateProfiles(AppConfig config, String file, List<String> errors) {
        LlmConfig llm = config.getLlm();
        if (!llm.hasExplicitProfiles()) {
            return;
        }
        var profiles = llm.getProfiles();
        java.util.Set<String> seenIds = new java.util.HashSet<>();
        for (int i = 0; i < profiles.size(); i++) {
            LlmProfile p = profiles.get(i);
            String idx = "llm.profiles[" + i + "]";
            if (isBlank(p.getId())) {
                errors.add("[配置错误] " + file + " → " + idx + ".id: 必填，且全列表唯一");
            } else if (!seenIds.add(p.getId())) {
                errors.add("[配置错误] " + file + " → " + idx + ".id: 重复的 id \"" + p.getId() + "\"");
            }
            if (!PROVIDERS.contains(p.normalizedProvider())) {
                errors.add("[配置错误] " + file + " → " + idx + ".provider: 不支持的值 \"" + p.getProvider()
                        + "\"，可选: deepseek / openai / ollama");
            }
            if (p.getTemperature() < 0 || p.getTemperature() > 2) {
                errors.add("[配置错误] " + file + " → " + idx + ".temperature: 取值应在 [0, 2] 之间，当前 "
                        + p.getTemperature());
            }
            if (p.getTimeoutSeconds() < 1) {
                errors.add("[配置错误] " + file + " → " + idx + ".timeoutSeconds: 必须 ≥ 1，当前 "
                        + p.getTimeoutSeconds());
            }
        }
        // defaultProfile 指向性校验（FR-MLLM-01）：存在且可用
        String def = llm.getDefaultProfile();
        if (!isBlank(def)) {
            LlmProfile target = llm.findProfile(def);
            if (target == null) {
                errors.add("[配置错误] " + file + " → llm.defaultProfile: 引用了不存在的 id \"" + def + "\"");
            } else if (!target.isUsable()) {
                errors.add("[配置错误] " + file + " → llm.defaultProfile: 指向不可用的 Profile \"" + def
                        + "\"（" + target.unusableReason() + "）");
            }
        }
    }

    /** 语义模式可用性判定（REPL 内 /mode smart 切换时复用，FR-13-02）：存在 ≥1 个可用 Profile。 */
    public static boolean llmConfigured(AppConfig config) {
        for (LlmProfile p : config.getLlm().resolvedProfiles()) {
            if (p.isUsable()) {
                return true;
            }
        }
        return false;
    }

    private static String providerOf(AppConfig config) {
        String provider = config.getLlm().getProvider();
        return provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
