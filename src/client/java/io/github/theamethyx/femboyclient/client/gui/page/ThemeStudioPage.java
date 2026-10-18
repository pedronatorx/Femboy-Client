package io.github.theamethyx.femboyclient.client.gui.page;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;

import net.minecraft.client.Minecraft;

import io.github.theamethyx.femboyclient.client.GlobalOptions;
import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.ThemePresets;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.gui.widget.Button;
import io.github.theamethyx.femboyclient.client.gui.widget.Container;
import io.github.theamethyx.femboyclient.client.gui.widget.ScrollList;
import io.github.theamethyx.femboyclient.client.gui.widget.SettingRow;
import io.github.theamethyx.femboyclient.client.gui.widget.TextField;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.core.setting.Setting;

/** Theme Studio: presets, a live preview, every appearance setting, and themes you save, share and import. */
public class ThemeStudioPage extends Page {
	private final ScrollList content;
	private final ThemePresets presets;
	private String feedback;
	private int feedbackColor;

	public ThemeStudioPage(UiContext ui) {
		super(ui);
		content = add(new ScrollList(ui, Theme.GAP, 0));
		presets = new ThemePresets(femboy.options(), femboy.config());
	}

	@Override
	public String title() {
		return "Settings";
	}

	@Override
	public String navLabel() {
		return "Appearance";
	}

	@Override
	public String subtitle() {
		return "Theme Studio: colors, corners, opacity and motion. Stored in each profile.";
	}

	@Override
	public Icon icon() {
		return Icon.PALETTE;
	}

	@Override
	protected void onLayout() {
		content.setBounds(x, y, width, height);
		rebuild();
	}

	private void report(String message, boolean ok) {
		feedback = message;
		feedbackColor = ok ? Theme.SUCCESS : Theme.ERROR;
		rebuild();
	}

	private void rebuild() {
		List<Widget> rows = new ArrayList<>();
		rows.add(new Feedback());
		rows.add(new ConfigPage.SectionTitle("Presets", "A starting point; adjust anything below afterwards."));
		rows.add(new PresetGrid());
		rows.add(new Preview());

		List<Setting<?>> theme = new ArrayList<>();
		List<Setting<?>> motion = new ArrayList<>();
		for (Setting<?> setting : femboy.options().settings.all()) {
			if (GlobalOptions.THEME_SECTION.equals(setting.section())) theme.add(setting);
			if (GlobalOptions.MOTION_SECTION.equals(setting.section())) motion.add(setting);
		}

		rows.add(new ConfigPage.SectionTitle("Colors and shapes", "Changes apply at once. Fonts: Minecraft's own font is used; other fonts "
				+ "are not supported."));
		rows.addAll(SettingRow.rowsFor("options", theme));
		rows.add(new ConfigPage.SectionTitle("Motion", "Animations also follow the performance mode: Performance mode turns them off."));
		rows.addAll(SettingRow.rowsFor("options", motion));

		rows.add(new ConfigPage.SectionTitle("Your themes", "Save the current look under a name, share it as text, or import one from the clipboard."));
		Map<String, Map<String, JsonElement>> saved = presets.saved();
		for (String name : saved.keySet()) {
			rows.add(new SavedRow(name, saved.get(name)));
		}

		if (saved.isEmpty()) {
			rows.add(new ModulesPage.Message("No saved themes yet."));
		}

		rows.add(new SaveRow());
		content.setChildren(rows);
	}

	private final class Feedback extends Widget {
		@Override
		public int preferredHeight(UiContext ui, int width) {
			return feedback == null ? 0 : ui.wrappedHeight(feedback, width - 16) + 8;
		}

		@Override
		public void render(UiContext ui) {
			if (feedback != null) {
				ui.roundRect(x, y, width, height, Theme.withAlpha(feedbackColor, 0.12f));
				ui.rect(x, y + 2, 2, height - 4, feedbackColor);
				ui.wrapped(feedback, x + 8, y + 4, width - 16, feedbackColor);
			}
		}
	}

	/** Built-in presets as cards with a color swatch; a click applies one. */
	private final class PresetGrid extends Widget {
		private int columns() {
			return Math.max(1, Math.min(3, width / 130));
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			int columns = Math.max(1, Math.min(3, width / 130));
			return (ThemePresets.BUILT_IN.size() + columns - 1) / columns * 34;
		}

		@Override
		public void render(UiContext ui) {
			int columns = columns();
			int cardW = (width - (columns - 1) * Theme.GAP) / columns;
			for (int i = 0; i < ThemePresets.BUILT_IN.size(); i++) {
				ThemePresets.Preset preset = ThemePresets.BUILT_IN.get(i);
				int cx = x + i % columns * (cardW + Theme.GAP);
				int cy = y + i / columns * 34;
				boolean hot = ui.hovered(cx, cy, cardW, 30);
				ui.roundRect(cx, cy, cardW, 30, hot ? Theme.SURFACE_HOVER : Theme.SURFACE, 5);
				Theme.Palette palette = Theme.Palette.valueOf(preset.values().get("theme").getAsString());
				int[] swatch = palette.swatch();
				ui.roundRect(cx + 5, cy + 5, 20, 20, swatch[0], 4);
				ui.gradientRect(cx + 9, cy + 13, 12, 5, swatch[1], swatch[2], 2);
				ui.textClipped(preset.name(), cx + 31, cy + 6, cardW - 36, Theme.TEXT);
				ui.textScaled(ui.ellipsize(preset.description(), Math.round((cardW - 36) / 0.75f)), cx + 31, cy + 18, 0.75f, Theme.TEXT_MUTED);
				if (hot) {
					ui.pointer();
					ui.tooltip(preset, preset.name() + ": " + preset.description() + " Click to apply.");
				}
			}
		}

		@Override
		public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
			int columns = columns();
			int cardW = (width - (columns - 1) * Theme.GAP) / columns;
			for (int i = 0; i < ThemePresets.BUILT_IN.size(); i++) {
				int cx = x + i % columns * (cardW + Theme.GAP);
				int cy = y + i / columns * 34;
				if (UiContext.inside(mouseX, mouseY, cx, cy, cardW, 30)) {
					ThemePresets.Preset preset = ThemePresets.BUILT_IN.get(i);
					List<String> problems = presets.apply(preset.values());
					report(problems.isEmpty() ? "Applied '" + preset.name() + "'." : String.join(" ", problems), problems.isEmpty());
					return true;
				}
			}

			return false;
		}
	}

	/** A small live sample of the current look. */
	private static final class Preview extends Widget {
		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 58;
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.PANEL, 6);
			ui.outline(x, y, width, height, Theme.BORDER, 6);
			ui.text("Preview", x + 8, y + 6, Theme.TEXT);
			ui.text("Secondary text", x + 8, y + 18, Theme.TEXT_SECONDARY);
			ui.text("Muted hint", x + 8, y + 29, Theme.TEXT_MUTED);
			int cx = x + Math.max(90, width / 3);
			ui.gradientRect(cx, y + 6, 70, 14, Theme.ACCENT, Theme.ACCENT_2, 5);
			ui.textCentered("Selected", cx + 35, y + 9, 0xFFFFFFFF);
			ui.roundRect(cx, y + 24, 70, 14, Theme.SURFACE_HOVER, 5);
			ui.textCentered("Hovered", cx + 35, y + 27, Theme.TEXT);
			int sx = cx + 80;
			ui.roundRect(sx, y + 8, 20, 10, Theme.ACCENT, 5);
			ui.roundRect(sx + 10, y + 8, 10, 10, 0xFFFFFFFF, 5);
			ui.roundRect(sx, y + 26, 20, 10, Theme.SURFACE_ACTIVE, 5);
			ui.roundRect(sx, y + 26, 10, 10, Theme.TEXT_SECONDARY, 5);
			ui.icon(Icon.HEART, sx + 28, y + 8, 10, Theme.ACCENT_ICONS ? Theme.ACCENT : Theme.TEXT_SECONDARY);
			ui.icon(Icon.BELL, sx + 28, y + 26, 10, Theme.ACCENT_2);
			ui.roundRect(x + 8, y + 42, Math.min(140, width - 16), 11, Theme.withAlpha(Theme.SURFACE, Theme.HUD_BACKGROUND), 3);
			ui.text("HUD: 120 FPS", x + 11, y + 44, Theme.TEXT);
		}
	}

	private final class SavedRow extends Container {
		private final String name;

		SavedRow(String name, Map<String, JsonElement> values) {
			this.name = name;
			add(new Button("Apply", Button.Style.PRIMARY, () -> {
				List<String> problems = presets.apply(values);
				report(problems.isEmpty() ? "Applied '" + name + "'." : String.join(" ", problems), problems.isEmpty());
			}).icon(Icon.CHECK)).tooltip("Use this theme.");
			add(new Button("", Button.Style.SECONDARY, () -> {
				Minecraft.getInstance().keyboardHandler.setClipboard(presets.export(name));
				report("'" + name + "' copied to the clipboard as text.", true);
			}).icon(Icon.COPY)).tooltip("Copy as text to share.");
			add(new Button("", Button.Style.DANGER, () -> {
				presets.delete(name);
				report("Deleted '" + name + "'.", true);
			}).icon(Icon.TRASH)).tooltip("Delete this saved theme.");
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 18;
		}

		@Override
		protected void onLayout() {
			int right = x + width;
			children.get(2).setBounds(right - 18, y + 1, 18, 16);
			children.get(1).setBounds(right - 40, y + 1, 18, 16);
			children.get(0).setBounds(right - 100, y + 1, 56, 16);
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			ui.icon(Icon.PALETTE, x + 5, y + 5, 8, Theme.ACCENT);
			ui.textClipped(name, x + 17, y + 5, width - 125, Theme.TEXT);
			super.render(ui);
		}
	}

	private final class SaveRow extends Container {
		private final TextField name = add(new TextField("Name for the current look", 24));

		SaveRow() {
			add(new Button("Save", Button.Style.PRIMARY, () -> {
				String problem = presets.save(name.text());
				report(problem == null ? "Saved '" + name.text().strip() + "'." : problem, problem == null);
			}).icon(Icon.SAVE)).tooltip("Save the current theme settings under this name.");
			add(new Button("Import", Button.Style.SECONDARY, () -> {
				ThemePresets.ImportResult result = presets.importTheme(Minecraft.getInstance().keyboardHandler.getClipboard());
				if (result.error() != null) {
					report("Import failed: " + result.error(), false);
				} else {
					report("Imported '" + result.name() + "'" + (result.warnings().isEmpty() ? "." : "; skipped: " + String.join(" ", result.warnings())), true);
				}
			}).icon(Icon.FOLDER)).tooltip("Import a theme copied as text (from the clipboard). It is saved, not applied.");
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 16;
		}

		@Override
		protected void onLayout() {
			int buttons = 130;
			children.get(0).setBounds(x, y, width - buttons - Theme.GAP, 16);
			children.get(1).setBounds(x + width - buttons, y, 56, 16);
			children.get(2).setBounds(x + width - 70, y, 70, 16);
		}
	}
}
