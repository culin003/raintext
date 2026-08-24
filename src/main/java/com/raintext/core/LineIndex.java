package com.raintext.core;

import java.nio.MappedByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class LineIndex {
    private final MappedFileReader reader;
    private long[] lineOffsets;
    private final Charset charset;
    private final int lineCount;
    private int lineOffsetCount = 0;

    public LineIndex(MappedFileReader reader, Charset charset) {
        this.reader = reader;
        this.charset = charset;
        this.lineOffsets = new long[4_000_000];
        buildIndex();
        this.lineCount = lineOffsetCount;
    }

    public LineIndex(MappedFileReader reader) {
        this(reader, StandardCharsets.UTF_8);
    }

    private void addOffset(long offset) {
        if (lineOffsetCount == lineOffsets.length) {
            long[] newArr = new long[lineOffsets.length * 2];
            System.arraycopy(lineOffsets, 0, newArr, 0, lineOffsets.length);
            lineOffsets = newArr;
        }
        lineOffsets[lineOffsetCount++] = offset;
    }

    private void buildIndex() {
        addOffset(0L);
        long fileSize = reader.fileSize();
        if (fileSize == 0) return;

        byte[] buf = new byte[64 * 1024];
        long pos = 0;
        int prevByte = -1;

        List<MappedByteBuffer> segs = reader.getSegments();
        for (MappedByteBuffer seg : segs) {
            seg.rewind();
            int remaining = seg.remaining();

            while (remaining > 0) {
                int toRead = Math.min(buf.length, remaining);
                seg.get(buf, 0, toRead);

                for (int i = 0; i < toRead; i++) {
                    int b = buf[i] & 0xFF;
                    if (b == '\n') {
                        if (prevByte == '\r') {
                            lineOffsetCount--;
                        }
                        long nextLineOffset = pos + i + 1;
                        if (nextLineOffset < fileSize) {
                            addOffset(nextLineOffset);
                        }
                    } else if (b == '\r') {
                        long nextLineOffset = pos + i + 1;
                        if (nextLineOffset < fileSize) {
                            addOffset(nextLineOffset);
                        }
                    }
                    prevByte = b;
                }
                pos += toRead;
                remaining -= toRead;
            }
        }
    }

    public int getLineCount() {
        return lineCount;
    }

    public String getLine(int lineIndex) {
        if (lineIndex < 0 || lineIndex >= lineCount) {
            throw new IndexOutOfBoundsException("Line index: " + lineIndex + ", line count: " + lineCount);
        }

        long start = lineOffsets[lineIndex];
        long end = (lineIndex + 1 < lineCount)
                ? lineOffsets[lineIndex + 1]
                : reader.fileSize();

        long maxLength = Math.min(end - start, 10 * 1024 * 1024);
        byte[] data = reader.readRange(start, start + maxLength);

        String line = new String(data, charset);
        if (line.endsWith("\n")) {
            line = line.substring(0, line.length() - 1);
        }
        if (line.endsWith("\r")) {
            line = line.substring(0, line.length() - 1);
        }
        return line;
    }

    public long getLineOffset(int lineIndex) {
        if (lineIndex < 0 || lineIndex >= lineCount) {
            throw new IndexOutOfBoundsException("Line index: " + lineIndex);
        }
        return lineOffsets[lineIndex];
    }

    public int findLineForOffset(long offset) {
        int index = java.util.Arrays.binarySearch(lineOffsets, 0, lineCount, offset);
        if (index >= 0) {
            return index;
        }
        return -index - 2;
    }

    public List<Long> getLineOffsets() {
        Long[] boxed = new Long[lineCount];
        for (int i = 0; i < lineCount; i++) {
            boxed[i] = lineOffsets[i];
        }
        return List.of(boxed);
    }
}
