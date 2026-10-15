package io.github.theamethyx.femboyclient.core.runner;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Looks for Java APIs that can change things outside the snippet: files, other programs, the network.
 * A match does not mean the code is harmful; it means the user is asked to confirm before it runs.
 * This is a speed bump against mistakes and pasted code, not a security boundary: the worker runs with
 * your normal user permissions.
 */
public final class RiskScanner {
	private record Rule(Pattern pattern, String description) {
	}

	private static final List<Rule> RULES = List.of(
			rule("Files\\s*\\.\\s*(delete|deleteIfExists)\\b|\\.\\s*delete\\s*\\(\\s*\\)|\\.\\s*deleteOnExit\\s*\\(", "deletes files"),
			rule("Files\\s*\\.\\s*(write|writeString|newBufferedWriter|newOutputStream|createFile|createDirector(y|ies)|move|copy)\\b"
					+ "|\\bnew\\s+(FileOutputStream|FileWriter|RandomAccessFile)\\b|\\.\\s*renameTo\\s*\\(", "writes, moves or creates files"),
			rule("\\bRuntime\\s*\\.\\s*getRuntime\\s*\\(\\s*\\)\\s*\\.\\s*exec\\b|\\bProcessBuilder\\b|\\bProcessHandle\\b", "starts or controls other programs"),
			rule("\\bSystem\\s*\\.\\s*exit\\b|\\bRuntime\\s*\\.\\s*getRuntime\\s*\\(\\s*\\)\\s*\\.\\s*(halt|exit)\\b", "exits the runner process"),
			rule("\\b(Socket|ServerSocket|DatagramSocket|HttpClient|URLConnection|HttpURLConnection)\\b|\\bnew\\s+URL\\s*\\(|\\bURI\\s*\\.\\s*create\\b",
					"uses the network"),
			rule("\\bsetAccessible\\s*\\(|\\bsun\\.misc\\.Unsafe\\b|\\bSystem\\s*\\.\\s*(load|loadLibrary)\\b", "bypasses Java access checks or loads native code"));

	private RiskScanner() {
	}

	private static Rule rule(String regex, String description) {
		return new Rule(Pattern.compile(regex), description);
	}

	/** Readable descriptions of everything risky found, without duplicates; empty when nothing was found. */
	public static List<String> scan(String source) {
		String code = codeOnly(source);
		List<String> found = new ArrayList<>();
		for (Rule rule : RULES) {
			if (rule.pattern().matcher(code).find() && !found.contains(rule.description())) {
				found.add(rule.description());
			}
		}

		return found;
	}

	/** Removes comments and blanks string contents, so only actual code can trigger a warning. */
	static String codeOnly(String source) {
		StringBuilder out = new StringBuilder(source.length());
		int i = 0;
		boolean inString = false;
		while (i < source.length()) {
			char c = source.charAt(i);
			if (inString) {
				if (c == '\\' && i + 1 < source.length()) {
					out.append("  ");
					i++;
				} else if (c == '"') {
					inString = false;
					out.append(c);
				} else {
					out.append(' ');
				}

				i++;
			} else if (c == '"') {
				inString = true;
				out.append(c);
				i++;
			} else if (source.startsWith("//", i)) {
				int end = source.indexOf('\n', i);
				i = end < 0 ? source.length() : end;
			} else if (source.startsWith("/*", i)) {
				int end = source.indexOf("*/", i + 2);
				i = end < 0 ? source.length() : end + 2;
			} else {
				out.append(c);
				i++;
			}
		}

		return out.toString();
	}
}
