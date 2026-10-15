package io.github.theamethyx.femboyclient.client.gui.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.modules.AutoAttributeSwapModule;
import io.github.theamethyx.femboyclient.core.setting.IdSetSetting;
import io.github.theamethyx.femboyclient.core.setting.Setting;

/** Auto Attribute Swap's panel: what happened last, then one card per item rule. */
public final class AttributeSwapPanel extends Container {
	private static final int GAP = 4;

	private final AutoAttributeSwapModule module;
	private final List<Integer> heights = new ArrayList<>();

	public AttributeSwapPanel(AutoAttributeSwapModule module) {
		this.module = module;
		add(new StatusCard());
		for (AutoAttributeSwapModule.RuleSettings rule : module.rules()) {
			add(new RuleCard(rule));
		}
	}

	@Override
	public int preferredHeight(UiContext ui, int width) {
		heights.clear();
		int total = 0;
		for (Widget child : children) {
			int h = child.preferredHeight(ui, width);
			heights.add(h);
			total += h + GAP;
		}

		return total;
	}

	@Override
	protected void onLayout() {
		int cy = y;
		for (int i = 0; i < children.size() && i < heights.size(); i++) {
			children.get(i).setBounds(x, cy, width, heights.get(i));
			cy += heights.get(i) + GAP;
		}
	}

	@Override
	public void render(UiContext ui) {
		onLayout();
		super.render(ui);
	}

	/** The last swap as the client saw it. */
	private final class StatusCard extends Widget {
		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 20 + ui.wrappedHeight(text(), width - 12) + (module.lastSkip() == null ? 0 : 11);
		}

		private String text() {
			return module.isRunning() ? module.describeLast() : "Turn the module on to start. Swaps happen when you attack.";
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			ui.icon(Icon.COMBAT, x + 6, y + 5, 9, Theme.ACCENT);
			ui.text("Last swap", x + 19, y + 6, Theme.TEXT);
			int used = ui.wrapped(text(), x + 6, y + 18, width - 12, Theme.TEXT_SECONDARY);
			if (module.lastSkip() != null) {
				ui.textClipped("Last attack without swap: " + module.lastSkip(), x + 6, y + 19 + used, width - 12, Theme.TEXT_MUTED);
			}
		}
	}

	/** One item rule: icon, name, purpose, on/off, reset, its settings and live state. */
	private final class RuleCard extends Container {
		private static final int HEADER = 32;

		private final AutoAttributeSwapModule.RuleSettings rule;
		private final ToggleSwitch toggle;
		private final Button reset;
		private final List<Widget> rows = new ArrayList<>();
		private final List<Integer> rowHeights = new ArrayList<>();

		RuleCard(AutoAttributeSwapModule.RuleSettings rule) {
			this.rule = rule;
			toggle = add(new ToggleSwitch(rule.enabled()::get, rule.enabled()::set));
			toggle.tooltip(rule.enabled().description());
			reset = add(new Button("", Button.Style.GHOST, () -> rule.all().forEach(Setting::reset)).icon(Icon.RESET));
			reset.tooltip("Reset every setting of '" + rule.rule().label() + "' to its default.");
			for (Setting<?> setting : rule.controls()) {
				rows.add(add(setting instanceof IdSetSetting ids ? new IdSetRow(ids) : new SettingRow(setting)));
			}
		}

		private boolean allDefault() {
			return rule.all().stream().allMatch(Setting::isDefault);
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			rowHeights.clear();
			int total = HEADER + 12;
			if (rule.enabled().get()) {
				for (Widget row : rows) {
					int h = row.preferredHeight(ui, width - 8);
					rowHeights.add(h);
					total += h + (h > 0 ? 2 : 0);
				}
			}

			return total + 4;
		}

		@Override
		protected void onLayout() {
			toggle.setBounds(x + width - 6 - ToggleSwitch.WIDTH - 15, y + 7, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT);
			reset.setBounds(x + width - 15, y + 6, 11, 11);
			int cy = y + HEADER + 12;
			for (int i = 0; i < rows.size(); i++) {
				int h = i < rowHeights.size() ? rowHeights.get(i) : 0;
				rows.get(i).setBounds(x + 4, cy, width - 8, h);
				cy += h + (h > 0 ? 2 : 0);
			}
		}

		@Override
		public void render(UiContext ui) {
			onLayout();
			boolean on = rule.enabled().get();
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			ui.outline(x, y, width, height, on ? Theme.ACCENT_DIM : Theme.BORDER);
			ui.graphics().renderItem(rule.rule().icon(), x + 6, y + 6);
			ui.text(rule.rule().label(), x + 27, y + 6, on ? Theme.TEXT : Theme.TEXT_SECONDARY);
			ui.textClipped(rule.rule().purpose(), x + 27, y + 18, width - 33 - ToggleSwitch.WIDTH - 20, Theme.TEXT_MUTED);
			toggle.render(ui);
			if (!allDefault()) {
				reset.render(ui);
			}

			ui.textClipped(state(), x + 6, y + HEADER, width - 12, stateColor());
			if (on) {
				for (Widget row : rows) {
					row.render(ui);
				}
			}
		}

		private String state() {
			LocalPlayer player = Minecraft.getInstance().player;
			if (player == null) {
				return "No world loaded";
			}

			int slot = rule.hotbarSlot();
			String where = slot < 0 ? "Not in your hotbar" : "In " + AutoAttributeSwapModule.slotName(slot) + ": "
					+ player.getInventory().getItem(slot).getHoverName().getString();
			long remaining = rule.remainingCooldown();
			return where + "  ·  " + (remaining > 0 ? String.format(Locale.ROOT, "next swap in %.1f s", remaining / 1000.0) : "ready");
		}

		private int stateColor() {
			if (!rule.enabled().get()) return Theme.TEXT_MUTED;
			if (rule.hotbarSlot() < 0) return Theme.WARNING;
			return rule.remainingCooldown() > 0 ? Theme.WARNING : Theme.SUCCESS;
		}

		@Override
		public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
			if (toggle.mouseClicked(ui, mouseX, mouseY, button)) {
				return true;
			}

			if (!allDefault() && reset.mouseClicked(ui, mouseX, mouseY, button)) {
				return true;
			}

			if (rule.enabled().get()) {
				for (Widget row : rows) {
					if (row.mouseClicked(ui, mouseX, mouseY, button)) {
						return true;
					}
				}
			}

			return false;
		}
	}
}
