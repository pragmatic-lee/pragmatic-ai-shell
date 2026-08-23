package io.pragmatic.shell.safety;

import io.pragmatic.shell.config.AppConfig;
import org.junit.jupiter.api.Test;

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
}
