package io.pragmatic.shell.interaction;

import io.pragmatic.shell.config.AppConfig;
import io.pragmatic.shell.config.model.LlmProfile;
import io.pragmatic.shell.nlu.EnvironmentProfile;
import io.pragmatic.shell.nlu.ModelRegistry;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动界面（FR-SPLASH）：纯终端 ANSI 字符线框渲染，全界面无按键交互（v1.1 决议）。
 * 四区纵向排布：品牌横幅 + 环境概览 + 模型展示 + 快捷键提示。
 * 非 ANSI / 非 TTY 环境由调用方跳过（退化为纯文本欢迎语，NFR-02）。
 */
public final class SplashScreen {

    /** 外层内容区显示宽度（display cells，CJK 计 2）。 */
    private static final int OUTER_W = 70;

    private SplashScreen() {
    }

    /**
     * 渲染启动界面。
     * @param mode      本次会话初始模式（语义/直通），用于横幅副标题
     * @param degraded  是否因无可用模型降级直通（true 时展示警告条，隐藏模型区）
     * @param warnings  配置警告文案（逐行 ⚠ 前缀展示，可为空）
     */
    public static void render(PrintWriter out, AppConfig config, ModelRegistry registry,
                              EnvironmentProfile env, ShellMode mode,
                              boolean degraded, List<String> warnings) {
        List<String> rows = new ArrayList<>();

        // ① 品牌横幅（logo 字符画 + 产品名/版本/定位）
        rows.addAll(banner());
        rows.add(null); // 分隔（render 时转 section 线）

        // ② 环境概览
        rows.add("环境概览");
        rows.addAll(envSection(config, env, mode));
        if (warnings != null && !warnings.isEmpty()) {
            rows.add(null);
            for (String w : warnings) {
                rows.add("⚠ " + firstLine(w));
            }
        }

        // ③ 模型展示区 / 降级警告条
        rows.add(null);
        if (degraded || registry.activeProfile() == null) {
            rows.add("⚠ LLM 未配置或不可用（apiKey/baseUrl/model 缺失），本次以直通模式运行");
        } else if (registry.isMulti()) {
            rows.addAll(modelSectionMulti(registry));
        } else {
            rows.addAll(modelSectionSingle(registry));
        }

        // ④ 快捷键提示
        rows.add(null);
        rows.add("输入 /help 查看帮助，! 开头直接进入直通模式");

        printBoxed(out, rows);
        out.flush();
    }

    /* ================= 区块内容 ================= */

    private static List<String> banner() {
        String[] art = {
                "  ╔═══╗",
                "  ║ >_╝   Smart CLI  v1.0",
                "  ╚═══╝   基于 LLM 的智能命令行工具 · 语义/直通双模式",
        };
        List<String> rows = new ArrayList<>();
        for (String a : art) {
            rows.add(a);
        }
        return rows;
    }

    private static List<String> envSection(AppConfig config, EnvironmentProfile env, ShellMode mode) {
        List<String> rows = new ArrayList<>();
        String os = env != null && env.os() != null ? env.os().toLine() : "未知";
        String shell = env != null && env.shell() != null ? env.shell().toLine() : "未知";
        rows.add("  系统: " + os + "      Shell: " + shell);
        rows.add("  工作目录: " + shortenHome(config.getExecution().getWorkDir())
                + "      模式: " + (mode == ShellMode.SMART ? "语义" : "直通"));
        var logging = config.getLogging();
        rows.add("  审计日志: " + (logging.isAuditEnabled() ? "开启" : "关闭")
                + "      只读: " + (config.getExecution().isReadOnly() ? "是" : "否"));
        var safety = config.getSafety();
        rows.add("  安全策略: 危险命令确认 " + mark(safety.isConfirmDestructive())
                + " · 内网拦截 " + mark(safety.isBlockPrivateAddresses())
                + " · 严格模式 " + mark(safety.isStrictMode()));
        return rows;
    }

    private static List<String> modelSectionMulti(ModelRegistry registry) {
        LlmProfile active = registry.activeProfile();
        List<String> inner = new ArrayList<>();
        inner.add("  模型（当前: " + active.getId() + " · 默认激活）");
        inner.add("  ┌" + "─".repeat(62) + "┐");
        for (LlmProfile p : registry.profiles()) {
            String check = p == active ? "✓" : " ";
            String status = p.isUsable()
                    ? (p == active ? "可用 (默认)" : "可用")
                    : p.unusableReason();
            String content = "  │ " + check + " " + pad(p.getId(), 11) + pad(p.displayLabel(), 30)
                    + status + "  │";
            inner.add(content);
        }
        inner.add("  │ 切换模型：/model switch <id> · 健康检查：/model check [id]      │");
        inner.add("  └" + "─".repeat(62) + "┘");
        return inner;
    }

    private static List<String> modelSectionSingle(ModelRegistry registry) {
        LlmProfile active = registry.activeProfile();
        List<String> rows = new ArrayList<>();
        rows.add("  当前模型: " + active.getId() + " (" + active.displayLabel()
                + " · timeout " + active.getTimeoutSeconds() + "s)");
        rows.add("  多模型接入：在 config.yaml 的 llm.profiles 中声明更多模型");
        return rows;
    }

    /* ================= 边框渲染 ================= */

    /** 将内容行用统一外框包裹；内容为 null 表示 section 分隔线。 */
    private static void printBoxed(PrintWriter out, List<String> rows) {
        out.println("┌" + "─".repeat(OUTER_W) + "┐");
        for (String r : rows) {
            if (r == null) {
                out.println("├" + "─".repeat(OUTER_W) + "┤");
            } else {
                out.println("│ " + fit(r, OUTER_W - 2) + " │");
            }
        }
        out.println("└" + "─".repeat(OUTER_W) + "┘");
    }

    /* ================= 宽度工具（CJK 计 2） ================= */

    /** 截断/补齐到目标显示宽度。 */
    private static String fit(String s, int target) {
        StringBuilder sb = new StringBuilder();
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int cw = wide(c) ? 2 : 1;
            if (w + cw > target) {
                break;
            }
            sb.append(c);
            w += cw;
        }
        while (w < target) {
            sb.append(' ');
            w++;
        }
        return sb.toString();
    }

    /** 右侧补空格到固定显示宽度（用于列对齐，不截断）。 */
    private static String pad(String s, int width) {
        int w = displayWidth(s);
        StringBuilder sb = new StringBuilder(s);
        while (w < width) {
            sb.append(' ');
            w++;
        }
        return sb.toString();
    }

    private static int displayWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += wide(s.charAt(i)) ? 2 : 1;
        }
        return w;
    }

    private static boolean wide(char c) {
        return (c >= 0x1100 && c <= 0x115F)          // Hangul Jamo
                || (c >= 0x2E80 && c <= 0x303E)      // CJK 部首/假名标点
                || (c >= 0x3041 && c <= 0x33FF)      // 平假名/片假名/CJK 兼容
                || (c >= 0x3400 && c <= 0x4DBF)      // CJK 扩展 A
                || (c >= 0x4E00 && c <= 0x9FFF)      // CJK 统一表意
                || (c >= 0xA000 && c <= 0xA4CF)      // 彝文
                || (c >= 0xAC00 && c <= 0xD7A3)      // 韩文音节
                || (c >= 0xF900 && c <= 0xFAFF)      // CJK 兼容表意
                || (c >= 0xFE30 && c <= 0xFE4F)      // CJK 兼容形式
                || (c >= 0xFF00 && c <= 0xFF60)      // 全角形式
                || (c >= 0xFFE0 && c <= 0xFFE6);
    }

    /* ================= 杂项 ================= */

    private static String mark(boolean b) {
        return b ? "✓" : "✗";
    }

    private static String firstLine(String s) {
        int idx = s.indexOf('\n');
        return idx < 0 ? s : s.substring(0, idx);
    }

    private static String shortenHome(String path) {
        String home = System.getProperty("user.home");
        if (home != null && path != null && path.startsWith(home)) {
            return "~" + path.substring(home.length());
        }
        return path;
    }
}
