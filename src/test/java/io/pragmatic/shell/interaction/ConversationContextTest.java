package io.pragmatic.shell.interaction;

import io.pragmatic.shell.config.model.ContextConfig;
import io.pragmatic.shell.nlu.ContextTurn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationContextTest {

    private static ContextConfig config(boolean enabled, int maxTurns) {
        ContextConfig c = new ContextConfig();
        c.setEnabled(enabled);
        c.setMaxTurns(maxTurns);
        return c;
    }

    private static ContextTurn turn(String input) {
        return ContextTurn.completed(input, "echo " + input, "ok", 0, false);
    }

    @Test
    void keepsOnlyLatestTurnsWithinWindow() {
        ConversationContext ctx = new ConversationContext(config(true, 2));
        ctx.add(turn("a"));
        ctx.add(turn("b"));
        ctx.add(turn("c"));
        List<ContextTurn> snap = ctx.snapshot();
        assertEquals(2, snap.size());
        assertEquals("b", snap.get(0).userInput());
        assertEquals("c", snap.get(1).userInput());
    }

    @Test
    void disabledContextDoesNotRecord() {
        ConversationContext ctx = new ConversationContext(config(false, 10));
        ctx.add(turn("a"));
        assertTrue(ctx.snapshot().isEmpty());
        assertFalse(ctx.enabled());
    }

    @Test
    void clearEmptiesHistory() {
        ConversationContext ctx = new ConversationContext(config(true, 10));
        ctx.add(turn("a"));
        ctx.clear();
        assertTrue(ctx.snapshot().isEmpty());
    }

    @Test
    void snapshotIsImmutableCopy() {
        ConversationContext ctx = new ConversationContext(config(true, 10));
        ctx.add(turn("a"));
        List<ContextTurn> snap = ctx.snapshot();
        ctx.add(turn("b"));
        assertEquals(1, snap.size());
        assertEquals(2, ctx.snapshot().size());
    }
}
