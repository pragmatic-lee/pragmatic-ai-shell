package io.pragmatic.shell.config;

import io.pragmatic.shell.config.model.LlmConfig;
import io.pragmatic.shell.config.model.LlmProfile;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置写回工具（/setup 向导使用，FR-SETUP-05）。
 *
 * <p>核心约束：仅替换 YAML 顶层的 {@code llm} 节点，其余顶层字段
 * （version / shell / execution / safety / logging 等）原样保留，避免覆盖用户手工配置。
 *
 * <p>写回前自动备份为 {@code <原文件名>.bak}。
 *
 * <p>llm 节点统一采用**多 Profile 写法**（defaultProfile + profiles）输出：
 * 即便只配置一个模型也写入 profiles 列表，便于后续 /setup 增量追加。
 */
public final class ConfigWriter {

    private ConfigWriter() {
    }

    /**
     * 将 llm 配置合并写回 config.yaml（仅替换 llm 节点）。
     *
     * @param yamlPath 配置文件路径
     * @param llm      新的 LLM 配置（context/profile 节点取当前生效值一并写回）
     * @return 备份文件路径
     * @throws IOException 读写失败
     */
    public static Path mergeLlmAndWrite(Path yamlPath, LlmConfig llm) throws IOException {
        Map<String, Object> root = loadMap(yamlPath);
        Path backup = backup(yamlPath);
        root.put("llm", llmToMap(llm));
        Files.writeString(yamlPath, dump(root));
        return backup;
    }

    /** 读取现有 YAML 为有序 Map（文件不存在或为空时返回空 Map，视为全新配置）。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> loadMap(Path yamlPath) throws IOException {
        if (!Files.exists(yamlPath)) {
            return new LinkedHashMap<>();
        }
        String text = Files.readString(yamlPath);
        if (text.isBlank()) {
            return new LinkedHashMap<>();
        }
        Object raw = new Yaml().load(text);
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                result.put(String.valueOf(e.getKey()), e.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    /** 备份原文件为 <name>.bak，返回备份路径。 */
    public static Path backup(Path yamlPath) throws IOException {
        Path backup = yamlPath.resolveSibling(yamlPath.getFileName() + ".bak");
        Files.copy(yamlPath, backup, StandardCopyOption.REPLACE_EXISTING);
        return backup;
    }

    /**
     * 序列化 llm 节点：defaultProfile + profiles + showProgress + context + profile。
     * 顶层单模型字段（provider/baseUrl/model/apiKey 等）不输出，避免与 profiles 并存产生歧义。
     */
    private static Map<String, Object> llmToMap(LlmConfig llm) {
        Map<String, Object> map = new LinkedHashMap<>();
        String defaultProfile = llm.getDefaultProfile();
        if (defaultProfile != null && !defaultProfile.isBlank()) {
            map.put("defaultProfile", defaultProfile);
        }
        List<LlmProfile> profiles = llm.getProfiles();
        if (profiles != null && !profiles.isEmpty()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (LlmProfile p : profiles) {
                list.add(profileToMap(p));
            }
            map.put("profiles", list);
        }
        map.put("showProgress", llm.isShowProgress());
        if (llm.getContext() != null) {
            map.put("context", toMap(llm.getContext()));
        }
        if (llm.getProfile() != null) {
            map.put("profile", toMap(llm.getProfile()));
        }
        return stripNulls(map);
    }

    /** 单个 Profile 序列化（apiKey 为 null 时不输出，如 ollama 本地模型）。 */
    private static Map<String, Object> profileToMap(LlmProfile p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", p.getId());
        map.put("provider", p.getProvider());
        map.put("baseUrl", p.getBaseUrl());
        map.put("model", p.getModel());
        map.put("temperature", p.getTemperature());
        map.put("apiKey", p.getApiKey());
        map.put("timeoutSeconds", p.getTimeoutSeconds());
        return stripNulls(map);
    }

    /**
     * POJO 转 Map（经 YAML dump/load 往返，规避类型标签）。
     * 必须为 POJO 类型显式声明 {@code Tag.MAP}，否则 dump 会写入
     * {@code !!io.pragmatic.shell.config.model.XxxConfig} 全局标签，回读时报
     * "Global tag is not allowed"。
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(Object pojo) {
        Object raw = new Yaml().load(dumpPojo(pojo));
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                result.put(String.valueOf(e.getKey()), e.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    /** 将 POJO 序列化为无类型标签的 YAML 文本。 */
    private static String dumpPojo(Object pojo) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        org.yaml.snakeyaml.representer.Representer representer =
                new org.yaml.snakeyaml.representer.Representer(options);
        representer.addClassTag(pojo.getClass(), org.yaml.snakeyaml.nodes.Tag.MAP);
        return new Yaml(representer, options).dump(pojo);
    }

    /** 序列化为 YAML 文本（块样式、2 空格缩进）。 */
    public static String dump(Map<String, Object> map) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        return new Yaml(options).dump(stripNulls(map));
    }

    /** 递归剔除值为 null 的键，避免输出 apiKey: null 之类冗余项。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> stripNulls(Map<String, Object> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Object v = e.getValue();
            if (v == null) {
                continue;
            }
            if (v instanceof Map<?, ?> child) {
                result.put(e.getKey(), stripNulls(cast(child)));
            } else if (v instanceof List<?> childList) {
                List<Object> list = new ArrayList<>();
                for (Object item : childList) {
                    if (item instanceof Map<?, ?> itemMap) {
                        list.add(stripNulls(cast(itemMap)));
                    } else if (item != null) {
                        list.add(item);
                    }
                }
                result.put(e.getKey(), list);
            } else {
                result.put(e.getKey(), v);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
