package io.github.theamethyx.femboyclient.client.gui.page;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.Util;

import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.gui.widget.Button;
import io.github.theamethyx.femboyclient.client.gui.widget.Container;
import io.github.theamethyx.femboyclient.client.gui.widget.ScrollList;
import io.github.theamethyx.femboyclient.client.gui.widget.SettingRow;
import io.github.theamethyx.femboyclient.client.gui.widget.TextField;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.core.config.ConfigurationManager;

/** Profiles, client-wide options, authorized test servers and config files. */
public class ConfigPage extends Page {
	private static final long CONFIRM_WINDOW_MS = 4000;

	private final ScrollList content;
	private String feedback;
	private int feedbackColor;
	private String pendingDelete;
	private long pendingDeleteAt;

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
		return "Profiles, client options and authorized servers.";
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

		rows.add(new SectionTitle("Profiles", "Each profile stores which modules are on, their keys and settings. Changes save automatically."));
		for (String profile : config.profiles()) {
			rows.add(new ProfileRow(profile));
		}

		rows.add(new CreateRow("New profile name", "Create", "Create a profile as a copy of the current one, without switching to it.", 32,
				cp -> Character.isLetterOrDigit(cp) || cp == '-' || cp == '_', name -> {
			String problem = config.createProfile(name.trim());
			report(problem == null ? "Created profile '" + name.trim() + "'." : problem, problem == null);
			return problem == null;
		}));

		rows.add(new SectionTitle("Client options", "Apply to every profile."));
		rows.addAll(SettingRow.rowsFor(femboy.options().settings.all()));

		rows.add(new SectionTitle("Authorized test servers", "Entity ESP, Storage ESP, the Geode Analyzer and Free Camera only run in "
				+ "singleplayer and on servers listed here. Only add servers you own or have permission to test on."));
		List<String> servers = config.authorizedServers();
		if (servers.isEmpty()) {
			rows.add(new ModulesPage.Message("No servers listed: restricted modules run in singleplayer only."));
		}

		for (String server : servers) {
			rows.add(new ServerRow(server));
		}

		rows.add(new CreateRow("host or host:port", "Add", "Allow restricted modules on this server address.", 253,
				cp -> cp > 32 && cp < 127, address -> {
			String problem = config.addAuthorizedServer(address);
			report(problem == null ? "Added " + ConfigurationManager.normalizeServer(address) + "." : problem, problem == null);
			return problem == null;
		}));

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

	private static final class SectionTitle extends Widget {
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

	private final class ProfileRow extends Container {
		private final String name;
		private final boolean active;
		private final Button switchButton;
		private final Button deleteButton;

		ProfileRow(String name) {
			this.name = name;
			this.active = name.equals(femboy.config().activeProfile());
			switchButton = add(new Button("Use", Button.Style.SECONDARY, () -> {
				String problem = femboy.config().switchProfile(name);
				report(problem == null ? "Switched to profile '" + name + "'." : problem, problem == null);
				rebuild();
			}));
			switchButton.tooltip("Save the current profile, then load '" + name + "'.");
			deleteButton = add(new Button(() -> confirming() ? "Confirm" : "", Button.Style.DANGER, this::delete).icon(Icon.TRASH));
			deleteButton.tooltip("Delete this profile. Click twice to confirm. A backup copy stays in the profiles folder.");
		}

		private boolean confirming() {
			return name.equals(pendingDelete) && System.currentTimeMillis() - pendingDeleteAt < CONFIRM_WINDOW_MS;
		}

		private void delete() {
			if (!confirming()) {
				pendingDelete = name;
				pendingDeleteAt = System.currentTimeMillis();
				return;
			}

			pendingDelete = null;
			String problem = femboy.config().deleteProfile(name);
			report(problem == null ? "Deleted profile '" + name + "'." : problem, problem == null);
			rebuild();
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 20;
		}

		@Override
		protected void onLayout() {
			int deleteWidth = confirming() ? 56 : 18;
			deleteButton.setBounds(x + width - 3 - deleteWidth, y + 3, deleteWidth, 14);
			switchButton.setBounds(x + width - 3 - deleteWidth - Theme.GAP - 34, y + 3, 34, 14);
		}

		@Override
		public void render(UiContext ui) {
			onLayout();
			ui.roundRect(x, y, width, height, active ? Theme.SURFACE_ACTIVE : Theme.SURFACE);
			if (active) {
				ui.rect(x, y + 3, 2, height - 6, Theme.ACCENT);
				ui.icon(Icon.CHECK, x + 7, y + 6, 8, Theme.ACCENT);
			} else {
				ui.icon(Icon.FOLDER, x + 7, y + 6, 8, Theme.TEXT_MUTED);
			}

			ui.textClipped(name, x + 20, y + 6, width - 120, active ? Theme.TEXT : Theme.TEXT_SECONDARY);
			if (active) {
				ui.textRight("Active", x + width - 6, y + 6, Theme.ACCENT_STRONG);
			} else {
				super.render(ui);
			}
		}

		@Override
		public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
			return !active && super.mouseClicked(ui, mouseX, mouseY, button);
		}
	}

	private final class ServerRow extends Container {
		private final String address;
		private final Button remove;

		ServerRow(String address) {
			this.address = address;
			remove = add(new Button("", Button.Style.DANGER, () -> {
				femboy.config().removeAuthorizedServer(address);
				report("Removed " + address + ".", true);
				rebuild();
			}).icon(Icon.CLOSE));
			remove.tooltip("Stop treating this server as an authorized test environment.");
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 18;
		}

		@Override
		protected void onLayout() {
			remove.setBounds(x + width - 21, y + 2, 18, 14);
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			ui.icon(Icon.LOCK, x + 7, y + 5, 8, Theme.SUCCESS);
			ui.textClipped(address, x + 20, y + 5, width - 46, Theme.TEXT);
			super.render(ui);
		}
	}

	/** Text field plus a button that submits it; clears the field on success. */
	private final class CreateRow extends Container {
		private final TextField field;
		private final Button button;

		CreateRow(String placeholder, String label, String tooltip, int maxLength, java.util.function.IntPredicate allowed,
				java.util.function.Predicate<String> action) {
			field = add(new TextField(placeholder, maxLength).allow(allowed));
			Runnable submit = () -> {
				if (action.test(field.text())) {
					field.setText("");
					rebuild();
				}
			};
			field.onSubmit(text -> submit.run());
			button = add(new Button(label, Button.Style.PRIMARY, submit).icon(Icon.PLUS));
			button.enabledWhen(() -> !field.text().isBlank());
			button.tooltip(tooltip);
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 15;
		}

		@Override
		protected void onLayout() {
			int buttonWidth = 58;
			field.setBounds(x, y, width - buttonWidth - Theme.GAP, 15);
			button.setBounds(x + width - buttonWidth, y, buttonWidth, 15);
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
