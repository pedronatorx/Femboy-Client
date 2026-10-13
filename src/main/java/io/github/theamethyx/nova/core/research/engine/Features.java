package io.github.theamethyx.nova.core.research.engine;

import io.github.theamethyx.nova.core.research.StageCounts;
import io.github.theamethyx.nova.core.research.geode.GeodeObservation;

/**
 * The only information a classifier may use: quantities directly observable from block states.
 * Deliberately has no field for ground truth, scenario parameters or run ids, so a model cannot
 * read labels by construction.
 */
public record Features(
		/** Clusters among all growth objects (NaN without growth objects). */
		double clusterFraction,
		/** Clusters among large buds + clusters only (NaN without either). */
		double clusterAmongLarge,
		int growthObjects,
		int buddingBlocks,
		/** Grown faces among growable faces (NaN without growable faces). */
		double occupancy,
		boolean complete) {

	public static Features of(GeodeObservation observation) {
		StageCounts c = observation.counts();
		return new Features(c.clustersOfAll().value(), c.clustersOfLargeAndClusters().value(), c.total(), observation.buddingBlocks(),
				observation.faces().occupancy(), observation.complete());
	}
}
