package com.raintext.ui;

import com.raintext.core.LineIndex;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.Map;

public class EditorView extends VBox {
    private final LineIndex lineIndex;
    private final ListView<String> listView;
    private final ObservableList<String> observableLines;
    private final Map<Integer, String> lineCache;
    private static final int CACHE_SIZE = 2000;

    public EditorView(LineIndex lineIndex) {
        this.lineIndex = lineIndex;
        this.listView = new ListView<>();
        this.observableLines = FXCollections.observableArrayList();
        this.lineCache = new HashMap<>();

        setupUI();
        initializeData();
    }

    private void setupUI() {
        listView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        listView.setItems(observableLines);

        listView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    int lineIdx = getIndex();
                    String prefix = String.format("%6d: ", lineIdx + 1);
                    setText(prefix + item);
                }
            }
        });

        listView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (newValue != null) {
                        int selectedIndex = listView.getSelectionModel().getSelectedIndex();
                        preloadAround(selectedIndex);
                    }
                }
        );

        listView.setOnScroll(event -> {
            int firstVisible = listView.getSelectionModel().getSelectedIndex();
            if (firstVisible < 0) {
                firstVisible = 0;
            }
            preloadAround(firstVisible);
        });

        VBox.setVgrow(listView, Priority.ALWAYS);
        getChildren().add(listView);
    }

    private void initializeData() {
        int totalLines = lineIndex.getLineCount();
        observableLines.clear();

        String[] placeholders = new String[totalLines];
        for (int i = 0; i < totalLines; i++) {
            placeholders[i] = "";
        }
        observableLines.addAll(placeholders);

        preloadAround(0);

        Platform.runLater(() -> {
            listView.getSelectionModel().select(0);
            listView.scrollTo(0);
        });
    }

    private void preloadAround(int centerIndex) {
        int preloadStart = Math.max(0, centerIndex - CACHE_SIZE / 2);
        int preloadEnd = Math.min(lineIndex.getLineCount(), centerIndex + CACHE_SIZE / 2);

        for (int i = preloadStart; i < preloadEnd; i++) {
            if (!lineCache.containsKey(i)) {
                String line = lineIndex.getLine(i);
                lineCache.put(i, line);
                final int idx = i;
                Platform.runLater(() -> {
                    if (idx < observableLines.size()) {
                        observableLines.set(idx, line);
                    }
                });
            }
        }

        cleanupCache(centerIndex);
    }

    private void cleanupCache(int currentCenter) {
        if (lineCache.size() > CACHE_SIZE * 2) {
            int removeBelow = currentCenter - CACHE_SIZE;
            int removeAbove = currentCenter + CACHE_SIZE;

            lineCache.keySet().removeIf(line ->
                    line < removeBelow || line > removeAbove
            );
        }
    }

    public void goToLine(int lineIndex) {
        if (lineIndex < 0 || lineIndex >= this.lineIndex.getLineCount()) {
            return;
        }

        preloadAround(lineIndex);

        Platform.runLater(() -> {
            listView.getSelectionModel().select(lineIndex);
            listView.scrollTo(lineIndex);
        });
    }

    public int getSelectedLine() {
        return listView.getSelectionModel().getSelectedIndex();
    }

    public void clearCache() {
        lineCache.clear();
    }

    public int getLineCount() {
        return lineIndex.getLineCount();
    }
}
