package dev.ttscli;

public class TtsException extends RuntimeException {

    private final int exitCode;

    public TtsException(String message, int exitCode) {
        super(message);
        this.exitCode = exitCode;
    }

    public int getExitCode() {
        return exitCode;
    }
}
