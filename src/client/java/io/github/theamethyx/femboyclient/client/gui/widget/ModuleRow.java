package io.github.theamethyx.femboyclient.client.gui.widget;

import java.util.function.Consumer;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.gui.Anim;
import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.ModuleStatus;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleManager;

/**
 * A module in a module list, the same everywhere: its name, one line saying what it does (or, when something is wrong,
 * what), its key if it has one, and an on/off switch. Clicking the row opens its settings; the switch turns it on or off.
 */
public class ModuleRow extends Container {
	public static final int HEIGHT = 26;

	private final Module module;
	private final ModuleManager manager;
	private final Consumer<Module> onSelect;
	private final java.util.function.Supplier<String> selected;
	private final ToggleSwitch toggle;
	private final Anim hover = new Anim(0, 18);

	public ModuleRow(Module module, ModuleManager manager, java.util.function.Supplier<String> selected, Consumer<Module> onSelect) {
		this.module = module;
		this.manager = manager;
		this.selected = selected;
		this.onSelect = onSelect;
		this.toggle = add(new ToggleSwitch(module::isEnabled, enabled -> {
			manager.setEnabled(module, enabled);
			FemboyClient.get().quickAccess().touch(module);
		}));
		toggle.tooltip(() -> (module.isEnabled() ? "On" : "Off") + ". Click to turn " + module.name() + (module.isEnabled() ? " off." : " on."));
	}

	public Module module() {
		return module;
	}

	public ToggleSwitch toggle() {
		return toggle;
	}

	@Override
	public int preferredHeight(UiContext ui, int width) {
		return HEIGHT;
	}

	@Override
	protected void onLayout() {
		toggle.setBounds(x + width - ToggleSwitch.WIDTH - 6, y + (HEIGHT - ToggleSwitch.HEIGHT) / 2, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT);
	}

	/** Short status: color and text (kept for the developer page and older callers). */
	public static StatusView status(Module module, ModuleManager manager) {
		ModuleStatus status = ModuleStatus.of(module, manager);
		return new StatusView(status.color(), status.sentence());
	}

	public record StatusView(int color, String text) {
	}

	@Override
	public void render(UiContext ui) {
		boolean isSelected = module.id().equals(selected.get());
		boolean hot = ui.hovered(x, y, width, height);
		float h = hover.update(hot ? 1 : 0, ui.frameSeconds(), ui.animate());

		if (isSelected) {
			ui.roundRect(x, y, width, height, Theme.SURFACE_ACTIVE, Theme.RADIUS_CONTROL);
			ui.roundRect(x, y + 5, 2, height - 10, Theme.ACCENT, 1);
		} else if (h > 0.01f) {
			ui.roundRect(x, y, width, height, Theme.withAlpha(Theme.SURFACE_HOVER, h), Theme.RADIUS_CONTROL);
		}

		ModuleStatus status = ModuleStatus.of(module, manager);
		int textRight = toggle.x() - 6;
		int left = x + 8;
		boolean favorite = FemboyClient.get().quickAccess().isFavorite(module);
		if (favorite) {
			ui.icon(Icon.STAR, left, y + 5, 7, Theme.WARNING);
			left += 10;
		}

		String key = module.key() == Module.NO_KEY ? null : KeybindButton.keyName(module.key());
		int keyWidth = 0;
		if (key != null) {
			keyWidth = Math.min(40, ui.textWidth(key) + 6);
			ui.roundRect(textRight - keyWidth, y + 4, keyWidth, 10, Theme.withAlpha(Theme.SURFACE_ACTIVE, 0.9f), 2);
			ui.textScaled(ui.ellipsize(key, Math.round((keyWidth - 4) / ui.small())), textRight - keyWidth + 3, y + 5 + (8 - 8 * ui.small()) / 2,
					ui.small(), Theme.TEXT_SECONDARY);
			keyWidth += 4;
		}

		ui.textClipped(module.name(), left, y + 5, textRight - left - keyWidth, Theme.TEXT);

		// Second line: what it does, unless something about its state matters more.
		boolean showState = status.kind() != ModuleStatus.Kind.OFF && status.kind() != ModuleStatus.Kind.ON;
		int lineY = y + 16;
		if (showState) {
			ui.icon(status.icon(), x + 8, lineY, 7, status.color());
			String text = status.detail().isEmpty() ? status.label() : status.label() + ": " + status.detail();
			ui.textScaled(ui.ellipsize(text, Math.round((textRight - x - 18) / ui.small())), x + 18, lineY + (8 - 8 * ui.small()) / 2, ui.small(),
					status.color());
		} else {
			ui.textScaled(ui.ellipsize(module.description(), Math.round((textRight - x - 8) / ui.small())), x + 8, lineY + (8 - 8 * ui.small()) / 2,
					ui.small(), Theme.TEXT_MUTED);
		}

		toggle.render(ui);
		if (hot) {
			ui.tooltip(module, module.description() + "\n\nStatus: " + status.sentence() + (key == null ? "" : "\nKey: " + key));
			if (!toggle.contains(ui.mouseX(), ui.mouseY())) {
				ui.pointer();
			}
		}
	}

	@Override
	public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
		if (!contains(mouseX, mouseY)) {
			return false;
		}

		if (toggle.mouseClicked(ui, mouseX, mouseY, button)) {
			onSelect.accept(module);
			return true;
		}

		if (button == 0 || button == 1) {
			// Left- or right-click opens the module's settings; the switch turns it on or off.
			onSelect.accept(module);
		}

		return true;
	}
}
