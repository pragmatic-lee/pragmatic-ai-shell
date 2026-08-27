package io.pragmatic.shell.nlu;

import io.pragmatic.shell.config.AppConfig;
import io.pragmatic.shell.config.model.LlmConfig;
import io.pragmatic.shell.config.model.LlmProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelRegistryTest {

    private LlmProfile usable(String id, String provider) {
        return new LlmProfile(id, provider, "http://x/v1", "m-" + id, 0.0, "key", 30);
    }

    private LlmProfile noKey(String id) {
        return new LlmProfile(id, "openai", "http://x/v1", "m-" + id, 0.0, null, 30);
    }

    private AppConfig configWith(List<LlmProfile> profiles, String defaultProfile) {
        AppConfig c = AppConfig.defaults();
        c.getLlm().setProfiles(profiles);
        c.getLlm().setDefaultProfile(defaultProfile);
        return c;
    }

    @Test
    void singleInlineSynthesizedWhenNoProfiles() {
        AppConfig c = AppConfig.defaults();
        c.getLlm().setProvider("openai");
        c.getLlm().setBaseUrl("http://x/v1");
        c.getLlm().setModel("gpt");
        c.getLlm().setApiKey("sk-1");
        ModelRegistry r = new ModelRegistry(c);
        assertEquals(1, r.profiles().size());
        assertEquals(LlmConfig.INLINE_PROFILE_ID, r.profiles().get(0).getId());
        assertFalse(r.isMulti());
        assertTrue(r.activeProfile().isUsable());
    }

    @Test
    void activeResolvesByDefaultProfile() {
        AppConfig c = configWith(List.of(usable("deep", "deepseek"), usable("local", "ollama")), "local");
        ModelRegistry r = new ModelRegistry(c);
        assertTrue(r.isMulti());
        assertEquals("local", r.activeProfile().getId());
    }

    @Test
    void activeFallsBackToFirstUsableWhenDefaultMissingOrUnusable() {
        AppConfig c = configWith(List.of(noKey("broken"), usable("good", "openai")), "broken");
        ModelRegistry r = new ModelRegistry(c);
        assertEquals("good", r.activeProfile().getId());
    }

    @Test
    void switchToRejectsUnknownAndUnusable() {
        AppConfig c = configWith(List.of(usable("deep", "deepseek"), noKey("broken")), "deep");
        ModelRegistry r = new ModelRegistry(c);
        assertFalse(r.switchTo("nope"));
        assertFalse(r.switchTo("broken"));
        assertTrue(r.switchTo("deep"));
    }

    @Test
    void otherUsableProfilesExcludesActive() {
        AppConfig c = configWith(List.of(usable("a", "deepseek"), usable("b", "openai"), noKey("z")), "a");
        ModelRegistry r = new ModelRegistry(c);
        List<LlmProfile> others = r.otherUsableProfiles();
        assertEquals(1, others.size());
        assertEquals("b", others.get(0).getId());
    }

    @Test
    void unusableReasonNeverLeaksKey() {
        LlmProfile p = noKey("x");
        assertFalse(p.isUsable());
        assertNotNull(p.unusableReason());
        assertFalse(p.unusableReason().contains("null"));
        assertNull(usable("y", "openai").unusableReason());
    }
}
