package io.github.theamethyx.femboyclient.core.hud;

import java.util.ArrayDeque;
import java.util.Deque;

/** Undo and redo stacks of snapshots. Record a snapshot before each change; undo hands back the previous one. */
public final class UndoHistory<T> {
	private final int limit;
	private final Deque<T> undo = new ArrayDeque<>();
	private final Deque<T> redo = new ArrayDeque<>();

	public UndoHistory(int limit) {
		this.limit = limit;
	}

	/** Call before changing the state, with a snapshot of the state as it is now. Clears the redo stack. */
	public void record(T before) {
		undo.push(before);
		while (undo.size() > limit) {
			undo.removeLast();
		}

		redo.clear();
	}

	/** The state to go back to, given the current one (which becomes redoable), or {@code null}. */
	public T undo(T current) {
		if (undo.isEmpty()) {
			return null;
		}

		redo.push(current);
		return undo.pop();
	}

	/** The state to go forward to, given the current one, or {@code null}. */
	public T redo(T current) {
		if (redo.isEmpty()) {
			return null;
		}

		undo.push(current);
		return redo.pop();
	}

	public boolean canUndo() {
		return !undo.isEmpty();
	}

	public boolean canRedo() {
		return !redo.isEmpty();
	}

	public void clear() {
		undo.clear();
		redo.clear();
	}
}
