package io.pragmatic.shell.config;

import org.junit.jupiter.api.Test;

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
}
