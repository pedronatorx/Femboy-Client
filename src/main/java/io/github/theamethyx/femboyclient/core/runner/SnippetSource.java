package io.github.theamethyx.femboyclient.core.runner;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns what the user typed into a compilable Java file.
 *
 * <p>Two forms are accepted: a complete class (with a {@code main} method or a static {@code run()}
 * method), or plain statements. Statements are wrapped into {@code Snippet.run()} with a few default
 * imports, and leading {@code import} lines are moved out of the method. Line numbers in compiler
 * messages are mapped back to what the user typed.
 */
public final class SnippetSource {
	public static final String WRAPPER_CLASS = "Snippet";
	private static final Pattern CLASS_DECLARATION = Pattern.compile("(?m)^\\s*(?:public\\s+)?(?:final\\s+)?class\\s+([A-Za-z_][A-Za-z0-9_]*)");
	private static final List<String> DEFAULT_IMPORTS = List.of(
			"import java.util.*;",
			"import java.util.stream.*;",
			"import io.github.theamethyx.femboyclient.core.runner.api.Femboy;");

	/**
	 * @param lineOffset add to a line of {@code javaSource} to get the line the user typed (may be negative)
	 */
	public record Prepared(String className, String javaSource, int lineOffset, boolean wrapped) {
		public int userLine(long generatedLine) {
			return (int) Math.max(1, generatedLine + lineOffset);
		}
	}

	private SnippetSource() {
	}

	public static Prepared prepare(String source) {
		String text = source.replace("\r\n", "\n").replace('\r', '\n');
		Matcher declaration = CLASS_DECLARATION.matcher(text);
		if (declaration.find()) {
			return new Prepared(declaration.group(1), text, 0, false);
		}

		String[] lines = text.split("\n", -1);
		List<String> imports = new ArrayList<>();
		int consumed = 0;
		for (String line : lines) {
			String trimmed = line.trim();
			if (trimmed.startsWith("import ")) {
				imports.add(trimmed);
			} else if (!trimmed.isEmpty()) {
				break;
			}

			consumed++;
		}

		List<String> out = new ArrayList<>(DEFAULT_IMPORTS);
		out.addAll(imports);
		out.add("public class " + WRAPPER_CLASS + " {");
		out.add("public static void run() throws Throwable {");
		int header = out.size();
		for (int i = consumed; i < lines.length; i++) {
			out.add(lines[i]);
		}

		out.add("}");
		out.add("}");
		// Generated line (header + n) holds user line (consumed + n).
		return new Prepared(WRAPPER_CLASS, String.join("\n", out), consumed - header, true);
	}
}
