package io.github.theamethyx.femboyclient.client.modules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;

/** Lists enabled modules at the edge of the screen. */
public final class ModuleListHudModule extends Module implements ClientHooks.HudBlock {
	public enum Sort implements EnumSetting.Labeled {
		LENGTH("Longest first"), NAME("Alphabetical");

		private final String label;

		Sort(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final EnumSetting<HudAnchor> anchor = settings().add(new EnumSetting<>("anchor", "Position",
			"Screen corner for the list.", HudAnchor.TOP_RIGHT));
	private final EnumSetting<Sort> sort = settings().add(new EnumSetting<>("sort", "Order",
			"How to order the list.", Sort.LENGTH));
	private final BooleanSetting hideHud = settings().add(new BooleanSetting("hide_hud", "Hide HUD modules",
			"Leave out modules whose only purpose is showing HUD text (they are visible anyway).", true));
	private final BooleanSetting showPaused = settings().add(new BooleanSetting("show_paused", "Show paused modules",
			"Also list modules that are enabled but currently paused (for example outside a test environment), dimmed.", true));

	public ModuleListHudModule() {
		super("module_list_hud", "Module List", "Lists your enabled modules at the edge of the screen.",
				ModuleCategory.HUD, false, NO_KEY);
	}

	@Override
	public HudAnchor hudAnchor() {
		return anchor.get();
	}

	@Override
	public boolean panelStyle() {
		return false;
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		List<Module> shown = new ArrayList<>();
		for (Module module : FemboyClient.get().modules().all()) {
			if (module == this || !module.isEnabled()) continue;
			if (hideHud.get() && module.category() == ModuleCategory.HUD) continue;
			if (!module.isRunning() && !showPaused.get()) continue;
			shown.add(module);
		}

		if (sort.get() == Sort.NAME) {
			shown.sort(Comparator.comparing(Module::name, String.CASE_INSENSITIVE_ORDER));
		} else {
			shown.sort(Comparator.comparingInt((Module m) -> Minecraft.getInstance().font.width(m.name())).reversed());
		}

		for (Module module : shown) {
			boolean running = module.isRunning();
			lines.add(new HudLine("", module.name(), running ? Theme.TEXT : Theme.TEXT_MUTED, running ? Theme.ACCENT : Theme.BORDER_STRONG));
		}
	}

	@Override
	public java.util.List<String> aliases() {
		return java.util.List.of("array list", "arraylist", "enabled modules");
	}
}
