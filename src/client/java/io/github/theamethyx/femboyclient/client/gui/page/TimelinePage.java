package io.github.theamethyx.femboyclient.client.gui.page;

import java.util.ArrayList;
import java.util.List;

import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.gui.widget.Button;
import io.github.theamethyx.femboyclient.client.gui.widget.ConfirmButton;
import io.github.theamethyx.femboyclient.client.gui.widget.Container;
import io.github.theamethyx.femboyclient.client.gui.widget.Dropdown;
import io.github.theamethyx.femboyclient.client.gui.widget.ScrollList;
import io.github.theamethyx.femboyclient.client.gui.widget.SettingRow;
import io.github.theamethyx.femboyclient.client.gui.widget.TextField;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.client.smart.SessionTimeline;
import io.github.theamethyx.femboyclient.core.setting.Setting;

/**
 * The session timeline: search and filter events, bookmark them, add notes and your own markers, export the session as
 * text, clear it, and choose what is recorded. Kept in memory for this session only (export to keep it).
 */
public class TimelinePage extends Page {
	private static final int SHOWN = 120;

	private final ScrollList content;
	private String query = "";
	private SessionTimeline.Kind kind;
	private boolean bookmarkedOnly;
	private SessionTimeline.Event selected;
	private int shownVersion = -1;
	private String feedback;

	public TimelinePage(UiContext ui) {
		super(ui);
		content = add(new ScrollList(ui, Theme.GAP, 0));
	}

	@Override
	public String title() {
		return "Settings";
	}

	@Override
	public String navLabel() {
		return "Timeline";
	}

	@Override
	public String subtitle() {
		return "This session's events: profiles, modules, worlds, deaths, markers, warnings and rules. No chat or server addresses.";
	}

	@Override
	public Icon icon() {
		return Icon.CLOCK;
	}

	private SessionTimeline timeline() {
		return femboy.timeline();
	}

	@Override
	protected void onLayout() {
		content.setBounds(x, y, width, height);
		rebuild();
	}

	@Override
	public void tick() {
		if (timeline().version() != shownVersion && ui.focused() == null) {
			rebuild();
		}
	}

	private void rebuild() {
		shownVersion = timeline().version();
		List<Widget> rows = new ArrayList<>();
		rows.add(new Filters());
		rows.add(new MarkerRow());
		if (selected != null) {
			rows.add(new NoteRow());
		}

		if (feedback != null) {
			rows.add(new ModulesPage.Message(feedback));
		}

		List<SessionTimeline.Event> events = timeline().events(query, kind, bookmarkedOnly);
		for (int i = 0; i < Math.min(SHOWN, events.size()); i++) {
			rows.add(new EventRow(events.get(i)));
		}

		if (events.isEmpty()) {
			rows.add(new ModulesPage.Message(timeline().size() == 0 ? "Nothing recorded yet this session." : "No event matches."));
		} else if (events.size() > SHOWN) {
			rows.add(new ModulesPage.Message((events.size() - SHOWN) + " older event(s) are not listed; export to see all."));
		}

		rows.add(new ConfigPage.SectionTitle("What is recorded", "Turn off kinds you do not want; the newest " + SessionTimeline.LIMIT
				+ " events are kept (bookmarked ones first)."));
		List<Setting<?>> settings = new ArrayList<>(femboy.options().timelineKinds.values());
		rows.addAll(SettingRow.rowsFor("options", settings));
		content.setChildren(rows);
	}

	private final class Filters extends Container {
		private final TextField search = add(new TextField("Search events and notes", 60).icon(Icon.SEARCH).onChange(text -> {
			query = text;
			rebuild();
		}));
		private final Dropdown<SessionTimeline.Kind> filter = add(new Dropdown<>(() -> {
			List<SessionTimeline.Kind> list = new ArrayList<>();
			list.add(null);
			list.addAll(List.of(SessionTimeline.Kind.values()));
			return list;
		}, () -> kind, value -> {
			kind = value;
			rebuild();
		}, value -> value == null ? "All events" : value.label()));
		private final Button bookmarks = add(new Button("", Button.Style.SECONDARY, () -> {
			bookmarkedOnly = !bookmarkedOnly;
			rebuild();
		}).icon(Icon.STAR));
		private final Button export = add(new Button("", Button.Style.SECONDARY, () -> timeline().export(femboy.config().root().resolve("reports"), result -> {
			feedback = result;
			rebuild();
		})).icon(Icon.SAVE));
		private final Button clear = add(new ConfirmButton("", "Clear?", Button.Style.DANGER, () -> {
			timeline().clear();
			selected = null;
			rebuild();
		}).icon(Icon.TRASH));

		Filters() {
			search.setText(query);
			filter.tooltip("Show one kind of event (the empty choice shows all).");
			bookmarks.tooltip(() -> bookmarkedOnly ? "Showing bookmarked events only; click to show all." : "Show bookmarked events only.");
			export.tooltip("Save the whole timeline, with notes, as text in the reports folder.");
			clear.tooltip("Remove every event of this session (two clicks).");
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 16;
		}

		@Override
		protected void onLayout() {
			int small = 18;
			int dropdown = 110;
			search.setBounds(x, y, width - dropdown - 3 * small - 4 * Theme.GAP, 16);
			filter.setBounds(x + width - dropdown - 3 * small - 3 * Theme.GAP, y + 1, dropdown, 14);
			bookmarks.setBounds(x + width - 3 * small - 2 * Theme.GAP, y, small, 16);
			export.setBounds(x + width - 2 * small - Theme.GAP, y, small, 16);
			clear.setBounds(x + width - small, y, small, 16);
		}

		@Override
		public void render(UiContext ui) {
			super.render(ui);
			if (bookmarkedOnly) {
				ui.outline(bookmarks.x(), bookmarks.y(), bookmarks.width(), bookmarks.height(), Theme.ACCENT);
			}
		}
	}

	private final class MarkerRow extends Container {
		private final TextField text = add(new TextField("Add a marker (e.g. 'found the stronghold')", 80));
		private final Button button = add(new Button("Add marker", Button.Style.PRIMARY, this::addMarker).icon(Icon.STAR));

		MarkerRow() {
			text.onSubmit(value -> addMarker());
		}

		private void addMarker() {
			String value = text.text().strip();
			if (value.isEmpty()) {
				return;
			}

			if (!timeline().records(SessionTimeline.Kind.MARKER)) {
				feedback = "Markers are not recorded; turn 'Record: Your markers' on below.";
			} else {
				timeline().record(SessionTimeline.Kind.MARKER, value, "");
				feedback = null;
			}

			ui.focus(null);
			rebuild();
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 16;
		}

		@Override
		protected void onLayout() {
			text.setBounds(x, y, width - 90 - Theme.GAP, 16);
			button.setBounds(x + width - 90, y, 90, 16);
		}
	}

	private final class NoteRow extends Container {
		private final TextField note = add(new TextField("Note for the selected event", 200));
		private final Button save = add(new Button("Save note", Button.Style.PRIMARY, this::saveNote).icon(Icon.CHECK));

		NoteRow() {
			note.setText(selected.note());
			note.onSubmit(value -> saveNote());
		}

		private void saveNote() {
			selected.setNote(note.text());
			timeline().changed();
			ui.focus(null);
			rebuild();
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 28;
		}

		@Override
		protected void onLayout() {
			note.setBounds(x, y + 12, width - 90 - Theme.GAP, 16);
			save.setBounds(x + width - 90, y + 12, 90, 16);
		}

		@Override
		public void render(UiContext ui) {
			ui.textClipped("Note for: " + selected.title(), x + 2, y + 1, width, Theme.ACCENT_STRONG);
			super.render(ui);
		}
	}

	private final class EventRow extends Widget {
		private final SessionTimeline.Event event;

		EventRow(SessionTimeline.Event event) {
			this.event = event;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return event.note().isEmpty() ? 22 : 32;
		}

		@Override
		public void render(UiContext ui) {
			boolean isSelected = event == selected;
			boolean hot = ui.hovered(x, y, width, height);
			ui.roundRect(x, y, width, height, isSelected ? Theme.ACCENT_TINT : hot ? Theme.SURFACE_HOVER : Theme.SURFACE);
			ui.icon(event.kind().icon(), x + 6, y + 7, 8, Theme.ACCENT);
			ui.text(SessionTimeline.time(event.time()), x + 18, y + 3, Theme.TEXT_MUTED);
			ui.textClipped(event.title(), x + 64, y + 3, width - 90, Theme.TEXT);
			ui.textScaled(ui.ellipsize(event.detail().isEmpty() ? event.kind().label() : event.detail(), Math.round((width - 90) / 0.75f)), x + 18, y + 13,
					0.75f, Theme.TEXT_SECONDARY);
			if (!event.note().isEmpty()) {
				ui.textScaled(ui.ellipsize("Note: " + event.note(), Math.round((width - 30) / 0.75f)), x + 18, y + 22, 0.75f, Theme.ACCENT_STRONG);
			}

			boolean starHot = ui.hovered(x + width - 16, y + 3, 12, 12);
			ui.icon(Icon.STAR, x + width - 14, y + 5, 9, event.bookmarked() ? Theme.WARNING : starHot ? Theme.TEXT : Theme.TEXT_MUTED);
			if (hot) {
				ui.pointer();
				ui.tooltip(this, starHot ? (event.bookmarked() ? "Remove the bookmark." : "Bookmark this event.") : "Click to add or edit a note.");
			}
		}

		@Override
		public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
			if (!contains(mouseX, mouseY)) {
				return false;
			}

			if (mouseX >= x + width - 18) {
				event.setBookmarked(!event.bookmarked());
				timeline().changed();
			} else {
				selected = event == selected ? null : event;
			}

			rebuild();
			return true;
		}
	}
}
