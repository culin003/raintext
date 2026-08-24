package com.raintext.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EditorUtilsTest {

    @Test
    void nextIndexWrapsAround() {
        assertEquals(1, EditorUtils.nextIndex(0, 5));
        assertEquals(4, EditorUtils.nextIndex(3, 5));
        assertEquals(0, EditorUtils.nextIndex(4, 5));
    }

    @Test
    void nextIndexSingleResultStays() {
        assertEquals(0, EditorUtils.nextIndex(0, 1));
    }

    @Test
    void nextIndexEmptyReturnsMinusOne() {
        assertEquals(-1, EditorUtils.nextIndex(0, 0));
        assertEquals(-1, EditorUtils.nextIndex(-1, 0));
    }

    @Test
    void nextIndexNegativeCurrentStartsAtZero() {
        assertEquals(0, EditorUtils.nextIndex(-3, 5));
    }

    @Test
    void prevIndexWrapsAround() {
        assertEquals(4, EditorUtils.prevIndex(0, 5));
        assertEquals(0, EditorUtils.prevIndex(1, 5));
        assertEquals(2, EditorUtils.prevIndex(3, 5));
    }

    @Test
    void prevIndexSingleResultStays() {
        assertEquals(0, EditorUtils.prevIndex(0, 1));
    }

    @Test
    void prevIndexEmptyReturnsMinusOne() {
        assertEquals(-1, EditorUtils.prevIndex(0, 0));
    }

    @Test
    void prevIndexNegativeCurrentStartsAtZero() {
        assertEquals(0, EditorUtils.prevIndex(-3, 5));
    }

    @Test
    void selectionCharsCountsCharacters() {
        assertEquals(0, EditorUtils.selectionChars(null));
        assertEquals(0, EditorUtils.selectionChars(""));
        assertEquals(5, EditorUtils.selectionChars("hello"));
        assertEquals(3, EditorUtils.selectionChars("中文字"));
    }

    @Test
    void selectionLinesCountsLines() {
        assertEquals(0, EditorUtils.selectionLines(null));
        assertEquals(0, EditorUtils.selectionLines(""));
        assertEquals(1, EditorUtils.selectionLines("single line"));
        assertEquals(2, EditorUtils.selectionLines("line1\nline2"));
        assertEquals(3, EditorUtils.selectionLines("a\nb\nc"));
        // split("\n", -1) keeps the trailing empty element after a final newline.
        assertEquals(3, EditorUtils.selectionLines("a\nb\n"));
    }
}
