package io.pragmatic.shell.interaction;

import io.pragmatic.shell.config.model.ContextConfig;
import io.pragmatic.shell.nlu.ContextTurn;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * 语义模式多轮对话上下文（FR-CTX-01/04）。
 * - 会话内内存历史，退出即清空（不跨会话持久化，FR-CTX-01-02）；
 * - 保留最近 maxTurns 轮，超限丢弃最旧（FR-CTX-04-01）；
 * - enabled=false 时不记录（FR-CTX-08-05：恢复 v2 无上下文行为）。
 */
public final class ConversationContext {

    private final boolean enabled;
    private final int maxTurns;
    private final Deque<ContextTurn> turns = new ArrayDeque<>();

    public ConversationContext(ContextConfig config) {
        this.enabled = config.isEnabled();
        this.maxTurns = Math.max(1, config.getMaxTurns());
    }

    public boolean enabled() {
        return enabled;
    }

    /** 追加一轮；禁用时不记录，超窗口丢弃最旧。 */
    public synchronized void add(ContextTurn turn) {
        if (!enabled) {
            return;
        }
        turns.addLast(turn);
        while (turns.size() > maxTurns) {
            turns.removeFirst();
        }
    }

    /** 清空全部上下文（/clear，FR-CTX-05-01）。 */
    public synchronized void clear() {
        turns.clear();
    }

    /** 当前保留轮次的不可变快照（供 LLM 组装与 /context 展示）。 */
    public synchronized List<ContextTurn> snapshot() {
        return List.copyOf(turns);
    }
}
