package com.raintext.editor;

import java.util.ArrayDeque;
import java.util.Deque;

public class UndoManager {
    private final Deque<EditAction> undoStack = new ArrayDeque<>();
    private final Deque<EditAction> redoStack = new ArrayDeque<>();
    private static final int MAX_STACK_SIZE = 1000;

    public static class EditAction {
        private final long offset;
        private final String deletedText;
        private final String insertedText;

        public EditAction(long offset, String deletedText, String insertedText) {
            this.offset = offset;
            this.deletedText = deletedText;
            this.insertedText = insertedText;
        }

        public long getOffset() { return offset; }
        public String getDeletedText() { return deletedText; }
        public String getInsertedText() { return insertedText; }

        public EditAction getReverse() {
            return new EditAction(offset, insertedText, deletedText);
        }
    }

    public void push(EditAction action) {
        undoStack.push(action);
        redoStack.clear();
        if (undoStack.size() > MAX_STACK_SIZE) {
            ArrayDeque<EditAction> trimmed = new ArrayDeque<>();
            int count = 0;
            for (EditAction a : undoStack) {
                if (count >= MAX_STACK_SIZE) break;
                trimmed.addFirst(a);
                count++;
            }
            undoStack.clear();
            undoStack.addAll(trimmed);
        }
    }

    public EditAction undo() {
        if (undoStack.isEmpty()) return null;
        EditAction action = undoStack.pop();
        redoStack.push(action);
        return action.getReverse();
    }

    public EditAction redo() {
        if (redoStack.isEmpty()) return null;
        EditAction action = redoStack.pop();
        undoStack.push(action);
        return action.getReverse();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }
}
