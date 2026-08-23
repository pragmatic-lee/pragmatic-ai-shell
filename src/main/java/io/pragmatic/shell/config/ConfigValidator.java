package io.pragmatic.shell.config;

import io.pragmatic.shell.config.model.LlmConfig;

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

        // 语义模式必填项（FR-13-02）：缺失 → 警告 + 降级直通（不阻断启动）
        // ollama 本地模型无需 apiKey，与 LangChainNluService.buildModel 分支对齐
        if (!llmConfigured(config)) {
            warnings.add("[配置警告] " + file + " → llm: 未配置完整的 LLM 参数（apiKey/baseUrl/model），"
                    + "语义模式不可用，本次启动已自动降级为直通模式（direct）");
        }

        return new ValidationResult(errors, warnings, !llmConfigured(config));
    }

    /** 语义模式可用性判定（REPL 内 /mode smart 切换时复用，FR-13-02）。 */
    public static boolean llmConfigured(AppConfig config) {
        LlmConfig llm = config.getLlm();
        String provider = providerOf(config);
        return !isBlank(llm.getBaseUrl()) && !isBlank(llm.getModel())
                && (provider.equals("ollama") || !isBlank(llm.getApiKey()));
    }

    private static String providerOf(AppConfig config) {
        String provider = config.getLlm().getProvider();
        return provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
