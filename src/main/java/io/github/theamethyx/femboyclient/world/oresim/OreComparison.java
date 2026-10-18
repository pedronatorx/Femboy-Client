package io.github.theamethyx.femboyclient.world.oresim;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Checks a simulation against the real world: each prediction is <em>verified</em> when the real block at that position
 * is the same kind of ore, <em>contradicted</em> when it is anything else (a different block, or an ore someone mined),
 * and <em>estimated</em> while its chunk cannot be read (not loaded). It also counts the real ores the simulation did not
 * predict, so precision and recall can both be reported.
 */
public final class OreComparison {
	public enum Check {
		ESTIMATED("Estimated"), VERIFIED("Verified"), CONTRADICTED("Not there");

		private final String label;

		Check(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	/** Totals of one comparison. {@code actual} counts real ores of the chosen kinds in the chunks that could be read. */
	public record Summary(int predicted, int verified, int contradicted, int estimated, int actual, int actualPredicted, int checkedChunks,
			int totalChunks) {
		/** Share of checked predictions that were right, or -1 when nothing could be checked. */
		public double precision() {
			int checked = verified + contradicted;
			return checked == 0 ? -1 : (double) verified / checked;
		}

		/** Share of real ores in the checked chunks that were predicted, or -1 when there were none. */
		public double recall() {
			return actual == 0 ? -1 : (double) actualPredicted / actual;
		}

		public String describe() {
			if (checkedChunks == 0) {
				return String.format(Locale.ROOT, "%,d predicted; none checked yet (no chunk of the area is loaded).", predicted);
			}

			return String.format(Locale.ROOT, "%,d predicted: %,d verified, %,d not there, %,d not checked (%d/%d chunks loaded). "
					+ "Precision %s, recall %s of %,d real ores.", predicted, verified, contradicted, estimated, checkedChunks, totalChunks,
					percent(precision()), percent(recall()), actual);
		}

		private static String percent(double value) {
			return value < 0 ? "n/a" : String.format(Locale.ROOT, "%.1f%%", value * 100);
		}
	}

	/** The checks of one comparison, by position, with its totals. */
	public record Outcome(Map<BlockPos, Check> checks, Summary summary) {
	}

	@FunctionalInterface
	public interface OreVisitor {
		void visit(BlockPos pos, OreKind ore, BlockState state);
	}

	private OreComparison() {
	}

	/** Visits the ores of the given kinds in a chunk; sections whose palette holds none of them are skipped. */
	public static void visitOres(ChunkAccess chunk, Set<OreKind> kinds, OreVisitor visitor) {
		LevelChunkSection[] sections = chunk.getSections();
		int minX = chunk.getPos().getMinBlockX();
		int minZ = chunk.getPos().getMinBlockZ();
		for (int index = 0; index < sections.length; index++) {
			LevelChunkSection section = sections[index];
			if (section == null || section.hasOnlyAir() || !section.maybeHas(state -> wanted(state, kinds))) {
				continue;
			}

			int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						BlockState state = section.getBlockState(x, y, z);
						OreKind ore = OreKind.of(state);
						if (ore != null && kinds.contains(ore)) {
							visitor.visit(new BlockPos(minX + x, baseY + y, minZ + z), ore, state);
						}
					}
				}
			}
		}
	}

	private static boolean wanted(BlockState state, Set<OreKind> kinds) {
		OreKind ore = OreKind.of(state);
		return ore != null && kinds.contains(ore);
	}

	/**
	 * Compares a simulation with the real world. {@code actualChunk} returns the real chunk at chunk coordinates, or
	 * {@code null} when it cannot be read (then its predictions stay estimated). Call it on the thread that owns those
	 * chunks.
	 */
	public static Outcome compare(OreSimulator.Result result, Set<OreKind> kinds, BiFunction<Integer, Integer, ChunkAccess> actualChunk) {
		Map<Long, List<OreSimulator.Prediction>> byChunk = new HashMap<>();
		for (OreSimulator.Prediction prediction : result.predictions()) {
			if (kinds.contains(prediction.ore())) {
				byChunk.computeIfAbsent(ChunkPos.asLong(prediction.pos()), key -> new java.util.ArrayList<>()).add(prediction);
			}
		}

		Map<BlockPos, Check> checks = new HashMap<>();
		int predicted = 0;
		int verified = 0;
		int contradicted = 0;
		int estimated = 0;
		int actual = 0;
		int actualPredicted = 0;
		int checkedChunks = 0;
		Set<ChunkPos> chunks = result.chunks();
		for (ChunkPos pos : chunks) {
			List<OreSimulator.Prediction> predictions = byChunk.getOrDefault(pos.toLong(), List.of());
			predicted += predictions.size();
			ChunkAccess chunk = actualChunk.apply(pos.x, pos.z);
			if (chunk == null) {
				estimated += predictions.size();
				predictions.forEach(p -> checks.put(p.pos(), Check.ESTIMATED));
				continue;
			}

			checkedChunks++;
			Set<BlockPos> predictedHere = new HashSet<>();
			for (OreSimulator.Prediction prediction : predictions) {
				predictedHere.add(prediction.pos());
				boolean same = OreKind.of(chunk.getBlockState(prediction.pos())) == prediction.ore();
				checks.put(prediction.pos(), same ? Check.VERIFIED : Check.CONTRADICTED);
				if (same) {
					verified++;
				} else {
					contradicted++;
				}
			}

			int[] counts = new int[2];
			visitOres(chunk, kinds, (blockPos, ore, state) -> {
				counts[0]++;
				if (predictedHere.contains(blockPos)) {
					counts[1]++;
				}
			});
			actual += counts[0];
			actualPredicted += counts[1];
		}

		return new Outcome(checks, new Summary(predicted, verified, contradicted, estimated, actual, actualPredicted, checkedChunks, chunks.size()));
	}

	/** Writes the predictions and their checks as CSV (x, y, z, ore, block, source, check). */
	public static void exportCsv(OreSimulator.Result result, Map<BlockPos, Check> checks, Path file) throws IOException {
		Files.createDirectories(file.getParent());
		try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			out.write(String.format(Locale.ROOT, "# Ore simulation, dimension %s, center chunk %d %d, radius %d chunks, %d predictions%n",
					result.dimension(), result.center().x, result.center().z, result.radius(), result.predictions().size()));
			out.write("# Positions are estimates of the world generator; 'check' says whether the real world was compared.\n");
			out.write("x,y,z,ore,block,source,check\n");
			for (OreSimulator.Prediction p : result.predictions()) {
				Check check = checks.getOrDefault(p.pos(), Check.ESTIMATED);
				out.write(String.format(Locale.ROOT, "%d,%d,%d,%s,%s,%s,%s%n", p.pos().getX(), p.pos().getY(), p.pos().getZ(), p.ore().name().toLowerCase(Locale.ROOT),
						net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(p.state().getBlock()), p.vein() ? "ore vein" : "ore feature",
						check.name().toLowerCase(Locale.ROOT)));
			}
		}
	}
}
