package io.pragmatic.shell.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * native-image 反射元数据覆盖率守卫。
 *
 * <p>背景：SnakeYAML 在 native image 下通过反射实例化配置 POJO，若某个配置类未登记到
 * {@code META-INF/native-image/reflect-config.json}，JVM 模式一切正常，但 native 二进制
 * 启动即失败（NoSuchMethodException: XxxConfig.<init>()），且失败点远在配置类之外，难以定位。
 * 本测试以 classpath 扫描替代人工维护，保证新增配置类时不会漏登记。
 */
class ReflectConfigCoverageTest {

    private static final String REFLECT_CONFIG = "META-INF/native-image/reflect-config.json";

    /** 需要保证已登记的反射包（配置反序列化路径）。 */
    private static final String[] SCANNED_PACKAGES = {
            "io/pragmatic/shell/config",
            "io/pragmatic/shell/config/model"
    };

    @Test
    void allConfigClassesAreRegisteredForReflection() throws Exception {
        Set<String> registered = readRegisteredTypes();
        List<String> missing = new ArrayList<>();
        for (String pkg : SCANNED_PACKAGES) {
            for (String className : listClassNames(pkg)) {
                if (!registered.contains(className)) {
                    missing.add(className);
                }
            }
        }
        assertTrue(missing.isEmpty(),
                () -> "以下类未登记到 " + REFLECT_CONFIG + "，native image 下反射会失败: " + missing
                        + "\n请在 " + REFLECT_CONFIG + " 中补充 allDeclaredConstructors/allPublicConstructors"
                        + "/allDeclaredMethods/allPublicMethods/allDeclaredFields/allPublicFields 条目。");
    }

    /** 读取 reflect-config.json 中已登记的类型全名。 */
    private static Set<String> readRegisteredTypes() throws Exception {
        try (InputStream in = ReflectConfigCoverageTest.class.getClassLoader()
                .getResourceAsStream(REFLECT_CONFIG)) {
            assertNotNull(in, "未找到 " + REFLECT_CONFIG + "（应位于 src/main/resources）");
            List<Map<String, Object>> entries =
                    new ObjectMapper().readValue(in, new TypeReference<>() {
                    });
            Set<String> names = new TreeSet<>();
            for (Map<String, Object> entry : entries) {
                Object name = entry.get("name");
                if (name instanceof String s) {
                    names.add(s.substring(s.lastIndexOf('.') + 1));
                }
            }
            return names;
        }
    }

    /**
     * 列出包下所有 classpath 根（target/classes、target/test-classes）中符合条件的顶层类名。
     * 只关注会被 SnakeYAML 反射实例化的配置 POJO：类名以 Config / Profile 结尾，且非测试类。
     */
    private static Set<String> listClassNames(String packagePath) throws Exception {
        ClassLoader cl = ReflectConfigCoverageTest.class.getClassLoader();
        Set<String> names = new TreeSet<>();
        var urls = Collections.list(cl.getResources(packagePath));
        assertFalse(urls.isEmpty(), () -> "classpath 中未找到包目录: " + packagePath);
        for (URL url : urls) {
            if (!"file".equals(url.getProtocol())) {
                continue; // 非目录型 classpath（jar 内）无法枚举，跳过
            }
            File[] files = new File(url.getPath()).listFiles();
            if (files == null) {
                continue;
            }
            for (File f : files) {
                String n = f.getName();
                if (!n.endsWith(".class") || n.contains("$") || n.endsWith("Test.class")) {
                    continue;
                }
                String simple = n.substring(0, n.length() - ".class".length());
                if (simple.endsWith("Config") || simple.endsWith("Profile")) {
                    names.add(simple);
                }
            }
        }
        assertFalse(names.isEmpty(),
                () -> "未在 " + packagePath + " 下扫描到任何配置类，请检查守卫实现的包路径是否正确");
        return names;
    }
}
