package io.pragmatic.shell.nlu;

import io.pragmatic.shell.config.model.NluConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LLM 职责边界（FR-NJD-01/03）单元测试：
 * 验证配置到 ExecutionJudgment 的映射、默认值与引导语切换。
 */
class ExecutionJudgmentTest {

    @Test
    void defaultsAreLoose() {
        // 默认：宽松模式（执行判定关闭 + 工具约束仅参考），零配置开箱即用
        ExecutionJudgment j = ExecutionJudgment.enabled();
        assertFalse(j.executionJudgment(), "默认应剥离执行判定（宽松）");
        assertFalse(j.toolConstraintStrict(), "默认工具约束应为参考（宽松）");
    }

    @Test
    void fromConfigMapsBothFlags() {
        NluConfig cfg = new NluConfig();
        cfg.setExecutionJudgment(false);
        cfg.setToolConstraint(NluConfig.TOOL_CONSTRAINT_REFERENCE);

        ExecutionJudgment j = ExecutionJudgment.from(cfg);
        assertFalse(j.executionJudgment(), "应剥离执行判定");
        assertFalse(j.toolConstraintStrict(), "工具清单应降级为参考");
    }

    @Test
    void fromNullConfigFallsBackToDefault() {
        ExecutionJudgment j = ExecutionJudgment.from(null);
        assertFalse(j.executionJudgment(), "缺省配置应回退默认宽松模式");
        assertFalse(j.toolConstraintStrict(), "缺省配置应回退工具参考模式");
    }

    @Test
    void fromBlankConfigUsesDefaults() {
        ExecutionJudgment j = ExecutionJudgment.from(new NluConfig());
        assertFalse(j.executionJudgment(), "空配置应取字段默认（宽松）");
        assertFalse(j.toolConstraintStrict(), "空配置应取字段默认（参考）");
    }

    @Test
    void envInfoHeaderSwitchesByToolConstraint() {
        NluConfig strict = new NluConfig();
        strict.setToolConstraint(NluConfig.TOOL_CONSTRAINT_STRICT);
        assertTrue(ExecutionJudgment.from(strict).envInfoHeader().contains("请遵守"));

        NluConfig reference = new NluConfig();
        reference.setToolConstraint(NluConfig.TOOL_CONSTRAINT_REFERENCE);
        String header = ExecutionJudgment.from(reference).envInfoHeader();
        assertTrue(header.contains("仅供参考"), "参考模式引导语应说明仅供参考, 实际: " + header);
        assertTrue(header.contains("由用户判断"));
    }

    /** 大小写不敏感，避免用户写 Strict/REFERENCE 时配置失效。 */
    @Test
    void toolConstraintIsCaseInsensitive() {
        NluConfig cfg = new NluConfig();
        cfg.setToolConstraint("REFERENCE");
        assertFalse(ExecutionJudgment.from(cfg).toolConstraintStrict());

        cfg.setToolConstraint("Strict");
        assertTrue(ExecutionJudgment.from(cfg).toolConstraintStrict());
    }

    @Test
    void promptSelectedByExecutionJudgment() {
        String defaultPrompt = LangChainNluService.loadSystemPrompt(true);
        String translateOnly = LangChainNluService.loadSystemPrompt(false);

        assertTrue(defaultPrompt.contains("UNSAFE"), "默认 prompt 应保留 UNSAFE 指令");
        assertTrue(defaultPrompt.contains("IMPOSSIBLE"), "默认 prompt 应保留 IMPOSSIBLE 指令");

        assertFalse(translateOnly.contains("UNSAFE"),
                "纯翻译 prompt 不应出现 UNSAFE 指令，否则模型仍会自行审查");
        assertTrue(translateOnly.contains("UNTRANSLATABLE"),
                "纯翻译 prompt 应提供 UNTRANSLATABLE 作为唯一拒绝出口");
        assertTrue(translateOnly.contains("不做可行性判断") || translateOnly.contains("不判断"),
                "纯翻译 prompt 应明确不判断可行性");
    }

    @Test
    void translateOnlyPromptRetainsOsConstraint() {
        // OS 约束属命令正确性问题，两档都应保留（FR-NJD-04）
        String prompt = LangChainNluService.loadSystemPrompt(false);
        assertTrue(prompt.contains("brew"), "Linux 下不得使用 brew 的约束应保留");
        assertTrue(prompt.contains("apt") || prompt.contains("yum"),
                "macOS 下不得使用 Linux 包管理器的约束应保留");
    }

    @Test
    void defaultPromptUnchangedSemantics() {
        // 回归：严格 prompt 仍要求"未安装工具不得使用"
        String prompt = LangChainNluService.loadSystemPrompt(true);
        assertTrue(prompt.contains("不得使用"), "严格 prompt 应保留工具硬约束");
        // 默认（宽松）使用纯翻译 prompt，不含 UNSAFE 指令
        String defaultPrompt = LangChainNluService.loadSystemPrompt(ExecutionJudgment.enabled().executionJudgment());
        assertTrue(defaultPrompt.contains("UNTRANSLATABLE"), "默认 prompt 应为纯翻译模式");
    }
}
