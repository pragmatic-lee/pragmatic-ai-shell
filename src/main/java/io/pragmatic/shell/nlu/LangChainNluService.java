package io.pragmatic.shell.nlu;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import io.pragmatic.shell.config.AppConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 LangChain4j 的 NLU 实现（FR-02）。
 * - 按 provider 选择后端（deepseek / openai / ollama）
 * - system prompt 要求只返回纯命令或 UNSAFE / IMPOSSIBLE
 * - temperature=0.0 保证稳定
 */
public final class LangChainNluService implements NluService {

    private final ChatLanguageModel model;
    private final String systemPrompt;
    private final EnvironmentProfile profile;

    public LangChainNluService(AppConfig config) {
        this(config, null);
    }

    public LangChainNluService(AppConfig config, EnvironmentProfile profile) {
        this.model = buildModel(config);
        this.systemPrompt = loadSystemPrompt();
        this.profile = profile;
    }

    private ChatLanguageModel buildModel(AppConfig config) {
        var llm = config.getLlm();
        Duration timeout = Duration.ofSeconds(llm.getTimeoutSeconds());
        return switch (LlmProvider.valueOf(llm.getProvider().toUpperCase())) {
            case OLLAMA -> OllamaChatModel.builder()
                    .baseUrl(llm.getBaseUrl())
                    .modelName(llm.getModel())
                    .temperature(llm.getTemperature())
                    .timeout(timeout)
                    .build();
            case DEEPSEEK, OPENAI -> OpenAiChatModel.builder()
                    .baseUrl(llm.getBaseUrl())
                    .apiKey(llm.getApiKey())
                    .modelName(llm.getModel())
                    .temperature(llm.getTemperature())
                    .timeout(timeout)
                    .build();
        };
    }

    private String loadSystemPrompt() {
        try (var in = LangChainNluService.class
                .getResourceAsStream("/prompts/nlu-system-prompt.txt")) {
            if (in != null) {
                return new String(in.readAllBytes());
            }
        } catch (Exception ignored) {
        }
        return "你是一个 shell 命令生成器。用户用自然语言描述操作意图，你只返回一条可执行的 shell 命令纯文本，"
                + "不要任何解释、不要 markdown 代码块。如果请求不安全返回 UNSAFE，如果不可能完成返回 IMPOSSIBLE。";
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
            messages.add(new SystemMessage("当前环境信息（生成命令时请遵守）："
                    + System.lineSeparator() + profile.toPromptBlock()));
        }
        for (ContextTurn turn : history) {
            appendTurn(messages, turn);
        }
        messages.add(new UserMessage(naturalLanguage));
        var response = model.generate(messages);
        AiMessage msg = response.content();
        if (msg == null || msg.text() == null) {
            return NluResult.impossible();
        }
        String text = msg.text().trim();
        if (text.equalsIgnoreCase("UNSAFE")) {
            return NluResult.unsafe();
        }
        if (text.equalsIgnoreCase("IMPOSSIBLE")) {
            return NluResult.impossible();
        }
        // 去除可能被模型包裹的 ``` 代码围栏
        String cmd = stripFence(text).trim();
        if (cmd.isBlank()) {
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

    private String stripFence(String text) {
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
