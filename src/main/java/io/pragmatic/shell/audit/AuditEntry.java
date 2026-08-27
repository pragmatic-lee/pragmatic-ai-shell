package io.pragmatic.shell.audit;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 审计日志条目（单行 JSON）。
 * model 为可选字段（多模型接入 FR-MLLM-07）：仅语义来源（LLM）命令记录当前激活模型；
 * 直通/状态命令为 null，序列化时不输出（NON_NULL），向后兼容既有 JSON 消费方。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditEntry(
        String ts,        // ISO-8601
        String source,    // LLM / USER
        String input,
        String command,
        int exitCode,
        long durationMs,
        String model      // provider/model，可空
) {
}
