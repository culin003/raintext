package com.raintext.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatusFormatterTest {

    @Test
    void encodingDefaultsToUtf8WhenBlank() {
        assertEquals("编码: UTF-8", StatusFormatter.encoding(null));
        assertEquals("编码: UTF-8", StatusFormatter.encoding(""));
        assertEquals("编码: UTF-8", StatusFormatter.encoding("  "));
    }

    @Test
    void encodingUsesProvidedValue() {
        assertEquals("编码: GBK", StatusFormatter.encoding("GBK"));
    }

    @Test
    void selectionEmptyWhenNoChars() {
        assertEquals("", StatusFormatter.selection(0, 0));
        assertEquals("", StatusFormatter.selection(-1, 3));
    }

    @Test
    void selectionFormatsCharsAndLines() {
        assertEquals("选中: 10 字符, 2 行", StatusFormatter.selection(10, 2));
        assertEquals("选中: 1,234 字符, 56 行", StatusFormatter.selection(1234, 56));
    }

    @Test
    void searchResultEmptyForNegative() {
        assertEquals("", StatusFormatter.searchResult(-1));
    }

    @Test
    void searchResultFormatsCount() {
        assertEquals("找到 0 个结果", StatusFormatter.searchResult(0));
        assertEquals("找到 42 个结果", StatusFormatter.searchResult(42));
    }

    @Test
    void searchStatusEmptyWhenNoTotal() {
        assertEquals("", StatusFormatter.searchStatus(0, 0));
        assertEquals("", StatusFormatter.searchStatus(3, 0));
    }

    @Test
    void searchStatusFormatsCurrentAndTotal() {
        assertEquals("结果 1 / 20", StatusFormatter.searchStatus(1, 20));
        assertEquals("结果 20 / 20", StatusFormatter.searchStatus(20, 20));
    }
}
