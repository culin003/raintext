package com.raintext.ui;

/**
 * Pure, UI-independent formatters for the status bar text.
 * Extracted so the status bar labels can be unit-tested without a JavaFX
 * toolkit.
 */
public final class StatusFormatter {

    private StatusFormatter() {
    }

    public static String encoding(String encoding) {
        if (encoding == null || encoding.trim().isEmpty()) encoding = "UTF-8";
        return "编码: " + encoding;
    }

    public static String selection(int charCount, int lineCount) {
        if (charCount <= 0) {
            return "";
        }
        return String.format("选中: %,d 字符, %,d 行", charCount, lineCount);
    }

    public static String searchResult(int count) {
        if (count >= 0) {
            return String.format("找到 %,d 个结果", count);
        }
        return "";
    }

    public static String searchStatus(int current, int total) {
        if (total > 0) {
            return String.format("结果 %,d / %,d", current, total);
        }
        return "";
    }
}
