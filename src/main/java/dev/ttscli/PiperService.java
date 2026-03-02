package dev.ttscli;

import jakarta.enterprise.context.Dependent;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Dependent
public class PiperService {

    public Path synthesize(String text, String voice, float speed) {
        try {
            Path tempWav = Files.createTempFile("tts-", ".wav");

            // Piper length_scale is inverse of speed: higher = slower
            float lengthScale = 1.0f / speed;

            ProcessBuilder pb = new ProcessBuilder(
                    "piper",
                    "--model", voice,
                    "--output-file", tempWav.toAbsolutePath().toString(),
                    "--length-scale", String.valueOf(lengthScale),
                    "--data-dir", getDataDir());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            try (OutputStream os = process.getOutputStream()) {
                os.write(text.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            String processOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                Files.deleteIfExists(tempWav);
                throw new TtsException("Piper failed (exit code " + exitCode + "): " + processOutput.trim(), 1);
            }

            if (!Files.exists(tempWav) || Files.size(tempWav) == 0) {
                throw new TtsException("Piper produced no output", 1);
            }

            return tempWav;
        } catch (IOException e) {
            throw new TtsException("Failed to run piper: " + e.getMessage(), 1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TtsException("Piper was interrupted", 1);
        }
    }

    public void listVoices(boolean jsonOutput) {
        Path dataDir = Path.of(getDataDir());
        java.util.List<String> models = java.util.List.of();

        if (Files.isDirectory(dataDir)) {
            try (var stream = Files.walk(dataDir)) {
                models = stream
                        .filter(p -> p.toString().endsWith(".onnx"))
                        .map(p -> dataDir.relativize(p).toString().replace(".onnx", ""))
                        .sorted()
                        .toList();
            } catch (IOException e) {
                // empty list
            }
        }

        if (jsonOutput) {
            StringBuilder sb = new StringBuilder("{\"data_dir\": \"");
            sb.append(escapeJson(dataDir.toString())).append("\", \"voices\": [");
            for (int i = 0; i < models.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append("\"").append(escapeJson(models.get(i))).append("\"");
            }
            sb.append("]}");
            System.out.println(sb);
            return;
        }

        if (!models.isEmpty()) {
            System.out.println("Installed voices (" + dataDir + "):");
            models.forEach(m -> System.out.println("  " + m));
            return;
        }

        System.out.println("No local voice models found in: " + dataDir);
        System.out.println();
        System.out.println("Download a model with:");
        System.out.println("  piper --model en_US-lessac-medium --data-dir " + dataDir + " <<< \"test\"");
        System.out.println();
        System.out.println("Browse voices: https://rhasspy.github.io/piper-samples/");
        System.out.println();
        System.out.println("Common voices:");
        System.out.println("  en_US-lessac-medium    (US English, female)");
        System.out.println("  en_US-amy-medium       (US English, female)");
        System.out.println("  en_GB-alan-medium      (British English, male)");
        System.out.println("  de_DE-thorsten-medium  (German, male)");
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String getDataDir() {
        String home = System.getProperty("user.home");
        return home + "/.local/share/piper-voices";
    }
}
