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
		return "Settings";
	}

	@Override
	public String subtitle() {
		return "Emergency off, client options and config files.";
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

		rows.add(new SectionTitle("Client options", "Apply to every profile. Profiles have their own page."));
		rows.addAll(SettingRow.rowsFor(femboy.options().settings.all()));

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
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 16;
		}

		@Override
		protected void onLayout() {
			int half = (width - Theme.GAP) / 2;
			open.setBounds(x, y, half, 16);
			save.setBounds(x + half + Theme.GAP, y, width - half - Theme.GAP, 16);
		}
	}
}
