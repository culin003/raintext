# RainText

A large text file editor built with Java 21 and JavaFX 21.

## Features

- Open and edit large text files efficiently
- Real-time search with highlighted results (Ctrl+F, F3 to find next)
- Editable editor view with line navigation
- Status bar showing cursor position and file info
- Keyboard shortcuts for quick navigation
- Cross-platform support (Linux, macOS, Windows)

## Requirements

- Java 21 or later
- JavaFX 21.0.1

## Build

```bash
export JAVA_HOME=/path/to/jdk-21
./gradlew build
```

## Run

```bash
./gradlew run
```

## Package

Build a platform-specific installer:

```bash
./gradlew jpackage -PinstallerType=deb    # Linux .deb
./gradlew jpackage -PinstallerType=dmg    # macOS .dmg
./gradlew jpackage -PinstallerType=msi    # Windows .msi
```

## Install (Debian/Ubuntu)

```bash
sudo apt install ./build/jpackage/raintext_1.0.0_amd64.deb
```

Then search for "RainText" in your application launcher.

## Project Structure

```
src/main/java/com/raintext/
├── core/           # File I/O and line index
├── editor/         # Edit buffer and undo
├── search/         # Search engine
├── ui/             # JavaFX views
└── util/           # Helper utilities
```

## License

MIT
