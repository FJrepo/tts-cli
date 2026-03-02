package dev.ttscli;

import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Command(name = "tts",
        mixinStandardHelpOptions = true,
        version = "tts-cli 0.1.0",
        description = "Convert text to speech using local Piper TTS")
public class TtsCommand implements Callable<Integer> {

    @Parameters(index = "0", arity = "0..1", description = "Text to synthesize (or use --file / --stdin)")
    String text;

    @Option(names = {"-o", "--output"}, description = "Output file path (default: ${DEFAULT-VALUE})", defaultValue = "output.mp3")
    Path outputFile;

    @Option(names = {"-f", "--file"}, description = "Read text from file")
    Path inputFile;

    @Option(names = {"--stdin"}, description = "Read text from stdin")
    boolean readStdin;

    @Option(names = {"-v", "--voice"}, description = "Piper voice model (default: ${DEFAULT-VALUE})", defaultValue = "en_US-lessac-medium")
    String voice;

    @Option(names = {"--max-words"}, description = "Maximum word count, must be >= 1 (default: ${DEFAULT-VALUE})", defaultValue = "200")
    int maxWords;

    @Option(names = {"--format"}, description = "Output format: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE})", defaultValue = "mp3")
    AudioFormat format;

    @Option(names = {"--json"}, description = "Output result as JSON")
    boolean jsonOutput;

    @Option(names = {"--list-voices"}, description = "List available Piper voices")
    boolean listVoices;

    @Option(names = {"-s", "--speed"}, description = "Speech speed 0.5-2.0 (default: ${DEFAULT-VALUE})", defaultValue = "1.0")
    float speed;

    @Option(names = {"-q", "--quiet"}, description = "Suppress non-essential output")
    boolean quiet;

    private static final int STDIN_TIMEOUT_SECONDS = 5;

    @Inject
    DependencyChecker dependencyChecker;

    @Inject
    PiperService piperService;

    @Inject
    AudioConverter audioConverter;

    @Override
    public Integer call() {
        try {
            if (listVoices) {
                piperService.listVoices(jsonOutput);
                return 0;
            }

            // Validate all args BEFORE dependency checks → deterministic exit code 2
            if (speed < 0.5f || speed > 2.0f) {
                throw new TtsException("Speed must be between 0.5 and 2.0", 2);
            }

            if (maxWords < 1) {
                throw new TtsException("Max words must be at least 1", 2);
            }

            String inputText = resolveInputText();

            // Dependency checks after validation
            dependencyChecker.checkPiper();
            if (format == AudioFormat.mp3) {
                dependencyChecker.checkFfmpeg();
            }

            String[] words = inputText.trim().split("\\s+");
            int wordCount = words.length;

            if (wordCount > maxWords) {
                inputText = truncateToMaxWords(inputText, maxWords);
                wordCount = maxWords;
                if (!quiet) {
                    System.err.println("Warning: Text truncated to " + maxWords + " words");
                }
            }

            outputFile = ensureExtension(outputFile, format.name());

            Path wavFile = piperService.synthesize(inputText, voice, speed);
            try {
                Path finalFile;
                if (format == AudioFormat.mp3) {
                    finalFile = audioConverter.wavToMp3(wavFile, outputFile);
                } else {
                    Files.move(wavFile, outputFile, StandardCopyOption.REPLACE_EXISTING);
                    wavFile = null;
                    finalFile = outputFile;
                }

                long durationMs = (jsonOutput || !quiet) ? audioConverter.getDurationMs(finalFile) : -1;

                if (jsonOutput) {
                    printJson(finalFile, durationMs, wordCount, voice);
                } else if (!quiet) {
                    System.out.println("Created: " + finalFile.toAbsolutePath());
                    System.out.println("Duration: " + durationMs + "ms | Words: " + wordCount + " | Voice: " + voice);
                }

                return 0;
            } finally {
                if (wavFile != null) {
                    Files.deleteIfExists(wavFile);
                }
            }
        } catch (TtsException e) {
            System.err.println("Error: " + e.getMessage());
            if (jsonOutput) {
                System.out.println("{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
            }
            return e.getExitCode();
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            if (jsonOutput) {
                System.out.println("{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
            }
            return 1;
        }
    }

    private String resolveInputText() {
        if (text != null && !text.isBlank()) {
            return text;
        }

        if (inputFile != null) {
            try {
                String content = Files.readString(inputFile);
                if (content.isBlank()) {
                    throw new TtsException("Input file is empty: " + inputFile, 2);
                }
                return content;
            } catch (IOException e) {
                throw new TtsException("Cannot read file: " + inputFile + " - " + e.getMessage(), 2);
            }
        }

        if (readStdin) {
            return readStdinWithTimeout();
        }

        throw new TtsException("No text provided. Use: tts \"text\", tts --file input.txt, or echo \"text\" | tts --stdin", 2);
    }

    private String readStdinWithTimeout() {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "stdin-reader");
            t.setDaemon(true);
            return t;
        });
        try {
            Future<String> future = executor.submit(() -> {
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                return reader.lines().collect(Collectors.joining("\n"));
            });

            String stdinText = future.get(STDIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (stdinText == null || stdinText.isBlank()) {
                throw new TtsException("No text provided via stdin", 2);
            }
            return stdinText;
        } catch (TimeoutException e) {
            throw new TtsException("Stdin read timed out after " + STDIN_TIMEOUT_SECONDS + "s (is input piped?)", 2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TtsException("Stdin read interrupted", 2);
        } catch (ExecutionException e) {
            throw new TtsException("Failed to read stdin: " + e.getCause().getMessage(), 2);
        } finally {
            executor.shutdownNow();
        }
    }

    private String truncateToMaxWords(String input, int max) {
        String[] words = input.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < max && i < words.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(words[i]);
        }
        return sb.toString();
    }

    private Path ensureExtension(Path path, String fmt) {
        String name = path.getFileName().toString();
        String ext = "." + fmt.toLowerCase();
        if (!name.endsWith(ext)) {
            int dot = name.lastIndexOf('.');
            if (dot > 0) {
                name = name.substring(0, dot) + ext;
            } else {
                name = name + ext;
            }
            return path.getParent() != null ? path.getParent().resolve(name) : Path.of(name);
        }
        return path;
    }

    private void printJson(Path file, long durationMs, int wordCount, String voice) {
        System.out.printf("{\"file\": \"%s\", \"duration_ms\": %d, \"words\": %d, \"voice\": \"%s\"}%n",
                escapeJson(file.toAbsolutePath().toString()),
                durationMs,
                wordCount,
                escapeJson(voice));
    }

    String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
