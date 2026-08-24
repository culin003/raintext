package com.raintext;

import com.raintext.core.LineIndex;
import com.raintext.core.MappedFileReader;
import com.raintext.search.SearchEngine;
import com.raintext.ui.EditableEditorView;
import com.raintext.util.EditorUtils;
import com.raintext.ui.SearchDialog;
import com.raintext.ui.SearchResultPanel;
import com.raintext.ui.StatusBar;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Dialog;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class RainTextApplication extends Application {
    private Stage primaryStage;
    private EditableEditorView editorView;
    private SearchResultPanel searchResultPanel;
    private BorderPane contentPane;
    private StatusBar statusBar;
    private MappedFileReader fileReader;
    private LineIndex lineIndex;
    private SearchEngine searchEngine;
    private File currentFile;
    private boolean pendingSearch = false;
    private String pendingSearchQuery = "";
    private String currentEncoding = "UTF-8";
    private List<SearchEngine.SearchResult> lastResults = null;
    private int currentResultIndex = -1;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        this.searchEngine = new SearchEngine();

        loadDefaultFont();

        BorderPane root = new BorderPane();

        MenuBar menuBar = createMenuBar();
        root.setTop(menuBar);

        statusBar = new StatusBar();
        root.setBottom(statusBar);

        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        loadApplicationIcons();

        primaryStage.setTitle("RainText - 超大文本编辑器");
        primaryStage.setScene(scene);
        primaryStage.show();

        primaryStage.setOnCloseRequest(e -> {
            if (editorView != null && editorView.isModified()) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                makeIndependent(confirm);
                confirm.setTitle("保存更改");
                confirm.setHeaderText("文件已被修改，是否保存？");
                confirm.setContentText("点击确定保存，点击取消放弃更改");

                confirm.showAndWait().ifPresent(response -> {
                    if (response == javafx.scene.control.ButtonType.OK) {
                        saveFile();
                    }
                });
            }
            searchEngine.shutdown();
            closeFile();
            Platform.exit();
            System.exit(0);
        });
    }

    private void loadDefaultFont() {
        try {
            Font fontRegular = Font.loadFont(
                    getClass().getResourceAsStream("/fonts/MapleMono-CN-Regular.ttf"), 14
            );
            Font.loadFont(getClass().getResourceAsStream("/fonts/MapleMono-CN-Bold.ttf"), 14);
            Font.loadFont(getClass().getResourceAsStream("/fonts/MapleMono-CN-Italic.ttf"), 14);
            Font.loadFont(getClass().getResourceAsStream("/fonts/MapleMono-CN-BoldItalic.ttf"), 14);

            if (fontRegular != null) {
                System.setProperty("javafx.font.maple", fontRegular.getName());
            }
        } catch (Exception e) {
            System.err.println("Failed to load Maple Mono font: " + e.getMessage());
        }
    }

    private void loadApplicationIcons() {
        try {
            String[] sizes = {"16", "32", "48", "64", "128", "256"};
            for (String size : sizes) {
                var stream = getClass().getResourceAsStream("/icons/icon-" + size + ".png");
                if (stream != null) {
                    primaryStage.getIcons().add(new Image(stream));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load application icons: " + e.getMessage());
        }
    }

    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();

        Menu fileMenu = new Menu("文件");
        MenuItem openItem = new MenuItem("打开");
        openItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
        openItem.setOnAction(e -> openFile());

        MenuItem saveItem = new MenuItem("保存");
        saveItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
        saveItem.setOnAction(e -> saveFile());

        MenuItem saveAsItem = new MenuItem("另存为");
        saveAsItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        saveAsItem.setOnAction(e -> saveFileAs());

        MenuItem closeItem = new MenuItem("关闭");
        closeItem.setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN));
        closeItem.setOnAction(e -> closeFile());

        MenuItem exitItem = new MenuItem("退出");
        exitItem.setAccelerator(new KeyCodeCombination(KeyCode.Q, KeyCombination.CONTROL_DOWN));
        exitItem.setOnAction(e -> {
            searchEngine.shutdown();
            closeFile();
            Platform.exit();
            System.exit(0);
        });

        fileMenu.getItems().addAll(openItem, saveItem, saveAsItem, new SeparatorMenuItem(), closeItem, exitItem);

        Menu editMenu = new Menu("编辑");

        MenuItem undoItem = new MenuItem("撤销");
        undoItem.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN));
        undoItem.setOnAction(e -> {
            if (editorView != null && editorView.getUndoManager().canUndo()) {
                editorView.getUndoManager().undo();
            }
        });

        MenuItem redoItem = new MenuItem("重做");
        redoItem.setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN));
        redoItem.setOnAction(e -> {
            if (editorView != null && editorView.getUndoManager().canRedo()) {
                editorView.getUndoManager().redo();
            }
        });

        MenuItem cutItem = new MenuItem("剪切");
        cutItem.setAccelerator(new KeyCodeCombination(KeyCode.X, KeyCombination.CONTROL_DOWN));
        cutItem.setOnAction(e -> {
            if (editorView != null) editorView.cut();
        });

        MenuItem copyItem = new MenuItem("复制");
        copyItem.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN));
        copyItem.setOnAction(e -> {
            if (editorView != null) editorView.copy();
        });

        MenuItem pasteItem = new MenuItem("粘贴");
        pasteItem.setAccelerator(new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN));
        pasteItem.setOnAction(e -> {
            if (editorView != null) editorView.paste();
        });

        MenuItem selectAllItem = new MenuItem("全选");
        selectAllItem.setAccelerator(new KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN));
        selectAllItem.setOnAction(e -> {
            if (editorView != null) editorView.selectAll();
        });

        editMenu.getItems().addAll(undoItem, redoItem, new SeparatorMenuItem(), cutItem, copyItem, pasteItem, new SeparatorMenuItem(), selectAllItem);

        Menu searchMenu = new Menu("搜索");
        MenuItem findItem = new MenuItem("查找");
        findItem.setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN));
        findItem.setOnAction(e -> showSearchDialog());

        MenuItem goToLineItem = new MenuItem("跳转到行");
        goToLineItem.setAccelerator(new KeyCodeCombination(KeyCode.G, KeyCombination.CONTROL_DOWN));
        goToLineItem.setOnAction(e -> showGoToLineDialog());

        MenuItem findNextItem = new MenuItem("查找下一个");
        findNextItem.setAccelerator(new KeyCodeCombination(KeyCode.F3));
        findNextItem.setOnAction(e -> findNext());

        MenuItem findPrevItem = new MenuItem("查找上一个");
        findPrevItem.setAccelerator(new KeyCodeCombination(KeyCode.F3, KeyCombination.SHIFT_DOWN));
        findPrevItem.setOnAction(e -> findPrevious());

        searchMenu.getItems().addAll(findItem, goToLineItem, new SeparatorMenuItem(), findNextItem, findPrevItem);

        Menu viewMenu = new Menu("视图");

        CheckMenuItem wrapItem = new CheckMenuItem("自动换行");
        wrapItem.setOnAction(e -> { if (editorView != null) editorView.setWrapTextEnabled(wrapItem.isSelected()); });

        CheckMenuItem lineNumberItem = new CheckMenuItem("显示行号");
        lineNumberItem.setSelected(true);
        lineNumberItem.setOnAction(e -> { if (editorView != null) editorView.setLineNumbersVisible(lineNumberItem.isSelected()); });

        CheckMenuItem searchPanelItem = new CheckMenuItem("显示搜索结果面板");
        searchPanelItem.setSelected(true);
        searchPanelItem.setOnAction(e -> toggleSearchPanel(searchPanelItem.isSelected()));

        viewMenu.getItems().addAll(wrapItem, lineNumberItem, searchPanelItem);

        Menu helpMenu = new Menu("帮助");
        MenuItem aboutItem = new MenuItem("关于");
        aboutItem.setOnAction(e -> showAboutDialog());
        helpMenu.getItems().add(aboutItem);

        menuBar.getMenus().addAll(fileMenu, editMenu, searchMenu, viewMenu, helpMenu);
        return menuBar;
    }

    private void toggleSearchPanel(boolean visible) {
        if (searchResultPanel == null) return;
        if (visible) searchResultPanel.show();
        else searchResultPanel.hide();
    }

    private void openFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("打开文件");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("所有文件", "*.*"),
                new FileChooser.ExtensionFilter("文本文件", "*.txt", "*.log", "*.csv"),
                new FileChooser.ExtensionFilter("源代码", "*.java", "*.py", "*.js", "*.ts", "*.c", "*.cpp", "*.h")
        );

        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            loadFile(file);
        }
    }

    private void loadFile(File file) {
        try {
            closeFile();

            currentFile = file;
            fileReader = new MappedFileReader(file.toPath());

            // Create editor view immediately (shows first screen without index)
            editorView = new EditableEditorView(fileReader, null);
            editorView.setOnSave(this::saveFile);
            editorView.setOnModificationChange(() -> {
                Platform.runLater(() -> statusBar.setModified(editorView.isModified()));
            });

            editorView.getTextArea().caretPositionProperty().addListener((obs, oldPos, newPos) -> {
                Platform.runLater(() -> {
                    statusBar.setCursorPosition(editorView.getCurrentLine(), editorView.getCurrentColumn());
                });
            });

            editorView.getTextArea().selectedTextProperty().addListener((obs, oldSel, newSel) -> {
                Platform.runLater(() -> updateSelectionStatus(newSel));
            });

            searchResultPanel = new SearchResultPanel();
            searchResultPanel.setOnResultClick(result ->
                    editorView.goToLineAndSelect(result.getLine(), result.getColumn(), result.getMatch().length())
            );

            BorderPane contentPane = new BorderPane();
            this.contentPane = contentPane;
            contentPane.setCenter(editorView);
            contentPane.setBottom(searchResultPanel);

            BorderPane root = (BorderPane) primaryStage.getScene().getRoot();
            root.setCenter(contentPane);

            statusBar.setLineCount(0);
            statusBar.setFileSize(fileReader.fileSize());
            statusBar.setEncoding(currentEncoding);
            statusBar.clearSearchResult();
            statusBar.setSelection(0, 0);
            statusBar.setModified(false);
            statusBar.setCursorPosition(0, 0);
            searchResultPanel.clearResults();

            String title = "RainText - " + file.getName();
            primaryStage.setTitle(title);

            // Build index in background thread
            final MappedFileReader reader = fileReader;
            Thread indexThread = new Thread(() -> {
                long start = System.currentTimeMillis();
                LineIndex idx = new LineIndex(reader, StandardCharsets.UTF_8);
                long elapsed = System.currentTimeMillis() - start;
                System.out.println("Index built in " + elapsed + "ms (" + idx.getLineCount() + " lines)");
                Platform.runLater(() -> {
                    lineIndex = idx;
                    editorView.setLineIndex(idx);
                    statusBar.setLineCount(idx.getLineCount());
                    if (pendingSearch) {
                        pendingSearch = false;
                        SearchDialog dialog = new SearchDialog(d -> performSearch(d.getSearchText(), d.isRegex(), d.isCaseSensitive()));
                        if (pendingSearchQuery != null && !pendingSearchQuery.isEmpty()) {
                            dialog.setInitialQuery(pendingSearchQuery);
                        }
                        makeIndependent(dialog);
                        dialog.show();
                    }
                });
            });
            indexThread.setDaemon(true);
            indexThread.start();

        } catch (IOException e) {
            showError("打开文件失败", e.getMessage());
        }
    }

    private void saveFile() {
        if (currentFile == null || editorView == null) return;

        try {
            String content = editorView.getText();
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            fileReader.writeContent(bytes);
            editorView.clearModified();
            statusBar.setModified(false);
            statusBar.setFileSize(bytes.length);
        } catch (IOException e) {
            showError("保存文件失败", e.getMessage());
        }
    }

    private void saveFileAs() {
        if (editorView == null) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("另存为");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("所有文件", "*.*"),
                new FileChooser.ExtensionFilter("文本文件", "*.txt")
        );

        if (currentFile != null) {
            fileChooser.setInitialFileName(currentFile.getName());
        }

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                String content = editorView.getText();
                byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                java.nio.file.Files.write(file.toPath(), bytes);
                currentFile = file;
                fileReader.close();
                fileReader = new MappedFileReader(file.toPath());
                lineIndex = new LineIndex(fileReader, StandardCharsets.UTF_8);
                editorView.clearModified();
                statusBar.setModified(false);
                statusBar.setFileSize(bytes.length);
                statusBar.setLineCount(lineIndex.getLineCount());
                primaryStage.setTitle("RainText - " + file.getName());
            } catch (IOException e) {
                showError("保存文件失败", e.getMessage());
            }
        }
    }

    private void closeFile() {
        if (fileReader != null) {
            try {
                fileReader.close();
            } catch (IOException e) {
                // Ignore
            }
            fileReader = null;
            lineIndex = null;
            editorView = null;
            searchResultPanel = null;
            currentFile = null;

            BorderPane root = (BorderPane) primaryStage.getScene().getRoot();
            root.setCenter(null);

            statusBar.setLineCount(0);
            statusBar.setFileSize(0);
            statusBar.clearSearchResult();
            statusBar.setModified(false);
            statusBar.setCursorPosition(0, 0);

            primaryStage.setTitle("RainText - 超大文本编辑器");
        }
    }

    private void showSearchDialog() {
        if (currentFile == null) {
            showError("错误", "请先打开一个文件");
            return;
        }

        if (lineIndex == null) {
            pendingSearch = true;
            pendingSearchQuery = getSelectedText();
            showError("提示", "正在建立索引，请稍候...");
            return;
        }

        SearchDialog dialog = new SearchDialog(d -> performSearch(d.getSearchText(), d.isRegex(), d.isCaseSensitive()));
        String selected = getSelectedText();
        if (selected != null && !selected.isEmpty()) {
            dialog.setInitialQuery(selected);
        }
        makeIndependent(dialog);
        dialog.show();
    }

    private String getSelectedText() {
        if (editorView != null) {
            try {
                return editorView.getTextArea().getSelectedText();
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    private void performSearch(String query, boolean regex, boolean caseSensitive) {
        if (query == null || query.isEmpty() || lineIndex == null) {
            return;
        }

        CompletableFuture<List<SearchEngine.SearchResult>> future =
                searchEngine.search(lineIndex, query, regex, caseSensitive);

        future.thenAccept(results -> {
            Platform.runLater(() -> {
                statusBar.setSearchResult(results.size());
                searchResultPanel.setResults(results);
                lastResults = results;
                currentResultIndex = 0;
                if (!results.isEmpty()) {
                    SearchEngine.SearchResult first = results.get(0);
                    editorView.goToLineAndSelect(first.getLine(), first.getColumn(), first.getMatch().length());
                } else {
                    statusBar.setSearchStatus(0, 0);
                }
            });
        });
    }

    private void navigateToResult(int index) {
        if (lastResults == null || lastResults.isEmpty()) return;
        if (index < 0 || index >= lastResults.size()) return;
        currentResultIndex = index;
        SearchEngine.SearchResult r = lastResults.get(index);
        editorView.goToLineAndSelect(r.getLine(), r.getColumn(), r.getMatch().length());
        statusBar.setSearchStatus(index + 1, lastResults.size());
    }

    private void findNext() {
        if (lastResults == null || lastResults.isEmpty()) return;
        int next = EditorUtils.nextIndex(currentResultIndex, lastResults.size());
        navigateToResult(next);
    }

    private void findPrevious() {
        if (lastResults == null || lastResults.isEmpty()) return;
        int prev = EditorUtils.prevIndex(currentResultIndex, lastResults.size());
        navigateToResult(prev);
    }

    private void updateSelectionStatus(String selected) {
        int chars = com.raintext.util.EditorUtils.selectionChars(selected);
        int lines = com.raintext.util.EditorUtils.selectionLines(selected);
        statusBar.setSelection(chars, lines);
    }

    private void showGoToLineDialog() {
        if (currentFile == null) {
            showError("错误", "请先打开一个文件");
            return;
        }
        if (lineIndex == null) {
            showError("提示", "正在建立索引，请稍候...");
            return;
        }

        TextInputDialog dialog = new TextInputDialog("1");
        makeIndependent(dialog);
        dialog.setTitle("跳转到行");
        dialog.setHeaderText("输入行号");
        dialog.setContentText("行号:");

        dialog.showAndWait().ifPresent(lineStr -> {
            try {
                int lineNumber = Integer.parseInt(lineStr);
                if (lineNumber >= 1 && lineNumber <= lineIndex.getLineCount()) {
                    editorView.goToLine(lineNumber - 1);
                } else {
                    showError("错误", "行号超出范围: 1 - " + lineIndex.getLineCount());
                }
            } catch (NumberFormatException e) {
                showError("错误", "请输入有效的数字");
            }
        });
    }

    private void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        makeIndependent(alert);
        alert.setTitle("关于 RainText");
        alert.setHeaderText("RainText 超大文本编辑器");
        alert.setContentText(
                "版本: 1.0.0\n\n" +
                        "一个用于处理超大文本文件的编辑器。\n\n" +
                        "特性:\n" +
                        "- 支持打开 GB 级别的大文件\n" +
                        "- 内存映射技术，高效读取\n" +
                        "- 并行搜索\n" +
                        "- 文本编辑、保存\n" +
                        "- 撤销/重做\n" +
                        "- 搜索结果面板\n\n" +
                        "技术栈:\n" +
                        "- Java 21\n" +
                        "- JavaFX 21"
        );
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        makeIndependent(alert);
        alert.showAndWait();
    }

    /**
     * Make a dialog a fully independent top-level window with no owner window.
     * This prevents window managers (e.g. KDE) from treating it as a transient
     * of the main stage, which otherwise causes a maximized parent to lose its
     * maximized state when the dialog appears.
     */
    private void makeIndependent(Dialog<?> dialog) {
        dialog.initOwner(null);
        dialog.initModality(Modality.NONE);
        // Explicitly center on the main stage once sized. Without an owner the
        // dialog would otherwise be positioned relative to the focused window at
        // show time, which right after loading a file (stage not yet fully laid
        // out) lands it at the top-left. Keeping initOwner(null) means no
        // transient relationship, so the KDE maximized-state bug stays fixed.
        dialog.setOnShown(e -> {
            double w = dialog.getWidth();
            double h = dialog.getHeight();
            if (w <= 0 || h <= 0) return;
            dialog.setX(primaryStage.getX() + primaryStage.getWidth() / 2 - w / 2);
            dialog.setY(primaryStage.getY() + primaryStage.getHeight() / 2 - h / 2);
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
