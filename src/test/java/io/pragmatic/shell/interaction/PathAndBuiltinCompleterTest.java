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
        List<Candidate> candidates = new ArrayList<>();
        completer.complete(null, new StubLine(word, wordIndex), candidates);
        return candidates;
    }

    private List<String> values(List<Candidate> candidates) {
        return candidates.stream().map(Candidate::value).toList();
    }

    @Test
    void dotDotCompletesToParentPath() {
        List<String> v = values(complete("..", 1));
        assertEquals(List.of(".." + java.io.File.separator), v);
    }

    @Test
    void dotCompletesToCurrentDirPath() {
        List<String> v = values(complete(".", 1));
        assertEquals(List.of("." + java.io.File.separator), v);
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

    /** 最小 ParsedLine 实现，仅承载补全所需的当前词与词序号。 */
    private static final class StubLine implements ParsedLine {
        private final String word;
        private final int wordIndex;

        StubLine(String word, int wordIndex) {
            this.word = word;
            this.wordIndex = wordIndex;
        }

        @Override public String word() { return word; }
        @Override public int wordCursor() { return word.length(); }
        @Override public int wordIndex() { return wordIndex; }
        @Override public List<String> words() { return List.of(word); }
        @Override public String line() { return word; }
        @Override public int cursor() { return word.length(); }
    }
}
