package com.raintext.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

public class StatusBar extends HBox {
    private final Label lineCountLabel;
    private final Label cursorPositionLabel;
    private final Label fileSizeLabel;
    private final Label searchResultLabel;
    private final Label modifiedLabel;
    private final Label encodingLabel;
    private final Label selectionLabel;

    public StatusBar() {
        setPadding(new Insets(5, 10, 5, 10));
        setSpacing(20);
        setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");

        lineCountLabel = new Label("总行数: 0");
        cursorPositionLabel = new Label("行 1, 列 1");
        fileSizeLabel = new Label("文件大小: 0 B");
        modifiedLabel = new Label("");
        searchResultLabel = new Label("");
        encodingLabel = new Label("编码: UTF-8");
        selectionLabel = new Label("");

        getChildren().addAll(
                cursorPositionLabel,
                lineCountLabel,
                fileSizeLabel,
                encodingLabel,
                selectionLabel,
                modifiedLabel,
                searchResultLabel
        );
    }

    public void setLineCount(int count) {
        lineCountLabel.setText(String.format("总行数: %,d", count));
    }

    public void setCursorPosition(int line, int column) {
        cursorPositionLabel.setText(String.format("行 %,d, 列 %,d", line + 1, column + 1));
    }

    public void setFileSize(long bytes) {
        String size;
        if (bytes < 1024) {
            size = bytes + " B";
        } else if (bytes < 1024 * 1024) {
            size = String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            size = String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            size = String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
        fileSizeLabel.setText("文件大小: " + size);
    }

    public void setModified(boolean modified) {
        if (modified) {
            modifiedLabel.setText("● 已修改");
            modifiedLabel.setStyle("-fx-text-fill: #ff6600;");
        } else {
            modifiedLabel.setText("");
            modifiedLabel.setStyle("");
        }
    }

    public void setEncoding(String encoding) {
        encodingLabel.setText(StatusFormatter.encoding(encoding));
    }

    public void setSelection(int charCount, int lineCount) {
        selectionLabel.setText(StatusFormatter.selection(charCount, lineCount));
    }

    public void setSearchResult(int count) {
        searchResultLabel.setText(StatusFormatter.searchResult(count));
    }

    public void setSearchStatus(int current, int total) {
        searchResultLabel.setText(StatusFormatter.searchStatus(current, total));
    }

    public void clearSearchResult() {
        searchResultLabel.setText("");
    }
}
