package io.pragmatic.shell.interaction;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 直通模式 Tab 补全：
 * - 当输入以 "/" 开头的首个 token 时，补全内置命令（help/exit/quit/mode/config）。
 * - 其余情况基于执行工作目录（config.execution.workDir）做文件路径/目录名补全，
 *   自动列出多个候选，行为对齐原生命令行。
 */
public final class PathAndBuiltinCompleter implements Completer {

    private static final List<String> BUILTIN_COMMANDS =
            List.of("/help", "/exit", "/quit", "/mode", "/config", "/model", "/context", "/clear", "/profile");

    private volatile Path workDir;

    public PathAndBuiltinCompleter(String workDir) {
        this.workDir = Path.of(workDir == null || workDir.isBlank() ? "." : workDir)
                .toAbsolutePath().normalize();
    }

    public PathAndBuiltinCompleter(Path workDir) {
        this.workDir = workDir.toAbsolutePath().normalize();
    }

    /** 运行时更新补全基准目录（REPL 的 cd 切换后调用）。 */
    public void setWorkDir(Path workDir) {
        this.workDir = workDir.toAbsolutePath().normalize();
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String word = line.word();
        int wordIndex = line.wordIndex();

        // 首个 token 以 / 开头 -> 内置命令补全
        if (wordIndex == 0 && word != null && word.startsWith("/")) {
            for (String cmd : BUILTIN_COMMANDS) {
                if (cmd.startsWith(word)) {
                    candidates.add(new Candidate(cmd));
                }
            }
            return;
        }

        // 路径补全：在 workDir 下查找匹配文件/目录（支持 ../ 向上导航到 workDir 之外）
        String safeWord = word == null ? "" : word;
        boolean absoluteInput = !safeWord.isEmpty() && Path.of(safeWord).isAbsolute();
        Path baseDir;
        String prefix;
        if (safeWord.isEmpty()) {
            prefix = "";
            baseDir = workDir;
        } else {
            boolean trailingSep = safeWord.endsWith(File.separator);
            Path resolved = (absoluteInput ? Path.of(safeWord) : workDir.resolve(safeWord)).normalize();
            if (trailingSep) {
                baseDir = resolved;
                prefix = "";
            } else {
                // 基于用户输入的原始末段判断（不能用 normalize 后的名字，. / .. 会被抹掉）
                int idx = safeWord.lastIndexOf(File.separatorChar);
                String lastSegment = idx >= 0 ? safeWord.substring(idx + 1) : safeWord;
                // "." / ".." 直接给出 ./、../ 候选，支持向上导航到 workDir 之外
                if (lastSegment.equals(".") || lastSegment.equals("..")) {
                    String value = safeWord + File.separator;
                    candidates.add(new Candidate(value, value, null, null, null, null, true));
                    return;
                }
                baseDir = resolved.getParent();
                prefix = resolved.getFileName() == null ? "" : resolved.getFileName().toString();
            }
        }

        File dir = Objects.requireNonNullElse(baseDir, Path.of("/")).toFile();
        if (!dir.isDirectory()) {
            return;
        }
        // 对齐原生 shell：前缀不以 . 开头时不列出隐藏文件
        boolean showHidden = prefix.startsWith(".");
        File[] files = dir.listFiles((d, name) ->
                name.startsWith(prefix) && (showHidden || !name.startsWith(".")));
        if (files == null) {
            return;
        }
        for (File f : files) {
            String value;
            if (absoluteInput) {
                value = f.getAbsolutePath();
            } else {
                value = dir.getAbsolutePath().equals(workDir.toFile().getAbsolutePath())
                        ? f.getName() : workDir.relativize(f.toPath()).toString();
                // 列父目录时 workDir 自身 relativize 为空串，回退为 ../<目录名>
                if (value.isEmpty()) {
                    value = ".." + File.separator + f.getName();
                }
            }
            String display = value + (f.isDirectory() ? File.separator : "");
            candidates.add(new Candidate(
                    display, display, null, null, null,
                    null, f.isDirectory()));
        }
    }
}
