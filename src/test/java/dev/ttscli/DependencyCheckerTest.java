package dev.ttscli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DependencyCheckerTest {

    @Test
    void testKnownCommandIsAvailable() {
        DependencyChecker checker = new DependencyChecker();
        assertTrue(checker.isCommandAvailable("echo", "test"), "echo should be available");
    }

    @Test
    void testUnknownCommandIsNotAvailable() {
        DependencyChecker checker = new DependencyChecker();
        assertFalse(checker.isCommandAvailable("nonexistent-command-xyz-12345", "--version"));
    }
}
