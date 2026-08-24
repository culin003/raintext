package com.raintext.ui;

import com.raintext.core.LineIndex;
import com.raintext.core.MappedFileReader;
import com.raintext.editor.UndoManager;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

public class EditableEditorView extends StackPane {
    private LineIndex lineIndex;
    private final MappedFileReader fileReader;
    private final TextArea textArea;
    private final Canvas lineNumberCanvas;
    private final ScrollBar virtualScrollBar;
    private final UndoManager undoManager;
    private static final double LINE_HEIGHT = 20.0;
    private static final int LINE_NUMBER_WIDTH = 70;
    private static final int BUFFER_LINES = 500;
    private boolean isLoading = false;
    private boolean isModified = false;
    private Runnable onModificationChange;
    private Runnable onLineIndexReady;
    private final Font lineFont = Font.font("Maple Mono CN", 14);
    private int totalLines = 0;
    private int loadedFromLine = -1;
    private boolean suppressScrollEvent = false;
    private boolean probingScrollTop = false;
    private double realMaxScrollTop = -1;
    private double measuredLineHeight = -1;
    private int pendingSelectColumn = -1;
    private int pendingSelectLength = 0;

    public EditableEditorView(MappedFileReader fileReader, LineIndex lineIndex) {
        this.fileReader = fileReader;
        this.lineIndex = lineIndex;
        this.totalLines = lineIndex != null ? lineIndex.getLineCount() : 0;
        this.textArea = new TextArea();
        this.lineNumberCanvas = new Canvas();
        this.virtualScrollBar = new ScrollBar();
        this.undoManager = new UndoManager();

        setupUI();
        hideTextAreaScrollbar();
    }

    private void setupUI() {
        textArea.setWrapText(false);
        textArea.setFont(Font.font("Maple Mono CN", 14));
        textArea.setStyle("-fx-padding: 0;");

        lineNumberCanvas.setWidth(LINE_NUMBER_WIDTH);
        lineNumberCanvas.setMouseTransparent(true);
        lineNumberCanvas.heightProperty().bind(heightProperty());
        lineNumberCanvas.widthProperty().addListener((obs, old, val) -> drawLineNumbers());
        lineNumberCanvas.setOnMouseClicked(e -> textArea.requestFocus());

        virtualScrollBar.setOrientation(javafx.geometry.Orientation.VERTICAL);
        virtualScrollBar.setMin(0);
        virtualScrollBar.setMax(Math.max(1, totalLines));
        virtualScrollBar.setValue(0);
        virtualScrollBar.setVisibleAmount(getVisibleLineCount());
        virtualScrollBar.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (suppressScrollEvent) return;
            onVirtualScroll(newVal.doubleValue());
        });

        textArea.scrollTopProperty().addListener((obs, oldVal, newVal) -> {
            if (isLoading || probingScrollTop) return;
            updateVirtualFromTextArea();
            Platform.runLater(this::drawLineNumbers);

            // Detect reaching the physical bottom of loaded content using the real
            // (probed) maximum scrollTop, not the estimate-based one.
            if (loadedFromLine + getLoadedLineCount() < totalLines
                    && newVal.doubleValue() >= getRealMaxScrollTop() - 2) {
                int visibleCount = getVisibleLineCount();
                int currentLine = loadedFromLine + (int) Math.round(newVal.doubleValue() / getLineHeight());
                int newStart = Math.max(0, currentLine - visibleCount / 3);
                if (newStart + visibleCount > totalLines) {
                    newStart = Math.max(0, totalLines - visibleCount);
                }
                if (newStart < totalLines && newStart != loadedFromLine) {
                    loadVisibleLines(newStart, currentLine);
                }
            }
        });

        // Mouse wheel drives content loading
        textArea.setOnScroll(event -> {
            if (isLoading || lineIndex == null) return;

            double delta = event.getDeltaY();
            double currentTop = textArea.getScrollTop();

            if (delta < 0 && currentTop >= getRealMaxScrollTop() - getLineHeight() * 20) {
                int loadedCount = getLoadedLineCount();
                int visibleCount = getVisibleLineCount();
                int currentLine = loadedFromLine + (int) Math.round(currentTop / getLineHeight());
                int newStart = Math.max(0, currentLine - visibleCount / 3);
                // Ensure the loaded window reaches the file end so the last line is visible.
                if (newStart + visibleCount > totalLines) {
                    newStart = Math.max(0, totalLines - visibleCount);
                }
                if (newStart < totalLines && newStart != loadedFromLine) {
                    loadVisibleLines(newStart, currentLine);
                }
            }
            // Scrolling up and near top → load more above
            else if (delta > 0 && currentTop <= getLineHeight() * 20 && loadedFromLine > 0) {
                int loadedCount = getLoadedLineCount();
                int visibleCount = getVisibleLineCount();
                int currentLine = loadedFromLine + (int) Math.round(currentTop / getLineHeight());
                int newStart = Math.max(0, currentLine - visibleCount * 2 / 3);
                if (newStart != loadedFromLine) {
                    loadVisibleLines(newStart, currentLine);
                }
            }
        });

        textArea.setOnKeyPressed(event -> {
            if (isLoading) return;
            if (event.isControlDown()) {
                handleControlKey(event.getCode());
            } else {
                handleRegularKey(event.getCode());
            }
        });

        // Accelerators (Ctrl+F, Ctrl+G, etc.) consume KEY_PRESSED but the
        // following KEY_TYPED still reaches the TextArea and would insert the
        // shortcut's character (e.g. 'f') as normal input, spuriously marking
        // the document modified. Consume KEY_TYPED while a shortcut modifier is
        // held so the character is never inserted.
        textArea.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            if (event.isShortcutDown()) {
                event.consume();
            }
        });

        textArea.setOnKeyTyped(event -> {
            if (isLoading) return;
            if (event.isShortcutDown()) return;
            markModified();
        });

        textArea.textProperty().addListener((obs, oldVal, newVal) -> {
            if (isLoading) return;
            Platform.runLater(this::drawLineNumbers);
        });

        textArea.caretPositionProperty().addListener((obs, oldVal, newVal) -> {
            if (isLoading) return;
            Platform.runLater(this::drawLineNumbers);
        });

        BorderPane inner = new BorderPane();
        inner.setLeft(lineNumberCanvas);
        inner.setCenter(textArea);
        inner.setRight(virtualScrollBar);
        BorderPane.setMargin(lineNumberCanvas, javafx.geometry.Insets.EMPTY);
        BorderPane.setMargin(virtualScrollBar, javafx.geometry.Insets.EMPTY);

        getChildren().add(inner);

        setMinHeight(0);
        setPrefHeight(0);
        setMaxHeight(Double.MAX_VALUE);

        inner.setMinHeight(0);
        inner.setPrefHeight(0);
        inner.setMaxHeight(Double.MAX_VALUE);
        textArea.setMinHeight(0);
        textArea.setMaxHeight(Double.MAX_VALUE);

        // Immediately load first screen
        Platform.runLater(this::loadInitialContent);
    }

    private void loadInitialContent() {
        isLoading = true;

        if (lineIndex != null) {
            loadVisibleLines(0, 0);
        } else {
            // Fast path: read directly from file without LineIndex
            String content = fileReader.readFirstLines(1000);
            textArea.setText(content);
            loadedFromLine = 0;
            textArea.setScrollTop(0);
            textArea.positionCaret(0);

            // Estimate total lines from file size
            long fileSize = fileReader.fileSize();
            if (fileSize > 0) {
                int avgLineLen = Math.max(1, content.length() / Math.max(1, content.split("\n", -1).length));
                totalLines = (int) (fileSize / avgLineLen);
                virtualScrollBar.setMax(totalLines);
            }
        }

        isLoading = false;
        Platform.runLater(this::drawLineNumbers);
    }

    /**
     * Called when LineIndex is built in background. Enables full navigation.
     */
    public void setLineIndex(LineIndex lineIndex) {
        this.lineIndex = lineIndex;
        this.totalLines = lineIndex.getLineCount();

        Platform.runLater(() -> {
            virtualScrollBar.setMax(Math.max(1, totalLines - getVisibleLineCount()));
            virtualScrollBar.setVisibleAmount(getVisibleLineCount());
            drawLineNumbers();
            if (onLineIndexReady != null) onLineIndexReady.run();
        });
    }

    public void setOnLineIndexReady(Runnable handler) {
        this.onLineIndexReady = handler;
    }

    public boolean isIndexReady() {
        return lineIndex != null;
    }

    private void hideTextAreaScrollbar() {
        textArea.skinProperty().addListener((obs, old, skin) -> {
            Platform.runLater(this::applyScrollbarHide);
        });
        Platform.runLater(this::applyScrollbarHide);
        Platform.runLater(() -> Platform.runLater(this::applyScrollbarHide));
    }

    private void applyScrollbarHide() {
        textArea.lookupAll(".scroll-bar:vertical").forEach(node -> {
            node.setVisible(false);
            node.setManaged(false);
        });
    }

    private void updateVirtualFromTextArea() {
        if (isLoading) return;
        double scrollTop = textArea.getScrollTop();
        double maxTop = getRealMaxScrollTop();
        int visibleCount = getVisibleLineCount();
        int total = Math.max(1, totalLines - visibleCount);

        // Ratio-based mapping: scrollTop/maxTop within the loaded window, mapped to
        // the top visible line in the whole document. This is robust to line-height
        // estimation errors because it uses the TextArea's real physical maximum.
        double ratio = (maxTop > 0) ? Math.min(1.0, Math.max(0.0, scrollTop / maxTop)) : 0;
        int loadedCount = getLoadedLineCount();
        double topLine = loadedFromLine + ratio * Math.max(0, loadedCount - visibleCount);
        if (topLine < 0) topLine = 0;
        if (topLine >= totalLines) topLine = totalLines - 1;
        double clamped = Math.min(topLine, total);

        suppressScrollEvent = true;
        virtualScrollBar.setMax(Math.max(1, total));
        virtualScrollBar.setVisibleAmount(visibleCount);
        virtualScrollBar.setValue(Math.max(0, clamped));
        suppressScrollEvent = false;
    }

    private void onVirtualScroll(double value) {
        if (isLoading || lineIndex == null) return;

        int targetLine = (int) Math.round(value);
        if (targetLine < 0) targetLine = 0;
        if (targetLine >= totalLines) targetLine = totalLines - 1;

        int visibleCount = getVisibleLineCount();
        int newFromLine = Math.max(0, targetLine - visibleCount / 3);
        if (newFromLine + visibleCount > totalLines) {
            newFromLine = Math.max(0, totalLines - visibleCount);
        }

        if (Math.abs(newFromLine - loadedFromLine) > visibleCount / 4) {
            loadVisibleLines(newFromLine, targetLine);
        }
    }

    private int getVisibleLineCount() {
        double h = textArea.getHeight();
        if (h <= 0) h = 800;
        return Math.max(10, (int) (h / getLineHeight()));
    }

    private int getLoadedLineCount() {
        String text = textArea.getText();
        if (text == null || text.isEmpty()) return 0;
        return text.split("\n", -1).length;
    }

    /**
     * Returns the real line height used by the TextArea. Measured once from the
     * VirtualFlow (which renders actual line boxes), falling back to LINE_HEIGHT.
     */
    private double getLineHeight() {
        if (measuredLineHeight > 0) return measuredLineHeight;
        try {
            Object flow = textArea.lookup(".virtual-flow");
            if (flow != null) {
                java.lang.reflect.Method m = flow.getClass().getMethod("getCellLength", int.class);
                m.setAccessible(true);
                Object v = m.invoke(flow, 0);
                if (v instanceof Number n && n.doubleValue() > 0) {
                    measuredLineHeight = n.doubleValue();
                    return measuredLineHeight;
                }
            }
        } catch (Exception ignored) {}
        return LINE_HEIGHT;
    }

    /**
     * Measures the real maximum scrollTop of the TextArea for the currently loaded
     * content, using the measured line height. This is reliable regardless of layout
     * state because it derives from line count and viewport height.
     */
    private double getActualMaxScrollTop() {
        int loadedCount = getLoadedLineCount();
        if (loadedCount <= 0) return 0;
        int visibleCount = getVisibleLineCount();
        double lh = getLineHeight();
        return Math.max(0, (loadedCount - visibleCount) * lh);
    }

    /**
     * Returns the TextArea's real physical maximum scrollTop for the currently loaded
     * content, measured by probing (setting scrollTop to a huge value and reading back
     * the clamped value). This is exact regardless of line-height estimation. Cached and
     * invalidated whenever the content changes. Falls back to the estimate if probing
     * fails (e.g. before layout/CSS is applied).
     */
    private double getRealMaxScrollTop() {
        if (realMaxScrollTop >= 0) return realMaxScrollTop;
        double saved = textArea.getScrollTop();
        probingScrollTop = true;
        textArea.setScrollTop(Double.MAX_VALUE);
        double probed = textArea.getScrollTop();
        textArea.setScrollTop(saved);
        probingScrollTop = false;
        if (probed < 1e15) {
            realMaxScrollTop = Math.max(0, probed);
        } else {
            realMaxScrollTop = getActualMaxScrollTop();
        }
        return realMaxScrollTop;
    }

    private void loadVisibleLines(int fromLine, int scrollToLine) {
        if (fromLine < 0) fromLine = 0;
        if (lineIndex != null && fromLine >= totalLines) fromLine = totalLines - 1;
        if (lineIndex == null) return;

        isLoading = true;

        int caretLine = getCurrentLine();
        int caretCol = getCurrentColumn();
        int visibleCount = getVisibleLineCount();
        int endLine = Math.min(fromLine + visibleCount + BUFFER_LINES, totalLines);

        StringBuilder sb = new StringBuilder();
        for (int i = fromLine; i < endLine; i++) {
            if (i > fromLine) sb.append("\n");
            sb.append(lineIndex.getLine(i));
        }

        textArea.setText(sb.toString());
        loadedFromLine = fromLine;
        realMaxScrollTop = -1;

        int targetLine = (scrollToLine >= 0) ? scrollToLine : caretLine;
        int localTarget = targetLine - fromLine;
        int finalLocalTarget = Math.max(0, Math.min(localTarget, endLine - fromLine - 1));

        // Center the target line vertically in the viewport instead of pinning
        // it to the top: scrollTop = (relativeIndex - visibleLines/2) * lineHeight.
        int loadedCount = endLine - fromLine;
        double lh = getLineHeight();
        double maxTop = Math.max(0, (loadedCount - visibleCount) * lh);
        double targetScrollTop = Math.max(0,
                Math.min((finalLocalTarget - visibleCount / 2.0 + 0.5) * lh, maxTop));

        // Restore scroll position after layout
        Platform.runLater(() -> {
            int pos = getCaretPosForLine(finalLocalTarget) + caretCol;
            if (pendingSelectColumn >= 0) {
                pos = getCaretPosForLine(finalLocalTarget) + pendingSelectColumn;
            }
            if (pos > textArea.getText().length()) pos = textArea.getText().length();
            textArea.positionCaret(pos);

            if (pendingSelectColumn >= 0 && pendingSelectLength > 0) {
                int selEnd = Math.min(pos + pendingSelectLength, textArea.getText().length());
                textArea.selectRange(pos, selEnd);
                pendingSelectColumn = -1;
                pendingSelectLength = 0;
            }

            // Set scroll last so the TextArea's caret-into-view auto-scroll does
            // not override our centered position.
            textArea.setScrollTop(targetScrollTop);

            suppressScrollEvent = true;
            virtualScrollBar.setValue(Math.max(0, targetLine));
            suppressScrollEvent = false;

            isLoading = false;
            drawLineNumbers();
        });
    }

    private void drawLineNumbers() {
        GraphicsContext gc = lineNumberCanvas.getGraphicsContext2D();
        double width = lineNumberCanvas.getWidth();
        double height = lineNumberCanvas.getHeight();
        if (width <= 0 || height <= 0) return;

        gc.clearRect(0, 0, width, height);
        gc.setFill(Color.web("#f0f0f0"));
        gc.fillRect(0, 0, width, height);

        gc.setFont(lineFont);
        gc.setFill(Color.web("#888888"));

        int visibleCount = (int) (height / getLineHeight()) + 2;

        // Scroll offset within loaded content
        double scrollTop = 0;
        try { scrollTop = textArea.getScrollTop(); } catch (Exception ignored) {}
        int scrollOffsetLines = (int) (scrollTop / getLineHeight());
        int startLine = loadedFromLine + scrollOffsetLines;
        int endLine = Math.min(startLine + visibleCount, totalLines > 0 ? totalLines : startLine + visibleCount);

        for (int i = startLine; i < endLine; i++) {
            double y = (i - startLine) * getLineHeight() + getLineHeight() - 4;
            if (y > height + getLineHeight()) break;
            gc.fillText(String.format("%6d", i + 1), 4, y);
        }

        gc.setStroke(Color.web("#cccccc"));
        gc.setLineWidth(1);
        gc.strokeLine(width - 0.5, 0, width - 0.5, height);
    }

    private void handleControlKey(KeyCode code) {
        switch (code) {
            case Z -> {
                if (undoManager.canUndo()) applyUndoAction(undoManager.undo());
            }
            case Y -> {
                if (undoManager.canRedo()) applyUndoAction(undoManager.redo());
            }
            case S -> saveFile();
        }
    }

    public void setWrapTextEnabled(boolean wrap) {
        textArea.setWrapText(wrap);
    }

    public boolean isWrapTextEnabled() {
        return textArea.isWrapText();
    }

    public void setLineNumbersVisible(boolean visible) {
        lineNumberCanvas.setVisible(visible);
    }

    public boolean isLineNumbersVisible() {
        return lineNumberCanvas.isVisible();
    }

    private void handleRegularKey(KeyCode code) {
        int caretPos = textArea.getCaretPosition();
        String currentText = textArea.getText();

        if (code == KeyCode.BACK_SPACE && caretPos > 0) {
            String deleted = currentText.substring(caretPos - 1, caretPos);
            undoManager.push(new UndoManager.EditAction(getCurrentLineOffset(), deleted, ""));
            textArea.setText(currentText.substring(0, caretPos - 1) + currentText.substring(caretPos));
            textArea.positionCaret(caretPos - 1);
            markModified();
        } else if (code == KeyCode.DELETE && caretPos < currentText.length()) {
            String deleted = currentText.substring(caretPos, caretPos + 1);
            undoManager.push(new UndoManager.EditAction(getCurrentLineOffset(), deleted, ""));
            textArea.setText(currentText.substring(0, caretPos) + currentText.substring(caretPos + 1));
            textArea.positionCaret(caretPos);
            markModified();
        } else if (code == KeyCode.ENTER) {
            undoManager.push(new UndoManager.EditAction(getCurrentLineOffset(), "", "\n"));
            textArea.setText(currentText.substring(0, caretPos) + "\n" + currentText.substring(caretPos));
            textArea.positionCaret(caretPos + 1);
            markModified();
        }
        Platform.runLater(this::drawLineNumbers);
    }

    private long getCurrentLineOffset() {
        if (lineIndex == null) return 0;
        int line = getCurrentLine();
        if (line < 0) line = 0;
        if (line >= totalLines) line = totalLines - 1;
        return lineIndex.getLineOffset(line);
    }

    private int getCaretPosForLine(int localLineIndex) {
        String text = textArea.getText();
        int line = 0, pos = 0;
        for (int i = 0; i < text.length(); i++) {
            if (line == localLineIndex) return pos;
            if (text.charAt(i) == '\n') { line++; pos = i + 1; }
        }
        return pos;
    }

    private void applyUndoAction(UndoManager.EditAction action) {
        isLoading = true;
        String text = textArea.getText();
        String deleted = action.getDeletedText();
        String inserted = action.getInsertedText();

        if (!deleted.isEmpty()) {
            int idx = text.indexOf(deleted);
            if (idx >= 0) {
                textArea.setText(text.substring(0, idx) + inserted + text.substring(idx + deleted.length()));
                textArea.positionCaret(idx + inserted.length());
            }
        } else if (!inserted.isEmpty()) {
            int pos = Math.min(textArea.getCaretPosition(), text.length());
            textArea.setText(text.substring(0, pos) + inserted + text.substring(pos));
            textArea.positionCaret(pos + inserted.length());
        }

        isLoading = false;
        markModified();
        Platform.runLater(this::drawLineNumbers);
    }

    private void saveFile() {
        if (onSave != null) onSave.run();
    }

    private Runnable onSave;

    public void setOnSave(Runnable handler) { this.onSave = handler; }
    public String getText() { return textArea.getText(); }
    public TextArea getTextArea() { return textArea; }
    public UndoManager getUndoManager() { return undoManager; }

    public void markModified() {
        if (!isModified) {
            isModified = true;
            if (onModificationChange != null) onModificationChange.run();
        }
    }
    public void clearModified() {
        isModified = false;
        if (onModificationChange != null) onModificationChange.run();
    }
    public boolean isModified() { return isModified; }
    public void setOnModificationChange(Runnable handler) { this.onModificationChange = handler; }

    public void goToLine(int line) {
        if (lineIndex == null || line < 0 || line >= totalLines) return;
        pendingSelectColumn = -1;
        pendingSelectLength = 0;
        int visibleCount = getVisibleLineCount();
        loadVisibleLines(Math.max(0, line - visibleCount / 2), line);
    }

    public void goToLineAndSelect(int line, int column, int length) {
        if (lineIndex == null || line < 0 || line >= totalLines) return;
        pendingSelectColumn = column;
        pendingSelectLength = length;
        int visibleCount = getVisibleLineCount();
        loadVisibleLines(Math.max(0, line - visibleCount / 2), line);
    }

    public int getCurrentLine() {
        String text = textArea.getText();
        int caretPos = textArea.getCaretPosition();
        int line = 0;
        for (int i = 0; i < caretPos && i < text.length(); i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return loadedFromLine + line;
    }

    public int getCurrentColumn() {
        String text = textArea.getText();
        int caretPos = textArea.getCaretPosition();
        int col = 0;
        for (int i = caretPos - 1; i >= 0; i--) {
            if (text.charAt(i) == '\n') break;
            col++;
        }
        return col;
    }

    public int getLineCount() { return totalLines; }
    public void selectAll() { textArea.selectAll(); }
    public void cut() { textArea.cut(); }
    public void copy() { textArea.copy(); }
    public void paste() { textArea.paste(); }
    public ScrollBar getVirtualScrollBar() { return virtualScrollBar; }
    public int debugLoadedFromLine() { return loadedFromLine; }
    public int debugTotalLines() { return totalLines; }
    public double debugScrollTop() { return textArea.getScrollTop(); }
    public double debugActualMaxScrollTop() { return getActualMaxScrollTop(); }
    public int debugLoadedCount() { return getLoadedLineCount(); }
    public int debugVisibleCount() { return getVisibleLineCount(); }
}
