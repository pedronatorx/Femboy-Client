package io.github.theamethyx.femboyclient.world.oresim;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** The kinds of ore the simulation tracks; stone and deepslate variants count as the same kind. */
public enum OreKind {
	COAL("Coal", 0x5A5A5A),
	IRON("Iron", 0xD8AF93),
	COPPER("Copper", 0xE07B4B),
	GOLD("Gold", 0xF2C14E),
	REDSTONE("Redstone", 0xF24A4A),
	LAPIS("Lapis lazuli", 0x3E64F2),
	DIAMOND("Diamond", 0x4FE3E3),
	EMERALD("Emerald", 0x3DDC6E),
	QUARTZ("Nether quartz", 0xEDE7DF),
	ANCIENT_DEBRIS("Ancient debris", 0x9C6B4E);

	private final String label;
	private final int color;

	OreKind(String label, int color) {
		this.label = label;
		this.color = color;
	}

	public String label() {
		return label;
	}

	public int color() {
		return color;
	}

	/** The kind of ore this block is, or {@code null} for anything else (raw ore blocks of veins are not ores). */
	public static OreKind of(BlockState state) {
		if (state.is(BlockTags.COAL_ORES)) {
			return COAL;
		}

		if (state.is(BlockTags.IRON_ORES)) {
			return IRON;
		}

		if (state.is(BlockTags.COPPER_ORES)) {
			return COPPER;
		}

		if (state.is(BlockTags.GOLD_ORES)) {
			return GOLD;
		}

		if (state.is(BlockTags.REDSTONE_ORES)) {
			return REDSTONE;
		}

		if (state.is(BlockTags.LAPIS_ORES)) {
			return LAPIS;
		}

		if (state.is(BlockTags.DIAMOND_ORES)) {
			return DIAMOND;
		}

		if (state.is(BlockTags.EMERALD_ORES)) {
			return EMERALD;
		}

		if (state.is(Blocks.NETHER_QUARTZ_ORE)) {
			return QUARTZ;
		}

		return state.is(Blocks.ANCIENT_DEBRIS) ? ANCIENT_DEBRIS : null;
	}
}
