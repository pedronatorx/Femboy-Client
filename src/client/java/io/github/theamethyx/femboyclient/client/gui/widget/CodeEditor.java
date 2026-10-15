package io.github.theamethyx.femboyclient.client.gui.widget;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;

/**
 * Multi-line code editor with Java syntax highlighting, line numbers, selection (shift + arrows or mouse
 * drag), undo (Ctrl+Z), copy, cut, paste, select all, Tab indentation and automatic indentation on Enter.
 */
public final class CodeEditor extends Widget {
	private static final int LINE_HEIGHT = 10;
	private static final int PAD = 4;
	private static final int MAX_CHARS = 20_000;
	private static final String INDENT = "    ";
	private static final Set<String> KEYWORDS = Set.of("abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "continue",
			"default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "if", "implements", "import", "instanceof", "int",
			"interface", "long", "new", "package", "private", "protected", "public", "record", "return", "short", "static", "super", "switch", "this",
			"throw", "throws", "try", "var", "void", "volatile", "while", "yield", "true", "false", "null");

	private static final int COLOR_KEYWORD = 0xFFC792EA;
	private static final int COLOR_STRING = 0xFFA5D6A7;
	private static final int COLOR_COMMENT = 0xFF6B6B7B;
	private static final int COLOR_NUMBER = 0xFFF2B36B;
	private static final int COLOR_TYPE = 0xFF8EC5FF;

	private final List<String> lines = new ArrayList<>(List.of(""));
	private final Deque<State> undo = new ArrayDeque<>();
	private final int preferredHeight;
	private Consumer<String> onChange = text -> { };
	private int row;
	private int col;
	private int anchorRow = -1;
	private int anchorCol;
	private int scrollRow;
	private int scrollX;
	private boolean dragging;
	/** Scroll to the cursor on the next frame (after typing or moving it, not after wheel scrolling). */
	private boolean reveal = true;

	private record State(List<String> lines, int row, int col) {
	}

	public CodeEditor(int preferredHeight) {
		this.preferredHeight = preferredHeight;
		this.height = preferredHeight;
	}

	public CodeEditor onChange(Consumer<String> onChange) {
		this.onChange = onChange;
		return this;
	}

	public String text() {
		return String.join("\n", lines);
	}

	public void setText(String text) {
		lines.clear();
		lines.addAll(List.of(text.replace("\r\n", "\n").replace("\t", INDENT).split("\n", -1)));
		row = 0;
		col = 0;
		anchorRow = -1;
		scrollRow = 0;
		scrollX = 0;
		undo.clear();
	}

	@Override
	public int preferredHeight(UiContext ui, int width) {
		return preferredHeight;
	}

	private int gutter(UiContext ui) {
		return ui.textWidth(String.valueOf(Math.max(99, lines.size()))) + 8;
	}

	private int visibleRows() {
		return Math.max(1, (height - 2 * PAD) / LINE_HEIGHT);
	}

	// ----- rendering -------------------------------------------------------------------------

	@Override
	public void render(UiContext ui) {
		boolean focused = ui.isFocused(this);
		ui.roundRect(x, y, width, height, 0xFF0E0E12);
		ui.outline(x, y, width, height, focused ? Theme.ACCENT_DIM : Theme.BORDER);
		int gutter = gutter(ui);
		ui.rect(x + 1, y + 1, gutter - 2, height - 2, 0xFF131318);

		int textX = x + gutter + 2;
		int textW = width - gutter - 6;
		if (reveal) {
			keepCursorVisible(ui, textW);
			reveal = false;
		}

		scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, lines.size() - visibleRows())));
		boolean inBlockComment = blockCommentStateBefore(scrollRow);
		int[] selection = selection();

		for (int i = 0; i < visibleRows() && scrollRow + i < lines.size(); i++) {
			int r = scrollRow + i;
			int ly = y + PAD + i * LINE_HEIGHT;
			ui.textRight(String.valueOf(r + 1), x + gutter - 4, ly + 1, r == row && focused ? Theme.TEXT_SECONDARY : Theme.TEXT_MUTED);
			ui.pushClip(textX - 1, y + 1, textW + 2, height - 2);
			String line = lines.get(r);
			if (selection != null && r >= selection[0] && r <= selection[2]) {
				int from = r == selection[0] ? selection[1] : 0;
				int to = r == selection[2] ? selection[3] : line.length();
				int sx = textX - scrollX + ui.textWidth(line.substring(0, from));
				int sw = Math.max(r < selection[2] ? 3 : 0, ui.textWidth(line.substring(from, to)));
				ui.rect(sx, ly, sw, LINE_HEIGHT, Theme.withAlpha(Theme.ACCENT, 0.35f));
			}

			inBlockComment = drawHighlighted(ui, line, textX - scrollX, ly + 1, inBlockComment);
			if (r == row && focused && (System.currentTimeMillis() / 500) % 2 == 0) {
				ui.rect(textX - scrollX + ui.textWidth(line.substring(0, col)), ly, 1, LINE_HEIGHT, Theme.ACCENT_STRONG);
			}

			ui.popClip();
		}

		if (lines.size() > visibleRows()) {
			int trackH = height - 4;
			int thumbH = Math.max(10, trackH * visibleRows() / lines.size());
			int thumbY = y + 2 + (trackH - thumbH) * scrollRow / Math.max(1, lines.size() - visibleRows());
			ui.rect(x + width - 3, thumbY, 2, thumbH, Theme.BORDER_STRONG);
		}

		if (hovered(ui)) {
			ui.textCursor();
		}
	}

	/** Draws one line with syntax colors; returns whether a block comment continues past it. */
	private boolean drawHighlighted(UiContext ui, String line, int x0, int y0, boolean inBlockComment) {
		int i = 0;
		int px = x0;
		while (i < line.length()) {
			int end;
			int color;
			char c = line.charAt(i);
			if (inBlockComment) {
				int close = line.indexOf("*/", i);
				end = close < 0 ? line.length() : close + 2;
				inBlockComment = close < 0;
				color = COLOR_COMMENT;
			} else if (line.startsWith("//", i)) {
				end = line.length();
				color = COLOR_COMMENT;
			} else if (line.startsWith("/*", i)) {
				int close = line.indexOf("*/", i + 2);
				end = close < 0 ? line.length() : close + 2;
				inBlockComment = close < 0;
				color = COLOR_COMMENT;
			} else if (c == '"' || c == '\'') {
				end = i + 1;
				while (end < line.length() && line.charAt(end) != c) {
					end += line.charAt(end) == '\\' ? 2 : 1;
				}

				end = Math.min(line.length(), end + 1);
				color = COLOR_STRING;
			} else if (Character.isDigit(c)) {
				end = i;
				while (end < line.length() && (Character.isLetterOrDigit(line.charAt(end)) || line.charAt(end) == '.' || line.charAt(end) == '_')) end++;
				color = COLOR_NUMBER;
			} else if (Character.isJavaIdentifierStart(c)) {
				end = i;
				while (end < line.length() && Character.isJavaIdentifierPart(line.charAt(end))) end++;
				String word = line.substring(i, end);
				color = KEYWORDS.contains(word) ? COLOR_KEYWORD : Character.isUpperCase(c) ? COLOR_TYPE : Theme.TEXT;
			} else {
				end = i + 1;
				color = c == '@' ? COLOR_TYPE : Theme.TEXT_SECONDARY;
			}

			String token = line.substring(i, end);
			ui.text(token, px, y0, color);
			px += ui.textWidth(token);
			i = end;
		}

		return inBlockComment;
	}

	private boolean blockCommentStateBefore(int targetRow) {
		boolean open = false;
		for (int r = 0; r < targetRow && r < lines.size(); r++) {
			String line = stripStringsAndLineComments(lines.get(r), open);
			int i = 0;
			while (i < line.length()) {
				if (open) {
					int close = line.indexOf("*/", i);
					if (close < 0) break;
					open = false;
					i = close + 2;
				} else {
					int start = line.indexOf("/*", i);
					if (start < 0) break;
					open = true;
					i = start + 2;
				}
			}
		}

		return open;
	}

	private static String stripStringsAndLineComments(String line, boolean inComment) {
		if (inComment) {
			return line;
		}

		StringBuilder out = new StringBuilder();
		char quote = 0;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (quote != 0) {
				if (c == '\\') i++;
				else if (c == quote) quote = 0;
				out.append(' ');
			} else if (c == '"' || c == '\'') {
				quote = c;
				out.append(' ');
			} else if (line.startsWith("//", i)) {
				break;
			} else {
				out.append(c);
			}
		}

		return out.toString();
	}

	private void keepCursorVisible(UiContext ui, int textW) {
		if (row < scrollRow) scrollRow = row;
		if (row >= scrollRow + visibleRows()) scrollRow = row - visibleRows() + 1;
		scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, lines.size() - visibleRows())));
		int cursorPx = ui.textWidth(lines.get(row).substring(0, col));
		if (cursorPx - scrollX > textW - 4) scrollX = cursorPx - textW + 4;
		if (cursorPx - scrollX < 0) scrollX = Math.max(0, cursorPx - 20);
	}

	// ----- selection -------------------------------------------------------------------------

	/** {fromRow, fromCol, toRow, toCol} in document order, or null without a selection. */
	private int[] selection() {
		if (anchorRow < 0 || anchorRow == row && anchorCol == col) {
			return null;
		}

		boolean anchorFirst = anchorRow < row || anchorRow == row && anchorCol < col;
		return anchorFirst ? new int[] {anchorRow, anchorCol, row, col} : new int[] {row, col, anchorRow, anchorCol};
	}

	private String selectedText() {
		int[] s = selection();
		if (s == null) return "";
		if (s[0] == s[2]) return lines.get(s[0]).substring(s[1], s[3]);
		StringBuilder out = new StringBuilder(lines.get(s[0]).substring(s[1]));
		for (int r = s[0] + 1; r < s[2]; r++) out.append('\n').append(lines.get(r));
		return out.append('\n').append(lines.get(s[2]), 0, s[3]).toString();
	}

	private boolean deleteSelection() {
		int[] s = selection();
		anchorRow = -1;
		if (s == null) return false;
		String merged = lines.get(s[0]).substring(0, s[1]) + lines.get(s[2]).substring(s[3]);
		for (int r = s[2]; r > s[0]; r--) lines.remove(r);
		lines.set(s[0], merged);
		row = s[0];
		col = s[1];
		return true;
	}

	// ----- editing ---------------------------------------------------------------------------

	private void snapshot() {
		undo.push(new State(List.copyOf(lines), row, col));
		while (undo.size() > 100) undo.removeLast();
	}

	private void changed() {
		reveal = true;
		onChange.accept(text());
	}

	private void insert(String text) {
		String clean = text.replace("\r\n", "\n").replace("\r", "\n").replace("\t", INDENT);
		StringBuilder filtered = new StringBuilder();
		clean.codePoints().filter(cp -> cp == '\n' || cp >= 32 && cp != 127).forEach(filtered::appendCodePoint);
		if (text().length() + filtered.length() > MAX_CHARS) {
			return;
		}

		snapshot();
		deleteSelection();
		String line = lines.get(row);
		String before = line.substring(0, col);
		String after = line.substring(col);
		String[] parts = filtered.toString().split("\n", -1);
		if (parts.length == 1) {
			lines.set(row, before + parts[0] + after);
			col += parts[0].length();
		} else {
			lines.set(row, before + parts[0]);
			for (int i = 1; i < parts.length; i++) {
				lines.add(row + i, parts[i] + (i == parts.length - 1 ? after : ""));
			}

			row += parts.length - 1;
			col = parts[parts.length - 1].length();
		}

		changed();
	}

	private void newline() {
		String line = lines.get(row);
		int indent = 0;
		while (indent < line.length() && line.charAt(indent) == ' ') indent++;
		String extra = col > 0 && line.substring(0, col).stripTrailing().endsWith("{") ? INDENT : "";
		insert("\n" + " ".repeat(indent) + extra);
	}

	private void backspace() {
		snapshot();
		if (!deleteSelection()) {
			if (col > 0) {
				String line = lines.get(row);
				// Remove a whole indent step when only spaces precede the cursor.
				int remove = line.substring(0, col).isBlank() && col % INDENT.length() == 0 ? INDENT.length() : 1;
				lines.set(row, line.substring(0, col - remove) + line.substring(col));
				col -= remove;
			} else if (row > 0) {
				col = lines.get(row - 1).length();
				lines.set(row - 1, lines.get(row - 1) + lines.remove(row));
				row--;
			} else {
				undo.pop();
				return;
			}
		}

		changed();
	}

	private void delete() {
		snapshot();
		if (!deleteSelection()) {
			String line = lines.get(row);
			if (col < line.length()) {
				lines.set(row, line.substring(0, col) + line.substring(col + 1));
			} else if (row < lines.size() - 1) {
				lines.set(row, line + lines.remove(row + 1));
			} else {
				undo.pop();
				return;
			}
		}

		changed();
	}

	private void move(int newRow, int newCol, boolean select) {
		if (select && anchorRow < 0) {
			anchorRow = row;
			anchorCol = col;
		} else if (!select) {
			anchorRow = -1;
		}

		row = Math.max(0, Math.min(lines.size() - 1, newRow));
		col = Math.max(0, Math.min(lines.get(row).length(), newCol));
		reveal = true;
	}

	/** Ctrl (Cmd on macOS) from the event, or from the keyboard state when the event carries no modifiers. */
	private static boolean controlHeld(Minecraft client, KeyEvent event) {
		return event.hasControlDownWithQuirk() || InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
				|| InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
	}

	@Override
	public boolean keyPressed(UiContext ui, KeyEvent event) {
		Minecraft client = Minecraft.getInstance();
		boolean shift = event.hasShiftDown() || InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT);
		boolean control = controlHeld(client, event);
		if (event.isSelectAll() || control && event.key() == GLFW.GLFW_KEY_A) {
			anchorRow = 0;
			anchorCol = 0;
			row = lines.size() - 1;
			col = lines.get(row).length();
			return true;
		}

		if (event.isCopy() || control && event.key() == GLFW.GLFW_KEY_C) {
			client.keyboardHandler.setClipboard(selection() == null ? text() : selectedText());
			return true;
		}

		if (event.isCut() || control && event.key() == GLFW.GLFW_KEY_X) {
			if (selection() != null) {
				client.keyboardHandler.setClipboard(selectedText());
				snapshot();
				deleteSelection();
				changed();
			}

			return true;
		}

		if (event.isPaste() || control && event.key() == GLFW.GLFW_KEY_V) {
			insert(client.keyboardHandler.getClipboard());
			return true;
		}

		if (control && event.key() == GLFW.GLFW_KEY_Z) {
			if (!undo.isEmpty()) {
				State state = undo.pop();
				lines.clear();
				lines.addAll(state.lines());
				row = Math.min(state.row(), lines.size() - 1);
				col = Math.min(state.col(), lines.get(row).length());
				anchorRow = -1;
				changed();
			}

			return true;
		}

		switch (event.key()) {
			case GLFW.GLFW_KEY_LEFT -> {
				if (col > 0) move(row, col - 1, shift);
				else if (row > 0) move(row - 1, lines.get(row - 1).length(), shift);
			}
			case GLFW.GLFW_KEY_RIGHT -> {
				if (col < lines.get(row).length()) move(row, col + 1, shift);
				else if (row < lines.size() - 1) move(row + 1, 0, shift);
			}
			case GLFW.GLFW_KEY_UP -> move(row - 1, col, shift);
			case GLFW.GLFW_KEY_DOWN -> move(row + 1, col, shift);
			case GLFW.GLFW_KEY_HOME -> move(control ? 0 : row, 0, shift);
			case GLFW.GLFW_KEY_END -> move(control ? lines.size() - 1 : row, Integer.MAX_VALUE, shift);
			case GLFW.GLFW_KEY_PAGE_UP -> move(row - visibleRows(), col, shift);
			case GLFW.GLFW_KEY_PAGE_DOWN -> move(row + visibleRows(), col, shift);
			case GLFW.GLFW_KEY_BACKSPACE -> backspace();
			case GLFW.GLFW_KEY_DELETE -> delete();
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> newline();
			case GLFW.GLFW_KEY_TAB -> insert(INDENT);
			case GLFW.GLFW_KEY_ESCAPE -> ui.focus(null);
			default -> {
				return false;
			}
		}

		return true;
	}

	@Override
	public boolean charTyped(UiContext ui, CharacterEvent event) {
		int cp = event.codepoint();
		if (cp >= 32 && cp != 127) {
			insert(event.codepointAsString());
		}

		return true;
	}

	// ----- mouse -----------------------------------------------------------------------------

	private int[] positionAt(UiContext ui, double mouseX, double mouseY) {
		int r = Math.max(0, Math.min(lines.size() - 1, scrollRow + (int) Math.floor((mouseY - y - PAD) / LINE_HEIGHT)));
		String line = lines.get(r);
		double target = mouseX - (x + gutter(ui) + 2) + scrollX;
		int c = 0;
		while (c < line.length() && ui.textWidth(line.substring(0, c + 1)) - ui.textWidth(String.valueOf(line.charAt(c))) / 2.0 < target) c++;
		return new int[] {r, c};
	}

	@Override
	public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
		if (!contains(mouseX, mouseY)) {
			return false;
		}

		ui.focus(this);
		int[] at = positionAt(ui, mouseX, mouseY);
		move(at[0], at[1], false);
		anchorRow = row;
		anchorCol = col;
		dragging = true;
		ui.capture(this);
		return true;
	}

	@Override
	public void mouseDragged(UiContext ui, double mouseX, double mouseY, int button) {
		if (dragging) {
			int[] at = positionAt(ui, mouseX, mouseY);
			row = at[0];
			col = at[1];
			reveal = true;
		}
	}

	@Override
	public void mouseReleased(UiContext ui, double mouseX, double mouseY, int button) {
		dragging = false;
		ui.releaseCapture();
	}

	@Override
	public boolean mouseScrolled(UiContext ui, double mouseX, double mouseY, double amount) {
		if (lines.size() <= visibleRows()) {
			return false;
		}

		scrollRow = Math.max(0, Math.min(lines.size() - visibleRows(), scrollRow - (int) Math.signum(amount) * 3));
		return true;
	}
}
