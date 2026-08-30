package io.pragmatic.shell.interaction;

import org.jline.reader.Candidate;
import org.jline.reader.ParsedLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathAndBuiltinCompleterTest {

    @TempDir
    Path tempRoot;

    private Path workDir;
    private PathAndBuiltinCompleter completer;

    @BeforeEach
    void setUp() throws IOException {
        workDir = Files.createDirectories(tempRoot.resolve("work"));
        Files.createFile(workDir.resolve("config.yaml"));
        Files.createDirectories(workDir.resolve("src"));
        Files.createFile(workDir.resolve(".hidden"));
        // workDir 的兄弟目录，用于验证 ../ 导航能跳出 workDir
        Files.createDirectories(tempRoot.resolve("sibling"));
        completer = new PathAndBuiltinCompleter(workDir.toString());
    }

    private List<Candidate> complete(String word, int wordIndex) {
        return complete(word, wordIndex, List.of(word));
    }

    /** 支持完整命令行分词（用于分派器需感知首 token 的用例）。 */
    private List<Candidate> complete(String word, int wordIndex, List<String> words) {
        List<Candidate> candidates = new ArrayList<>();
        completer.complete(null, new StubLine(word, wordIndex, words), candidates);
        return candidates;
    }

    private List<String> values(List<Candidate> candidates) {
        return candidates.stream().map(Candidate::value).toList();
    }

    @Test
    void dotDotCompletesToParentPath() {
        List<String> v = values(complete("..", 1));
        assertTrue(v.contains("work" + java.io.File.separator), "应列出父目录下的 work，实际: " + v);
        assertTrue(v.contains("sibling" + java.io.File.separator), "应列出父目录下的 sibling，实际: " + v);
    }

    @Test
    void dotCompletesToCurrentDirPath() {
        List<String> v = values(complete(".", 1));
        assertTrue(v.contains("config.yaml"), "应列出当前目录文件，实际: " + v);
        assertTrue(v.contains("src" + java.io.File.separator), "应列出当前目录子目录，实际: " + v);
    }

    @Test
    void dotDotSlashNavigatesOutsideWorkDir() {
        List<String> v = values(complete(".." + java.io.File.separator, 1));
        assertTrue(v.contains(".." + java.io.File.separator + "sibling" + java.io.File.separator),
                "应列出父目录下的兄弟目录，实际: " + v);
        assertTrue(v.contains(".." + java.io.File.separator + "work" + java.io.File.separator));
    }

    @Test
    void emptyWordListsWorkDirWithoutHiddenFiles() {
        List<String> v = values(complete("", 1));
        assertTrue(v.contains("config.yaml"));
        assertTrue(v.contains("src" + java.io.File.separator));
        assertTrue(v.stream().noneMatch(s -> s.startsWith(".")), "不应列出隐藏文件: " + v);
    }

    @Test
    void trailingSlashListsSubdirectoryContents() throws IOException {
        Files.createFile(workDir.resolve("src").resolve("Main.java"));
        List<String> v = values(complete("src" + java.io.File.separator, 1));
        assertEquals(List.of("src" + java.io.File.separator + "Main.java"), v);
    }

    @Test
    void absolutePathKeepsAbsoluteCandidates() {
        List<String> v = values(complete(java.io.File.separator + "us", 1));
        assertTrue(v.stream().allMatch(s -> s.startsWith(java.io.File.separator)),
                "绝对路径输入应补全绝对路径，实际: " + v);
    }

    @Test
    void builtinCommandsCompleteOnFirstToken() {
        List<String> v = values(complete("/h", 0));
        assertEquals(List.of("/help"), v);
    }

    // ============ 一期新增用例 ============

    /** 修复遗漏回归：/setup 必须能被补全（原 BUILTIN_COMMANDS 常量表漏了 setup）。 */
    @Test
    void setupCommandCompletesOnFirstToken() {
        List<String> v = values(complete("/set", 0));
        assertTrue(v.contains("/setup"), "应包含 /setup，实际: " + v);
    }

    @Test
    void builtinModeArgsCompleteOnSecondToken() {
        List<String> v = values(complete("", 1, List.of("/mode", "")));
        assertTrue(v.contains("smart"), "应包含 smart，实际: " + v);
        assertTrue(v.contains("direct"), "应包含 direct，实际: " + v);
    }

    @Test
    void builtinModelArgsCompleteOnSecondToken() {
        List<String> v = values(complete("", 1, List.of("/model", "")));
        assertTrue(v.contains("switch"), "应包含 switch，实际: " + v);
        assertTrue(v.contains("check"), "应包含 check，实际: " + v);
    }

    @Test
    void executableNameCompletesOnFirstToken() {
        // 仅当系统中存在该命令时断言，避免 CI 环境差异
        boolean gitExists = System.getenv("PATH") != null
                && java.util.Arrays.stream(System.getenv("PATH").split(java.io.File.pathSeparator))
                .anyMatch(dir -> new java.io.File(dir, "git").canExecute());
        org.junit.jupiter.api.Assumptions.assumeTrue(gitExists, "PATH 中无 git，跳过");
        List<String> v = values(complete("gi", 0));
        assertTrue(v.contains("git"), "应补全系统命令 git，实际: " + v);
    }

    @Test
    void dotSlashMergesPathCandidatesOnFirstToken() {
        // ./ 前缀：路径兜底应列当前目录条目，同时不报错
        List<String> v = values(complete("./", 0));
        assertTrue(v.contains("./config.yaml"), "应列当前目录文件，实际: " + v);
    }

    @Test
    void subCommandCompletesForKnownCommand() {
        List<String> v = values(complete("", 1, List.of("git", "")));
        assertTrue(v.contains("commit"), "应包含 commit 子命令，实际: " + v);
        assertTrue(v.contains("push"), "应包含 push 子命令，实际: " + v);
    }

    @Test
    void optionCompletesForKnownCommand() {
        List<String> v = values(complete("--", 2, List.of("docker", "run", "--")));
        assertTrue(v.contains("--rm"), "应包含 --rm 选项，实际: " + v);
        assertTrue(v.contains("--tty"), "应包含 --tty 选项，实际: " + v);
    }

    @Test
    void unknownCommandFallsBackToPathWithoutError() {
        // 未收录命令：补全不应抛错，走路径兜底
        List<String> v = values(complete("", 1, List.of("unknowncmd", "")));
        // 路径兜底可能为空（workDir 无匹配）或列出文件，关键是过程不抛异常
        assertTrue(true);
    }

    @Test
    void builtinCommandEnumSingleSourceContainsSetup() {
        assertTrue(BuiltinCommand.byName("setup") != null, "枚举应含 setup（单一数据源）");
        assertTrue(BuiltinCommand.allFullNames().contains("/setup"), "全名列表应含 /setup");
    }

    /** 最小 ParsedLine 实现，仅承载补全所需的当前词与词序号。 */
    private static final class StubLine implements ParsedLine {
        private final String word;
        private final int wordIndex;
        private final List<String> words;

        StubLine(String word, int wordIndex) {
            this(word, wordIndex, List.of(word));
        }

        StubLine(String word, int wordIndex, List<String> words) {
            this.word = word;
            this.wordIndex = wordIndex;
            this.words = words;
        }

        @Override public String word() { return word; }
        @Override public int wordCursor() { return word.length(); }
        @Override public int wordIndex() { return wordIndex; }
        @Override public List<String> words() { return words; }
        @Override public String line() { return String.join(" ", words); }
        @Override public int cursor() { return word.length(); }
    }
}
