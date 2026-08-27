package io.pragmatic.shell.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void missingConfigThrowsWithGuidance() {
        Path missing = tempDir.resolve("config.yaml");
        ConfigLoadException ex = assertThrows(ConfigLoadException.class,
                () -> ConfigLoader.load(missing.toString()));
        assertTrue(ex.getMessage().contains("未找到配置文件"));
        assertTrue(ex.getMessage().contains("--config"));
    }

    @Test
    void unreadableConfigThrows() {
        Path missing = tempDir.resolve("not-exist.yaml");
        ConfigLoadException ex = assertThrows(ConfigLoadException.class,
                () -> ConfigLoader.load(missing.toString()));
        assertTrue(ex.getMessage().contains("未找到配置文件"));
        assertTrue(ex.getMessage().contains("--config 指定的路径是否正确"));
    }

    @Test
    void invalidYamlThrows() throws Exception {
        Path cfg = tempDir.resolve("config.yaml");
        Files.writeString(cfg, "llm: [unclosed");
        ConfigLoadException ex = assertThrows(ConfigLoadException.class,
                () -> ConfigLoader.load(cfg.toString()));
        assertTrue(ex.getMessage().contains("解析失败"));
    }

    @Test
    void emptyFileThrows() throws Exception {
        Path cfg = tempDir.resolve("config.yaml");
        Files.writeString(cfg, "");
        ConfigLoadException ex = assertThrows(ConfigLoadException.class,
                () -> ConfigLoader.load(cfg.toString()));
        assertTrue(ex.getMessage().contains("为空"));
    }

    @Test
    void loadsValidConfigAndNormalizesPaths() throws Exception {
        Path cfg = tempDir.resolve("config.yaml");
        Files.writeString(cfg, """
                version: 1
                shell:
                  initialMode: direct
                llm:
                  provider: ollama
                execution:
                  workDir: .
                logging:
                  auditPath: ~/.smartcli/audit.log
                """);
        ConfigLoader.LoadResult r = ConfigLoader.load(cfg.toString());
        assertEquals("direct", r.config().getShell().getInitialMode());
        assertEquals("ollama", r.config().getLlm().getProvider());
        // ~ 展开 + 绝对化（FR-15-02）
        Path audit = Path.of(r.config().getLogging().getAuditPath());
        assertTrue(audit.isAbsolute());
        assertTrue(audit.toString().contains("/.smartcli/audit.log"));
        // 相对路径基于进程启动目录绝对化
        assertTrue(Path.of(r.config().getExecution().getWorkDir()).isAbsolute());
        // 加载结果为绝对路径
        assertTrue(r.configPath().isAbsolute());
    }

    @Test
    void topLevelNonMapThrows() throws Exception {
        Path cfg = tempDir.resolve("config.yaml");
        Files.writeString(cfg, "- a\n- b\n");
        ConfigLoadException ex = assertThrows(ConfigLoadException.class,
                () -> ConfigLoader.load(cfg.toString()));
        assertTrue(ex.getMessage().contains("顶层应为键值映射"));
    }

    @Test
    void unknownFieldsCollected() throws Exception {
        Path cfg = tempDir.resolve("config.yaml");
        Files.writeString(cfg, """
                version: 1
                llm:
                  provider: deepseek
                  apikey: xxx
                """);
        ConfigLoader.LoadResult r = ConfigLoader.load(cfg.toString());
        assertTrue(r.unknownFields().contains("llm.apikey"));
        // 未知字段被剔除，不影响正常字段加载（FR-13-04：告警不阻断）
        assertEquals("deepseek", r.config().getLlm().getProvider());
    }

    @Test
    void profileConfigLoadedWithoutUnknownWarning() throws Exception {
        Path cfg = tempDir.resolve("config.yaml");
        Files.writeString(cfg, """
                version: 1
                llm:
                  provider: deepseek
                  profile:
                    enabled: false
                    toolWhitelist: [git, docker]
                    toolProbeTimeoutMs: 500
                """);
        ConfigLoader.LoadResult r = ConfigLoader.load(cfg.toString());
        // llm.profile 系列为已知字段，不应告警（环境指纹 FR-PROFILE）
        assertTrue(r.unknownFields().stream().noneMatch(f -> f.startsWith("llm.profile")));
        // profile 配置值完整加载，不被剔除
        assertEquals(false, r.config().getLlm().getProfile().isEnabled());
        assertEquals(500, r.config().getLlm().getProfile().getToolProbeTimeoutMs());
        assertEquals(2, r.config().getLlm().getProfile().getToolWhitelist().size());
    }

    @Test
    void expandHomeHandlesTilde() {
        String home = System.getProperty("user.home");
        assertEquals(home, ConfigLoader.expandHome("~"));
        assertEquals(home + "/x", ConfigLoader.expandHome("~/x"));
        assertEquals("relative/path", ConfigLoader.expandHome("relative/path"));
    }

    @Test
    void loadsMultiProfilesAndSplashWithoutUnknownWarning() throws Exception {
        Path cfg = tempDir.resolve("config.yaml");
        Files.writeString(cfg, """
                version: 1
                shell:
                  splash:
                    enabled: false
                llm:
                  defaultProfile: deep
                  profiles:
                    - id: deep
                      provider: deepseek
                      baseUrl: https://api.deepseek.com/v1
                      model: deepseek-chat
                      apiKey: sk-xxx
                      timeoutSeconds: 60
                    - id: local
                      provider: ollama
                      baseUrl: http://localhost:11434
                      model: qwen3:8b
                      timeoutSeconds: 120
                """);
        ConfigLoader.LoadResult r = ConfigLoader.load(cfg.toString());
        // 新字段均为已知，不应告警
        assertTrue(r.unknownFields().stream().noneMatch(f ->
                f.startsWith("llm.profiles") || f.startsWith("llm.defaultProfile")
                        || f.startsWith("shell.splash")));
        assertEquals("deep", r.config().getLlm().getDefaultProfile());
        assertEquals(2, r.config().getLlm().resolvedProfiles().size());
        assertEquals("deep", r.config().getLlm().resolvedProfiles().get(0).getId());
        assertEquals(120, r.config().getLlm().findProfile("local").getTimeoutSeconds());
        assertEquals(false, r.config().getShell().getSplash().isEnabled());
    }

    @Test
    void singleLegacyLlmSynthesizesInlineProfile() throws Exception {
        Path cfg = tempDir.resolve("config.yaml");
        Files.writeString(cfg, """
                version: 1
                llm:
                  provider: openai
                  baseUrl: https://api.openai.com/v1
                  model: gpt-4o-mini
                  apiKey: sk-legacy
                """);
        ConfigLoader.LoadResult r = ConfigLoader.load(cfg.toString());
        assertEquals(1, r.config().getLlm().resolvedProfiles().size());
        assertEquals("(inline)", r.config().getLlm().resolvedProfiles().get(0).getId());
        assertTrue(r.config().getLlm().resolvedProfiles().get(0).isUsable());
    }
}
