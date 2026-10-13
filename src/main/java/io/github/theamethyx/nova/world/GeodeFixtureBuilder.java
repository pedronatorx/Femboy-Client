package io.github.theamethyx.nova.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.CaveFeatures;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Builds reproducible geode fixtures with Minecraft's own code:
 * <ol>
 *   <li>fills a cube with stone as host rock (the vanilla geode feature refuses to place when more
 *       than one of its sample points is air, exactly as it would refuse open caves),</li>
 *   <li>places the vanilla {@code minecraft:amethyst_geode} worldgen feature with a fixed seed. The
 *       feature also samples noise seeded from the <em>world</em> seed, so a fixture is reproducible
 *       for the same world seed and fixture seed,</li>
 *   <li>optionally applies {@code n} random ticks to every budding amethyst block using the real
 *       {@code BlockState.randomTick} (the same growth code the game runs), in a fixed order with a
 *       seeded random source,</li>
 *   <li>counts the result on the server ({@link GeodeCensus}).</li>
 * </ol>
 *
 * <p>Applying ticks directly is a controlled stand-in for elapsed time: at the default random tick
 * speed of 3, a block receives on average 3/4096 random ticks per game tick, so {@code n} ticks
 * correspond to about {@code n * 4096 / 3} game ticks of a loaded, randomly ticking chunk. Server
 * thread only.
 */
public final class GeodeFixtureBuilder {
	/** Half the edge length of the host-rock cube; vanilla geodes fit well inside. */
	public static final int HALF = 16;
	public static final int DEFAULT_RANDOM_TICK_SPEED = 3;
	public static final int BLOCKS_PER_SECTION = 4096;

	public record Result(boolean placed, BoundingBox cleared, GeodeCensus census, int randomTicksApplied, long seed) {
	}

	private GeodeFixtureBuilder() {
	}

	public static BoundingBox clearedBox(BlockPos center) {
		return new BoundingBox(center.getX() - HALF, center.getY() - HALF, center.getZ() - HALF,
				center.getX() + HALF, center.getY() + HALF, center.getZ() + HALF);
	}

	public static Result build(ServerLevel level, BlockPos center, long seed, int randomTicksPerBudding) {
		BoundingBox cleared = clearedBox(center);
		for (BlockPos pos : BlockPos.betweenClosed(cleared.minX(), cleared.minY(), cleared.minZ(), cleared.maxX(), cleared.maxY(), cleared.maxZ())) {
			level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		}

		ConfiguredFeature<?, ?> geode = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE)
				.getOrThrow(CaveFeatures.AMETHYST_GEODE).value();
		boolean placed = geode.place(level, level.getChunkSource().getGenerator(), RandomSource.create(seed), center);

		int applied = 0;
		if (placed && randomTicksPerBudding > 0) {
			applied = applyRandomTicks(level, cleared, randomTicksPerBudding, seed ^ 0x5DEECE66DL);
		}

		return new Result(placed, cleared, GeodeCensus.of(level, cleared), applied, seed);
	}

	/**
	 * Calls the block's real random-tick handler {@code ticks} times on every budding amethyst block
	 * in the region (round-robin, positions in a fixed order).
	 *
	 * @return the number of random ticks applied per budding block
	 */
	public static int applyRandomTicks(ServerLevel level, BoundingBox region, int ticks, long seed) {
		List<BlockPos> budding = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(region.minX(), region.minY(), region.minZ(), region.maxX(), region.maxY(), region.maxZ())) {
			if (level.getBlockState(pos).is(Blocks.BUDDING_AMETHYST)) {
				budding.add(pos.immutable());
			}
		}

		budding.sort(Comparator.comparingInt((BlockPos p) -> p.getX()).thenComparingInt(p -> p.getY()).thenComparingInt(p -> p.getZ()));
		RandomSource random = RandomSource.create(seed);
		for (int tick = 0; tick < ticks; tick++) {
			for (BlockPos pos : budding) {
				level.getBlockState(pos).randomTick(level, pos, random);
			}
		}

		return ticks;
	}

	/** Expected game ticks for {@code randomTicks} random ticks per block at a given random tick speed. */
	public static double equivalentGameTicks(int randomTicks, int randomTickSpeed) {
		return randomTicks * (double) BLOCKS_PER_SECTION / randomTickSpeed;
	}
}
