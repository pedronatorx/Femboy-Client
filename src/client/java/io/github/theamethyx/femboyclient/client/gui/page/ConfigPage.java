package io.github.theamethyx.femboyclient.client.gui.page;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.Util;

import io.github.theamethyx.femboyclient.client.ClientKeys;
import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.gui.widget.Button;
import io.github.theamethyx.femboyclient.client.gui.widget.Container;
import io.github.theamethyx.femboyclient.client.gui.widget.ScrollList;
import io.github.theamethyx.femboyclient.client.gui.widget.SettingRow;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.core.config.ConfigurationManager;

/** Profiles, client-wide options and config files. */
public class ConfigPage extends Page {
	private final ScrollList content;
	private String feedback;
	private int feedbackColor;

	public ConfigPage(UiContext ui) {
		super(ui);
		content = add(new ScrollList(ui, Theme.GAP, 0));
	}

	@Override
	public String title() {
		return "Settings";
	}

	@Override
	public String navLabel() {
		return "General";
	}

	@Override
	public String subtitle() {
		return "Emergency off, client options, backups and config files.";
	}

	@Override
	public Icon icon() {
		return Icon.SETTINGS;
	}

	@Override
	protected void onLayout() {
		content.setBounds(x, y, width, height);
		rebuild();
	}

	private void report(String message, boolean ok) {
		feedback = message;
		feedbackColor = ok ? Theme.SUCCESS : Theme.ERROR;
	}

	private void rebuild() {
		ConfigurationManager config = femboy.config();
		List<Widget> rows = new ArrayList<>();
		rows.add(new Feedback());

		rows.add(new SectionTitle("Emergency off", "Turns every module off at once; each one restores what it changed (camera, "
				+ "lighting, held item). Automatic profile switching is paused too. Bind a key for it in Minecraft's Options > "
				+ "Controls > Key Binds under 'Femboy Client' (currently " + ClientKeys.disableAll.getTranslatedKeyMessage().getString() + ")."));
		rows.add(new DisableAllRow());

		rows.add(new SectionTitle("HUD layout", "Drag HUD blocks anywhere on the screen. Turn on the HUD modules you want to place first."));
		rows.add(new HudEditorRow());

		rows.add(new SectionTitle("Client options", "The look (theme, motion) is on the Appearance tab. Profiles have their own page."));
		rows.addAll(SettingRow.rowsFor("options", femboy.options().settings.all().stream()
				.filter(setting -> !io.github.theamethyx.femboyclient.client.GlobalOptions.THEME_SECTION.equals(setting.section())
						&& !io.github.theamethyx.femboyclient.client.GlobalOptions.MOTION_SECTION.equals(setting.section())
						&& !io.github.theamethyx.femboyclient.client.GlobalOptions.SMART_SECTION.equals(setting.section()))
				.<io.github.theamethyx.femboyclient.core.setting.Setting<?>>map(setting -> setting).toList()));

		rows.add(new SectionTitle("Restore points", "Copies of all settings and profiles. One is made automatically before a restore, "
				+ "a full reset, replacing a profile with a preset, and a configuration format update; the newest "
				+ ConfigurationManager.MAX_RESTORE_POINTS + " are kept."));
		rows.add(new CreateRestorePoint());
		List<ConfigurationManager.RestorePoint> points = config.restorePoints();
		for (int i = 0; i < Math.min(8, points.size()); i++) {
			rows.add(new RestorePointRow(points.get(i)));
		}

		if (points.isEmpty()) {
			rows.add(new ModulesPage.Message("No restore points yet."));
		} else if (points.size() > 8) {
			rows.add(new ModulesPage.Message((points.size() - 8) + " older restore point(s) are in the restore-points folder."));
		}

		rows.add(new SectionTitle("Reset everything", "Puts client options, the HUD layout, switching rules and every module of the active "
				+ "profile back to a fresh installation's state (all modules off). Other profiles are kept, and a restore point is made "
				+ "first, so this can be undone above."));
		rows.add(new ResetEverything());

		rows.add(new SectionTitle("Files", "Stored in " + config.root().toAbsolutePath()));
		rows.add(new FileActions());
		for (String problem : config.lastReport()) {
			rows.add(new ModulesPage.Message("Recovered at startup: " + problem));
		}

		content.setChildren(rows);
	}

	private final class Feedback extends Widget {
		@Override
		public int preferredHeight(UiContext ui, int width) {
			return feedback == null ? 0 : ui.wrappedHeight(feedback, width - 16) + 8;
		}

		@Override
		public void render(UiContext ui) {
			if (feedback == null) {
				return;
			}

			ui.roundRect(x, y, width, height, Theme.withAlpha(feedbackColor, 0.12f));
			ui.rect(x, y + 2, 2, height - 4, feedbackColor);
			ui.wrapped(feedback, x + 8, y + 4, width - 16, feedbackColor);
		}
	}

	/** Accent heading with a muted explanation; shared with the Profiles page. */
	static final class SectionTitle extends Widget {
		private final String title;
		private final String description;

		SectionTitle(String title, String description) {
			this.title = title;
			this.description = description;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 16 + ui.wrappedHeight(description, width - 4);
		}

		@Override
		public void render(UiContext ui) {
			ui.text(title, x + 2, y + 4, Theme.ACCENT_STRONG);
			ui.wrapped(description, x + 2, y + 15, width - 4, Theme.TEXT_MUTED);
		}
	}

	private final class HudEditorRow extends Container {
		private final Button button;

		HudEditorRow() {
			button = add(new Button(() -> "Edit HUD layout" + (femboy.hud().layout().size() > 0 ? " (" + femboy.hud().layout().size() + " moved)" : ""),
					Button.Style.SECONDARY, () -> net.minecraft.client.Minecraft.getInstance().setScreen(
					new io.github.theamethyx.femboyclient.client.gui.HudEditorScreen(net.minecraft.client.Minecraft.getInstance().screen, femboy.hud())))
					.icon(Icon.HUD));
			button.tooltip("Open the HUD editor: drag blocks, right-click to put one back in its corner, R to reset all.");
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 16;
		}

		@Override
		protected void onLayout() {
			button.setBounds(x, y, width, 16);
		}
	}

	private final class DisableAllRow extends Container {
		private final Button button;

		DisableAllRow() {
			button = add(new Button("Turn all modules off", Button.Style.DANGER, () -> {
				int count = femboy.disableEverything();
				report(count == 0 ? "No module was on." : "Turned " + count + " module(s) off.", true);
				rebuild();
			}).icon(Icon.STOP));
			button.tooltip("Turn every module off now. Turn them back on one by one, or switch profiles.");
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 16;
		}

		@Override
		protected void onLayout() {
			button.setBounds(x, y, width, 16);
		}
	}

	private final class CreateRestorePoint extends Container {
		private final Button button = add(new Button("Create a restore point now", Button.Style.SECONDARY, () -> {
			String problem = femboy.config().createRestorePoint("Made by hand");
			report(problem == null ? "Restore point created." : problem, problem == null);
			rebuild();
		}).icon(Icon.SAVE));

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 16;
		}

		@Override
		protected void onLayout() {
			button.setBounds(x, y, width, 16);
		}
	}

	private static final java.time.format.DateTimeFormatter WHEN = java.time.format.DateTimeFormatter.ofPattern("d MMM HH:mm")
			.withZone(java.time.ZoneId.systemDefault());

	private final class RestorePointRow extends Container {
		private final ConfigurationManager.RestorePoint point;

		RestorePointRow(ConfigurationManager.RestorePoint point) {
			this.point = point;
			add(new io.github.theamethyx.femboyclient.client.gui.widget.ConfirmButton("Restore", "Click again", Button.Style.SECONDARY, () -> {
				String problem = femboy.config().restore(point.id());
				report(problem == null ? "Restored '" + point.label() + "'. The state before it is the newest restore point." : problem, problem == null);
				rebuild();
			}).icon(Icon.UNDO)).tooltip("Put these settings back (two clicks). Your current state is saved as a restore point first.");
			add(new io.github.theamethyx.femboyclient.client.gui.widget.ConfirmButton("", "Delete?", Button.Style.DANGER, () -> {
				String problem = femboy.config().deleteRestorePoint(point.id());
				report(problem == null ? "Deleted the restore point." : problem, problem == null);
				rebuild();
			}).icon(Icon.TRASH)).tooltip("Delete this restore point (two clicks).");
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 22;
		}

		@Override
		protected void onLayout() {
			children.get(1).setBounds(x + width - 46, y + 3, 44, 16);
			children.get(0).setBounds(x + width - 46 - Theme.GAP - 76, y + 3, 76, 16);
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			ui.icon(Icon.CLOCK, x + 5, y + 7, 8, Theme.ACCENT);
			String when = point.createdMillis() == 0 ? point.id() : WHEN.format(java.time.Instant.ofEpochMilli(point.createdMillis()));
			ui.textClipped(point.label(), x + 17, y + 3, width - 150, Theme.TEXT);
			ui.textScaled(ui.ellipsize(when + "  ·  " + point.profiles().size() + " profile(s): " + String.join(", ", point.profiles()),
					Math.round((width - 150) / 0.75f)), x + 17, y + 13, 0.75f, Theme.TEXT_MUTED);
			super.render(ui);
		}
	}

	private final class ResetEverything extends Container {
		private final Button button = add(new io.github.theamethyx.femboyclient.client.gui.widget.ConfirmButton("Reset everything to defaults",
				"Click again to reset everything", Button.Style.DANGER, () -> {
			String problem = femboy.config().resetEverything();
			report(problem == null ? "Everything is back to defaults. The previous state is the newest restore point." : problem, problem == null);
			rebuild();
		}).icon(Icon.RESET));

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 16;
		}

		@Override
		protected void onLayout() {
			button.setBounds(x, y, width, 16);
		}
	}

	private final class FileActions extends Container {
		private final Button open;
		private final Button save;

		FileActions() {
			open = add(new Button("Open folder", Button.Style.SECONDARY, () -> Util.getPlatform().openPath(femboy.config().root())).icon(Icon.FOLDER));
			open.tooltip("Open the Femboy Client configuration folder in your file manager.");
			save = add(new Button("Save now", Button.Style.SECONDARY, () -> {
				boolean ok = femboy.config().saveNow();
				report(ok ? "All settings saved." : "Saving failed; see Diagnostics for details.", ok);
				rebuild();
			}).icon(Icon.CHECK));
			save.tooltip("Write all settings to disk immediately (normally done automatically).");
			reload = add(new io.github.theamethyx.femboyclient.client.gui.widget.ConfirmButton("Reload from disk", "Click again to reload",
					Button.Style.SECONDARY, () -> {
				List<String> problems = femboy.config().load();
				report(problems.isEmpty() ? "Reloaded the files from disk." : "Reloaded; " + problems.size() + " problem(s) recovered (listed below).", true);
				rebuild();
			}).icon(Icon.RESET));
			reload.tooltip("Read the configuration files again, for example after editing them by hand. Changes not yet saved are lost (two clicks).");
		}

		private final Button reload;

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 16;
		}

		@Override
		protected void onLayout() {
			int third = (width - 2 * Theme.GAP) / 3;
			open.setBounds(x, y, third, 16);
			save.setBounds(x + third + Theme.GAP, y, third, 16);
			reload.setBounds(x + 2 * (third + Theme.GAP), y, width - 2 * (third + Theme.GAP), 16);
		}
	}
}
