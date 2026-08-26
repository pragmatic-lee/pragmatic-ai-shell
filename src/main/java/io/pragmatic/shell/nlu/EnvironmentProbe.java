package io.pragmatic.shell.nlu;

import io.pragmatic.shell.config.model.ProfileConfig;

import io.pragmatic.shell.nlu.EnvironmentProfile.OsInfo;
import io.pragmatic.shell.nlu.EnvironmentProfile.ShellInfo;
import io.pragmatic.shell.nlu.EnvironmentProfile.ToolInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 环境指纹采集器（环境感知 v5+）。
 * 容错优先：任一子采集失败都降级为"未知/未安装"，绝不让采集异常导致 shell 启动崩溃。
 */
public final class EnvironmentProbe {

    private final ProfileConfig cfg;

    public EnvironmentProbe(ProfileConfig cfg) {
        this.cfg = cfg == null ? new ProfileConfig() : cfg;
    }

    /** 执行一次全量采集，返回不可变快照；任一子采集失败不影响其余字段。 */
    public EnvironmentProfile probe() {
        OsInfo os = probeOs();
        ShellInfo shell = probeShell();
        List<ToolInfo> tools = probeTools();
        return new EnvironmentProfile(os, shell, tools, Instant.now());
    }

    private OsInfo probeOs() {
        try {
            String family = System.getProperty("os.name", "unknown");
            String arch = System.getProperty("os.arch", null);
            String name = family;
            String version = null;
            String kernel = null;
            String low = family.toLowerCase();
            if (low.contains("mac") || low.contains("darwin")) {
                name = "macOS";
                String[] sw = run("sw_vers", "-productVersion");
                if (sw != null && sw.length > 0) {
                    version = sw[0].strip();
                }
                String[] un = run("uname", "-sr");
                if (un != null && un.length > 0) {
                    kernel = un[0].strip();
                }
            } else if (low.contains("win")) {
                name = "Windows";
                String[] ver = run("cmd", "/c", "ver");
                if (ver != null && ver.length > 0) {
                    version = ver[0].replaceAll(".*\\[|].*", "").strip();
                }
            } else if (low.contains("nux") || low.contains("nix")) {
                name = "Linux";
                String[] rel = run("cat", "/etc/os-release");
                if (rel != null) {
                    for (String l : rel) {
                        if (l.startsWith("PRETTY_NAME=")) {
                            version = stripQuotes(l.substring("PRETTY_NAME=".length()));
                        }
                    }
                }
                String[] un = run("uname", "-sr");
                if (un != null && un.length > 0) {
                    kernel = un[0].strip();
                }
            }
            return new OsInfo(family, name, version, kernel, arch);
        } catch (Exception e) {
            return new OsInfo("unknown", "unknown", null, null, null);
        }
    }

    private ShellInfo probeShell() {
        try {
            String shellEnv = System.getenv("SHELL");
            String type = "unknown";
            if (shellEnv != null && !shellEnv.isBlank()) {
                int slash = shellEnv.lastIndexOf('/');
                type = (slash >= 0 ? shellEnv.substring(slash + 1) : shellEnv);
            }
            String version = null;
            if ("zsh".equals(type) || "bash".equals(type) || "fish".equals(type)) {
                String[] out = run(type, "--version");
                if (out != null && out.length > 0) {
                    // 形如 "zsh 5.9 (x86_64-apple-darwin23.0)"
                    version = out[0].replaceAll("\\(.*", "").strip().replaceFirst("^" + type + "\\s*", "");
                }
            }
            return new ShellInfo(type, version);
        } catch (Exception e) {
            return new ShellInfo("unknown", null);
        }
    }

    private List<ToolInfo> probeTools() {
        List<String> whitelist = (cfg.getToolWhitelist() == null || cfg.getToolWhitelist().isEmpty())
                ? ProfileConfig.defaultTools() : cfg.getToolWhitelist();
        List<ToolInfo> result = new ArrayList<>();
        for (String tool : whitelist) {
            result.add(probeOneTool(tool));
        }
        return result;
    }

    private ToolInfo probeOneTool(String tool) {
        try {
            // 优先 which/where 确认存在（Windows 用 where）
            String[] which = run(isWindows() ? "where" : "which", tool);
            String path = null;
            if (which != null && which.length > 0 && !which[0].isBlank()) {
                path = which[0].strip();
            }
            if (path == null) {
                return new ToolInfo(tool, null, null, false);
            }
            // 探测版本：--version 优先，回退 -V
            String version = null;
            for (String flag : new String[]{"--version", "-V", "--version"}) {
                String[] out = run(timeoutMs(), tool, flag);
                if (out != null) {
                    for (String line : out) {
                        String v = extractVersion(line);
                        if (v != null) {
                            version = v;
                            break;
                        }
                    }
                }
                if (version != null) {
                    break;
                }
            }
            return new ToolInfo(tool, path, version, true);
        } catch (Exception e) {
            return new ToolInfo(tool, null, null, false);
        }
    }

    private static String extractVersion(String line) {
        if (line == null) {
            return null;
        }
        // 匹配 主.次.修订 或 主.次 形式版本号
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+\\.\\d+(?:\\.\\d+)?)")
                .matcher(line);
        return m.find() ? m.group(1) : null;
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private int timeoutMs() {
        return Math.max(50, cfg.getToolProbeTimeoutMs());
    }

    /** 执行命令并返回逐行输出；超时或异常返回 null（不抛）。 */
    private String[] run(long timeoutMs, String... command) {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);
        try {
            Process p = pb.start();
            boolean finished = p.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroyForcibly();
                return null;
            }
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = reader.readLine()) != null) {
                    lines.add(l);
                }
            }
            return lines.toArray(new String[0]);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private String[] run(String... command) {
        return run(5000, command); // 默认 5s 兜底（OS/Shell 探测）
    }

    private static String stripQuotes(String s) {
        if (s.length() >= 2) {
            char first = s.charAt(0);
            char last = s.charAt(s.length() - 1);
            if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
                return s.substring(1, s.length() - 1);
            }
        }
        return s;
    }
}
