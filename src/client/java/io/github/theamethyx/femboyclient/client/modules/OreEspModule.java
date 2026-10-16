package io.github.theamethyx.femboyclient.client.modules;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.client.render.RenderingUtilities;
import io.github.theamethyx.femboyclient.client.world.BlockScanner;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.IdSetSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Highlights ore blocks in chunks your client has loaded. It shows what the server sent: servers with anti-xray send
 * fake or hidden ores, and those appear as sent. Many servers forbid this kind of module.
 */
public final class OreEspModule extends Module implements ClientHooks.WorldRender {
	static final Set<String> DEFAULT_ORES = Set.of("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore", "minecraft:emerald_ore",
			"minecraft:deepslate_emerald_ore", "minecraft:ancient_debris", "minecraft:gold_ore", "minecraft:deepslate_gold_ore",
			"minecraft:iron_ore", "minecraft:deepslate_iron_ore", "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore",
			"minecraft:lapis_ore", "minecraft:deepslate_lapis_ore");

	private static final Map<String, Integer> COLORS = Map.ofEntries(Map.entry("diamond", 0x4FE3E3), Map.entry("emerald", 0x3DDC6E),
			Map.entry("ancient_debris", 0x9C6B4E), Map.entry("gold", 0xF2C14E), Map.entry("iron", 0xD8AF93), Map.entry("redstone", 0xF24A4A),
			Map.entry("lapis", 0x3E64F2), Map.entry("coal", 0x5A5A5A), Map.entry("copper", 0xE07B4B), Map.entry("quartz", 0xEDE7DF));

	private final IdSetSetting ores = settings().add(new IdSetSetting("blocks", "Ores", "Blocks to highlight.", DEFAULT_ORES));
	private final NumberSetting radius = settings().add(new NumberSetting("radius", "Radius", "Chunks around you to search.", 4, 1, 12, 1, "chunks"));
	private final BooleanSetting exposedOnly = settings().add(new BooleanSetting("exposed_only", "Only exposed ores",
			"Show only ores touching air, water or lava (what you could see from a cave).", false));
	private final BooleanSetting throughWalls = settings().add(new BooleanSetting("through_walls", "Through walls", "Draw through blocks.", true));
	private final NumberSetting limit = settings().add(new NumberSetting("limit", "Maximum shown", "Closest ores drawn at most.", 400, 50, 2000, 50, ""));

	private final BlockScanner scanner = new BlockScanner();
	private Set<Block> blocks = Set.of();
	private List<BlockScanner.Hit> shown = List.of();

	public OreEspModule() {
		super("ore_esp", "Ore ESP", "Highlights ores in chunks your client has loaded (what the server sent; anti-xray servers send fake ores).",
				ModuleCategory.VISUALS, false, NO_KEY);
		ores.choices(() -> io.github.theamethyx.femboyclient.client.combat.ItemChoices.blocks());
		ores.onChange(ids -> rebuildFilter());
	}

	private void rebuildFilter() {
		blocks = io.github.theamethyx.femboyclient.client.combat.ItemChoices.resolveBlocks(ores.get());
		Set<Block> current = blocks;
		scanner.setFilter(state -> current.contains(state.getBlock()));
	}

	@Override
	protected void onEnable() {
		rebuildFilter();
	}

	@Override
	protected void onDisable() {
		scanner.clear();
		shown = List.of();
	}

	public List<BlockScanner.Hit> shown() {
		return shown;
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		scanner.tick(client.level, player.blockPosition(), radius.intValue(), 5000, 3_000_000);
		BlockPos at = player.blockPosition();
		shown = scanner.hits().stream()
				.filter(hit -> !exposedOnly.get() || exposed(client, hit.pos()))
				.sorted(java.util.Comparator.comparingDouble(hit -> hit.pos().distSqr(at)))
				.limit(limit.intValue())
				.toList();
	}

	private static boolean exposed(Minecraft client, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			BlockState neighbor = client.level.getBlockState(pos.relative(direction));
			if (neighbor.isAir() || !neighbor.getFluidState().isEmpty()) {
				return true;
			}
		}

		return false;
	}

	static int colorFor(BlockState state) {
		String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		for (Map.Entry<String, Integer> entry : COLORS.entrySet()) {
			if (id.contains(entry.getKey())) {
				return entry.getValue();
			}
		}

		return 0xB58FFF;
	}

	@Override
	public void renderWorld(RenderingUtilities render) {
		for (BlockScanner.Hit hit : shown) {
			int rgb = colorFor(hit.state());
			AABB box = new AABB(hit.pos());
			render.filledBox(box, 0x30000000 | rgb, throughWalls.get());
			render.boxOutline(box, 0xD0000000 | rgb, 1.5f, throughWalls.get());
		}
	}
}
