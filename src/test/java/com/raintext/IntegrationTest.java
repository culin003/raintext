package com.raintext;

import com.raintext.core.LineIndex;
import com.raintext.core.MappedFileReader;
import com.raintext.search.SearchEngine;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class IntegrationTest {

    private final String testFilePath = "/home/cooper/下载/sidra_SMC202_取药接口超时日志/smart-cabinet-2026-08-11.2.log";

    @Test
    void testLargeFileLineCount() throws Exception {
        if (!new File(testFilePath).exists()) {
            System.out.println("Test file not found, skipping");
            return;
        }

        Path path = Paths.get(testFilePath);
        try (MappedFileReader reader = new MappedFileReader(path)) {
            LineIndex index = new LineIndex(reader);
            System.out.println("Total lines: " + index.getLineCount());
            assertTrue(index.getLineCount() > 400000, "Should have >400k lines");
        }
    }

    @Test
    void testLargeFileGetLine() throws Exception {
        if (!new File(testFilePath).exists()) return;

        Path path = Paths.get(testFilePath);
        try (MappedFileReader reader = new MappedFileReader(path)) {
            LineIndex index = new LineIndex(reader);

            String first = index.getLine(0);
            assertNotNull(first);
            System.out.println("Line 1: " + first.substring(0, Math.min(100, first.length())));

            int mid = index.getLineCount() / 2;
            String middle = index.getLine(mid);
            assertNotNull(middle);
            System.out.println("Line " + (mid + 1) + ": " + middle.substring(0, Math.min(100, middle.length())));

            String last = index.getLine(index.getLineCount() - 1);
            assertNotNull(last);
            System.out.println("Line " + index.getLineCount() + ": " + last.substring(0, Math.min(100, last.length())));
        }
    }

    @Test
    void testLargeFileSearch() throws Exception {
        if (!new File(testFilePath).exists()) return;

        Path path = Paths.get(testFilePath);
        try (MappedFileReader reader = new MappedFileReader(path)) {
            LineIndex index = new LineIndex(reader);
            SearchEngine engine = new SearchEngine();

            List<SearchEngine.SearchResult> results = engine.search(index, "ERROR", false).join();
            System.out.println("Found " + results.size() + " ERROR matches");
            assertFalse(results.isEmpty(), "Should find ERROR in log file");

            if (!results.isEmpty()) {
                SearchEngine.SearchResult first = results.get(0);
                System.out.println("  First: line " + first.getLine() + ", col " + first.getColumn());
                System.out.println("  Match: " + first.getMatch());
            }

            engine.shutdown();
        }
    }

    @Test
    void testLoadVisibleChunk() throws Exception {
        if (!new File(testFilePath).exists()) return;

        Path path = Paths.get(testFilePath);
        try (MappedFileReader reader = new MappedFileReader(path)) {
            LineIndex index = new LineIndex(reader);

            int fromLine = 1000;
            int visibleCount = 50;
            int endLine = Math.min(fromLine + visibleCount, index.getLineCount());

            StringBuilder sb = new StringBuilder();
            long start = System.currentTimeMillis();
            for (int i = fromLine; i < endLine; i++) {
                if (i > fromLine) sb.append("\n");
                sb.append(index.getLine(i));
            }
            long elapsed = System.currentTimeMillis() - start;

            System.out.println("Loaded lines " + (fromLine + 1) + "-" + endLine + " in " + elapsed + "ms");
            System.out.println("Chunk size: " + sb.length() + " bytes");
            assertTrue(elapsed < 1000, "Loading 50 lines should be fast");
        }
    }

    @Test
    void testIndexBuildTime() throws Exception {
        if (!new File(testFilePath).exists()) return;

        Path path = Paths.get(testFilePath);
        long fileSize = new File(testFilePath).length();
        System.out.println("File size: " + (fileSize / 1024 / 1024) + " MB");

        long start = System.currentTimeMillis();
        MappedFileReader reader = new MappedFileReader(path);
        long mmapMs = System.currentTimeMillis() - start;
        System.out.println("mmap: " + mmapMs + "ms");

        start = System.currentTimeMillis();
        LineIndex index = new LineIndex(reader);
        long indexMs = System.currentTimeMillis() - start;
        double throughput = fileSize / 1024.0 / 1024.0 / (indexMs / 1000.0);
        long totalMs = mmapMs + indexMs;
        System.out.println("Index build: " + indexMs + "ms (" + index.getLineCount() + " lines)");
        System.out.println("Throughput: " + String.format("%.0f", throughput) + " MB/s");
        System.out.println("Total open time: " + totalMs + "ms");
        System.out.println("Estimated for 1.6GB: " + String.format("%.1f", 1600 / throughput) + "s");

        reader.close();
    }

    @Test
    void testRandomAccessPerformance() throws Exception {
        if (!new File(testFilePath).exists()) return;

        Path path = Paths.get(testFilePath);
        try (MappedFileReader reader = new MappedFileReader(path)) {
            LineIndex index = new LineIndex(reader);

            int[] positions = {0, 50000, 100000, 150000, 200000, 250000, 300000, 350000, 400000};
            long totalMs = 0;
            int count = 0;

            for (int pos : positions) {
                if (pos >= index.getLineCount()) continue;
                long start = System.currentTimeMillis();
                String line = index.getLine(pos);
                long elapsed = System.currentTimeMillis() - start;
                totalMs += elapsed;
                count++;
                System.out.println("Jump to line " + (pos + 1) + ": " + elapsed + "ms, length=" + line.length());
            }

            System.out.println("Average: " + (totalMs / count) + "ms per jump");
            assertTrue(totalMs / count < 10, "Each jump should be <10ms");
        }
    }
}
