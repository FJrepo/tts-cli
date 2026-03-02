package dev.ttscli;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusMainTest
class TtsCommandTest {

    @Test
    @Launch(value = {"--help"}, exitCode = 0)
    void testHelp(LaunchResult result) {
        String output = result.getOutput();
        assertTrue(output.contains("tts"), "Should contain command name");
        assertTrue(output.contains("--output"), "Should contain --output option");
        assertTrue(output.contains("--voice"), "Should contain --voice option");
        assertTrue(output.contains("--max-words"), "Should contain --max-words option");
        assertTrue(output.contains("--json"), "Should contain --json option");
        assertTrue(output.contains("--stdin"), "Should contain --stdin option");
    }

    @Test
    @Launch(value = {"--version"}, exitCode = 0)
    void testVersion(LaunchResult result) {
        assertTrue(result.getOutput().contains("0.1.0"));
    }

    // --- Validation errors return exit code 2, regardless of piper availability ---

    @Test
    void testInvalidSpeedReturnsValidationError(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("--speed", "5.0", "hello");
        assertEquals(2, result.exitCode(), "Invalid speed should return exit code 2");
    }

    @Test
    void testSpeedTooLow(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("--speed", "0.1", "hello");
        assertEquals(2, result.exitCode(), "Speed below 0.5 should return exit code 2");
    }

    @Test
    void testInvalidFormatRejectedByPicocli(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("--format", "ogg", "hello");
        // Picocli rejects invalid enum values before call() runs — returns exit code 2
        assertEquals(2, result.exitCode(), "Invalid format should return exit code 2");
    }

    @Test
    void testInvalidMaxWordsZero(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("--max-words", "0", "hello");
        assertEquals(2, result.exitCode(), "max-words 0 should return exit code 2");
    }

    @Test
    void testInvalidMaxWordsNegative(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("--max-words", "-5", "hello");
        assertEquals(2, result.exitCode(), "Negative max-words should return exit code 2");
    }

    @Test
    void testNoTextProvided(QuarkusMainLauncher launcher) {
        // Use -o to temp path to avoid leaving output.mp3 in project root
        LaunchResult result = launcher.launch("-o", "/tmp/tts-test-notext.mp3");
        assertEquals(2, result.exitCode(), "No text should return exit code 2");
    }

    @Test
    void testEmptyFileInput(@TempDir Path tempDir, QuarkusMainLauncher launcher) throws IOException {
        Path emptyFile = tempDir.resolve("empty.txt");
        Files.writeString(emptyFile, "   \n  ");
        LaunchResult result = launcher.launch("--file", emptyFile.toString());
        assertEquals(2, result.exitCode(), "Blank file should return exit code 2");
    }

    // --- JSON error output is valid, single-line, escaped ---

    @Test
    void testJsonErrorOutputIsValidJson(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("--json", "--speed", "5.0", "hello");
        assertEquals(2, result.exitCode());
        String output = result.getOutput().trim();
        assertTrue(output.startsWith("{") && output.endsWith("}"), "Should be JSON object");
        assertTrue(output.contains("\"error\""), "Should contain error key");
        assertFalse(output.contains(System.lineSeparator() + "{"), "JSON should be single line");
    }

    @Test
    void testJsonErrorEscapesNewlines(QuarkusMainLauncher launcher) {
        // Use an empty --file to trigger an error with a controlled message,
        // then verify that --json output with a multiline error from --max-words -1
        // doesn't produce raw newlines. But the best unconditional test is to
        // verify escapeJson directly, which we do in a dedicated unit test below.

        // This integration test checks whichever error occurs (dep or validation)
        // produces single-line JSON
        LaunchResult result = launcher.launch("--json", "-o", "/tmp/tts-test-escape.mp3", "hello");
        String output = result.getOutput().trim();
        if (!output.isEmpty()) {
            long jsonLines = output.lines().filter(l -> l.trim().startsWith("{")).count();
            assertEquals(1, jsonLines, "JSON output must be exactly one line");
        }
    }

    @Test
    void testEscapeJsonHandlesAllControlChars() {
        // Direct unit test for escapeJson — always runs, no environment dependency
        TtsCommand cmd = new TtsCommand();
        String input = "line1\nline2\rline3\ttab \"quoted\" back\\slash";
        String escaped = cmd.escapeJson(input);
        assertEquals("line1\\nline2\\rline3\\ttab \\\"quoted\\\" back\\\\slash", escaped);
    }

    // --- --list-voices with --json ---

    @Test
    void testListVoicesJson(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("--list-voices", "--json");
        assertEquals(0, result.exitCode());
        String output = result.getOutput().trim();
        assertTrue(output.startsWith("{"), "JSON list-voices should start with {");
        assertTrue(output.contains("\"voices\""), "Should contain voices key");
        assertTrue(output.contains("\"data_dir\""), "Should contain data_dir key");
    }

    @Test
    @Launch(value = {"--list-voices"}, exitCode = 0)
    void testListVoicesText(LaunchResult result) {
        // Should not crash even if no voices installed
        assertNotNull(result.getOutput());
    }
}
