package io.pragmatic.shell.nlu;

import java.time.Instant;
import java.util.List;

/**
 * 环境指纹快照（环境感知 v5+）：采集一次后不可变，作为稳定的系统上下文注入 LLM。
 * 与多轮对话历史（ConversationContext/ContextTurn）解耦——不进对话历史，避免随轮次滚动丢失。
 */
public final class EnvironmentProfile {

    private final OsInfo os;
    private final ShellInfo shell;
    private final List<ToolInfo> tools;
    private final Instant collectedAt;

    public EnvironmentProfile(OsInfo os, ShellInfo shell, List<ToolInfo> tools, Instant collectedAt) {
        this.os = os;
        this.shell = shell;
        this.tools = List.copyOf(tools);
        this.collectedAt = collectedAt;
    }

    public OsInfo os() {
        return os;
    }

    public ShellInfo shell() {
        return shell;
    }

    public List<ToolInfo> tools() {
        return tools;
    }

    public Instant collectedAt() {
        return collectedAt;
    }

    /** 渲染为注入 LLM 的文本块（仅包含 OS/Shell/工具名与版本等中性信息）。 */
    public String toPromptBlock() {
        StringBuilder sb = new StringBuilder();
        sb.append("OS: ").append(os == null ? "未知" : os.toLine()).append(System.lineSeparator());
        sb.append("Shell: ").append(shell == null ? "未知" : shell.toLine()).append(System.lineSeparator());
        List<ToolInfo> installed = tools.stream().filter(ToolInfo::available).toList();
        List<ToolInfo> missing = tools.stream().filter(t -> !t.available()).toList();
        sb.append("已安装工具: ")
                .append(installed.isEmpty() ? "(无)" : String.join(", ",
                        installed.stream().map(ToolInfo::toLabel).toList()))
                .append(System.lineSeparator());
        sb.append("未安装(勿使用): ")
                .append(missing.isEmpty() ? "(无)" : String.join(", ",
                        missing.stream().map(ToolInfo::name).toList()))
                .append(System.lineSeparator());
        sb.append("约定: 请优先使用本机已安装工具；未列出工具视为不可用。");
        return sb.toString();
    }

    /** 操作系统信息。 */
    public record OsInfo(String family, String name, String version, String kernel, String arch) {
        public String toLine() {
            StringBuilder sb = new StringBuilder();
            sb.append(name == null ? family : name);
            if (version != null) {
                sb.append(' ').append(version);
            }
            if (kernel != null) {
                sb.append(" (").append(kernel);
                if (arch != null) {
                    sb.append(", ").append(arch);
                }
                sb.append(')');
            }
            return sb.toString();
        }
    }

    /** Shell 信息。 */
    public record ShellInfo(String type, String version) {
        public String toLine() {
            return version == null ? String.valueOf(type) : (type + " " + version);
        }
    }

    /** 工具探测结果。 */
    public record ToolInfo(String name, String path, String version, boolean available) {
        public String toLabel() {
            return version == null ? name : (name + " " + version);
        }
    }
}
