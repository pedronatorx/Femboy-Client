package io.github.theamethyx.femboyclient.client.modules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.combat.InventoryCounts;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.gui.widget.Button;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * The items the Schematic Viewer's build needs: how many in total, how many are still to place, and how many you carry.
 * Updated with every comparison pass of the Schematic Viewer, which must be on with a placed schematic.
 */
public final class MaterialListModule extends Module implements ClientHooks.HudBlock, ClientHooks.CustomControls {
	private final NumberSetting hudLines = settings().add(new NumberSetting("hud_lines", "HUD lines", "Materials shown on the HUD, most needed first.", 6, 1, 20, 1, ""));
	private final BooleanSetting onlyMissing = settings().add(new BooleanSetting("only_missing", "Only what is left",
			"List only materials you still have to place.", true));

	/** One material: {@code needed} for the whole build, {@code left} still to place, {@code carried} in your inventory. */
	public record Entry(Item item, int needed, int left, int carried) {
		public String name() {
			return item.getName(new ItemStack(item)).getString();
		}
	}

	public MaterialListModule() {
		super("material_list", "Material List", "Items needed for the loaded schematic, what is left to place and what you carry.",
				ModuleCategory.WORLD, false, NO_KEY);
	}

	@Override
	public Tier tier() {
		return Tier.ASSIST;
	}

	private static SchematicViewerModule viewer() {
		SchematicViewerModule viewer = FemboyClient.get().modules().get(SchematicViewerModule.class);
		return viewer != null && viewer.isRunning() ? viewer : null;
	}

	/** The material list, most left to place first; empty when there is no comparison yet. */
	public List<Entry> entries() {
		SchematicViewerModule viewer = viewer();
		SchematicViewerModule.Progress progress = viewer == null ? null : viewer.progress();
		LocalPlayer player = Minecraft.getInstance().player;
		List<Entry> result = new ArrayList<>();
		if (progress == null || player == null) {
			return result;
		}

		for (Map.Entry<Item, int[]> entry : progress.materials().entrySet()) {
			int needed = entry.getValue()[0];
			int left = needed - entry.getValue()[1];
			if (onlyMissing.get() && left <= 0) {
				continue;
			}

			Item item = entry.getKey();
			result.add(new Entry(item, needed, left, InventoryCounts.count(player, stack -> stack.is(item))));
		}

		result.sort(Comparator.comparingInt(Entry::left).reversed().thenComparing(Entry::name));
		return result;
	}

	/** The list as plain text, one material per line. */
	public String asText() {
		StringBuilder text = new StringBuilder();
		for (Entry entry : entries()) {
			text.append(entry.name()).append(": ").append(entry.left()).append(" left of ").append(entry.needed())
					.append(" (").append(stacks(entry.left(), entry.item())).append("), carrying ").append(entry.carried()).append('\n');
		}

		return text.toString();
	}

	private static String stacks(int count, Item item) {
		int max = new ItemStack(item).getMaxStackSize();
		return count >= max ? count / max + " stacks + " + count % max : count + "";
	}

	@Override
	public HudAnchor hudAnchor() {
		return HudAnchor.TOP_LEFT;
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		if (viewer() == null) {
			lines.add(HudLine.of("Materials", "turn on Schematic Viewer and load a schematic", Theme.TEXT_MUTED));
			return;
		}

		List<Entry> entries = entries();
		if (entries.isEmpty()) {
			lines.add(HudLine.of("Materials", viewer().progress() == null ? "waiting for the comparison" : "nothing left to place", Theme.SUCCESS));
			return;
		}

		for (Entry entry : entries.subList(0, Math.min(hudLines.intValue(), entries.size()))) {
			boolean enough = entry.carried() >= entry.left();
			lines.add(HudLine.of(entry.name(), entry.left() + " left, have " + entry.carried(), enough ? Theme.SUCCESS : Theme.WARNING));
		}
	}

	@Override
	public List<Widget> createControls(UiContext ui, Runnable refresh) {
		List<Widget> widgets = new ArrayList<>();
		Button copy = new Button("Copy list to clipboard", Button.Style.SECONDARY,
				() -> Minecraft.getInstance().keyboardHandler.setClipboard(asText()));
		widgets.add(new Row(copy));
		List<Entry> entries = entries();
		if (entries.isEmpty()) {
			widgets.add(new SchematicViewerModule.Note(viewer() == null ? "Turn on Schematic Viewer, load and place a schematic." : "No materials left to place.",
					Theme.TEXT_MUTED));
		}

		for (Entry entry : entries) {
			widgets.add(new SchematicViewerModule.Note(entry.name() + ": " + entry.left() + " left of " + entry.needed() + ", carrying " + entry.carried(),
					entry.carried() >= entry.left() ? Theme.SUCCESS : Theme.TEXT_SECONDARY));
		}

		return widgets;
	}

	private static final class Row extends io.github.theamethyx.femboyclient.client.gui.widget.Container {
		private final Button button;

		Row(Button button) {
			this.button = add(button);
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 15;
		}

		@Override
		protected void onLayout() {
			button.setBounds(x, y, width, 15);
		}
	}
}
