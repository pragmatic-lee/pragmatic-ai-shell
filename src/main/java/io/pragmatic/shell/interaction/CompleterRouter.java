package io.pragmatic.shell.interaction;

import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.jline.utils.AttributedString;

import java.nio.file.Path;
import java.util.List;

/**
 * 补全分派器（设计文档 4.5 / G1）：
 * 依据 ParsedLine 的 wordIndex 与 token 特征，把补全请求分派给
 * 内置命令 / 系统可执行 / 子命令-选项 / 路径 四类补全器。
 *
 * <p>分派优先级：
 * <pre>
 * wordIndex == 0:
 *   - 以 / 开头         → 内置命令全名（/xxx）
 *   - 以 $ 开头         → 一期无 VariableCompleter，转路径兜底
 *   - 其他             → 系统命令名（Executable，合并 Path 兜底，兼容 ./script）
 * wordIndex >= 1:
 *   - 首 token 命中内置 → 第 1 参数位返回参数候选，否则路径兜底
 *   - 当前 word 以 -    → 首 token 对应命令的选项候选
 *   - 首 token 在规格表 且 wordIndex==1 → 子命令候选
 *   - 其他             → 路径兜底
 * </pre>
 */
public final class CompleterRouter {

    private final ExecutableCompleter executable = new ExecutableCompleter();
    private final PathCompleter path;
    private volatile Path workDir;

    public CompleterRouter(Path workDir) {
        this.workDir = workDir.toAbsolutePath().normalize();
        this.path = new PathCompleter(this.workDir);
    }

    public void setWorkDir(Path workDir) {
        this.workDir = workDir.toAbsolutePath().normalize();
        this.path.setWorkDir(this.workDir);
    }

    /** 可选预热：异步触发 PATH 扫描缓存，避免首次 Tab 卡顿。 */
    public void warmUp() {
        executable.warmUp();
    }

    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String word = line.word();
        int wordIndex = line.wordIndex();
        List<String> words = line.words();

        if (wordIndex == 0) {
            completeFirstToken(word, candidates);
            return;
        }
        completeArgument(words, wordIndex, word, candidates);
    }

    private void completeFirstToken(String word, List<Candidate> candidates) {
        if (word.startsWith("/")) {
            String prefix = word.substring(1);
            for (String name : BuiltinCommand.allFullNames()) {
                if (name.startsWith("/" + prefix)) {
                    candidates.add(new Candidate(
                            AttributedString.stripAnsi(name), name, null, null, null, null, true));
                }
            }
            return;
        }
        if (word.startsWith("$")) {
            // 一期：无 VariableCompleter，转路径兜底
            path.completeCurrentWordOnly(word, candidates);
            return;
        }
        // 系统命令名（合并路径兜底，兼容 ./script.sh 相对执行）
        for (String name : executable.complete(word)) {
            candidates.add(new Candidate(
                    AttributedString.stripAnsi(name), name, null, null, null, null, true));
        }
        path.completeCurrentWordOnly(word, candidates);
    }

    private void completeArgument(List<String> words, int wordIndex, String word, List<Candidate> candidates) {
        String first = words.isEmpty() ? "" : words.get(0);

        // 内置命令参数候选
        if (first.startsWith("/")) {
            BuiltinCommand cmd = BuiltinCommand.byName(first.substring(1));
            if (cmd != null) {
                if (wordIndex == 1) {
                    addCandidates(cmd.args(), word, candidates);
                    return;
                }
                path.completeCurrentWordOnly(word, candidates);
                return;
            }
        }

        // 选项候选
        if (word.startsWith("-")) {
            CommandSpec spec = CommandSpecs.forCommand(first);
            addCandidates(spec.options(), word, candidates);
            return;
        }

        // 子命令候选（wordIndex == 1 且首 token 在规格表）
        if (wordIndex == 1) {
            CommandSpec spec = CommandSpecs.forCommand(first);
            if (spec != CommandSpec.NONE) {
                addCandidates(spec.subcommands(), word, candidates);
                return;
            }
        }

        // 兜底：路径补全
        path.completeCurrentWordOnly(word, candidates);
    }

    private void addCandidates(List<String> source, String word, List<Candidate> candidates) {
        String prefix = word == null ? "" : word;
        for (String s : source) {
            if (prefix.isEmpty() || s.startsWith(prefix)) {
                candidates.add(new Candidate(
                        AttributedString.stripAnsi(s), s, null, null, null, null, true));
            }
        }
    }
}
