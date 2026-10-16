package io.github.theamethyx.femboyclient.client.gui.widget;

import java.util.function.Consumer;
import java.util.function.Supplier;

import io.github.theamethyx.femboyclient.client.gui.Anim;
import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleManager;

/**
 * A compact module entry for the Home grid: icon, name, a short description and a switch. Left-click turns the module on
 * or off, right-click opens its settings panel.
 */
public class ModuleTile extends Container {
	public static final int HEIGHT = 24;

	private final Module module;
	private final ModuleManager manager;
	private final Supplier<String> selected;
	private final Consumer<Module> onOpen;
	private final ToggleSwitch toggle;
	private final Anim hover = new Anim(0, 20);
	private final Anim active = new Anim(0, 14);

	public ModuleTile(Module module, ModuleManager manager, Supplier<String> selected, Consumer<Module> onOpen) {
		this.module = module;
		this.manager = manager;
		this.selected = selected;
		this.onOpen = onOpen;
		this.toggle = add(new ToggleSwitch(module::isEnabled, enabled -> manager.setEnabled(module, enabled)));
		active.snap(module.isEnabled() ? 1 : 0);
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
		toggle.setBounds(x + width - ToggleSwitch.WIDTH - 6, y + (height - ToggleSwitch.HEIGHT) / 2, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT);
	}

	@Override
	public void render(UiContext ui) {
		boolean hot = ui.hovered(x, y, width, height);
		boolean isSelected = module.id().equals(selected.get());
		float h = hover.update(hot ? 1 : 0, ui.frameSeconds(), ui.animate());
		float on = active.update(module.isEnabled() ? 1 : 0, ui.frameSeconds(), ui.animate());

		int base = Theme.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, h);
		ui.roundRect(x, y, width, height, isSelected ? Theme.SURFACE_ACTIVE : Theme.lerp(base, Theme.ACCENT_DIM, on * 0.3f), 3);
		if (isSelected || on > 0.01f) {
			// Accent bar on the left grows in when the module is on.
			int barHeight = Math.round((height - 8) * (isSelected ? 1 : on));
			ui.rect(x, y + (height - barHeight) / 2, 2, barHeight, Theme.ACCENT);
		}

		ModuleRow.StatusView status = ModuleRow.status(module, manager);
		Icon icon = module.lastError() != null ? Icon.WARNING : Icon.byName(module.category().icon());
		ui.roundRect(x + 5, y + 4, 16, 16, Theme.withAlpha(Theme.lerp(Theme.ACCENT_DIM, Theme.ACCENT, on), 0.25f + on * 0.35f), 3);
		ui.icon(icon, x + 9, y + 8, 8, module.lastError() != null ? Theme.ERROR : Theme.lerp(Theme.TEXT_SECONDARY, 0xFFFFFFFF, on));

		int textX = x + 26;
		int textWidth = toggle.x() - textX - 4;
		ui.textClipped(module.name(), textX, y + 4, textWidth, Theme.lerp(Theme.TEXT_SECONDARY, Theme.TEXT, Math.max(on, h)));
		String description = ui.ellipsize(module.description(), Math.round(textWidth / 0.7f));
		ui.textScaled(description, textX, y + 14.5f, 0.7f, Theme.TEXT_MUTED);
		if (module.isEnabled() && !module.isRunning()) {
			// Enabled but paused (or failed): a small dot before the switch.
			ui.rect(toggle.x() - 5, y + height / 2 - 1, 3, 3, status.color());
		}

		toggle.render(ui);
		if (hot) {
			ui.tooltip(module, module.description() + "\n\nStatus: " + status.text() + "\nLeft-click: on/off   Right-click: settings");
			ui.pointer();
		}
	}

	@Override
	public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
		if (!contains(mouseX, mouseY)) {
			return false;
		}

		if (button == 1) {
			onOpen.accept(module);
		} else if (button == 0) {
			manager.toggle(module);
		}

		return true;
	}
}
