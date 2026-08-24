package com.raintext.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LineIndexTest {

    @TempDir
    Path tempDir;

    private MappedFileReader reader;
    private LineIndex lineIndex;
    private Path testFile;

    @BeforeEach
    void setUp() throws IOException {
        testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Line 1\nLine 2\nLine 3\nLine 4\nLine 5\n");
        reader = new MappedFileReader(testFile);
        lineIndex = new LineIndex(reader);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (reader != null) {
            reader.close();
        }
    }

    @Test
    void testLineCount() {
        assertEquals(5, lineIndex.getLineCount());
    }

    @Test
    void testGetLine() {
        assertEquals("Line 1", lineIndex.getLine(0));
        assertEquals("Line 2", lineIndex.getLine(1));
        assertEquals("Line 3", lineIndex.getLine(2));
        assertEquals("Line 4", lineIndex.getLine(3));
        assertEquals("Line 5", lineIndex.getLine(4));
    }

    @Test
    void testGetLineOutOfBounds() {
        assertThrows(IndexOutOfBoundsException.class, () -> lineIndex.getLine(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> lineIndex.getLine(5));
        assertThrows(IndexOutOfBoundsException.class, () -> lineIndex.getLine(100));
    }

    @Test
    void testFindLineForOffset() {
        long offset0 = lineIndex.getLineOffset(0);
        assertEquals(0, lineIndex.findLineForOffset(offset0));

        long offset1 = lineIndex.getLineOffset(1);
        assertEquals(1, lineIndex.findLineForOffset(offset1));
    }

    @Test
    void testGetLineOffset() {
        assertEquals(0, lineIndex.getLineOffset(0));
        assertTrue(lineIndex.getLineOffset(1) > 0);
    }

    @Test
    void testEmptyFile() throws IOException {
        Path emptyFile = tempDir.resolve("empty.txt");
        Files.writeString(emptyFile, "");
        try (MappedFileReader emptyReader = new MappedFileReader(emptyFile)) {
            LineIndex emptyIndex = new LineIndex(emptyReader);
            assertEquals(1, emptyIndex.getLineCount());
            assertEquals("", emptyIndex.getLine(0));
        }
    }

    @Test
    void testSingleLine() throws IOException {
        Path singleLineFile = tempDir.resolve("single.txt");
        Files.writeString(singleLineFile, "Only one line");
        try (MappedFileReader singleReader = new MappedFileReader(singleLineFile)) {
            LineIndex singleIndex = new LineIndex(singleReader);
            assertEquals(1, singleIndex.getLineCount());
            assertEquals("Only one line", singleIndex.getLine(0));
        }
    }

    @Test
    void testWindowsLineEndings() throws IOException {
        Path windowsFile = tempDir.resolve("windows.txt");
        Files.writeString(windowsFile, "Line 1\r\nLine 2\r\nLine 3\r\n");
        try (MappedFileReader windowsReader = new MappedFileReader(windowsFile)) {
            LineIndex windowsIndex = new LineIndex(windowsReader);
            assertEquals(3, windowsIndex.getLineCount());
            assertEquals("Line 1", windowsIndex.getLine(0));
            assertEquals("Line 2", windowsIndex.getLine(1));
            assertEquals("Line 3", windowsIndex.getLine(2));
        }
    }

    @Test
    void testMixedLineEndings() throws IOException {
        Path mixedFile = tempDir.resolve("mixed.txt");
        Files.writeString(mixedFile, "Line 1\nLine 2\r\nLine 3\rLine 4\n");
        try (MappedFileReader mixedReader = new MappedFileReader(mixedFile)) {
            LineIndex mixedIndex = new LineIndex(mixedReader);
            assertEquals(4, mixedIndex.getLineCount());
        }
    }

    @Test
    void testLargeFile() throws IOException {
        Path largeFile = tempDir.resolve("large.txt");
        StringBuilder sb = new StringBuilder();
        int expectedLines = 10000;
        for (int i = 0; i < expectedLines; i++) {
            sb.append("Line ").append(i).append("\n");
        }
        Files.writeString(largeFile, sb.toString());

        try (MappedFileReader largeReader = new MappedFileReader(largeFile)) {
            LineIndex largeIndex = new LineIndex(largeReader);
            assertEquals(expectedLines, largeIndex.getLineCount());
            assertEquals("Line 0", largeIndex.getLine(0));
            assertEquals("Line 9999", largeIndex.getLine(9999));
        }
    }

    @Test
    void testGetLineOffsets() {
        var offsets = lineIndex.getLineOffsets();
        assertEquals(5, offsets.size());
        assertEquals(0L, offsets.get(0));
    }

    @Test
    void testUnicodeContent() throws IOException {
        Path unicodeFile = tempDir.resolve("unicode.txt");
        Files.writeString(unicodeFile, "你好世界\nHello World\n日本語テスト\n");
        try (MappedFileReader unicodeReader = new MappedFileReader(unicodeFile)) {
            LineIndex unicodeIndex = new LineIndex(unicodeReader);
            assertEquals(3, unicodeIndex.getLineCount());
            assertEquals("你好世界", unicodeIndex.getLine(0));
            assertEquals("Hello World", unicodeIndex.getLine(1));
            assertEquals("日本語テスト", unicodeIndex.getLine(2));
        }
    }
}
