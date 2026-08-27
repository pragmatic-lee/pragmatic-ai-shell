package io.pragmatic.shell.config;

import org.junit.jupiter.api.Test;

import io.pragmatic.shell.config.model.LlmProfile;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigValidatorTest {

    private AppConfig validConfig() {
        AppConfig c = AppConfig.defaults();
        c.getLlm().setProvider("deepseek");
        c.getLlm().setBaseUrl("https://api.deepseek.com/v1");
        c.getLlm().setModel("deepseek-chat");
        c.getLlm().setApiKey("sk-test");
        return c;
    }

    private ConfigLoader.LoadResult load(AppConfig c) {
        return new ConfigLoader.LoadResult(c, Path.of("/tmp/config.yaml"), List.of());
    }

    @Test
    void validConfigPasses() {
        ConfigValidator.ValidationResult r = ConfigValidator.validate(load(validConfig()));
        assertTrue(r.errors().isEmpty());
        assertFalse(r.degradeToDirect());
    }

    @Test
    void invalidProviderIsFatal() {
        AppConfig c = validConfig();
        c.getLlm().setProvider("gpt");
        ConfigValidator.ValidationResult r = ConfigValidator.validate(load(c));
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("llm.provider")));
    }

    @Test
    void temperatureOutOfRangeIsFatal() {
        AppConfig c = validConfig();
        c.getLlm().setTemperature(3.0);
        assertFalse(ConfigValidator.validate(load(c)).errors().isEmpty());
    }

    @Test
    void timeoutBelowOneIsFatal() {
        AppConfig c = validConfig();
        c.getLlm().setTimeoutSeconds(0);
        assertFalse(ConfigValidator.validate(load(c)).errors().isEmpty());

        AppConfig c2 = validConfig();
        c2.getExecution().setDefaultTimeoutSeconds(-1);
        assertFalse(ConfigValidator.validate(load(c2)).errors().isEmpty());
    }

    @Test
    void missingApiKeyDegradesToDirect() {
        AppConfig c = validConfig();
        c.getLlm().setApiKey(null);
        ConfigValidator.ValidationResult r = ConfigValidator.validate(load(c));
        assertTrue(r.degradeToDirect());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("降级")));
    }

    @Test
    void ollamaWithoutApiKeyIsFine() {
        AppConfig c = validConfig();
        c.getLlm().setProvider("ollama");
        c.getLlm().setApiKey(null);
        ConfigValidator.ValidationResult r = ConfigValidator.validate(load(c));
        assertFalse(r.degradeToDirect());
        assertTrue(r.errors().isEmpty());
    }

    @Test
    void unknownFieldsProduceWarning() {
        ConfigLoader.LoadResult lr = new ConfigLoader.LoadResult(
                validConfig(), Path.of("/tmp/config.yaml"), List.of("llm.apikey"));
        ConfigValidator.ValidationResult r = ConfigValidator.validate(lr);
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("llm.apikey") && w.contains("未知字段")));
    }

    @Test
    void versionMismatchProducesWarning() {
        AppConfig c = validConfig();
        c.setVersion(2);
        ConfigValidator.ValidationResult r = ConfigValidator.validate(load(c));
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("version")));
    }

    @Test
    void contextMaxTurnsBelowOneIsFatal() {
        AppConfig c = validConfig();
        c.getLlm().getContext().setMaxTurns(0);
        ConfigValidator.ValidationResult r = ConfigValidator.validate(load(c));
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("llm.context.maxTurns")));
    }

    @Test
    void contextMaxResultCharsBelow100IsFatal() {
        AppConfig c = validConfig();
        c.getLlm().getContext().setMaxResultChars(50);
        ConfigValidator.ValidationResult r = ConfigValidator.validate(load(c));
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("llm.context.maxResultChars")));
    }

    @Test
    void contextDefaultsAreValid() {
        ConfigValidator.ValidationResult r = ConfigValidator.validate(load(validConfig()));
        assertTrue(r.errors().isEmpty());
        assertTrue(r.errors().stream().noneMatch(e -> e.contains("llm.context")));
    }

    @Test
    void maskedApiKeyNeverAppearsInMessages() {
        AppConfig c = validConfig();
        c.getLlm().setApiKey("sk-super-secret-value");
        ConfigValidator.ValidationResult r = ConfigValidator.validate(load(c));
        for (String line : r.warnings()) {
            assertFalse(line.contains("sk-super-secret-value"));
        }
        for (String line : r.errors()) {
            assertFalse(line.contains("sk-super-secret-value"));
        }
    }

    /* ==================== 多模型接入（FR-MLLM）==================== */

    private LlmProfile prof(String id, String provider, String apiKey) {
        return new LlmProfile(id, provider, "http://x/v1", "m-" + id, 0.0, apiKey, 30);
    }

    @Test
    void someProfileUsableNoDegrade() {
        AppConfig c = AppConfig.defaults();
        c.getLlm().setProfiles(List.of(prof("bad", "openai", null), prof("good", "deepseek", "sk-1")));
        ConfigValidator.ValidationResult r = ConfigValidator.validate(load(c));
        assertTrue(r.errors().isEmpty());
        assertFalse(r.degradeToDirect());
    }

    @Test
    void allProfilesUnusableDegrades() {
        AppConfig c = AppConfig.defaults();
        c.getLlm().setProfiles(List.of(prof("a", "openai", null), prof("b", "openai", null)));
        ConfigValidator.ValidationResult r = ConfigValidator.validate(load(c));
        assertTrue(r.degradeToDirect());
    }

    @Test
    void duplicateProfileIdIsFatal() {
        AppConfig c = AppConfig.defaults();
        c.getLlm().setProfiles(List.of(prof("dup", "deepseek", "k"), prof("dup", "openai", "k")));
        ConfigValidator.ValidationResult r = ConfigValidator.validate(load(c));
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("llm.profiles") && e.contains("重复")));
    }

    @Test
    void defaultProfilePointingUnusableIsFatal() {
        AppConfig c = AppConfig.defaults();
        c.getLlm().setProfiles(List.of(prof("good", "deepseek", "k"), prof("bad", "openai", null)));
        c.getLlm().setDefaultProfile("bad");
        ConfigValidator.ValidationResult r = ConfigValidator.validate(load(c));
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("llm.defaultProfile")));
    }

    @Test
    void invalidProfileProviderIsFatal() {
        AppConfig c = AppConfig.defaults();
        c.getLlm().setProfiles(List.of(prof("x", "gpt", "k")));
        ConfigValidator.ValidationResult r = ConfigValidator.validate(load(c));
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("llm.profiles[0].provider")));
    }
}
