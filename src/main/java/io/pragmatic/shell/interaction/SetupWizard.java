package io.pragmatic.shell.interaction;

import io.pragmatic.shell.config.model.LlmConfig;
import io.pragmatic.shell.config.model.LlmProfile;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * LLM 配置向导（FR-SETUP-01 ~ 04）：纯文本交互，引导录入 / 管理多个模型。
 *
 * <p>设计要点：
 * - **可重复运行**：基于当前已有 profiles 启动，支持增量追加（明天再加模型不会丢今天的）；
 * - **概览 + 菜单**：① 新增 ② 设为默认 ③ 删除 ④ 保存并退出 ⑤ 放弃退出；
 * - apiKey 掩码录入，ollama 本地模型跳过 apiKey；
 * - 只修改内存中的 {@link LlmConfig}（profiles / defaultProfile），落盘由调用方
 *   通过 {@code ConfigWriter} 完成；context / profile 节点原样保留。
 */
public final class SetupWizard {

    private static final String[] PROVIDERS = {"deepseek", "openai", "ollama"};

    private final Terminal terminal;
    private final LineReader reader;
    private final LlmConfig llm;
    /** 工作副本：保存前不污染 llm。 */
    private final List<LlmProfile> working = new ArrayList<>();
    private String defaultProfileId;
    /** 是否已发生变更（决定是否需要写回）。 */
    private boolean changed = false;

    public SetupWizard(Terminal terminal, LineReader reader, LlmConfig llm) {
        this.terminal = terminal;
        this.reader = reader;
        this.llm = llm;
        // 起点：显式 profiles；若无则尝试保留旧版单 llm 写法中可用的 (inline) 项
        if (llm.hasExplicitProfiles()) {
            for (LlmProfile p : llm.getProfiles()) {
                if (p != null) {
                    working.add(copy(p));
                }
            }
        } else {
            LlmProfile inline = llm.findProfile(LlmConfig.INLINE_PROFILE_ID);
            if (inline != null && inline.isUsable()) {
                working.add(copy(inline));
            }
        }
        this.defaultProfileId = llm.getDefaultProfile();
        if (!contains(this.defaultProfileId) && !working.isEmpty()) {
            this.defaultProfileId = working.get(0).getId();
        }
    }

    /**
     * 运行向导主循环。
     *
     * @return true=已产生变更且已写回 llm 对象（需调用方落盘）；false=放弃或无变更
     */
    public boolean run() {
        PrintWriter out = terminal.writer();
        out.println();
        out.println("── LLM 配置向导 ──────────────────────────────");
        out.println("引导配置大模型；支持配置多个并指定默认项。随时按 Ctrl+C 放弃。");
        try {
            while (true) {
                printOverview(out);
                String choice = prompt("请选择 [1-5]", "1").trim();
                switch (choice) {
                    case "1" -> addProfile(out);
                    case "2" -> setDefault(out);
                    case "3" -> removeProfile(out);
                    case "4" -> {
                        if (working.isEmpty()) {
                            out.println("（未配置任何模型，请先选择 1 新增）");
                            continue;
                        }
                        apply();
                        out.println("─────────────────────────────────────────────");
                        out.println("已更新配置（内存中）。执行保存后生效。");
                        return changed;
                    }
                    case "5" -> {
                        out.println("（已放弃，未做任何修改）");
                        return false;
                    }
                    default -> out.println("无效选择，请输入 1-5。");
                }
            }
        } catch (UserInterruptException | EndOfFileException e) {
            out.println();
            out.println("（已取消，未做任何修改）");
            return false;
        }
    }

    /** 打印当前工作副本概览。 */
    private void printOverview(PrintWriter out) {
        out.println();
        if (working.isEmpty()) {
            out.println("当前模型：无");
        } else {
            out.println("当前模型（* 为默认）：");
            for (int i = 0; i < working.size(); i++) {
                LlmProfile p = working.get(i);
                boolean def = p.getId() != null && p.getId().equals(defaultProfileId);
                out.printf("  [%d] %s %s/%s  baseUrl=%s  apiKey=%s  %s%n",
                        i + 1, def ? "*" : " ", p.normalizedProvider(), p.getModel(),
                        p.getBaseUrl(), p.maskedApiKey(),
                        p.isUsable() ? "" : "（不可用：" + p.unusableReason() + "）");
            }
        }
        out.println("  1) 新增模型   2) 设为默认   3) 删除模型   4) 保存并退出   5) 放弃退出");
        out.flush();
    }

    /** 新增一个模型。 */
    private void addProfile(PrintWriter out) {
        String provider = promptProvider(out);
        if (provider == null) {
            return;
        }
        String suggestedId = provider;
        String id = prompt("模型 id（用于 /model switch <id>，需唯一）", suggestedId).trim();
        if (id.isEmpty()) {
            out.println("（id 不能为空，已取消）");
            return;
        }
        if (contains(id)) {
            out.println("（id \"" + id + "\" 已存在，已取消；如需修改请先删除再新增）");
            return;
        }
        String baseUrl = prompt("baseUrl", defaultBaseUrl(provider)).trim();
        String model = prompt("model", defaultModel(provider)).trim();
        if (baseUrl.isEmpty() || model.isEmpty()) {
            out.println("（baseUrl 与 model 均不能为空，已取消）");
            return;
        }
        String apiKey = null;
        if (!"ollama".equals(provider)) {
            apiKey = promptMasked("apiKey（输入不回显）").trim();
            if (apiKey.isEmpty()) {
                out.println("（apiKey 不能为空，已取消；若使用本地模型请选择 ollama）");
                return;
            }
        }
        double temperature = promptDouble("temperature", 0.0);
        int timeout = promptInt("timeoutSeconds（秒）", 60);
        working.add(new LlmProfile(id, provider, baseUrl, model, temperature, apiKey, timeout));
        if (defaultProfileId == null || defaultProfileId.isBlank()) {
            defaultProfileId = id;
        }
        changed = true;
        out.println("（已添加模型 " + id + "）");
    }

    /** 设置默认模型。 */
    private void setDefault(PrintWriter out) {
        if (working.isEmpty()) {
            out.println("（当前无模型）");
            return;
        }
        String input = prompt("输入要设为默认的模型 id 或序号", defaultProfileId == null ? "1" : defaultProfileId)
                .trim();
        LlmProfile target = resolve(input);
        if (target == null) {
            out.println("（未找到该模型）");
            return;
        }
        defaultProfileId = target.getId();
        changed = true;
        out.println("（默认模型已设为 " + defaultProfileId + "）");
    }

    /** 删除模型。 */
    private void removeProfile(PrintWriter out) {
        if (working.isEmpty()) {
            out.println("（当前无模型）");
            return;
        }
        String input = prompt("输入要删除的模型 id 或序号", "").trim();
        LlmProfile target = resolve(input);
        if (target == null) {
            out.println("（未找到该模型）");
            return;
        }
        working.remove(target);
        if (target.getId() != null && target.getId().equals(defaultProfileId)) {
            defaultProfileId = working.isEmpty() ? null : working.get(0).getId();
        }
        changed = true;
        out.println("（已删除模型 " + target.getId() + "）");
    }

    /** 将工作副本写回 llm 对象（profiles / defaultProfile），context / profile 保持不动。 */
    private void apply() {
        List<LlmProfile> copies = new ArrayList<>();
        for (LlmProfile p : working) {
            copies.add(copy(p));
        }
        llm.setProfiles(copies);
        llm.setDefaultProfile(defaultProfileId);
        changed = true;
    }

    // ---------- 交互原语 ----------

    private String promptProvider(PrintWriter out) {
        out.println("选择 provider：  1) deepseek   2) openai   3) ollama（本地，无需 apiKey）");
        out.flush();
        String input = prompt("provider [1-3]", "1").trim();
        return switch (input) {
            case "1" -> "deepseek";
            case "2" -> "openai";
            case "3" -> "ollama";
            default -> {
                if (isValidProvider(input.toLowerCase(Locale.ROOT))) {
                    yield input.toLowerCase(Locale.ROOT);
                }
                out.println("（无效 provider，已取消）");
                yield null;
            }
        };
    }

    /** 普通输入：回车采用默认值。 */
    private String prompt(String label, String def) {
        String text = def == null ? "" : def;
        String line = reader.readLine(label + (text.isEmpty() ? ": " : " [" + text + "]: "));
        return line == null || line.isBlank() ? text : line;
    }

    /** 掩码输入（apiKey）：输入内容以 * 回显。 */
    private String promptMasked(String label) {
        String line = reader.readLine(label + ": ", '*');
        return line == null ? "" : line;
    }

    private double promptDouble(String label, double def) {
        while (true) {
            String s = prompt(label, String.valueOf(def)).trim();
            try {
                double v = Double.parseDouble(s);
                if (v < 0 || v > 2) {
                    terminal.writer().println("（temperature 应在 [0, 2] 之间）");
                    continue;
                }
                return v;
            } catch (NumberFormatException e) {
                terminal.writer().println("（请输入数字）");
            }
        }
    }

    private int promptInt(String label, int def) {
        while (true) {
            String s = prompt(label, String.valueOf(def)).trim();
            try {
                int v = Integer.parseInt(s);
                if (v < 1) {
                    terminal.writer().println("（必须 ≥ 1）");
                    continue;
                }
                return v;
            } catch (NumberFormatException e) {
                terminal.writer().println("（请输入整数）");
            }
        }
    }

    // ---------- 工具 ----------

    /** 按 id 或序号解析模型。 */
    private LlmProfile resolve(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        for (LlmProfile p : working) {
            if (input.equals(p.getId())) {
                return p;
            }
        }
        try {
            int idx = Integer.parseInt(input) - 1;
            if (idx >= 0 && idx < working.size()) {
                return working.get(idx);
            }
        } catch (NumberFormatException ignored) {
            // 非序号，落回 id 匹配失败
        }
        return null;
    }

    private boolean contains(String id) {
        if (id == null) {
            return false;
        }
        for (LlmProfile p : working) {
            if (id.equals(p.getId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isValidProvider(String p) {
        for (String s : PROVIDERS) {
            if (s.equals(p)) {
                return true;
            }
        }
        return false;
    }

    private static String defaultBaseUrl(String provider) {
        return switch (provider) {
            case "openai" -> "https://api.openai.com/v1";
            case "ollama" -> "http://localhost:11434";
            default -> "https://api.deepseek.com/v1";
        };
    }

    private static String defaultModel(String provider) {
        return switch (provider) {
            case "openai" -> "gpt-4o-mini";
            case "ollama" -> "qwen3:8b";
            default -> "deepseek-chat";
        };
    }

    private static LlmProfile copy(LlmProfile p) {
        return new LlmProfile(p.getId(), p.getProvider(), p.getBaseUrl(), p.getModel(),
                p.getTemperature(), p.getApiKey(), p.getTimeoutSeconds());
    }
}
