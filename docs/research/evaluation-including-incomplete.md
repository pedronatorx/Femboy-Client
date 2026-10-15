# Same evaluation including incomplete observations

This report evaluates what amethyst growth-stage observations can and cannot tell about how long a geode's chunk was randomly ticked, in a controlled test world with known ground truth. It does not identify players or locate bases, and a correlation found here does not establish causation outside the tested setup.

## Setup

- World seed: 0
- Minecraft: 1.21.11
- Geode center: 6190880, 42, -1964163
- Sweep geodes: 40 (feature seeds 2001-2040)
- Exposure levels (random ticks): [0, 10, 20, 35, 52, 75, 100, 130, 180, 300, 520, 800, 1300, 2000]
- Harvest arm: 12 geodes: 1300 random ticks, then 70% of clusters removed, then 26 more random ticks
- Method: vanilla minecraft:amethyst_geode feature in a stone cube, growth by calling BlockState.randomTick on every budding block with a seeded RandomSource; observations by the analyzer's grouping code on server block data
- Samples: 672 observations; 672 used, 0 incomplete observations excluded
- Split by geode: 35 training geodes, 17 held-out test geodes (every third geode by id). Repeated observations of one geode never span both sets.
- Exposure unit: random ticks received by each budding block. One hour of a loaded, randomly ticking chunk at the default random tick speed (3) is 52.7 random ticks.
- Classifiers receive only observed features (stage counts, budding blocks, face occupancy). Ground truth is stored separately and is used only to score predictions.
- Pre-registered support criteria (held-out set): balanced accuracy >= 0.80, false-positive and false-negative rates <= 0.20, at least 30 test samples and 10 per class. Below 0.65 balanced accuracy a hypothesis is unsupported.

## Results by hypothesis

| Hypothesis | Verdict | Test samples (pos/neg) | Threshold | Precision | Recall | False-positive rate | False-negative rate |
|---|---|---|---|---|---|---|---|
| H1 Cluster share separates short from long exposure | **SUPPORTED** | 199 (108/91) | 0.687 | 100.0% | 90.7% | 0.0% | 9.3% |
| H1b Cluster share among large buds and clusters separates short from long exposure | **SUPPORTED** | 196 (107/89) | 0.836 | 90.7% | 90.7% | 11.2% | 9.3% |
| H2 Cluster share distinguishes durations beyond saturation | **UNSUPPORTED** | 77 (38/39) | 0.150 | 49.4% | 100.0% | 100.0% | 0.0% |
| H3 Any cluster indicates exposure (naive rule) | **UNSUPPORTED** | 220 (168/52) | 1.000 | 82.4% | 94.6% | 65.4% | 5.4% |

### H1: Cluster share separates short from long exposure

Statement: Geodes whose budding blocks were randomly ticked for at least 2 hours (at default speed) show a higher share of clusters among all growth objects than geodes with less exposure, enough to classify them on unseen geodes.

- Label: positive when exposure >= 105.5 random ticks (2.0 h)
- Rule: clusters / all growth objects >= threshold, threshold fitted on training geodes (Youden's J)
- Verdict: **SUPPORTED**. Meets all pre-registered criteria on held-out geodes (balanced accuracy 0.95, false-positive rate 0.00, false-negative rate 0.09).
- Training: 407 samples; held-out test: 199 samples
- Test precision: 100.0% (98/98; 95% CI 96.2-100.0%)
- Test recall: 90.7% (98/108; 95% CI 83.8-94.9%)
- Test false-positive rate: 0.0% (0/91; 95% CI 0.0-4.1%)
- Test false-negative rate: 9.3% (10/108; 95% CI 5.1-16.2%)
- Balanced accuracy: test 0.954, training 0.972 (a large gap would indicate overfitting)
- Calibration of a logistic model p = 1/(1+exp(-(-9.12 + 14.83 x))) fitted on training geodes: Brier score 0.034, expected calibration error 0.047 on test geodes

| Predicted probability | Test samples | Mean predicted | Observed positive rate |
|---|---:|---:|---:|
| 0.0-0.2 | 85 | 2.1% | 5.9% |
| 0.2-0.4 | 9 | 29.3% | 0.0% |
| 0.4-0.6 | 3 | 50.7% | 33.3% |
| 0.6-0.8 | 7 | 73.3% | 100.0% |
| 0.8-1.0 | 95 | 98.9% | 100.0% |

Performance by experimental condition (held-out geodes):

| Condition | Samples | Positives | Accuracy | False-positive rate | False-negative rate |
|---|---:|---:|---:|---:|---:|
| sweep | 182 | 91 | 97.3% | 0.0% | 5.5% |
| partial-scan | 5 | 5 | 80.0% | n/a | 20.0% |
| harvest-baseline | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested-regrowth | 4 | 4 | 0.0% | n/a | 100.0% |

### H1b: Cluster share among large buds and clusters separates short from long exposure

Statement: Same as H1 but using clusters / (large buds + clusters), which ignores small and medium buds.

- Label: positive when exposure >= 105.5 random ticks (2.0 h)
- Rule: clusters / (large + clusters) >= threshold, threshold fitted on training geodes (Youden's J)
- Verdict: **SUPPORTED**. Meets all pre-registered criteria on held-out geodes (balanced accuracy 0.90, false-positive rate 0.11, false-negative rate 0.09).
- Training: 407 samples; held-out test: 196 samples
- Test precision: 90.7% (97/107; 95% CI 83.6-94.8%)
- Test recall: 90.7% (97/107; 95% CI 83.6-94.8%)
- Test false-positive rate: 11.2% (10/89; 95% CI 6.2-19.5%)
- Test false-negative rate: 9.3% (10/107; 95% CI 5.2-16.4%)
- Balanced accuracy: test 0.897, training 0.954 (a large gap would indicate overfitting)
- Calibration of a logistic model p = 1/(1+exp(-(-20.03 + 24.59 x))) fitted on training geodes: Brier score 0.079, expected calibration error 0.049 on test geodes

| Predicted probability | Test samples | Mean predicted | Observed positive rate |
|---|---:|---:|---:|
| 0.0-0.2 | 73 | 3.3% | 5.5% |
| 0.2-0.4 | 8 | 27.7% | 37.5% |
| 0.4-0.6 | 5 | 44.8% | 40.0% |
| 0.6-0.8 | 9 | 70.1% | 33.3% |
| 0.8-1.0 | 101 | 97.8% | 94.1% |

Performance by experimental condition (held-out geodes):

| Condition | Samples | Positives | Accuracy | False-positive rate | False-negative rate |
|---|---:|---:|---:|---:|---:|
| sweep | 180 | 91 | 90.0% | 11.2% | 8.8% |
| partial-scan | 4 | 4 | 75.0% | n/a | 25.0% |
| harvest-baseline | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested-regrowth | 4 | 4 | 75.0% | n/a | 25.0% |

### H2: Cluster share distinguishes durations beyond saturation

Statement: Among geodes exposed for at least 5 hours, those exposed for at least 24 hours have a higher cluster share.

- Label: positive when exposure >= 1265.6 random ticks (24.0 h); only samples with exposure >= 263.7 random ticks (5.0 h)
- Rule: clusters / all growth objects >= threshold, threshold fitted on training geodes (Youden's J)
- Verdict: **UNSUPPORTED**. Performance on held-out geodes is close to chance (balanced accuracy 0.50, false-positive rate 1.00, false-negative rate 0.00).
- Training: 159 samples; held-out test: 77 samples
- Test precision: 49.4% (38/77; 95% CI 38.5-60.3%)
- Test recall: 100.0% (38/38; 95% CI 90.8-100.0%)
- Test false-positive rate: 100.0% (39/39; 95% CI 91.0-100.0%)
- Test false-negative rate: 0.0% (0/38; 95% CI 0.0-9.2%)
- Balanced accuracy: test 0.500, training 0.500 (a large gap would indicate overfitting)
- Calibration of a logistic model p = 1/(1+exp(-(7.89 + -8.04 x))) fitted on training geodes: Brier score 0.236, expected calibration error 0.003 on test geodes

| Predicted probability | Test samples | Mean predicted | Observed positive rate |
|---|---:|---:|---:|
| 0.0-0.2 | 0 | - | - |
| 0.2-0.4 | 0 | - | - |
| 0.4-0.6 | 73 | 46.3% | 46.6% |
| 0.6-0.8 | 0 | - | - |
| 0.8-1.0 | 4 | 98.9% | 100.0% |

Performance by experimental condition (held-out geodes):

| Condition | Samples | Positives | Accuracy | False-positive rate | False-negative rate |
|---|---:|---:|---:|---:|---:|
| sweep | 65 | 26 | 40.0% | 100.0% | 0.0% |
| harvest-baseline | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested-regrowth | 4 | 4 | 100.0% | n/a | 0.0% |

### H3: Any cluster indicates exposure (naive rule)

Statement: A geode containing at least one fully grown cluster has been exposed for at least 30 minutes. Threshold fixed in advance at 'one or more clusters'; not fitted.

- Label: positive when exposure >= 26.4 random ticks (0.5 h)
- Rule: cluster count >= threshold, threshold fixed in advance
- Verdict: **UNSUPPORTED**. Performance on held-out geodes is close to chance (balanced accuracy 0.65, false-positive rate 0.65, false-negative rate 0.05).
- Training: 452 samples; held-out test: 220 samples
- Test precision: 82.4% (159/193; 95% CI 76.4-87.1%)
- Test recall: 94.6% (159/168; 95% CI 90.1-97.2%)
- Test false-positive rate: 65.4% (34/52; 95% CI 51.8-76.8%)
- Test false-negative rate: 5.4% (9/168; 95% CI 2.8-9.9%)
- Balanced accuracy: test 0.646, training 0.621 (a large gap would indicate overfitting)
- Calibration of a logistic model p = 1/(1+exp(-(-1.22 + 0.34 x))) fitted on training geodes: Brier score 0.092, expected calibration error 0.052 on test geodes

| Predicted probability | Test samples | Mean predicted | Observed positive rate |
|---|---:|---:|---:|
| 0.0-0.2 | 0 | - | - |
| 0.2-0.4 | 46 | 26.2% | 32.6% |
| 0.4-0.6 | 15 | 48.4% | 26.7% |
| 0.6-0.8 | 22 | 67.6% | 54.5% |
| 0.8-1.0 | 137 | 98.2% | 100.0% |

Performance by experimental condition (held-out geodes):

| Condition | Samples | Positives | Accuracy | False-positive rate | False-negative rate |
|---|---:|---:|---:|---:|---:|
| sweep | 182 | 143 | 81.3% | 87.2% | 0.0% |
| partial-scan | 26 | 13 | 65.4% | 0.0% | 69.2% |
| harvest-baseline | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested-regrowth | 4 | 4 | 100.0% | n/a | 0.0% |

## Cluster share versus exposure (sweep arm, complete observations)

| Random ticks | Hours at default speed | Samples | Mean cluster share | SD | Min | Max |
|---:|---:|---:|---:|---:|---:|---:|
| 0 | 0.0 | 40 | 21.9% | 0.165 | 0.0% | 55.6% |
| 10 | 0.2 | 40 | 16.1% | 0.101 | 0.0% | 40.0% |
| 20 | 0.4 | 40 | 15.2% | 0.072 | 4.2% | 37.9% |
| 35 | 0.7 | 40 | 18.3% | 0.087 | 3.7% | 45.2% |
| 52 | 1.0 | 40 | 25.2% | 0.082 | 8.0% | 45.5% |
| 75 | 1.4 | 40 | 38.0% | 0.092 | 13.3% | 54.1% |
| 100 | 1.9 | 40 | 55.4% | 0.076 | 36.7% | 68.6% |
| 130 | 2.5 | 40 | 72.9% | 0.064 | 61.8% | 90.2% |
| 180 | 3.4 | 40 | 90.4% | 0.045 | 80.0% | 100.0% |
| 300 | 5.7 | 40 | 99.7% | 0.009 | 96.7% | 100.0% |
| 520 | 9.9 | 40 | 100.0% | 0.000 | 100.0% | 100.0% |
| 800 | 15.2 | 40 | 100.0% | 0.000 | 100.0% | 100.0% |
| 1300 | 24.7 | 40 | 100.0% | 0.000 | 100.0% | 100.0% |
| 2000 | 37.9 | 40 | 100.0% | 0.000 | 100.0% | 100.0% |

Repeated observations: in 40 of 40 sweep geodes the cluster count never decreased between consecutive observations.

## Findings derived from the data

- The mean cluster share is not monotone in exposure: it fell from 21.9% at 0 to 16.1% at 10 random ticks. New small buds on free faces add to the denominator before any of them matures, so a higher share does not simply mean longer exposure.
- The share saturates: from 300 random ticks (about 5.7 h at default speed) the mean is 99.7%, so longer exposure leaves no further trace in this statistic.
- Freshly generated geodes already have a mean cluster share of 21.9% (range 0.0-55.6%) from world generation alone.
- H1: in the 'harvested-regrowth' condition 4 of 4 long-exposure geodes were missed (held-out set; small sample).
- H3: in the 'partial-scan' condition 9 of 13 long-exposure geodes were missed (held-out set; small sample).
- 40 of 40 repeatedly observed geodes never lost a cluster between observations (expected: growth never reverses without block removal).

## Limitations and confounders

- Precision depends on the share of long-exposure geodes in the sample, which here is set by the experiment design. Applied to a population with a different mix, the same rule would have a different precision.
- Exposure was produced by calling the game's real random-tick code on budding blocks. In a live world, random ticks reach every entity-ticking chunk (including force-loaded ones), while a chunk's inhabited time only counts nearby non-spectator players; the two are therefore not interchangeable.
- The random tick speed game rule scales growth directly; a world with a different setting shifts every threshold.
- Generation already places buds of all four stages (about a quarter clusters on average), so fresh geodes are not cluster-free.
- Growth saturates: once every free face holds a cluster, more time leaves no trace.
- Harvesting clusters resets the visible state; harvested geodes look younger than they are.
- Small geodes have few growth objects, so their shares are noisy; incomplete observations undercount.
- Geode fixtures share one generation method (vanilla feature, flat test world seed); natural terrain, caves and water can change free faces.
- Geode shapes depend on the world seed and position as well as the feature seed, so a rerun in another world produces a different (equally valid) sample; the world seed and position are listed under Setup.
- Results hold only for the tested environment and the stated exposure range.
