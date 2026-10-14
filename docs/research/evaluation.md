# Amethyst growth as an exposure indicator: controlled evaluation

This report evaluates what amethyst growth-stage observations can and cannot tell about how long a geode's chunk was randomly ticked, in a controlled test world with known ground truth. It does not identify players or locate bases, and a correlation found here does not establish causation outside the tested setup.

## Setup

- Sweep geodes: 40 (feature seeds 2001-2040)
- Geode center: 13713168, 42, -669782
- Minecraft: 1.21.11
- World seed: 0
- Method: vanilla minecraft:amethyst_geode feature in a stone cube, growth by calling BlockState.randomTick on every budding block with a seeded RandomSource; observations by the analyzer's grouping code on server block data
- Harvest arm: 12 geodes: 1300 random ticks, then 70% of clusters removed, then 26 more random ticks
- Exposure levels (random ticks): [0, 10, 20, 35, 52, 75, 100, 130, 180, 300, 520, 800, 1300, 2000]
- Samples: 606 observations; 596 used, 10 incomplete observations excluded
- Split by geode: 35 training geodes, 17 held-out test geodes (every third geode by id). Repeated observations of one geode never span both sets.
- Exposure unit: random ticks received by each budding block. One hour of a loaded, randomly ticking chunk at the default random tick speed (3) is 52.7 random ticks.
- Classifiers receive only observed features (stage counts, budding blocks, face occupancy). Ground truth is stored separately and is used only to score predictions.
- Pre-registered support criteria (held-out set): balanced accuracy >= 0.80, false-positive and false-negative rates <= 0.20, at least 30 test samples and 10 per class. Below 0.65 balanced accuracy a hypothesis is unsupported.

## Results by hypothesis

| Hypothesis | Verdict | Test samples (pos/neg) | Threshold | Precision | Recall | False-positive rate | False-negative rate |
|---|---|---|---|---|---|---|---|
| H1 Cluster share separates short from long exposure | **SUPPORTED** | 194 (103/91) | 0.616 | 97.1% | 96.1% | 3.3% | 3.9% |
| H1b Cluster share among large buds and clusters separates short from long exposure | **SUPPORTED** | 194 (103/91) | 0.839 | 92.2% | 91.3% | 8.8% | 8.7% |
| H2 Cluster share distinguishes durations beyond saturation | **UNSUPPORTED** | 77 (38/39) | 0.214 | 49.4% | 100.0% | 100.0% | 0.0% |
| H3 Any cluster indicates exposure (naive rule) | **UNSUPPORTED** | 194 (155/39) | 1.000 | 81.9% | 99.4% | 87.2% | 0.6% |

### H1: Cluster share separates short from long exposure

Statement: Geodes whose budding blocks were randomly ticked for at least 2 hours (at default speed) show a higher share of clusters among all growth objects than geodes with less exposure, enough to classify them on unseen geodes.

- Label: positive when exposure >= 105.5 random ticks (2.0 h)
- Rule: clusters / all growth objects >= threshold, threshold fitted on training geodes (Youden's J)
- Verdict: **SUPPORTED**. Meets all pre-registered criteria on held-out geodes (balanced accuracy 0.96, false-positive rate 0.03, false-negative rate 0.04).
- Training: 402 samples; held-out test: 194 samples
- Test precision: 97.1% (99/102; 95% CI 91.7-99.0%)
- Test recall: 96.1% (99/103; 95% CI 90.4-98.5%)
- Test false-positive rate: 3.3% (3/91; 95% CI 1.1-9.2%)
- Test false-negative rate: 3.9% (4/103; 95% CI 1.5-9.6%)
- Balanced accuracy: test 0.964, training 0.973 (a large gap would indicate overfitting)
- Calibration of a logistic model p = 1/(1+exp(-(-8.80 + 14.65 x))) fitted on training geodes: Brier score 0.041, expected calibration error 0.046 on test geodes

| Predicted probability | Test samples | Mean predicted | Observed positive rate |
|---|---:|---:|---:|
| 0.0-0.2 | 84 | 2.3% | 4.8% |
| 0.2-0.4 | 5 | 30.1% | 0.0% |
| 0.4-0.6 | 3 | 42.9% | 0.0% |
| 0.6-0.8 | 7 | 69.8% | 100.0% |
| 0.8-1.0 | 95 | 98.9% | 96.8% |

Performance by experimental condition (held-out geodes):

| Condition | Samples | Positives | Accuracy | False-positive rate | False-negative rate |
|---|---:|---:|---:|---:|---:|
| sweep | 182 | 91 | 98.4% | 3.3% | 0.0% |
| harvest-baseline | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested-regrowth | 4 | 4 | 0.0% | n/a | 100.0% |

### H1b: Cluster share among large buds and clusters separates short from long exposure

Statement: Same as H1 but using clusters / (large buds + clusters), which ignores small and medium buds.

- Label: positive when exposure >= 105.5 random ticks (2.0 h)
- Rule: clusters / (large + clusters) >= threshold, threshold fitted on training geodes (Youden's J)
- Verdict: **SUPPORTED**. Meets all pre-registered criteria on held-out geodes (balanced accuracy 0.91, false-positive rate 0.09, false-negative rate 0.09).
- Training: 400 samples; held-out test: 194 samples
- Test precision: 92.2% (94/102; 95% CI 85.3-96.0%)
- Test recall: 91.3% (94/103; 95% CI 84.2-95.3%)
- Test false-positive rate: 8.8% (8/91; 95% CI 4.5-16.4%)
- Test false-negative rate: 8.7% (9/103; 95% CI 4.7-15.8%)
- Balanced accuracy: test 0.912, training 0.911 (a large gap would indicate overfitting)
- Calibration of a logistic model p = 1/(1+exp(-(-14.18 + 17.28 x))) fitted on training geodes: Brier score 0.067, expected calibration error 0.026 on test geodes

| Predicted probability | Test samples | Mean predicted | Observed positive rate |
|---|---:|---:|---:|
| 0.0-0.2 | 69 | 2.8% | 0.0% |
| 0.2-0.4 | 12 | 26.5% | 41.7% |
| 0.4-0.6 | 12 | 48.8% | 41.7% |
| 0.6-0.8 | 11 | 71.4% | 72.7% |
| 0.8-1.0 | 90 | 94.8% | 94.4% |

Performance by experimental condition (held-out geodes):

| Condition | Samples | Positives | Accuracy | False-positive rate | False-negative rate |
|---|---:|---:|---:|---:|---:|
| sweep | 182 | 91 | 91.8% | 8.8% | 7.7% |
| harvest-baseline | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested-regrowth | 4 | 4 | 50.0% | n/a | 50.0% |

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
- Calibration of a logistic model p = 1/(1+exp(-(7.22 + -7.37 x))) fitted on training geodes: Brier score 0.241, expected calibration error 0.003 on test geodes

| Predicted probability | Test samples | Mean predicted | Observed positive rate |
|---|---:|---:|---:|
| 0.0-0.2 | 0 | - | - |
| 0.2-0.4 | 0 | - | - |
| 0.4-0.6 | 73 | 46.9% | 46.6% |
| 0.6-0.8 | 0 | - | - |
| 0.8-1.0 | 4 | 98.6% | 100.0% |

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
- Verdict: **UNSUPPORTED**. Performance on held-out geodes is close to chance (balanced accuracy 0.56, false-positive rate 0.87, false-negative rate 0.01).
- Training: 402 samples; held-out test: 194 samples
- Test precision: 81.9% (154/188; 95% CI 75.8-86.8%)
- Test recall: 99.4% (154/155; 95% CI 96.4-99.9%)
- Test false-positive rate: 87.2% (34/39; 95% CI 73.3-94.4%)
- Test false-negative rate: 0.6% (1/155; 95% CI 0.1-3.6%)
- Balanced accuracy: test 0.561, training 0.537 (a large gap would indicate overfitting)
- Calibration of a logistic model p = 1/(1+exp(-(-4.00 + 1.02 x))) fitted on training geodes: Brier score 0.041, expected calibration error 0.018 on test geodes

| Predicted probability | Test samples | Mean predicted | Observed positive rate |
|---|---:|---:|---:|
| 0.0-0.2 | 31 | 6.7% | 6.5% |
| 0.2-0.4 | 11 | 28.0% | 45.5% |
| 0.4-0.6 | 4 | 51.8% | 50.0% |
| 0.6-0.8 | 9 | 74.9% | 88.9% |
| 0.8-1.0 | 139 | 99.2% | 99.3% |

Performance by experimental condition (held-out geodes):

| Condition | Samples | Positives | Accuracy | False-positive rate | False-negative rate |
|---|---:|---:|---:|---:|---:|
| sweep | 182 | 143 | 80.8% | 87.2% | 0.7% |
| harvest-baseline | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested-regrowth | 4 | 4 | 100.0% | n/a | 0.0% |

## Cluster share versus exposure (sweep arm, complete observations)

| Random ticks | Hours at default speed | Samples | Mean cluster share | SD | Min | Max |
|---:|---:|---:|---:|---:|---:|---:|
| 0 | 0.0 | 40 | 23.4% | 0.187 | 0.0% | 100.0% |
| 10 | 0.2 | 40 | 16.7% | 0.109 | 0.0% | 41.7% |
| 20 | 0.4 | 40 | 15.4% | 0.095 | 0.0% | 40.0% |
| 35 | 0.7 | 40 | 19.6% | 0.080 | 0.0% | 38.5% |
| 52 | 1.0 | 40 | 26.7% | 0.080 | 13.0% | 43.8% |
| 75 | 1.4 | 40 | 39.6% | 0.084 | 23.1% | 62.5% |
| 100 | 1.9 | 40 | 55.0% | 0.088 | 34.8% | 81.3% |
| 130 | 2.5 | 40 | 71.9% | 0.080 | 60.9% | 100.0% |
| 180 | 3.4 | 40 | 89.3% | 0.060 | 77.8% | 100.0% |
| 300 | 5.7 | 40 | 99.1% | 0.019 | 92.6% | 100.0% |
| 520 | 9.9 | 40 | 100.0% | 0.000 | 100.0% | 100.0% |
| 800 | 15.2 | 40 | 100.0% | 0.000 | 100.0% | 100.0% |
| 1300 | 24.7 | 40 | 100.0% | 0.000 | 100.0% | 100.0% |
| 2000 | 37.9 | 40 | 100.0% | 0.000 | 100.0% | 100.0% |

Repeated observations: in 40 of 40 sweep geodes the cluster count never decreased between consecutive observations.

## Findings derived from the data

- The mean cluster share is not monotone in exposure: it fell from 23.4% at 0 to 16.7% at 10 random ticks. New small buds on free faces add to the denominator before any of them matures, so a higher share does not simply mean longer exposure.
- The share saturates: from 300 random ticks (about 5.7 h at default speed) the mean is 99.1%, so longer exposure leaves no further trace in this statistic.
- Freshly generated geodes already have a mean cluster share of 23.4% (range 0.0-100.0%) from world generation alone.
- H1: in the 'harvested-regrowth' condition 4 of 4 long-exposure geodes were missed (held-out set; small sample).
- H1b: in the 'harvested-regrowth' condition 2 of 4 long-exposure geodes were missed (held-out set; small sample).
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
