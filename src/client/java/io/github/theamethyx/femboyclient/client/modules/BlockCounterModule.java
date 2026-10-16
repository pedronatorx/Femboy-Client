package io.github.theamethyx.femboyclient.client.modules;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;

import io.github.theamethyx.femboyclient.client.combat.ItemChoices;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.client.world.BlockScanner;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;
import io.github.theamethyx.femboyclient.core.setting.IdSetSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/** Counts chosen blocks in the chunks your client has loaded around you, with scan progress on the HUD. */
public final class BlockCounterModule extends Module implements ClientHooks.HudBlock {
	private final EnumSetting<HudAnchor> anchor = settings().add(new EnumSetting<>("anchor", "Position", "Screen corner for the counts.", HudAnchor.TOP_LEFT));
	private final IdSetSetting chosen = settings().add(new IdSetSetting("blocks", "Blocks", "Blocks to count.",
			Set.of("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore", "minecraft:ancient_debris", "minecraft:spawner")));
	private final NumberSetting radius = settings().add(new NumberSetting("radius", "Radius", "Chunks around you to count.", 4, 1, 12, 1, "chunks"));

	private final BlockScanner scanner = new BlockScanner();
	private Map<String, Integer> counts = Map.of();

	public BlockCounterModule() {
		super("block_counter", "Block Counter", "Counts chosen blocks in loaded chunks around you, with scan progress.", ModuleCategory.WORLD, false, NO_KEY);
		chosen.choices(ItemChoices::blocks);
		chosen.onChange(ids -> rebuild());
	}

	private void rebuild() {
		Set<Block> blocks = ItemChoices.resolveBlocks(chosen.get());
		scanner.setFilter(state -> blocks.contains(state.getBlock()));
	}

	@Override
	protected void onEnable() {
		rebuild();
	}

	@Override
	protected void onDisable() {
		scanner.clear();
		counts = Map.of();
	}

	public Map<String, Integer> counts() {
		return counts;
	}

	public BlockScanner scanner() {
		return scanner;
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		scanner.tick(client.level, client.player.blockPosition(), radius.intValue(), 5000, 3_000_000);
		Map<String, Integer> next = new LinkedHashMap<>();
		for (BlockScanner.Hit hit : scanner.hits()) {
			next.merge(hit.state().getBlock().getName().getString(), 1, Integer::sum);
		}

		counts = next;
	}

	@Override
	public HudAnchor hudAnchor() {
		return anchor.get();
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		int total = scanner.chunksInRange();
		int done = Math.min(scanner.scannedChunks(), total);
		lines.add(HudLine.of("Scanned", done + "/" + total + " chunks", done < total ? Theme.WARNING : Theme.SUCCESS));
		if (counts.isEmpty()) {
			lines.add(HudLine.of("Found", "none", Theme.TEXT_MUTED));
		}

		counts.forEach((name, count) -> lines.add(HudLine.of(name, String.valueOf(count))));
	}
}
