package io.pragmatic.shell.config;

import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 使用 SnakeYAML 加载 config.yaml 为 AppConfig（v4：强制加载，FR-12）。
 * - 未指定 --config 时默认加载进程启动目录下的 config.yaml；
 * - 文件缺失 / 不可读 / 解析失败 / 为空一律抛出 {@link ConfigLoadException}，
 *   由启动入口报错退出，不再静默回退内置默认值；
 * - 内置默认值仅作为字段级缺省（POJO 字段初始值）；
 * - 加载后统一处理路径字段（~ 展开、相对进程启动目录绝对化），并检测未知字段（FR-13-04）。
 */
public final class ConfigLoader {

    /** 默认配置文件路径（相对进程启动目录）。 */
    public static final String DEFAULT_CONFIG_FILE = "config.yaml";

    /** 已知字段全路径白名单（未知字段告警用，FR-13-04）。 */
    private static final Set<String> KNOWN_FIELDS = Set.of(
            "version",
            "shell", "shell.initialMode", "shell.splash", "shell.splash.enabled",
            "llm", "llm.provider", "llm.baseUrl", "llm.model", "llm.temperature",
            "llm.apiKey", "llm.timeoutSeconds", "llm.showProgress",
            "llm.defaultProfile", "llm.profiles",
            "llm.context", "llm.context.enabled", "llm.context.maxTurns", "llm.context.maxResultChars",
            "llm.profile", "llm.profile.enabled", "llm.profile.toolWhitelist", "llm.profile.toolProbeTimeoutMs",
            "execution", "execution.defaultTimeoutSeconds", "execution.workDir", "execution.readOnly",
            "safety", "safety.strictMode", "safety.confirmDestructive", "safety.blockPrivateAddresses",
            "logging", "logging.auditEnabled", "logging.auditPath");

    private ConfigLoader() {
    }

    /** 加载结果：生效配置 + 配置文件绝对路径 + 未知字段列表（供告警展示）。 */
    public record LoadResult(AppConfig config, Path configPath, List<String> unknownFields) {
    }

    public static LoadResult load(String configPath) {
        Path path = resolveConfigPath(configPath);
        if (!Files.exists(path)) {
            throw new ConfigLoadException(missingMessage(configPath, path));
        }
        if (!Files.isReadable(path)) {
            throw new ConfigLoadException("[配置错误] 配置文件不可读: " + path);
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (Exception e) {
            throw new ConfigLoadException("[配置错误] 读取配置文件失败: " + path
                    + "\n  原因: " + e.getMessage());
        }
        Object raw;
        try {
            raw = new Yaml().load(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw new ConfigLoadException("[配置错误] 配置文件解析失败: " + path
                    + "\n  原因: " + e.getMessage());
        }
        if (raw == null) {
            throw new ConfigLoadException("[配置错误] 配置文件为空: " + path
                    + "\n  修复方式: 基于模板填写内容后重启: cp config.example.yaml ./config.yaml");
        }
        List<String> unknownFields = new ArrayList<>();
        Map<String, Object> map = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> rawMap) {
            for (Map.Entry<?, ?> e : rawMap.entrySet()) {
                map.put(String.valueOf(e.getKey()), e.getValue());
            }
            collectUnknownFields(map, "", unknownFields);
            // SnakeYAML loadAs 遇到未知属性会抛异常，先剔除未知字段再反序列化（FR-13-04：告警不阻断）
            removeUnknownFields(map, "", unknownFields);
        } else {
            throw new ConfigLoadException("[配置错误] 配置文件格式不正确（顶层应为键值映射）: " + path);
        }
        AppConfig config;
        try {
            String yamlText = new Yaml().dump(map);
            config = new Yaml().loadAs(new StringReader(yamlText), AppConfig.class);
        } catch (Exception e) {
            throw new ConfigLoadException("[配置错误] 配置文件字段解析失败: " + path
                    + "\n  原因: " + e.getMessage());
        }
        if (config == null) {
            throw new ConfigLoadException("[配置错误] 配置文件为空: " + path);
        }
        normalizePaths(config);
        return new LoadResult(config, path.toAbsolutePath().normalize(), unknownFields);
    }

    /** 定位配置文件：--config 指定 > 当前目录 config.yaml；~ 展开后基于进程启动目录绝对化。 */
    private static Path resolveConfigPath(String configPath) {
        String p = configPath != null ? configPath : DEFAULT_CONFIG_FILE;
        return Path.of(expandHome(p)).toAbsolutePath().normalize();
    }

    private static String missingMessage(String configPath, Path path) {
        StringBuilder sb = new StringBuilder("[配置错误] 未找到配置文件: ").append(path);
        if (configPath == null) {
            sb.append("\n  当前目录下不存在 config.yaml，且未通过 --config 指定配置文件。")
                    .append("\n  修复方式:")
                    .append("\n    1) 基于模板创建: cp config.example.yaml ./config.yaml")
                    .append("\n    2) 或指定其他路径: smartcli --config /path/to/config.yaml");
        } else {
            sb.append("\n  请检查 --config 指定的路径是否正确。");
        }
        return sb.toString();
    }

    /** 递归收集未知字段路径（SnakeYAML loadAs 对未知属性会抛异常，需先检测剔除）。 */
    private static void collectUnknownFields(Map<String, Object> map, String prefix, List<String> unknown) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String path = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            if (!KNOWN_FIELDS.contains(path)) {
                unknown.add(path);
            } else if (e.getValue() instanceof Map<?, ?> child) {
                collectUnknownFields(castMap(child), path, unknown);
            }
        }
    }

    /** 从配置 Map 中剔除未知字段，避免 loadAs 因未知属性抛异常。 */
    private static void removeUnknownFields(Map<String, Object> map, String prefix, List<String> unknown) {
        map.keySet().removeIf(key -> {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            return unknown.contains(path);
        });
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getValue() instanceof Map<?, ?> child) {
                removeUnknownFields(castMap(child), prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey(),
                        unknown);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    /** 路径字段统一规范化（FR-15-02）：~ 展开，相对路径基于进程启动目录绝对化。 */
    private static void normalizePaths(AppConfig config) {
        String workDir = config.getExecution().getWorkDir();
        if (workDir != null && !workDir.isBlank()) {
            config.getExecution().setWorkDir(
                    Path.of(expandHome(workDir)).toAbsolutePath().normalize().toString());
        }
        String auditPath = config.getLogging().getAuditPath();
        if (auditPath != null && !auditPath.isBlank()) {
            config.getLogging().setAuditPath(
                    Path.of(expandHome(auditPath)).toAbsolutePath().normalize().toString());
        }
    }

    /** 展开路径中的 ~ / ~/ 前缀为用户主目录，其余原样返回。 */
    public static String expandHome(String path) {
        if (path == null || path.isBlank()) {
            return path;
        }
        if (path.equals("~")) {
            return System.getProperty("user.home");
        }
        if (path.startsWith("~/") || path.startsWith("~\\")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }
}
