package com.raintext.search;

import com.raintext.core.LineIndex;
import com.raintext.core.MappedFileReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class SearchEngineTest {

    @TempDir
    Path tempDir;

    private MappedFileReader reader;
    private LineIndex lineIndex;
    private SearchEngine searchEngine;
    private Path testFile;

    @BeforeEach
    void setUp() throws IOException {
        testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Hello World\nJava Programming\nHello Java\nTest Line\nJava Virtual Machine\n");
        reader = new MappedFileReader(testFile);
        lineIndex = new LineIndex(reader);
        searchEngine = new SearchEngine();
    }

    @AfterEach
    void tearDown() throws IOException {
        searchEngine.shutdown();
        if (reader != null) {
            reader.close();
        }
    }

    @Test
    void testBasicSearch() throws Exception {
        CompletableFuture<List<SearchEngine.SearchResult>> future =
                searchEngine.search(lineIndex, "Hello", false);

        List<SearchEngine.SearchResult> results = future.get();

        assertEquals(2, results.size());
        assertEquals(0, results.get(0).getLine());
        assertEquals(2, results.get(1).getLine());
    }

    @Test
    void testSearchCaseSensitive() throws Exception {
        CompletableFuture<List<SearchEngine.SearchResult>> future =
                searchEngine.search(lineIndex, "hello", false, true);

        List<SearchEngine.SearchResult> results = future.get();
        assertEquals(0, results.size());
    }

    @Test
    void testSearchCaseInsensitive() throws Exception {
        CompletableFuture<List<SearchEngine.SearchResult>> future =
                searchEngine.search(lineIndex, "hello", false, false);

        List<SearchEngine.SearchResult> results = future.get();
        assertEquals(2, results.size());
    }

    @Test
    void testSearchJava() throws Exception {
        CompletableFuture<List<SearchEngine.SearchResult>> future =
                searchEngine.search(lineIndex, "Java", false);

        List<SearchEngine.SearchResult> results = future.get();
        assertEquals(3, results.size());
    }

    @Test
    void testSearchNotFound() throws Exception {
        CompletableFuture<List<SearchEngine.SearchResult>> future =
                searchEngine.search(lineIndex, "Python", false);

        List<SearchEngine.SearchResult> results = future.get();
        assertEquals(0, results.size());
    }

    @Test
    void testSearchRegex() throws Exception {
        CompletableFuture<List<SearchEngine.SearchResult>> future =
                searchEngine.search(lineIndex, "Java.*Machine", true, false);

        List<SearchEngine.SearchResult> results = future.get();
        assertEquals(1, results.size());
        assertEquals(4, results.get(0).getLine());
    }

    @Test
    void testSearchRegexSimple() throws Exception {
        CompletableFuture<List<SearchEngine.SearchResult>> future =
                searchEngine.search(lineIndex, "Hello\\s+\\w+", true, false);

        List<SearchEngine.SearchResult> results = future.get();
        assertEquals(2, results.size());
    }

    @Test
    void testSearchEmptyQuery() throws Exception {
        CompletableFuture<List<SearchEngine.SearchResult>> future =
                searchEngine.search(lineIndex, "", false);

        List<SearchEngine.SearchResult> results = future.get();
        assertEquals(0, results.size());
    }

    @Test
    void testSearchResultsContainCorrectColumns() throws Exception {
        CompletableFuture<List<SearchEngine.SearchResult>> future =
                searchEngine.search(lineIndex, "World", false);

        List<SearchEngine.SearchResult> results = future.get();
        assertEquals(1, results.size());
        assertEquals(6, results.get(0).getColumn());
        assertEquals("World", results.get(0).getMatch());
    }

    @Test
    void testSearchMultipleMatchesInLine() throws Exception {
        Files.writeString(testFile, "Java Java Java\n");
        try (MappedFileReader newReader = new MappedFileReader(testFile)) {
            LineIndex newIndex = new LineIndex(newReader);

            CompletableFuture<List<SearchEngine.SearchResult>> future =
                    searchEngine.search(newIndex, "Java", false);

            List<SearchEngine.SearchResult> results = future.get();
            assertEquals(3, results.size());
            assertEquals(0, results.get(0).getColumn());
            assertEquals(5, results.get(1).getColumn());
            assertEquals(10, results.get(2).getColumn());
        }
    }

    @Test
    void testSearchSpecialCharacters() throws Exception {
        Files.writeString(testFile, "price is $10.00\nversion 1.0.0\n");
        try (MappedFileReader newReader = new MappedFileReader(testFile)) {
            LineIndex newIndex = new LineIndex(newReader);

            CompletableFuture<List<SearchEngine.SearchResult>> future =
                    searchEngine.search(newIndex, "$10", false);

            List<SearchEngine.SearchResult> results = future.get();
            assertEquals(1, results.size());
            assertEquals(0, results.get(0).getLine());
        }
    }

    @Test
    void testSearchNewline() throws Exception {
        Files.writeString(testFile, "line1\nline2\n");
        try (MappedFileReader newReader = new MappedFileReader(testFile)) {
            LineIndex newIndex = new LineIndex(newReader);

            CompletableFuture<List<SearchEngine.SearchResult>> future =
                    searchEngine.search(newIndex, "line", false);

            List<SearchEngine.SearchResult> results = future.get();
            assertEquals(2, results.size());
        }
    }

    @Test
    void testSearchChinese() throws Exception {
        Files.writeString(testFile, "你好世界\nJava编程\n测试行\n");
        try (MappedFileReader newReader = new MappedFileReader(testFile)) {
            LineIndex newIndex = new LineIndex(newReader);

            CompletableFuture<List<SearchEngine.SearchResult>> future =
                    searchEngine.search(newIndex, "Java", false);

            List<SearchEngine.SearchResult> results = future.get();
            assertEquals(1, results.size());
            assertEquals(1, results.get(0).getLine());
        }
    }
}
