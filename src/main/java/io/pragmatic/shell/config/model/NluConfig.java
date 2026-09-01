package io.pragmatic.shell.config.model;

/**
 * 语义理解（NLU）配置（普通 POJO，便于 SnakeYAML 反序列化）。
 *
 * <p>职责边界控制（FR-NJD-01/03）：LLM 除"自然语言 → shell 命令"的翻译职责外，
 * 当前还在 system prompt 层面承担了两项额外审查——可行性与安全性。
 * 在运维等场景下这会造成误伤（如 nginx 不在工具探测清单 → 模型返回 IMPOSSIBLE，
 * 命令根本不生成，人连"它想执行什么"都看不到）。本配置用于按需剥离该审查职责，
 * 把"是否执行"的决定权交回给人。
 */
public class NluConfig {

    /** 工具约束模式：strict=禁用未列出工具（现状）；reference=工具清单仅作参考。 */
    public static final String TOOL_CONSTRAINT_STRICT = "strict";
    public static final String TOOL_CONSTRAINT_REFERENCE = "reference";

    /**
     * 执行判定（FR-NJD-01）：true 时模型可因"有风险/不可行"返回 UNSAFE 或 IMPOSSIBLE；
     * false 时模型只做翻译，不判定能否执行，命令交由人确认（默认，宽松优先）。
     */
    private boolean executionJudgment = false;

    /** 工具约束（FR-NJD-03）：strict=只能使用环境信息中已安装的工具；reference=仅参考（默认）。 */
    private String toolConstraint = TOOL_CONSTRAINT_REFERENCE;

    public NluConfig() {
    }

    public boolean isExecutionJudgment() {
        return executionJudgment;
    }

    public void setExecutionJudgment(boolean executionJudgment) {
        this.executionJudgment = executionJudgment;
    }

    public String getToolConstraint() {
        return toolConstraint;
    }

    public void setToolConstraint(String toolConstraint) {
        this.toolConstraint = toolConstraint;
    }

    /** 工具清单是否作为硬约束（false 表示仅参考，允许使用未列出/未安装的工具）。 */
    public boolean isToolConstraintStrict() {
        return !TOOL_CONSTRAINT_REFERENCE.equalsIgnoreCase(toolConstraint);
    }

    @Override
    public String toString() {
        return "NluConfig{executionJudgment=" + executionJudgment
                + ", toolConstraint='" + toolConstraint + "'}";
    }
}
