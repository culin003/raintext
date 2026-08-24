package com.raintext.util;

/**
 * Pure, UI-independent helpers for editor navigation and selection metrics.
 * Extracted so the logic behind search result navigation and selection stats
 * can be unit-tested without a JavaFX toolkit.
 */
public final class EditorUtils {

    private EditorUtils() {
    }

    /**
     * Index of the next search result, wrapping around to the first when past
     * the end. Returns -1 when there are no results.
     */
    public static int nextIndex(int current, int total) {
        if (total <= 0) return -1;
        if (current < 0) return 0;
        return (current + 1) % total;
    }

    /**
     * Index of the previous search result, wrapping around to the last when
     * before the start. Returns -1 when there are no results.
     */
    public static int prevIndex(int current, int total) {
        if (total <= 0) return -1;
        if (current < 0) return 0;
        return (current - 1 + total) % total;
    }

    public static int selectionChars(String text) {
        return (text == null) ? 0 : text.length();
    }

    public static int selectionLines(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.split("\n", -1).length;
    }
}
