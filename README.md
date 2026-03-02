# tts-cli

CLI tool for converting text to speech using local [Piper TTS](https://github.com/rhasspy/piper). Designed as a tool AI agents can call from the command line.

Built with [Quarkus](https://quarkus.io/) + [Picocli](https://picocli.info/), compiles to a native binary with ~6ms startup time.

## Setup

### 1. Install Piper TTS

Piper is a fast, local neural TTS engine. Install via `pipx` (recommended) or `pip`:

```bash
# Using pipx (recommended — isolated install, no system Python conflicts)
pipx install piper-tts
pipx inject piper-tts pathvalidate   # required dependency

# OR using pip in a venv
python3 -m venv ~/.piper-venv
~/.piper-venv/bin/pip install piper-tts pathvalidate
# Then add ~/.piper-venv/bin to your PATH
```

Verify piper is working:
```bash
piper --help
```

### 2. Download a voice model

Piper needs an `.onnx` voice model file. Download one to the default data directory:

```bash
# Create data directory
mkdir -p ~/.local/share/piper-voices

# Download the default English voice (~61MB)
# Using the bundled download tool:
$(dirname $(which piper))/../lib/python*/site-packages/../../../bin/python3 \
  -m piper.download_voices --download-dir ~/.local/share/piper-voices en_US-lessac-medium

# OR download manually from Hugging Face:
cd ~/.local/share/piper-voices
wget https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium/en_US-lessac-medium.onnx
wget https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium/en_US-lessac-medium.onnx.json
```

Browse all available voices at [piper-samples](https://rhasspy.github.io/piper-samples/).

### 3. Install ffmpeg (for MP3 output)

```bash
# Debian/Ubuntu
sudo apt install ffmpeg

# macOS
brew install ffmpeg

# Arch
sudo pacman -S ffmpeg
```

Not needed if you only use `--format wav`.

### 4. Build tts-cli

Requires **Java 21+** and **Maven** (or use the included Maven wrapper).

```bash
git clone <repo-url>
cd tts-cli

# JVM build (quick, for development)
./mvnw package

# Native build (recommended for production — ~6ms startup)
./mvnw package -Dnative

# Native build without local GraalVM (uses container)
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

### 5. Install the binary

```bash
# Option A: Symlink native binary to your PATH
sudo ln -s $(pwd)/target/tts-cli-0.1.0-runner /usr/local/bin/tts

# Option B: Copy it
sudo cp target/tts-cli-0.1.0-runner /usr/local/bin/tts

# Option C: Run via JVM (no native build needed)
alias tts='java -jar /path/to/tts-cli/target/quarkus-app/quarkus-run.jar'
```

### Verify setup

```bash
tts --version
tts --list-voices
tts --json -o /tmp/test.mp3 "Hello, setup is working"
```

## Agent Integration

Add this to your `CLAUDE.md` or `agents.md` to give agents access to TTS:

```markdown
## Tools

Text-to-speech: `tts --json --stdin -o <output.mp3> <<< "text"` (run `tts -h` for full options)
```

## Usage

```bash
# Basic usage
tts "Hello, this is a test"

# Specify output file
tts -o greeting.mp3 "Welcome to the system"

# Read from file
tts --file input.txt -o speech.mp3

# Pipe from another command (agent use case)
echo "Text from agent" | tts --stdin --json -o /tmp/output.mp3

# Custom voice and speed
tts -v en_GB-alan-medium -s 1.5 "Slower British voice"

# WAV output (skips ffmpeg requirement)
tts --format wav -o output.wav "Direct WAV output"

# Limit word count (for finding sweet spot)
tts --max-words 50 --file long-document.txt

# JSON output for agent parsing
tts --json "Hello world" -o hello.mp3

# List installed voices
tts --list-voices

# List voices as JSON (for agent parsing)
tts --list-voices --json
```

### Options

```
Usage: tts [-hqV] [--json] [--list-voices] [--stdin] [-f=<inputFile>]
           [--format=<format>] [--max-words=<maxWords>] [-o=<outputFile>]
           [-s=<speed>] [-v=<voice>] [<text>]

Arguments:
  [<text>]               Text to synthesize (or use --file / --stdin)

Options:
  -o, --output <FILE>    Output file path (default: output.mp3)
  -f, --file <FILE>      Read text from file
  --stdin                Read text from stdin
  -v, --voice <VOICE>    Piper voice model (default: en_US-lessac-medium)
  --max-words <INT>      Maximum word count, >= 1 (default: 200)
  --format mp3|wav       Output format (default: mp3)
  --json                 Output result as JSON (also works with --list-voices)
  --list-voices          List installed Piper voices
  -s, --speed <FLOAT>    Speech speed 0.5-2.0 (default: 1.0)
  -q, --quiet            Suppress non-essential output
  -h, --help             Show help
  -V, --version          Print version
```

### Input Priority

1. Text argument: `tts "hello"`
2. File flag: `tts --file input.txt`
3. Stdin (explicit): `echo "hello" | tts --stdin`

### JSON Output

When using `--json`, output is a single JSON line on stdout:

```json
{"file": "/absolute/path/to/output.mp3", "duration_ms": 2340, "words": 15, "voice": "en_US-lessac-medium"}
```

On error:
```json
{"error": "piper is not installed or not in PATH.\\nInstall: ..."}
```

With `--list-voices --json`:
```json
{"data_dir": "/home/user/.local/share/piper-voices", "voices": ["en_US-lessac-medium"]}
```

### Exit Codes

| Code | Meaning |
|------|---------|
| 0 | Success |
| 1 | Runtime error (piper/ffmpeg failure) |
| 2 | Validation error (no text, invalid speed) |
| 3 | Missing dependency (piper or ffmpeg not found) |

## Development

```bash
# Run in dev mode with live reload
./mvnw quarkus:dev -Dquarkus.args='--help'

# Run tests
./mvnw test
```
