package dev.ttscli;

import jakarta.enterprise.context.Dependent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Dependent
public class AudioConverter {

    public Path wavToMp3(Path wavFile, Path outputPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-y",
                    "-i", wavFile.toAbsolutePath().toString(),
                    "-codec:a", "libmp3lame",
                    "-b:a", "192k",
                    "-loglevel", "error",
                    outputPath.toAbsolutePath().toString());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            String processOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new TtsException(
                        "ffmpeg conversion failed (exit code " + exitCode + "): " + processOutput.trim(), 1);
            }

            if (!Files.exists(outputPath) || Files.size(outputPath) == 0) {
                throw new TtsException("ffmpeg produced no output", 1);
            }

            return outputPath;
        } catch (IOException e) {
            throw new TtsException("Failed to run ffmpeg: " + e.getMessage(), 1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TtsException("ffmpeg was interrupted", 1);
        }
    }

    public long getDurationMs(Path audioFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe",
                    "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "csv=p=0",
                    audioFile.toAbsolutePath().toString());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();

            int exitCode = process.waitFor();
            if (exitCode != 0 || output.isBlank()) {
                return -1;
            }

            return Math.round(Double.parseDouble(output) * 1000);
        } catch (Exception e) {
            return -1;
        }
    }
}
