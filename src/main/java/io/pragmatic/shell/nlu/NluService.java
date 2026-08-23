package io.pragmatic.shell.nlu;

/**
 * NLU 服务接口。每次调用独立，不传递历史上下文（FR-02-06）。
 */
public interface NluService {
    NluResult understand(String naturalLanguage);
}
