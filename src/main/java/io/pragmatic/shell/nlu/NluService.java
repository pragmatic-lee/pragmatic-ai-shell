package io.pragmatic.shell.nlu;

import java.util.List;

/**
 * NLU 服务接口（FR-02 / FR-CTX-03 / 环境感知）。
 * - understand(String)：无上下文调用（v2 行为，每次独立）；
 * - understand(String, List&lt;ContextTurn&gt;)：带多轮上下文调用，模型可引用
 *   历史命令与执行结果（含直通模式轮次，FR-CTX-01-03）。默认实现回退无上下文；
 * - understand(String, List, EnvironmentProfile)：额外注入环境指纹。默认实现忽略 profile。
 */
public interface NluService {
    NluResult understand(String naturalLanguage);

    default NluResult understand(String naturalLanguage, List<ContextTurn> history) {
        return understand(naturalLanguage);
    }

    default NluResult understand(String naturalLanguage, List<ContextTurn> history,
                                 EnvironmentProfile profile) {
        return understand(naturalLanguage, history);
    }
}
