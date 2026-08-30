package io.pragmatic.shell.interaction;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 路径与内置命令补全门面（设计文档 4.5）：
 * 兼容 JLine 历史用法，内部委托 {@link CompleterRouter} 完成实际分派。
 * 路径补全逻辑已抽离至 {@link PathCompleter}，本类不再持有硬编码常量表。
 */
public final class PathAndBuiltinCompleter implements Completer {

    private final CompleterRouter router;

    public PathAndBuiltinCompleter(String workDir) {
        Path abs = Path.of(workDir).toAbsolutePath().normalize();
        this.router = new CompleterRouter(abs);
    }

    public PathAndBuiltinCompleter(Path workDir) {
        this.router = new CompleterRouter(workDir);
    }

    public void setWorkDir(Path workDir) {
        router.setWorkDir(workDir);
    }

    /** 可选预热 PATH 缓存（二期 VariableCompleter 也会用到 envOverrides）。 */
    public void warmUp() {
        router.warmUp();
    }

    /**
     * 二期 VariableCompleter 预留：注入会话环境覆盖表。
     * 一期内部暂存备用，不影响当前补全行为。
     */
    public void setEnvOverrides(Map<String, String> envOverrides) {
        // 预留：一期暂不参与分派
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        router.complete(reader, line, candidates);
    }
}
