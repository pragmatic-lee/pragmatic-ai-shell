package io.pragmatic.shell.nlu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SecretMaskerTest {

    @Test
    void skPrefixedKeyIsMasked() {
        assertEquals("sk-****", SecretMasker.mask("sk-abc123456789"));
    }

    @Test
    void skKeyInsideTextIsMasked() {
        assertEquals("curl -H 'Authorization: Bearer sk-****' http://x",
                SecretMasker.mask("curl -H 'Authorization: Bearer sk-abc123456789' http://x"));
    }

    @Test
    void credentialKeyValuePairsAreMasked() {
        assertEquals("token=****", SecretMasker.mask("token=abc123"));
        assertEquals("password=****", SecretMasker.mask("password=hunter2"));
        assertEquals("api_key=****", SecretMasker.mask("api_key=xyz789"));
    }

    @Test
    void plainTextUnchanged() {
        String s = "ls -la /tmp && echo hello";
        assertEquals(s, SecretMasker.mask(s));
    }

    @Test
    void nullOrBlankUnchanged() {
        assertNull(SecretMasker.mask(null));
        assertEquals("", SecretMasker.mask(""));
    }
}
