package io.github.theamethyx.femboyclient.core.research.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.theamethyx.femboyclient.core.research.geode.GeodeObservation;

/**
 * Observations and ground truth for one experiment, kept in two separate collections joined only by
 * sample id. {@link ActivityResearchEngine} gives classifiers {@link Features} built from samples and
 * uses the truth map exclusively to score them.
 */
public final class Dataset {
	/**
	 * One observation. {@code groupId} identifies the physical geode, so repeated observations of the
	 * same geode stay in the same train/test split (otherwise the test set would leak).
	 */
	public record Sample(String sampleId, String groupId, GeodeObservation observation) {
	}

	/**
	 * Known conditions of a sample.
	 *
	 * @param exposureRandomTicks random ticks each budding block received after generation (for
	 *                            natural ticking: the expected value from game ticks and tick speed)
	 * @param condition           experiment arm, e.g. "sweep", "harvested", "partial-scan"
	 */
	public record Truth(double exposureRandomTicks, String condition) {
	}

	private final List<Sample> samples = new ArrayList<>();
	private final Map<String, Truth> truth = new LinkedHashMap<>();
	private final Map<String, String> metadata = new LinkedHashMap<>();

	public void add(Sample sample, Truth groundTruth) {
		if (truth.containsKey(sample.sampleId())) {
			throw new IllegalArgumentException("Duplicate sample id " + sample.sampleId());
		}

		samples.add(sample);
		truth.put(sample.sampleId(), groundTruth);
	}

	public List<Sample> samples() {
		return List.copyOf(samples);
	}

	/** Ground truth; only the engine's scoring code reads this. */
	Truth truth(String sampleId) {
		return truth.get(sampleId);
	}

	public Map<String, Truth> truthView() {
		return Map.copyOf(truth);
	}

	public Map<String, String> metadata() {
		return metadata;
	}

	public int size() {
		return samples.size();
	}
}
