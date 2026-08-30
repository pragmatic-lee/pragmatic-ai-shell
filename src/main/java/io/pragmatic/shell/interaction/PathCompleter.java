package io.pragmatic.shell.interaction;

import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.jline.utils.AttributedString;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件系统路径补全（设计文档 4.1）：
 * 从 {@link PathAndBuiltinCompleter} 原样抽离的路径逻辑，行为保持不变。
 * 支持 .. / . 导航、隐藏文件规则、绝对路径候选（/usr、~/... 等）。
 */
public final class PathCompleter {

    private volatile Path workDir;

    public PathCompleter(String workDir) {
        this.workDir = Path.of(workDir).toAbsolutePath().normalize();
    }

    public PathCompleter(Path workDir) {
        this.workDir = workDir.toAbsolutePath().normalize();
    }

    public void setWorkDir(Path workDir) {
        this.workDir = workDir.toAbsolutePath().normalize();
    }

    /** 仅针对当前 word 做路径补全（供 CompleterRouter 兜底使用，不依赖完整 ParsedLine）。 */
    public void completeCurrentWordOnly(String word, List<Candidate> candidates) {
        completeWord(word, candidates);
    }

    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        completeWord(line.word(), candidates);
    }

    private void completeWord(String word, List<Candidate> candidates) {
        String trimmed = word.replaceAll("\\s+$", "");
        List<String> matches = new ArrayList<>();

        if (trimmed.equals("..") || trimmed.equals(".")) {
            // 只列目录，补全后追加 /，且隐藏文件排除（还原原始行为：候选不带前缀）
            Path base = trimmed.equals("..")
                    ? workDir.getParent() != null ? workDir.getParent() : workDir
                    : workDir;
            addDirectoryChildren(base, matches, /* includeHidden */ false);
            produce(matches, candidates);
            return;
        }

        if (trimmed.isEmpty()) {
            // 空词：列 workDir 下条目，不显隐藏文件
            addDirectoryChildren(workDir, matches, false);
            produce(matches, candidates);
            return;
        }

        if (trimmed.endsWith("/")) {
            // 已是目录：列其子目录，候选保留已输入前缀
            Path dir = resolve(trimmed);
            addDirectoryChildren(dir, matches, false);
            List<String> withPrefix = new ArrayList<>();
            for (String m : matches) {
                withPrefix.add(trimmed + m);
            }
            produce(withPrefix, candidates);
            return;
        }

        // 普通前缀：相对 workDir 匹配，候选保留目录前缀
        Path dir = workDir;
        String prefix = trimmed;
        int lastSlash = trimmed.lastIndexOf('/');
        if (lastSlash >= 0) {
            dir = resolve(trimmed.substring(0, lastSlash + 1));
            prefix = trimmed.substring(lastSlash + 1);
        }
        addMatchingChildren(dir, prefix, matches, false);
        String dirPrefix = lastSlash >= 0 ? trimmed.substring(0, lastSlash + 1) : "";
        List<String> withPrefix = new ArrayList<>();
        for (String m : matches) {
            withPrefix.add(dirPrefix + m);
        }
        produce(withPrefix, candidates);
    }

    private Path resolve(String p) {
        Path path = Path.of(p);
        if (path.isAbsolute()) {
            return path;
        }
        if (p.startsWith("~" + File.separator) || p.equals("~")) {
            String home = System.getProperty("user.home");
            String rest = p.equals("~") ? "" : p.substring(2);
            return Path.of(home, rest);
        }
        return workDir.resolve(p);
    }

    private void addMatchingChildren(Path dir, String prefix, List<String> out, boolean includeHidden) {
        File dirFile = dir.toFile();
        if (!dirFile.isDirectory()) {
            return;
        }
        File[] files = dirFile.listFiles((d, name) -> {
            if (!includeHidden && name.startsWith(".")) {
                return false;
            }
            return name.startsWith(prefix);
        });
        if (files == null) {
            return;
        }
        for (File f : files) {
            out.add(f.getName() + (f.isDirectory() ? "/" : ""));
        }
    }

    private void addDirectoryChildren(Path dir, List<String> out, boolean includeHidden) {
        File dirFile = dir.toFile();
        if (!dirFile.isDirectory()) {
            return;
        }
        File[] files = dirFile.listFiles((d, name) -> includeHidden || !name.startsWith("."));
        if (files == null) {
            return;
        }
        for (File f : files) {
            out.add(f.getName() + (f.isDirectory() ? "/" : ""));
        }
    }

    private void produce(List<String> matches, List<Candidate> candidates) {
        for (String m : matches) {
            candidates.add(new Candidate(
                    AttributedString.stripAnsi(m),
                    m,
                    null, null, null, null, true));
        }
    }
}
