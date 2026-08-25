package io.pragmatic.shell.nlu;

/**
 * 多轮对话上下文轮次（FR-CTX-01）。
 * - source：LLM（语义模式生成）/ DIRECT（直通执行，含状态类命令）；
 * - 所有文本字段在构造时已脱敏（SecretMasker，FR-CTX-06-01），
 *   /context 展示与 LLM 组装使用的都是打码后的值。
 */
public record ContextTurn(
        String source,          // LLM / DIRECT
        String userInput,       // 语义轮次为自然语言；直通轮次为含 ! 前缀的原始输入
        String command,         // 语义轮次为模型生成命令；直通轮次为原样命令（状态命令为 null）
        String resultSummary,   // 执行结果摘要（截断后，可空）
        int exitCode,           // 退出码（-1 超时/被拦截/未执行）
        boolean timedOut,       // 是否超时
        String rejectReason     // 被安全策略拒绝的原因（可空）
) {

    /** 语义模式：命令已执行完成。 */
    public static ContextTurn completed(String userInput, String command, String resultSummary,
                                        int exitCode, boolean timedOut) {
        return new ContextTurn("LLM", mask(userInput), mask(command), mask(resultSummary),
                exitCode, timedOut, null);
    }

    /** 直通模式：命令已执行完成（含 ! 前缀与 DIRECT 模式）。 */
    public static ContextTurn direct(String userInput, String command, String resultSummary,
                                     int exitCode, boolean timedOut) {
        return new ContextTurn("DIRECT", mask(userInput), mask(command), mask(resultSummary),
                exitCode, timedOut, null);
    }

    /** 状态类命令：REPL 层进程内处理（仅摘要，不审计，FR-CTX-08-04）。 */
    public static ContextTurn stateCommand(String command, String summary) {
        return new ContextTurn("DIRECT", mask(command), null, mask(summary), 0, false, null);
    }

    /** 被安全过滤拦截：标注拒绝原因，模型不得规避安全策略（FR-CTX-01-04）。 */
    public static ContextTurn rejected(String source, String userInput, String command, String reason) {
        return new ContextTurn(source, mask(userInput), mask(command), null, -1, false, mask(reason));
    }

    /** 模型判定失败（UNSAFE/IMPOSSIBLE）或 LLM 调用超时：仅用户输入与结论，防止模型重复尝试。 */
    public static ContextTurn modelRefused(String userInput, String reason) {
        return new ContextTurn("LLM", mask(userInput), null, mask(reason), -1, false, null);
    }

    /** 用户跳过执行。 */
    public static ContextTurn skipped(String source, String userInput, String command) {
        return new ContextTurn(source, mask(userInput), mask(command), "（用户跳过执行）", -1, false, null);
    }

    private static String mask(String s) {
        return SecretMasker.mask(s);
    }
}
