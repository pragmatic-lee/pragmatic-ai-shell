package io.pragmatic.shell.nlu;

import io.pragmatic.shell.config.model.NluConfig;

/**
 * LLM 职责边界（FR-NJD-01/03），与配置解耦，便于单测直接构造。
 *
 * <p>当前 LLM 除翻译外还承担两项审查，在运维等场景会造成误伤
 * （如 nginx 不在工具探测清单 → 模型返回 IMPOSSIBLE，命令根本不生成，
 * 人连"它想执行什么"都看不到）。本类型用于按需剥离这些审查职责。
 *
 * @param executionJudgment true=模型可因"有风险/不可行"返回 UNSAFE / IMPOSSIBLE（现状行为）；
 *                          false=模型只做翻译，不判定能否执行，命令交由人确认
 * @param toolConstraintStrict true=只能使用环境信息中已安装的工具（现状）；false=工具清单仅作参考
 */
public record ExecutionJudgment(boolean executionJudgment, boolean toolConstraintStrict) {

    /** 默认：宽松模式（执行判定关闭、工具约束仅参考），零配置开箱即用。 */
    public static ExecutionJudgment enabled() {
        return new ExecutionJudgment(false, false);
    }

    /** 由配置构造；配置缺失时回退默认值。 */
    public static ExecutionJudgment from(NluConfig cfg) {
        if (cfg == null) {
            return enabled();
        }
        return new ExecutionJudgment(cfg.isExecutionJudgment(), cfg.isToolConstraintStrict());
    }

    /**
     * 环境信息块的引导语（FR-NJD-03）：
     * 严格模式下要求模型"遵守"，参考模式下声明"仅供参考"。
     */
    public String envInfoHeader() {
        return toolConstraintStrict
                ? "当前环境信息（生成命令时请遵守）："
                : "当前环境信息（仅供参考，命令是否可执行由用户判断）：";
    }
}
