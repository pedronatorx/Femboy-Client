package io.github.theamethyx.nova.core.research.geode;

import java.util.List;

import io.github.theamethyx.nova.core.research.GrowthStage;
import io.github.theamethyx.nova.core.research.StageCounts;

/**
 * Everything directly observed about one geode in one scan. Contains no estimates: counts come
 * straight from block states, and incompleteness is recorded rather than filled in.
 *
 * @param id                   stable identifier: dimension and minimum corner
 * @param counts               growth objects by stage
 * @param buddingBlocks        budding amethyst blocks (the only blocks that grow buds)
 * @param amethystBlocks       plain amethyst blocks of the inner layer
 * @param faces                what surrounds the budding blocks
 * @param unavailablePositions block positions near the geode that could not be read
 * @param complete             whether the whole geode and its margin were readable
 * @param completenessNote     why it is incomplete, or a confirmation that it is complete
 * @param scenarioId           test scenario run whose registered region contains it, or null
 * @param growth               positions of every growth object (for overlays; not persisted)
 */
public record GeodeObservation(
		String id,
		String dimension,
		int minX, int minY, int minZ,
		int maxX, int maxY, int maxZ,
		StageCounts counts,
		int buddingBlocks,
		int amethystBlocks,
		FaceSummary faces,
		int unavailablePositions,
		boolean complete,
		String completenessNote,
		long timestampMillis,
		String scenarioId,
		String source,
		List<PositionedStage> growth) {

	public record PositionedStage(int x, int y, int z, GrowthStage stage) {
	}

	/** Counts of budding-block faces by what is next to them. */
	public record FaceSummary(int free, int ownGrowth, int blocked, int unavailable) {
		public int total() {
			return free + ownGrowth + blocked + unavailable;
		}

		/**
		 * Faces that already carry growth among faces where growth is possible (free + occupied).
		 * Approaches 1 as a geode saturates; undefined without growable faces.
		 */
		public double occupancy() {
			int growable = free + ownGrowth;
			return growable == 0 ? Double.NaN : (double) ownGrowth / growable;
		}
	}

	public double centerX() {
		return (minX + maxX + 1) / 2.0;
	}

	public double centerY() {
		return (minY + maxY + 1) / 2.0;
	}

	public double centerZ() {
		return (minZ + maxZ + 1) / 2.0;
	}

	public GeodeObservation withScenario(String scenario) {
		return new GeodeObservation(id, dimension, minX, minY, minZ, maxX, maxY, maxZ, counts, buddingBlocks, amethystBlocks, faces,
				unavailablePositions, complete, completenessNote, timestampMillis, scenario, source, growth);
	}
}
