package io.github.theamethyx.femboyclient.client.smart;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;

import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;

/**
 * A journal of this session: profile switches, module changes, worlds and dimensions, deaths, your markers, schematics,
 * performance warnings, module errors and rules. Kept in memory (the newest {@link #LIMIT} events) and exported as text on
 * request. It records no chat, no player names and no server addresses: a world is described only as singleplayer, a
 * private network or a public server.
 */
public final class SessionTimeline {
	public static final int LIMIT = 500;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

	public enum Kind implements EnumSetting.Labeled {
		PROFILE("Profile switches", Icon.LAYERS),
		MODULE("Module changes", Icon.POWER),
		WORLD("Worlds and dimensions", Icon.GLOBE),
		DEATH("Deaths", Icon.HEART),
		MARKER("Your markers", Icon.STAR),
		SCHEMATIC("Schematics", Icon.SCENARIOS),
		PERFORMANCE("Performance warnings", Icon.BOLT),
		ERROR("Module errors", Icon.WARNING),
		RULE("Rules", Icon.FLOW);

		private final String label;
		private final Icon icon;

		Kind(String label, Icon icon) {
			this.label = label;
			this.icon = icon;
		}

		@Override
		public String label() {
			return label;
		}

		public Icon icon() {
			return icon;
		}
	}

	/** One event. The note and the bookmark can be changed. */
	public static final class Event {
		private final long time;
		private final Kind kind;
		private final String title;
		private final String detail;
		private String note = "";
		private boolean bookmarked;

		Event(long time, Kind kind, String title, String detail) {
			this.time = time;
			this.kind = kind;
			this.title = title;
			this.detail = detail == null ? "" : detail;
		}

		public long time() {
			return time;
		}

		public Kind kind() {
			return kind;
		}

		public String title() {
			return title;
		}

		public String detail() {
			return detail;
		}

		public String note() {
			return note;
		}

		public void setNote(String note) {
			this.note = note == null ? "" : note.strip();
		}

		public boolean bookmarked() {
			return bookmarked;
		}

		public void setBookmarked(boolean bookmarked) {
			this.bookmarked = bookmarked;
		}

		boolean matches(String query) {
			String q = query.toLowerCase(Locale.ROOT);
			return title.toLowerCase(Locale.ROOT).contains(q) || detail.toLowerCase(Locale.ROOT).contains(q) || note.toLowerCase(Locale.ROOT).contains(q)
					|| kind.label().toLowerCase(Locale.ROOT).contains(q);
		}
	}

	private final List<Event> events = new ArrayList<>();
	private Set<Kind> recorded = EnumSet.allOf(Kind.class);
	private final long started = System.currentTimeMillis();
	private int version;

	/** Which kinds are recorded; others are ignored from now on (already recorded events stay). */
	public void setRecorded(Set<Kind> kinds) {
		recorded = kinds.isEmpty() ? EnumSet.noneOf(Kind.class) : EnumSet.copyOf(kinds);
	}

	public boolean records(Kind kind) {
		return recorded.contains(kind);
	}

	/** Adds an event if its kind is recorded. Client thread only. */
	public void record(Kind kind, String title, String detail) {
		if (!recorded.contains(kind)) {
			return;
		}

		events.add(new Event(System.currentTimeMillis(), kind, title, detail));
		while (events.size() > LIMIT) {
			// Keep bookmarked events when trimming.
			int oldest = 0;
			while (oldest < events.size() - 1 && events.get(oldest).bookmarked()) oldest++;
			events.remove(oldest);
		}

		version++;
	}

	/** Increases on every change, so the page knows when to refresh. */
	public int version() {
		return version;
	}

	public void changed() {
		version++;
	}

	/** Events, newest first, filtered by text (empty: all), kind ({@code null}: all) and bookmarks. */
	public List<Event> events(String query, Kind kind, boolean bookmarkedOnly) {
		List<Event> result = new ArrayList<>();
		for (int i = events.size() - 1; i >= 0; i--) {
			Event event = events.get(i);
			if ((kind == null || event.kind() == kind) && (!bookmarkedOnly || event.bookmarked()) && (query.isBlank() || event.matches(query.strip()))) {
				result.add(event);
			}
		}

		return result;
	}

	public int size() {
		return events.size();
	}

	public void clear() {
		events.clear();
		version++;
	}

	public static String time(long millis) {
		return TIME.format(Instant.ofEpochMilli(millis));
	}

	/** The session as text. */
	public String report() {
		StringBuilder out = new StringBuilder("Femboy Client session timeline\n");
		out.append("Session started ").append(LocalDateTime.ofInstant(Instant.ofEpochMilli(started), ZoneId.systemDefault())).append('\n');
		out.append(events.size()).append(" event(s)\n\n");
		for (Event event : events) {
			out.append(time(event.time())).append("  [").append(event.kind().label()).append("] ").append(event.title());
			if (!event.detail().isEmpty()) out.append(" - ").append(event.detail());
			if (event.bookmarked()) out.append("  (bookmarked)");
			if (!event.note().isEmpty()) out.append("\n          Note: ").append(event.note());
			out.append('\n');
		}

		return out.toString();
	}

	/** Writes {@link #report()} to {@code reports/} on a background thread. */
	public void export(Path reports, Consumer<String> done) {
		String text = report();
		Path file = reports.resolve("session-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt");
		CompletableFuture.runAsync(() -> {
			try {
				Files.createDirectories(reports);
				Files.writeString(file, text, StandardCharsets.UTF_8);
				Minecraft.getInstance().execute(() -> done.accept("Saved " + file.getFileName() + " in the reports folder."));
			} catch (IOException e) {
				Minecraft.getInstance().execute(() -> done.accept("Could not save the timeline: " + e.getMessage()));
			}
		});
	}
}
