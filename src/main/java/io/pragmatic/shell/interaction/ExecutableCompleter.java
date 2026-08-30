package io.pragmatic.shell.interaction;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 系统可执行文件补全（设计文档 4.2 / G4）：
 * 扫描 PATH 环境变量各目录下的可执行文件，补全首个 token 的命令名。
 *
 * <p>性能设计：首次触发时扫描并缓存进程内结果（会话级，不监听 PATH 变化）；
 * 同名命令取 PATH 中靠前者；按字典序返回；单次候选上限 200。
 * native-image 友好：仅用 System.getenv 与 NIO 遍历，无反射、无资源加载。
 */
public final class ExecutableCompleter {

    /** 单次返回候选上限。 */
    private static final int LIMIT = 200;

    private final AtomicReference<List<String>> cached = new AtomicReference<>();

    /** 完成前缀过滤后的候选（用于分派器 wordIndex == 0 命令名补全）。 */
    public List<String> complete(String prefix) {
        List<String> all = allExecutables();
        String p = prefix == null ? "" : prefix;
        return all.stream()
                .filter(n -> p.isEmpty() || n.startsWith(p))
                .limit(LIMIT)
                .toList();
    }

    /** 预热：主动触发扫描并缓存（可在 REPL 启动后异步调用，避免首次 Tab 卡顿）。 */
    public void warmUp() {
        allExecutables();
    }

    private List<String> allExecutables() {
        List<String> existing = cached.get();
        if (existing != null) {
            return existing;
        }
        List<String> scanned = scanPath();
        // 多个线程最多一个结果生效；后写入者也是等价结果，无副作用
        cached.compareAndSet(null, scanned);
        return cached.get();
    }

    private List<String> scanPath() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return List.of();
        }
        boolean isWindows = System.getProperty("os.name")
                .toLowerCase(Locale.ROOT).contains("win");
        List<String> result = new ArrayList<>();
        for (String dir : pathEnv.split(File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            Path dirPath = Path.of(dir);
            File dirFile = dirPath.toFile();
            if (!dirFile.isDirectory()) {
                continue;
            }
            File[] files = dirFile.listFiles((d, name) -> isExecutable(name, d.toPath().resolve(name), isWindows));
            if (files == null) {
                continue;
            }
            for (File f : files) {
                result.add(f.getName());
            }
        }
        // 去重（同名取先出现者，即 PATH 靠前）+ 字典序排序
        return result.stream()
                .distinct()
                .sorted()
                .toList();
    }

    private static boolean isExecutable(String name, Path path, boolean isWindows) {
        if (isWindows) {
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.endsWith(".exe") || lower.endsWith(".bat")
                    || lower.endsWith(".cmd") || lower.endsWith(".ps1");
        }
        return Files.isExecutable(path) && !Files.isDirectory(path);
    }
}
