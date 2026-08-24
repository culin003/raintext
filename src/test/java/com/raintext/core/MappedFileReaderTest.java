package com.raintext.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MappedFileReaderTest {

    @TempDir
    Path tempDir;

    private MappedFileReader reader;
    private Path testFile;

    @BeforeEach
    void setUp() throws IOException {
        testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Hello, World!\nThis is a test file.\nThird line here.\n");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (reader != null) {
            reader.close();
        }
    }

    @Test
    void testFileSize() throws IOException {
        reader = new MappedFileReader(testFile);
        long expectedSize = Files.size(testFile);
        assertEquals(expectedSize, reader.fileSize());
    }

    @Test
    void testReadRange() throws IOException {
        reader = new MappedFileReader(testFile);
        byte[] data = reader.readRange(0, 5);
        assertEquals("Hello", new String(data, StandardCharsets.UTF_8));
    }

    @Test
    void testReadFullContent() throws IOException {
        reader = new MappedFileReader(testFile);
        byte[] data = reader.readRange(0, reader.fileSize());
        String content = new String(data, StandardCharsets.UTF_8);
        assertTrue(content.contains("Hello, World!"));
        assertTrue(content.contains("This is a test file."));
        assertTrue(content.contains("Third line here."));
    }

    @Test
    void testGetByte() throws IOException {
        reader = new MappedFileReader(testFile);
        byte firstByte = reader.getByte(0);
        assertEquals((byte) 'H', firstByte);
    }

    @Test
    void testReadRangeOutOfBounds() throws IOException {
        reader = new MappedFileReader(testFile);
        assertThrows(IndexOutOfBoundsException.class, () -> reader.readRange(-1, 5));
        assertThrows(IndexOutOfBoundsException.class, () -> reader.readRange(0, reader.fileSize() + 1));
        assertThrows(IndexOutOfBoundsException.class, () -> reader.readRange(10, 5));
    }

    @Test
    void testEmptyRange() throws IOException {
        reader = new MappedFileReader(testFile);
        byte[] data = reader.readRange(5, 5);
        assertEquals(0, data.length);
    }

    @Test
    void testEmptyFile() throws IOException {
        Path emptyFile = tempDir.resolve("empty.txt");
        Files.writeString(emptyFile, "");
        reader = new MappedFileReader(emptyFile);
        assertEquals(0, reader.fileSize());
        byte[] data = reader.readRange(0, 0);
        assertEquals(0, data.length);
    }

    @Test
    void testLargeFile() throws IOException {
        Path largeFile = tempDir.resolve("large.txt");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100000; i++) {
            sb.append("Line ").append(i).append("\n");
        }
        Files.writeString(largeFile, sb.toString());

        reader = new MappedFileReader(largeFile);
        assertTrue(reader.fileSize() > 0);

        byte[] data = reader.readRange(0, 100);
        String start = new String(data, StandardCharsets.UTF_8);
        assertTrue(start.startsWith("Line 0"));
    }

    @Test
    void testSegmentCount() throws IOException {
        reader = new MappedFileReader(testFile);
        assertTrue(reader.getSegmentCount() >= 1);
    }
}
