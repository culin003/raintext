package com.raintext.editor;

import java.util.Map;
import java.util.TreeMap;

public class EditBuffer {
    private final TreeMap<Long, EditOperation> edits = new TreeMap<>();
    private long totalInsertBytes = 0;
    private long totalDeleteBytes = 0;

    public static class EditOperation {
        private final long offset;
        private final byte[] deleted;
        private final byte[] inserted;

        public EditOperation(long offset, byte[] deleted, byte[] inserted) {
            this.offset = offset;
            this.deleted = deleted != null ? deleted : new byte[0];
            this.inserted = inserted != null ? inserted : new byte[0];
        }

        public long getOffset() { return offset; }
        public byte[] getDeleted() { return deleted; }
        public byte[] getInserted() { return inserted; }
        public int getDeletedLength() { return deleted.length; }
        public int getInsertedLength() { return inserted.length; }
    }

    public void insert(long offset, byte[] data) {
        if (data == null || data.length == 0) return;
        edits.put(offset, new EditOperation(offset, new byte[0], data));
        totalInsertBytes += data.length;
    }

    public void delete(long offset, byte[] deleted) {
        if (deleted == null || deleted.length == 0) return;
        edits.put(offset, new EditOperation(offset, deleted, new byte[0]));
        totalDeleteBytes += deleted.length;
    }

    public void replace(long offset, byte[] deleted, byte[] inserted) {
        edits.put(offset, new EditOperation(offset, deleted, inserted));
        if (deleted != null) totalDeleteBytes += deleted.length;
        if (inserted != null) totalInsertBytes += inserted.length;
    }

    public void clear() {
        edits.clear();
        totalInsertBytes = 0;
        totalDeleteBytes = 0;
    }

    public boolean hasEdits() {
        return !edits.isEmpty();
    }

    public TreeMap<Long, EditOperation> getEdits() {
        return edits;
    }

    public long getNetByteDelta() {
        return totalInsertBytes - totalDeleteBytes;
    }

    public long getOriginalOffsetWithDelta(long originalOffset) {
        long adjustedOffset = originalOffset;
        for (Map.Entry<Long, EditOperation> entry : edits.entrySet()) {
            if (entry.getKey() > originalOffset) break;
            EditOperation op = entry.getValue();
            adjustedOffset += op.getInsertedLength() - op.getDeletedLength();
        }
        return adjustedOffset;
    }
}
