package io.pragmatic.shell.safety;

import io.pragmatic.shell.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafetyFilterChainTest {

    private final AppConfig config = AppConfig.defaults();

    @Test
    void shouldRejectCriticalBlacklist() {
        FilterVerdict v = new SafetyFilterChain(config).evaluate("rm -rf /");
        assertEquals(FilterVerdict.VerdictType.REJECT, v.type());
    }

    @Test
    void shouldRejectForkBomb() {
        FilterVerdict v = new SafetyFilterChain(config).evaluate(":(){ :|:& };:");
        assertEquals(FilterVerdict.VerdictType.REJECT, v.type());
    }

    @Test
    void shouldConfirmDestructive() {
        FilterVerdict v = new SafetyFilterChain(config).evaluate("rm -rf ./tmp");
        assertEquals(FilterVerdict.VerdictType.CONFIRM, v.type());
    }

    @Test
    void shouldPassReadOnlyCommand() {
        FilterVerdict v = new SafetyFilterChain(config).evaluate("ls -la /opt/app");
        assertEquals(FilterVerdict.VerdictType.PASS, v.type());
    }

    @Test
    void shouldRejectSensitiveAddress() {
        FilterVerdict v = new SafetyFilterChain(config).evaluate("curl http://169.254.169.254/latest/meta-data/");
        assertEquals(FilterVerdict.VerdictType.REJECT, v.type());
    }

    @Test
    void readOnlyModeBlocksWrite() {
        AppConfig ro = config.withReadOnly(true);
        FilterVerdict v = new SafetyFilterChain(ro).evaluate("cp a b");
        assertEquals(FilterVerdict.VerdictType.REJECT, v.type());
        assertTrue(v.message().contains("只读"));
    }

    @Test
    void riskClassifierLevels() {
        assertEquals(CommandRisk.READ, RiskClassifier.classify("cat file.txt"));
        assertEquals(CommandRisk.WRITE, RiskClassifier.classify("mkdir foo"));
        assertEquals(CommandRisk.DESTRUCTIVE, RiskClassifier.classify("kill -9 1234"));
    }

    // ===== sudo 策略（运维场景，FR-NJD-Q3）=====

    @Test
    void sudoConfirmWhenPolicyConfirm() {
        AppConfig cfg = configWithSudoPolicy("confirm");
        FilterVerdict v = new SafetyFilterChain(cfg).evaluate("sudo systemctl restart nginx");
        assertEquals(FilterVerdict.VerdictType.CONFIRM, v.type());
        assertTrue(v.message().contains("提权"), "确认提示应说明需要提权, 实际: " + v.message());
    }

    @Test
    void sudoAllowedWhenPolicyAllow() {
        AppConfig cfg = configWithSudoPolicy("allow");
        // 用非破坏性命令验证：sudo 本身不再被黑名单拒绝
        FilterVerdict v = new SafetyFilterChain(cfg).evaluate("sudo cat /etc/hosts");
        assertEquals(FilterVerdict.VerdictType.PASS, v.type());
    }

    /**
     * 层次关系验证：sudo 策略与破坏性确认是两层独立拦截。
     * 放开 sudo 只解除黑名单层的拒绝，不解除 ConfirmationGate 对破坏性命令的确认要求。
     */
    @Test
    void sudoAllowDoesNotBypassDestructiveConfirmation() {
        AppConfig cfg = configWithSudoPolicy("allow");
        // systemctl restart 属 DESTRUCTIVE，即便 sudo 放行仍需确认
        assertEquals(FilterVerdict.VerdictType.CONFIRM,
                new SafetyFilterChain(cfg).evaluate("sudo systemctl restart nginx").type());
        // 非破坏性提权命令则完全放行
        assertEquals(FilterVerdict.VerdictType.PASS,
                new SafetyFilterChain(cfg).evaluate("sudo nginx -s reload").type());
    }

    /**
     * 安全底线：放开 sudo 后，高危命令不得以加 sudo 前缀的方式绕过黑名单。
     * 这是 FR-NJD-09 的核心断言。
     */
    @Test
    void criticalCommandsStillRejectedWithSudoPrefix() {
        AppConfig cfg = configWithSudoPolicy("allow");
        List<String> critical = List.of(
                "sudo rm -rf /",
                "sudo rm -fr /",
                "sudo rm -rf / ",
                "sudo env rm -rf /",
                "sudo nohup rm -rf /",
                "sudo command rm -rf /"
        );
        for (String cmd : critical) {
            FilterVerdict v = new SafetyFilterChain(cfg).evaluate(cmd);
            assertEquals(FilterVerdict.VerdictType.REJECT, v.type(),
                    "高危命令必须被拦截（不得因 sudo 前缀绕过）: " + cmd);
        }
    }

    /** 放开 sudo 后，普通提权命令仍可通行（否则策略失去意义）。 */
    @Test
    void sudoAllowStillPermitsNormalPrivilegedCommands() {
        AppConfig cfg = configWithSudoPolicy("allow");
        assertEquals(FilterVerdict.VerdictType.PASS,
                new SafetyFilterChain(cfg).evaluate("sudo nginx -s reload").type());
        assertEquals(FilterVerdict.VerdictType.PASS,
                new SafetyFilterChain(cfg).evaluate("sudo cat /var/log/nginx/error.log").type());
    }

    /** 未配置 sudoPolicy 时应回退 confirm（默认宽松：提权确认后放行）。 */
    @Test
    void nullSudoPolicyFallsBackToConfirm() {
        config.getSafety().setSudoPolicy(null);
        assertEquals(FilterVerdict.VerdictType.CONFIRM,
                new SafetyFilterChain(config).evaluate("sudo ls").type());
    }

    /** 默认配置（零配置首次启动）下 sudo 命令确认后放行，不再硬拒。 */
    @Test
    void defaultConfigAllowsSudoWithConfirmation() {
        FilterVerdict v = new SafetyFilterChain(config).evaluate("sudo systemctl restart nginx");
        assertEquals(FilterVerdict.VerdictType.CONFIRM, v.type(),
                "默认 sudoPolicy=confirm，应确认后放行而非拒绝");
    }

    private static AppConfig configWithSudoPolicy(String policy) {
        AppConfig cfg = AppConfig.defaults();
        cfg.getSafety().setSudoPolicy(policy);
        return cfg;
    }
}
