package io.pragmatic.shell.config;

import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 使用 SnakeYAML 加载 config.yaml 为 AppConfig。
 * 若文件不存在或加载失败，回退到内置默认值。
 */
public final class ConfigLoader {

    private ConfigLoader() {
    }

    public static AppConfig load(String configPath) {
        if (configPath == null) {
            return AppConfig.defaults();
        }
        Path path = Path.of(configPath);
        if (!Files.exists(path)) {
            return AppConfig.defaults();
        }
        try {
            Yaml yaml = new Yaml();
            AppConfig cfg = yaml.loadAs(Files.newInputStream(path), AppConfig.class);
            return cfg != null ? cfg : AppConfig.defaults();
        } catch (Exception e) {
            return AppConfig.defaults();
        }
    }
}
