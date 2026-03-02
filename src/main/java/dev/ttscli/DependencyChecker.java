package dev.ttscli;

import jakarta.enterprise.context.Dependent;
import java.io.IOException;

@Dependent
public class DependencyChecker {

    public void checkPiper() {
        if (!isCommandAvailable("piper", "--version") && !isCommandAvailable("piper", "--help")) {
            throw new TtsException(
                    "piper is not installed or not in PATH.\n" +
                    "Install: https://github.com/rhasspy/piper/releases\n" +
                    "Or via pip: pip install piper-tts",
                    3);
        }
    }

    public void checkFfmpeg() {
        if (!isCommandAvailable("ffmpeg", "-version")) {
            throw new TtsException(
                    "ffmpeg is not installed or not in PATH.\n" +
                    "Install: sudo apt install ffmpeg (Debian/Ubuntu)\n" +
                    "         brew install ffmpeg (macOS)",
                    3);
        }
    }

    boolean isCommandAvailable(String command, String testArg) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command, testArg);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.getInputStream().readAllBytes();
            process.waitFor();
            return true;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
