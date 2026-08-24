# Project Rules — RainText

## Testing is mandatory for new code

- Every new feature or behavior change **must** include unit tests.
- Tests must be added under `src/test/java/...` following the existing JUnit 5
  style (see `SearchEngineTest`, `LineIndexTest`).
- The command `./gradlew test` **must pass** before any change is considered
  complete. A feature is not done until its tests are green.
- Prefer extracting pure, UI-independent logic (no JavaFX toolkit) into helper
  classes so it can be unit-tested headlessly. UI nodes (e.g. `StatusBar`,
  `EditableEditorView`) should delegate formatting/navigation math to these
  helpers rather than inlining it.

## Build / run notes

- The project targets **Java 21**. The default `java` on this machine is 17, so
  point Gradle at the JDK 21 install, e.g.:

  ```bash
  export JAVA_HOME=/home/cooper/Softwares/zulu21.52.15-ca-jdk21.0.12-linux_x64
  ./gradlew test
  ```

- Gradle test runs can hang when launched in the foreground interactive shell
  (daemon/terminal binding). Run via `nohup ./gradlew test ... &` and poll the
  log if the foreground invocation stalls.
- UI tests that open a `Stage` (e.g. `ScrollbarGeometryTest`) cannot run in this
  headless environment and will hang; keep new tests on pure logic only.

## Conventions

- Package layout: `core` (file/memory-mapped IO, line index), `search`
  (search engine), `editor` (edit buffer / undo), `ui` (JavaFX views),
  `util` (pure helpers).
- Match existing code style; do not add comments unless requested.
