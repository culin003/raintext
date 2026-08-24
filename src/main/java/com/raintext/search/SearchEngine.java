package com.raintext.search;

import com.raintext.core.LineIndex;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public class SearchEngine {
    private final ExecutorService executor;

    public static class SearchResult {
        private final int line;
        private final int column;
        private final String match;
        private final String lineText;

        public SearchResult(int line, int column, String match, String lineText) {
            this.line = line;
            this.column = column;
            this.match = match;
            this.lineText = lineText;
        }

        public int getLine() { return line; }
        public int getColumn() { return column; }
        public String getMatch() { return match; }
        public String getLineText() { return lineText; }
    }

    public SearchEngine() {
        this.executor = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
    }

    public SearchEngine(ExecutorService executor) {
        this.executor = executor;
    }

    public CompletableFuture<List<SearchResult>> search(
            LineIndex lineIndex,
            String query,
            boolean caseSensitive
    ) {
        return search(lineIndex, query, false, caseSensitive);
    }

    public CompletableFuture<List<SearchResult>> search(
            LineIndex lineIndex,
            String query,
            boolean regex,
            boolean caseSensitive
    ) {
        return CompletableFuture.supplyAsync(() -> {
            List<SearchResult> results = new CopyOnWriteArrayList<>();
            Pattern pattern = regex
                    ? Pattern.compile(query, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE)
                    : null;

            int lineCount = lineIndex.getLineCount();

            IntStream.range(0, lineCount).parallel()
                    .forEach(lineIdx -> {
                        try {
                            String line = lineIndex.getLine(lineIdx);
                            if (pattern != null) {
                                Matcher matcher = pattern.matcher(line);
                                while (matcher.find()) {
                                    results.add(new SearchResult(
                                            lineIdx, matcher.start(), matcher.group(), line
                                    ));
                                }
                            } else {
                                String searchLine = caseSensitive ? line : line.toLowerCase();
                                String searchQuery = caseSensitive ? query : query.toLowerCase();
                                int fromIndex = 0;
                                while ((fromIndex = searchLine.indexOf(searchQuery, fromIndex)) != -1) {
                                    String actual = line.substring(fromIndex, fromIndex + query.length());
                                    results.add(new SearchResult(lineIdx, fromIndex, actual, line));
                                    fromIndex++;
                                }
                            }
                        } catch (Exception e) {
                            // Skip lines that can't be processed
                        }
                    });

            results.sort(Comparator.comparingInt(SearchResult::getLine));
            return new ArrayList<>(results);
        }, executor);
    }

    public void shutdown() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
