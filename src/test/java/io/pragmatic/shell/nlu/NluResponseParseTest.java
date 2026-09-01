package io.pragmatic.shell.nlu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 模型响应解析（FR-NJD-01/02）：决定"命令能否走到人工确认环节"的关键路径。
 * 不依赖真实 LLM 调用（直接测 {@link LangChainNluService#parseResponse}）。
 */
class NluResponseParseTest {

    private static final ExecutionJudgment WITH_JUDGMENT = new ExecutionJudgment(true, true);
    private static final ExecutionJudgment TRANSLATE_ONLY = new ExecutionJudgment(false, true);

    // ===== 现状行为（executionJudgment=true）=====

    @Test
    void unsafeReturnedWhenJudgmentEnabled() {
        assertEquals(NluStatus.UNSAFE,
                LangChainNluService.parseResponse("UNSAFE", WITH_JUDGMENT).status());
    }

    @Test
    void impossibleReturnedWhenJudgmentEnabled() {
        assertEquals(NluStatus.IMPOSSIBLE,
                LangChainNluService.parseResponse("IMPOSSIBLE", WITH_JUDGMENT).status());
    }

    @Test
    void commandReturnedWhenJudgmentEnabled() {
        NluResult r = LangChainNluService.parseResponse("ls -la", WITH_JUDGMENT);
        assertEquals(NluStatus.COMMAND, r.status());
        assertEquals("ls -la", r.command());
    }

    // ===== 纯翻译模式（executionJudgment=false）=====

    @Test
    void unsafeIsNeutralizedInTranslateOnlyMode() {
        // 核心：模型的安全自我审查不得阻断命令展示，判定权归人
        assertEquals(NluStatus.IMPOSSIBLE,
                LangChainNluService.parseResponse("UNSAFE", TRANSLATE_ONLY).status(),
                "纯翻译模式下 UNSAFE 应按翻译失败处理，不得作为安全拒绝");
    }

    @Test
    void impossibleIsNeutralizedInTranslateOnlyMode() {
        assertEquals(NluStatus.IMPOSSIBLE,
                LangChainNluService.parseResponse("IMPOSSIBLE", TRANSLATE_ONLY).status());
    }

    @Test
    void untranslatableIsOnlyRejectionInTranslateOnlyMode() {
        NluResult r = LangChainNluService.parseResponse("UNTRANSLATABLE", TRANSLATE_ONLY);
        assertEquals(NluStatus.IMPOSSIBLE, r.status());
        assertNull(r.command(), "翻译失败时不应带命令");
    }

    @Test
    void riskyCommandsStillShownInTranslateOnlyMode() {
        // 运维场景核心：破坏性命令照常输出，由人确认
        NluResult r = LangChainNluService.parseResponse("systemctl restart nginx", TRANSLATE_ONLY);
        assertEquals(NluStatus.COMMAND, r.status());
        assertEquals("systemctl restart nginx", r.command());
    }

    @Test
    void commandsUsingUndetectedToolsStillShownInTranslateOnlyMode() {
        // 运维场景核心：nginx 未在工具清单中也不再被拒
        NluResult r = LangChainNluService.parseResponse("nginx -s reload", TRANSLATE_ONLY);
        assertEquals(NluStatus.COMMAND, r.status());
        assertEquals("nginx -s reload", r.command());
    }

    // ===== 通用边界 =====

    @Test
    void blankResponseIsImpossible() {
        assertEquals(NluStatus.IMPOSSIBLE,
                LangChainNluService.parseResponse("   ", TRANSLATE_ONLY).status());
        assertEquals(NluStatus.IMPOSSIBLE,
                LangChainNluService.parseResponse(null, TRANSLATE_ONLY).status());
    }

    @Test
    void codeFenceIsStripped() {
        NluResult r = LangChainNluService.parseResponse("```bash\nls -la\n```", TRANSLATE_ONLY);
        assertEquals(NluStatus.COMMAND, r.status());
        assertEquals("ls -la", r.command(), "应去除代码围栏与语言标记");
    }

    @Test
    void nullJudgmentFallsBackToLoose() {
        // judgment 缺失时回退默认（宽松）：UNSAFE 被无效化，命令归人决定
        assertEquals(NluStatus.IMPOSSIBLE,
                LangChainNluService.parseResponse("UNSAFE", null).status(),
                "judgment 缺失时应回退默认宽松模式（执行判定关闭）");
        assertEquals(NluStatus.COMMAND,
                LangChainNluService.parseResponse("systemctl restart nginx", null).status(),
                "宽松默认下命令应照常展示，交给人工确认");
    }

    @Test
    void statusWordsAreCaseInsensitive() {
        assertEquals(NluStatus.IMPOSSIBLE,
                LangChainNluService.parseResponse("untranslatable", TRANSLATE_ONLY).status());
    }
}
