package io.github.theamethyx.femboyclient.world.oresim;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.ScatteredOreFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Predicts where a world's generator puts ores, from the seed alone, without loading or touching the world's chunks.
 *
 * <p>How: for every chunk around the center it fills a private chunk with the generator's own terrain noise (stone,
 * noise caves, aquifers and ore veins), builds the surface (bedrock, deepslate, dirt, sand...) and carves caves and
 * canyons with vanilla's own code, and then runs vanilla's ore features with vanilla's decoration and feature seeds in
 * vanilla's step and index order.
 *
 * <p>What it does not reproduce, and so where predictions can be wrong: features before the ore step (lakes, geodes,
 * dungeons, fossils), structures and the terrain they adapt, and the order in which the real world generated
 * neighbouring chunks. Ores where those changed the terrain may be missing, moved or extra. That is why each prediction
 * is an estimate until {@link OreComparison} has checked it against the real world.
 *
 * <p>The simulation needs the generator, its random state and the seed, so it runs only where those exist: in
 * singleplayer (the integrated server) and, for operators, on a server through its command. It never reads hidden
 * information from a server.
 */
public final class OreSimulator {
	/** Reports progress and lets the caller cancel; called from the simulation thread. */
	public interface Progress {
		boolean cancelled();

		void progress(int done, int total, String stage);

		Progress NONE = new Progress() {
			@Override
			public boolean cancelled() {
				return false;
			}

			@Override
			public void progress(int done, int total, String stage) {
			}
		};
	}

	/** The simulation was stopped through {@link Progress#cancelled()}. */
	public static final class CancelledException extends RuntimeException {
		CancelledException() {
			super("Cancelled", null, false, false);
		}
	}

	/** One predicted ore block. {@code vein} marks ores from the terrain noise's ore veins rather than an ore feature. */
	public record Prediction(BlockPos pos, OreKind ore, BlockState state, boolean vein) {
	}

	/** What a simulation found. Predictions are sorted by chunk, then position. */
	public record Result(long seed, String dimension, ChunkPos center, int radius, List<Prediction> predictions, int terrainChunks,
			int featureRuns, int failedFeatures, long millis) {
		public Set<ChunkPos> chunks() {
			Set<ChunkPos> chunks = new HashSet<>();
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					chunks.add(new ChunkPos(center.x + dx, center.z + dz));
				}
			}

			return chunks;
		}
	}

	public static final String UNSUPPORTED_GENERATOR = "This dimension does not use the normal terrain generator (flat, void or a custom generator), so its ores cannot be simulated.";

	private OreSimulator() {
	}

	/** Whether the level's generator is one this simulator can reproduce. */
	public static boolean supports(ServerLevel level) {
		return level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator;
	}

	/**
	 * Simulates the square of chunks within {@code radius} of {@code center}. Runs on the calling thread (call it from a
	 * worker, never the server or render thread); the terrain noise is computed on vanilla's background executor.
	 *
	 * @throws IllegalStateException with {@link #UNSUPPORTED_GENERATOR} for flat and custom generators
	 * @throws CancelledException when {@code progress} asked to stop
	 */
	public static Result simulate(ServerLevel level, ChunkPos center, int radius, Set<OreKind> ores, Progress progress) {
		long started = System.nanoTime();
		if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)) {
			throw new IllegalStateException(UNSUPPORTED_GENERATOR);
		}

		Simulation simulation = new Simulation(level, generator, center, radius, ores, progress);
		List<Prediction> predictions = simulation.run();
		return new Result(level.getSeed(), level.dimension().identifier().toString(), center, radius, predictions, simulation.terrain.size(),
				simulation.featureRuns, simulation.failedFeatures, (System.nanoTime() - started) / 1_000_000);
	}

	/** One run: its private chunks and the generator objects it uses. */
	private static final class Simulation {
		private final ServerLevel level;
		private final NoiseBasedChunkGenerator generator;
		private final RandomState randomState;
		private final NoiseGeneratorSettings settings;
		private final ChunkPos center;
		private final int radius;
		private final Set<OreKind> ores;
		private final Progress progress;
		private final long seed;
		private final Map<Long, ProtoChunk> terrain = new HashMap<>();
		private final Set<BlockPos> veinOres = new HashSet<>();
		private final BiomeManager biomes;
		private final WorldGenLevel proxy;
		private ChunkPos decorating;
		private int featureRuns;
		private int failedFeatures;

		Simulation(ServerLevel level, NoiseBasedChunkGenerator generator, ChunkPos center, int radius, Set<OreKind> ores, Progress progress) {
			this.level = level;
			this.generator = generator;
			this.randomState = level.getChunkSource().randomState();
			this.settings = generator.generatorSettings().value();
			this.center = center;
			this.radius = radius;
			this.ores = ores;
			this.progress = progress;
			this.seed = level.getSeed();
			int minY = QuartPos.fromBlock(level.getMinY());
			int maxY = minY + QuartPos.fromBlock(level.getHeight()) - 1;
			this.biomes = new BiomeManager((x, y, z) -> generator.getBiomeSource().getNoiseBiome(x, Mth.clamp(y, minY, maxY), z, randomState.sampler()),
					BiomeManager.obfuscateSeed(seed));
			this.proxy = (WorldGenLevel) Proxy.newProxyInstance(OreSimulator.class.getClassLoader(), new Class<?>[] {WorldGenLevel.class}, new Handler());
		}

		List<Prediction> run() {
			List<Candidate> features = oreFeatures();
			int decorated = 2 * radius + 3;
			int total = decorated * decorated;
			int done = 0;
			// Neighbours decorate into each other's chunks, so the ring around the requested area is decorated too.
			for (int dx = -radius - 1; dx <= radius + 1; dx++) {
				for (int dz = -radius - 1; dz <= radius + 1; dz++) {
					checkCancelled();
					decorate(new ChunkPos(center.x + dx, center.z + dz), features);
					progress.progress(++done, total, "Placing ores");
				}
			}

			return collect();
		}

		private void checkCancelled() {
			if (progress.cancelled()) {
				throw new CancelledException();
			}
		}

		/** A placed feature that places ores, with the step and index vanilla seeds it with. */
		private record Candidate(int step, int index, PlacedFeature feature) {
		}

		@SuppressWarnings("unchecked")
		private List<Candidate> oreFeatures() {
			List<FeatureSorter.StepFeatureData> steps = ((ChunkGenerator) generator).featuresPerStep.get();
			List<Candidate> candidates = new ArrayList<>();
			for (int step = 0; step < steps.size(); step++) {
				List<PlacedFeature> list = steps.get(step).features();
				for (int index = 0; index < list.size(); index++) {
					PlacedFeature feature = list.get(index);
					var kind = feature.feature().value().feature();
					if (kind instanceof OreFeature || kind instanceof ScatteredOreFeature) {
						candidates.add(new Candidate(step, index, feature));
					}
				}
			}

			return candidates;
		}

		private void decorate(ChunkPos pos, List<Candidate> features) {
			decorating = pos;
			BlockPos origin = SectionPos.of(pos, level.getMinSectionY()).origin();
			WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(0));
			long decorationSeed = random.setDecorationSeed(seed, origin.getX(), origin.getZ());
			for (Candidate candidate : features) {
				random.setFeatureSeed(decorationSeed, candidate.index, candidate.step);
				featureRuns++;
				try {
					candidate.feature.placeWithBiomeCheck(proxy, generator, random, origin);
				} catch (CancelledException e) {
					throw e;
				} catch (RuntimeException e) {
					// A feature that needs something the private chunks do not provide (a modded ore feature, say) is skipped.
					failedFeatures++;
				}
			}
		}

		/** The private chunk at this position, filled from the terrain noise the first time it is needed. */
		ProtoChunk chunk(int x, int z) {
			long key = ChunkPos.asLong(x, z);
			ProtoChunk chunk = terrain.get(key);
			if (chunk == null) {
				checkCancelled();
				chunk = fill(new ChunkPos(x, z));
				terrain.put(key, chunk);
			}

			return chunk;
		}

		private ProtoChunk fill(ChunkPos pos) {
			ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, level, level.palettedContainerFactory(), null);
			// Without structures: the noise is made here so the generator does not ask the world for structure starts.
			chunk.getOrCreateNoiseChunk(c -> NoiseChunk.forChunk(c, randomState, Beardifier.EMPTY, settings, generator.globalFluidPicker.get(), Blender.empty()));
			generator.fillFromNoise(Blender.empty(), randomState, null, chunk).join();
			applySurface(chunk);
			applyCarvers(chunk);
			if (Math.abs(pos.x - center.x) <= radius && Math.abs(pos.z - center.z) <= radius) {
				scan(chunk, (blockPos, state) -> veinOres.add(blockPos));
			}

			return chunk;
		}

		/** Vanilla's surface step: grass, dirt, sand, terracotta, bedrock and deepslate, from the same rules and randomness. */
		private void applySurface(ProtoChunk chunk) {
			generator.buildSurface(chunk, new WorldGenerationContext(generator, level), randomState, null, biomes,
					level.registryAccess().lookupOrThrow(Registries.BIOME), Blender.empty());
		}

		/** Vanilla's carver step (caves and canyons), as {@code NoiseBasedChunkGenerator.applyCarvers} runs it. */
		private void applyCarvers(ProtoChunk chunk) {
			BiomeManager carverBiomes = biomes.withDifferentSource((x, y, z) -> generator.getBiomeSource().getNoiseBiome(x, y, z, randomState.sampler()));
			WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0));
			ChunkPos pos = chunk.getPos();
			NoiseChunk noise = chunk.getOrCreateNoiseChunk(c -> {
				throw new IllegalStateException("The noise chunk is made when the chunk is filled");
			});
			Aquifer aquifer = noise.aquifer();
			CarvingContext context = new CarvingContext(generator, level.registryAccess(), chunk.getHeightAccessorForGeneration(), noise, randomState,
					settings.surfaceRule());
			CarvingMask mask = chunk.getOrCreateCarvingMask();
			for (int dx = -8; dx <= 8; dx++) {
				for (int dz = -8; dz <= 8; dz++) {
					ChunkPos start = new ChunkPos(pos.x + dx, pos.z + dz);
					BiomeGenerationSettings generation = generator.getBiomeGenerationSettings(generator.getBiomeSource().getNoiseBiome(
							QuartPos.fromBlock(start.getMinBlockX()), 0, QuartPos.fromBlock(start.getMinBlockZ()), randomState.sampler()));
					int index = 0;
					for (Holder<ConfiguredWorldCarver<?>> carver : generation.getCarvers()) {
						random.setLargeFeatureSeed(seed + index, start.x, start.z);
						if (carver.value().isStartChunk(random)) {
							carver.value().carve(context, chunk, carverBiomes::getBiome, random, aquifer, start, mask);
						}

						index++;
					}
				}
			}
		}

		private interface Visitor {
			void visit(BlockPos pos, BlockState state);
		}

		/** Visits the ores of the chosen kinds in a chunk, skipping sections whose palette has none. */
		private void scan(ChunkAccess chunk, Visitor visitor) {
			OreComparison.visitOres(chunk, ores, (pos, ore, state) -> visitor.visit(pos, state));
		}

		private List<Prediction> collect() {
			List<Prediction> predictions = new ArrayList<>();
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					ProtoChunk chunk = chunk(center.x + dx, center.z + dz);
					OreComparison.visitOres(chunk, ores, (pos, ore, state) -> predictions.add(new Prediction(pos, ore, state, veinOres.contains(pos))));
				}
			}

			return predictions;
		}

		/** The private chunks seen as a world by vanilla's feature code. */
		private final class Handler implements InvocationHandler {
			@Override
			public Object invoke(Object self, Method method, Object[] args) throws Throwable {
				switch (method.getName()) {
					case "getSeed":
						return seed;
					case "getChunk":
						if (args[0] instanceof BlockPos pos) {
							return chunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
						}

						return chunk((Integer) args[0], (Integer) args[1]);
					case "hasChunk":
						return true;
					case "getBlockState":
						return blockState((BlockPos) args[0]);
					case "getFluidState":
						return blockState((BlockPos) args[0]).getFluidState();
					case "setBlock":
						return setBlock((BlockPos) args[0], (BlockState) args[1]);
					case "removeBlock":
					case "destroyBlock":
						return false;
					case "ensureCanWrite":
						return canWrite((BlockPos) args[0]);
					case "getHeight":
						if (args == null || args.length == 0) {
							return level.getHeight();
						}

						if (args[0] instanceof Heightmap.Types type) {
							int x = (Integer) args[1];
							int z = (Integer) args[2];
							return chunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)).getHeight(type, x & 15, z & 15) + 1;
						}

						break;
					case "getMinY":
						return level.getMinY();
					case "getBiomeManager":
						return biomes;
					case "getBiome":
						return biomes.getBiome((BlockPos) args[0]);
					case "getNoiseBiome":
					case "getUncachedNoiseBiome":
						return generator.getBiomeSource().getNoiseBiome((Integer) args[0], (Integer) args[1], (Integer) args[2], randomState.sampler());
					case "registryAccess":
						return level.registryAccess();
					case "getLevel":
						return level;
					case "getRandom":
						return RandomSource.create();
					case "getSeaLevel":
						return generator.getSeaLevel();
					case "isClientSide":
						return false;
					case "dimensionType":
						return level.dimensionType();
					case "enabledFeatures":
						return level.enabledFeatures();
					case "toString":
						return "OreSimulator world";
					case "hashCode":
						return System.identityHashCode(self);
					case "equals":
						return self == args[0];
					default:
						break;
				}

				if (method.isDefault()) {
					return InvocationHandler.invokeDefault(self, method, args);
				}

				throw new UnsupportedOperationException("Not available in the ore simulation: " + method.getName());
			}
		}

		private BlockState blockState(BlockPos pos) {
			if (level.isOutsideBuildHeight(pos.getY())) {
				return Blocks.VOID_AIR.defaultBlockState();
			}

			return chunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())).getBlockState(pos);
		}

		/**
		 * Like vanilla's feature step: a chunk's features may write into it and its eight neighbours. Like vanilla, the
		 * height is not checked here: features start from origins below the world (triangle height ranges reach under
		 * the bottom), place the part of the vein inside it and, above all, use up their random numbers; refusing them
		 * would shift every later vein of the same feature.
		 */
		private boolean canWrite(BlockPos pos) {
			return decorating != null && Math.abs(SectionPos.blockToSectionCoord(pos.getX()) - decorating.x) <= 1
					&& Math.abs(SectionPos.blockToSectionCoord(pos.getZ()) - decorating.z) <= 1;
		}

		private boolean setBlock(BlockPos pos, BlockState state) {
			if (!canWrite(pos) || level.isOutsideBuildHeight(pos.getY())) {
				return false;
			}

			chunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())).setBlockState(pos, state, 0);
			return true;
		}
	}
}
