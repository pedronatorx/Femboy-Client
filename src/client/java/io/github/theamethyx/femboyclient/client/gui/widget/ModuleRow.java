package io.github.theamethyx.femboyclient.client.gui.widget;

import java.util.function.Consumer;

import io.github.theamethyx.femboyclient.client.gui.Anim;
import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleManager;

/** A module in the module list: name, status and an on/off switch. Clicking selects it. */
public class ModuleRow extends Container {
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
		this.toggle = add(new ToggleSwitch(module::isEnabled, enabled -> manager.setEnabled(module, enabled)));
	}

	public Module module() {
		return module;
	}

	public ToggleSwitch toggle() {
		return toggle;
	}

	@Override
	public int preferredHeight(UiContext ui, int width) {
		return 20;
	}

	@Override
	protected void onLayout() {
		toggle.setBounds(x + width - ToggleSwitch.WIDTH - 5, y + 5, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT);
	}

	/** Short status used for the dot color and tooltip. */
	public static StatusView status(Module module, ModuleManager manager) {
		if (module.lastError() != null) {
			return new StatusView(Theme.ERROR, "Error: " + module.lastError());
		}

		if (module.isRunning()) {
			return new StatusView(Theme.SUCCESS, "Running");
		}

		if (module.isEnabled()) {
			return new StatusView(Theme.WARNING, "Enabled but paused: " + manager.availability(module).reason());
		}

		return new StatusView(Theme.TEXT_MUTED, "Off");
	}

	public record StatusView(int color, String text) {
	}

	@Override
	public void render(UiContext ui) {
		boolean isSelected = module.id().equals(selected.get());
		boolean hot = ui.hovered(x, y, width, height);
		float h = hover.update(hot ? 1 : 0, ui.frameSeconds(), ui.animate());

		int background = isSelected ? Theme.SURFACE_ACTIVE : Theme.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, h);
		ui.roundRect(x, y, width, height, background);
		if (isSelected) {
			ui.rect(x, y + 3, 2, height - 6, Theme.ACCENT);
		}

		StatusView status = status(module, manager);
		Icon icon = module.lastError() != null ? Icon.WARNING : Icon.byName(module.category().icon());
		ui.icon(icon, x + 6, y + 6, 8, module.lastError() != null ? Theme.ERROR : module.isRunning() ? Theme.ACCENT : Theme.TEXT_MUTED);

		int nameColor = module.isEnabled() ? Theme.TEXT : Theme.TEXT_SECONDARY;
		int textRight = toggle.x() - 6;
		ui.textClipped(module.name(), x + 18, y + 6, textRight - x - 24, nameColor);
		// Small status dot: green running, amber paused, red error.
		ui.rect(textRight - 1, y + 9, 3, 3, status.color());

		toggle.render(ui);
		if (hot) {
			ui.tooltip(module, module.description() + "\n\nStatus: " + status.text());
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

		if (button == 0) {
			onSelect.accept(module);
		} else if (button == 1) {
			// Right-click toggles, a common shortcut in module lists.
			manager.toggle(module);
			onSelect.accept(module);
		}

		return true;
	}
}
