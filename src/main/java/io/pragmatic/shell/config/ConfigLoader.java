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
 * - 未指定 --config 时按三级回落解析（FR-CFG-02/03）：当前目录 config.yaml（存在才用）
 *   → ~/.smartcli/config.yaml（DMG/App 分发归口，与审计日志同目录）；
 * - 文件缺失时：仅在 {@code generateIfMissing=true}（首次启动场景，FR-ZERO-01）
 *   自动生成默认配置文件并继续；否则仍抛 {@link ConfigLoadException} 报错退出；
 * - 文件不可读 / 解析失败 / 为空一律抛 {@link ConfigLoadException}（FR-ZERO-03：
 *   不静默覆盖用户已有的手工配置）；
 * - 内置默认值仅作为字段级缺省（POJO 字段初始值）；
 * - 加载后统一处理路径字段（~ 展开、相对进程启动目录绝对化），并检测未知字段（FR-13-04）。
 */
public final class ConfigLoader {

    /** 默认配置文件路径（相对进程启动目录）。 */
    public static final String DEFAULT_CONFIG_FILE = "config.yaml";

    /**
     * 首次启动自动生成的默认配置模板（FR-ZERO-01）。
     * llm.apiKey 留空 → 无可用 LLM → 由 ConfigValidator 触发降级直通，用户首次即可进入 REPL。
     */
    private static final String DEFAULT_CONFIG_TEMPLATE = """
            # ============================================================
            # smartcli 配置文件（首次启动自动生成）
            # 说明: 尚未配置大模型，当前以直通模式运行。
            #       在 REPL 中输入 /setup 可引导配置大模型。
            # ============================================================

            version: 1                      # 配置版本

            # ===== 启动行为 =====
            shell:
              initialMode: smart            # smart（语义）| direct（直通）
              splash:
                enabled: true               # 启动界面总开关

            # ===== LLM 后端（语义模式必填）=====
            llm:
              provider: deepseek            # deepseek | openai | ollama
              baseUrl: https://api.deepseek.com/v1
              model: deepseek-chat
              temperature: 0.0              # 采样温度 [0, 2]
              apiKey:                       # 留空 → 语义模式不可用，自动降级为直通模式
              timeoutSeconds: 60            # LLM 调用超时（秒），≥ 1
              showProgress: true            # 等待动画开关
              context:                      # 多轮对话上下文
                enabled: true
                maxTurns: 10                # 保留最近轮数，≥ 1
                maxResultChars: 2000        # 单轮结果摘要上限（字符），≥ 100
              profile:                      # 环境指纹（环境感知）
                enabled: true
                toolWhitelist: []           # 留空使用内置默认探测清单
                toolProbeTimeoutMs: 200

            # ===== 命令执行 =====
            execution:
              defaultTimeoutSeconds: 60     # 命令执行超时（秒），≥ 1
              workDir: .                    # 默认工作目录（支持 ~ 展开）
              readOnly: false               # 只读模式

            # ===== 安全 =====
            safety:
              strictMode: false             # 所有命令都需二次确认
              confirmDestructive: true      # 危险命令二次确认
              blockPrivateAddresses: true   # 拦截内网地址探测
              sudoPolicy: confirm           # confirm=提权强确认后放行（默认）| reject=拒绝 sudo | allow=放行

            # ===== 语义理解（LLM 职责边界）=====
            nlu:
              executionJudgment: false      # false=模型只翻译，不判定能否执行，命令交由人确认（默认）
                                            # true=模型可因风险/可行性拒绝
              toolConstraint: reference     # reference=工具清单仅参考，不禁止未列出工具（默认）
                                            # strict=只用环境信息中已安装的工具

            # ===== 日志 / 审计 =====
            logging:
              auditEnabled: true
              auditPath: ~/.smartcli/audit.log
            """;

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
            "safety.sudoPolicy",
            "nlu", "nlu.executionJudgment", "nlu.toolConstraint",
            "logging", "logging.auditEnabled", "logging.auditPath");

    private ConfigLoader() {
    }

    /** 加载结果：生效配置 + 配置文件绝对路径 + 未知字段列表 + 是否本次自动生成（供提示展示）。 */
    public record LoadResult(AppConfig config, Path configPath, List<String> unknownFields,
                             boolean generated) {
    }

    /** 严格加载：文件缺失即报错（显式 --config 指定错误路径等场景）。 */
    public static LoadResult load(String configPath) {
        return load(configPath, false);
    }

    /**
     * 加载配置。
     * @param generateIfMissing 文件缺失时是否自动生成默认配置（FR-ZERO-01，首次启动场景）
     */
    public static LoadResult load(String configPath, boolean generateIfMissing) {
        return load(configPath, generateIfMissing, Path.of(""), Path.of(System.getProperty("user.home")));
    }

    /** 包级可见重载：注入基准目录与主目录，供测试验证回落行为而不触达真实环境；生产路径等价委托。 */
    static LoadResult load(String configPath, boolean generateIfMissing, Path baseDir, Path homeDir) {
        Path path = resolveConfigPath(configPath, baseDir, homeDir);
        boolean generated = false;
        if (!Files.exists(path)) {
            if (!generateIfMissing) {
                throw new ConfigLoadException(missingMessage(configPath, path));
            }
            writeDefaultConfig(path);
            generated = true;
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
        return new LoadResult(config, path.toAbsolutePath().normalize(), unknownFields, generated);
    }

    /** 生成默认配置文件（FR-ZERO-01）：落盘带注释的模板，llm.apiKey 留空以触发降级直通。 */
    private static void writeDefaultConfig(Path path) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, DEFAULT_CONFIG_TEMPLATE);
        } catch (Exception e) {
            throw new ConfigLoadException("[配置错误] 自动生成默认配置文件失败: " + path
                    + "\n  原因: " + e.getMessage());
        }
    }

    /**
     * 定位配置文件（三级回落，FR-CFG-01/02/03）：--config 指定 &gt; 基准目录 config.yaml（存在才用，
     * 兼容存量习惯与项目级配置）&gt; ~/.smartcli/config.yaml（DMG/App 分发归口，与审计日志同目录）。
     */
    static Path resolveConfigPath(String configPath) {
        return resolveConfigPath(configPath, Path.of(""), Path.of(System.getProperty("user.home")));
    }

    /** 包级可见重载：注入基准目录与主目录，供测试验证三级回落而不触达真实环境。 */
    static Path resolveConfigPath(String configPath, Path baseDir, Path homeDir) {
        if (configPath != null) {
            return Path.of(expandHome(configPath)).toAbsolutePath().normalize();
        }
        Path local = baseDir.resolve(DEFAULT_CONFIG_FILE).toAbsolutePath().normalize();
        if (Files.exists(local)) {
            return local;
        }
        return homeDir.resolve(".smartcli").resolve(DEFAULT_CONFIG_FILE).normalize();
    }

    private static String missingMessage(String configPath, Path path) {
        StringBuilder sb = new StringBuilder("[配置错误] 未找到配置文件: ").append(path);
        if (configPath == null) {
            sb.append("\n  当前目录与 ~/.smartcli 目录下均不存在 config.yaml，且未通过 --config 指定配置文件。")
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
