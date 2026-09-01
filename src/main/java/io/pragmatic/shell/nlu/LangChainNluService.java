package io.pragmatic.shell.nlu;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 LangChain4j 的 NLU 实现（FR-02 / 多模型接入 FR-MLLM-03）。
 * - 持有 {@link ModelRegistry}，每次调用取当前激活 Profile 对应的客户端（切换即时生效）；
 * - system prompt 要求只返回纯命令或 UNSAFE / IMPOSSIBLE；
 * - temperature 由各 Profile 自行控制（默认 0.0 保证稳定）。
 */
public final class LangChainNluService implements NluService {

    private final ModelRegistry registry;
    private final String systemPrompt;
    private final EnvironmentProfile profile;
    private final ExecutionJudgment judgment;

    public LangChainNluService(ModelRegistry registry) {
        this(registry, null);
    }

    public LangChainNluService(ModelRegistry registry, EnvironmentProfile profile) {
        this(registry, profile, ExecutionJudgment.enabled());
    }

    public LangChainNluService(ModelRegistry registry, EnvironmentProfile profile,
                               ExecutionJudgment judgment) {
        this.registry = registry;
        this.judgment = judgment == null ? ExecutionJudgment.enabled() : judgment;
        this.systemPrompt = loadSystemPrompt(this.judgment.executionJudgment());
        this.profile = profile;
    }

    /**
     * 按 executionJudgment 选择 prompt：
     * - true（默认，现状）：模型可返回 UNSAFE / IMPOSSIBLE；
     * - false（FR-NJD-01）：模型只翻译，唯一拒绝出口为 UNTRANSLATABLE。
     */
    /** 包内可见以便单测断言 prompt 选择（FR-NJD-01）。 */
    static String loadSystemPrompt(boolean executionJudgment) {
        String resource = executionJudgment
                ? "/prompts/nlu-system-prompt.txt"
                : "/prompts/nlu-system-prompt-translate-only.txt";
        try (var in = LangChainNluService.class.getResourceAsStream(resource)) {
            if (in != null) {
                return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
        // 兜底：资源缺失时不阻断启动，退回内置文本
        return executionJudgment
                ? "你是一个 shell 命令生成器。用户用自然语言描述操作意图，你只返回一条可执行的 shell 命令纯文本，"
                        + "不要任何解释、不要 markdown 代码块。如果请求不安全返回 UNSAFE，如果不可能完成返回 IMPOSSIBLE。"
                : "你是一个 shell 命令翻译器。用户用自然语言描述操作意图，你只返回一条对应的 shell 命令纯文本，"
                        + "不要任何解释、不要 markdown 代码块。只做翻译，不判断命令能否执行成功、是否有风险；"
                        + "仅当请求无法转换为任何 shell 命令时返回 UNTRANSLATABLE。";
    }

    @Override
    public NluResult understand(String naturalLanguage) {
        return understand(naturalLanguage, List.of());
    }

    @Override
    public NluResult understand(String naturalLanguage, List<ContextTurn> history) {
        return understand(naturalLanguage, history, this.profile);
    }

    @Override
    public NluResult understand(String naturalLanguage, List<ContextTurn> history,
                                EnvironmentProfile profile) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        if (profile != null) {
            messages.add(new SystemMessage(judgment.envInfoHeader()
                    + System.lineSeparator() + profile.toPromptBlock()));
        }
        for (ContextTurn turn : history) {
            appendTurn(messages, turn);
        }
        messages.add(new UserMessage(naturalLanguage));
        var response = registry.currentModel().generate(messages);
        AiMessage msg = response.content();
        if (msg == null || msg.text() == null) {
            return NluResult.impossible();
        }
        return parseResponse(msg.text(), judgment);
    }

    /**
     * 将模型原始输出解析为 {@link NluResult}（包内可见以便单测，无需真实 LLM 调用）。
     *
     * <p>执行判定开启时（现状）：UNSAFE / IMPOSSIBLE 原样返回，由调用方提示"模型拒绝"。
     *
     * <p>执行判定关闭时（FR-NJD-01/02）：模型的自我审查一律无效化——
     * UNSAFE / IMPOSSIBLE 只按"翻译失败"处理，绝不允许模型的风险判断阻断命令展示
     * （是否执行由人决定）。此时唯一有效的拒绝出口是 UNTRANSLATABLE，
     * 语义严格限定为"无法转换为任何 shell 命令"。
     */
    static NluResult parseResponse(String rawText, ExecutionJudgment judgment) {
        ExecutionJudgment j = judgment == null ? ExecutionJudgment.enabled() : judgment;
        String text = rawText == null ? "" : rawText.trim();
        String cmd = stripFence(text).trim();
        if (cmd.isBlank()) {
            return NluResult.impossible();
        }
        if (text.equalsIgnoreCase("UNTRANSLATABLE")) {
            return NluResult.impossible();
        }
        if (!j.executionJudgment()) {
            if (text.equalsIgnoreCase("UNSAFE") || text.equalsIgnoreCase("IMPOSSIBLE")) {
                return NluResult.impossible();
            }
            return NluResult.command(cmd);
        }
        if (text.equalsIgnoreCase("UNSAFE")) {
            return NluResult.unsafe();
        }
        if (text.equalsIgnoreCase("IMPOSSIBLE")) {
            return NluResult.impossible();
        }
        return NluResult.command(cmd);
    }

    /**
     * 一轮历史组装（FR-CTX-03）：用户输入 → UserMessage，命令 → AiMessage，
     * 执行结果/拒绝原因 → SystemMessage 结果块。语义与直通轮次统一处理。
     */
    private void appendTurn(List<ChatMessage> messages, ContextTurn turn) {
        messages.add(new UserMessage(turn.userInput()));
        if (turn.command() != null && !turn.command().isBlank()) {
            messages.add(new AiMessage(turn.command()));
        }
        if (turn.rejectReason() != null) {
            messages.add(new SystemMessage("上一条命令被安全策略拒绝：" + turn.rejectReason()
                    + "。禁止生成规避安全策略的命令，应改用安全的等价方式或返回 IMPOSSIBLE。"));
            return;
        }
        if (turn.resultSummary() != null && !turn.resultSummary().isBlank()) {
            String block = "命令执行结果（退出码 " + turn.exitCode()
                    + (turn.timedOut() ? "，超时被终止" : "") + "）：" + System.lineSeparator()
                    + turn.resultSummary();
            messages.add(new SystemMessage(block));
        }
    }

    private static String stripFence(String text) {
        String t = text;
        if (t.startsWith("```")) {
            int end = t.lastIndexOf("```");
            t = end > 3 ? t.substring(3, end) : t.substring(3);
        }
        // 去除首行可能的语言标记（sh/bash）
        int firstNewline = t.indexOf('\n');
        if (firstNewline > 0) {
            String firstLine = t.substring(0, firstNewline).trim();
            if (firstLine.matches("^(sh|bash|shell|console)$")) {
                t = t.substring(firstNewline + 1);
            }
        }
        return t.trim();
    }
}
