package com.raintext.ui;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

public class SearchDialog extends Dialog<ButtonType> {
    private final TextField searchField;
    private final CheckBox caseSensitiveCheckBox;
    private final CheckBox regexCheckBox;
    private final Consumer<SearchDialog> onSearch;

    public SearchDialog(Consumer<SearchDialog> onSearch) {
        this.onSearch = onSearch;

        setTitle("搜索");
        setHeaderText("输入搜索内容");

        searchField = new TextField();
        searchField.setPromptText("搜索内容...");
        searchField.setPrefWidth(300);

        caseSensitiveCheckBox = new CheckBox("区分大小写");
        regexCheckBox = new CheckBox("正则表达式");

        HBox optionsBox = new HBox(10, caseSensitiveCheckBox, regexCheckBox);
        optionsBox.setPadding(new Insets(10, 0, 0, 0));

        VBox content = new VBox(10, searchField, optionsBox);
        content.setPadding(new Insets(10));

        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        searchField.setOnAction(e -> doSearch());

        setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                doSearch();
            }
            return null;
        });
    }

    private void doSearch() {
        if (onSearch != null) {
            onSearch.accept(this);
        }
    }

    public String getSearchText() {
        return searchField.getText();
    }

    public void setInitialQuery(String query) {
        if (query != null) {
            searchField.setText(query);
        }
    }

    public boolean isCaseSensitive() {
        return caseSensitiveCheckBox.isSelected();
    }

    public boolean isRegex() {
        return regexCheckBox.isSelected();
    }
}
