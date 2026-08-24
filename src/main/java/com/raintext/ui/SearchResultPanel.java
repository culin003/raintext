package com.raintext.ui;

import com.raintext.search.SearchEngine;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.List;
import java.util.function.Consumer;

public class SearchResultPanel extends VBox {
    private final ListView<SearchEngine.SearchResult> listView;
    private final ObservableList<SearchEngine.SearchResult> results;
    private final Label countLabel;
    private final Button toggleButton;
    private final Button closeButton;
    private final HBox header;
    private Consumer<SearchEngine.SearchResult> onResultClick;
    private boolean collapsed = false;
    private static final double EXPANDED_HEIGHT = 280;
    private static final double COLLAPSED_HEIGHT = 32;

    public SearchResultPanel() {
        this.listView = new ListView<>();
        this.results = FXCollections.observableArrayList();
        this.countLabel = new Label("搜索结果");
        this.toggleButton = new Button("▼");
        this.closeButton = new Button("✕");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        this.header = new HBox(8, toggleButton, countLabel, spacer, closeButton);

        setupUI();
    }

    private void setupUI() {
        countLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        toggleButton.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-font-size: 12px; -fx-padding: 2 6;");
        toggleButton.setOnAction(e -> toggleCollapse());

        closeButton.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-font-size: 12px; -fx-padding: 2 6;");
        closeButton.setOnAction(e -> hide());

        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(4, 8, 4, 8));
        header.setStyle("-fx-background-color: #e8e8e8; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");

        listView.setItems(results);
        listView.setMinHeight(0);
        listView.setPrefHeight(Region.USE_COMPUTED_SIZE);

        listView.setCellFactory(param -> new ListCell<>() {
            private final TextFlow flow = new TextFlow();
            private final Text prefix = new Text();
            private final Text before = new Text();
            private final Label hit = new Label();
            private final Text after = new Text();

            {
                hit.setStyle("-fx-background-color: #ffeb3b; -fx-text-fill: #b71c1c; -fx-padding: 0 1;");
                flow.getChildren().addAll(prefix, before, hit, after);
            }

            @Override
            protected void updateItem(SearchEngine.SearchResult item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                prefix.setText(String.format("行 %d, 列 %d: ",
                        item.getLine() + 1, item.getColumn() + 1));

                String line = item.getLineText();
                String match = item.getMatch();
                int col = item.getColumn();
                int len = match.length();

                int maxDisplay = 500;
                String display = line;
                int mStart = col;
                int mEnd = col + len;
                if (line.length() > maxDisplay) {
                    int context = Math.max(0, (maxDisplay - len) / 2);
                    int start = Math.max(0, mStart - context);
                    int end = Math.min(line.length(), mEnd + context);
                    display = (start > 0 ? "…" : "") + line.substring(start, end)
                            + (end < line.length() ? "…" : "");
                    mStart -= start;
                    mEnd -= start;
                }

                if (mStart < 0) mStart = 0;
                if (mEnd > display.length()) mEnd = display.length();
                if (mStart > mEnd) mStart = mEnd;

                before.setText(display.substring(0, mStart));
                hit.setText(display.substring(mStart, mEnd));
                after.setText(display.substring(mEnd));

                setText(null);
                setGraphic(flow);
            }
        });

        listView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                SearchEngine.SearchResult selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null && onResultClick != null) {
                    onResultClick.accept(selected);
                }
            }
        });

        VBox.setVgrow(listView, Priority.ALWAYS);
        getChildren().addAll(header, listView);

        setStyle("-fx-background-color: #ffffff; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");
        hide();
    }

    private void applyHeight(double h) {
        setMinHeight(h);
        setPrefHeight(h);
        setMaxHeight(h);
        requestLayout();
        if (getParent() != null) {
            getParent().requestLayout();
            Platform.runLater(() -> {
                if (getParent() != null) getParent().requestLayout();
            });
        }
    }

    private void toggleCollapse() {
        collapsed = !collapsed;
        toggleButton.setText(collapsed ? "▶" : "▼");
        header.setVisible(true);
        header.setManaged(true);
        listView.setVisible(!collapsed);
        listView.setManaged(!collapsed);
        applyHeight(collapsed ? COLLAPSED_HEIGHT : EXPANDED_HEIGHT);
    }

    public void setResults(List<SearchEngine.SearchResult> searchResults) {
        results.clear();
        if (searchResults != null) {
            results.addAll(searchResults);
            countLabel.setText("搜索结果 (" + searchResults.size() + ")");
            show();
        } else {
            countLabel.setText("搜索结果");
        }
    }

    public void clearResults() {
        results.clear();
        countLabel.setText("搜索结果");
    }

    public void hide() {
        header.setVisible(false);
        header.setManaged(false);
        listView.setVisible(false);
        listView.setManaged(false);
        setManaged(false);
        setVisible(false);
        applyHeight(0);
    }

    public void show() {
        collapsed = false;
        toggleButton.setText("▼");
        header.setVisible(true);
        header.setManaged(true);
        listView.setVisible(true);
        listView.setManaged(true);
        setManaged(true);
        setVisible(true);
        applyHeight(EXPANDED_HEIGHT);
    }

    public void setOnResultClick(Consumer<SearchEngine.SearchResult> handler) {
        this.onResultClick = handler;
    }

    public int getResultCount() {
        return results.size();
    }
}