package io.github.theamethyx.femboyclient.core.runner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import io.github.theamethyx.femboyclient.core.runner.api.Femboy;

/**
 * Entry point of the separate Java process that compiles and runs one snippet. Started by
 * {@link JavaRunner}; never loaded inside Minecraft.
 *
 * <p>Input on stdin: the client snapshot as {@code key=value} lines, a line holding only
 * {@link #SOURCE_MARKER}, then the source. The snippet's own output goes to stdout and stderr unchanged.
 * The worker announces the start of execution with {@link #RUNNING_MARKER} on stderr and reports the
 * result through its exit code.
 */
public final class RunnerWorker {
	public static final String SOURCE_MARKER = "\u0000SOURCE";
	public static final String RUNNING_MARKER = "\u0000femboyclient:running";

	public static final int EXIT_OK = 0;
	public static final int EXIT_COMPILE_ERROR = 2;
	public static final int EXIT_EXCEPTION = 3;
	public static final int EXIT_NO_COMPILER = 4;
	public static final int EXIT_INTERNAL = 5;

	private RunnerWorker() {
	}

	public static void main(String[] args) {
		int code;
		try {
			code = run(System.in);
		} catch (Throwable t) {
			System.err.println("Runner error: " + t);
			code = EXIT_INTERNAL;
		}

		System.out.flush();
		System.err.flush();
		System.exit(code);
	}

	private static int run(InputStream in) throws IOException {
		String input = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		int split = input.indexOf("\n" + SOURCE_MARKER + "\n");
		String header = split < 0 ? "" : input.substring(0, split);
		String source = split < 0 ? input : input.substring(split + SOURCE_MARKER.length() + 2);
		Femboy.load(decodeSnapshot(header));

		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			System.err.println("This Java installation has no compiler (" + System.getProperty("java.home") + ").");
			System.err.println("Set 'Java for the runner' in the module settings to a JDK 21 or newer.");
			return EXIT_NO_COMPILER;
		}

		SnippetSource.Prepared prepared = SnippetSource.prepare(source);
		Map<String, byte[]> classes = new HashMap<>();
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
		boolean ok;
		try (StandardJavaFileManager standard = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
			JavaFileManager files = new ForwardingJavaFileManager<>(standard) {
				@Override
				public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
					return new SimpleJavaFileObject(URI.create("mem:///" + className.replace('.', '/') + kind.extension), kind) {
						@Override
						public OutputStream openOutputStream() {
							return new ByteArrayOutputStream() {
								@Override
								public void close() {
									classes.put(className, toByteArray());
								}
							};
						}
					};
				}
			};
			JavaFileObject unit = new SimpleJavaFileObject(URI.create("string:///" + prepared.className() + ".java"), JavaFileObject.Kind.SOURCE) {
				@Override
				public CharSequence getCharContent(boolean ignoreEncodingErrors) {
					return prepared.javaSource();
				}
			};
			List<String> options = List.of("-classpath", System.getProperty("java.class.path"), "-proc:none", "-g", "-Xlint:none", "-nowarn");
			ok = compiler.getTask(null, files, diagnostics, options, null, List.of(unit)).call();
		}

		for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
			if (d.getKind() == Diagnostic.Kind.ERROR) {
				String where = d.getLineNumber() > 0 ? "Line " + prepared.userLine(d.getLineNumber()) + ": " : "";
				System.err.println(where + d.getMessage(Locale.ROOT));
			}
		}

		if (!ok) {
			return EXIT_COMPILE_ERROR;
		}

		ClassLoader loader = new ClassLoader(RunnerWorker.class.getClassLoader()) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] bytes = classes.get(name);
				if (bytes == null) {
					throw new ClassNotFoundException(name);
				}

				return defineClass(name, bytes, 0, bytes.length);
			}
		};

		Method entry;
		try {
			Class<?> type = loader.loadClass(prepared.className());
			entry = findEntry(type);
		} catch (ClassNotFoundException e) {
			System.err.println("Runner error: compiled class not found: " + e.getMessage());
			return EXIT_INTERNAL;
		}

		if (entry == null) {
			System.err.println("Add 'public static void main(String[] args)' or 'public static void run()' to " + prepared.className()
					+ ", or write plain statements without a class.");
			return EXIT_COMPILE_ERROR;
		}

		System.err.println(RUNNING_MARKER);
		System.err.flush();
		try {
			entry.setAccessible(true);
			if (entry.getParameterCount() == 1) {
				entry.invoke(null, (Object) new String[0]);
			} else {
				entry.invoke(null);
			}
		} catch (InvocationTargetException e) {
			System.out.flush();
			reportException(e.getCause(), prepared);
			return EXIT_EXCEPTION;
		} catch (IllegalAccessException e) {
			System.err.println("Runner error: " + e.getMessage());
			return EXIT_INTERNAL;
		}

		return EXIT_OK;
	}

	private static Method findEntry(Class<?> type) {
		for (Method method : type.getDeclaredMethods()) {
			boolean isStatic = Modifier.isStatic(method.getModifiers());
			if (isStatic && method.getName().equals("main") && method.getParameterCount() == 1 && method.getParameterTypes()[0] == String[].class) {
				return method;
			}
		}

		for (Method method : type.getDeclaredMethods()) {
			if (Modifier.isStatic(method.getModifiers()) && method.getName().equals("run") && method.getParameterCount() == 0) {
				return method;
			}
		}

		return null;
	}

	/** Prints the exception with stack lines from the snippet only, mapped to the lines the user typed. */
	private static void reportException(Throwable error, SnippetSource.Prepared prepared) {
		System.err.println("Exception: " + error);
		for (StackTraceElement frame : error.getStackTrace()) {
			if (frame.getFileName() != null && frame.getFileName().equals(prepared.className() + ".java")) {
				System.err.println("    at " + frame.getMethodName() + " (line " + prepared.userLine(frame.getLineNumber()) + ")");
			}
		}

		if (error.getCause() != null && error.getCause() != error) {
			System.err.println("Caused by: " + error.getCause());
		}
	}

	// ----- snapshot encoding (shared with JavaRunner) ----------------------------------------

	public static String encodeSnapshot(Map<String, String> snapshot) {
		StringBuilder out = new StringBuilder();
		snapshot.forEach((key, value) -> out.append(escape(key)).append('=').append(escape(value)).append('\n'));
		return out.toString();
	}

	static Map<String, String> decodeSnapshot(String text) {
		Map<String, String> snapshot = new LinkedHashMap<>();
		for (String line : text.split("\n")) {
			int eq = line.indexOf('=');
			if (eq > 0) {
				snapshot.put(unescape(line.substring(0, eq)), unescape(line.substring(eq + 1)));
			}
		}

		return snapshot;
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "").replace("=", "\\e");
	}

	private static String unescape(String value) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '\\' && i + 1 < value.length()) {
				char next = value.charAt(++i);
				out.append(next == 'n' ? '\n' : next == 'e' ? '=' : next);
			} else {
				out.append(c);
			}
		}

		return out.toString();
	}
}
