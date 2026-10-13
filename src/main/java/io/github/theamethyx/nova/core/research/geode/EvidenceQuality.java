package io.github.theamethyx.nova.core.research.geode;

/**
 * How much an observation can support any statistic, judged only from data quality (sample size
 * and completeness). This is a rating of the data, <strong>not</strong> a probability that any
 * hypothesis about the geode is true.
 */
public enum EvidenceQuality {
	INSUFFICIENT("Insufficient", "Fewer growth objects than the minimum sample size; percentages are dominated by chance."),
	LIMITED("Limited", "Incomplete observation or a small sample; treat percentages as rough."),
	ADEQUATE("Adequate", "Complete observation with a sample at least twice the minimum size.");

	private final String label;
	private final String explanation;

	EvidenceQuality(String label, String explanation) {
		this.label = label;
		this.explanation = explanation;
	}

	public String label() {
		return label;
	}

	public String explanation() {
		return explanation;
	}

	public static EvidenceQuality rate(GeodeObservation observation, int minimumSample) {
		int n = observation.counts().total();
		if (n < minimumSample) {
			return INSUFFICIENT;
		}

		if (!observation.complete() || n < 2 * minimumSample) {
			return LIMITED;
		}

		return ADEQUATE;
	}
}
