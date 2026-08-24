package com.raintext.core;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;

public class MappedFileReader implements AutoCloseable {
    private static final long SEGMENT_SIZE = 256 * 1024 * 1024;

    private final RandomAccessFile raf;
    private final FileChannel channel;
    private final List<MappedByteBuffer> segments = new ArrayList<>();
    private long fileSize;
    private final Path filePath;

    public MappedFileReader(Path filePath) throws IOException {
        this.filePath = filePath;
        this.raf = new RandomAccessFile(filePath.toFile(), "rw");
        this.channel = raf.getChannel();
        this.fileSize = channel.size();
        mapSegments();
    }

    private void mapSegments() throws IOException {
        segments.clear();
        long position = 0;
        while (position < fileSize) {
            long size = Math.min(SEGMENT_SIZE, fileSize - position);
            MappedByteBuffer buffer = channel.map(
                    FileChannel.MapMode.READ_WRITE, position, size
            );
            segments.add(buffer);
            position += size;
        }
    }

    public byte getByte(long offset) {
        int segIndex = (int) (offset / SEGMENT_SIZE);
        int localOffset = (int) (offset % SEGMENT_SIZE);
        return segments.get(segIndex).get(localOffset);
    }

    public byte[] readRange(long start, long end) {
        if (start < 0 || end > fileSize || start > end) {
            throw new IndexOutOfBoundsException(
                    String.format("Range [%d, %d) out of bounds [0, %d)", start, end, fileSize)
            );
        }

        int length = (int) (end - start);
        if (length == 0) {
            return new byte[0];
        }

        byte[] data = new byte[length];
        int pos = 0;
        long offset = start;

        while (offset < end) {
            int segIndex = (int) (offset / SEGMENT_SIZE);
            int localOffset = (int) (offset % SEGMENT_SIZE);

            MappedByteBuffer buffer = segments.get(segIndex);
            buffer.position(localOffset);

            int available = Math.min(buffer.remaining(), length - pos);
            buffer.get(data, pos, available);

            pos += available;
            offset += available;
        }
        return data;
    }

    public void writeContent(byte[] content) throws IOException {
        raf.setLength(0);
        raf.seek(0);
        raf.write(content);
        fileSize = content.length;
        mapSegments();
    }

    public long fileSize() {
        return fileSize;
    }

    /**
     * Scan bytes from start to end, calling consumer with each byte's absolute offset and value.
     * Zero-copy: reads directly from memory-mapped segments.
     */
    public void scanBytes(long start, long end, java.util.function.BiConsumer<Long, Integer> consumer) {
        if (start < 0 || end > fileSize || start >= end) return;

        long offset = start;
        while (offset < end) {
            int segIndex = (int) (offset / SEGMENT_SIZE);
            int localOffset = (int) (offset % SEGMENT_SIZE);

            MappedByteBuffer buffer = segments.get(segIndex);
            int limit = (int) Math.min(SEGMENT_SIZE - localOffset, end - offset);

            for (int i = 0; i < limit; i++) {
                consumer.accept(offset + i, buffer.get(localOffset + i) & 0xFF);
            }
            offset += limit;
        }
    }

    public List<MappedByteBuffer> getSegments() {
        return segments;
    }

    public int getSegmentCount() {
        return segments.size();
    }

    public Path getFilePath() {
        return filePath;
    }

    public FileChannel openChannel() throws IOException {
        return new java.io.FileInputStream(filePath.toFile()).getChannel();
    }

    /**
     * Read first N lines directly from file without LineIndex.
     * Returns the text and how many bytes were consumed.
     */
    public String readFirstLines(int lineCount) {
        if (fileSize == 0) return "";

        long endPos = Math.min(fileSize, 8 * 1024 * 1024);
        byte[] data = readRange(0, endPos);

        int linesFound = 0;
        int lastNewline = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == '\n') {
                linesFound++;
                if (linesFound >= lineCount) {
                    return new String(data, 0, i + 1, java.nio.charset.StandardCharsets.UTF_8);
                }
                lastNewline = i + 1;
            }
        }
        return new String(data, 0, Math.min(data.length, (int) fileSize), java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public void close() throws IOException {
        segments.clear();
        channel.close();
        raf.close();
    }
}
